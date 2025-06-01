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

public class Broker extends Agent {

    private AID stockAID; 
    private Random random = new Random();
    private int waitStock = 0;
 

    @Override
    protected void setup() {
        System.out.println(getLocalName() + ": Start broker");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("brokerage-services"); 
        sd.setName(getLocalName() + "-StockBroker"); 
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

  
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
	                    waitStock ++;
	                    if(waitStock > 3) {
	                       myAgent.doDelete(); 	                    	
	                    }	                         
	                }
	                else {
	                	stockAID = result[0].getName(); 
	                	waitStock = 0;
	                }
	            } catch (FIPAException fe) {
	                fe.printStackTrace();
	            }
	        }
	    });
        
 
        addBehaviour(new HandleInvestorRequestsServer());
    }

    private class HandleInvestorRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.CFP),
                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                if (stockAID == null) {
                    System.out.println(getLocalName() + ": Cannot process request, stock-market agent not available.");
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("Broker internal error: stock-market agent not found.");
                    myAgent.send(reply);
                    return;
                } 
                String requestedActionName = msg.getContent(); 
                switch (msg.getPerformative()) {
                    case ACLMessage.CFP:
                        ACLMessage cfpToStock = new ACLMessage(ACLMessage.CFP);
                        cfpToStock.addReceiver(stockAID);
                        cfpToStock.setConversationId("stock-price");
                        cfpToStock.setReplyWith("cfpStockReply" + System.nanoTime());
                        cfpToStock.setContent(msg.getContent()); 
                        myAgent.send(cfpToStock);
                        System.out.println(getLocalName() + ": CFP for " + msg.getContent() + " to " + stockAID);
                        
                        myAgent.addBehaviour(new PriceToInvestor(msg, cfpToStock));
                        break;

                    case ACLMessage.ACCEPT_PROPOSAL:                    
                        ACLMessage requestToStock = new ACLMessage(ACLMessage.REQUEST);
                        requestToStock.addReceiver(stockAID);
                        requestToStock.setConversationId("broker-stock-buy-request");
                        requestToStock.setReplyWith("buyStockReply" + System.nanoTime());
                        requestToStock.setContent(msg.getContent());
                        myAgent.send(requestToStock);
                        System.out.println(getLocalName() + ": Request action " + msg.getContent()); 
                    
                        myAgent.addBehaviour(new ForwardTradeOutcomeToInvestor( msg, requestToStock));
                        break;
                        
                    default:
                        break;
                }
            } else {
                block();
            }
        }
    }

    private class PriceToInvestor extends Behaviour {
        private ACLMessage originalInvestorCFP;
        private ACLMessage originalBrokerCFP;
        private boolean finished = false;
        private long startTime;

        public PriceToInvestor(ACLMessage investorCFP,ACLMessage brokerCFP) {
            this.originalInvestorCFP = investorCFP;
            this.originalBrokerCFP = brokerCFP;
            this.startTime = System.currentTimeMillis();
        }
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId(originalBrokerCFP.getConversationId()),
                MessageTemplate.MatchInReplyTo(originalBrokerCFP.getReplyWith())
            );
            ACLMessage stockResponse = myAgent.receive(mt);

            if (stockResponse != null) {
                ACLMessage replyToInvestor = originalInvestorCFP.createReply();
                replyToInvestor.setInReplyTo(originalInvestorCFP.getReplyWith()); 
                replyToInvestor.setConversationId(originalInvestorCFP.getConversationId()); 

                if (stockResponse.getPerformative() == ACLMessage.INFORM) { 
                    try {
                        double priceFromStock = Double.parseDouble(stockResponse.getContent());
                        double finalPrice = placeFee(priceFromStock); 

                        replyToInvestor.setPerformative(ACLMessage.PROPOSE);
                        replyToInvestor.setContent(String.valueOf(finalPrice));
                        replyToInvestor.setReplyWith("proposal" + System.nanoTime()); 
                        System.out.println(getLocalName() + " propose for " + originalInvestorCFP.getSender());
    
                    } catch (NumberFormatException e) {
                        System.err.println(getLocalName() + ": Error parsing price from stock: " + stockResponse.getContent());
                        replyToInvestor.setPerformative(ACLMessage.REFUSE);
                        replyToInvestor.setContent("Internal error reading price");
                    }
                } else { 
                    replyToInvestor.setPerformative(ACLMessage.REFUSE);
                    replyToInvestor.setContent(stockResponse.getContent()); 
                    System.out.println(getLocalName() + ": Stock REFUSED/FAILED price to " + originalInvestorCFP.getSender().getLocalName());
                }
                myAgent.send(replyToInvestor);
                finished = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
             if (finished) return true;
             if (System.currentTimeMillis() - startTime > Broker.BEHAVIOUR_TIMEOUT) { // Usando o timeout estático do Investor para consistência ou defina um no Broker
                 System.out.println(getLocalName() + ": Timeout waiting for price from Stock Agent for investor " + originalInvestorCFP.getSender().getLocalName());
                 ACLMessage replyToInvestor = originalInvestorCFP.createReply();
                 replyToInvestor.setPerformative(ACLMessage.REFUSE);
                 replyToInvestor.setContent("Timeout obtaining price information");
                 replyToInvestor.setInReplyTo(originalInvestorCFP.getReplyWith());
                 replyToInvestor.setConversationId(originalInvestorCFP.getConversationId());
                 myAgent.send(replyToInvestor);
                 finished = true; 
                 return true;
             }
             return false;
        }
    }
    
    private class ForwardTradeOutcomeToInvestor extends Behaviour {
        private ACLMessage originalInvestorAccept;
        private ACLMessage originalBrokerRequest;
        private boolean finished = false;
        private long startTime;

        public ForwardTradeOutcomeToInvestor(ACLMessage investorAccept,ACLMessage brokerRequest) {
            this.originalInvestorAccept = investorAccept;
            this.originalBrokerRequest = brokerRequest;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId(originalBrokerRequest.getConversationId()),
                MessageTemplate.MatchInReplyTo(originalBrokerRequest.getReplyWith())
            );
            ACLMessage stockResponse = myAgent.receive(mt);

            if (stockResponse != null) {
                ACLMessage replyToInvestor = originalInvestorAccept.createReply();
                replyToInvestor.setInReplyTo(originalInvestorAccept.getReplyWith());
                replyToInvestor.setConversationId(originalInvestorAccept.getConversationId());

                if (stockResponse.getPerformative() == ACLMessage.AGREE) { 
                    replyToInvestor.setPerformative(ACLMessage.INFORM); 
                    replyToInvestor.setContent("Transaction successful");
                    System.out.println(getLocalName() + ": Stock AGREED to trade. Relaying INFORM (success) to " + originalInvestorAccept.getSender().getLocalName());
                } else { 
                    replyToInvestor.setPerformative(ACLMessage.FAILURE); 
                    replyToInvestor.setContent("Transaction failed");
                     System.out.println(getLocalName() + ": Stock REFUSED/FAILED trade. Relaying FAILURE to " + originalInvestorAccept.getSender().getLocalName());
                }
                myAgent.send(replyToInvestor);
                finished = true;
            } else {
                block();
            }
        }
        @Override
        public boolean done() {
             if (finished) return true;
             if (System.currentTimeMillis() - startTime > Broker.BEHAVIOUR_TIMEOUT) {
                 System.out.println(getLocalName() + ": Timeout waiting for trade confirmation from Stock Agent for investor " + originalInvestorAccept.getSender().getLocalName());
                 ACLMessage replyToInvestor = originalInvestorAccept.createReply();
                 replyToInvestor.setPerformative(ACLMessage.FAILURE);
                 replyToInvestor.setContent("Timeout confirming trade");
                 replyToInvestor.setInReplyTo(originalInvestorAccept.getReplyWith());
                 replyToInvestor.setConversationId(originalInvestorAccept.getConversationId());
                 myAgent.send(replyToInvestor);
                 finished = true;
                 return true;
             }
             return false;
        }
    }


    private double placeFee(Double priceStock) {
        double transactionFeePercentage = 0.001 + random.nextDouble() * 0.004; 
        double finalPrice = priceStock * (1 + transactionFeePercentage);
        return Math.round(finalPrice * 100.0) / 100.0;
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println(getLocalName() + ": Closing broker.");
    }

    private static final long BEHAVIOUR_TIMEOUT = 7000; 
}