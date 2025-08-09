import java.util.*;
import java.io.*;
import java.text.DecimalFormat;
/**
 * Model2
 * ------
 * Chạy thí nghiệm với:
 *   - NHIỀU minSup (tỷ lệ) cho MỖI dataset
 *   - MỘT minSim cố định cho MỖI dataset
 * và so sánh 3 độ đo tương đồng (Jaccard/Dice/Kulczynski) trên tập mẫu đóng đã khai thác.
 *
 * Khác biệt chính so với Model1:
 *   - Model1: minSup cố định, quét NHIỀU minSim
 *   - Model2: minSim cố định, quét NHIỀU minSup
 *
 * Luồng:
 *   [load dataset] -> [analyze] -> lặp qua minSup:
 *       -> [mine closed itemsets với absSup]
 *       -> [filter theo minSim với SimilarityMeasure]
 *       -> [ghi nhận thời gian, bộ nhớ, số ứng viên… vào ResultRow]
 *   -> [đưa vào allDatasetResults]
 *   -> [export Excel] + [in thống kê tổng hợp]
 *
 * Lưu ý:
 *   - Các class phụ trợ (ClosedPatternMining, SimilarityChecker, ResultRow, ExcelExporter, …) cần có sẵn trong project.
 *   - Đo bộ nhớ/thời gian chỉ là tương đối (GC có thể gây nhiễu). Có mục “Gợi ý tối ưu” trong comment bên dưới.
 */
