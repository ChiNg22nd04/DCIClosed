import java.util.*;

public class KulczynskiSimilarity implements SimilarityMeasure {
    @Override
    public double compute(Set<String> A, Set<String> B) {
        int supportA = A.size();
        int supportB = B.size();

        // Nếu 1 trong 2 tập rỗng thì độ đo = 0
        if (supportA == 0 || supportB == 0) {
            return 0.0;
        }

        // Tính giao nhau
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);
        int intersectionSize = intersection.size();

        // Nếu không có phần tử chung → độ đo = 0
        if (intersectionSize == 0) {
            return 0.0;
        }

        // Tính 2 phần tử: intersection / A và intersection / B
        double term1 = (double) intersectionSize / supportA;
        double term2 = (double) intersectionSize / supportB;

        return 0.5 * (term1 + term2); // Trung bình cộng của 2 giá trị
    }
}
