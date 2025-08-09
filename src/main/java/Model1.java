import java.util.*;
import java.io.*;
/**
 * Model1 - Chạy thí nghiệm khai thác "mẫu đóng thường xuyên" (Closed Frequent Itemsets)
 *          trên nhiều bộ dữ liệu, rồi lọc các mẫu theo độ tương đồng (Jaccard/Dice/Kulczynski)
 *          với nhiều ngưỡng minSim. Cuối cùng tổng hợp số liệu hiệu năng để xuất Excel.
 *
 *  Luồng xử lý tổng quát:
 *  [Đọc dataset] -> [Phân tích thống kê] -> [Khai thác mẫu đóng với absSup] -> [Lọc theo minSim & SimilarityMeasure]
 *                  -> [Ghi nhận thời gian, bộ nhớ, số lượng] -> [Lặp các minSim & measure] -> [Xuất Excel]
 *
 *  Định dạng dữ liệu đầu vào:
 *    - Mỗi dòng trong file .txt là 1 transaction (danh sách item)
 *    - Item trong 1 dòng cách nhau bởi Tab hoặc khoảng trắng
 *
 *  Các class/Interface phụ trợ (cần có trong project):
 *    - ClosedPatternMining: thực hiện khai thác mẫu đóng với constructor ClosedPatternMining(int absSup)
 *        + run(List<Set<String>> database) -> Set<Set<String>> closed
 *        + setMaxRuntime(ms), setMaxPatterns(n), getCandidatesGenerated()
 *    - SimilarityMeasure: interface tính độ tương đồng giữa 2 itemset dựa trên TID-sets (hoặc hỗ trợ khác)
 *    - JaccardSimilarity/DiceSimilarity/KulczynskiSimilarity: 3 triển khai cụ thể của SimilarityMeasure
 *    - SimilarityChecker: nhận SimilarityMeasure, có checkSimilarityBatch(...), getComparisonCount()
 *    - ResultRow: struct lưu số liệu 1 dòng kết quả (thời gian, bộ nhớ, số mẫu...)
 *    - ExcelExporter: export map kết quả -> file Excel (.xlsx)
 */