public class Model2 {
    public static void main(String[] args) {
        // 1) CẤU HÌNH minSup (tỷ lệ) cho từng dataset: sẽ chuyển sang absSup = ceil(ratio * |DB|)
        Map<String, double[]> datasetConfigs = new LinkedHashMap<>();
        datasetConfigs.put("mushrooms.txt", new double[]{0.005, 0.006, 0.007, 0.008, 0.009, 0.010});
        datasetConfigs.put("retail.txt",    new double[]{0.005, 0.006, 0.007, 0.008, 0.009, 0.010});
        datasetConfigs.put("chess.txt",     new double[]{0.600, 0.620, 0.640, 0.660, 0.680, 0.700}); // dữ liệu dày đặc hơn
        datasetConfigs.put("kosarak.txt",   new double[]{0.005, 0.006, 0.007, 0.008, 0.009, 0.010});

        // 2) CẤU HÌNH minSim cố định (một giá trị) cho từng dataset
        Map<String, Double> datasetMinSims = new LinkedHashMap<>();
        datasetMinSims.put("mushrooms.txt", 0.3);
        datasetMinSims.put("retail.txt",    0.3);
        datasetMinSims.put("chess.txt",     0.5);
        datasetMinSims.put("kosarak.txt",   0.3);

        // Cấu trúc kết quả cuối: Map<DATASET, Map<minSupRatio, Map<MeasureName, ResultRow>>>
        Map<String, Map<Double, Map<String, ResultRow>>> allDatasetResults = new LinkedHashMap<>();

        // 3) VÒNG LẶP THEO DATASET
        for (Map.Entry<String, double[]> entry : datasetConfigs.entrySet()) {
            String dataset = entry.getKey();
            double[] minSups = entry.getValue();
            double minSim = datasetMinSims.getOrDefault(dataset, 0.3); // minSim cố định cho dataset

            String datasetName = dataset.replace(".txt", "").toUpperCase();

            System.out.println("\n===============================");
            System.out.println("\uD83D\uDCC1 Đang xử lý dataset: " + datasetName);
            System.out.println("===============================");

            // 3.1) Đọc file thành List<transaction>; mỗi transaction là Set<String> item
            List<Set<String>> database = loadDatabase(dataset);
            if (database.isEmpty()) {
                System.err.println("❌ Không có dữ liệu để xử lý: " + dataset);
                continue;
            }

            System.out.println("✅ Đã load " + database.size() + " transactions");
            analyzeDataset(database, datasetName); // in thống kê nhanh (transactions, items, density...)

            // “Gợi ý” GC để baseline bộ nhớ ổn hơn (chỉ mang tính tương đối)
            System.gc();
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // 3.2) Chuẩn bị 3 độ đo tương đồng
            // Jaccard nhận database (để tự chuẩn bị TID-set/cache nếu cần)
            JaccardSimilarity jaccard = new JaccardSimilarity(database);
            DiceSimilarity dice = new DiceSimilarity();
            KulczynskiSimilarity kulc = new KulczynskiSimilarity();

            List<SimilarityMeasure> measures = Arrays.asList(jaccard, dice, kulc);
            List<String> names = Arrays.asList("Jaccard", "Dice", "Kulczynski");

            // Kết quả theo từng minSup: Map<minSupRatio, Map<MeasureName, ResultRow>>
            Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>(); // TreeMap để in ra theo thứ tự tăng dần

            // 4) VÒNG LẶP THEO minSupRatio (khác Model1)
            for (double minSupRatio : minSups) {
                // absSup = số giao dịch tối thiểu
                int absSup = Math.max(1, (int) Math.ceil(minSupRatio * database.size()));
                System.out.printf("\n\uD83D\uDD04 Processing minSup: %.3f (%d transactions)\n", minSupRatio, absSup);

                summaryMap.putIfAbsent(minSupRatio, new LinkedHashMap<>());

                // “Gợi ý” GC giữa các minSup để hạn chế nhiễu
                System.gc();
                try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                // 5) VÒNG LẶP THEO ĐỘ ĐO TƯƠNG ĐỒNG
                for (int i = 0; i < measures.size(); i++) {
                    SimilarityMeasure sim = measures.get(i);
                    String name = names.get(i);
                    System.out.printf("   ▶ Thuật toán: %s\n", name);

                    try {
                        // Thêm một vài nhịp GC ngắn (có thể lược bớt cho đỡ tốn thời gian)
                        for (int gc = 0; gc < 5; gc++) { System.gc(); Thread.sleep(100); }

                        Runtime runtime = Runtime.getRuntime();
                        long baselineMem = runtime.totalMemory() - runtime.freeMemory(); // baseline tương đối

                        long totalStart = System.currentTimeMillis(); // thời gian tổng cho (minSup, measure)

                        // 5.1) KHAI THÁC MẪU ĐÓNG với absSup
                        ClosedPatternMining miner = new ClosedPatternMining(absSup);
                        miner.setMaxRuntime(300000); // 300s để tránh chạy quá dài
                        miner.setMaxPatterns(50000); // cắt bùng nổ mẫu

                        long miningStart = System.currentTimeMillis();
                        Set<Set<String>> closed = miner.run(database);
                        long miningEnd = System.currentTimeMillis();

                        if (closed == null) {
                            // run() tự trả về null khi timeout/hủy bỏ
                            System.err.println("     ⚠️ Timeout - bỏ qua");
                            continue;
                        }

                        long afterMiningMem = runtime.totalMemory() - runtime.freeMemory();
                        int miningCandidates = miner.getCandidatesGenerated(); // ứng viên đã tạo trong pha mining

                        // 5.2) LỌC THEO ĐỘ TƯƠNG ĐỒNG (minSim cố định theo dataset)
                        long filterStart = System.currentTimeMillis();
                        SimilarityChecker checker = new SimilarityChecker(sim);
                        // batchSize=1000 -> tránh bùng RAM khi so sánh cặp
                        List<Set<String>> filtered = checker.checkSimilarityBatch(closed, minSim, 1000);
                        long filterEnd = System.currentTimeMillis();

                        int similarityComparisons = checker.getComparisonCount(); // số phép so sánh similarity

                        long totalEnd = System.currentTimeMillis();
                        long finalMem = runtime.totalMemory() - runtime.freeMemory();

                        // 5.3) ƯỚC LƯỢNG BỘ NHỚ DÙNG
                        // Dùng “đỉnh tương đối” giữa sau-mining và cuối-quy-trình
                        long peakMemoryUsage = Math.max(afterMiningMem, finalMem) - baselineMem;
                        // Nếu âm do GC -> chặn 0
                        double usedMemMb = Math.max(0, peakMemoryUsage) / (1024.0 * 1024.0);

                        // 5.4) THỜI GIAN
                        long totalRuntimeMs = totalEnd - totalStart;
                        long miningTime = miningEnd - miningStart;
                        long filterTime = filterEnd - filterStart;

                        // Tổng “độ nặng” quy trình = ứng viên mining + số so sánh similarity
                        int totalCandidates = miningCandidates + similarityComparisons;

                        // 5.5) GHI KẾT QUẢ
                        ResultRow row = new ResultRow(
                                minSupRatio,          // (ở Model2, ResultRow.field đầu tiên lưu minSupRatio)
                                totalRuntimeMs,       // tổng thời gian
                                usedMemMb,            // MB
                                closed.size(),        // số mẫu đóng sinh ra
                                filtered.size(),      // số mẫu còn lại sau lọc similarity
                                totalCandidates       // tổng “độ nặng” (mining + similarity)
                        );

                        // Ghi thêm chi tiết vào row
                        row.miningTime = miningTime;
                        row.filterTime = filterTime;
                        row.miningCandidates = miningCandidates;
                        row.similarityComparisons = similarityComparisons;

                        // Lưu theo tên measure
                        summaryMap.get(minSupRatio).put(name, row);

                    } catch (Exception e) {
                        // Có lỗi -> vẫn ghi 1 row để giữ cấu trúc
                        System.err.println("     ❌ Lỗi: " + e.getMessage());
                        ResultRow errorRow = new ResultRow(minSupRatio, -1, -1, 0, 0, 0);
                        summaryMap.get(minSupRatio).put(name, errorRow);
                    }
                } // end for measure
            } // end for minSup

            // Lưu kết quả dataset vào tổng thể
            if (!summaryMap.isEmpty()) {
                allDatasetResults.put(datasetName, summaryMap);
            }
        } // end for dataset

        // 6) XUẤT EXCEL + IN THỐNG KÊ TỔNG HỢP
        if (!allDatasetResults.isEmpty()) {
            try {
                // ExcelExporter nên tạo file có sheet theo dataset, hoặc 1 sheet với cột: Dataset/MinSup/Measure/...
                ExcelExporter.exportModel2Summary(allDatasetResults, "All_Datasets_Summary_Model2.xlsx");
                System.out.println("\n✅ Đã xuất toàn bộ kết quả vào file All_Datasets_Summary_Model2.xlsx");

                // In thống kê tổng hợp ra console
                generateSummaryStatistics(allDatasetResults);
            } catch (Exception e) {
                System.err.println("❌ Lỗi xuất file: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("❌ Không có kết quả nào để xuất");
        }
    }

    /**
     * Đọc file dữ liệu thành List<Set<String>> (mỗi dòng = 1 transaction).
     * - Hỗ trợ phân tách bằng tab hoặc khoảng trắng (\\s+).
     * - Thêm logging dấu chấm mỗi 10k dòng để biết đang đọc tiến độ.
     * - Lọc item rỗng (trim) trước khi thêm.
     *
     * @param filename tên file dataset (.txt)
     * @return danh sách transaction
     */
    private static List<Set<String>> loadDatabase(String filename) {
        List<Set<String>> db = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineCount = 0;

            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (lineCount % 10000 == 0) {
                    System.out.print("."); // đánh dấu tiến độ đọc
                }

                if (!line.trim().isEmpty()) {
                    String[] items;

                    // Có tab -> ưu tiên tách theo tab; ngược lại tách theo mọi khoảng trắng
                    // Gợi ý: có thể luôn dùng "\\s+" để đơn giản hóa
                    if (line.contains("\t")) {
                        items = line.trim().split("\t");
                    } else {
                        items = line.trim().split("\\s+");
                    }

                    // Dùng Set để loại trùng item trong cùng transaction
                    Set<String> transaction = new HashSet<>();
                    for (String item : items) {
                        if (!item.trim().isEmpty()) {
                            transaction.add(item.trim());
                        }
                    }

                    if (!transaction.isEmpty()) {
                        db.add(transaction);
                    }
                }
            }

            if (lineCount >= 10000) {
                System.out.println(); // xuống dòng sau khi in dấu chấm
            }

        } catch (IOException e) {
            System.err.println("❌ Lỗi đọc file " + filename + ": " + e.getMessage());
        }

        return db;
    }

