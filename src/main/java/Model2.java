import java.util.*;
import java.io.*;
import java.text.DecimalFormat;

public class Model2 {
    public static void main(String[] args) {
        // ✅ Cấu hình minSup riêng cho từng dataset
        Map<String, double[]> datasetConfigs = new LinkedHashMap<>();
        datasetConfigs.put("mushrooms.txt", new double[]{0.005, 0.006, 0.007, 0.008, 0.009, 0.010});
        datasetConfigs.put("retail.txt", new double[]{0.005, 0.006, 0.007, 0.008, 0.009, 0.010});
        datasetConfigs.put("chess.txt", new double[]{0.600, 0.620, 0.640, 0.660, 0.680, 0.700});
        datasetConfigs.put("kosarak.txt", new double[]{0.005, 0.006, 0.007, 0.008, 0.009, 0.010});

        // ✅ Cấu hình minSim riêng cho từng dataset
        Map<String, Double> datasetMinSims = new LinkedHashMap<>();
        datasetMinSims.put("mushrooms.txt", 0.3);
        datasetMinSims.put("retail.txt", 0.3);
        datasetMinSims.put("chess.txt", 0.5);
        datasetMinSims.put("kosarak.txt", 0.3);

        Map<String, Map<Double, Map<String, ResultRow>>> allDatasetResults = new LinkedHashMap<>();

        for (Map.Entry<String, double[]> entry : datasetConfigs.entrySet()) {
            String dataset = entry.getKey();
            double[] minSups = entry.getValue();
            double minSim = datasetMinSims.getOrDefault(dataset, 0.3);

            String datasetName = dataset.replace(".txt", "").toUpperCase();

            System.out.println("\n===============================");
            System.out.println("\uD83D\uDCC1 Đang xử lý dataset: " + datasetName);
            System.out.println("===============================");

            List<Set<String>> database = loadDatabase(dataset);
            if (database.isEmpty()) {
                System.err.println("❌ Không có dữ liệu để xử lý: " + dataset);
                continue;
            }

            System.out.println("✅ Đã load " + database.size() + " transactions");
            analyzeDataset(database, datasetName);

            System.gc();
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            JaccardSimilarity jaccard = new JaccardSimilarity(database);
            DiceSimilarity dice = new DiceSimilarity();
            KulczynskiSimilarity kulc = new KulczynskiSimilarity();

            List<SimilarityMeasure> measures = Arrays.asList(jaccard, dice, kulc);
            List<String> names = Arrays.asList("Jaccard", "Dice", "Kulczynski");

            Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>();

            for (double minSupRatio : minSups) {
                int absSup = Math.max(1, (int) Math.ceil(minSupRatio * database.size()));
                System.out.printf("\n\uD83D\uDD04 Processing minSup: %.3f (%d transactions)\n", minSupRatio, absSup);

                summaryMap.putIfAbsent(minSupRatio, new LinkedHashMap<>());

                System.gc();
                try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                for (int i = 0; i < measures.size(); i++) {
                    SimilarityMeasure sim = measures.get(i);
                    String name = names.get(i);
                    System.out.printf("   ▶ Thuật toán: %s\n", name);

                    try {
                        for (int gc = 0; gc < 5; gc++) { System.gc(); Thread.sleep(100); }

                        Runtime runtime = Runtime.getRuntime();
                        long baselineMem = runtime.totalMemory() - runtime.freeMemory();

                        long totalStart = System.currentTimeMillis();

                        ClosedPatternMining miner = new ClosedPatternMining(absSup);
                        miner.setMaxRuntime(300000);
                        miner.setMaxPatterns(50000);

                        long miningStart = System.currentTimeMillis();
                        Set<Set<String>> closed = miner.run(database);
                        long miningEnd = System.currentTimeMillis();

                        if (closed == null) {
                            System.err.println("     ⚠️ Timeout - bỏ qua");
                            continue;
                        }

                        long afterMiningMem = runtime.totalMemory() - runtime.freeMemory();
                        int miningCandidates = miner.getCandidatesGenerated();

                        long filterStart = System.currentTimeMillis();
                        SimilarityChecker checker = new SimilarityChecker(sim);
                        List<Set<String>> filtered = checker.checkSimilarityBatch(closed, minSim, 1000);
                        long filterEnd = System.currentTimeMillis();

                        int similarityComparisons = checker.getComparisonCount();

                        long totalEnd = System.currentTimeMillis();
                        long finalMem = runtime.totalMemory() - runtime.freeMemory();

                        long peakMemoryUsage = Math.max(afterMiningMem, finalMem) - baselineMem;
                        double usedMemMb = Math.max(0, peakMemoryUsage) / (1024.0 * 1024.0);

                        long totalRuntimeMs = totalEnd - totalStart;
                        long miningTime = miningEnd - miningStart;
                        long filterTime = filterEnd - filterStart;
                        int totalCandidates = miningCandidates + similarityComparisons;

                        ResultRow row = new ResultRow(minSupRatio, totalRuntimeMs, usedMemMb,
                                closed.size(), filtered.size(), totalCandidates);

                        row.miningTime = miningTime;
                        row.filterTime = filterTime;
                        row.miningCandidates = miningCandidates;
                        row.similarityComparisons = similarityComparisons;

                        summaryMap.get(minSupRatio).put(name, row);

                    } catch (Exception e) {
                        System.err.println("     ❌ Lỗi: " + e.getMessage());
                        ResultRow errorRow = new ResultRow(minSupRatio, -1, -1, 0, 0, 0);
                        summaryMap.get(minSupRatio).put(name, errorRow);
                    }
                }
            }

            if (!summaryMap.isEmpty()) {
                allDatasetResults.put(datasetName, summaryMap);
            }
        }

        if (!allDatasetResults.isEmpty()) {
            try {
                ExcelExporter.exportMultipleSheetsSummary(allDatasetResults, "All_Datasets_Summary_Model2.xlsx");
                System.out.println("\n✅ Đã xuất toàn bộ kết quả vào file All_Datasets_Summary_Model2.xlsx");
                generateSummaryStatistics(allDatasetResults);
            } catch (Exception e) {
                System.err.println("❌ Lỗi xuất file: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("❌ Không có kết quả nào để xuất");
        }
    }

        private static List<Set<String>> loadDatabase(String filename) {
        List<Set<String>> db = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineCount = 0;

            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (lineCount % 10000 == 0) {
                    System.out.print(".");
                }

                if (!line.trim().isEmpty()) {
                    String[] items;

                    if (line.contains("\t")) {
                        items = line.trim().split("\t");
                    } else {
                        items = line.trim().split("\\s+");
                    }

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
                System.out.println();
            }

        } catch (IOException e) {
            System.err.println("❌ Lỗi đọc file " + filename + ": " + e.getMessage());
        }

        return db;
    }

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

    private static void generateSummaryStatistics(Map<String, Map<Double, Map<String, ResultRow>>> allResults) {
        System.out.println("\n📈 TỔNG KẾT KẾT QUẢ:");
        DecimalFormat df = new DecimalFormat("#,###");

        for (Map.Entry<String, Map<Double, Map<String, ResultRow>>> datasetEntry : allResults.entrySet()) {
            String dataset = datasetEntry.getKey();
            System.out.println("\n" + dataset + ":");

            // Tính toán riêng cho từng thuật toán
            Map<String, AlgorithmStats> algoStats = new HashMap<>();

            for (Map.Entry<Double, Map<String, ResultRow>> supEntry : datasetEntry.getValue().entrySet()) {
                for (Map.Entry<String, ResultRow> algoEntry : supEntry.getValue().entrySet()) {
                    String algoName = algoEntry.getKey();
                    ResultRow row = algoEntry.getValue();

                    if (row.runtimeMs > 0) {
                        algoStats.computeIfAbsent(algoName, k -> new AlgorithmStats())
                                .addResult(row);
                    }
                }
            }

            // In kết quả cho từng thuật toán
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

    // Helper class để tính statistics
    static class AlgorithmStats {
        long totalRuntime = 0;
        double totalMemory = 0;
        int totalPatterns = 0;
        int totalCandidates = 0;
        int count = 0;

        void addResult(ResultRow row) {
            totalRuntime += row.runtimeMs;
            totalMemory += row.memoryUsageMb;
            totalPatterns += row.filteredPatterns;
            totalCandidates += row.candidatesGenerated;
            count++;
        }

        double avgRuntime() { return count > 0 ? (double) totalRuntime / count : 0; }
        double avgMemory() { return count > 0 ? totalMemory / count : 0; }
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