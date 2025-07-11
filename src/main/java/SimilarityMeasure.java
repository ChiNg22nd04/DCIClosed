import java.util.Set;
import java.util.*;

public interface SimilarityMeasure {
    double compute(Set<String> A, Set<String> B);
}


// Cosine Similarity (thêm để đa dạng hóa)
class CosineSimilarity implements SimilarityMeasure {
    @Override
    public double compute(Set<String> A, Set<String> B) {
        if (A.isEmpty() && B.isEmpty()) return 1.0;
        if (A.isEmpty() || B.isEmpty()) return 0.0;
        
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);
        
        double denominator = Math.sqrt(A.size()) * Math.sqrt(B.size());
        return denominator == 0 ? 0.0 : intersection.size() / denominator;
    }
}

// Overlap Similarity (thêm để đa dạng hóa)
class OverlapSimilarity implements SimilarityMeasure {
    @Override
    public double compute(Set<String> A, Set<String> B) {
        if (A.isEmpty() && B.isEmpty()) return 1.0;
        if (A.isEmpty() || B.isEmpty()) return 0.0;
        
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);
        
        int minSize = Math.min(A.size(), B.size());
        return minSize == 0 ? 0.0 : (double) intersection.size() / minSize;
    }
}