    /**
     * In thống kê nhanh của dataset:
     *  - Số transactions
     *  - Số items unique
     *  - Kích thước transaction min/max/avg
     *  - “Mật độ” xấp xỉ = avgTranSize / |allItems|
     */
    private static void analyzeDataset(List<Set<String>> database, String datasetName) {
        if (database.isEmpty()) return;

        int totalTransactions = database.size();
        Set<String> allItems = new HashSet<>();
        int totalItems = 0;
        int minTranSize = Integer.MAX_VALUE;
        int maxTranSize = 0;

        for (Set<String> transaction : database) {
            allItems.addAll(transaction);
            totalItems += transaction.size();
            minTranSize = Math.min(minTranSize, transaction.size());
            maxTranSize = Math.max(maxTranSize, transaction.size());
        }

        double avgTranSize = (double) totalItems / totalTransactions;

        System.out.println("📊 Thông tin dataset " + datasetName + ":");
        System.out.println("   - Số transactions: " + totalTransactions);
        System.out.println("   - Số items unique: " + allItems.size());
        System.out.println("   - Kích thước transaction: min=" + minTranSize +
                ", max=" + maxTranSize + ", avg=" + String.format("%.2f", avgTranSize));
        System.out.println("   - Mật độ: " + String.format("%.4f", avgTranSize / allItems.size()));
    }

