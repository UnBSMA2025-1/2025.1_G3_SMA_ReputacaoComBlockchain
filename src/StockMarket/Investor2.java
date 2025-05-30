package StockMarket;

import jade.core.Agent; // Classe - agente
import jade.core.AID; // Classe - ID do agente
import jade.core.behaviours.TickerBehaviour; // Classe - ações repetidas a cada X tempo
import jade.core.behaviours.OneShotBehaviour; // Classe - ações que acontecem uma única vez
import jade.lang.acl.ACLMessage; // Classe - comunicação entre agentes
import jade.domain.DFService; // Classe - registro e busca de serviços no DF
import jade.domain.FIPAException; // Para lidar com exceções do FIPA
import jade.domain.FIPAAgentManagement.DFAgentDescription; // Descrição de agente para o Directory Facilitator (DF)
import jade.domain.FIPAAgentManagement.ServiceDescription; // Descrição de serviço prestado pelo agente para o DF

import java.util.*; // Utilitários gerais do java
 
public class Investor extends Agent {

    private Map<String, Integer> portfolio = new HashMap<>(); 
	// Adiciona algumas ações iniciais ao portfólio para simular posses que podem ser vendidas
	portfolio.put("BBDC3", 10);
	portfolio.put("ABEV3", 5);
    private double balance = 10000.0; 
    // Lista - AIDs das corretoras conhecidas
    private List<AID> brokers = new ArrayList<>(); 

