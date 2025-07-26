import java.util.*;
import java.io.*;

public class Model1 {
    public static void main(String[] args) {
        // Dataset và minSup
        Map<String, Double> datasetSupConfigs = new LinkedHashMap<>();
        datasetSupConfigs.put("mushrooms.txt", 0.005);
        datasetSupConfigs.put("retail.txt", 0.005);
        datasetSupConfigs.put("chess.txt", 0.064);
        datasetSupConfigs.put("kosarak.txt", 0.005);

        // Cấu hình minSim riêng cho từng dataset
        Map<String, double[]> minSimConfigs = new LinkedHashMap<>();
        minSimConfigs.put("mushrooms.txt", new double[]{0.1, 0.2,0.3, 0.4, 0.5, 0.6, 0.7, 0.8});
        minSimConfigs.put("retail.txt", new double[]{0.1, 0.2,0.3, 0.4, 0.5, 0.6, 0.7, 0.8});
        minSimConfigs.put("chess.txt", new double[]{0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0});
        minSimConfigs.put("kosarak.txt", new double[]{0.1, 0.2,0.3, 0.4, 0.5, 0.6, 0.7, 0.8});

        Map<String, Map<Double, Map<String, ResultRow>>> allDatasetResults = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : datasetSupConfigs.entrySet()) {
            String dataset = entry.getKey();
            double fixedMinSupRatio = entry.getValue();
            String datasetName = dataset.replace(".txt", "").toUpperCase();

            System.out.println("\n===============================");
            System.out.println("📁 Đang xử lý dataset: " + datasetName);
            System.out.println("===============================");

            List<Set<String>> database = loadDatabase(dataset);
            if (database.isEmpty()) {
                System.err.println("❌ Không có dữ liệu để xử lý: " + dataset);
                continue;
            }

            System.out.println("✅ Đã load " + database.size() + " transactions");
            analyzeDataset(database, datasetName);

            // Khởi tạo các độ đo tương đồng
            JaccardSimilarity jaccard = new JaccardSimilarity(database);
            DiceSimilarity dice = new DiceSimilarity();
            KulczynskiSimilarity kulc = new KulczynskiSimilarity();

            Map<String, SimilarityMeasure> measureMap = new LinkedHashMap<>();
            measureMap.put("Jaccard", jaccard);
            measureMap.put("Dice", dice);
            measureMap.put("Kulczynski", kulc);

            int absSup = Math.max(1, (int) Math.ceil(fixedMinSupRatio * database.size()));
            System.out.printf("\n🔒 minSup cố định: %.3f (%d transactions)\n", fixedMinSupRatio, absSup);

            double[] minSims = minSimConfigs.getOrDefault(dataset, new double[]{0.5});
            Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>();

            for (double minSim : minSims) {
                for (Map.Entry<String, SimilarityMeasure> simEntry : measureMap.entrySet()) {
                    String name = simEntry.getKey();
                    SimilarityMeasure sim = simEntry.getValue();

                    System.out.printf("\n🔄 Thuật toán: %s - minSim: %.2f\n", name, minSim);
                    summaryMap.putIfAbsent(minSim, new LinkedHashMap<>());

                    try {
                        Runtime runtime = Runtime.getRuntime();
                        long baselineMem = runtime.totalMemory() - runtime.freeMemory();
                        long totalStart = System.currentTimeMillis();

                        // Khai thác mẫu đóng
                        long miningStart = System.currentTimeMillis();
                        ClosedPatternMining miner = new ClosedPatternMining(absSup);
                        miner.setMaxRuntime(300000);
                        miner.setMaxPatterns(50000);
                        Set<Set<String>> closed = miner.run(database);
                        long miningEnd = System.currentTimeMillis();

                        if (closed == null) {
                            System.err.println("     ⚠️ Timeout - bỏ qua");
                            continue;
                        }

                        long afterMiningMem = runtime.totalMemory() - runtime.freeMemory();
                        int miningCandidates = miner.getCandidatesGenerated();

                        // Kiểm tra tương đồng
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

                        ResultRow row = new ResultRow(minSim, totalRuntimeMs, usedMemMb,
                                closed.size(), filtered.size(), totalCandidates);
                        row.miningTime = miningTime;
                        row.filterTime = filterTime;
                        row.miningCandidates = miningCandidates;
                        row.similarityComparisons = similarityComparisons;

                        summaryMap.get(minSim).put(name, row);

                        System.out.printf("     ✅ Closed: %d, Filtered: %d\n", closed.size(), filtered.size());
                        System.out.printf("     ⏱️ Mining: %.3fs, Filter: %.3fs, Total: %.3fs\n",
                                miningTime / 1000.0, filterTime / 1000.0, totalRuntimeMs / 1000.0);
                        System.out.printf("     💾 Memory: %.2fMB (Peak: %.2fMB)\n", usedMemMb,
                                Math.max(afterMiningMem, finalMem) / (1024.0 * 1024.0));
                        System.out.printf("     🔢 Mining candidates: %d, Similarity comparisons: %d, Total: %d\n",
                                miningCandidates, similarityComparisons, totalCandidates);

                    } catch (Exception e) {
                        System.err.println("     ❌ Lỗi: " + e.getMessage());
                        ResultRow errorRow = new ResultRow(minSim, -1, -1, 0, 0, 0);
                        summaryMap.get(minSim).put(name, errorRow);
                    }
                }
            }

            if (!summaryMap.isEmpty()) {
                allDatasetResults.put(datasetName, summaryMap);
            }
        }

        // Export kết quả
        if (!allDatasetResults.isEmpty()) {
            try {
                ExcelExporter.exportMultipleSheetsSummary(allDatasetResults, "All_Datasets_Summary_Model1.xlsx");
                System.out.println("\n✅ Đã xuất toàn bộ kết quả vào file All_Datasets_Summary_Model1.xlsx");
            } catch (Exception e) {
                System.err.println("❌ Lỗi xuất file: " + e.getMessage());
            }
        } else {
            System.err.println("❌ Không có kết quả nào để xuất");
        }
    }

    // Load dữ liệu
    private static List<Set<String>> loadDatabase(String filename) {
        List<Set<String>> db = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    String[] items = line.contains("\t") ? line.trim().split("\t") : line.trim().split("\\s+");
                    Set<String> transaction = new HashSet<>(Arrays.asList(items));
                    if (!transaction.isEmpty()) db.add(transaction);
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Lỗi đọc file " + filename + ": " + e.getMessage());
        }
        return db;
    }

    // Phân tích dữ liệu
    private static void analyzeDataset(List<Set<String>> database, String datasetName) {
        if (database.isEmpty()) return;
        int totalTransactions = database.size();
        Set<String> allItems = new HashSet<>();
        int totalItems = 0, minTranSize = Integer.MAX_VALUE, maxTranSize = 0;

        for (Set<String> transaction : database) {
            allItems.addAll(transaction);
            totalItems += transaction.size();
            minTranSize = Math.min(minTranSize, transaction.size());
            maxTranSize = Math.max(maxTranSize, transaction.size());
        }

        double avgTranSize = (double) totalItems / totalTransactions;
        System.out.println("📊 Thông tin dataset " + datasetName + ":");
        System.out.println("   - Transactions: " + totalTransactions);
        System.out.println("   - Unique items: " + allItems.size());
        System.out.println("   - Transaction size: min=" + minTranSize + ", max=" + maxTranSize + ", avg=" + String.format("%.2f", avgTranSize));
        System.out.println("   - Density: " + String.format("%.4f", avgTranSize / allItems.size()));
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