    /**
     * In tổng kết theo từng DATASET và theo từng THUẬT TOÁN (measure).
     * - Tính trung bình runtime (ms), memory (MB), tổng số patterns (sau lọc), tổng candidates.
     * - Chỉ tính các row có runtimeMs > 0 (loại trừ case lỗi).
     *
     * @param allResults Map<Dataset, Map<minSup, Map<Measure, ResultRow>>>
     */
    private static void generateSummaryStatistics(Map<String, Map<Double, Map<String, ResultRow>>> allResults) {
        System.out.println("\n📈 TỔNG KẾT KẾT QUẢ:");
        DecimalFormat df = new DecimalFormat("#,###");

        for (Map.Entry<String, Map<Double, Map<String, ResultRow>>> datasetEntry : allResults.entrySet()) {
            String dataset = datasetEntry.getKey();
            System.out.println("\n" + dataset + ":");

            // Gom theo measure để tính trung bình/tổng
            Map<String, AlgorithmStats> algoStats = new HashMap<>();

            for (Map.Entry<Double, Map<String, ResultRow>> supEntry : datasetEntry.getValue().entrySet()) {
                for (Map.Entry<String, ResultRow> algoEntry : supEntry.getValue().entrySet()) {
                    String algoName = algoEntry.getKey();
                    ResultRow row = algoEntry.getValue();

                    // runtimeMs > 0 => row hợp lệ
                    if (row.runtimeMs > 0) {
                        algoStats.computeIfAbsent(algoName, k -> new AlgorithmStats())
                                .addResult(row);
                    }
                }
            }

            // In số liệu tổng hợp theo thuật toán
            for (Map.Entry<String, AlgorithmStats> entry : algoStats.entrySet()) {
                AlgorithmStats stats = entry.getValue();
                System.out.println("   " + entry.getKey() + ":");
                System.out.println("     - Avg runtime: " + String.format("%.3f", stats.avgRuntime() / 1000.0) + "s");
                System.out.println("     - Avg memory: " + String.format("%.2f", stats.avgMemory()) + "MB");
                System.out.println("     - Total patterns: " + df.format(stats.totalPatterns));
                System.out.println("     - Total candidates: " + df.format(stats.totalCandidates));
            }
        }
    }

    // ==========================
    // Helper class thống kê gọn
    // ==========================
    static class AlgorithmStats {
        long totalRuntime = 0;     // tổng thời gian (ms)
        double totalMemory = 0;    // tổng MB
        int totalPatterns = 0;     // tổng số mẫu (đÃ lọc) across các minSup
        int totalCandidates = 0;   // tổng # (miningCandidates + similarityComparisons)
        int count = 0;             // số row hợp lệ

        void addResult(ResultRow row) {
            totalRuntime += row.runtimeMs;
            totalMemory += row.memoryUsageMb;
            totalPatterns += row.filteredPatterns;
            totalCandidates += row.candidatesGenerated;
            count++;
        }

        double avgRuntime() { return count > 0 ? (double) totalRuntime / count : 0; }
        double avgMemory()  { return count > 0 ? totalMemory / count : 0; }
    }
}




// import java.util.*;
// import java.io.*;
// import java.text.DecimalFormat;

// public class Model2 {
//     public static void main(String[] args) {
//         // Danh sách các dataset và cấu hình minSup phù hợp
//         Map<String, double[]> datasetConfigs = new LinkedHashMap<>();

//         datasetConfigs.put("mushrooms.txt", new double[]{0.005, 0.006, 0.007, 0.008, 0.009, 0.010});
//         datasetConfigs.put("retail.txt", new double[]{0.001, 0.002, 0.003, 0.004, 0.005, 0.006});
//         datasetConfigs.put("chess.txt", new double[]{0.005, 0.006, 0.007, 0.008, 0.009, 0.010});
//         datasetConfigs.put("kosarak.txt", new double[]{0.005, 0.006, 0.007, 0.008, 0.009, 0.010});

