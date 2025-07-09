import java.util.*;
import java.io.*;
import java.text.DecimalFormat;

public class Main {
    public static void main(String[] args) {
        // Danh sách các dataset và cấu hình minSup phù hợp
        Map<String, double[]> datasetConfigs = new LinkedHashMap<>();
        
        datasetConfigs.put("mushrooms.txt", new double[]{0.005, 0.01, 0.02, 0.03, 0.04, 0.05});
        datasetConfigs.put("retail.txt", new double[]{0.001, 0.002, 0.003, 0.004, 0.005, 0.006});
        datasetConfigs.put("chess.txt", new double[]{0.6, 0.65, 0.7, 0.75, 0.8, 0.85});
        datasetConfigs.put("kosarak.txt", new double[]{0.003, 0.004, 0.005, 0.006});

        Map<String, Map<Double, Map<String, ResultRow>>> allDatasetResults = new LinkedHashMap<>();

        for (Map.Entry<String, double[]> entry : datasetConfigs.entrySet()) {
            String dataset = entry.getKey();
            double[] minSups = entry.getValue();
            
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

            List<SimilarityMeasure> measures = Arrays.asList(jaccard, dice, kulc);
            List<String> names = Arrays.asList("Jaccard", "Dice", "Kulczynski");

            double minSim = 0.3;
            Map<Double, Map<String, ResultRow>> summaryMap = new TreeMap<>();

            // ✅ SỬA LỖI: Chạy từng thuật toán riêng biệt cho mỗi minSup
            for (double minSupRatio : minSups) {
                int absSup = Math.max(1, (int) Math.ceil(minSupRatio * database.size()));
                System.out.printf("\n🔄 Processing minSup: %.3f (%d transactions)\n", minSupRatio, absSup);

                summaryMap.putIfAbsent(minSupRatio, new LinkedHashMap<>());

                // ✅ SỬA LỖI: Chạy RIÊNG BIỆT cho từng thuật toán
                for (int i = 0; i < measures.size(); i++) {
                    SimilarityMeasure sim = measures.get(i);
                    String name = names.get(i);
                    System.out.printf("   ▶ Thuật toán: %s\n", name);

                    try {
                        // ✅ QUAN TRỌNG: Tạo riêng instance cho mỗi thuật toán
                        System.gc();
                        Thread.sleep(100);
                        
                        Runtime runtime = Runtime.getRuntime();
                        long beforeMem = runtime.totalMemory() - runtime.freeMemory();
                        
                        // ✅ Đo từng bước riêng biệt
                        long totalStart = System.currentTimeMillis();
                        
                        // Bước 1: Mining (tạo riêng cho mỗi thuật toán)
                        long miningStart = System.currentTimeMillis();
                        ClosedPatternMining miner = new ClosedPatternMining(absSup);
                        miner.setMaxRuntime(300000); // 5 phút timeout
                        miner.setMaxPatterns(50000); // Giới hạn patterns
                        
                        Set<Set<String>> closed = miner.run(database);
                        long miningEnd = System.currentTimeMillis();
                        
                        if (closed == null) {
                            System.err.println("     ⚠️ Timeout - bỏ qua");
                            continue;
                        }
                        
                        // ✅ Lấy riêng candidates cho từng thuật toán
                        int miningCandidates = miner.getCandidatesGenerated();
                        
                        // Bước 2: Similarity checking (đo riêng)
                        long filterStart = System.currentTimeMillis();
                        SimilarityChecker checker = new SimilarityChecker(sim);
                        List<Set<String>> filtered = checker.checkSimilarityBatch(
                            closed, minSim, 1000);
                        long filterEnd = System.currentTimeMillis();
                        
                        // ✅ Lấy similarity comparison count
                        int similarityComparisons = checker.getComparisonCount();
                        
                        long totalEnd = System.currentTimeMillis();
                        
                        // Đo memory sau khi hoàn thành
                        System.gc();
                        long afterMem = runtime.totalMemory() - runtime.freeMemory();

                        // ✅ Tính toán metrics riêng biệt cho từng thuật toán
                        double usedMemMb = Math.max(0, (afterMem - beforeMem) / (1024.0 * 1024.0));
                        long totalRuntimeMs = totalEnd - totalStart;
                        long miningTime = miningEnd - miningStart;
                        long filterTime = filterEnd - filterStart;
                        
                        // ✅ Tổng candidates = mining + similarity comparisons
                        int totalCandidates = miningCandidates + similarityComparisons;

                        // ✅ Tạo result row riêng cho từng thuật toán
                        ResultRow row = new ResultRow(minSupRatio, totalRuntimeMs, usedMemMb, 
                            closed.size(), filtered.size(), totalCandidates);
                        
                        // Thêm thông tin chi tiết
                        row.miningTime = miningTime;
                        row.filterTime = filterTime;
                        row.miningCandidates = miningCandidates;
                        row.similarityComparisons = similarityComparisons;

                        summaryMap.get(minSupRatio).put(name, row);
                        
                        System.out.printf("     ✅ Closed: %d, Filtered: %d\n", closed.size(), filtered.size());
                        System.out.printf("     ⏱️ Mining: %dms, Filter: %dms, Total: %dms\n", 
                            miningTime, filterTime, totalRuntimeMs);
                        System.out.printf("     💾 Memory: %.2fMB\n", usedMemMb);
                        System.out.printf("     🔢 Mining candidates: %d, Similarity comparisons: %d, Total: %d\n", 
                            miningCandidates, similarityComparisons, totalCandidates);
                            
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

        // Export kết quả
        if (!allDatasetResults.isEmpty()) {
            try {
                ExcelExporter.exportMultipleSheetsSummary(allDatasetResults, "All_Datasets_Summary.xlsx");
                System.out.println("\n✅ Đã xuất toàn bộ kết quả vào file All_Datasets_Summary.xlsx");
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
                System.out.println("     - Avg runtime: " + df.format(stats.avgRuntime()) + "ms");
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