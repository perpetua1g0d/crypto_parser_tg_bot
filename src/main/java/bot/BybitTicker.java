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
import java.util.*;

public class BybitTicker extends Ticker{
    public BybitTicker(String exName, String symbol, PairAsset pairAsset, String lastPrice, String vol) {
        super(exName, symbol, pairAsset, lastPrice, vol);
    }

    @Override
    public String toString() {
        return "bot.BybitTicker{" +
                "symbol='" + symbol + '\'' +
                "pairAsset='" + pairAsset + '\'' +
                ", lastPrice='" + lastPrice + '\'' +
                ", volCcy24h='" + vol24h + '\'' +
                '}';
    }

    public static HashMap<String, PairAsset> genBybitSymbolsMapping() throws IOException, ParseException {
        HttpGet request = new HttpGet("https://api.bybit.com/spot/v3/public/symbols");

        String responseStr = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseStr = EntityUtils.toString(entity);
            }
        }

        JSONParser jsonParser = new JSONParser();
        JSONArray data = (JSONArray) ((JSONObject) ((JSONObject) jsonParser.parse(responseStr)).get("result")).get("list");

        HashMap<String, PairAsset> mapping = new HashMap<>();

        Set<String> futureEndings = new HashSet<>(Arrays.asList("1S", "2S", "3S", "1L", "2L", "3L"));
        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = (String) cur.get("name");
            String assetFirst = (String) cur.get("baseCoin");
            String assetSecond = (String) cur.get("quoteCoin");

            if (assetFirst.length() > 1 && futureEndings.contains(assetFirst.substring(assetFirst.length() - 2)))
                continue;

            mapping.put(symbol, new PairAsset(assetFirst, assetSecond));
        }

        return mapping;
    }

    private static PairAsset mapBybitSymbol(HashMap<String, PairAsset> mapping, String symbol) {
//        if (!mapping.containsKey(symbol))
//            System.out.println(symbol + " doesn't exist.");
        return mapping.get(symbol);
    }

    public static ArrayList<BybitTicker> genBybitTickers(HashMap<String, PairAsset> mapping) throws IOException, ParseException {
            HttpGet request = new HttpGet("https://api.bybit.com/spot/v3/public/quote/ticker/24hr");

        String responseStr = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseStr = EntityUtils.toString(entity);
            }
        }

        JSONParser jsonParser = new JSONParser();
        JSONArray data = (JSONArray) ((JSONObject) ((JSONObject) jsonParser.parse(responseStr)).get("result")).get("list");

//        HashMap<String, PairAsset> mapping = genBybitSymbolsMapping();
        ArrayList<BybitTicker> tickers = new ArrayList<>();
        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = (String) cur.get("s");
            PairAsset mapped = mapBybitSymbol(mapping, symbol);
            if (mapped == null)
                continue;

            tickers.add(new BybitTicker("bybit", symbol, mapped, String.valueOf(cur.get("lp")), String.valueOf(cur.get("qv"))));
        }

        return tickers;
    }
}
