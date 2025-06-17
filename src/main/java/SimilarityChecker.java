
import java.util.*;

public class SimilarityChecker {
    public static List<Set<String>> checkSimilarity(Set<Set<String>> closedPatterns, double minSim, SimilarityMeasure measure) {
        List<Set<String>> filtered = new ArrayList<>();
        for (Set<String> X : closedPatterns) {
            boolean isSimilar = false;
            for (Set<String> Y : filtered) {
                double sim = measure.compute(X, Y);
                if (sim >= minSim) {
                    isSimilar = true;
                    break;
                }
            }
            if (!isSimilar) filtered.add(X);
        }
        return filtered;
    }
}
