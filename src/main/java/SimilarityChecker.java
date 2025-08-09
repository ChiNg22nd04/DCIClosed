import java.util.*;

public class SimilarityChecker {
    // Interface dùng để tính độ tương đồng (Jaccard, Dice, Kulczynski, ...)
    private final SimilarityMeasure measure;

    // Đếm số lần thực hiện so sánh similarity (để phân tích hiệu năng)
    private int comparisonCount = 0;

    // Khởi tạo với một loại measure cụ thể
    public SimilarityChecker(SimilarityMeasure measure) {
        this.measure = measure;
        this.comparisonCount = 0;
    }

    public int getComparisonCount() {
        return comparisonCount;
    }

    public void resetComparisonCount() {
        this.comparisonCount = 0;
    }

    /**
     * Lọc danh sách closedPatterns dựa trên minSim.
     * - closedPatterns: tập các pattern đã được mining (đóng)
     * - minSim: ngưỡng độ tương đồng tối thiểu
     * - batchSize: chỉ so sánh với batch cuối cùng thay vì so sánh toàn bộ
     */
    public List<Set<String>> checkSimilarityBatch(Set<Set<String>> closedPatterns,
                                                  double minSim, int batchSize) {
        comparisonCount = 0; // Reset bộ đếm
        List<Set<String>> filtered = new ArrayList<>(); // Danh sách patterns sau khi lọc
        List<Set<String>> patterns = new ArrayList<>(closedPatterns); // Chuyển sang list để dễ sắp xếp

        // Sắp xếp patterns theo kích thước tăng dần để giảm so sánh không cần thiết
        patterns.sort(Comparator.comparingInt(Set::size));

        System.out.println("   🔍 Checking similarity for " + patterns.size() + " patterns");

        int processed = 0;
        for (Set<String> X : patterns) {
            processed++;

            // In log mỗi khi xử lý được 1000 patterns
            if (processed % 1000 == 0) {
                System.out.println("   📊 Processed " + processed + "/" + patterns.size() +
                        " patterns, filtered: " + filtered.size() +
                        ", comparisons: " + comparisonCount);
            }

            boolean isSimilar = false;

            // So sánh X với các pattern mới nhất trong batch
            int batchStart = Math.max(0, filtered.size() - batchSize);

            for (int i = batchStart; i < filtered.size(); i++) {
                Set<String> Y = filtered.get(i);

                // Nếu chênh lệch kích thước quá lớn → bỏ qua (không cần check similarity)
                if (canSkipSimilarityCheck(X, Y)) {
                    continue;
                }

                try {
                    // Mỗi lần tính similarity thì tăng bộ đếm
                    comparisonCount++;
                    double sim = measure.compute(X, Y);

                    // Nếu similarity >= ngưỡng thì X bị coi là tương đồng, bỏ qua
                    if (sim >= minSim) {
                        isSimilar = true;
                        break;
                    }
                } catch (Exception e) {
                    continue; // Nếu có lỗi thì bỏ qua so sánh này
                }
            }

            // Nếu X không giống pattern nào đã chọn → thêm vào filtered
            if (!isSimilar) {
                filtered.add(X);
            }

            // Giới hạn số lượng patterns để tránh tràn bộ nhớ
            if (filtered.size() > 10000) {
                System.out.println("   ⚠️ Đã đạt giới hạn patterns, dừng similarity check");
                break;
            }
        }

        System.out.println("   ✅ Similarity check completed: " + filtered.size() +
                " patterns after filtering, " + comparisonCount + " comparisons made");
        return filtered;
    }

    /**
     * Hàm tối ưu — bỏ qua similarity check nếu chênh lệch kích thước giữa X và Y quá lớn.
     * Ví dụ: sizeRatio < 0.1 nghĩa là một pattern quá nhỏ so với pattern kia → không cần so sánh.
     */
    private static boolean canSkipSimilarityCheck(Set<String> X, Set<String> Y) {
        int sizeX = X.size();
        int sizeY = Y.size();

        if (sizeX > 0 && sizeY > 0) {
            double sizeRatio = (double) Math.min(sizeX, sizeY) / Math.max(sizeX, sizeY);
            if (sizeRatio < 0.1) {
                return true; // Bỏ qua so sánh
            }
        }

        return false;
    }
}