//         Map<String, Map<Double, Map<String, ResultRow>>> allDatasetResults = new LinkedHashMap<>();

//         for (Map.Entry<String, double[]> entry : datasetConfigs.entrySet()) {
//             String dataset = entry.getKey();
//             double[] minSups = entry.getValue();

//             String datasetName = dataset.replace(".txt", "").toUpperCase();

//             System.out.println("\n===============================");
//             System.out.println("📁 Đang xử lý dataset: " + datasetName);
//             System.out.println("===============================");

//             List<Set<String>> database = loadDatabase(dataset);
//             if (database.isEmpty()) {
//                 System.err.println("❌ Không có dữ liệu để xử lý: " + dataset);
//                 continue;
//             }

//             System.out.println("✅ Đã load " + database.size() + " transactions");
//             analyzeDataset(database, datasetName);

//             // ✅ FORCE GC và reset memory cho dataset mới
//             System.gc();
//             try {
//                 Thread.sleep(500); // Đợi GC hoàn tất cho dataset mới
//             } catch (InterruptedException e) {
//                 Thread.currentThread().interrupt();
//             }

//             // Khởi tạo các độ đo tương đồng
//             JaccardSimilarity jaccard = new JaccardSimilarity(database);
//             DiceSimilarity dice = new DiceSimilarity();
//             KulczynskiSimilarity kulc = new KulczynskiSimilarity();

//             List<SimilarityMeasure> measures = Arrays.asList(jaccard, dice, kulc);
//             List<String> names = Arrays.asList("Jaccard", "Dice", "Kulczynski");

//             double minSim = 0.3;
//             Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>();

//             // ✅ SỬA LỖI: Chạy từng thuật toán riêng biệt cho mỗi minSup
//             for (double minSupRatio : minSups) {
//                 int absSup = Math.max(1, (int) Math.ceil(minSupRatio * database.size()));
//                 System.out.printf("\n🔄 Processing minSup: %.3f (%d transactions)\n", minSupRatio, absSup);

//                 summaryMap.putIfAbsent(minSupRatio, new LinkedHashMap<>());

//                 // ✅ Force GC trước khi bắt đầu minSup mới
//                 System.gc();
//                 try {
//                     Thread.sleep(300);
//                 } catch (InterruptedException e) {
//                     Thread.currentThread().interrupt();
//                 }

//                 // ✅ SỬA LỖI: Chạy RIÊNG BIỆT cho từng thuật toán
//                 for (int i = 0; i < measures.size(); i++) {
//                     SimilarityMeasure sim = measures.get(i);
//                     String name = names.get(i);
//                     System.out.printf("   ▶ Thuật toán: %s\n", name);

//                     try {
//                         // ✅ QUAN TRỌNG: Reset hoàn toàn memory state cho từng thuật toán
//                         for (int gc = 0; gc < 5; gc++) {
//                             System.gc();
//                             Thread.sleep(100);
//                         }

//                         Runtime runtime = Runtime.getRuntime();

//                         // ✅ Đo memory baseline riêng cho từng thuật toán
//                         long baselineMem = runtime.totalMemory() - runtime.freeMemory();
//                         System.out.printf("     🔍 Baseline memory: %.2fMB\n", baselineMem / (1024.0 * 1024.0));

//                         // ✅ Đo từng bước riêng biệt
//                         long totalStart = System.currentTimeMillis();

//                         // Bước 1: Mining (tạo riêng cho mỗi thuật toán)
//                         long miningStart = System.currentTimeMillis();
//                         ClosedPatternMining miner = new ClosedPatternMining(absSup);
//                         miner.setMaxRuntime(300000); // 5 phút timeout
//                         miner.setMaxPatterns(50000); // Giới hạn patterns

//                         Set<Set<String>> closed = miner.run(database);
//                         long miningEnd = System.currentTimeMillis();

//                         if (closed == null) {
//                             System.err.println("     ⚠️ Timeout - bỏ qua");
//                             continue;
//                         }

//                         // ✅ Đo memory sau mining (không force GC để giữ memory peak)
//                         long afterMiningMem = runtime.totalMemory() - runtime.freeMemory();

//                         // ✅ Lấy riêng candidates cho từng thuật toán
//                         int miningCandidates = miner.getCandidatesGenerated();

