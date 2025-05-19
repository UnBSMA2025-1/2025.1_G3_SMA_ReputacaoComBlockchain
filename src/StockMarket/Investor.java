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
	private List<String> actions = new ArrayList<>();
	
	
	@Override
	protected void setup() {
		System.out.println(getLocalName() + "Start Investor");
		
		addBehaviour(new TickerBehaviour(this, 10000) {
		    protected void onTick() {
		        System.out.println("looking for stocks");
		        
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
		        			if (!actions.contains(service.getName())) {
		                        actions.add(service.getName());
		                        brokers.put(service.getName(), dfd.getName());
		                        System.out.println("action: " + service.getName() + " broker: " + dfd.getName());		        				 
		        			}
		        			 
		        		}
		        	}
	                if (!actions.isEmpty()) {
	                    myAgent.addBehaviour(new Negotiate());
	                }

		        			        	
		        } catch (FIPAException fe){
		        	fe.printStackTrace();
		        }
		        		        
		    }
		});
	}
	
	private class Negotiate extends Behaviour {
		
	    private int total = 0;
	    private int response = 0;
	    private Random random = new Random();
	    
	    
	    @Override
	    public void onStart() {
	        for (String action : actions) {
	            boolean buy = random.nextBoolean();
	            if (buy) {
	                AID broker = brokers.get(action);
	                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
	                msg.addReceiver(broker);
	                msg.setContent(action);
	                send(msg);
	                System.out.println(getLocalName() + ": requesting" + action);
	            } else {
	                System.out.println(getLocalName() + ": not to buy " + action);
	                response++;
	            }
	        }
	    	
	    }
	    
	    @Override 
	    public void action() {
	    	ACLMessage msg = receive();
	        if (msg != null) {
	            System.out.println(getLocalName() + ": Response: " + msg.getContent());
	            response ++;
	        } else {
	            block();
	        }
	    }
	    
	    
	    @Override
	    public boolean done() {
	        return response >= total;
	    }    


	}

}
