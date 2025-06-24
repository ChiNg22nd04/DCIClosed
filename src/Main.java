import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
public class Main {
    public static void main(String[] args) {
        List<Set<String>> database = loadDatabase("kosarak.dat.txt");

        // Chỉ tạo BitMatrix 1 lần cho Jaccard
        JaccardSimilarity jaccard = new JaccardSimilarity(database);
        DiceSimilarity dice = new DiceSimilarity();
        KulczynskiSimilarity kulc = new KulczynskiSimilarity();

        List<SimilarityMeasure> measures = Arrays.asList(jaccard, dice, kulc);
        List<String> names = Arrays.asList("Jaccard", "Dice", "Kulczynski");

        double[] minSups = {0.005, 0.006, 0.007, 0.008, 0.009, 0.01};
        double minSim = 0.5;

        for (int i = 0; i < measures.size(); i++) {
            SimilarityMeasure sim = measures.get(i);
            String name = names.get(i);
            System.out.println("\n▶ Similarity: " + name);
            System.out.printf("%-8s %-12s %-18s %-12s\n", "minSup", "runtime(ms)", "closedPatterns", "filtered");

            for (double minSupRatio : minSups) {
                int absSup = (int) Math.ceil(minSupRatio * database.size());

                long start = System.currentTimeMillis();
                ClosedPatternMining miner = new ClosedPatternMining(absSup);
                Set<Set<String>> closed = miner.run(database);
                List<Set<String>> filtered = SimilarityChecker.checkSimilarity(closed, minSim, sim);
                long end = System.currentTimeMillis();

                System.out.printf("%-8.3f %-12d %-18d %-12d\n", minSupRatio, end - start, closed.size(), filtered.size());
            }
        }
    }

    private static List<Set<String>> loadDatabase(String filename) {
        List<Set<String>> db = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    String[] items = line.trim().split("\\s+");
                    db.add(new HashSet<>(Arrays.asList(items)));
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi đọc file: " + e.getMessage());
        }
        return db;
    }
}
