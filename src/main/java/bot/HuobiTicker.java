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

public class HuobiTicker extends Ticker{
    public HuobiTicker(String exName, String symbol, PairAsset pairAsset, String lastPrice, String vol) {
        super(exName, symbol, pairAsset, lastPrice, vol);
    }

    @Override
    public String toString() {
        return "bot.HuobiTicker{" +
                "symbol='" + symbol + '\'' +
                "pairAsset='" + pairAsset + '\'' +
                ", lastPrice='" + lastPrice + '\'' +
                ", volCcy24h='" + vol24h + '\'' +
                '}';
    }

    public static HashMap<String, PairAsset> genHuobiSymbolsMapping() throws IOException, ParseException {
        HttpGet request = new HttpGet("https://api.huobi.pro/v1/common/symbols");

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

        HashMap<String, PairAsset> mapping = new HashMap<>();

        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = ((String) cur.get("symbol")).toUpperCase();
            String assetFirst = ((String) cur.get("base-currency")).toUpperCase();
            String assetSecond = ((String) cur.get("quote-currency")).toUpperCase();
            if (cur.get("underlying") != null)
                continue;

            mapping.put(symbol, new PairAsset(assetFirst, assetSecond));
        }

        return mapping;
    }

    private static PairAsset mapHuobiSymbol(HashMap<String, PairAsset> mapping, String symbol) {
        return mapping.get(symbol);
    }

    public static ArrayList<HuobiTicker> genHuobiTickers(HashMap<String, PairAsset> mapping) throws IOException, ParseException {
        HttpGet request = new HttpGet("https://api.huobi.pro/market/tickers");

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

//        HashMap<String, PairAsset> mapping = genHuobiSymbolsMapping();
        ArrayList<HuobiTicker> tickers = new ArrayList<>();
        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = ((String) cur.get("symbol")).toUpperCase();
            PairAsset mapped = mapHuobiSymbol(mapping, symbol);
            if (mapped == null)
                continue;

            tickers.add(new HuobiTicker("huobi", symbol, mapped, String.valueOf(cur.get("close")), String.valueOf(cur.get("vol"))));
        }

        return tickers;
    }
}
