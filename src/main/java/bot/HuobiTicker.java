package bot;

public class HuobiTicker extends Ticker{
    public HuobiTicker(String exName, String symbol, PairAsset pairAsset, String lastPrice, String vol) {
        super(exName, symbol, pairAsset, lastPrice, vol);
    }

    @Override
    public String toString() {
        return "bot.HuobiTicker{" +
                "symbol='" + symbol + '\'' +
                "pairAsset='" + pairAsset + '\'' +
                ", lastPrice='" + lastPrice + '\'' +
                ", volCcy24h='" + vol24h + '\'' +
                '}';
    }
}
