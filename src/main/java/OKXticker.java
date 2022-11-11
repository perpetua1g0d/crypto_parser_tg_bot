public class OKXticker {
    String instId;
    String lastPrice;
    String volCcy24h;

    public OKXticker(String instId, String lastPrice, String volCcy24h) {
        this.instId = instId;
        this.lastPrice = lastPrice;
        this.volCcy24h = volCcy24h;
    }

    @Override
    public String toString() {
        return "OKXticker{" +
                "instId='" + instId + '\'' +
                ", LastPrice='" + lastPrice + '\'' +
                ", volCcy24h='" + volCcy24h + '\'' +
                '}';
    }
}
