
import java.util.*;
import java.io.IOException;
import java.io.BufferedReader; // Đọc file dòng–theo–dòng

import java.io.FileReader;
// import java.io.IOException;
// import java.util.*; // List, Set, ArrayList, HashSet,...
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

        for (int i = 0; i < measures.size(); i++) {
            SimilarityMeasure sim = measures.get(i);
            String name = names.get(i);

            // Tạo danh sách lưu kết quả cho từng độ đo
            List<ResultRow> results = new ArrayList<>();

            for (double minSupRatio : minSups) {
                int absSup = (int) Math.ceil(minSupRatio * database.size());

                long start = System.currentTimeMillis();
                ClosedPatternMining miner = new ClosedPatternMining(absSup);
                Set<Set<String>> closed = miner.run(database);
                List<Set<String>> filtered = SimilarityChecker.checkSimilarity(closed, minSim, sim);
                long end = System.currentTimeMillis();

                // Thêm kết quả vào danh sách
                results.add(new ResultRow(minSupRatio, end - start, closed.size(), filtered.size()));
            }

            // Xuất ra file Excel cho từng độ đo
            String fileName = "output_" + name + ".xlsx";
            try {
                ExcelExporter.export(results, fileName);
                System.out.println("Đã xuất kết quả ra file: " + fileName);
            } catch (Exception e) {
                System.err.println("Lỗi xuất file Excel: " + e.getMessage());
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
