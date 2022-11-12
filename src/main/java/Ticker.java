import lombok.AllArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
public class Ticker {
    String symbol;
    PairAsset pairAsset;
    String lastPrice;
    String vol24h;

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
