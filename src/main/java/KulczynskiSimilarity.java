
import java.util.*;

public class KulczynskiSimilarity implements SimilarityMeasure {
    @Override
    public double compute(Set<String> A, Set<String> B) {
        if (A.isEmpty() && B.isEmpty()) return 1.0;
        if (A.isEmpty() || B.isEmpty()) return 0.0;
        
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);
        
        double term1 = (double) intersection.size() / A.size();
        double term2 = (double) intersection.size() / B.size();
        
        return 0.5 * (term1 + term2);
    }
}
