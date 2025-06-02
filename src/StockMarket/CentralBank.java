package StockMarket;

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

    private double currentInterestRate = 0.05; 
    private Random random = new Random();

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " iniciado. Taxa de juros atual: " + String.format("%.2f", (currentInterestRate * 100)) + "%");

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

        // Anuncia mudanças na taxa de juros
        addBehaviour(new TickerBehaviour(this, 20000) { // A cada 20 segundos
            @Override
            protected void onTick() {
                
                double change = (random.nextDouble() - 0.5) * 0.01;
                currentInterestRate += change;
                if (currentInterestRate < 0.01) currentInterestRate = 0.01; // Taxa mínima
                if (currentInterestRate > 0.10) currentInterestRate = 0.10; // Taxa máxima

                String announcement = "INTEREST_RATE_UPDATE|" + String.format("%.2f", currentInterestRate * 100);
                System.out.println(getLocalName() + " (Banco Central) ANÚNCIO: " + announcement + "%");

                // Envia uma mensagem do tipo INFORM para todos os agentes
                ACLMessage informMsg = new ACLMessage(ACLMessage.INFORM);
                
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                // Poderia selecionar o que mandar e a quem mandar, mas até o mesmo somos generalistas
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
}
