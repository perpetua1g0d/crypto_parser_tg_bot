package bot;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Ticker {
    String symbol;
    PairAsset pairAsset;
    String lastPrice;
    String vol24h;

    @Override
    public String toString() {
        return "bot.Ticker{" +
                "symbol='" + symbol + '\'' +
                ", pairAsset=" + pairAsset +
                ", lastPrice='" + lastPrice + '\'' +
                ", vol24h='" + vol24h + '\'' +
                '}';
    }
}
