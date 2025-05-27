package StockMarket;

public class ReputationCalculator {
    String agentName;
    int successfulInteractions = 0;
    int failedInteractions = 0; 

    public ReputationCalculator(String name) {
        this.agentName = name;
    }

    // register the result of the interaction
    public void recordInteraction(boolean wasSuccessful) {
        if (wasSuccessful) {
            successfulInteractions++;
        } else {
            failedInteractions++;
        }
    }

    // calculate the reputation score
    public double getReputationScore() {
        int totalInteractions = successfulInteractions + failedInteractions;
        if (totalInteractions == 0) {
            return 0.5; // initial neutral
        }
        return (double) successfulInteractions / totalInteractions; // a very simple score between 0 and 1
    }

    @Override
    // returns a string representation of the reputation for monitoring purposes
    public String toString() {
        return "Reputation for " + agentName +
               ": Success=" + successfulInteractions +
               ", Failures=" + failedInteractions +
               ", Score=" + String.format("%.2f", getReputationScore());
    }
}