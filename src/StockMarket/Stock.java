package StockMarket;

import jade.core.Agent; 
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;                    
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;                        
import jade.domain.FIPAException;                    
import jade.domain.FIPAAgentManagement.DFAgentDescription; 
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.*;

public class Stock extends Agent {
	
	
	private Map<String, Double> pricestable = new HashMap<>();
	private List<String> actions = new ArrayList<>(Arrays.asList("BBDC3", "AZUL4"));
	private Random random = new Random();
	
	
	protected void setup() {
		
		System.out.println("Starting Stock");
		

		
		DFAgentDescription dfd = new  DFAgentDescription();
		dfd.setName(getAID());
		
	    ServiceDescription sd = new ServiceDescription();
	    sd.setType("stock-market");
	    sd.setName("MainStock");
	    dfd.addServices(sd);
		try {
			
			DFService.register(this, dfd);
		}
		catch (FIPAException e) {
              e.printStackTrace();
        }	
		
		
	    for(String action:actions) {
	    	Double price = generateStockPrices(action);
	    	pricestable.put(action, price);
	    	
	    }
	    
        for (Map.Entry<String, Double> brokeractions : pricestable.entrySet()) {
            System.out.println(" - " + brokeractions.getKey() + ": R$ " + brokeractions.getValue());
        }
        
        
        addBehaviour(new OfferRequestServer());
		
	}
	
	private class OfferRequestServer extends CyclicBehaviour{

		@Override
		public void action() {
			ACLMessage msg = myAgent.receive();
			if(msg!=null) {
				String contentID = msg.getConversationId();
				 String content = msg.getContent();
				ACLMessage reply = msg.createReply();
				switch (msg.getPerformative()) {				
				   case ACLMessage.CFP:
					   if("sale-actions".equals(contentID)) {
						   String action = content;
						   if(!actions.isEmpty() && actions.contains(content) ) {
							   Double price = pricestable.get(msg.getContent());
							   reply.setPerformative(ACLMessage.INFORM);
							   reply.setInReplyTo(msg.getReplyWith());
							   reply.setContent(String.valueOf(price.doubleValue()));
							   System.out.println(getLocalName() + ": action " + msg.getContent() + " price " + price);							   
						   }
						   else {
							   reply.setPerformative(ACLMessage.REFUSE);
							   reply.setContent("action unavailable");	
							   reply.setInReplyTo(msg.getReplyWith());
							   System.out.println(getLocalName() + "action unavailable");
						   }
						   send(reply);						  
					   }
					   break;
					   
				   case ACLMessage.REQUEST:
					   if("buy".equals(contentID)) {
						   String toBuy = msg.getContent();
						   if(pricestable.containsKey(toBuy)) {
							   double price = pricestable.get(toBuy);
	                           pricestable.remove(toBuy); 
	                           actions.remove(toBuy);
	                           reply.setPerformative(ACLMessage.AGREE);
	                           reply.setInReplyTo(msg.getReplyWith());
	                           reply.setContent("get" + toBuy);
	                           System.out.println("Confirmed Stock");
						   }
						   else {
	                            reply.setPerformative(ACLMessage.REFUSE);
	                            reply.setInReplyTo(msg.getReplyWith());
	                            reply.setContent("action unavailable");
	                            System.out.println("Not confirmed Stock");
						   }
						   send(reply);
	                       if(pricestable.isEmpty()) {
	                          	myAgent.doDelete();
	                          }
					   }
					   break;
					   
					   
	               default:
	                    block();
	                    break;
				}
			}else {
				block();
			}
			
			
		}
		
	}
	
	private double generateStockPrices(String action) {
		double base = 5 + random.nextDouble() * 195; // base value
		double reflexivity = random.nextDouble() * 0.05; //expectations
		
		base *= 1 + (reflexivity *(random.nextGaussian() / 10));
		if (base < 0.01) {
			base = 0.01;
		}
		
		return Math.round(base * 100.0) / 100.0;		
	}
	
	@Override
	protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println(getLocalName() + "closing Stock");
		
	}

}
