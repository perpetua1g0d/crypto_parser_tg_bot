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

public class GateTicker extends Ticker{

    public GateTicker(String exName, String symbol, PairAsset pairAsset, String lastPrice, String vol24) {
        super(exName, symbol, pairAsset, lastPrice, vol24);
    }

    @Override
    public String toString() {
        return "bot.GateTicker{" +
                "symbol='" + symbol + '\'' +
                "pairAsset='" + pairAsset + '\'' +
                ", lastPrice='" + lastPrice + '\'' +
                ", volCcy24h='" + vol24h + '\'' +
                '}';
    }

    public static ArrayList<GateTicker> genGateTickers() throws IOException, ParseException {
        HttpGet request = new HttpGet("https://api.gateio.ws/api/v4/spot/tickers");
        String responseStr = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseStr = EntityUtils.toString(entity);
            }
        }

        JSONParser jsonParser = new JSONParser();
        JSONArray data = (JSONArray) (jsonParser.parse(responseStr));

        ArrayList<GateTicker> tickers = new ArrayList<>();
        for (Object dataItem : data) {
            JSONObject cur = (JSONObject) dataItem;

            String instId = (String) cur.get("currency_pair");
            final int dashPos = instId.indexOf('_');
            String assetFirst = instId.substring(0, dashPos);
            String assetSecond = instId.substring(dashPos + 1);
            String lastPrice = (String) cur.get("last");
            String vol24 = (String) cur.get("quote_volume");
            tickers.add(new GateTicker("gate", assetFirst + assetSecond, new PairAsset(assetFirst, assetSecond), lastPrice, vol24));
        }

        return tickers;
    }
}
