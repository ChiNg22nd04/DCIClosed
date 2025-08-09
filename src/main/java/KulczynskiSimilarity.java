import java.util.*;

// Lớp tính độ tương đồng Kulczynski giữa 2 tập hợp item
public class KulczynskiSimilarity implements SimilarityMeasure {

    @Override
    public double compute(Set<String> A, Set<String> B) {
        // Nếu cả 2 tập đều rỗng → coi như tương đồng tuyệt đối (1.0)
        if (A.isEmpty() && B.isEmpty()) return 1.0;

        // Nếu 1 tập rỗng, 1 tập có phần tử → không tương đồng (0.0)
        if (A.isEmpty() || B.isEmpty()) return 0.0;

        // Tính giao giữa A và B
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);

        // term1 = tỷ lệ giao / kích thước A (P(A|B) nếu nghĩ theo xác suất)
        double term1 = (double) intersection.size() / A.size();

        // term2 = tỷ lệ giao / kích thước B (P(B|A) nếu nghĩ theo xác suất)
        double term2 = (double) intersection.size() / B.size();

        // Công thức Kulczynski = trung bình cộng của 2 tỷ lệ trên
        return 0.5 * (term1 + term2);
    }
}
