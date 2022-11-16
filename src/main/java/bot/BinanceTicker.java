package bot;

public class BinanceTicker extends Ticker {

    public BinanceTicker(String symbol, PairAsset pairAsset, String lastPrice, String vol24h) {
        super(symbol, pairAsset, lastPrice, vol24h);
    }
}
