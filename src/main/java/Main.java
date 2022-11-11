import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.TickerPrice;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.*;

public class Main {
//    public static HashMap<String, HashMap<String, Double>> getBinancePrices(List<TickerPrice> tickerPrices, ArrayList<String> assets) {
//        HashMap<String, HashMap<String, Double>> binancePrices = new HashMap<>();
//        assets.forEach(asset -> binancePrices.put(asset, new HashMap<>()));
//        tickerPrices.forEach(tickerPrice -> {
//            String symbol = tickerPrice.getSymbol(); // USDT -> S, [1, len - 2)
//            for (int i = 0; i < symbol.length() - 1; ++i) {
//                final String asset1 = symbol.substring(0, i + 1), asset2 = symbol.substring(i + 1);
//                if (assets.contains(asset1) && assets.contains(asset2)) {
//                    binancePrices.get(asset1).put(asset2, Double.valueOf(tickerPrice.getPrice()));
//                    binancePrices.get(asset2).put(asset1, 1 / Double.parseDouble(tickerPrice.getPrice()));
//                }
//            }
//        });
//
//        return binancePrices;
//    }

//    public static void OKXrequest() throws IOException {
//        HttpPost request = new HttpPost("https://www.okx.com/api/v5/market/open-oracle");
//
//
//        String responseStr = null;
//        try (CloseableHttpClient httpClient = HttpClients.createDefault();
//             CloseableHttpResponse response = httpClient.execute(request)) {
//            HttpEntity entity = response.getEntity();
//            if (entity != null) {
//                responseStr = EntityUtils.toString(entity);
//            }
//        }
//    }

    public static ArrayList<OKXticker> getOKXtickers(String assetSecondFilter) throws IOException, ParseException {

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
            if (!assetSecond.equals(assetSecondFilter))
                continue;

            String lastPrice = (String) cur.get("lastPrice");
            String volCcy24h = (String) cur.get("volCcy24h");
            tickers.add(new OKXticker(instId, assetFirst, assetSecond, lastPrice, volCcy24h));
        }

        return tickers;
    }

    public static HashMap<String, ArrayList<String>> genBinanceSymbolsMapping() throws IOException, ParseException {
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

        HashMap<String, ArrayList<String>> mapping = new HashMap<>();

        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = (String) cur.get("symbol");
            String assetFirst = (String) cur.get("baseAsset");
            String assetSecond = (String) cur.get("quoteAsset");

            mapping.put(symbol, new ArrayList<>(Arrays.asList(assetFirst, assetSecond)));
        }

        return mapping;
    }

    public static ArrayList<String> mapBinanceSymbol(HashMap<String, ArrayList<String>> mapping, String symbol) {
        if (!mapping.containsKey(symbol))
            System.out.println(symbol + " doesn't exist.");
        return mapping.get(symbol);
    }

    public static ArrayList<BinanceTicker> getBinanceTickers(HashMap<String, ArrayList<String>> mapping, String assetFilter) throws IOException, ParseException {
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
            ArrayList<String> mapped = mapBinanceSymbol(mapping, symbol);
            if (!mapped.get(1).equals(assetFilter))
                continue;

            tickers.add(new BinanceTicker(symbol, mapped.get(0), mapped.get(1), (String) cur.get("lastPrice"), (String) cur.get("volume")));
        }

        return tickers;
    }

    public static ArrayList<String> genMergedSymbolList(ArrayList<OKXticker> OKXtickers, ArrayList<BinanceTicker> binanceTickers) {
        ArrayList<String> merged = new ArrayList<>();
        Set<String> binanceAssets = Set.copyOf(binanceTickers.stream().map(ticker -> ticker.assetFirst).toList());
        merged = (ArrayList<String>) OKXtickers.stream()
                .map(ticker -> ticker.assetFirst)
                .filter(binanceAssets::contains).toList(); // asset -> binanceAssets.contains(asset)

        return merged;
    }

    public static void main(String[] args) throws IOException, ParseException {
//        ArrayList<OKXticker> OKXtickers = getOKXtickers("USDT");
//        OKXtickers.forEach(System.out::println);

        HashMap<String, ArrayList<String>> binanceSymbolsMapping = genBinanceSymbolsMapping();
        ArrayList<BinanceTicker> binanceTickers = getBinanceTickers(binanceSymbolsMapping, "USDT");
        binanceTickers.forEach(System.out::println);

//        ArrayList<String> allExSymbolsMerged = genMergedSymbolList(OKXtickers, binanceTickers);
//        allExSymbolsMerged.forEach(System.out::println);
    }
}