    @Override
    protected void setup() {

        System.out.println(getLocalName() + " iniciado com R$ " + String.format("%.2f", balance));

		//Primeira atitude
        addBehaviour(new OneShotBehaviour() { // Procura corretoras
            @Override
            public void action() {
                findBrokers();
            }
        });

        //Segunda atitude
        addBehaviour(new TickerBehaviour(this, 10000) { // Decisão de negociação a cada 10 seg.
            @Override
            protected void onTick() {
                if (brokers.isEmpty()) {
                    System.out.println(getLocalName() + " Nenhuma corretora encontrada ainda, tentando novamente...");
                    findBrokers(); 
                    return;
                }
                makeSellDecision();
                makeBuyDecision();
            }
        });

        //Terceira atitude
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage message = receive(); 

                if (message != null) {

                    System.out.println(getLocalName() + " - recebeu mensagem: " + message.getContent() + " de " + message.getSender().getLocalName());
                    String content = message.getContent();
                    String[] parts = content.split("\\|");

                    if (parts[0].equals("TRADE_FILLED")) { //"TRADE_FILLED|TYPE|STOCK|QUANTITY|PRICE"
                        String tradeType = parts[1]; 
                        String stock = parts[2];
                        int quantity = Integer.parseInt(parts[3]); 
                        double price = Double.parseDouble(parts[4]);

            
                    } else if (msg.getPerformative() == ACLMessage.AGREE) { // ordem aceita pela corretora
                        System.out.println(getLocalName() + " - Ordem aceita pela corretora: " + content);
                        // se a corretor aceita // atualiza saldo e portifolio

                    } else if (msg.getPerformative() == ACLMessage.REFUSE) { // ordem recusada pela corretora
                        System.out.println(getLocalName() + " - Ordem recusada pela corretora: " + content);
                        // Em um sistema mais robusto, aqui você reverteria alterações de saldo/portfólio
                        // que foram feitas preventivamente ao enviar a ordem.

                    } else if (parts[0].equals("ORDER_QUEUED_ON_EXCHANGE")) {
                        // Mensagem informando que a ordem foi enfileirada na bolsa (não imediatamente casada)
                        System.out.println(getLocalName() + " (Investidor) Ordem enfileirada na Bolsa de Valores: " + content);
                    }
                } else {
                    block();
                }
            }
        });
    }



    private void findBrokers() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sale-of-share");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template); // Realiza a busca
            brokers.clear();
            for (int i = 0; i < result.length; ++i) {
                brokers.add(result[i].getName()); // Adiciona o AID da corretora encontrada
                System.out.println(getLocalName() + " (Investidor) encontrou corretora: " + result[i].getName().getLocalName());
            }
        } catch (FIPAException fe) {
            fe.printStackTrace(); // Falha na busca no DF
        }
    }

    private void makeBuyDecision() {

        if (brokers.isEmpty()) {
            System.out.println(getLocalName() + "- nenhuma corretora disponível para negociar.");
            return;
        }

        // Escolhe uma corretora
        AID chosenBroker = brokers.get(random.nextInt(brokers.size()));

        // Escolhe uma ação 
        String chosenStock = availableStocks[random.nextInt(availableStocks.length)];

        // Escolhe a quantidade de ações
        int quantity = random.nextInt(10) + 1; 

        // Cria uma mensagem ACL do tipo REQUEST
        ACLMessage message = new ACLMessage(ACLMessage.REQUEST); 

        // Define a corretora escolhida como destinatário
        message.addReceiver(chosenBroker); 

        // Define um ID (único) para esta ordem
        message.setReplyWith("investor-order-buy" + System.currentTimeMillis()); 

        // Constrói o conteúdo da mensagem para uma ordem de COMPRA
        String content = "BUY|" + chosenStock + "|" + quantity;
        message.setContent(content);

        System.out.println(getLocalName() + " - quer COMPRAR " + quantity + " de " + chosenStock + " da " + chosenBroker.getLocalName());
        
        // Envia a mensagem para a corretora
        send(message); 

        //Adiciona o ID desta ordem na lista de ordens pendentes
        //
    }


    private void makeSellDecision() { 

        if (brokers.isEmpty()) {
            System.out.println(getLocalName() + "- nenhuma corretora disponível para negociar.");
            return;
        }

        //verifica se quer vender alguma ação e a quantidade que quer vender
        // se sim, continua, se não : block

        // Escolhe uma corretora
        AID chosenBroker = brokers.get(random.nextInt(brokers.size()));

        // Escolhe uma ação 
        String chosenStock = availableStocks[random.nextInt(availableStocks.length)];

        // Escolhe a quantidade de ações
        int quantity = random.nextInt(10) + 1;

        // Cria uma mensagem ACL do tipo REQUEST
        ACLMessage message = new ACLMessage(ACLMessage.REQUEST); 

        // Define a corretora escolhida como destinatário
        message.addReceiver(chosenBroker);
        
        // Define um ID (único) para esta ordem do investidor
        message.setReplyWith("investor-order-sell" + System.currentTimeMillis()); 

        
        // Constrói o conteúdo da mensagem para uma ordem de VENDA
        String content = "SELL|" + chosenStock + "|" + quantity;
        message.setContent(content);

        System.out.println(getLocalName() + " - quer VENDER " + quantity + " de " + chosenStock + " para " + chosenBroker.getLocalName());
        
        // Envia a mensagem para a corretora
        send(message); 

         //Adiciona o ID desta ordem na lista de ordens pendentes
        //

        }


    public void addStock(String stock, int quantity) {
        portfolio.put(stock, portfolio.getOrDefault(stock, 0) + quantity);
        System.out.println(getLocalName() + " portfólio atualizado: " + stock + " agora " + portfolio.get(stock));
    }

    public void removeStock(String stock, int quantity) {
        if (portfolio.containsKey(stock)) {
            int currentQty = portfolio.get(stock);
            if (currentQty >= quantity) {
                portfolio.put(stock, currentQty - quantity);
                System.out.println(getLocalName() + " portfólio atualizado: " + stock + " agora " + portfolio.get(stock));
            } else {
                System.out.println(getLocalName() + " Erro: Tentando remover mais ações do que possui para " + stock);
            }
        }
    }


    public void updateBalance(double amount) {
        this.balance += amount;
        System.out.println(getLocalName() + " - saldo atualizado para R$ " + String.format("%.2f", balance));
    }

    @Override
    protected void takeDown() {
        System.out.println(getLocalName() + " - encerrando.");
    }

}