public class Model1 {
    public static void main(String[] args) {
        /*
         * 1) CẤU HÌNH ĐẦU VÀO
         *   - datasetSupConfigs: ánh xạ <tên file, minSup theo tỉ lệ>
         *       Ví dụ 0.005 nghĩa là 0.5% tổng số giao dịch -> sẽ đổi sang absSup = ceil(ratio * |DB|)
         *   - minSimConfigs: với mỗi dataset, chạy qua một dãy ngưỡng minSim (0..1)
         *       Tác dụng: cùng 1 tập mẫu đóng, thay đổi minSim sẽ giữ lại số mẫu khác nhau
         */
        Map<String, Double> datasetSupConfigs = new LinkedHashMap<>();
        datasetSupConfigs.put("mushrooms.txt", 0.005);
        datasetSupConfigs.put("retail.txt", 0.005);
        datasetSupConfigs.put("chess.txt", 0.064);
        datasetSupConfigs.put("kosarak.txt", 0.005);

        Map<String, double[]> minSimConfigs = new LinkedHashMap<>();
        minSimConfigs.put("mushrooms.txt", new double[]{0.3, 0.4, 0.5, 0.6, 0.7, 0.8});
        minSimConfigs.put("retail.txt", new double[]{0.3, 0.4, 0.5, 0.6, 0.7, 0.8});
        minSimConfigs.put("chess.txt", new double[]{0.5, 0.6, 0.7, 0.8, 0.9, 1.0}); // chess thường dày đặc hơn
        minSimConfigs.put("kosarak.txt", new double[]{0.3, 0.4, 0.5, 0.6, 0.7, 0.8});

        /*
         * Cấu trúc chứa toàn bộ kết quả:
         *   allDatasetResults: Map<DATASET, Map<minSim, Map<MeasureName, ResultRow>>>
         * Dùng LinkedHashMap để giữ thứ tự chèn -> in ra/ghi file có trật tự dễ đọc.
         */
        Map<String, Map<Double, Map<String, ResultRow>>> allDatasetResults = new LinkedHashMap<>();

        // 2) VÒNG LẶP THEO DATASET
        for (Map.Entry<String, Double> entry : datasetSupConfigs.entrySet()) {
            final String dataset = entry.getKey();
            final double fixedMinSupRatio = entry.getValue();
            final String datasetName = dataset.replace(".txt", "").toUpperCase();

            System.out.println("\n===============================");
            System.out.println("📁 Đang xử lý dataset: " + datasetName);
            System.out.println("===============================");

            // 2.1) Đọc database từ file -> List<transaction>, mỗi transaction là Set<String> các item không trùng
            List<Set<String>> database = loadDatabase(dataset);
            if (database.isEmpty()) {
                System.err.println("❌ Không có dữ liệu để xử lý: " + dataset);
                continue; // bỏ qua dataset rỗng/hỏng
            }

            System.out.println("✅ Đã load " + database.size() + " transactions");
            analyzeDataset(database, datasetName); // In thống kê: số giao dịch, item, density...

            // 2.2) Khởi tạo 3 độ đo tương đồng cần kiểm thử
            // Lưu ý: JaccardSimilarity nhận cả database -> bên trong có thể xây TID-sets, caching,...
            JaccardSimilarity jaccard = new JaccardSimilarity(database);
            DiceSimilarity dice = new DiceSimilarity();
            KulczynskiSimilarity kulc = new KulczynskiSimilarity();

            // gom vào map để lặp gọn
            Map<String, SimilarityMeasure> measureMap = new LinkedHashMap<>();
            measureMap.put("Jaccard", jaccard);
            measureMap.put("Dice", dice);
            measureMap.put("Kulczynski", kulc);

            // 2.3) Tính minSup tuyệt đối (absSup) từ tỉ lệ
            // absSup = ceil(ratio * |DB|), tối thiểu là 1 để không rơi về 0.
            final int absSup = Math.max(1, (int) Math.ceil(fixedMinSupRatio * database.size()));
            System.out.printf("\n🔒 minSup cố định: %.3f (%d transactions)\n", fixedMinSupRatio, absSup);

            // 2.4) Lấy danh sách ngưỡng minSim cho dataset này
            final double[] minSims = minSimConfigs.getOrDefault(dataset, new double[]{0.5});
            // Mỗi minSim sẽ có 1 map con: Map<MeasureName, ResultRow>
            Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>(); // TreeMap -> minSim tăng dần

            // 3) VÒNG LẶP THEO MINSIM & THEO MEASURE
            for (double minSim : minSims) {
                for (Map.Entry<String, SimilarityMeasure> simEntry : measureMap.entrySet()) {
                    final String measureName = simEntry.getKey();
                    final SimilarityMeasure measure = simEntry.getValue();

                    System.out.printf("\n🔄 Thuật toán: %s - minSim: %.2f\n", measureName, minSim);
                    summaryMap.putIfAbsent(minSim, new LinkedHashMap<>());

                    try {
                        // 3.1) CHUẨN BỊ ĐO BỘ NHỚ / THỜI GIAN
                        final Runtime runtime = Runtime.getRuntime();

                        // Gợi ý GC để baseline bộ nhớ “sạch” hơn (không đảm bảo tuyệt đối, chỉ tương đối)
                        System.gc();
                        Thread.sleep(100); // chờ GC 1 nhịp
                        final long baselineMem = runtime.totalMemory() - runtime.freeMemory();

                        final long totalStart = System.currentTimeMillis(); // tổng thời gian thí nghiệm cho cặp (minSim, measure)

                        // 3.2) PHA KHAI THÁC MẪU ĐÓNG (CLOSED)
                        final long miningStart = System.currentTimeMillis();
                        ClosedPatternMining miner = new ClosedPatternMining(absSup);
                        miner.setMaxRuntime(300000);   // Giới hạn thời gian 300s để tránh chạy vô hạn
                        miner.setMaxPatterns(50000);   // Giới hạn số mẫu tối đa để tránh bùng nổ bộ nhớ
                        Set<Set<String>> closed = miner.run(database);
                        final long miningEnd = System.currentTimeMillis();

                        // Nếu null -> có thể timeout/bị hủy; bỏ qua tổ hợp này
                        if (closed == null) {
                            System.err.println("     ⚠️ Timeout - bỏ qua");
                            continue;
                        }

                        // Bộ nhớ tiêu thụ ngay sau pha mining (ước lượng “đỉnh” cục bộ)
                        final long afterMiningMem = runtime.totalMemory() - runtime.freeMemory();
                        final int miningCandidates = miner.getCandidatesGenerated(); // số ứng viên đã tạo trong pha mining

                        // 3.3) PHA LỌC THEO ĐỘ TƯƠNG ĐỒNG
                        final long filterStart = System.currentTimeMillis();
                        // SimilarityChecker sẽ dùng 'measure' (Jaccard/Dice/Kulczynski) để so sánh theo batch
                        SimilarityChecker checker = new SimilarityChecker(measure);
                        // checkSimilarityBatch(closed, minSim, 1000) -> lọc các mẫu “na ná” nhau, giữ mẫu đại diện
                        // batchSize=1000 để giảm áp lực bộ nhớ (tuỳ implement)
                        List<Set<String>> filtered = checker.checkSimilarityBatch(closed, minSim, 1000);
                        final long filterEnd = System.currentTimeMillis();

                        final int similarityComparisons = checker.getComparisonCount(); // số lần so sánh similarity đã thực hiện

                        // Kết thúc toàn bộ quy trình (mining + filter)
                        final long totalEnd = System.currentTimeMillis();
                        final long finalMem = runtime.totalMemory() - runtime.freeMemory();

                        // 3.4) TÍNH BỘ NHỚ SỬ DỤNG
                        // Lấy “đỉnh” tương đối giữa sau-mining và cuối-quy-trình
                        final long maxMem = Math.max(afterMiningMem, finalMem);
                        long actualMemoryUsage = maxMem - baselineMem;

                        // ⚠️ Lưu ý: Nếu GC chạy giữa chừng, chênh lệch có thể âm -> fallback
                        if (actualMemoryUsage < 0) {
                            // BUG tiềm ẩn trong bản gốc: không nên nhân runtime.totalMemory() * 1024 * 1024
                            // vì totalMemory() đã là byte. Ở đây dùng fallback an toàn hơn:
                            actualMemoryUsage = Math.max(0L, maxMem - baselineMem); // hoặc chỉ dùng maxMem
                        }
                        final double usedMemMb = actualMemoryUsage / (1024.0 * 1024.0);

                        // 3.5) TỔNG HỢP THỜI GIAN
                        final long totalRuntimeMs = totalEnd - totalStart;
                        final long miningTime = miningEnd - miningStart;
                        final long filterTime = filterEnd - filterStart;
                        final int totalCandidates = miningCandidates + similarityComparisons;

                        // 3.6) GHI KẾT QUẢ VÀO DÒNG ResultRow
                        ResultRow row = new ResultRow(
                                minSim,
                                totalRuntimeMs,
                                usedMemMb,
                                closed.size(),     // số mẫu đóng sinh ra
                                filtered.size(),   // số mẫu còn lại sau lọc similarity
                                totalCandidates    // tổng độ nặng: ứng viên mining + số so sánh similarity
                        );
                        // Ghi thêm chi tiết phụ
                        row.miningTime = miningTime;
                        row.filterTime = filterTime;
                        row.miningCandidates = miningCandidates;
                        row.similarityComparisons = similarityComparisons;

                        summaryMap.get(minSim).put(measureName, row);

                        // 3.7) LOG RA MÀN HÌNH ĐỂ THEO DÕI
                        System.out.printf("     ✅ Closed: %d, Filtered: %d\n", closed.size(), filtered.size());
                        System.out.printf("     ⏱️ Mining: %.3fs, Filter: %.3fs, Total: %.3fs\n",
                                miningTime / 1000.0, filterTime / 1000.0, totalRuntimeMs / 1000.0);
                        System.out.printf("     💾 Memory: %.2fMB (Peak approx: %.2fMB)\n",
                                usedMemMb, Math.max(afterMiningMem, finalMem) / (1024.0 * 1024.0));
                        System.out.printf("     🔢 Mining candidates: %d, Similarity comparisons: %d, Total: %d\n",
                                miningCandidates, similarityComparisons, totalCandidates);

                    } catch (Exception e) {
                        // Bất kỳ lỗi nào trong vòng (minSim, measure) -> Ghi 1 dòng lỗi để vẫn xuất Excel đầy đủ cấu trúc
                        System.err.println("     ❌ Lỗi: " + e.getMessage());
                        ResultRow errorRow = new ResultRow(minSim, -1, -1, 0, 0, 0);
                        summaryMap.get(minSim).put(measureName, errorRow);
                    }
                } // end for measure
            } // end for minSim

            // 3.8) Sau khi chạy xong mọi measure cho mọi minSim -> lưu kết quả dataset này
            if (!summaryMap.isEmpty()) {
                allDatasetResults.put(datasetName, summaryMap);
            }
        } // end for dataset

        // 4) XUẤT EXCEL TỔNG HỢP
        //   - Thường ExcelExporter sẽ tạo mỗi dataset 1 sheet, hoặc 1 sheet lớn có cột Dataset/MinSim/Measure/...
        if (!allDatasetResults.isEmpty()) {
            try {
                ExcelExporter.exportModel1Summary(allDatasetResults, "All_Datasets_Summary_Model1.xlsx");
                System.out.println("\n✅ Đã xuất toàn bộ kết quả vào file All_Datasets_Summary_Model1.xlsx");
            } catch (Exception e) {
                System.err.println("❌ Lỗi xuất file: " + e.getMessage());
            }
        } else {
            System.err.println("❌ Không có kết quả nào để xuất");
        }
    }

