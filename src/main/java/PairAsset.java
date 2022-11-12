public class PairAsset {
    String first;
    String second;

    public PairAsset(String first, String second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return "pairAsset{" +
                "first='" + first + '\'' +
                ", second='" + second + '\'' +
                '}';
    }
}