//                         // Bước 2: Similarity checking (đo riêng)
//                         long filterStart = System.currentTimeMillis();
//                         SimilarityChecker checker = new SimilarityChecker(sim);
//                         List<Set<String>> filtered = checker.checkSimilarityBatch(
//                                 closed, minSim, 1000);
//                         long filterEnd = System.currentTimeMillis();

//                         // ✅ Lấy similarity comparison count
//                         int similarityComparisons = checker.getComparisonCount();

//                         long totalEnd = System.currentTimeMillis();

//                         // ✅ Đo memory cuối cùng (memory peak)
//                         long finalMem = runtime.totalMemory() - runtime.freeMemory();

//                         // ✅ Tính toán memory usage: lấy peak memory usage
//                         long peakMemoryUsage = Math.max(afterMiningMem, finalMem) - baselineMem;
//                         double usedMemMb = Math.max(0, peakMemoryUsage) / (1024.0 * 1024.0);

//                         System.out.printf("     📊 Memory details: Mining=%.2fMB, Final=%.2fMB, Peak Usage=%.2fMB\n",
//                                 (afterMiningMem - baselineMem) / (1024.0 * 1024.0),
//                                 (finalMem - baselineMem) / (1024.0 * 1024.0),
//                                 usedMemMb);

//                         long totalRuntimeMs = totalEnd - totalStart;
//                         long miningTime = miningEnd - miningStart;
//                         long filterTime = filterEnd - filterStart;

//                         // ✅ Tổng candidates = mining + similarity comparisons
//                         int totalCandidates = miningCandidates + similarityComparisons;

//                         // ✅ Tạo result row riêng cho từng thuật toán
//                         ResultRow row = new ResultRow(minSupRatio, totalRuntimeMs, usedMemMb,
//                                 closed.size(), filtered.size(), totalCandidates);

//                         // Thêm thông tin chi tiết
//                         row.miningTime = miningTime;
//                         row.filterTime = filterTime;
//                         row.miningCandidates = miningCandidates;
//                         row.similarityComparisons = similarityComparisons;

//                         summaryMap.get(minSupRatio).put(name, row);

//                         System.out.printf("     ✅ Closed: %d, Filtered: %d\n", closed.size(), filtered.size());
//                         System.out.printf("     ⏱️ Mining: %.3fs, Filter: %.3fs, Total: %.3fs\n",
//                                 miningTime/1000.0, filterTime/1000.0, totalRuntimeMs/1000.0);
//                         System.out.printf("     💾 Memory: %.2fMB (Peak: %.2fMB)\n", usedMemMb,
//                                 Math.max(afterMiningMem, finalMem) / (1024.0 * 1024.0));
//                         System.out.printf("     🔢 Mining candidates: %d, Similarity comparisons: %d, Total: %d\n",
//                                 miningCandidates, similarityComparisons, totalCandidates);

//                     } catch (Exception e) {
//                         System.err.println("     ❌ Lỗi: " + e.getMessage());
//                         ResultRow errorRow = new ResultRow(minSupRatio, -1, -1, 0, 0, 0);
//                         summaryMap.get(minSupRatio).put(name, errorRow);
//                     }
//                 }
//             }

//             if (!summaryMap.isEmpty()) {
//                 allDatasetResults.put(datasetName, summaryMap);
//             }
//         }

//         // Export kết quả
//         if (!allDatasetResults.isEmpty()) {
//             try {
//                 ExcelExporter.exportMultipleSheetsSummary(allDatasetResults, "All_Datasets_Summary_Model2.xlsx");
//                 System.out.println("\n✅ Đã xuất toàn bộ kết quả vào file All_Datasets_Summary_Model2.xlsx");
//                 generateSummaryStatistics(allDatasetResults);

//             } catch (Exception e) {
//                 System.err.println("❌ Lỗi xuất file: " + e.getMessage());
//                 e.printStackTrace();
//             }
//         } else {
//             System.err.println("❌ Không có kết quả nào để xuất");
//         }
//     }

//     private static List<Set<String>> loadDatabase(String filename) {
//         List<Set<String>> db = new ArrayList<>();

//         try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
//             String line;
//             int lineCount = 0;

//             while ((line = reader.readLine()) != null) {
//                 lineCount++;
//                 if (lineCount % 10000 == 0) {
//                     System.out.print(".");
//                 }

//                 if (!line.trim().isEmpty()) {
//                     String[] items;

