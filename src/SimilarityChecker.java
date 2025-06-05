import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Class để kiểm tra similarity
class SimilarityChecker {
    public static List<Set<String>> checkSimilarity(Set<Set<String>> closedPatterns, double minSim) {
        List<Set<String>> filteredPatterns = new ArrayList<>();

        for (Set<String> pattern : closedPatterns) {
            boolean isSimilar = false;

            for (Set<String> filteredPattern : filteredPatterns) {
                double similarity = computeSimilarity(pattern, filteredPattern);
                if (similarity >= minSim) {
                    isSimilar = true;
                    break;
                }
            }

            if (!isSimilar) {
                filteredPatterns.add(pattern);
            }
        }

        return filteredPatterns;
    }

    private static double computeSimilarity(Set<String> pattern1, Set<String> pattern2) {
        Set<String> intersection = new HashSet<>(pattern1);
        intersection.retainAll(pattern2);

        Set<String> union = new HashSet<>(pattern1);
        union.addAll(pattern2);

        return (double) intersection.size() / union.size();
    }
}