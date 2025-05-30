package StockMarket;

// Libraries
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.Random;

public class CentralBank extends Agent {

    private double currentInterestRate = 0.05; // 5%
    private Random random = new Random();

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " (Banco Central) iniciado. Taxa de juros atual: " + String.format("%.2f", (currentInterestRate * 100)) + "%");

        // Registra com o DF como um serviço 'central-bank'
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("central-bank");
        sd.setName("main-central-bank");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Comportamento para anunciar periodicamente mudanças na taxa de juros ou notícias econômicas
        addBehaviour(new TickerBehaviour(this, 10000) { // A cada 10 segundos
            @Override
            protected void onTick() {
                // Simula uma mudança aleatória na taxa de juros
                double change = (random.nextDouble() - 0.5) * 0.01; // -0.005 a +0.005
                currentInterestRate += change;
                if (currentInterestRate < 0.01) currentInterestRate = 0.01; // Taxa mínima
                if (currentInterestRate > 0.10) currentInterestRate = 0.10; // Taxa máxima

                String announcement = "INTEREST_RATE_UPDATE|" + String.format("%.2f", currentInterestRate * 100);
                System.out.println(getLocalName() + " (Banco Central) ANÚNCIO: " + announcement + "%");

                // Envia uma mensagem INFORM para todos os agentes (ou tipos específicos como corretoras/investidores)
                ACLMessage informMsg = new ACLMessage(ACLMessage.INFORM);
                // Descobrir todos os agentes para informar, ou direcionar serviços específicos
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                // Você poderia direcionar tipos de serviço específicos aqui, ex: "sale-of-share", "investor-service"
                // Para simplificar, vamos informar a todos
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    for (int i = 0; i < result.length; ++i) {
                        if (!result[i].getName().equals(myAgent.getAID())) { // Não envia para si mesmo
                            informMsg.addReceiver(result[i].getName());
                        }
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
                informMsg.setContent(announcement);
                send(informMsg);
            }
        });
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(getLocalName() + " (Banco Central) encerrando.");
    }

    // Você pode adicionar métodos aqui para serem chamados por outros agentes para consultas específicas,
    // ex: `getCurrentInterestRate()`, mas para simulação, anúncios periódicos são mais simples.
}
