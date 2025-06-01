package StockMarket;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class Main {
    public static void main(String[] args) {
        try {
            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.GUI, "true");
            ContainerController cc = rt.createMainContainer(p);
            

            AgentController stock = cc.createNewAgent("Stock", "StockMarket.Stock", null);
            stock.start();
            
            AgentController investor = cc.createNewAgent("Investor", "StockMarket.Investor", null);
            investor.start();

            AgentController broker = cc.createNewAgent("Broker", "StockMarket.Broker", null);
            broker.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
