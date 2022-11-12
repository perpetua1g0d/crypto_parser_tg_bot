import lombok.AllArgsConstructor;
import lombok.ToString;

@ToString
@AllArgsConstructor
public class ArbChain {
    String profit;
    String exFrom;
    String exTo;
    Ticker tickerFrom;
    Ticker tickerTo;
}
