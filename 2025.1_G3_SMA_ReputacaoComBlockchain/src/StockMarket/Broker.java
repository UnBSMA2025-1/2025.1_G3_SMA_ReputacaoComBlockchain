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

    private AID stockAgentAID; // Renomeado de 'stock' para clareza
    private Random random = new Random();
    // private boolean busy = false; // 'busy' pode ser complexo com múltiplos investidores,
                                 // por agora, cada WaitStock trata uma interação.

    @Override
    protected void setup() {
        System.out.println(getLocalName() + ": Start broker");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("brokerage-services"); // Investor procura por este serviço
        sd.setName(getLocalName() + "-StockBroker"); // Nome do serviço mais único
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + ": Registered service '" + sd.getName() + "' of type '" + sd.getType() + "'");
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        // Comportamento para encontrar o agente Stock
        addBehaviour(new TickerBehaviour(this, 3000) {
            boolean stockAgentFound = false;
            protected void onTick() {
                if (stockAgentFound) {
                    // Se já encontrou, verificar se ainda está ativo (opcionalmente)
                    // E parar este TickerBehaviour para não ficar procurando sempre
                    // this.stop(); // Para este exemplo, vamos deixar procurar, mas em um caso real, pararia.
                    return; // Ou this.stop(); se quiser procurar só uma vez.
                }
                System.out.println(getLocalName() + ": Looking for stock-market agent...");
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sdd = new ServiceDescription();
                sdd.setType("stock-market"); // Stock agent oferece este serviço
                template.addServices(sdd);

                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    if (result.length > 0) {
                        stockAgentAID = result[0].getName();
                        System.out.println(getLocalName() + ": Found stock-market agent: " + stockAgentAID.getLocalName());
                        stockAgentFound = true; // Marcar que encontrou
                        // Se quiser que este Ticker pare após encontrar:
                        // stop();
                    } else {
                        System.out.println(getLocalName() + ": stock-market agent not found yet...");
                        stockAgentAID = null; // Garantir que está nulo se não encontrar
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        });
        
        // Comportamento para lidar com requisições de investidores
        addBehaviour(new HandleInvestorRequestsServer());
    }

    private class HandleInvestorRequestsServer extends CyclicBehaviour {
        public void action() {
            // Template para receber CFP ou ACCEPT_PROPOSAL do Investor
            MessageTemplate mt = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.CFP),
                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                if (stockAgentAID == null) {
                    System.out.println(getLocalName() + ": Cannot process request, stock-market agent not available.");
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("Broker internal error: stock-market agent not found.");
                    myAgent.send(reply);
                    return;
                }

                String requestedActionName = msg.getContent(); // Pode precisar de parsing se ACCEPT_PROPOSAL
                
                System.out.println(getLocalName() + ": Received " + ACLMessage.getPerformative(msg.getPerformative()) +
                                   " from " + msg.getSender().getLocalName() +
                                   " for action/details: " + requestedActionName +
                                   " (ConvID: " + msg.getConversationId() + ")");

                switch (msg.getPerformative()) {
                    case ACLMessage.CFP:
                        // Investor está pedindo cotação de uma ação
                        ACLMessage cfpToStock = new ACLMessage(ACLMessage.CFP);
                        cfpToStock.addReceiver(stockAgentAID);
                        // Usar um novo ConversationID para a conversa com o StockAgent, ou propagar/adaptar
                        // Para simplificar, vamos usar um novo, mas manteremos o original do investor.
                        cfpToStock.setConversationId("broker-stock-price-query-" + System.nanoTime());
                        cfpToStock.setReplyWith("cfpStockReply" + System.nanoTime());
                        cfpToStock.setContent(requestedActionName); // Nome da ação
                        myAgent.send(cfpToStock);
                        System.out.println(getLocalName() + ": Forwarded CFP for " + requestedActionName + " to " + stockAgentAID.getLocalName());
                        // Adiciona comportamento para esperar a resposta do Stock e então responder ao Investor
                        myAgent.addBehaviour(new ForwardPriceToInvestor(myAgent, msg, cfpToStock.getConversationId(), cfpToStock.getReplyWith()));
                        break;

                    case ACLMessage.ACCEPT_PROPOSAL:
                        // Investor aceitou uma proposta e quer comprar
                        // O conteúdo de ACCEPT_PROPOSAL do Investor é "actionName:price"
                        String[] parts = requestedActionName.split(":");
                        String actionToBuy = parts[0];
                        // double priceAgreed = Double.parseDouble(parts[1]); // Preço acordado

                        ACLMessage requestToStock = new ACLMessage(ACLMessage.REQUEST);
                        requestToStock.addReceiver(stockAgentAID);
                        requestToStock.setConversationId("broker-stock-buy-request-" + System.nanoTime());
                        requestToStock.setReplyWith("buyStockReply" + System.nanoTime());
                        requestToStock.setContent(actionToBuy); // Ação a ser comprada
                        myAgent.send(requestToStock);
                        System.out.println(getLocalName() + ": Forwarded buy REQUEST for " + actionToBuy + " to " + stockAgentAID.getLocalName());
                        // Adiciona comportamento para esperar a resposta do Stock e então responder ao Investor
                        myAgent.addBehaviour(new ForwardTradeOutcomeToInvestor(myAgent, msg, requestToStock.getConversationId(), requestToStock.getReplyWith()));
                        break;
                        
                    default:
                        // Não deveria chegar aqui devido ao MessageTemplate
                        System.out.println(getLocalName() + ": Received unexpected performative: " + ACLMessage.getPerformative(msg.getPerformative()));
                        break;
                }
            } else {
                block();
            }
        }
    }

    private class ForwardPriceToInvestor extends Behaviour {
        private ACLMessage originalInvestorCFP;
        private String stockConvId;
        private String stockReplyWith;
        private boolean finished = false;
        private long startTime;

        public ForwardPriceToInvestor(Agent a, ACLMessage investorCFP, String stockConvId, String stockReplyWith) {
            super(a);
            this.originalInvestorCFP = investorCFP;
            this.stockConvId = stockConvId;
            this.stockReplyWith = stockReplyWith;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId(stockConvId),
                MessageTemplate.MatchInReplyTo(stockReplyWith)
                // MessageTemplate.MatchSender(stockAgentAID) // Adicionar se necessário
            );
            ACLMessage stockResponse = myAgent.receive(mt);

            if (stockResponse != null) {
                ACLMessage replyToInvestor = originalInvestorCFP.createReply();
                replyToInvestor.setInReplyTo(originalInvestorCFP.getReplyWith()); // Crucial!
                replyToInvestor.setConversationId(originalInvestorCFP.getConversationId()); // Manter a conv com Investor

                if (stockResponse.getPerformative() == ACLMessage.INFORM) { // Stock envia INFORM com preço
                    try {
                        double priceFromStock = Double.parseDouble(stockResponse.getContent());
                        double finalPrice = placeFee(priceFromStock); // Broker adiciona sua taxa

                        replyToInvestor.setPerformative(ACLMessage.PROPOSE); // Broker PROPÕE ao Investor
                        replyToInvestor.setContent(String.valueOf(finalPrice));
                        replyToInvestor.setReplyWith("proposal" + System.nanoTime()); // Broker define seu replyWith para a proposta
                        System.out.println(getLocalName() + ": Stock provided price " + priceFromStock +
                                           ". Proposing " + finalPrice + " to " + originalInvestorCFP.getSender().getLocalName());
                    } catch (NumberFormatException e) {
                        System.err.println(getLocalName() + ": Error parsing price from stock: " + stockResponse.getContent());
                        replyToInvestor.setPerformative(ACLMessage.REFUSE);
                        replyToInvestor.setContent("Internal error reading price");
                    }
                } else { // Stock recusou ou falhou (ex: action unavailable)
                    replyToInvestor.setPerformative(ACLMessage.REFUSE);
                    replyToInvestor.setContent(stockResponse.getContent()); // Repassa motivo da recusa
                    System.out.println(getLocalName() + ": Stock REFUSED/FAILED price query. Relaying REFUSE to " + originalInvestorCFP.getSender().getLocalName());
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
                 finished = true; // Termina para não ficar esperando indefinidamente
                 return true;
             }
             return false;
        }
    }
    
    private class ForwardTradeOutcomeToInvestor extends Behaviour {
        private ACLMessage originalInvestorAccept;
        private String stockConvId;
        private String stockReplyWith;
        private boolean finished = false;
        private long startTime;

        public ForwardTradeOutcomeToInvestor(Agent a, ACLMessage investorAccept, String stockConvId, String stockReplyWith) {
            super(a);
            this.originalInvestorAccept = investorAccept;
            this.stockConvId = stockConvId;
            this.stockReplyWith = stockReplyWith;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId(stockConvId),
                MessageTemplate.MatchInReplyTo(stockReplyWith)
            );
            ACLMessage stockResponse = myAgent.receive(mt);

            if (stockResponse != null) {
                ACLMessage replyToInvestor = originalInvestorAccept.createReply();
                replyToInvestor.setInReplyTo(originalInvestorAccept.getReplyWith()); // Crucial!
                replyToInvestor.setConversationId(originalInvestorAccept.getConversationId());

                if (stockResponse.getPerformative() == ACLMessage.AGREE) { // Stock AGREEs to the buy
                    replyToInvestor.setPerformative(ACLMessage.INFORM); // Broker INFORMs success to Investor
                    replyToInvestor.setContent("Transaction successful: " + originalInvestorAccept.getContent().split(":")[0]);
                    System.out.println(getLocalName() + ": Stock AGREED to trade. Relaying INFORM (success) to " + originalInvestorAccept.getSender().getLocalName());
                } else { // Stock REFUSED or FAILED the buy
                    replyToInvestor.setPerformative(ACLMessage.FAILURE); // Broker INFORMs failure to Investor
                    replyToInvestor.setContent("Transaction failed: " + stockResponse.getContent());
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
        double transactionFeePercentage = 0.001 + random.nextDouble() * 0.004; // Taxa entre 0.1% e 0.5%
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
     // Constante de timeout para os comportamentos do Broker
    // Pode ser a mesma do Investor ou diferente.
    // Para usar Investor.BEHAVIOUR_TIMEOUT, precisaria de acesso, ou defina uma aqui.
    private static final long BEHAVIOUR_TIMEOUT = 7000; 
}