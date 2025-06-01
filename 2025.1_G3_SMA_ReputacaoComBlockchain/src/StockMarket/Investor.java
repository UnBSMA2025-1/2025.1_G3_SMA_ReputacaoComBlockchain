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

    private List<AID> agentsBrokersFoundThisTick = new ArrayList<>(); // Renomeado para clareza
    private Random random = new Random();
    private int days = 0;
    private String[] actions = {"BBDC3", "AZUL4"}; // Ações que o investidor pode se interessar
    private int noBrokerStreak = 0; // Renomeado de count
    
    // --- REPUTAÇÃO: Atributos ---
    private Map<AID, Double> brokerReputations = new HashMap<>();
    private static final double INITIAL_REPUTATION = 0.5; // Reputação inicial (0.0 a 1.0)
    private static final double LEARNING_RATE = 0.1;     // Quão rápido a reputação muda
    private static final double POSITIVE_EVENT = 1.0;    // Valor para evento positivo
    private static final double NEGATIVE_EVENT = 0.0;    // Valor para evento negativo
    private static final long BEHAVIOUR_TIMEOUT = 7000; // Timeout para esperar respostas (7 segundos)
    // --- FIM REPUTAÇÃO ---


    
    @Override
    protected void setup() {
        System.out.println(getLocalName() + ": Start Investor");

        addBehaviour(new TickerBehaviour(this, 10000) { // Procura brokers a cada 10 segundos
            protected void onTick() {
                System.out.println(getLocalName() + ": ----------------- DAY " + days + "----------------------");
                days++;
                System.out.println(getLocalName() + ": Looking for brokers...");
                agentsBrokersFoundThisTick.clear(); // Limpa a lista de brokers da rodada anterior

                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("brokerage-services"); // Tipo de serviço que os corretores oferecem
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    System.out.println(getLocalName() + ": Found " + result.length + " potential brokerage services.");
                    for (DFAgentDescription dfd : result) {
                        AID brokerAID = dfd.getName();
                        agentsBrokersFoundThisTick.add(brokerAID);
                        // --- REPUTAÇÃO: Inicializar se for novo ---
                        brokerReputations.putIfAbsent(brokerAID, INITIAL_REPUTATION);
                        System.out.println(getLocalName() + ": Identified Broker: " + brokerAID.getLocalName() +
                                           " (Current Rep: " + String.format("%.2f", brokerReputations.get(brokerAID)) + ")");
                        // --- FIM REPUTAÇÃO ---
                    }

                    if (!agentsBrokersFoundThisTick.isEmpty()) {
                        
                    	myAgent.addBehaviour(new RequestPricesSequence()); // Inicia a sequência de requisição de preços
                        noBrokerStreak = 0; // Reseta a contagem se encontrar brokers
                        
                    } else {
                        System.out.println(getLocalName() + ": No brokers found offering 'brokerage-services' this tick.");
                        noBrokerStreak++;
                        if (noBrokerStreak >= 2) { // Se não encontrar brokers por 2 "dias" seguidos
                            System.out.println(getLocalName() + ": No brokers found for " + noBrokerStreak +
                                               " consecutive ticks. Shutting down.");
                            myAgent.doDelete(); // Encerra o agente
                        }
                    }
                } catch (FIPAException fe) {
                    System.err.println(getLocalName() + ": Error searching for brokers in DF: " + fe.getMessage());
                    fe.printStackTrace();
                }
            }
        });
    }

    // --- REPUTAÇÃO: Método para atualizar reputação ---
    private void updateReputation(AID brokerAID, double eventOutcome) {
        if (brokerAID == null) return;
        double currentReputation = brokerReputations.getOrDefault(brokerAID, INITIAL_REPUTATION);
        double newReputation = (1 - LEARNING_RATE) * currentReputation + LEARNING_RATE * eventOutcome;
        newReputation = Math.max(0.0, Math.min(1.0, newReputation)); // Garante 0.0 <= rep <= 1.0
        brokerReputations.put(brokerAID, newReputation);
        System.out.println(getLocalName() + ": Updated reputation for " + brokerAID.getLocalName() +
                           " to " + String.format("%.2f", newReputation) +
                           (eventOutcome == POSITIVE_EVENT ? " (Positive Event)" : " (Negative Event)"));
    }
    // --- FIM REPUTAÇÃO ---

    // --- REPUTAÇÃO: Modificado para usar reputação ---
    private boolean decideToContactBroker(AID broker) {
        double reputation = brokerReputations.getOrDefault(broker, INITIAL_REPUTATION);
        // Ex: Chance base de 20% + 80% ponderado pela reputação
        // Se rep = 0.5, chance = 0.2 + 0.4 = 0.6 (60%)
        // Se rep = 1.0, chance = 0.2 + 0.8 = 1.0 (100%)
        // Se rep = 0.0, chance = 0.2 + 0.0 = 0.2 (20%)
        boolean decision = random.nextDouble() < (0.2 + reputation * 0.8);
        System.out.println(getLocalName() + ": Deciding whether to contact broker " + broker.getLocalName() +
                           " (Rep: " + String.format("%.2f", reputation) + "). Decision: " + (decision ? "Yes" : "No"));
        return decision;
    }
    // --- FIM REPUTAÇÃO ---

    private String selectActionToQuery() {
        int index = random.nextInt(actions.length);
        return actions[index];
    }

    private boolean decideToAcceptOffer(Double price) {
        // Lógica de decisão de compra baseada no preço (pode ser mais complexa)
        // Exemplo: comprar se o preço for menor que um valor aleatório ou um limite
        boolean decision = price < (random.nextDouble() * 100 + 50); // Exemplo: preço < (50 a 150)
        System.out.println(getLocalName() + ": Price offered " + String.format("%.2f", price) + ". Decision to accept: " + decision);
        return decision;
    }

    // Comportamento sequencial para enviar CFPs
    private class RequestPricesSequence extends SequentialBehaviour {
        public RequestPricesSequence() {
            super(getAgent()); // Construtor do SequentialBehaviour
            // Itera sobre uma cópia da lista para evitar ConcurrentModificationException
            List<AID> brokersToContactThisRound = new ArrayList<>(agentsBrokersFoundThisTick);

            for (AID brokerAID : brokersToContactThisRound) {
                // Adiciona um OneShotBehaviour para cada broker que será contatado
                addSubBehaviour(new OneShotBehaviour(myAgent) {
                    private final AID currentBroker = brokerAID; // Captura o broker para este OneShot

                    public void action() {
                        if (decideToContactBroker(currentBroker)) {
                            String actionToQuery = selectActionToQuery();
                            String conversationId = "cfp-stock-" + actionToQuery + "-" + System.nanoTime();
                            String replyWith = "cfp-reply-" + System.nanoTime();

                            ACLMessage cfpMsg = new ACLMessage(ACLMessage.CFP);
                            cfpMsg.addReceiver(currentBroker);
                            cfpMsg.setLanguage("English"); // Opcional: especificar linguagem
                            cfpMsg.setOntology("stock-market-ontology"); // Opcional: especificar ontologia
                            cfpMsg.setConversationId(conversationId);
                            cfpMsg.setReplyWith(replyWith);
                            cfpMsg.setContent(actionToQuery); // Ação pela qual está pedindo cotação

                            myAgent.send(cfpMsg);
                            System.out.println(getLocalName() + ": Sent CFP for " + actionToQuery +
                                               " to " + currentBroker.getLocalName() + " (ConvID: " + conversationId + ")");

                            // Adiciona comportamento para esperar a proposta (resposta ao CFP)
                            myAgent.addBehaviour(new HandleProposalResponse(currentBroker, actionToQuery, conversationId, replyWith));
                        } else {
                            // Não fazer nada ou registrar que não contatou devido à baixa reputação/decisão
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

    // Comportamento para lidar com a resposta ao CFP (PROPOSE ou REFUSE)
    private class HandleProposalResponse extends Behaviour {
        private AID brokerAID;
        private String queriedAction;
        private String originalConvId;
        private String originalReplyWith; // Para o MessageTemplate
        private boolean responseHandled = false;
        private long behaviourStartTime;

        public HandleProposalResponse(AID broker, String action, String convId, String replyWith) {
            super(myAgent);
            this.brokerAID = broker;
            this.queriedAction = action;
            this.originalConvId = convId;
            this.originalReplyWith = replyWith;
            this.behaviourStartTime = System.currentTimeMillis();
        }

        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId(originalConvId),
                MessageTemplate.MatchInReplyTo(originalReplyWith) // Espera que o broker use InReplyTo
                // MessageTemplate.MatchSender(brokerAID) // Opcional, se InReplyTo for confiável
            );
            ACLMessage reply = myAgent.receive(mt);

            if (reply != null) {
                if (!reply.getSender().equals(brokerAID)) {
                     System.out.println(getLocalName() + ": Received message from unexpected sender " + reply.getSender().getLocalName() + " for CFP " + originalConvId + ". Ignoring.");
                     block(); // Ignora e continua esperando
                     return;
                }
                System.out.println(getLocalName() + ": Received reply from " + brokerAID.getLocalName() +
                                   " for action " + queriedAction + " (Performative: " + ACLMessage.getPerformative(reply.getPerformative()) + ")");

                switch (reply.getPerformative()) {
                    case ACLMessage.PROPOSE:
                        try {
                            double price = Double.parseDouble(reply.getContent());
                            System.out.println(getLocalName() + ": Broker " + brokerAID.getLocalName() +
                                               " PROPOSED price " + String.format("%.2f", price) + " for " + queriedAction);
                            updateReputation(brokerAID, POSITIVE_EVENT); // Broker respondeu positivamente ao CFP

                            if (decideToAcceptOffer(price)) {
                                String acceptConvId = "accept-proposal-" + queriedAction + "-" + System.nanoTime();
                                String acceptReplyWith = "accept-reply-" + System.nanoTime();

                                ACLMessage acceptMsg = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                                acceptMsg.addReceiver(brokerAID);
                                acceptMsg.setLanguage("English");
                                acceptMsg.setOntology("stock-market-ontology");
                                acceptMsg.setConversationId(acceptConvId);
                                acceptMsg.setInReplyTo(reply.getReplyWith()); // Referencia a proposta do broker
                                acceptMsg.setReplyWith(acceptReplyWith);
                                acceptMsg.setContent(queriedAction + ":" + price); // Confirma ação e preço

                                myAgent.send(acceptMsg);
                                System.out.println(getLocalName() + ": Sent ACCEPT_PROPOSAL for " + queriedAction +
                                                   " at " + String.format("%.2f", price) + " to " + brokerAID.getLocalName());
                                // Adiciona comportamento para esperar o resultado da transação
                                myAgent.addBehaviour(new HandleTradeOutcome(brokerAID, queriedAction, price, acceptConvId, acceptReplyWith));
                            } else {
                                // Enviar REJECT_PROPOSAL
                                String rejectConvId = "reject-proposal-" + queriedAction + "-" + System.nanoTime();
                                ACLMessage rejectMsg = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                rejectMsg.addReceiver(brokerAID);
                                rejectMsg.setLanguage("English");
                                rejectMsg.setOntology("stock-market-ontology");
                                rejectMsg.setConversationId(rejectConvId); // Pode ser um novo ID ou o mesmo do CFP
                                rejectMsg.setInReplyTo(reply.getReplyWith());
                                rejectMsg.setContent("Price " + price + " for " + queriedAction + " not accepted.");
                                myAgent.send(rejectMsg);
                                System.out.println(getLocalName() + ": Sent REJECT_PROPOSAL for " + queriedAction + " to " + brokerAID.getLocalName());
                            }
                        } catch (NumberFormatException e) {
                            System.err.println(getLocalName() + ": Error parsing price from " + brokerAID.getLocalName() +
                                               ". Content: " + reply.getContent());
                            updateReputation(brokerAID, NEGATIVE_EVENT); // Resposta mal formatada
                        }
                        break;

                    case ACLMessage.REFUSE:
                        System.out.println(getLocalName() + ": Broker " + brokerAID.getLocalName() +
                                           " REFUSED CFP for " + queriedAction);
                        updateReputation(brokerAID, NEGATIVE_EVENT); // Broker recusou explicitamente
                        break;

                    default:
                        System.out.println(getLocalName() + ": Received unexpected performative (" +
                                           ACLMessage.getPerformative(reply.getPerformative()) + ") from " +
                                           brokerAID.getLocalName() + " in response to CFP for " + queriedAction);
                        // Não atualizar reputação aqui, pode ser um erro de protocolo, esperar timeout
                        block(); // Continua esperando uma resposta válida ou timeout
                        return; // Não marca como done
                }
                responseHandled = true; // Marca como feito após processar uma resposta válida ao CFP
            } else {
                block(); // Continua esperando
            }
        }

        @Override
        public boolean done() {
            if (responseHandled) {
                return true;
            }
            if (System.currentTimeMillis() - behaviourStartTime > BEHAVIOUR_TIMEOUT) {
                System.out.println(getLocalName() + ": Timeout waiting for PROPOSAL/REFUSE from " +
                                   brokerAID.getLocalName() + " for " + queriedAction + " (ConvID: " + originalConvId + ")");
                updateReputation(brokerAID, NEGATIVE_EVENT); // Penaliza por não responder ao CFP
                return true; // Termina o comportamento devido ao timeout
            }
            return false;
        }
    }

    // Comportamento para lidar com o resultado da transação (INFORM ou FAILURE)
    private class HandleTradeOutcome extends Behaviour {
        private AID brokerAID;
        private String tradedAction;
        private double agreedPrice;
        private String originalConvId; // Conversation ID do ACCEPT_PROPOSAL
        private String originalReplyWith; // ReplyWith do ACCEPT_PROPOSAL
        private boolean outcomeHandled = false;
        private long behaviourStartTime;

        public HandleTradeOutcome(AID broker, String action, double price, String convId, String replyWith) {
            super(myAgent);
            this.brokerAID = broker;
            this.tradedAction = action;
            this.agreedPrice = price;
            this.originalConvId = convId;
            this.originalReplyWith = replyWith;
            this.behaviourStartTime = System.currentTimeMillis();
        }

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId(originalConvId),
                MessageTemplate.MatchInReplyTo(originalReplyWith)
                // MessageTemplate.MatchSender(brokerAID)
            );
            ACLMessage reply = myAgent.receive(mt);

            if (reply != null) {
                 if (!reply.getSender().equals(brokerAID)) {
                     System.out.println(getLocalName() + ": Received message from unexpected sender " + reply.getSender().getLocalName() + " for trade outcome " + originalConvId + ". Ignoring.");
                     block();
                     return;
                }
                System.out.println(getLocalName() + ": Received trade outcome from " + brokerAID.getLocalName() +
                                   " for action " + tradedAction + " (Performative: " + ACLMessage.getPerformative(reply.getPerformative()) + ")");

                switch (reply.getPerformative()) {
                    case ACLMessage.INFORM: // Broker confirma que a transação foi bem-sucedida
                        System.out.println(getLocalName() + ": Successfully bought " + tradedAction +
                                           " from " + brokerAID.getLocalName() + " at " + String.format("%.2f", agreedPrice) +
                                           ". Broker says: " + reply.getContent());
                        updateReputation(brokerAID, POSITIVE_EVENT); // Transação bem-sucedida
                        break;

                    case ACLMessage.FAILURE: // Broker informa que a transação falhou
                        System.out.println(getLocalName() + ": Failed to buy " + tradedAction +
                                           " from " + brokerAID.getLocalName() + ". Broker says: " + reply.getContent());
                        updateReputation(brokerAID, NEGATIVE_EVENT); // Transação falhou
                        break;
                    
                    case ACLMessage.REFUSE: // Broker explicitamente recusa a transação final
                         System.out.println(getLocalName() + ": Broker " + brokerAID.getLocalName() +
                                           " REFUSED to complete trade for " + tradedAction + ". Broker says: " + reply.getContent());
                        updateReputation(brokerAID, NEGATIVE_EVENT); // Transação falhou
                        break;

                    default:
                        System.out.println(getLocalName() + ": Received unexpected performative (" +
                                           ACLMessage.getPerformative(reply.getPerformative()) + ") from " +
                                           brokerAID.getLocalName() + " as trade outcome for " + tradedAction);
                        // Não atualizar reputação aqui, pode ser um erro de protocolo, esperar timeout
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
                System.out.println(getLocalName() + ": Timeout waiting for trade outcome (INFORM/FAILURE) from " +
                                   brokerAID.getLocalName() + " for " + tradedAction + " (ConvID: " + originalConvId + ")");
                updateReputation(brokerAID, NEGATIVE_EVENT); // Penaliza por não confirmar/falhar a transação
                return true;
            }
            return false;
        }
    }

    @Override
    protected void takeDown() {
        System.out.println(getLocalName() + ": Final broker reputations at shutdown:");
        if (brokerReputations.isEmpty()) {
            System.out.println(getLocalName() + ": No reputation data recorded.");
        } else {
            for (Map.Entry<AID, Double> entry : brokerReputations.entrySet()) {
                System.out.println("  Broker: " + entry.getKey().getLocalName() + ", Reputation: " + String.format("%.2f", entry.getValue()));
            }
        }

        try {
            DFService.deregister(this); // Desregistrar do DF ao encerrar
            System.out.println(getLocalName() + ": Deregistered from DF.");
        } catch (FIPAException e) {
            System.err.println(getLocalName() + ": Error deregistering from DF: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println(getLocalName() + ": Closing investor agent.");
    }
}