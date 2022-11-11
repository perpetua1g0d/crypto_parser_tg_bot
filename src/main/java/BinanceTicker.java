public class BinanceTicker {
    String symbol;
    String assetFirst;
    String assetSecond;
    String lastPrice;
    String vol24h;

    public BinanceTicker(String symbol, String assetFirst, String assetSecond, String lastPrice, String vol24h) {
        this.symbol = symbol;
        this.assetFirst = assetFirst;
        this.assetSecond = assetSecond;
        this.lastPrice = lastPrice;
        this.vol24h = vol24h;
    }

    @Override
    public String toString() {
        return "BinanceTicker{" +
                "symbol='" + symbol + '\'' +
                ", assetFirst='" + assetFirst + '\'' +
                ", assetSecond='" + assetSecond + '\'' +
                ", lastPrice='" + lastPrice + '\'' +
                ", vol24h='" + vol24h + '\'' +
                '}';
    }
}
