package bot;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.checkerframework.checker.units.qual.A;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class MainService {
    private static String liquidityFilter = "0";
    private static String quoteAssetFilter = "USDT";
    private static String arbChainProfitFilter = "0.0";
    private static Set<String> allowedBaseAssetsSet = null;
    private static Set<String> blackListSet = null;
    private static HashMap<String, Ticker> OKXtickers = null;
    private static HashMap<String, Ticker> binanceTickers = null;
    private static HashMap<String, Ticker> huobiTickers = null;
    private static HashMap<String, Ticker> bybitTickers = null;
    private static HashMap<String, Ticker> kucoinTickers = null;
    private static HashMap<String, Ticker> gateTickers = null;
    private static Set<String> commonSymbols = null;
    private static List<HashMap<String, Ticker>> tickersList = null;
    private static int subListMaxDim = 0;

    public void setSubListMaxDim(int newSubListMaxDim) {
        subListMaxDim = newSubListMaxDim;
    }

    public void setLiquidityFilter(String newLiquidityFilter) {
        liquidityFilter = newLiquidityFilter;
    }

    public void setQuoteAssetFilter(String newQuoteAssetFilter) {
        quoteAssetFilter = newQuoteAssetFilter;
    }

    public void setArbChainProfitFilter(String newArbChainProfitFilter) {
        arbChainProfitFilter = newArbChainProfitFilter;
    }

    public void setAllowedBaseAssetsSet(ArrayList<String> newAllowedBaseAssetsList) {
        allowedBaseAssetsSet = new HashSet<>(newAllowedBaseAssetsList);
    }

    public void setBlackList(ArrayList<String> blackListSet) {
        MainService.blackListSet = new HashSet<>(blackListSet);
    }

    private static class ArbChainComparator implements Comparator<ArbChain> {

        @Override
        public int compare(ArbChain ac1, ArbChain ac2) {
            return Double.compare(Double.parseDouble(ac2.profit.get(0)), Double.parseDouble(ac1.profit.get(0)));
        }
    }

    private static class TickerPriceComparator implements Comparator<Ticker> {
        @Override
        public int compare(Ticker t1, Ticker t2) {
            return Double.compare(Double.parseDouble(t2.lastPrice), Double.parseDouble(t1.lastPrice));
        }
    }

    public ArrayList<ArbChain> getArbChains() {
        if (tickersList == null || tickersList.isEmpty())
            return null;

        ArrayList<ArbChain> arbChains = genArbChains(tickersList, true);
        arbChains.sort(new ArbChainComparator());

        return arbChains;
    }

    public ArrayList<ArbChain> getUnfilteredArbChains() {
        ArrayList<ArbChain> arbChains = genArbChains(tickersList, false);
        arbChains.sort(new ArbChainComparator());

        return arbChains;
    }

    public Set<String> getCommonSymbols() {
        return commonSymbols;
    }

    public static MainService getInstance() {
        return new MainService();
    }

    private ArrayList<ArbChain> recursiveGenArbChains(List<HashMap<String, Ticker>> tickersList, List<Integer> idxs, int curDim, final int dim, final boolean toFilter) {
        if (curDim == dim) {
            List<HashMap<String, Ticker>> subList = new ArrayList<>();
            List<HashMap<String, Ticker>> finalSubList = subList;
            idxs.forEach(idx -> finalSubList.add(tickersList.get(idx)));
            commonSymbols = genMergedBaseAssetSet(subList); // contents: sublist = finalsublist
            if (toFilter) {
                subList = new ArrayList<>(filterTickersList(commonSymbols, finalSubList));
                subList = subList.stream().map(tickers -> new HashMap<>(filterMatch(tickers, commonSymbols))).toList();
                commonSymbols = genMergedBaseAssetSet(subList);
            }
            return new ArrayList<>(genArbChainsSubList(subList, commonSymbols));
        }

        ArrayList<ArbChain> arbChains = new ArrayList<>();
        int idx = idxs.isEmpty() ? -1 : idxs.get(idxs.size() - 1);
        for (int i = idx + 1; i < tickersList.size(); ++i) {
            idxs.add(i);
            arbChains.addAll(recursiveGenArbChains(tickersList, idxs, curDim + 1, dim, toFilter));
            idxs.remove(idxs.size() - 1);
        }

        return arbChains;
    }

    private ArrayList<ArbChain> genArbChains(List<HashMap<String, Ticker>> tickersList, final boolean toFilter) {
        return recursiveGenArbChains(tickersList, new ArrayList<>(), 0, subListMaxDim, toFilter);
    }

    public void updateInstance() throws IOException, ParseException {
        System.out.println("Binance parsing started.");
        Instant timeMeasureStart = Instant.now();
        binanceTickers = Ticker.tickersToHashMap(BinanceTicker.genBinanceTickers());
        Instant timeMeasureEnd = Instant.now();
        System.out.println("Binance parsing finished. Elapsed time: " + Duration.between(timeMeasureStart, timeMeasureEnd).toMillis() + " ms.");

        System.out.println("OKX parsing started.");
        timeMeasureStart = Instant.now();
        OKXtickers = Ticker.tickersToHashMap(OKXticker.genOKXtickers());
        timeMeasureEnd = Instant.now();
        System.out.println("OKX parsing finished. Elapsed time: " + Duration.between(timeMeasureStart, timeMeasureEnd).toMillis() + " ms.");

        System.out.println("Huobi parsing started.");
        timeMeasureStart = Instant.now();
        huobiTickers = Ticker.tickersToHashMap(HuobiTicker.genHuobiTickers());
        timeMeasureEnd = Instant.now();
        System.out.println("Huobi parsing finished. Elapsed time: " + Duration.between(timeMeasureStart, timeMeasureEnd).toMillis() + " ms.");

        System.out.println("Bybit parsing started.");
        timeMeasureStart = Instant.now();
        bybitTickers = Ticker.tickersToHashMap(BybitTicker.genBybitTickers());
        timeMeasureEnd = Instant.now();
        System.out.println("Bybit parsing finished. Elapsed time: " + Duration.between(timeMeasureStart, timeMeasureEnd).toMillis() + " ms.");

        System.out.println("Kucoin parsing started.");
        timeMeasureStart = Instant.now();
        kucoinTickers = Ticker.tickersToHashMap(KucoinTicker.genKucoinTickers());
        timeMeasureEnd = Instant.now();
        System.out.println("Kucoin parsing finished. Elapsed time: " + Duration.between(timeMeasureStart, timeMeasureEnd).toMillis() + " ms.");

        System.out.println("Gate parsing started.");
        timeMeasureStart = Instant.now();
        gateTickers = Ticker.tickersToHashMap(GateTicker.genGateTickers());
        timeMeasureEnd = Instant.now();
        System.out.println("Gate parsing finished. Elapsed time: " + Duration.between(timeMeasureStart, timeMeasureEnd).toMillis() + " ms.");

        tickersList = new ArrayList<>(Arrays.asList(binanceTickers, kucoinTickers, gateTickers));
    }

    private Set<String> genMergedBaseAssetSet(List<HashMap<String, Ticker>> tickersSubList) {
        Set<String> mergedSymbols = new HashSet<>(tickersSubList.get(0).keySet());
        for (int i = 1; i < tickersSubList.size(); ++i) {
            int finalI = i;
            mergedSymbols = mergedSymbols.stream().
                    filter(symbol -> tickersSubList.get(finalI).containsKey(symbol))
                    .collect(Collectors.toSet());
        }

        return mergedSymbols;
    }

    private Set<String> symbolsQuoteFilter(Set<String> mergedSymbols, String filter, List<HashMap<String, Ticker>> tickersList) {
        if (filter.length() == 0)
            return mergedSymbols;

        for (int i = 0; i < tickersList.size(); ++i) {
            int finalI = i;
            mergedSymbols = mergedSymbols.stream()
                    .filter(symbol -> tickersList.get(finalI).get(symbol).pairAsset.second.equals(filter))
                    .collect(Collectors.toSet());
        }

        return mergedSymbols;
    }

    private Set<String> symbolsLiquidityFilter(Set<String> mergedSymbols, String filter, List<HashMap<String, Ticker>> tickersList) {
        for (int i = 0; i < tickersList.size(); ++i) {
            int finalI = i;
            mergedSymbols = mergedSymbols.stream()
                    .filter(symbol -> Double.compare(Double.parseDouble(tickersList.get(finalI).get(symbol).vol24h), Double.parseDouble(filter)) >= 0)
                    .collect(Collectors.toSet());
        }

        return mergedSymbols;
    }

    private Set<String> symbolsAssetFilter(Set<String> mergedSymbols, Set<String> filter, List<HashMap<String, Ticker>> tickersList) {
        if (filter == null || filter.isEmpty())
            return mergedSymbols;

        for (int i = 0; i < tickersList.size(); ++i) {
            int finalI = i;
            mergedSymbols = mergedSymbols.stream()
                    .filter(symbol -> filter.contains(tickersList.get(finalI).get(symbol).pairAsset.first))
                    .collect(Collectors.toSet());
        }

        return mergedSymbols;
    }

    private Set<String> symbolsBlackListFilter(Set<String> mergedSymbols, Set<String> filter, List<HashMap<String, Ticker>> tickersList) {
        if (filter == null || filter.isEmpty())
            return mergedSymbols;

        for (int i = 0; i < tickersList.size(); ++i) {
            int finalI = i;
            mergedSymbols = mergedSymbols.stream()
                    .filter(symbol -> !filter.contains(tickersList.get(finalI).get(symbol).pairAsset.first))
                    .collect(Collectors.toSet());
        }

        return mergedSymbols;
    }
    
    private Map<String, Ticker> filterMatch(Map<String, Ticker> tickers, Set<String> symbols) {
        return tickers.entrySet().stream()
                .filter(e -> symbols.contains(e.getValue().symbol))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<HashMap<String, Ticker>> filterTickersList(Set<String> symbols, List<HashMap<String, Ticker>> tickersList) {
        symbols = symbolsAssetFilter(symbols, allowedBaseAssetsSet, tickersList);
        symbols = symbolsBlackListFilter(symbols, blackListSet, tickersList);
        symbols = symbolsQuoteFilter(symbols, quoteAssetFilter, tickersList);
        symbols = symbolsLiquidityFilter(symbols, liquidityFilter, tickersList);

        Set<String> finalSymbols = symbols;
        List<HashMap<String, Ticker>> newList = new ArrayList<>();
        for (HashMap<String, Ticker> stringTickerHashMap : tickersList) { // stringTickerHashMap???
            newList.add(new HashMap<>(stringTickerHashMap.entrySet().stream()
                    .filter(e -> finalSymbols.contains(e.getValue().symbol))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        }

        return newList;
    }

    private ArrayList<ArbChain> genArbChainsSubList(List<HashMap<String, Ticker>> tickersSubList, Set<String> symbols) {
        ArrayList<ArbChain> arbChains = new ArrayList<>();
        symbols.forEach(symbol -> {
            List<Ticker> curTickers = new ArrayList<>(tickersSubList.stream().map(tickers -> tickers.get(symbol)).toList());
            if (curTickers.size() <= 1)
                return;

            curTickers.sort(new TickerPriceComparator());

            List<String> profit = new ArrayList<>();
            List<Ticker> tickerTo = new ArrayList<>();

            Ticker topTicker = curTickers.get(0);
            for (int i = curTickers.size() - 1; i > 0; --i) {
                Ticker curTicker = curTickers.get(i);
                double curProfit = 100 * (Double.parseDouble(topTicker.lastPrice) - Double.parseDouble(curTicker.lastPrice)) / Double.parseDouble(topTicker.lastPrice);
                if (Double.compare(curProfit, Double.parseDouble(arbChainProfitFilter)) < 0)
                    break;

                profit.add(Double.toString(curProfit));
                tickerTo.add(curTicker);
            }

            if (profit.isEmpty())
                return;

            arbChains.add(new ArbChain(profit, topTicker, tickerTo));
        });

        return arbChains;
    }

//    public static void main(String[] args) throws IOException, ParseException {
//        updateInstance();
//    }
}
