package bot;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class ArbChain {
    List<String> profit;
    Ticker tickerFrom;
    List<Ticker> tickerTo;
}
