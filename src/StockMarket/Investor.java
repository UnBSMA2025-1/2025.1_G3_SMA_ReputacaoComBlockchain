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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Investor extends Agent {

    private List<AID> agentsBrokers = new ArrayList<>(); 
    private Random random = new Random();
    private int days = 0;
    private String[] actions = {"BBDC3","AZUL4","PETR4","VALE3"}; 
    private int noBroker = 0;
    
 
    private Map<AID, Double> brokerReputations = new HashMap<>();
    private static final double INITIAL_REPUTATION = 0.5; // Reputação inicial (0.0 a 1.0)
    private static final double LEARNING_RATE = 0.1;     // Quão rápido a reputação muda
    private static final double POSITIVE_EVENT = 1.0;    // Valor para evento positivo
    private static final double NEGATIVE_EVENT = 0.0;    // Valor para evento negativo
    private static final long BEHAVIOUR_TIMEOUT = 7000; // Timeout para esperar respostas (7 segundos)



    
    @Override
    protected void setup() {
        System.out.println(getLocalName() + ": Start Investor");

        addBehaviour(new TickerBehaviour(this, 10000) { 
            protected void onTick() {
                System.out.println(getLocalName() + ": ----------------- DAY " + days + "----------------------");
                days++;
                System.out.println(getLocalName() + ": Looking for brokers...");
                agentsBrokers.clear(); 
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("brokerage-services");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    for (DFAgentDescription dfd : result) {
                        agentsBrokers.add(dfd.getName());
                        brokerReputations.putIfAbsent(dfd.getName(), INITIAL_REPUTATION);
                        System.out.println(getLocalName() + ": Inicial reputation" + dfd.getName());
                    }
                    if (!agentsBrokers.isEmpty()) {  
                    	noBroker = 0;
                    	myAgent.addBehaviour(new RequestPrices()); 
                        
                    } else {
                        System.out.println(getLocalName() + ": No brokers found");
                        noBroker++;
                        if (noBroker >= 2) { 
                            myAgent.doDelete(); 
                        }
                    }
                } catch (FIPAException fe) {
                    System.err.println(getLocalName() + ": Error searching for brokers in DF: " + fe.getMessage());
                    fe.printStackTrace();
                }
            }
        });
    }
    private void updateReputation(AID brokerAID, double eventOutcome) {
        if (brokerAID == null) return;
        double currentReputation = brokerReputations.getOrDefault(brokerAID, INITIAL_REPUTATION);
        double newReputation = (1 - LEARNING_RATE) * currentReputation + LEARNING_RATE * eventOutcome;
        newReputation = Math.max(0.0, Math.min(1.0, newReputation)); 
        brokerReputations.put(brokerAID, newReputation);
    }
  
    private boolean decideToContactBroker(AID broker) {
        double reputation = brokerReputations.getOrDefault(broker, INITIAL_REPUTATION);
        // se a reputação da broker for boa, aumenta as chances 
        boolean decision = random.nextDouble() < (0.2 + reputation * 0.8);
        return decision;
    }


    private String selectAction() {
        int index = random.nextInt(actions.length);
        return actions[index];
    }

    private boolean decideToAcceptOffer(Double price) {
        boolean decision = price < (random.nextDouble() * 100 + 50); 
        return decision;
    }

    private class RequestPrices extends SequentialBehaviour {
        public RequestPrices() { 
            for (AID brokerAID : agentsBrokers) {
                addSubBehaviour(new OneShotBehaviour(myAgent) {
                    public void action() {
                        if (decideToContactBroker(brokerAID)) {
                            String action = selectAction();
                            ACLMessage cfpMsg = new ACLMessage(ACLMessage.CFP);
                            cfpMsg.addReceiver(brokerAID);
                            cfpMsg.setConversationId("stock-trade");
                            cfpMsg.setReplyWith("cfp-reply-" + System.nanoTime());
                            cfpMsg.setContent(action); 
                            myAgent.send(cfpMsg);
                            
                            System.out.println(getLocalName() + ": CFP for " + brokerAID + " action " + action);
                            
                            myAgent.addBehaviour(new ResponsePrice(brokerAID, action, cfpMsg));
                        } else {
                            System.out.println(getLocalName() + ": not buying " + brokerAID);
                        }
                    }
                });
            }
        }
        @Override
        public int onEnd() {
            System.out.println(getLocalName() + ": RequestPricesSequence finished sending all CFPs for this day.");
            return super.onEnd();
        }
    }

    private class ResponsePrice extends Behaviour {
        private AID brokerAID;
        private String Action;
        ACLMessage originalMsg;
        private boolean responseHandled = false;
        private long behaviourStartTime;

        public ResponsePrice(AID broker, String action, ACLMessage originalMsg) {
            this.brokerAID = broker;
            this.Action = action;
            this.originalMsg = originalMsg;
            this.behaviourStartTime = System.currentTimeMillis();
        }

        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId(originalMsg.getConversationId()),
                MessageTemplate.MatchInReplyTo(originalMsg.getReplyWith()) 
            );
            ACLMessage reply = myAgent.receive(mt);

            if (reply != null) {
                if (!reply.getSender().equals(brokerAID)) {
                     block(); 
                     return;
                }
                switch (reply.getPerformative()) {
                    case ACLMessage.PROPOSE:
                        try {
                            double price = Double.parseDouble(reply.getContent());
                            updateReputation(brokerAID, POSITIVE_EVENT); 

                            if (decideToAcceptOffer(price)) {
                                ACLMessage acceptMsg = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                                acceptMsg.addReceiver(brokerAID);
                                acceptMsg.setConversationId("accept-proposal");
                                acceptMsg.setInReplyTo(reply.getReplyWith()); 
                                acceptMsg.setReplyWith("accept-reply" + System.nanoTime());
                                acceptMsg.setContent(Action); 
                                myAgent.send(acceptMsg);
                                System.out.println(getLocalName() + ": accept propose " + " action " + Action);
                                myAgent.addBehaviour(new HandleTradeOutcome(brokerAID, acceptMsg));
                            } else {

                                ACLMessage rejectMsg = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                rejectMsg.addReceiver(brokerAID);
                                rejectMsg.setConversationId("reject-proposal"); 
                                rejectMsg.setInReplyTo(reply.getReplyWith());
                                rejectMsg.setContent("Price " + price + " for " + Action + " not accepted.");
                                myAgent.send(rejectMsg);
                                System.out.println(getLocalName() + ": Sent REJECT_PROPOSAL for " + Action + " to " + brokerAID.getLocalName());
                            }
                        } catch (NumberFormatException e) {
                            updateReputation(brokerAID, NEGATIVE_EVENT); 
                        }
                        break;

                    case ACLMessage.REFUSE:
                        updateReputation(brokerAID, NEGATIVE_EVENT); 
                        break;

                    default:
                        block();
                        return; 
                }
                responseHandled = true; 
            } else {
                block(); 
            }
        }

        @Override
        public boolean done() {
            if (responseHandled) {
                return true;
            }
            if (System.currentTimeMillis() - behaviourStartTime > BEHAVIOUR_TIMEOUT) {
                updateReputation(brokerAID, NEGATIVE_EVENT); 
                return true; 
            }
            return false;
        }
    }

    private class HandleTradeOutcome extends Behaviour {
        private AID brokerAID;
        private String tradedAction;
        private double agreedPrice;
        private ACLMessage originalInvestorAccept;
        private boolean outcomeHandled = false;
        private long behaviourStartTime;

        public HandleTradeOutcome(AID broker, ACLMessage InvestorAccept) {
            this.brokerAID = broker;
            this.originalInvestorAccept = InvestorAccept;
            this.behaviourStartTime = System.currentTimeMillis();
        }

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId(originalInvestorAccept.getConversationId()),
                MessageTemplate.MatchInReplyTo(originalInvestorAccept.getReplyWith())
            );
            ACLMessage reply = myAgent.receive(mt);

            if (reply != null) {
                 if (!reply.getSender().equals(brokerAID)) {
                     return;
                }
                switch (reply.getPerformative()) {
                    case ACLMessage.INFORM: 
                    	System.out.println("success");
                        updateReputation(brokerAID, POSITIVE_EVENT); 
                        break;

                    case ACLMessage.FAILURE: 
                        System.out.println("Failed");
                        updateReputation(brokerAID, NEGATIVE_EVENT); 
                        break;
                    
                    case ACLMessage.REFUSE: 
                    	System.out.println("Refuse");
                        updateReputation(brokerAID, NEGATIVE_EVENT);
                        break;

                    default:
                        block();
                        return;
                }
                outcomeHandled = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            if (outcomeHandled) {
                return true;
            }
            if (System.currentTimeMillis() - behaviourStartTime > BEHAVIOUR_TIMEOUT) {
                updateReputation(brokerAID, NEGATIVE_EVENT); 
                return true;
            }
            return false;
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this); 
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println(getLocalName() + ": Closing investor agent.");
    }
}