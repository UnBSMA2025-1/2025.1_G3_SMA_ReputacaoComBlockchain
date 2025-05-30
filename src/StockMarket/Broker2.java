package StockMarket;

//library
import jade.core.Agent; 
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;                    
import jade.lang.acl.ACLMessage;                                
import jade.domain.DFService;                        
import jade.domain.FIPAException;                    
import jade.domain.FIPAAgentManagement.DFAgentDescription; 
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.*;

public class Broker2 extends Agent {
	
	//prices table and random
	private Map<String, Double> pricestable = new HashMap<>();
	private Random random = new Random();
	
	
	@Override
	protected void setup() {
		// start message 
		System.out.println(getLocalName() + "Start broker" );
		
		//actions
		String[] actions = {"ALOS3","BLUE3"};
		
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

