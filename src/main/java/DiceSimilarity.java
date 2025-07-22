import java.util.*;

public class DiceSimilarity implements SimilarityMeasure {
    @Override
    public double compute(Set<String> A, Set<String> B) {
        if (A.isEmpty() && B.isEmpty()) return 1.0;
        
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);
        
        int totalSize = A.size() + B.size();
        return totalSize == 0 ? 0.0 : (2.0 * intersection.size()) / totalSize;
    }
}
