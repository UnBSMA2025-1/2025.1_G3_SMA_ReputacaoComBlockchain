package StockMarket;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class Investor extends Agent {

    //INVESTIDOR EXPERIENTE 

    private Map<String, Integer> portfolio = new HashMap<>();
    private double balance = 100000.0;
    private List<AID> brokers = new ArrayList<>();
    private Random random = new Random();

    // Rastreia subidas e quedas
    private Map<String, Integer> consecutiveRises = new HashMap<>();
    private Map<String, Integer> consecutiveFalls = new HashMap<>();

    // Ações que o investidor já possui
    private String[] ownedStocks = {
        "PETR4", "VALE3", "ITUB4", "BBDC4", "ABEV3",
        "WEGE3", "RENT3", "B3SA3", "BBAS3", "CMIG4",
        "BITH11"
    };

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " iniciado com R$ " + String.format("%.2f", balance));

        // Inicializa o portfólio com as ações e quantidades
        portfolio.put("PETR4", 161);
        portfolio.put("VALE3", 96);
        portfolio.put("ITUB4", 133);
        portfolio.put("BBDC4", 308);
        portfolio.put("ABEV3", 356);
        portfolio.put("WEGE3", 118);
        portfolio.put("RENT3", 116);
        portfolio.put("B3SA3", 358);
        portfolio.put("BBAS3", 213);
        portfolio.put("CMIG4", 461);

        portfolio.put("BITH11", 366);

        // Procura corretoras
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                findBrokers(); 
            }
        });

        // Comportamento de decisão de negociação a cada 10 segundos
        addBehaviour(new TickerBehaviour(this, 10000) {
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

        // Comportamento para receber e processar mensagens
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage message = receive();

                if (message != null) {
                    if (!brokers.contains(message.getSender())) {
                        System.out.println(getLocalName() + " - Mensagem ignorada de corretora desconhecida: " + message.getSender().getLocalName());
                        return; 
                    }
                    System.out.println(getLocalName() + " - recebeu mensagem: " + message.getContent() + " de " + message.getSender().getLocalName());
                    String content = message.getContent();
                    String[] parts = content.split("\\|");

                    if (parts[0].equals("BUY") || parts[0].equals("SELL")) { 
                    // Ex: "BUY|ATIVO|QUANTIDADE|PRECO"
                        String tradeType = parts[0];
                        String stock = parts[1];
                        int quantity = Integer.parseInt(parts[2]);
                        double price = Double.parseDouble(parts[3]);

                        if (tradeType.equals("BUY")) {
                            addStock(stock, quantity);
                            updateBalance(-(quantity * price));
                            System.out.println(getLocalName() + " - COMPRA de " + quantity + " " + stock + " a R$" + String.format("%.2f", price) + " concluída.");
                        } else if (tradeType.equals("SELL")) {
                            removeStock(stock, quantity);
                            updateBalance(quantity * price);
                            System.out.println(getLocalName() + " - VENDA de " + quantity + " " + stock + " a R$" + String.format("%.2f", price) + " concluída.");
                        }
                        // Reinicia as contagens de subidas/quedas para a ação após a transação
                        consecutiveRises.put(stock, 0);
                        consecutiveFalls.put(stock, 0);

                    } else if (message.getPerformative() == ACLMessage.AGREE) { // ordem aceita pela corretora
                        System.out.println(getLocalName() + " - Ordem aceita pela corretora: " + content);

                    } else if (message.getPerformative() == ACLMessage.REFUSE) { // ordem recusada pela corretora
                        System.out.println(getLocalName() + " - Ordem recusada pela corretora: " + content);
                        
                    } else if (parts[0].equals("PRICE_UPDATE")) { // "PRICE_UPDATE|STOCK|CURRENT_PRICE|PREVIOUS_PRICE"
                        String stock = parts[1];
                        double currentPrice = Double.parseDouble(parts[2]);
                        double previousPrice = Double.parseDouble(parts[3]);

                        // Atualiza as sequências de subidas e quedas
                        if (currentPrice > previousPrice) {
                            consecutiveRises.put(stock, consecutiveRises.getOrDefault(stock, 0) + 1);
                            consecutiveFalls.put(stock, 0); // Reseta as quedas
                        } else if (currentPrice < previousPrice) {
                            consecutiveFalls.put(stock, consecutiveFalls.getOrDefault(stock, 0) + 1);
                            consecutiveRises.put(stock, 0); // Reseta as subidas
                        } else { // Preço igual
                            consecutiveRises.put(stock, 0);
                            consecutiveFalls.put(stock, 0);
                        }
                    } else if (parts[0].equals("ORDER_QUEUED_ON_EXCHANGE")) {
                        System.out.println(getLocalName() + " - Ordem enfileirada na Bolsa de Valores: " + content);
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
            DFAgentDescription[] result = DFService.search(this, template);
            brokers.clear();
            for (int i = 0; i < result.length; ++i) {
                brokers.add(result[i].getName());
                System.out.println(getLocalName() + " - encontrou corretora: " + result[i].getName().getLocalName());
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
    
    private void makeBuyDecision() {
        if (brokers.isEmpty()) {
            System.out.println(getLocalName() + "- nenhuma corretora disponível para negociar.");
            return;
        }

        // Critério: só compra mais ações das que já possui
        List<String> currentOwnedStocks = new ArrayList<>(portfolio.keySet());
        if (currentOwnedStocks.isEmpty()) {
            System.out.println(getLocalName() + " - Não possui ações para comprar mais.");
            return;
        }

        String chosenStock = currentOwnedStocks.get(random.nextInt(currentOwnedStocks.size()));

        // Critério: só compra se a ação teve 5 subidas seguidas
        if (consecutiveRises.getOrDefault(chosenStock, 0) >= 5) {
            AID chosenBroker = brokers.get(random.nextInt(brokers.size()));
            int quantity = random.nextInt(10) + 1; // Quantidade aleatória

            ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
            message.addReceiver(chosenBroker);
            message.setReplyWith("investor-order-buy" + System.currentTimeMillis());
            String content = "BUY|" + chosenStock + "|" + quantity;
            message.setContent(content);

            System.out.println(getLocalName() + " - quer COMPRAR " + quantity + " de " + chosenStock + " da " + chosenBroker.getLocalName() + " (5 subidas seguidas).");
            send(message);
        }
    }

    private void makeSellDecision() {
        if (brokers.isEmpty()) {
            System.out.println(getLocalName() + "- nenhuma corretora disponível para negociar.");
            return;
        }

        // Critério: só vende uma ação se ela teve 10 quedas seguidas e se possui a ação
        List<String> sellableStocks = new ArrayList<>();
        for (String stock : portfolio.keySet()) {
            if (portfolio.get(stock) > 0 && consecutiveFalls.getOrDefault(stock, 0) >= 10) {
                sellableStocks.add(stock);
            }
        }

        String chosenStock = sellableStocks.get(random.nextInt(sellableStocks.size()));
        AID chosenBroker = brokers.get(random.nextInt(brokers.size()));
        int quantity = random.nextInt(portfolio.get(chosenStock)) + 1; // Vende uma quantidade que possui

        ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
        message.addReceiver(chosenBroker);
        message.setReplyWith("investor-order-sell" + System.currentTimeMillis());
        String content = "SELL|" + chosenStock + "|" + quantity;
        message.setContent(content);

        System.out.println(getLocalName() + " - quer VENDER " + quantity + " de " + chosenStock + " para " + chosenBroker.getLocalName() + " (10 quedas seguidas).");
        send(message);
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