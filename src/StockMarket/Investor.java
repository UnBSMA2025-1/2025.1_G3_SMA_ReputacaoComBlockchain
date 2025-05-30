package StockMarket;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;                 
import jade.lang.acl.ACLMessage;                                
import jade.domain.DFService;                        
import jade.domain.FIPAException;                    
import jade.domain.FIPAAgentManagement.DFAgentDescription; 
import jade.domain.FIPAAgentManagement.ServiceDescription;


import java.util.*;

public class Investor extends Agent {
	
	private Map<String, AID> brokers = new HashMap<>();
	private List<AID> AgentsBrokers = new ArrayList<>();
	private Random random = new Random();
	
	
	@Override
	protected void setup() {
		System.out.println(getLocalName() + "Start Investor");
		
		addBehaviour(new TickerBehaviour(this, 10000) {
		    protected void onTick() {
		        System.out.println("looking for stocks");
		        brokers.clear();
		        AgentsBrokers.clear();
		        DFAgentDescription template = new DFAgentDescription();
		        ServiceDescription sd = new ServiceDescription();
		        sd.setType("sale-of-share");
		        template.addServices(sd);
		        try {
		        	DFAgentDescription[] result = DFService.search(myAgent, template);
		        	
		            for (DFAgentDescription dfd : result) {
		        		Iterator services = dfd.getAllServices();
		        		while (services.hasNext()) {
		        			ServiceDescription service = (ServiceDescription) services.next();
		        			if (!AgentsBrokers.contains(dfd.getName())) {
		                        AgentsBrokers.add(dfd.getName());		        				 
		        			}
		        			brokers.put(service.getName(), dfd.getName());
		        			 
		        		}
		        	}
	                if (!AgentsBrokers.isEmpty()) {
	                    myAgent.addBehaviour(new RequestPrices());
	                }
	                else {
	                	 System.out.println("No Broker");
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
	                            ACLMessage msg = new ACLMessage(ACLMessage.CFP);
	                            msg.addReceiver(broker);
	                            msg.setConversationId("stock-trade");
	                            msg.setReplyWith("msg"+ System.currentTimeMillis());
	                            msg.setContent("request-prices");
	                            send(msg);
	                            System.out.println(getLocalName() + ": Requested price list from " + broker.getLocalName());

	                            addSubBehaviour(new PriceResponse(broker));
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
	        private boolean done = false;

	        public PriceResponse(AID broker) {
	            this.broker = broker;
	        }

	        public void action() {
	            ACLMessage msg = receive();
	            if (msg != null && msg.getSender().equals(broker) && msg.getConversationId().equals("stock-trade")) {
	                String prices = msg.getContent();
	                System.out.println(getLocalName() + ": Received prices from " + broker.getLocalName() + " -> " + prices);
	                String Action = chosenAction(prices);
	                if (Action != null) {
	                    ACLMessage buyRequest = new ACLMessage(ACLMessage.REQUEST);
	                    buyRequest.addReceiver(broker);
	                    buyRequest.setConversationId("buy-stock");
	                    buyRequest.setReplyWith("buyRequest"+ System.currentTimeMillis());
	                    buyRequest.setContent(Action);
	                    send(buyRequest);
	                    System.out.println(getLocalName() + ": Sent buy request for " + Action + " to " + broker.getLocalName());
	                }
	                done = true;
	            } else {
	                block(2000);
	            }
	        }

	        public boolean done() {
	            return done;
	        }
	    }

	    private boolean DecideToBuyBroker(AID broker) {
	        return random.nextBoolean();
	    }

	    private String chosenAction(String prices) {
	        String[] actions = prices.split(",");
	        String bestAction = null;
	        double bestPrice = Double.MAX_VALUE;

	        for (String action : actions) {
	            String[] parts = action.split(":");
	            if (parts.length == 2) {
	                String Name = parts[0];
	                try {
	                    double price = Double.parseDouble(parts[1]);
	                    if (price < bestPrice) {
	                        bestPrice = price;
	                        bestAction = Name;
	                    }
	                } catch (NumberFormatException e) {
	                    System.out.println("invalid price: " + action);
	                }
	            }
	        }

	        return bestAction;
	    }

}
