import java.util.*;

public class JaccardSimilarity implements SimilarityMeasure {
    private final BitMatrix matrix;

    public JaccardSimilarity() {
        this.matrix = null; // placeholder nếu chưa init
    }

    public JaccardSimilarity(List<Set<String>> database) {
        this.matrix = new BitMatrix(database);
    }

    private BitMatrix getMatrix() {
        if (matrix == null) throw new IllegalStateException("JaccardSimilarity not initialized with database.");
        return matrix;
    }

    public void init(List<Set<String>> database) {
        // Optional: tạo BitMatrix từ database
    }

    @Override
    public double compute(Set<String> A, Set<String> B) {
        if (A.isEmpty() || B.isEmpty()) return 0.0;

        BitSet bitset1 = new BitSet();
        BitSet bitset2 = new BitSet();

        for (String item : A) {
            bitset1.or(getMatrix().getBitSetOf(item));
        }

        for (String item : B) {
            bitset2.or(getMatrix().getBitSetOf(item));
        }

        BitSet intersection = (BitSet) bitset1.clone();
        intersection.and(bitset2);

        BitSet union = (BitSet) bitset1.clone();
        union.or(bitset2);

        return union.cardinality() == 0 ? 0.0 : (double) intersection.cardinality() / union.cardinality();
    }
}


class BitMatrix {
    private final Map<String, BitSet> itemToBitSet;
    private final int transactionCount;

    public BitMatrix(List<Set<String>> database) {
        this.transactionCount = database.size();
        this.itemToBitSet = new HashMap<>();

        // Khởi tạo BitSet cho mỗi item
        for (int i = 0; i < database.size(); i++) {
            Set<String> transaction = database.get(i);
            for (String item : transaction) {
                BitSet bitSet = itemToBitSet.computeIfAbsent(item, k -> new BitSet(transactionCount));
                bitSet.set(i);
            }
        }
    }

    public BitSet getBitSetOf(String item) {
        return itemToBitSet.getOrDefault(item, new BitSet(transactionCount));
    }
}