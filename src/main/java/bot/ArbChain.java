package bot;

import lombok.AllArgsConstructor;
import lombok.ToString;

import java.util.List;

@ToString
@AllArgsConstructor
public class ArbChain {
    List<String> profit;
    Ticker tickerFrom;
    List<Ticker> tickerTo;
}
