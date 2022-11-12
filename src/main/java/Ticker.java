public class Ticker {
    String symbol;
    PairAsset pairAsset;
    String lastPrice;
    String vol24h;

    public Ticker(String symbol, PairAsset pairAsset, String lastPrice, String vol24h) {
        this.symbol = symbol;
        this.pairAsset = pairAsset;
        this.lastPrice = lastPrice;
        this.vol24h = vol24h;
    }

    @Override
    public String toString() {
        return "Ticker{" +
                "symbol='" + symbol + '\'' +
                ", pairAsset=" + pairAsset +
                ", lastPrice='" + lastPrice + '\'' +
                ", vol24h='" + vol24h + '\'' +
                '}';
    }
}
