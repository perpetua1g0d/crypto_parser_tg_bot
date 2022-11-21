package bot;

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
import java.util.ArrayList;
import java.util.HashMap;

public class BinanceTicker extends Ticker {
    public String exName = "Binance";

    public BinanceTicker(String exName, String symbol, PairAsset pairAsset, String lastPrice, String vol24h) {
        super(exName, symbol, pairAsset, lastPrice, vol24h);
    }

    private static HashMap<String, PairAsset> genBinanceSymbolsMapping() throws IOException, ParseException {
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

    private static PairAsset mapBinanceSymbol(HashMap<String, PairAsset> mapping, String symbol) {
//        if (!mapping.containsKey(symbol))
//            System.out.println(symbol + " doesn't exist.");
        return mapping.get(symbol);
    }

    public static ArrayList<BinanceTicker> genBinanceTickers() throws IOException, ParseException {
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

        HashMap<String, PairAsset> mapping = genBinanceSymbolsMapping();
        ArrayList<BinanceTicker> tickers = new ArrayList<>();
        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = (String) cur.get("symbol");
            PairAsset mapped = mapBinanceSymbol(mapping, symbol);

            tickers.add(new BinanceTicker("Binance", symbol, mapped, (String) cur.get("lastPrice"), (String) cur.get("quoteVolume")));
        }

        return tickers;
    }
}