//                     if (line.contains("\t")) {
//                         items = line.trim().split("\t");
//                     } else {
//                         items = line.trim().split("\\s+");
//                     }

//                     Set<String> transaction = new HashSet<>();
//                     for (String item : items) {
//                         if (!item.trim().isEmpty()) {
//                             transaction.add(item.trim());
//                         }
//                     }

//                     if (!transaction.isEmpty()) {
//                         db.add(transaction);
//                     }
//                 }
//             }

//             if (lineCount >= 10000) {
//                 System.out.println();
//             }

//         } catch (IOException e) {
//             System.err.println("❌ Lỗi đọc file " + filename + ": " + e.getMessage());
//         }

//         return db;
//     }

//     private static void analyzeDataset(List<Set<String>> database, String datasetName) {
//         if (database.isEmpty()) return;

//         int totalTransactions = database.size();
//         Set<String> allItems = new HashSet<>();
//         int totalItems = 0;
//         int minTranSize = Integer.MAX_VALUE;
//         int maxTranSize = 0;

//         for (Set<String> transaction : database) {
//             allItems.addAll(transaction);
//             totalItems += transaction.size();
//             minTranSize = Math.min(minTranSize, transaction.size());
//             maxTranSize = Math.max(maxTranSize, transaction.size());
//         }

//         double avgTranSize = (double) totalItems / totalTransactions;

//         System.out.println("📊 Thông tin dataset " + datasetName + ":");
//         System.out.println("   - Số transactions: " + totalTransactions);
//         System.out.println("   - Số items unique: " + allItems.size());
//         System.out.println("   - Kích thước transaction: min=" + minTranSize +
//                 ", max=" + maxTranSize + ", avg=" + String.format("%.2f", avgTranSize));
//         System.out.println("   - Mật độ: " + String.format("%.4f", avgTranSize / allItems.size()));
//     }

//     private static void generateSummaryStatistics(Map<String, Map<Double, Map<String, ResultRow>>> allResults) {
//         System.out.println("\n📈 TỔNG KẾT KẾT QUẢ:");
//         DecimalFormat df = new DecimalFormat("#,###");

//         for (Map.Entry<String, Map<Double, Map<String, ResultRow>>> datasetEntry : allResults.entrySet()) {
//             String dataset = datasetEntry.getKey();
//             System.out.println("\n" + dataset + ":");

//             // Tính toán riêng cho từng thuật toán
//             Map<String, AlgorithmStats> algoStats = new HashMap<>();

//             for (Map.Entry<Double, Map<String, ResultRow>> supEntry : datasetEntry.getValue().entrySet()) {
//                 for (Map.Entry<String, ResultRow> algoEntry : supEntry.getValue().entrySet()) {
//                     String algoName = algoEntry.getKey();
//                     ResultRow row = algoEntry.getValue();

//                     if (row.runtimeMs > 0) {
//                         algoStats.computeIfAbsent(algoName, k -> new AlgorithmStats())
//                                 .addResult(row);
//                     }
//                 }
//             }

//             // In kết quả cho từng thuật toán
//             for (Map.Entry<String, AlgorithmStats> entry : algoStats.entrySet()) {
//                 AlgorithmStats stats = entry.getValue();
//                 System.out.println("   " + entry.getKey() + ":");
//                 System.out.println("     - Avg runtime: " + String.format("%.3f", stats.avgRuntime() / 1000.0) + "s");
//                 System.out.println("     - Avg memory: " + String.format("%.2f", stats.avgMemory()) + "MB");
//                 System.out.println("     - Total patterns: " + df.format(stats.totalPatterns));
//                 System.out.println("     - Total candidates: " + df.format(stats.totalCandidates));
//             }
//         }
//     }

//     // Helper class để tính statistics
//     static class AlgorithmStats {
//         long totalRuntime = 0;
//         double totalMemory = 0;
//         int totalPatterns = 0;
//         int totalCandidates = 0;
//         int count = 0;

//         void addResult(ResultRow row) {
//             totalRuntime += row.runtimeMs;
//             totalMemory += row.memoryUsageMb;
//             totalPatterns += row.filteredPatterns;
//             totalCandidates += row.candidatesGenerated;
//             count++;
//         }

//         double avgRuntime() { return count > 0 ? (double) totalRuntime / count : 0; }
//         double avgMemory() { return count > 0 ? totalMemory / count : 0; }
//     }
// }