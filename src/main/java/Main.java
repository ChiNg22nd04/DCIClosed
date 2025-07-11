

import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) {
        // Danh sách các file database bạn có
        String[] datasets = {"mushrooms.txt", "retail.txt","kosarak.dat.txt"};

        // Map chứa toàn bộ kết quả: <DatasetName, SummaryMap>
        Map<String, Map<Double, Map<String, ResultRow>>> allDatasetResults = new LinkedHashMap<>();

        for (String dataset : datasets) {
            String datasetName = dataset.replace(".txt", "").toUpperCase(); // VD: MUSHROOMS

            System.out.println("\n===============================");
            System.out.println("📁 Đang xử lý dataset: " + datasetName);
            System.out.println("===============================");

            List<Set<String>> database = loadDatabase(dataset);
            if (database.isEmpty()) {
                System.err.println("❌ Không có dữ liệu để xử lý: " + dataset);
                continue;
            }

            // Khởi tạo các độ đo tương đồng
            JaccardSimilarity jaccard = new JaccardSimilarity(database);
            DiceSimilarity dice = new DiceSimilarity();
            KulczynskiSimilarity kulc = new KulczynskiSimilarity();

            List<SimilarityMeasure> measures = Arrays.asList(jaccard, dice, kulc);
            List<String> names = Arrays.asList("Jaccard", "Dice", "Kulczynski");

            double[] minSups = {0.005, 0.006, 0.007, 0.008, 0.009, 0.01};
            double minSim = 0.3;

            Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>();

            for (int i = 0; i < measures.size(); i++) {
                SimilarityMeasure sim = measures.get(i);
                String name = names.get(i);
                System.out.println("▶ Độ đo: " + name);

                for (double minSupRatio : minSups) {
                    int absSup = (int) Math.ceil(minSupRatio * database.size());
                    System.out.printf("   - minSup: %.3f (%d transactions)\n", minSupRatio, absSup);

                    Runtime runtime = Runtime.getRuntime();
                    runtime.gc();
                    long beforeMem = runtime.totalMemory() - runtime.freeMemory();

                    long start = System.currentTimeMillis();

                    ClosedPatternMining miner = new ClosedPatternMining(absSup);
                    Set<Set<String>> closed = miner.run(database);
                    int candidates = miner.getCandidatesGenerated();
                    List<Set<String>> filtered = SimilarityChecker.checkSimilarity(closed, minSim, sim);

                    long end = System.currentTimeMillis();
                    long afterMem = runtime.totalMemory() - runtime.freeMemory();

                    double usedMemMb = (afterMem - beforeMem) / (1024.0 * 1024.0);
                    long runtimeMs = end - start;

                    ResultRow row = new ResultRow(minSupRatio, runtimeMs, usedMemMb, closed.size(), filtered.size(), candidates);

                    summaryMap.putIfAbsent(minSupRatio, new LinkedHashMap<>());
                    summaryMap.get(minSupRatio).put(name, row);
                }
            }

            allDatasetResults.put(datasetName, summaryMap);
        }

        // Sau khi đã có tất cả kết quả, export ra Excel (nhiều sheet)
        try {
            ExcelExporter.exportMultipleSheetsSummary(allDatasetResults, "All_Datasets_Summary.xlsx");
            System.out.println("\n✅ Đã xuất toàn bộ kết quả vào file All_Datasets_Summary.xlsx");
        }  catch (Exception e) {
            e.printStackTrace(); 
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
