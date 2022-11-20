package bot;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;

@AllArgsConstructor
public class Ticker {
    String exName;
    String symbol;
    PairAsset pairAsset;
    String lastPrice;
    String vol24h;

    @Override
    public String toString() {
        return "bot.Ticker{" +
                "exName='" + exName + '\'' +
                "symbol='" + symbol + '\'' +
                ", pairAsset=" + pairAsset +
                ", lastPrice='" + lastPrice + '\'' +
                ", vol24h='" + vol24h + '\'' +
                '}';
    }

    public static HashMap<String, Ticker> tickersToHashMap(ArrayList<? extends Ticker> tickers) {
        HashMap<String, Ticker> tickersMap = new HashMap<>();
        tickers.forEach(ticker -> tickersMap.put(ticker.symbol, ticker));
        return tickersMap;
    }
}
