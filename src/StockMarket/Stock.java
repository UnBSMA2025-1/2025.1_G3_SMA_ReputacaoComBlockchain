package StockMarket;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.MessageTemplate; 

import java.util.*;

public class Stock extends Agent {

    private Map<String, Double> pricestable = new HashMap<>();
    private List<String> availableActions = new ArrayList<>(Arrays.asList("BBDC3", "AZUL4", "PETR4", "VALE3")); 
    private Random random = new Random();

    protected void setup() {
        System.out.println(getLocalName() + ": Starting Stock Agent");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("stock-market"); 
        sd.setName(getLocalName() + "-StockMarketService"); 
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }


        for (String action : availableActions) {
            Double price = generateStockPrices(action);
            pricestable.put(action, price);
        }

        System.out.println(getLocalName() + ": Initial stock prices:");
        for (Map.Entry<String, Double> entry : pricestable.entrySet()) {
            System.out.println("  - " + entry.getKey() + ": R$ " + String.format("%.2f", entry.getValue()));
        }

        addBehaviour(new StockServiceServer());
    }

    private class StockServiceServer extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.CFP),
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) { 
                ACLMessage reply = msg.createReply();
                reply.setInReplyTo(msg.getReplyWith()); 
                switch (msg.getPerformative()) {
                    case ACLMessage.CFP: 
                        if (pricestable.containsKey(msg.getContent())) {
                            Double price = pricestable.get(msg.getContent());
                            reply.setPerformative(ACLMessage.INFORM); 
                            reply.setContent(String.valueOf(price));
                            System.out.println(getLocalName() + ": Action " + msg.getContent() + " price is " + String.format("%.2f", price) + ". Sending INFORM to " + msg.getSender().getLocalName());
                        } else {
                            reply.setPerformative(ACLMessage.REFUSE);
                            reply.setContent("Action " + msg.getContent() + " unavailable");
                            System.out.println(getLocalName() + ": Action " + msg.getContent() + " unavailable. Sending REFUSE to " + msg.getSender().getLocalName());
                        }
                        myAgent.send(reply);
                        break;

                    case ACLMessage.REQUEST: 
                        if (pricestable.containsKey(msg.getContent()) && availableActions.contains(msg.getContent())) {
                            availableActions.remove(msg.getContent()); 
                            pricestable.remove(msg.getContent());
                            reply.setPerformative(ACLMessage.AGREE); 
                            reply.setContent("Sale of " + msg.getContent() + " confirmed by stock");
                            System.out.println(getLocalName() + ": Confirmed sale of " + msg.getContent() + ". Sending AGREE to " + msg.getSender().getLocalName());
                        } else {
                            reply.setPerformative(ACLMessage.REFUSE); 
                            reply.setContent("Action " + msg.getContent() + " no longer available for sale");
                            System.out.println(getLocalName() + ": Action " + msg.getContent() + " not available for sale. Sending REFUSE to " + msg.getSender().getLocalName());
                        }
                        
                        myAgent.send(reply);
                        if(pricestable.isEmpty()) {
                        	myAgent.doDelete();
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
        double base = 5 + random.nextDouble() * 195; 
        double reflexivityFactor = 1 + (random.nextGaussian() * 0.05); 
        base *= reflexivityFactor;
        if (base < 0.01) base = 0.01;
        return Math.round(base * 100.0) / 100.0;
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println(getLocalName() + ": Closing Stock Agent.");
    }
}