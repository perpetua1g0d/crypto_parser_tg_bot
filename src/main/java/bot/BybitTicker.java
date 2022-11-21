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

    private static HashMap<String, PairAsset> genBybitSymbolsMapping() throws IOException, ParseException {
        HttpGet request = new HttpGet("https://api-testnet.bybit.com/v2/public/symbols");

        String responseStr = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseStr = EntityUtils.toString(entity);
            }
        }

        JSONParser jsonParser = new JSONParser();
        JSONArray data = (JSONArray) ((JSONObject) jsonParser.parse(responseStr)).get("result");

        HashMap<String, PairAsset> mapping = new HashMap<>();

        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = (String) cur.get("name");
            String assetFirst = (String) cur.get("base_currency");
            String assetSecond = (String) cur.get("quote_currency");

            mapping.put(symbol, new PairAsset(assetFirst, assetSecond));
        }

        return mapping;
    }

    private static PairAsset mapBybitSymbol(HashMap<String, PairAsset> mapping, String symbol) {
//        if (!mapping.containsKey(symbol))
//            System.out.println(symbol + " doesn't exist.");
        return mapping.get(symbol);
    }

    public static ArrayList<BybitTicker> genBybitTickers() throws IOException, ParseException {
        HttpGet request = new HttpGet("https://api-testnet.bybit.com/v2/public/tickers");

        String responseStr = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseStr = EntityUtils.toString(entity);
            }
        }

        JSONParser jsonParser = new JSONParser();
        JSONArray data = (JSONArray) ((JSONObject) jsonParser.parse(responseStr)).get("result");

        HashMap<String, PairAsset> mapping = genBybitSymbolsMapping();
        ArrayList<BybitTicker> tickers = new ArrayList<>();
        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String symbol = ((String) cur.get("symbol")).toUpperCase();
            PairAsset mapped = mapBybitSymbol(mapping, symbol);

            tickers.add(new BybitTicker("Bybit", symbol, mapped, String.valueOf(cur.get("last_price")), String.valueOf(cur.get("turnover_24h"))));
        }

        return tickers;
    }
}
