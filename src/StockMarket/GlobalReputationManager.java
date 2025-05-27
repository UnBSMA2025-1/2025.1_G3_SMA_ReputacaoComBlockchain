package StockMarket;

import jade.core.AID;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class GlobalReputationManager {

    private static final Map<AID, ReputationCalculator> reputationDatabase = new HashMap<>();

    public static synchronized void recordInteractionOutcome(AID agentAID, boolean wasSuccessful) {
    	
        reputationDatabase.putIfAbsent(agentAID, new ReputationCalculator(agentAID.getLocalName()));
        
        // Get the reputation data for the agent and record the outcome on the db
        ReputationCalculator rData = reputationDatabase.get(agentAID);
        rData.recordInteraction(wasSuccessful);

        // for debug purposes
         System.out.println("Reputation updated for " + agentAID.getLocalName() + ": " + rData);
    }

    // returns the reputation score of the agent
    public static synchronized double getAgentReputation(AID agentAID) {
        ReputationCalculator rData = reputationDatabase.get(agentAID);
        if (rData == null) {
            return 0.5; 
        }
        return rData.getReputationScore();
    }

    // for debug purposes, prints all reputations
    public static synchronized void printAllReputations() {
        if (reputationDatabase.isEmpty()) {
            System.out.println("[ReputationManager] No reputations recorded yet.");
            return;
        }
        System.out.println("\n----- CURRENT GLOBAL REPUTATIONS -----");
        for (Map.Entry<AID, ReputationCalculator> entry : reputationDatabase.entrySet()) {
            System.out.println(entry.getKey().getLocalName() + ": " + entry.getValue().getReputationScore());
        }
        System.out.println("-------------------------------------\n");
    }
}