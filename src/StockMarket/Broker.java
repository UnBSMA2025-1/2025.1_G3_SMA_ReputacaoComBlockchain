package StockMarket;

//library
import jade.core.Agent; 
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;                        
import jade.domain.FIPAException;                    
import jade.domain.FIPAAgentManagement.DFAgentDescription; 
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.*;

public class Broker extends Agent {
	
	//prices table and random
	private AID stock;
	private Random random = new Random();
	private boolean busy = false;

	
	
	@Override
	protected void setup() {
		// start message 
		System.out.println(getLocalName() + "Start broker" );
		
		DFAgentDescription dfd = new  DFAgentDescription();
		dfd.setName(getAID());	
		
		ServiceDescription sd = new ServiceDescription();
		sd.setType("brokerage-services");
		sd.setName("StockBroker");
		dfd.addServices(sd);
		//Register in DF			
		try {
				
			DFService.register(this, dfd);
		}
		catch (FIPAException e) {
              e.printStackTrace();
        }	
		    
		
		addBehaviour(new TickerBehaviour(this, 3000) {
		    protected void onTick() {
		        DFAgentDescription template = new DFAgentDescription();
		        ServiceDescription sdd = new ServiceDescription();
		        sdd.setType("stock-market");
		        template.addServices(sdd);

		        try {
		            DFAgentDescription[] result = DFService.search(myAgent, template);
		            if (result.length > 0) {
		                stock = result[0].getName(); 
		                System.out.println(getLocalName() + ": found " + stock.getLocalName());
		                this.stop();

		            } else {
		                System.out.println(getLocalName() + " loocking bag...");
		            }

		        } catch (FIPAException fe) {
		            fe.printStackTrace();
		        }
		    }
		});
		
	    addBehaviour(new TickerBehaviour(this, 5000) {
	        protected void onTick() {
	            DFAgentDescription template = new DFAgentDescription();
	            ServiceDescription sd = new ServiceDescription();
	            sd.setType("stock-market");
	            template.addServices(sd);

	            try {
	                DFAgentDescription[] result = DFService.search(myAgent, template);
	                if (result.length == 0) {
	                    System.out.println(getLocalName() + ": Stock not found. Closing Broker...");
	                    myAgent.doDelete(); 
	                }
	            } catch (FIPAException fe) {
	                fe.printStackTrace();
	            }
	        }
	    });


		
		
        addBehaviour(new OfferRequestsServer());
				
	}
	
	
	private class OfferRequestsServer extends CyclicBehaviour {				
	    public void action() {
	        ACLMessage msg = myAgent.receive();
	        
	        if (msg != null) {
	            String content = msg.getConversationId();
	            ACLMessage reply = msg.createReply();

	            switch (msg.getPerformative()) {
	                case ACLMessage.CFP:
	                    if ("stock-trade".equals(content)) {             			                    	
	                    	ACLMessage msg2 = new ACLMessage(ACLMessage.CFP);
	                    	msg2.addReceiver(stock);
	                    	msg2.setConversationId("sale-actions");
	                    	msg2.setReplyWith("msg2"+ System.currentTimeMillis());
	                    	msg2.setContent(msg.getContent());
	                    	send(msg2);
	                    	System.out.println(getLocalName() + ": Requested "  + msg.getContent() + " for "  + stock);
	                    	
	                    	myAgent.addBehaviour(new WaitStock(myAgent,msg,"sale-actions",msg2.getReplyWith()));
	                    
	                    }
	                    break;

	                case ACLMessage.REQUEST:
	                    if ("buy-action".equals(msg.getConversationId())) {
	                    	ACLMessage hd = new ACLMessage(ACLMessage.REQUEST);
	                    	hd.addReceiver(stock);
	                    	hd.setConversationId("buy");
	                    	hd.setReplyWith("hd" + System.currentTimeMillis());
	                    	hd.setContent(msg.getContent());
	                    	send(hd);
	                    	
	                    	myAgent.addBehaviour(new WaitStock(myAgent,msg,"buy",hd.getReplyWith()));
	                    }
	                    break;

	                default:
	                    block();
	                    break;
	            }

	        } else {
	            block();
	        }
	    }
	}
	
	
	private class WaitStock extends Behaviour{
		
		private boolean finished = false;
		private ACLMessage originalMsg;
        private String conversationId;
        private String replyWith;
		
		public WaitStock(Agent a , ACLMessage msg, String conversationId , String replyWith) {
			super(a);
			this.originalMsg = msg;
			this.conversationId = conversationId;
	        this.replyWith = replyWith;
	        busy = true;
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
				    MessageTemplate.and(
				        MessageTemplate.MatchConversationId(conversationId),
				        MessageTemplate.MatchSender(stock)
				    ),
				    MessageTemplate.MatchInReplyTo(replyWith)
				);
			ACLMessage response = myAgent.receive(mt);
			
			if(response != null) {			
				String responseStock = response.getContent();
				ACLMessage reply = originalMsg.createReply();
				if("action unavailable".equals(responseStock)) {
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setInReplyTo(response.getReplyWith());
					reply.setContent("action unavailable");
					System.out.println(" Not Confirmed broker");
				}
				else {
					switch (response.getPerformative()) {
					
					   case ACLMessage.INFORM:
						   Double priceStock = Double.parseDouble(response.getContent());
						   Double finalPrice = placeFee(priceStock); 
						   reply.setPerformative(ACLMessage.INFORM);
						   reply.setInReplyTo(response.getReplyWith());
						   reply.setContent(String.valueOf(finalPrice.doubleValue()));
						   System.out.println(getLocalName() + " Final Price " + finalPrice);
						   break;
							
					   case ACLMessage.AGREE:
						   reply.setPerformative(ACLMessage.AGREE);
						   reply.setInReplyTo(response.getReplyWith());
						   reply.setContent("confirmed-purchase");
						   System.out.println("Confirmed-puchase broker");
						   break;						   
					   default:
						   block();
						   break;
					}
			  
				}
				myAgent.send(reply);
				finished = true;
				busy = false;
				
			}
			else {
				block();
			}
			
		}

		@Override
		public boolean done() {

			return finished;
		}
		
	}
	
	private double placeFee(Double priceStock) {
		double transaction = 0.001 + random.nextDouble() * 0.004;
		double finalprice = priceStock * (1 + transaction);
		return Math.round(finalprice * 100.0) / 100.0;		
	}	
	
	@Override
	protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println(getLocalName() + "closing broker");
		
	}
	
}
