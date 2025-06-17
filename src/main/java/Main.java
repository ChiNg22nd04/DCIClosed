import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) {
        List<Set<String>> database = loadDatabase("mushrooms.txt");

        // Khởi tạo các độ đo tương đồng
        JaccardSimilarity jaccard = new JaccardSimilarity(database);
        DiceSimilarity dice = new DiceSimilarity();
        KulczynskiSimilarity kulc = new KulczynskiSimilarity();

        List<SimilarityMeasure> measures = Arrays.asList(jaccard, dice, kulc);
        List<String> names = Arrays.asList("Jaccard", "Dice", "Kulczynski");

        double[] minSups = {0.005, 0.006, 0.007, 0.008, 0.009, 0.01};
        double minSim = 0.3;

        // Tạo map để chứa kết quả của từng độ đo
        Map<String, List<ResultRow>> allResults = new LinkedHashMap<>();

        for (int i = 0; i < measures.size(); i++) {
            SimilarityMeasure sim = measures.get(i);
            String name = names.get(i);

            List<ResultRow> results = new ArrayList<>();

            for (double minSupRatio : minSups) {
                int absSup = (int) Math.ceil(minSupRatio * database.size());

                // Dọn dẹp bộ nhớ và đo bộ nhớ ban đầu
                Runtime runtime = Runtime.getRuntime();
                runtime.gc(); // Yêu cầu dọn dẹp bộ nhớ trước khi đo
                long beforeMem = runtime.totalMemory() - runtime.freeMemory();

                // Bắt đầu đo thời gian
                long start = System.currentTimeMillis();

                ClosedPatternMining miner = new ClosedPatternMining(absSup);
                Set<Set<String>> closed = miner.run(database);
                List<Set<String>> filtered = SimilarityChecker.checkSimilarity(closed, minSim, sim);

                // Kết thúc đo thời gian và đo bộ nhớ
                long end = System.currentTimeMillis();
                long afterMem = runtime.totalMemory() - runtime.freeMemory();

                double usedMemMb = (afterMem - beforeMem) / (1024.0 * 1024.0); // Convert to MB
                long runtimeMs = end - start;

                results.add(new ResultRow(minSupRatio, runtimeMs, usedMemMb, closed.size(), filtered.size()));
            }

            allResults.put(name, results); // Lưu kết quả vào map
        }

        // Gộp và xuất ra Excel với nhiều sheet
        String outputFileName = "output_AllSimilarities.xlsx";
        try {
            ExcelExporter.exportMultipleSheets(allResults, outputFileName);
            System.out.println("✅ Đã xuất tất cả kết quả vào file: " + outputFileName);
        } catch (Exception e) {
            System.err.println("❌ Lỗi xuất file Excel: " + e.getMessage());
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
            System.err.println("❌ Lỗi đọc file: " + e.getMessage());
        }
        return db;
    }
}