    /**
     * Đọc file dữ liệu thành List<Set<String>> (mỗi Set là 1 transaction).
     * - Chấp nhận dữ liệu ngăn cách bởi tab hoặc khoảng trắng.
     * - Dùng HashSet để loại trùng item trong cùng transaction.
     *
     * @param filename tên file, ví dụ "mushrooms.txt"
     * @return danh sách transaction; rỗng nếu đọc lỗi/không có dữ liệu hợp lệ
     */
    private static List<Set<String>> loadDatabase(String filename) {
        List<Set<String>> db = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Bỏ qua dòng trống/space
                if (!line.trim().isEmpty()) {
                    /*
                     * Chọn split:
                     *  - Nếu có tab thì split theo tab, ngược lại split theo \\s+ (mọi khoảng trắng)
                     *  - Mẹo an toàn: có thể luôn dùng line.trim().split("\\s+") để không lệ thuộc tab
                     */
                    String[] items = line.contains("\t")
                            ? line.trim().split("\t")
                            : line.trim().split("\\s+");

                    // Tạo transaction; HashSet để loại trùng item trong cùng dòng
                    Set<String> transaction = new HashSet<>(Arrays.asList(items));

                    if (!transaction.isEmpty()) {
                        db.add(transaction);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Lỗi đọc file " + filename + ": " + e.getMessage());
        }
        return db;
    }

    /**
     * In ra các thống kê nhanh của dataset để hiểu “độ dày” và hình dạng dữ liệu:
     *  - Transactions: số dòng (giao dịch)
     *  - Unique items: số item khác nhau trong toàn DB
     *  - Transaction size: min, max, avg (độ dài mỗi giao dịch)
     *  - Density xấp xỉ: avgTranSize / |allItems|
     *      -> Giá trị càng cao = dữ liệu càng “dày đặc”
     *
     * @param database   danh sách transaction đã load
     * @param datasetName tên (để in log)
     */
    private static void analyzeDataset(List<Set<String>> database, String datasetName) {
        if (database.isEmpty()) return;

        final int totalTransactions = database.size();
        final Set<String> allItems = new HashSet<>();
        int totalItems = 0;
        int minTranSize = Integer.MAX_VALUE;
        int maxTranSize = 0;

        // Duyệt toàn bộ giao dịch để thống kê
        for (Set<String> transaction : database) {
            allItems.addAll(transaction);               // gom hết item duy nhất
            totalItems += transaction.size();           // cộng độ dài
            minTranSize = Math.min(minTranSize, transaction.size());
            maxTranSize = Math.max(maxTranSize, transaction.size());
        }

        final double avgTranSize = (double) totalItems / totalTransactions;
        final double density = allItems.isEmpty() ? 0.0 : (avgTranSize / allItems.size());

        // In thống kê ra console
        System.out.println("📊 Thông tin dataset " + datasetName + ":");
        System.out.println("   - Transactions: " + totalTransactions);
        System.out.println("   - Unique items: " + allItems.size());
        System.out.println("   - Transaction size: min=" + minTranSize
                + ", max=" + maxTranSize
                + ", avg=" + String.format("%.2f", avgTranSize));
        System.out.println("   - Density: " + String.format("%.4f", density));
    }
}




// import java.util.*;
// import java.io.*;

// public class Model1 {
//     public static void main(String[] args) {
//         // Dataset và minSup
//         Map<String, Double> datasetSupConfigs = new LinkedHashMap<>();
//         datasetSupConfigs.put("mushrooms.txt", 0.005);
//         datasetSupConfigs.put("retail.txt", 0.005);
//         datasetSupConfigs.put("chess.txt", 0.064);
//         datasetSupConfigs.put("kosarak.txt", 0.005);

//         // Cấu hình minSim riêng cho từng độ đo
//         Map<String, double[]> minSimConfigs = new LinkedHashMap<>();
//         minSimConfigs.put("Jaccard", new double[]{0.3, 0.4, 0.5});
//         minSimConfigs.put("Dice", new double[]{0.5, 0.6, 0.7});
//         minSimConfigs.put("Kulczynski", new double[]{0.2, 0.3, 0.4});

//         Map<String, Map<Double, Map<String, ResultRow>>> allDatasetResults = new LinkedHashMap<>();

//         for (Map.Entry<String, Double> entry : datasetSupConfigs.entrySet()) {
//             String dataset = entry.getKey();
//             double fixedMinSupRatio = entry.getValue();
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

//             // Khởi tạo các độ đo tương đồng
//             JaccardSimilarity jaccard = new JaccardSimilarity(database);
//             DiceSimilarity dice = new DiceSimilarity();
//             KulczynskiSimilarity kulc = new KulczynskiSimilarity();

//             Map<String, SimilarityMeasure> measureMap = new LinkedHashMap<>();
//             measureMap.put("Jaccard", jaccard);
//             measureMap.put("Dice", dice);
//             measureMap.put("Kulczynski", kulc);

//             int absSup = Math.max(1, (int) Math.ceil(fixedMinSupRatio * database.size()));
//             System.out.printf("\n🔒 minSup cố định: %.3f (%d transactions)\n", fixedMinSupRatio, absSup);

//             Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>();

//             for (Map.Entry<String, SimilarityMeasure> simEntry : measureMap.entrySet()) {
//                 String name = simEntry.getKey();
//                 SimilarityMeasure sim = simEntry.getValue();

//                 double[] minSims = minSimConfigs.getOrDefault(name, new double[]{0.5});
//                 for (double minSim : minSims) {
//                     System.out.printf("\n🔄 Thuật toán: %s - minSim: %.2f\n", name, minSim);
//                     summaryMap.putIfAbsent(minSim, new LinkedHashMap<>());

//                     try {
//                         Runtime runtime = Runtime.getRuntime();
//                         long baselineMem = runtime.totalMemory() - runtime.freeMemory();
//                         long totalStart = System.currentTimeMillis();

//                         // Khai thác mẫu đóng
//                         long miningStart = System.currentTimeMillis();
//                         ClosedPatternMining miner = new ClosedPatternMining(absSup);
//                         miner.setMaxRuntime(300000);
//                         miner.setMaxPatterns(50000);
//                         Set<Set<String>> closed = miner.run(database);
//                         long miningEnd = System.currentTimeMillis();

//                         if (closed == null) {
//                             System.err.println("     ⚠️ Timeout - bỏ qua");
//                             continue;
//                         }

//                         long afterMiningMem = runtime.totalMemory() - runtime.freeMemory();
//                         int miningCandidates = miner.getCandidatesGenerated();

//                         // Kiểm tra tương đồng
//                         long filterStart = System.currentTimeMillis();
//                         SimilarityChecker checker = new SimilarityChecker(sim);
//                         List<Set<String>> filtered = checker.checkSimilarityBatch(closed, minSim, 1000);
//                         long filterEnd = System.currentTimeMillis();

//                         int similarityComparisons = checker.getComparisonCount();
//                         long totalEnd = System.currentTimeMillis();
//                         long finalMem = runtime.totalMemory() - runtime.freeMemory();

//                         long peakMemoryUsage = Math.max(afterMiningMem, finalMem) - baselineMem;
//                         double usedMemMb = Math.max(0, peakMemoryUsage) / (1024.0 * 1024.0);

//                         long totalRuntimeMs = totalEnd - totalStart;
//                         long miningTime = miningEnd - miningStart;
//                         long filterTime = filterEnd - filterStart;
//                         int totalCandidates = miningCandidates + similarityComparisons;

//                         ResultRow row = new ResultRow(minSim, totalRuntimeMs, usedMemMb,
//                                 closed.size(), filtered.size(), totalCandidates);
//                         row.miningTime = miningTime;
//                         row.filterTime = filterTime;
//                         row.miningCandidates = miningCandidates;
//                         row.similarityComparisons = similarityComparisons;

//                         summaryMap.get(minSim).put(name, row);

//                         System.out.printf("     ✅ Closed: %d, Filtered: %d\n", closed.size(), filtered.size());
//                         System.out.printf("     ⏱️ Mining: %.3fs, Filter: %.3fs, Total: %.3fs\n",
//                                 miningTime/1000.0, filterTime/1000.0, totalRuntimeMs/1000.0);
//                         System.out.printf("     💾 Memory: %.2fMB (Peak: %.2fMB)\n", usedMemMb,
//                                 Math.max(afterMiningMem, finalMem) / (1024.0 * 1024.0));
//                         System.out.printf("     🔢 Mining candidates: %d, Similarity comparisons: %d, Total: %d\n",
//                                 miningCandidates, similarityComparisons, totalCandidates);

//                     } catch (Exception e) {
//                         System.err.println("     ❌ Lỗi: " + e.getMessage());
//                         ResultRow errorRow = new ResultRow(minSim, -1, -1, 0, 0, 0);
//                         summaryMap.get(minSim).put(name, errorRow);
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
//                 ExcelExporter.exportMultipleSheetsSummary(allDatasetResults, "All_Datasets_Summary_Model1.xlsx");
//                 System.out.println("\n✅ Đã xuất toàn bộ kết quả vào file All_Datasets_Summary_Model1.xlsx");
//             } catch (Exception e) {
//                 System.err.println("❌ Lỗi xuất file: " + e.getMessage());
//             }
//         } else {
//             System.err.println("❌ Không có kết quả nào để xuất");
//         }
//     }

//     // Load dữ liệu
//     private static List<Set<String>> loadDatabase(String filename) {
//         List<Set<String>> db = new ArrayList<>();
//         try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
//             String line;
//             while ((line = reader.readLine()) != null) {
//                 if (!line.trim().isEmpty()) {
//                     String[] items = line.contains("\t") ? line.trim().split("\t") : line.trim().split("\\s+");
//                     Set<String> transaction = new HashSet<>(Arrays.asList(items));
//                     if (!transaction.isEmpty()) db.add(transaction);
//                 }
//             }
//         } catch (IOException e) {
//             System.err.println("❌ Lỗi đọc file " + filename + ": " + e.getMessage());
//         }
//         return db;
//     }

//     // Phân tích dữ liệu
//     private static void analyzeDataset(List<Set<String>> database, String datasetName) {
//         if (database.isEmpty()) return;
//         int totalTransactions = database.size();
//         Set<String> allItems = new HashSet<>();
//         int totalItems = 0, minTranSize = Integer.MAX_VALUE, maxTranSize = 0;

//         for (Set<String> transaction : database) {
//             allItems.addAll(transaction);
//             totalItems += transaction.size();
//             minTranSize = Math.min(minTranSize, transaction.size());
//             maxTranSize = Math.max(maxTranSize, transaction.size());
//         }

//         double avgTranSize = (double) totalItems / totalTransactions;
//         System.out.println("📊 Thông tin dataset " + datasetName + ":");
//         System.out.println("   - Transactions: " + totalTransactions);
//         System.out.println("   - Unique items: " + allItems.size());
//         System.out.println("   - Transaction size: min=" + minTranSize + ", max=" + maxTranSize + ", avg=" + String.format("%.2f", avgTranSize));
//         System.out.println("   - Density: " + String.format("%.4f", avgTranSize / allItems.size()));
//     }
// }




// // import java.util.*;
// // import java.io.*;
// // import java.text.DecimalFormat;

// // public class Model1 {
// //     public static void main(String[] args) {
// //         // Dataset và minSim
// //         Map<String, Double> datasetSupConfigs = new LinkedHashMap<>();
// //         datasetSupConfigs.put("mushrooms.txt", 0.01);
// //         datasetSupConfigs.put("retail.txt", 0.003);
// //         datasetSupConfigs.put("chess.txt", 0.7);
// //         datasetSupConfigs.put("kosarak.dat.txt", 0.005);

// //         double[] minSims = new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};
// //         Map<String, Map<Double, Map<String, ResultRow>>> allDatasetResults = new LinkedHashMap<>();

// //         for (Map.Entry<String, Double> entry : datasetSupConfigs.entrySet()) {
// //             String dataset = entry.getKey();
// //             double fixedMinSupRatio = entry.getValue();
// //             String datasetName = dataset.replace(".txt", "").toUpperCase();

// //             System.out.println("\n===============================");
// //             System.out.println("📁 Đang xử lý dataset: " + datasetName);
// //             System.out.println("===============================");

// //             List<Set<String>> database = loadDatabase(dataset);
// //             if (database.isEmpty()) {
// //                 System.err.println("❌ Không có dữ liệu để xử lý: " + dataset);
// //                 continue;
// //             }

// //             System.out.println("✅ Đã load " + database.size() + " transactions");
// //             analyzeDataset(database, datasetName);

// //             // Khởi tạo các độ đo tương đồng
// //             JaccardSimilarity jaccard = new JaccardSimilarity(database);
// //             DiceSimilarity dice = new DiceSimilarity();
// //             KulczynskiSimilarity kulc = new KulczynskiSimilarity();

// //             List<SimilarityMeasure> measures = Arrays.asList(jaccard, dice, kulc);
// //             List<String> names = Arrays.asList("Jaccard", "Dice", "Kulczynski");

// //             int absSup = Math.max(1, (int) Math.ceil(fixedMinSupRatio * database.size()));
// //             System.out.printf("\n🔒 minSup cố định: %.3f (%d transactions)\n", fixedMinSupRatio, absSup);

// //             Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>();

// //             for (double minSim : minSims) {
// //                 System.out.printf("\n🔄 Processing minSim: %.2f\n", minSim);
// //                 summaryMap.putIfAbsent(minSim, new LinkedHashMap<>());

// //                 for (int i = 0; i < measures.size(); i++) {
// //                     SimilarityMeasure sim = measures.get(i);
// //                     String name = names.get(i);
// //                     System.out.printf("   ▶ Thuật toán: %s\n", name);

// //                     try {
// //                         Runtime runtime = Runtime.getRuntime();
// //                         long baselineMem = runtime.totalMemory() - runtime.freeMemory();
// //                         long totalStart = System.currentTimeMillis();

// //                         // Bước 1: Khai thác
// //                         long miningStart = System.currentTimeMillis();
// //                         ClosedPatternMining miner = new ClosedPatternMining(absSup);
// //                         miner.setMaxRuntime(300000);
// //                         miner.setMaxPatterns(50000);
// //                         Set<Set<String>> closed = miner.run(database);
// //                         long miningEnd = System.currentTimeMillis();

// //                         if (closed == null) {
// //                             System.err.println("     ⚠️ Timeout - bỏ qua");
// //                             continue;
// //                         }

// //                         long afterMiningMem = runtime.totalMemory() - runtime.freeMemory();
// //                         int miningCandidates = miner.getCandidatesGenerated();

// //                         // Bước 2: Kiểm tra tương đồng
// //                         long filterStart = System.currentTimeMillis();
// //                         SimilarityChecker checker = new SimilarityChecker(sim);
// //                         List<Set<String>> filtered = checker.checkSimilarityBatch(closed, minSim, 1000);
// //                         long filterEnd = System.currentTimeMillis();

// //                         int similarityComparisons = checker.getComparisonCount();
// //                         long totalEnd = System.currentTimeMillis();
// //                         long finalMem = runtime.totalMemory() - runtime.freeMemory();

// //                         long peakMemoryUsage = Math.max(afterMiningMem, finalMem) - baselineMem;
// //                         double usedMemMb = Math.max(0, peakMemoryUsage) / (1024.0 * 1024.0);

// //                         long totalRuntimeMs = totalEnd - totalStart;
// //                         long miningTime = miningEnd - miningStart;
// //                         long filterTime = filterEnd - filterStart;
// //                         int totalCandidates = miningCandidates + similarityComparisons;

// //                         ResultRow row = new ResultRow(minSim, totalRuntimeMs, usedMemMb,
// //                                 closed.size(), filtered.size(), totalCandidates);
// //                         row.miningTime = miningTime;
// //                         row.filterTime = filterTime;
// //                         row.miningCandidates = miningCandidates;
// //                         row.similarityComparisons = similarityComparisons;

// //                         summaryMap.get(minSim).put(name, row);

// //                         System.out.printf("     ✅ Closed: %d, Filtered: %d\n", closed.size(), filtered.size());
// //                         System.out.printf("     ⏱️ Mining: %.3fs, Filter: %.3fs, Total: %.3fs\n",
// //                                 miningTime/1000.0, filterTime/1000.0, totalRuntimeMs/1000.0);
// //                         System.out.printf("     💾 Memory: %.2fMB (Peak: %.2fMB)\n", usedMemMb,
// //                                 Math.max(afterMiningMem, finalMem) / (1024.0 * 1024.0));
// //                         System.out.printf("     🔢 Mining candidates: %d, Similarity comparisons: %d, Total: %d\n",
// //                                 miningCandidates, similarityComparisons, totalCandidates);

// //                     } catch (Exception e) {
// //                         System.err.println("     ❌ Lỗi: " + e.getMessage());
// //                         ResultRow errorRow = new ResultRow(minSim, -1, -1, 0, 0, 0);
// //                         summaryMap.get(minSim).put(name, errorRow);
// //                     }
// //                 }
// //             }

// //             if (!summaryMap.isEmpty()) {
// //                 allDatasetResults.put(datasetName, summaryMap);
// //             }
// //         }

// //         if (!allDatasetResults.isEmpty()) {
// //             try {
// //                 ExcelExporter.exportMultipleSheetsSummary(allDatasetResults, "All_Datasets_Summary_Model1.xlsx");
// //                 System.out.println("\n✅ Đã xuất toàn bộ kết quả vào file All_Datasets_Summary_Model1.xlsx");
// //             } catch (Exception e) {
// //                 System.err.println("❌ Lỗi xuất file: " + e.getMessage());
// //             }
// //         } else {
// //             System.err.println("❌ Không có kết quả nào để xuất");
// //         }
// //     }

// //     // Load dữ liệu
// //     private static List<Set<String>> loadDatabase(String filename) {
// //         List<Set<String>> db = new ArrayList<>();
// //         try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
// //             String line;
// //             while ((line = reader.readLine()) != null) {
// //                 if (!line.trim().isEmpty()) {
// //                     String[] items = line.contains("\t") ? line.trim().split("\t") : line.trim().split("\\s+");
// //                     Set<String> transaction = new HashSet<>(Arrays.asList(items));
// //                     if (!transaction.isEmpty()) db.add(transaction);
// //                 }
// //             }
// //         } catch (IOException e) {
// //             System.err.println("❌ Lỗi đọc file " + filename + ": " + e.getMessage());
// //         }
// //         return db;
// //     }

// //     // Phân tích dữ liệu
// //     private static void analyzeDataset(List<Set<String>> database, String datasetName) {
// //         if (database.isEmpty()) return;
// //         int totalTransactions = database.size();
// //         Set<String> allItems = new HashSet<>();
// //         int totalItems = 0, minTranSize = Integer.MAX_VALUE, maxTranSize = 0;

// //         for (Set<String> transaction : database) {
// //             allItems.addAll(transaction);
// //             totalItems += transaction.size();
// //             minTranSize = Math.min(minTranSize, transaction.size());
// //             maxTranSize = Math.max(maxTranSize, transaction.size());
// //         }

// //         double avgTranSize = (double) totalItems / totalTransactions;
// //         System.out.println("📊 Thông tin dataset " + datasetName + ":");
// //         System.out.println("   - Transactions: " + totalTransactions);
// //         System.out.println("   - Unique items: " + allItems.size());
// //         System.out.println("   - Transaction size: min=" + minTranSize + ", max=" + maxTranSize + ", avg=" + String.format("%.2f", avgTranSize));
// //         System.out.println("   - Density: " + String.format("%.4f", avgTranSize / allItems.size()));
// //     }
// // }
