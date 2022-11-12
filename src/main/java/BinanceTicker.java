public class BinanceTicker extends Ticker {

    public BinanceTicker(String symbol, PairAsset pairAsset, String lastPrice, String vol24h) {
        super(symbol, pairAsset, lastPrice, vol24h);
    }

    @Override
    public String toString() {
        return "BinanceTicker{" +
                "symbol='" + symbol + '\'' +
                "pairAsset='" + pairAsset + '\'' +
                ", lastPrice='" + lastPrice + '\'' +
                ", vol24h='" + vol24h + '\'' +
                '}';
    }
}
