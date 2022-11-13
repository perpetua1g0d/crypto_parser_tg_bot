import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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
    private static Set<String> allowedBaseAssetsSet = null;
    private static HashMap<String, Ticker> OKXtickers = null;
    private static HashMap<String, Ticker> binanceTickers = null;
    private static ArrayList<ArbChain> arbChains = null;
    private static ArrayList<ArbChain> unfilteredArbChains = null;
    private static Set<String> commonSymbols = null;

    public void setLiquidityFilter(String newLiquidityFilter) {
        liquidityFilter = newLiquidityFilter;
    }

    public void setQuoteAssetFilter(String newQuoteAssetFilter) {
        quoteAssetFilter = newQuoteAssetFilter;
    }

    public void setAllowedBaseAssetsSet(ArrayList<String> newAllowedBaseAssetsList) {
        allowedBaseAssetsSet = new HashSet<>(newAllowedBaseAssetsList);
    }

    public ArrayList<ArbChain> getArbChains() {
        return arbChains;
    }

    public Set<String> getCommonSymbols() {
        return commonSymbols;
    }

    public static MainService getInstance() {
        return new MainService();
    }

    public void updateInstance() throws IOException, ParseException {
        System.out.println("OKX parsing started.");
        Instant timeMeasureStart = Instant.now();
        OKXtickers = tickersToHashMap(genOKXtickers());
        Instant timeMeasureEnd = Instant.now();
        System.out.println("OKX parsing finished. Elapsed time: " + Duration.between(timeMeasureStart, timeMeasureEnd).toMillis() + " ms.");

        System.out.println("Binance parsing started.");
        timeMeasureStart = Instant.now();
        HashMap<String, PairAsset> binanceSymbolsMapping = genBinanceSymbolsMapping();
        binanceTickers = tickersToHashMap(genBinanceTickers(binanceSymbolsMapping));
        timeMeasureEnd = Instant.now();
        System.out.println("Binance parsing finished. Elapsed time: " + Duration.between(timeMeasureStart, timeMeasureEnd).toMillis() + " ms.");

        Set<String> unfilteredCommonSymbols = OKXtickers.keySet().stream()
                .filter(binanceTickers::containsKey).collect(Collectors.toSet());
        unfilteredArbChains = genArbChainsUnfiltered(new HashMap<>(filterTickersMap(OKXtickers, unfilteredCommonSymbols)),
                new HashMap<>(filterTickersMap(binanceTickers, unfilteredCommonSymbols)));
        unfilteredArbChains.sort(new ArbChainComparator());

        commonSymbols = genFilteredMergedBaseAssetList();

        binanceTickers = new HashMap<>(filterTickersMap(binanceTickers, commonSymbols));
        OKXtickers = new HashMap<>(filterTickersMap(OKXtickers, commonSymbols));

        arbChains = genArbChains();
        arbChains.sort(new ArbChainComparator());
//        arbChains.forEach(System.out::println);
    }

    private ArrayList<ArbChain> genArbChainsUnfiltered(HashMap<String, Ticker> unfilteredBinanceTickers,
                                                      HashMap<String, Ticker> unfilteredOKXTickers) {
        ArrayList<ArbChain> unfilteredArbChains = new ArrayList<>();
        unfilteredOKXTickers.forEach((key, OKXticker) -> {
            Ticker binanceTicker = unfilteredBinanceTickers.get(key);
            double OKXprice = Double.parseDouble(OKXticker.lastPrice);
            double binancePrice = Double.parseDouble(binanceTicker.lastPrice);
            if (Double.compare(OKXprice, 0) == 0 || Double.compare(binancePrice, 0) == 0)
                return;

            if (OKXprice < binancePrice) {
                unfilteredArbChains.add(new ArbChain(Double.toString(100 * (binancePrice - OKXprice) / binancePrice), "OKX", "Binance", OKXticker, binanceTicker));
            } else {
                unfilteredArbChains.add(new ArbChain(Double.toString(100 * (OKXprice - binancePrice) / OKXprice), "Binance", "OKX", binanceTicker, OKXticker));
            }
        });

        return unfilteredArbChains;
    }

    public ArrayList<ArbChain> getUnfilteredArbChains() {
        return unfilteredArbChains;
    }

    private ArrayList<OKXticker> genOKXtickers() throws IOException, ParseException {
        HttpGet request = new HttpGet("https://www.okx.com/priapi/v5/market/tickers?t=1668155757120&instType=SPOT");
        String responseStr = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseStr = EntityUtils.toString(entity);
            }
        }

        JSONParser jsonParser = new JSONParser();
        JSONArray data = (JSONArray) ((JSONObject) jsonParser.parse(responseStr)).get("data");

        ArrayList<OKXticker> tickers = new ArrayList<>();
        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String instId = (String) cur.get("instId");
            final int dashPos = instId.indexOf('-');
            String assetFirst = instId.substring(0, dashPos);
            String assetSecond = instId.substring(dashPos + 1);
            String lastPrice = (String) cur.get("last");
            String volCcy24h = (String) cur.get("volCcy24h");
            tickers.add(new OKXticker(assetFirst + assetSecond, new PairAsset(assetFirst, assetSecond), lastPrice, volCcy24h));
        }

        return tickers;
    }

    private HashMap<String, PairAsset> genBinanceSymbolsMapping() throws IOException, ParseException {
        HttpGet request = new HttpGet("https://api.binance.com/api/v1/exchangeInfo");

        String responseStr = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseStr = EntityUtils.toString(entity);
            }
        }

        JSONParser jsonParser = new JSONParser();
        JSONArray data = (JSONArray) ((JSONObject) jsonParser.parse(responseStr)).get("symbols");

        HashMap<String, PairAsset> mapping = new HashMap<>();

        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = (String) cur.get("symbol");
            String assetFirst = (String) cur.get("baseAsset");
            String assetSecond = (String) cur.get("quoteAsset");

            mapping.put(symbol, new PairAsset(assetFirst, assetSecond));
        }

        return mapping;
    }

    private PairAsset mapBinanceSymbol(HashMap<String, PairAsset> mapping, String symbol) {
        if (!mapping.containsKey(symbol))
            System.out.println(symbol + " doesn't exist.");
        return mapping.get(symbol);
    }

    private ArrayList<BinanceTicker> genBinanceTickers(HashMap<String, PairAsset> mapping) throws IOException, ParseException {
        HttpGet request = new HttpGet("https://api.binance.com/api/v1/ticker/24hr");

        String responseStr = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseStr = EntityUtils.toString(entity);
            }
        }

        JSONParser jsonParser = new JSONParser();
        JSONArray data = (JSONArray) jsonParser.parse(responseStr);

        ArrayList<BinanceTicker> tickers = new ArrayList<>();
        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = (String) cur.get("symbol");
            PairAsset mapped = mapBinanceSymbol(mapping, symbol);

            tickers.add(new BinanceTicker(symbol, mapped, (String) cur.get("lastPrice"), (String) cur.get("quoteVolume")));
        }

        return tickers;
    }

    private HashMap<String, Ticker> tickersToHashMap(ArrayList<? extends Ticker> tickers) {
        HashMap<String, Ticker> tickersMap = new HashMap<>();
        tickers.forEach(ticker -> tickersMap.put(ticker.symbol, ticker));
        return tickersMap;
    }

    private Set<String> genMergedBaseAssetList() {
        return OKXtickers.entrySet().stream()
                .filter(e ->
                        (quoteAssetFilter.length() == 0 || e.getValue().pairAsset.second.equals(quoteAssetFilter))
                        && Double.compare(Double.parseDouble(e.getValue().vol24h), Double.parseDouble(liquidityFilter)) >= 0
                        && binanceTickers.containsKey(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Set<String> genFilteredMergedBaseAssetList() {
        if (allowedBaseAssetsSet == null)
            return genMergedBaseAssetList();

        return OKXtickers.entrySet().stream()
                .filter(e ->
                        (quoteAssetFilter.length() == 0 || e.getValue().pairAsset.second.equals(quoteAssetFilter))
                        && Double.compare(Double.parseDouble(e.getValue().vol24h), Double.parseDouble(liquidityFilter)) >= 0
                        && binanceTickers.containsKey(e.getKey())
                        && allowedBaseAssetsSet.contains(e.getValue().pairAsset.first))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static Map<String, Ticker> filterTickersMap(Map<String, Ticker> tickers, Set<String> commonSymbols) {
        return tickers.entrySet().stream()
                .filter(e -> commonSymbols.contains(e.getValue().symbol))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private ArrayList<ArbChain> genArbChains() {
        ArrayList<ArbChain> arbChains = new ArrayList<>();
        OKXtickers.forEach((key, OKXticker) -> {
            Ticker binanceTicker = binanceTickers.get(key);
            double OKXprice = Double.parseDouble(OKXticker.lastPrice);
            double binancePrice = Double.parseDouble(binanceTicker.lastPrice);
            if (Double.compare(OKXprice, 0) == 0 || Double.compare(binancePrice, 0) == 0)
                return;

            if (OKXprice < binancePrice) {
                arbChains.add(new ArbChain(Double.toString(100 * (binancePrice - OKXprice) / binancePrice), "OKX", "Binance", OKXticker, binanceTicker));
            } else {
                arbChains.add(new ArbChain(Double.toString(100 * (OKXprice - binancePrice) / OKXprice), "Binance", "OKX", binanceTicker, OKXticker));
            }
        });

        return arbChains;
    }

    private static class ArbChainComparator implements Comparator<ArbChain> {

        @Override
        public int compare(ArbChain ac1, ArbChain ac2) {
            return Double.compare(Double.parseDouble(ac2.profit), Double.parseDouble(ac1.profit));
        }
    }

//    public static void main(String[] args) throws IOException, ParseException {
//        updateInstance();
//    }
}
