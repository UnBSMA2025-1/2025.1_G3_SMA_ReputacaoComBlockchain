package StockMarket;


import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

// Essa Array de AÇÕES pode ser uma importação de um arquivo independente String[] availableStocks = { }; 

// A corretora gerenciará ordens e portfólios de investidores.
public class Broker extends Agent {

    // AID da bolsa
    private AID stockExchangeAID;
    
    // Mapas - portfólios dos investidos da corretora // NomeInvestidor -> Ação -> Quantidade
    private Map<String, Map<String, Integer>> investorPortfolios = new HashMap<>(); 

    // Mapa - saldos dos investidores que usam esta corretora // NomeInvestidor -> Saldo
    private Map<String, Double> investorBalances = new HashMap<>(); 

    // Mapa para rastrear ordens pendentes
    private Map<String, InvestorOrderInfo> pendingInvestorOrders = new HashMap<>();


    @Override
    protected void setup() {
        System.out.println(getLocalName() + "- Corretora iniciada.");

        // Registra no DF como um serviço 'sale-of-share' -> venda de ação
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sale-of-share"); 
        sd.setName(getLocalName() + "-broker-service");
        dfd.addServices(sd);
        
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                findStockExchange();
            }
        });

        // Comportamento para lidar com mensagens recebidas (de Investidores ou da Bolsa de Valores)
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    String senderName = msg.getSender().getLocalName();
                    String content = msg.getContent();
                    System.out.println(getLocalName() + " - recebeu: '" + content + "' de " + senderName);

                    if (senderName.startsWith("Investor")) { // Mensagem de um Investidor
                        processInvestorOrder(msg);
                    } else if (senderName.startsWith("StockExchange")) { // Mensagem da Bolsa de Valores
                        processExchangeMessage(msg);
                    } else {
                        System.out.println(getLocalName() + " - recebeu mensagem inesperada de " + senderName);
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void findStockExchange() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("stock-exchange");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                stockExchangeAID = result[0].getName();
                System.out.println(getLocalName() + " - encontrou a Bolsa de Valores: " + stockExchangeAID.getLocalName());
            } else {
                System.out.println(getLocalName() + " - Bolsa de Valores não encontrada. Tentará novamente.");
                // Adiciona um comportamento para tentar encontrá-la novamente periodicamente
                addBehaviour(new OneShotBehaviour() {
                    @Override
                    public void action() {
                        try {
                            Thread.sleep(5000); // Espera 5 segundos antes de tentar novamente
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        findStockExchange();
                    }
                });
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private void processInvestorOrder(ACLMessage msg) {
        String investorName = msg.getSender().getLocalName();
        AID investorAID = msg.getSender();
        String content = msg.getContent();
        String originalReplyWith = msg.getReplyWith(); // rastrear a ordem
        String[] parts = content.split("\\|");
        
        if (parts.length == 3) {
            String orderType = parts[0];
            String stock = parts[1];
            int quantity = Integer.parseInt(parts[2]);

            // Inicializa portfólio e saldo do investidor se ainda não estiverem presentes
            investorPortfolios.putIfAbsent(investorName, new HashMap<>());

            // Saldo inicial para novos investidores ????
            investorBalances.putIfAbsent(investorName, 10000.0); 

            // Registra a ordem pendente do investidor
            pendingInvestorOrders.put(originalReplyWith, new InvestorOrderInfo(investorAID, orderType, stock, quantity));

            // Agora, obtém o preço atual da Bolsa de Valores para validar a ordem
            ACLMessage priceQuery = new ACLMessage(ACLMessage.REQUEST);
            priceQuery.addReceiver(stockExchangeAID);
            priceQuery.setContent("GET_PRICE|" + stock);
            priceQuery.setReplyWith("price-query-for-investor-order-" + originalReplyWith); // Associa à ordem do investidor
            send(priceQuery);

            // Adiciona um comportamento para esperar a resposta de preço da Bolsa
            addBehaviour(new OneShotBehaviour() {
                @Override
                public void action() {
                    ACLMessage priceReply = blockingReceive(ACLMessage.INFORM, 5000); // Espera pela resposta de preço
                    if (priceReply != null && priceReply.getContent().startsWith("PRICE")) {
                        double currentPrice = Double.parseDouble(priceReply.getContent().split("\\|")[2]);

                        if (orderType.equalsIgnoreCase("BUY")) {
                            double totalCost = currentPrice * quantity;
                            if (investorBalances.get(investorName) >= totalCost) {
                                // Encaminha para a Bolsa de Valores
                                ACLMessage orderMsg = new ACLMessage(ACLMessage.REQUEST); // REQUEST para uma ordem ser colocada
                                orderMsg.addReceiver(stockExchangeAID);
                                // Formato para a Bolsa de Valores: "ORDER|TIPO|AÇÃO|QUANTIDADE|PREÇO" (Corretora é a remetente)
                                orderMsg.setContent("ORDER|BUY|" + stock + "|" + quantity + "|" + String.format("%.2f", currentPrice));
                                orderMsg.setConversationId(originalReplyWith); // Usa o ID da conversa do investidor para rastreamento
                                send(orderMsg);
                                System.out.println(getLocalName() + " (Corretora) encaminhou ordem de COMPRA para " + stock + " para a Bolsa de Valores.");
                                // Deduz o valor imediatamente, ajusta depois se falhar
                                investorBalances.put(investorName, investorBalances.get(investorName) - totalCost);
                                sendReplyToInvestor(msg, ACLMessage.AGREE, "ORDER_RECEIVED|BUY|" + stock + "|" + quantity + "|ExpectedPrice:" + String.format("%.2f", currentPrice));
                            } else {
                                sendReplyToInvestor(msg, ACLMessage.REFUSE, "INSUFFICIENT_BALANCE|Required:" + String.format("%.2f", totalCost));
                                pendingInvestorOrders.remove(originalReplyWith); // Remove a ordem pendente
                            }
                        } else if (orderType.equalsIgnoreCase("SELL")) {
                            int currentStockQty = investorPortfolios.get(investorName).getOrDefault(stock, 0);
                            if (currentStockQty >= quantity) {
                                // Encaminha para a Bolsa de Valores
                                ACLMessage orderMsg = new ACLMessage(ACLMessage.REQUEST);
                                orderMsg.addReceiver(stockExchangeAID);
                                orderMsg.setContent("ORDER|SELL|" + stock + "|" + quantity + "|" + String.format("%.2f", currentPrice));
                                orderMsg.setConversationId(originalReplyWith); // Usa o ID da conversa do investidor para rastreamento
                                send(orderMsg);
                                System.out.println(getLocalName() + " (Corretora) encaminhou ordem de VENDA para " + stock + " para a Bolsa de Valores.");
                                // Remove do portfólio imediatamente, ajusta depois se falhar
                                investorPortfolios.get(investorName).put(stock, currentStockQty - quantity);
                                sendReplyToInvestor(msg, ACLMessage.AGREE, "ORDER_RECEIVED|SELL|" + stock + "|" + quantity + "|ExpectedPrice:" + String.format("%.2f", currentPrice));
                            } else {
                                sendReplyToInvestor(msg, ACLMessage.REFUSE, "INSUFFICIENT_STOCK|Available:" + currentStockQty);
                                pendingInvestorOrders.remove(originalReplyWith); // Remove a ordem pendente
                            }
                        }
                    } else {
                        System.out.println(getLocalName() + " (Corretora) Falha ao obter preço para " + stock + ". Não foi possível processar a ordem.");
                        sendReplyToInvestor(msg, ACLMessage.FAILURE, "PRICE_UNAVAILABLE");
                        pendingInvestorOrders.remove(originalReplyWith); // Remove a ordem pendente
                    }
                }
            });

        } else {
            sendReplyToInvestor(msg, ACLMessage.NOT_UNDERSTOOD, "INVALID_ORDER_FORMAT");
        }
    }

    private void processExchangeMessage(ACLMessage msg) {
        String content = msg.getContent();
        String[] parts = content.split("\\|");
        String conversationId = msg.getConversationId(); // Usado para rastrear a ordem original do investidor

        if (parts[0].equals("TRADE_CONFIRM") && parts.length == 6) {
            // Formato: "TRADE_CONFIRM|AÇÃO|QUANTIDADE|PREÇO|BROKER_COMPRADOR_AID|BROKER_VENDEDOR_AID"
            String stock = parts[1];
            int quantity = Integer.parseInt(parts[2]);
            double price = Double.parseDouble(parts[3]);
            String buyerBrokerName = parts[4];
            String sellerBrokerName = parts[5];

            // Verifica se esta corretora é a compradora ou vendedora nesta negociação
            if (buyerBrokerName.equals(getLocalName())) {
                // O investidor desta corretora comprou ações
                System.out.println(getLocalName() + " (Corretora) COMPRA Confirmada de " + quantity + " " + stock + " por R$" + String.format("%.2f", price));
                // Encontra a ordem pendente correspondente
                for (Map.Entry<String, InvestorOrderInfo> entry : pendingInvestorOrders.entrySet()) {
                    if (entry.getValue().getStock().equals(stock) && entry.getValue().getOrderType().equals("BUY")) {
                        // Encontramos uma ordem de compra pendente para esta ação.
                        // Em um sistema real, você precisaria de mais critérios (ex: preço, timestamp) para identificar a ordem exata.
                        // Para este exemplo, usaremos o primeiro match.
                        InvestorOrderInfo info = entry.getValue();
                        String investorName = info.getInvestorAID().getLocalName();

                        investorPortfolios.get(investorName).put(stock, investorPortfolios.get(investorName).getOrDefault(stock, 0) + quantity);
                        // O ajuste do saldo foi feito na Corretora ao encaminhar a ordem.
                        // Aqui, apenas confirma. Se o preço final for diferente, ajustaria.
                        // Para simplificar, assumimos que o preço usado para o cálculo inicial foi o preço de mercado na hora.
                        // Se o preço negociado (price) for diferente do 'currentPrice' que foi usado para deduzir o saldo,
                        // seria necessário um ajuste de saldo aqui. Por exemplo:
                        // double originalCost = info.getQuantity() * info.getPrice(); // Se a Corretora guardasse o preço original da ordem.
                        // double newCost = quantity * price;
                        // investorBalances.put(investorName, investorBalances.get(investorName) + (originalCost - newCost)); // Ajuste de troco/débito adicional.

                        // Remove a ordem pendente após a confirmação
                        pendingInvestorOrders.remove(entry.getKey());

                        // Notifica o investidor
                        ACLMessage investorConfirmMsg = new ACLMessage(ACLMessage.INFORM);
                        investorConfirmMsg.addReceiver(info.getInvestorAID());
                        investorConfirmMsg.setContent("TRADE_FILLED|BUY|" + stock + "|" + quantity + "|" + String.format("%.2f", price));
                        send(investorConfirmMsg);
                        return; // Processou a ordem, sai do loop
                    }
                }

            } else if (sellerBrokerName.equals(getLocalName())) {
                // O investidor desta corretora vendeu ações
                System.out.println(getLocalName() + " (Corretora) VENDA Confirmada de " + quantity + " " + stock + " por R$" + String.format("%.2f", price));
                for (Map.Entry<String, InvestorOrderInfo> entry : pendingInvestorOrders.entrySet()) {
                    if (entry.getValue().getStock().equals(stock) && entry.getValue().getOrderType().equals("SELL")) {
                        InvestorOrderInfo info = entry.getValue();
                        String investorName = info.getInvestorAID().getLocalName();

                        // O portfólio já foi atualizado quando a ordem foi enviada.
                        // Se o preço final for diferente do esperado, ajusta o saldo.
                        // double originalRevenue = info.getQuantity() * info.getPrice();
                        // double newRevenue = quantity * price;
                        // investorBalances.put(investorName, investorBalances.get(investorName) + (newRevenue - originalRevenue));

                        investorBalances.put(investorName, investorBalances.get(investorName) + (price * quantity)); // Adiciona ao saldo o valor da venda

                        // Remove a ordem pendente após a confirmação
                        pendingInvestorOrders.remove(entry.getKey());

                        // Notifica o investidor
                        ACLMessage investorConfirmMsg = new ACLMessage(ACLMessage.INFORM);
                        investorConfirmMsg.addReceiver(info.getInvestorAID());
                        investorConfirmMsg.setContent("TRADE_FILLED|SELL|" + stock + "|" + quantity + "|" + String.format("%.2f", price));
                        send(investorConfirmMsg);
                        return; // Processou a ordem, sai do loop
                    }
                }
            }
        } else if (parts[0].equals("ORDER_QUEUED") && parts.length == 4) {
            // "ORDER_QUEUED|AÇÃO|QUANTIDADE|PREÇO"
            String stock = parts[1];
            int quantity = Integer.parseInt(parts[2]);
            double price = Double.parseDouble(parts[3]);
            System.out.println(getLocalName() + " (Corretora) Ordem para " + quantity + " de " + stock + " por R$" + String.format("%.2f", price) + " foi enfileirada na bolsa.");

            // Encontrar a ordem pendente e notificar o investidor que a ordem foi enfileirada
            for (Map.Entry<String, InvestorOrderInfo> entry : pendingInvestorOrders.entrySet()) {
                if (entry.getValue().getStock().equals(stock) && entry.getValue().getQuantity() == quantity && entry.getValue().getPrice() == price) {
                    // Esta é uma correspondência muito simples. Em um ambiente real, você precisaria de um ID de ordem.
                    // Para simplificar, remove a ordem pendente, embora ela ainda esteja na bolsa.
                    // Uma abordagem melhor seria manter a ordem pendente, mas atualizar seu status.
                    InvestorOrderInfo info = entry.getValue();
                    ACLMessage investorQueuedMsg = new ACLMessage(ACLMessage.INFORM);
                    investorQueuedMsg.addReceiver(info.getInvestorAID());
                    investorQueuedMsg.setContent("ORDER_QUEUED_ON_EXCHANGE|" + stock + "|" + quantity + "|" + String.format("%.2f", price));
                    send(investorQueuedMsg);
                    return; // Processou a mensagem, sai do loop
                }
            }

        } else if (msg.getPerformative() == ACLMessage.REJECT) {
            // A bolsa rejeitou uma ordem
            System.out.println(getLocalName() + " (Corretora) Ordem rejeitada pela Bolsa: " + content);
            // Você precisaria reverter as mudanças de saldo/portfólio feitas quando a ordem foi inicialmente colocada
            // e informar o investidor sobre a rejeição.
            // O `conversationId` pode ser usado para encontrar a ordem `pendingInvestorOrders`.
            if (conversationId != null && pendingInvestorOrders.containsKey(conversationId)) {
                InvestorOrderInfo info = pendingInvestorOrders.get(conversationId);
                String investorName = info.getInvestorAID().getLocalName();

                // Reverter as mudanças (simplificado)
                if (info.getOrderType().equals("BUY")) {
                    investorBalances.put(investorName, investorBalances.get(investorName) + (info.getQuantity() * info.getPrice())); // Devolve o dinheiro
                } else if (info.getOrderType().equals("SELL")) {
                    investorPortfolios.get(investorName).put(info.getStock(), investorPortfolios.get(investorName).getOrDefault(info.getStock(), 0) + info.getQuantity()); // Devolve as ações
                }
                sendReplyToInvestor(new ACLMessage(ACLMessage.FAILURE), ACLMessage.FAILURE, "ORDER_REJECTED|" + content); // Notifica o investidor
                pendingInvestorOrders.remove(conversationId);
            }
        }
    }

    private void sendReplyToInvestor(ACLMessage originalInvestorMsg, int performative, String content) {
        ACLMessage reply = originalInvestorMsg.createReply(); // Cria uma resposta para a mensagem original do investidor
        reply.setPerformative(performative);
        reply.setContent(content);
        send(reply);
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println(getLocalName() + " (Corretora) encerrando.");
    }

    // Classe interna para armazenar informações da ordem do investidor enquanto ela está pendente
    private static class InvestorOrderInfo {
        AID investorAID;
        String orderType; // "BUY" ou "SELL"
        String stock;
        int quantity;
        double price; // Preço na hora que a ordem foi enviada para a Bolsa (para referência)

        public InvestorOrderInfo(AID investorAID, String orderType, String stock, int quantity) {
            this.investorAID = investorAID;
            this.orderType = orderType;
            this.stock = stock;
            this.quantity = quantity;
            this.price = 0.0; // Será preenchido quando o preço for obtido da bolsa
        }

        public AID getInvestorAID() { return investorAID; }
        public String getOrderType() { return orderType; }
        public String getStock() { return stock; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; } // Getter para o preço

        public void setPrice(double price) { this.price = price; } // Setter para o preço
    }
}
