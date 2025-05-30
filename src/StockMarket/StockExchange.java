package StockMarket;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class StockExchange extends Agent {

    // Livro de Ofertas para COMPRA:
    private Map<String, TreeMap<Double, List<Order>>> buyOrders = new HashMap<>(); 
    // Livro de Ofertas para VENDA: 
    private Map<String, TreeMap<Double, List<Order>>> sellOrders = new HashMap<>(); 
    
    // Último preço negociado para cada ação. 
    private Map<String, Double> currentPrices = new HashMap<>(); 

    @Override
    protected void setup() {
        System.out.println(getLocalName() + "- iniciado.");

        // Inicializa ações e seus preços baseados no modelo realista
        String[] actions = {"BBDC3", "AZUL4", "CRFB3", "ABEV3"};

        // Também inicializa os TreeMaps para cada ação.
        // Tirar aleatório
        for (String action : actions) {
            // Preço inicial para simulação
            currentPrices.put(action, 50.0 + new Random().nextDouble() * 50.0); 
            // Ordens de compra: TreeMap com ordem reversa para que o maior preço venha primeiro
            buyOrders.put(action, new TreeMap<>(Collections.reverseOrder())); 
            // Ordens de venda: TreeMap com ordem natural (menor preço vem primeiro)
            sellOrders.put(action, new TreeMap<>()); 
        }

        // Registra o serviço da bolsa de valores com o Directory Facilitator (DF)
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID()); // Define o AID do próprio agente no DFDescription
        ServiceDescription sd = new ServiceDescription();
        sd.setType("stock-exchange"); // Tipo de serviço que a Bolsa oferece
        sd.setName("main-exchange"); // Nome específico desta instância de serviço
        dfd.addServices(sd); // Adiciona a descrição do serviço ao DFDescription
        try {
            DFService.register(this, dfd); // Tenta registrar no DF
        } catch (FIPAException fe) {
            fe.printStackTrace(); // Imprime o erro se o registro falhar
        }

        // Comportamento: processar mensagens recebidas (ordens das corretoras, consultas de preço)
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive(); 

                if (msg != null) { // Se uma mensagem foi recebida
                    String senderName = msg.getSender().getLocalName(); // Nome do remetente
                    String content = msg.getContent(); // Conteúdo da mensagem
                    System.out.println(getLocalName() + "recebeu: '" + content + "' de " + senderName);

                    String[] parts = content.split("\\|");

                    // Verifica se é ("ORDER|TIPO|AÇÃO|QUANTIDADE|PREÇO")
                    if (parts.length == 5 && parts[0].equals("ORDER")) {
                        try {
                            String orderType = parts[1]; // (BUY/SELL)
                            String stock = parts[2];     // Ticker da ação
                            int quantity = Integer.parseInt(parts[3]); // Quantidade
                            double price = Double.parseDouble(parts[4]); // Preço limite da ordem

                            // AID da corretora que enviou a ordem
                            AID brokerAID = msg.getSender(); 

                            // O ConversationID da ordem vem da Corretora, que por sua vez pegou do Investidor.
                            String conversationID = msg.getConversationId(); 

                            Order newOrder;
                            if (orderType.equalsIgnoreCase("BUY")) {
                                // Cria uma nova ordem de compra
                                newOrder = new Order(stock, quantity, price, brokerAID, true, conversationID);
                                // Processa a ordem usando os livros de compra e venda específicos para essa ação
                                processOrder(newOrder, buyOrders.get(stock), sellOrders.get(stock));
                            } else if (orderType.equalsIgnoreCase("SELL")) {
                                // Cria uma nova ordem de venda
                                newOrder = new Order(stock, quantity, price, brokerAID, false, conversationID);
                                // Processa a ordem usando os livros de venda e compra específicos para essa ação
                                processOrder(newOrder, sellOrders.get(stock), buyOrders.get(stock));
                            } else {
                                // Se o tipo de ordem for desconhecido
                                System.out.println(getLocalName() + " (Bolsa de Valores) Tipo de ordem desconhecido: " + orderType);
                                // Envia uma rejeição de volta para a corretora
                                sendReply(msg, ACLMessage.REJECT, "UNKNOWN_ORDER_TYPE", conversationID);
                            }
                        } catch (NumberFormatException e) {
                            // Erro de formato nos números da ordem
                            System.out.println(getLocalName() + " (Bolsa de Valores) Erro ao analisar conteúdo da ordem: " + content);
                            sendReply(msg, ACLMessage.REJECT, "INVALID_ORDER_FORMAT", msg.getConversationId());
                        }
                    // Verifica se é uma mensagem de CONSULTA DE PREÇO ("GET_PRICE|AÇÃO")
                    } else if (parts.length == 2 && parts[0].equals("GET_PRICE")) { 
                        String stock = parts[1]; // Ticker da ação a consultar
                        if (currentPrices.containsKey(stock)) {
                            // Se o preço existe, informa de volta com o preço atual
                            sendReply(msg, ACLMessage.INFORM, "PRICE|" + stock + "|" + String.format("%.2f", currentPrices.get(stock)), msg.getConversationId());
                        } else {
                            // Se a ação não for encontrada
                            sendReply(msg, ACLMessage.FAILURE, "STOCK_NOT_FOUND", msg.getConversationId());
                        }
                    } else {
                        // Se o formato da mensagem não for reconhecido
                        System.out.println(getLocalName() + " (Bolsa de Valores) Formato de mensagem não reconhecido: " + content);
                    }
                } else {
                    block(); 
                }
            }
        });
    }

    // Método para processar uma nova ordem e tentar casá-la com ordens existentes
    private void processOrder(Order newOrder, TreeMap<Double, List<Order>> ownBook, TreeMap<Double, List<Order>> counterBook) {
        // Percorre o livro oposto para tentar casar a nova ordem
        // O loop continua enquanto a nova ordem ainda tem quantidade a ser casada
        while (newOrder.getQuantity() > 0) {
            Double bestPrice = null;

            // Encontra o melhor preço no livro oposto para casamento
            if (counterBook.isEmpty()) {
                break; // Não há ordens opostas, não há casamento possível
            }

            // O TreeMap já mantém as ordens com o melhor preço no 'firstKey()'
            // Para buyOrders (ordenado decrescentemente), firstKey() é o maior preço de compra.
            // Para sellOrders (ordenado crescentemente), firstKey() é o menor preço de venda.
            bestPrice = counterBook.firstKey(); 
            List<Order> matchingOrders = counterBook.get(bestPrice);

            // Se não houver ordens para este preço (não deveria acontecer com firstKey(), mas por segurança)
            if (matchingOrders == null || matchingOrders.isEmpty()) {
                break; 
            }

            // A ordem existente com o melhor preço (FIFO: First-In, First-Out, pois está no início da lista)
            Order existingOrder = matchingOrders.get(0); 

            boolean canMatch = false;
            // Regras de casamento de mercado:
            // - Ordem de COMPRA só casa com VENDA se o preço de COMPRA >= preço de VENDA.
            // - Ordem de VENDA só casa com COMPRA se o preço de VENDA <= preço de COMPRA.
            if (newOrder.isBuy() && newOrder.getPrice() >= existingOrder.getPrice()) { 
                canMatch = true;
            } else if (!newOrder.isBuy() && newOrder.getPrice() <= existingOrder.getPrice()) { 
                canMatch = true;
            }

            if (canMatch) {
                // Encontrou um casamento!
                // A quantidade negociada é o mínimo entre a quantidade da nova ordem e da ordem existente.
                int tradedQuantity = Math.min(newOrder.getQuantity(), existingOrder.getQuantity());
                // O preço da negociação é o preço da ordem que já estava no livro (melhor preço).
                double tradedPrice = existingOrder.getPrice(); 

                System.out.println(getLocalName() + " (Bolsa de Valores) CASAMENTO REALIZADO! " + tradedQuantity + " de " + newOrder.getStock() +
                                   " por R$" + String.format("%.2f", tradedPrice));

                // Atualiza o último preço de mercado negociado para esta ação
                currentPrices.put(newOrder.getStock(), tradedPrice);

                // Notifica as corretoras envolvidas na negociação
                // Formato: "TRADE_CONFIRM|AÇÃO|QUANTIDADE|PREÇO|BROKER_COMPRADOR_AID|BROKER_VENDEDOR_AID"
                ACLMessage tradeMsg = new ACLMessage(ACLMessage.INFORM);
                tradeMsg.setContent("TRADE_CONFIRM|" + newOrder.getStock() + "|" + tradedQuantity + "|" +
                                    String.format("%.2f", tradedPrice) + "|" +
                                    (newOrder.isBuy() ? newOrder.getBrokerAID().getLocalName() : existingOrder.getBrokerAID().getLocalName()) + "|" + 
                                    (newOrder.isBuy() ? existingOrder.getBrokerAID().getLocalName() : newOrder.getBrokerAID().getLocalName())); 

                tradeMsg.addReceiver(newOrder.getBrokerAID()); // Envia para a corretora da nova ordem
                tradeMsg.addReceiver(existingOrder.getBrokerAID()); // Envia para a corretora da ordem existente
                
                // **IMPORTANTE**: Usa o ConversationID da nova ordem para a notificação de trade.
                // Isso permite que a Corretora original rastreie a ordem do Investidor.
                tradeMsg.setConversationId(newOrder.getConversationID()); 
                send(tradeMsg);

                // Atualiza as quantidades restantes das ordens
                newOrder.decreaseQuantity(tradedQuantity);
                existingOrder.decreaseQuantity(tradedQuantity);

                // Se a ordem existente foi totalmente preenchida, remove-a do livro
                if (existingOrder.getQuantity() == 0) {
                    matchingOrders.remove(0); 
                    if (matchingOrders.isEmpty()) {
                        counterBook.remove(bestPrice); // Remove a entrada do TreeMap se não houver mais ordens para este preço
                    }
                }
                // Continua o loop para tentar casar o restante da nova ordem, se houver
            } else {
                // Se a nova ordem não pode casar com o melhor preço do livro oposto,
                // significa que não casará com mais nada.
                break; 
            }
        }

        // Se a nova ordem ainda tem quantidade restante (não foi totalmente casada)
        if (newOrder.getQuantity() > 0) {
            // Adiciona a ordem (ou o restante dela) ao próprio livro de ofertas
            ownBook.computeIfAbsent(newOrder.getPrice(), k -> new LinkedList<>()).add(newOrder);
            System.out.println(getLocalName() + " (Bolsa de Valores) adicionada " + newOrder.getQuantity() + " de " + newOrder.getStock() +
                               " ordem de " + (newOrder.isBuy() ? "COMPRA" : "VENDA") + " ao livro por R$" + String.format("%.2f", newOrder.getPrice()));
            
            // Notifica a corretora que a ordem foi enfileirada na bolsa (aguardando casamento)
            ACLMessage queuedMsg = new ACLMessage(ACLMessage.INFORM);
            queuedMsg.addReceiver(newOrder.getBrokerAID());
            queuedMsg.setContent("ORDER_QUEUED|" + newOrder.getStock() + "|" + newOrder.getQuantity() + "|" + String.format("%.2f", newOrder.getPrice()));
            queuedMsg.setConversationId(newOrder.getConversationID()); // Mantém o ConversationID
            send(queuedMsg);
        }
    }

    // Método auxiliar para enviar respostas
    // Agora inclui o conversationID para que a Corretora possa correlacionar
    private void sendReply(ACLMessage originalMsg, int performative, String content, String conversationID) {
        ACLMessage reply = originalMsg.createReply();
        reply.setPerformative(performative);
        reply.setContent(content);
        reply.setConversationId(conversationID); // Garante que o ID da conversação seja propagado
        send(reply);
    }
    
    // Método sobrecarregado para chamadas que não especificam o conversationID (usa o da mensagem original)
    private void sendReply(ACLMessage originalMsg, int performative, String content) {
        sendReply(originalMsg, performative, content, originalMsg.getConversationId());
    }


    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this); // Deregistra o agente do DF ao encerrar
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(getLocalName() + " (Bolsa de Valores) encerrando.");
    }

    // Classe auxiliar para representar uma Ordem no livro de ofertas
    private static class Order {
        String stock;      // Ticker da ação
        int quantity;      // Quantidade de ações na ordem
        double price;      // Preço limite da ordem
        AID brokerAID;     // AID da corretora que enviou a ordem
        boolean isBuy;     // true para ordem de compra, false para venda
        String conversationID; // ID da conversação da ordem original do Investidor

        public Order(String stock, int quantity, double price, AID brokerAID, boolean isBuy, String conversationID) {
            this.stock = stock;
            this.quantity = quantity;
            this.price = price;
            this.brokerAID = brokerAID;
            this.isBuy = isBuy;
            this.conversationID = conversationID; // Inicializa o ConversationID
        }

        public String getStock() { return stock; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }
        public AID getBrokerAID() { return brokerAID; }
        public boolean isBuy() { return isBuy; }
        public String getConversationID() { return conversationID; } // Getter para o ConversationID

        // Método para diminuir a quantidade da ordem quando ela é parcialmente casada
        public void decreaseQuantity(int amount) {
            this.quantity -= amount;
        }

        @Override
        public String toString() {
            return (isBuy ? "COMPRA" : "VENDA") + " " + quantity + " de " + stock + " por R$" + String.format("%.2f", price) + " (Corretora: " + brokerAID.getLocalName() + ") ConvID: " + conversationID;
        }
    }
}