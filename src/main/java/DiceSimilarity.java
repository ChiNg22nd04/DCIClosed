import java.util.*;

/**
 * DiceSimilarity
 * --------------
 * Tính hệ số tương đồng Sørensen–Dice giữa HAI tập (symmetry, miền [0,1]):
 *
 *   Dice(A, B) = 2 * |A ∩ B| / (|A| + |B|)
 *
 * Đặc tính:
 *  - = 1 khi A == B (và cùng rỗng cũng coi là 1)
 *  - = 0 khi A ∩ B = ∅ và không đồng thời rỗng
 *  - Đối xứng: Dice(A,B) == Dice(B,A)
 */
public class DiceSimilarity implements SimilarityMeasure {

    @Override
    public double compute(Set<String> A, Set<String> B) {
        // Phòng thủ null (tuỳ giao kèo interface, có thể bỏ nếu chắc chắn không null)
        if (A == null || B == null) {
            return 0.0;
        }

        // Trường hợp cả hai rỗng -> tương đồng tuyệt đối
        if (A.isEmpty() && B.isEmpty()) return 1.0;

        // Nếu một rỗng, một không rỗng -> không trùng phần giao -> Dice = 0
        if (A.isEmpty() || B.isEmpty()) return 0.0;

        // Tạo bản sao để tính giao (dễ hiểu nhưng có cấp phát bộ nhớ)
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);

        int totalSize = A.size() + B.size();
        // totalSize không thể = 0 vì đã xử lý phía trên, nhưng vẫn để phòng thủ:
        return totalSize == 0 ? 0.0 : (2.0 * intersection.size()) / totalSize;
    }
}