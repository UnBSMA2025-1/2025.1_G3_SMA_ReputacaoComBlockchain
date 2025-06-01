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
    // Ações que este Stock Agent gerencia e seus preços iniciais
    private List<String> availableActions = new ArrayList<>(Arrays.asList("BBDC3", "AZUL4", "PETR4", "VALE3")); // Adicione mais se quiser
    private Random random = new Random();

    protected void setup() {
        System.out.println(getLocalName() + ": Starting Stock Agent");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("stock-market"); // Broker procura por este serviço
        sd.setName(getLocalName() + "-StockMarketService"); // Nome do serviço mais único
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + ": Registered service '" + sd.getName() + "' of type '" + sd.getType() + "'");
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        // Inicializa preços para as ações disponíveis
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
            // Template para receber CFP ou REQUEST do Broker
            MessageTemplate mt = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.CFP),
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String actionName = msg.getContent(); // Nome da ação
                ACLMessage reply = msg.createReply();
                reply.setInReplyTo(msg.getReplyWith()); // ESSENCIAL para o Broker rastrear

                System.out.println(getLocalName() + ": Received " + ACLMessage.getPerformative(msg.getPerformative()) +
                                   " from " + msg.getSender().getLocalName() +
                                   " for action: " + actionName +
                                   " (ConvID: " + msg.getConversationId() + ")");

                switch (msg.getPerformative()) {
                    case ACLMessage.CFP: // Broker pedindo cotação
                        if (pricestable.containsKey(actionName)) {
                            Double price = pricestable.get(actionName);
                            reply.setPerformative(ACLMessage.INFORM); // Stock INFORMA o preço ao Broker
                            reply.setContent(String.valueOf(price));
                            System.out.println(getLocalName() + ": Action " + actionName + " price is " + String.format("%.2f", price) + ". Sending INFORM to " + msg.getSender().getLocalName());
                        } else {
                            reply.setPerformative(ACLMessage.REFUSE);
                            reply.setContent("Action " + actionName + " unavailable");
                            System.out.println(getLocalName() + ": Action " + actionName + " unavailable. Sending REFUSE to " + msg.getSender().getLocalName());
                        }
                        myAgent.send(reply);
                        break;

                    case ACLMessage.REQUEST: // Broker pedindo para comprar a ação
                        // O Investor já decidiu o preço com o Broker. Aqui o Stock apenas confirma/recusa a venda.
                        if (pricestable.containsKey(actionName) && availableActions.contains(actionName)) {
                            // Simular a venda removendo a ação (ou diminuindo quantidade, se tivesse)
                            // pricestable.remove(actionName); // Não remover o preço, apenas a disponibilidade
                            availableActions.remove(actionName); // Marcar como vendida/indisponível

                            reply.setPerformative(ACLMessage.AGREE); // Stock CONCORDA com a venda
                            reply.setContent("Sale of " + actionName + " confirmed by stock");
                            System.out.println(getLocalName() + ": Confirmed sale of " + actionName + ". Sending AGREE to " + msg.getSender().getLocalName());
                             // Opcional: Regenerar preço para a próxima vez ou marcar como esgotado
                             // if (availableActions.isEmpty()) { // Exemplo de condição de parada
                             //    System.out.println(getLocalName() + ": All actions sold. Shutting down.");
                             //    myAgent.doDelete();
                             // }
                        } else {
                            reply.setPerformative(ACLMessage.REFUSE); // Stock RECUSA a venda
                            reply.setContent("Action " + actionName + " no longer available for sale");
                            System.out.println(getLocalName() + ": Action " + actionName + " not available for sale. Sending REFUSE to " + msg.getSender().getLocalName());
                        }
                        myAgent.send(reply);
                        break;

                    default:
                        // Não deveria chegar aqui por causa do MessageTemplate
                        System.out.println(getLocalName() + ": Received unexpected performative: " + ACLMessage.getPerformative(msg.getPerformative()));
                        block();
                        break;
                }
            } else {
                block();
            }
        }
    }

    private double generateStockPrices(String action) {
        double base = 5 + random.nextDouble() * 195; // 5.0 a 200.0
        // Pequena variação aleatória para simular flutuação
        double reflexivityFactor = 1 + (random.nextGaussian() * 0.05); // Variação de +/- alguns %
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