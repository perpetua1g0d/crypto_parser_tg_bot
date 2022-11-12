public class OKXticker extends Ticker{

    public OKXticker(String symbol, PairAsset pairAsset, String lastPrice, String volCcy24h) {
        super(symbol, pairAsset, lastPrice, volCcy24h);
    }

    @Override
    public String toString() {
        return "OKXticker{" +
                "symbol='" + symbol + '\'' +
                "pairAsset='" + pairAsset + '\'' +
                ", lastPrice='" + lastPrice + '\'' +
                ", volCcy24h='" + vol24h + '\'' +
                '}';
    }
}
