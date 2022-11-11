public class OKXticker {
    String instId;
    String assetFirst;
    String assetSecond;
    String lastPrice;
    String volCcy24h;

    public OKXticker(String instId, String assetFirst, String assetSecond, String lastPrice, String volCcy24h) {
        this.instId = instId;
        this.assetFirst = assetFirst;
        this.assetSecond = assetSecond;
        this.lastPrice = lastPrice;
        this.volCcy24h = volCcy24h;
    }

    @Override
    public String toString() {
        return "OKXticker{" +
                "instId='" + instId + '\'' +
                "assetFirst='" + assetFirst + '\'' +
                "assetSecond='" + assetSecond + '\'' +
                ", LastPrice='" + lastPrice + '\'' +
                ", volCcy24h='" + volCcy24h + '\'' +
                '}';
    }
}
