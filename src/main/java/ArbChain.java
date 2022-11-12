public class ArbChain {
    String profit;
    String exFrom;
    String exTo;
    Ticker tickerFrom;
    Ticker tickerTo;

    public ArbChain(String profit, String exFrom, String exTo, Ticker tickerFrom, Ticker tickerTo) {
        this.profit = profit;
        this.exFrom = exFrom;
        this.exTo = exTo;
        this.tickerFrom = tickerFrom;
        this.tickerTo = tickerTo;
    }

    @Override
    public String toString() {
        return "arbChain{" +
                "profit='" + profit + '\'' +
                ", exFrom='" + exFrom + '\'' +
                ", exTo='" + exTo + '\'' +
                ", tickerFrom=" + tickerFrom +
                ", tickerTo=" + tickerTo +
                '}';
    }
}
