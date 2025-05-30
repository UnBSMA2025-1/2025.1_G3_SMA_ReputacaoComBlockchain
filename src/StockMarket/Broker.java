package StockMarket;

//library
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

public class Broker extends Agent {
	
	//prices table and random
	private Map<String, Double> pricestable = new HashMap<>();
	private Random random = new Random();
	
	
	@Override
	protected void setup() {
		// start message 
		System.out.println(getLocalName() + "Start broker" );
		
		//actions
		String[] actions = {"BBDC3","AZUL4"};
		
		DFAgentDescription dfd = new  DFAgentDescription();
		dfd.setName(getAID());	
		
		//Generating prices
		for (String action:actions) {
			double price = generateStockPrices(action);
			pricestable.put(action, price);
			
			ServiceDescription sd = new ServiceDescription();
			sd.setType("sale-of-share");
			sd.setName(action);
			dfd.addServices(sd);
		}
			
			
		//Register in DF			
		try {
				
			DFService.register(this, dfd);
		}
		catch (FIPAException e) {
              e.printStackTrace();
        }								
			
		System.out.println(getLocalName() + "actions created");
        for (Map.Entry<String, Double> brokeractions : pricestable.entrySet()) {
            System.out.println(" - " + brokeractions.getKey() + ": R$ " + brokeractions.getValue());
        }
        
        
        addBehaviour(new OfferRequestsServer());
				
	}
	
	
	private class OfferRequestsServer extends CyclicBehaviour {

	    public void action() {
	        ACLMessage msg = myAgent.receive();
	        
	        if (msg != null) {
	            String content = msg.getContent();
	            ACLMessage reply = msg.createReply();

	            switch (msg.getPerformative()) {
	                case ACLMessage.CFP:
	                    if ("request-prices".equals(content)) {
	                        StringBuilder ActionsList = new StringBuilder();
	                        for (Map.Entry<String, Double> entry : pricestable.entrySet()) {
	                            if (ActionsList.length() > 0) {
	                                ActionsList.append(",");
	                            }
	                            ActionsList.append(entry.getKey()).append(":").append(entry.getValue());
	                        }

	                        reply.setPerformative(ACLMessage.INFORM);
	                        reply.setConversationId("stock-trade");
	                        reply.setContent(ActionsList.toString());
	                        send(reply);
	                    }
	                    break;

	                case ACLMessage.REQUEST:
	                    if ("buy-stock".equals(msg.getConversationId())) {
	                        String ToBuy = content;

	                        if (pricestable.containsKey(ToBuy)) {
	                            double price = pricestable.get(ToBuy);
	                            pricestable.remove(ToBuy); 
	                            reply.setPerformative(ACLMessage.AGREE);
	                            reply.setConversationId("buy-stock-confirmation");
	                            reply.setContent("get" + ToBuy + " R$ " + price);
	                        } else {
	                            reply.setPerformative(ACLMessage.REFUSE);
	                            reply.setConversationId("buy-stock-confirmation");
	                            reply.setContent("Action " + ToBuy + "unavailable");
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

	        } else {
	            block();
	        }
	    }
	}


	private double generateStockPrices(String action) {
		double base = 5 + random.nextDouble() * 195; // base value
		double reflexivity = random.nextDouble() * 0.05; //expectations
		double transaction = 0.001 + random.nextDouble() * 0.004; //broker fee
		
		base *= 1 + (reflexivity *(random.nextGaussian() / 10));
		if (base < 0.01) {
			base = 0.01;
		}
		double finalprice = base * (1 + transaction);
		
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
