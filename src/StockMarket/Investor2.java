package StockMarket;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription; // Descrição de agente para o Directory Facilitator (DF)
import jade.domain.FIPAAgentManagement.ServiceDescription; // Descrição de serviço prestado pelo agente para o DF

import java.util.*; // Utilitários gerais do java
import java.util.concurrent.ThreadLocalRandom; 

public class Investor2 extends Agent {

    //INVESTIDOR INICIANTE

    // Portfólio do investidor: Ação -> Quantidade, começa vazio.
    private Map<String, Integer> portfolio = new HashMap<>();

    private double balance = 5000.0;

    // Variável para armazenar a AID da MELHOR corretora (será a primeira encontrada)
    private AID selectedBrokerAID = null;

    // AID da corretora -> quantidade de investidores
    private Map<AID, Integer> brokerInvestorCounts;

    // Ações disponíveis para negociação , a bolsa atualiza a cada 5 seg
    private List<String> availableStocksList = new ArrayList<>(); 

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " iniciado com R$ " + String.format("%.2f", balance));

        // Inicializa o brokerInvestorCounts
        brokerInvestorCounts = new HashMap<>();
        // Exemplo de inicialização (remova ou ajuste conforme a lógica real de reputação)
        brokerInvestorCounts.put(new AID("CorretoraA", AID.ISLOCALNAME), 600);
        brokerInvestorCounts.put(new AID("CorretoraB", AID.ISLOCALNAME), 300);


        // Seleciona a melhor corretora
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                findTheBestBroker();
            }
        });

        // Simula a atualização das ações disponíveis da bolsa a cada 5 segundos
        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                
                updateAvailableStocks();
                System.out.println(getLocalName() + " - Ações disponíveis atualizadas: " + availableStocksList);
            }
        });

        // Decisão de negócio (a cada 15seg)
        addBehaviour(new TickerBehaviour(this, 15000) {
            @Override
            protected void onTick() {
                if (selectedBrokerAID == null) {
                    System.out.println(getLocalName() + " Nenhuma corretora selecionada ainda, tentando novamente...");
                    findTheBestBroker(); // Tenta encontrar novamente
                    return;
                }
                // makeSellDecision(); o investidor iniciante não têm ações para vender
                
                // Decisão de compra autônoma, fluxo : investidor -> corretora
                makeBuyDecision(); 
            }
        });

        // Comportamento para receber mensagens da corretora
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage message = receive();

                // Processa a mensagem APENAS se for da corretora escolhida
                if (message != null && selectedBrokerAID != null && message.getSender().equals(selectedBrokerAID)) {
                    System.out.println(getLocalName() + " - recebeu mensagem: " + message.getPerformative() + " | " + message.getContent() + " de " + message.getSender().getLocalName());
                    String content = message.getContent();
                    String[] parts = content.split("\\|");

                    // Se a mensagem for uma OFERTA
                    if (message.getPerformative() == ACLMessage.PROPOSE) {
                        // Ex: "BUY|ATIVO|QUANTIDADE|PRECO"
                        if (parts.length >= 4 && parts[0].equals("BUY")) { 
                            String stock = parts[1];
                            int quantity = Integer.parseInt(parts[2]);
                            double price = Double.parseDouble(parts[3]);

                            // Chama o método para decidir sobre a proposta recebida
                            makeBuyDecisionOFFER(stock, quantity, price);
                        } 
                    }
                    else if (message.getPerformative() == ACLMessage.AGREE) { // ordem aceita pela corretora
                        System.out.println(getLocalName() + " - Ordem aceita pela corretora: " + content);

                    } else if (message.getPerformative() == ACLMessage.REFUSE) { // ordem recusada pela corretora
                        System.out.println(getLocalName() + " - Ordem recusada pela corretora: " + content);

                    }
                } else {
                    block();
                }
            }
        });
    }

    // Método para simular a atualização das ações disponíveis
    private void updateAvailableStocks() {
        availableStocksList.clear();
        //FIX - As ações virão da bolsa
        String[] possibleStocks = {"PETR4", "VALE3", "ITUB4", "BBDC4", "MGLU3", "AMER3", "WEGE3", "PRIO3", "RENT3", "FLRY3", "EZTC3"};
        
        // Garante que não haja duplicatas
        availableStocksList = new ArrayList<>(new HashSet<>(availableStocksList));
    }


    // Seleciona a melhor corretora
    // Critério: mínimo de 500 investidores
    private void findTheBestBroker() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sale-of-share");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template); // Realiza a busca no DF

            int minInvestors = 500;
            selectedBrokerAID = null; // Reseta a corretora selecionada antes de uma nova busca

            if (result.length == 0) {
                System.out.println(getLocalName() + " - Nenhuma corretora encontrada no DF com o serviço 'sale-of-share'.");
                return;
            }

            for (int i = 0; i < result.length; ++i) {
                AID brokerAID = result[i].getName(); // Obtém o AID da corretora encontrada

                // Lógica para verificar a quantidade de investidores
                if (brokerInvestorCounts.containsKey(brokerAID)) { // Verifica se temos dados de investidores para esta corretora
                    int currentInvestorCount = brokerInvestorCounts.get(brokerAID);

                    if (currentInvestorCount >= minInvestors) { // Se a quantidade de investidores for igual ou maior que o mínimo
                        selectedBrokerAID = brokerAID; // Seleciona esta como a melhor corretora
                        System.out.println(getLocalName() + " - Melhor corretora selecionada: " + selectedBrokerAID.getLocalName() + " (" + currentInvestorCount + " investidores)");
                        break; // Como queremos apenas a PRIMEIRA válida
                    } else {
                        System.out.println(getLocalName() + " - Corretora com baixa reputação (investidores < " + minInvestors + "): " + brokerAID.getLocalName() + " (" + currentInvestorCount + " investidores), ignorada.");
                    }
                } else {
                    System.out.println(getLocalName() + " - Corretora (quantidade de investidores desconhecida): " + brokerAID.getLocalName() + ", ignorada.");
                }
            }

            if (selectedBrokerAID == null) {
                System.out.println(getLocalName() + " - Nenhuma corretora atendeu ao critério mínimo de " + minInvestors + " investidores.");
            }

        } catch (FIPAException fe) {
            fe.printStackTrace(); // Falha na busca no DF
        }
    }

    // Método para decidir se o investidor aceita a oferta/proposta da corretora
    private void makeBuyDecisionOFFER(String stock, int quantity, double price) {
        System.out.println(getLocalName() + " - Recebeu oferta/proposta para comprar " + quantity + " de " + stock + " por R$ " + String.format("%.2f", price) + " cada.");

        double maxInvestAllowed = balance * 0.10; // 10% 
        double totalCost = quantity * price;

        // Preço aceitável 
        boolean priceIsAcceptable = (price <= 500.0);

        if (totalCost <= maxInvestAllowed && priceIsAcceptable) {
            System.out.println(getLocalName() + " - ACEITOU a oferta/proposta para comprar " + quantity + " de " + stock + " por R$ " + String.format("%.2f", price) + " cada.");

            // Envia uma mensagem AGREE para a corretora 
            ACLMessage reply = new ACLMessage(ACLMessage.AGREE);
            reply.addReceiver(selectedBrokerAID);
            reply.setContent("CONFIRM_BUY|" + stock + "|" + quantity + "|" + String.format("%.2f", price)); // Conteúdo para confirmar a compra
            send(reply);

            // Atualiza o portfólio e o saldo
            addStock(stock, quantity);
            updateBalance(-totalCost);
        } 
    }


    // Método para decisão de compra 
    private void makeBuyDecision() {
        if (selectedBrokerAID == null) {
            System.out.println(getLocalName() + " - Não há corretora selecionada para negociar.");
            return;
        }
        if (availableStocksList.isEmpty()) {
            System.out.println(getLocalName() + " - Nenhuma ação disponível para comprar no momento.");
            return;
        }

        // A ação é escolhida de forma aleatória
        String chosenStock = availableStocksList.get(ThreadLocalRandom.current().nextInt(availableStocksList.size()));

        // O investidor está disposto a pagar no máximo 10% de seu saldo
        double desiredPrice = 500.0 ;

        //Calcula a quantidade de ações 
        //FIX
        int quantity = (int) Math.floor(desiredPrice/chosenStock.price);

        if (quantity == 0) {
            System.out.println(getLocalName() + " - Saldo insuficiente para comprar " + chosenStock + " no preço desejado de R$" + String.format("%.2f", desiredPrice) + " (max 10% do saldo).");
            return;
        }

        // Cria uma mensagem ACL para a corretora
        ACLMessage message = new ACLMessage(ACLMessage.CFP);
        message.addReceiver(selectedBrokerAID);
        message.setReplyWith("investor-cfp-buy" + System.currentTimeMillis());

        // "BUY|ATIVO|QUANTIDADE_DESEJADA|PRECO_MAX_ACEITAVEL"
        String content = "BUY|" + chosenStock + "|" + quantity + "|" + String.format("%.2f", desiredPrice);
        message.setContent(content);

        System.out.println(getLocalName() + " - ENVIOU proposta para COMPRAR " + quantity + " de " + chosenStock + " com preço máximo de R$" + String.format("%.2f", desiredPrice) + " da " + selectedBrokerAID.getLocalName());

        
        send(message);
    }


    public void addStock(String stock, int quantity) {
        portfolio.put(stock, portfolio.getOrDefault(stock, 0) + quantity);
        System.out.println(getLocalName() + " portfólio atualizado: " + stock + " agora " + portfolio.get(stock) + " unidades.");
    }


    public void removeStock(String stock, int quantity) {
        if (portfolio.containsKey(stock)) {
            int currentQty = portfolio.get(stock);
            if (currentQty >= quantity) {
                portfolio.put(stock, currentQty - quantity);
                System.out.println(getLocalName() + " portfólio atualizado: " + stock + " agora " + portfolio.get(stock) + " unidades.");
            } else {
                System.out.println(getLocalName() + " Erro: Tentando remover mais ações do que possui para " + stock);
            }
        } else {
            System.out.println(getLocalName() + " Erro: Não possui a ação " + stock + " no portfólio para remover.");
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