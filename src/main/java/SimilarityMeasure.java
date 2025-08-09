import java.util.Set;
import java.util.*;

// ==========================
// Interface chung cho mọi độ đo tương đồng
// ==========================
public interface SimilarityMeasure {
    /**
     * Hàm tính độ tương đồng giữa 2 tập hợp item A và B.
     * - A, B: 2 tập các item (pattern) cần so sánh.
     * - Trả về giá trị double trong khoảng [0.0, 1.0]
     */
    double compute(Set<String> A, Set<String> B);
}


// ==========================
// Cosine Similarity
// ==========================
// Công thức: sim(A, B) = |A ∩ B| / ( sqrt(|A|) * sqrt(|B|) )
// - Đo mức độ tương đồng dựa trên góc giữa 2 vector đặc trưng.
// - Giá trị từ 0 → 1 (1 là giống nhau hoàn toàn).
class CosineSimilarity implements SimilarityMeasure {
    @Override
    public double compute(Set<String> A, Set<String> B) {
        // Trường hợp cả 2 rỗng → coi như hoàn toàn giống nhau
        if (A.isEmpty() && B.isEmpty()) return 1.0;

        // Nếu 1 trong 2 rỗng → không có điểm chung → 0
        if (A.isEmpty() || B.isEmpty()) return 0.0;

        // Tìm giao của A và B
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);

        // Mẫu số: sqrt(|A|) * sqrt(|B|)
        double denominator = Math.sqrt(A.size()) * Math.sqrt(B.size());

        // Nếu denominator = 0 (không xảy ra trừ khi cả 2 rỗng) → 0
        return denominator == 0 ? 0.0 : intersection.size() / denominator;
    }
}


// ==========================
// Overlap Similarity
// ==========================
// Công thức: sim(A, B) = |A ∩ B| / min(|A|, |B|)
// - Đo tỷ lệ phần trăm phần tử chung so với tập nhỏ hơn.
// - Hữu ích khi muốn ưu tiên "bao phủ" tối đa tập nhỏ hơn.
class OverlapSimilarity implements SimilarityMeasure {
    @Override
    public double compute(Set<String> A, Set<String> B) {
        // Cả 2 rỗng → giống nhau tuyệt đối
        if (A.isEmpty() && B.isEmpty()) return 1.0;

        // Một tập rỗng → không có điểm chung
        if (A.isEmpty() || B.isEmpty()) return 0.0;

        // Lấy giao
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);

        // Mẫu số: kích thước nhỏ nhất của 2 tập
        int minSize = Math.min(A.size(), B.size());

        // Nếu tập nhỏ hơn rỗng → trả 0
        return minSize == 0 ? 0.0 : (double) intersection.size() / minSize;
    }
}
