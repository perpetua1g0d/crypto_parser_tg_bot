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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Main {
    public static HashMap<String, HashMap<String, Double>> getBinancePrices(List<TickerPrice> tickerPrices, ArrayList<String> assets) {
        HashMap<String, HashMap<String, Double>> binancePrices = new HashMap<>();
        assets.forEach(asset -> binancePrices.put(asset, new HashMap<>()));
        tickerPrices.forEach(tickerPrice -> {
            String symbol = tickerPrice.getSymbol(); // USDT -> S, [1, len - 2)
            for (int i = 0; i < symbol.length() - 1; ++i) {
                final String asset1 = symbol.substring(0, i + 1), asset2 = symbol.substring(i + 1);
                if (assets.contains(asset1) && assets.contains(asset2)) {
                    binancePrices.get(asset1).put(asset2, Double.valueOf(tickerPrice.getPrice()));
                    binancePrices.get(asset2).put(asset1, 1 / Double.parseDouble(tickerPrice.getPrice()));
                }
            }
        });

        return binancePrices;
    }

    public static void OKXrequest() throws IOException {
        HttpPost request = new HttpPost("https://www.okx.com/api/v5/market/open-oracle");


        String responseStr = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseStr = EntityUtils.toString(entity);
            }
        }
    }

    public static ArrayList<OKXticker> getOKXtickers(String assetFilter) throws IOException, ParseException {

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
//            System.out.println(instId);
            if (!instId.endsWith("-"+assetFilter))
                continue;

            String lastPrice = (String) cur.get("lastPrice");
            String volCcy24h = (String) cur.get("volCcy24h");
            tickers.add(new OKXticker(instId, lastPrice, volCcy24h));
        }

        return tickers;
    }


    public static void main(String[] args) throws IOException, ParseException {
//        BinanceApiRestClient binanceApiRestClient = BinanceApiClientFactory.newInstance(
//                "",
//                "").newRestClient();
//        List<TickerPrice> tickerPrices = binanceApiRestClient.getAllPrices();

        ArrayList<OKXticker> OKXtickers = getOKXtickers("USDT");
        OKXtickers.forEach(System.out::println);
    }
}
