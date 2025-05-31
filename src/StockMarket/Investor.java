package StockMarket;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;                 
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;                        
import jade.domain.FIPAException;                    
import jade.domain.FIPAAgentManagement.DFAgentDescription; 
import jade.domain.FIPAAgentManagement.ServiceDescription;


import java.util.*;

public class Investor extends Agent {
	

	private List<AID> AgentsBrokers = new ArrayList<>();
	private Random random = new Random();
	private int days = 0;
	private String[] actions = {"BBDC3","AZUL4"};
	private int count = 0;

	
	@Override
	protected void setup() {
		

		
		System.out.println(getLocalName() + "Start Investor");
		
		addBehaviour(new TickerBehaviour(this, 10000) {
		    protected void onTick() {
		    	System.out.println("----------------- DAY " + days + "----------------------");	
		    	days ++;
		    	System.out.println("looking for brokers");
		        AgentsBrokers.clear();
		        DFAgentDescription template = new DFAgentDescription();
		        ServiceDescription sd = new ServiceDescription();
		        sd.setType("brokerage-services");
		        template.addServices(sd);
		        try {
		        	DFAgentDescription[] result = DFService.search(myAgent, template);		        	
		            for (DFAgentDescription dfd : result) {
		            	if (!AgentsBrokers.contains(dfd.getName())) {
		            		AgentsBrokers.add(dfd.getName());		            		
		            	}
		        	}
	                if (!AgentsBrokers.isEmpty()) {
	                    myAgent.addBehaviour(new RequestPrices());
	                }
	                else {
	                	 System.out.println("No Broker");
	                	 count ++;
	                	 if(count == 2) {
	                		 myAgent.doDelete();
	                	 }
	                	 
	                }		        			        	
		        } catch (FIPAException fe){
		        	fe.printStackTrace();
		        }
		        		        
		    }
		});
	}
	
	private class RequestPrices extends SequentialBehaviour {		 
	    public RequestPrices() {
	        for (AID broker : AgentsBrokers) {
	            addSubBehaviour(new OneShotBehaviour() {
	                public void action() {
	                    boolean buy = DecideToBuyBroker(broker);
	                    if (buy) {
	                        String nameAction = DecideToBuyAction();
	                        ACLMessage msg = new ACLMessage(ACLMessage.CFP);
	                        msg.addReceiver(broker);
	                        msg.setConversationId("stock-trade");
	                        msg.setReplyWith("msg"+ System.currentTimeMillis());
	                        msg.setContent(nameAction);
	                        send(msg);
	                        System.out.println(getLocalName() + ": Requested" + nameAction + " for"  + broker.getLocalName());

	                        addSubBehaviour(new PriceResponse(broker,nameAction));
	                    } else {
	                        System.out.println(getLocalName() + ": Not buying from broker " + broker.getLocalName());
	                    }
	                }
	            });
	        }
	    }
	}	 	 

	private class PriceResponse extends Behaviour {
	    private AID broker;
	    private String Action;
	    private boolean PriceResponseDone = false;

	    public PriceResponse(AID broker, String Action) {
	        this.broker = broker;
	        this.Action = Action;
	    }

	    public void action() {
	        ACLMessage msg = receive();
	        if (msg != null && msg.getSender().equals(broker) && msg.getConversationId().equals("stock-trade")) {
	            switch (msg.getPerformative()) {                      
	                case ACLMessage.INFORM:
	                	Double price = Double.parseDouble(msg.getContent());
	                    boolean toBuy = DecideToBuy(price);
	                    if(toBuy) {
	                        ACLMessage msg2 = new ACLMessage(ACLMessage.REQUEST);
	                        msg2.addReceiver(broker);
	                        msg2.setConversationId("buy-action");
	                        msg2.setReplyWith("msg2"+ System.currentTimeMillis());
	                        msg2.setContent(Action);
	                        System.out.println(getLocalName() + " to buy " + Action + " for " + price);
	                        send(msg2);
	                        addBehaviour(new ResultTrade(broker,Action,"buy-action",msg.getReplyWith()));
	                    }
	                    else {
	                        System.out.print("No money");
	                    }
	                    break;

	                case ACLMessage.REFUSE:
	                    System.out.println(getLocalName() + " Refuse " + broker );
	                    break;

	                default:
	                    block();
	                    break;
	            }
	            
	            PriceResponseDone = true;
	        } else {
	            block(2000);
	        }
	    }

		@Override
		public boolean done() {
			return PriceResponseDone;
		}
	}

	private class ResultTrade extends Behaviour {
		
		private boolean ResultTradeDone = false;
	    private AID broker2;
	    private String Action2;
        private String conversationId;
        private String replyWith;	    
		
	    public ResultTrade(AID broker, String Action,String conversationId , String replyWith) {
	        this.broker2 = broker;
	        this.Action2 = Action;
			this.conversationId = conversationId;
	        this.replyWith = replyWith;
	    }
	    @Override
	    public void action() {
			MessageTemplate mt = MessageTemplate.and(
				    MessageTemplate.MatchConversationId(conversationId),
				    MessageTemplate.MatchSender(broker2)
				);
	    	ACLMessage msg = receive(mt);
	        if (msg != null && msg.getSender().equals(broker2) && msg.getConversationId().equals("buy-action")) {
	            switch (msg.getPerformative()) {                      
	                case ACLMessage.REFUSE:
	                	System.out.println(getLocalName() + " not buy " + Action2 + " for " + broker2);
	                	break;

	                case ACLMessage.AGREE:
	                    System.out.println(getLocalName() + " buy " +  Action2 + " for " +  broker2 );
	                    break;

	                default:
	                    block();
	                    break;
	            }
	            
	            ResultTradeDone = true;
	        } else {
	            block(2000);
	        }
	        
	    }

	    @Override
	    public boolean done() {
	        return ResultTradeDone;
	    }
	}



	    private boolean DecideToBuyBroker(AID broker) {
	        return random.nextBoolean();
	    }
	    
	    private String DecideToBuyAction() {
	    	int index = random.nextInt(actions.length);
	    	return actions[index];
	    }
	    
	    private boolean DecideToBuy (Double price) {
	    	return random.nextBoolean();
	    }

		@Override
		protected void takeDown() {
	        try {
	            DFService.deregister(this);
	        } catch (FIPAException e) {
	            e.printStackTrace();
	        }
	        System.out.println(getLocalName() + "closing investor");
			
		}


}
