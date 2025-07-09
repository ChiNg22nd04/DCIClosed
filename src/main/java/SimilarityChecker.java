import java.util.*;

public class SimilarityChecker {
    private final SimilarityMeasure measure;
    private int comparisonCount = 0;
    
    public SimilarityChecker(SimilarityMeasure measure) {
        this.measure = measure;
        this.comparisonCount = 0;
    }
    
    public int getComparisonCount() {
        return comparisonCount;
    }
    
    public void resetComparisonCount() {
        this.comparisonCount = 0;
    }
    
    public List<Set<String>> checkSimilarityBatch(Set<Set<String>> closedPatterns, 
                                                 double minSim, int batchSize) {
        comparisonCount = 0; // Reset counter
        List<Set<String>> filtered = new ArrayList<>();
        List<Set<String>> patterns = new ArrayList<>(closedPatterns);
        
        // Sort patterns theo size để optimize
        patterns.sort(Comparator.comparingInt(Set::size));
        
        System.out.println("   🔍 Checking similarity for " + patterns.size() + " patterns");
        
        int processed = 0;
        for (Set<String> X : patterns) {
            processed++;
            
            if (processed % 1000 == 0) {
                System.out.println("   📊 Processed " + processed + "/" + patterns.size() + 
                                 " patterns, filtered: " + filtered.size() + 
                                 ", comparisons: " + comparisonCount);
            }
            
            boolean isSimilar = false;
            int batchStart = Math.max(0, filtered.size() - batchSize);
            
            for (int i = batchStart; i < filtered.size(); i++) {
                Set<String> Y = filtered.get(i);
                
                if (canSkipSimilarityCheck(X, Y)) {
                    continue;
                }
                
                try {
                    // ✅ Đếm mỗi lần compute similarity
                    comparisonCount++;
                    double sim = measure.compute(X, Y);
                    
                    if (sim >= minSim) {
                        isSimilar = true;
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            
            if (!isSimilar) {
                filtered.add(X);
            }
            
            if (filtered.size() > 10000) {
                System.out.println("   ⚠️ Đã đạt giới hạn patterns, dừng similarity check");
                break;
            }
        }
        
        System.out.println("   ✅ Similarity check completed: " + filtered.size() + 
                          " patterns after filtering, " + comparisonCount + " comparisons made");
        return filtered;
    }
    
    private static boolean canSkipSimilarityCheck(Set<String> X, Set<String> Y) {
        int sizeX = X.size();
        int sizeY = Y.size();
        
        if (sizeX > 0 && sizeY > 0) {
            double sizeRatio = (double) Math.min(sizeX, sizeY) / Math.max(sizeX, sizeY);
            if (sizeRatio < 0.1) {
                return true;
            }
        }
        
        return false;
    }
}