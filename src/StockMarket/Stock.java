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
    private List<String> availableActions = new ArrayList<>(Arrays.asList("PETR3", "PETR4", "VALE3", "ITUB4", "BBDC4", "BBAS3", "ABEV3", "WEGE3", "PRIO3", "RADL3", "LREN3", "HAPV3", "SUZB3", "KLAB11", "VIVT3", "CYRE3", "GGBR4", "CMIG4", "ELET3", "ELET6", "ITSA4", "ENGI11", "EGIE3", "KLBN11", "MGLU3", "B3SA3", "BBSE3", "BPAC11", "CVCB3", "GOAU4", "FLRY3", "COGN3", "EZTC3", "SLCE3", "RAIL3", "HYPE3", "CSNA3", "USIM5", "MRVE3", "CPFE3", "TAEE11", "EQTL3", "CCRO3", "RDOR3", "SULA11", "IRBR3", "BRKM5", "QUAL3", "CXSE3", "PSSA3", "PCAR3", "KLBN4", "BBDC3", "ITUB3", "SANB11", "RENT3", "ALPA4", "NTCO3", "GMAT3", "AZUL4", "GOLL4", "EMBR3", "BEEF3", "BRFS3", "SEER3", "YDUQ3", "CRFB3", "MILS3", "CASH3", "LINX3", "LWSA3", "STBP3", "GRND3", "GRUP3", "VAMO3", "DASA3", "BLAU3", "ARZZ3", "CPLA3", "PARD3", "ENBR3", "CPLE6", "TIET11", "SBSP3", "SANEPAR4", "MRFG3", "HGTX3", "SOMM3", "OMGE3", "AERI3", "MBLY3", "LIGT3", "TOTS3", "CLSC4", "TGMA3", "VIVA3", "CEAB3", "BPAN4", "PINE4", "GETT3")); 
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