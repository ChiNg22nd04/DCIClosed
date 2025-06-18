import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) {
        // Đọc dữ liệu từ file
        List<Set<String>> database = loadDatabase("mushrooms.txt");
        if (database.isEmpty()) {
            System.err.println("❌ Không có dữ liệu để xử lý.");
            return;
        }

        // Khởi tạo các độ đo tương đồng
        JaccardSimilarity jaccard = new JaccardSimilarity(database);
        DiceSimilarity dice = new DiceSimilarity();
        KulczynskiSimilarity kulc = new KulczynskiSimilarity();

        List<SimilarityMeasure> measures = Arrays.asList(jaccard, dice, kulc);
        List<String> names = Arrays.asList("Jaccard", "Dice", "Kulczynski");

        double[] minSups = {0.005, 0.006, 0.007, 0.008, 0.009, 0.01};
        double minSim = 0.3;

        // Lưu kết quả tổng hợp: Map<minSup, Map<Measure, ResultRow>>
        Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>();

        System.out.println("🔍 Bắt đầu thực nghiệm với các độ đo tương đồng...");

        for (int i = 0; i < measures.size(); i++) {
            SimilarityMeasure sim = measures.get(i);
            String name = names.get(i);
            System.out.println("▶ Độ đo: " + name);

            for (double minSupRatio : minSups) {
                int absSup = (int) Math.ceil(minSupRatio * database.size());
                System.out.printf("   - minSup: %.3f (%d transactions)\n", minSupRatio, absSup);

                Runtime runtime = Runtime.getRuntime();
                runtime.gc(); // Yêu cầu JVM dọn bộ nhớ
                long beforeMem = runtime.totalMemory() - runtime.freeMemory();

                long start = System.currentTimeMillis();

                ClosedPatternMining miner = new ClosedPatternMining(absSup);
                Set<Set<String>> closed = miner.run(database);
                List<Set<String>> filtered = SimilarityChecker.checkSimilarity(closed, minSim, sim);

                long end = System.currentTimeMillis();
                long afterMem = runtime.totalMemory() - runtime.freeMemory();

                double usedMemMb = (afterMem - beforeMem) / (1024.0 * 1024.0);
                long runtimeMs = end - start;

                ResultRow row = new ResultRow(minSupRatio, runtimeMs, usedMemMb, closed.size(), filtered.size());

                summaryMap.putIfAbsent(minSupRatio, new LinkedHashMap<>());
                summaryMap.get(minSupRatio).put(name, row);
            }
        }

        // Ghi kết quả ra file Excel
        String outputFileName = "output_Summary.xlsx";
        try {
            ExcelExporter.exportSummarySheet(summaryMap, outputFileName);
            System.out.println("✅ Đã xuất kết quả ra file: " + outputFileName);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi xuất file Excel: " + e.getMessage());
        }
    }

    // Hàm đọc dữ liệu từ file
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
