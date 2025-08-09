import java.util.*;

// Lớp tính toán độ tương đồng Jaccard giữa 2 tập hợp item
public class JaccardSimilarity implements SimilarityMeasure {
    // Ma trận bit để tối ưu việc tính toán khi làm việc với CSDL lớn
    private final BitMatrix matrix;

    // Constructor mặc định - chưa gán dữ liệu (matrix = null)
    public JaccardSimilarity() {
        this.matrix = null; // placeholder nếu chưa init
    }

    // Constructor khởi tạo luôn ma trận bit từ database (list các transaction)
    public JaccardSimilarity(List<Set<String>> database) {
        this.matrix = new BitMatrix(database);
    }

    // Lấy ra matrix, nếu chưa khởi tạo thì báo lỗi
    private BitMatrix getMatrix() {
        if (matrix == null)
            throw new IllegalStateException("JaccardSimilarity not initialized with database.");
        return matrix;
    }

    // Phương thức khởi tạo lại matrix (lazy initialization) - chưa implement
    public void init(List<Set<String>> database) {
        // Optional: tạo BitMatrix từ database
    }

    // Hàm tính toán độ tương đồng Jaccard
    @Override
    public double compute(Set<String> A, Set<String> B) {
        // Nếu cả 2 tập rỗng thì độ tương đồng là 1
        if (A.isEmpty() && B.isEmpty()) return 1.0;

        // Tạo giao của A và B
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);

        // Tạo hợp của A và B
        Set<String> union = new HashSet<>(A);
        union.addAll(B);

        // Công thức Jaccard = |A ∩ B| / |A ∪ B|
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}

// ======================= LỚP BITMATRIX =======================

// Lớp dùng để lưu dữ liệu database dưới dạng ma trận bit (BitSet)
// Giúp tính toán giao/hợp nhanh hơn nhiều so với dùng Set<String> thông thường
class BitMatrix {
    // Ánh xạ mỗi item sang một BitSet (bit=1 nếu item xuất hiện trong transaction đó)
    private final Map<String, BitSet> itemToBitSet;
    // Tổng số transaction trong database
    private final int transactionCount;

    // Khởi tạo BitMatrix từ danh sách transaction
    public BitMatrix(List<Set<String>> database) {
        this.transactionCount = database.size();
        this.itemToBitSet = new HashMap<>();

        // Duyệt từng transaction (theo chỉ số i)
        for (int i = 0; i < database.size(); i++) {
            Set<String> transaction = database.get(i);
            for (String item : transaction) {
                // Nếu item chưa có BitSet thì tạo mới, nếu có rồi thì lấy lại
                BitSet bitSet = itemToBitSet.computeIfAbsent(item,
                        k -> new BitSet(transactionCount));
                // Đánh dấu bit thứ i là 1 (item này xuất hiện ở transaction i)
                bitSet.set(i);
            }
        }
    }

    // Trả về BitSet của một item (nếu không tồn tại thì trả BitSet rỗng)
    public BitSet getBitSetOf(String item) {
        return itemToBitSet.getOrDefault(item, new BitSet(transactionCount));
    }
}
