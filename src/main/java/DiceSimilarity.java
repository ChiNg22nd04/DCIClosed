import java.util.*;

public class DiceSimilarity implements SimilarityMeasure {
    @Override
    public double compute(Set<String> A, Set<String> B) {
        int supportA = A.size();
        int supportB = B.size();
        int sumOfSupports = supportA + supportB;

        if (sumOfSupports == 0) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);
        int intersectionSize = intersection.size();

        return (2.0 * intersectionSize) / sumOfSupports;
    }
}
