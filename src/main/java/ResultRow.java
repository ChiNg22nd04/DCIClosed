public class ResultRow {
    // ====== Các thông tin cơ bản của kết quả ======
    public double minSupRatio;       // Tỷ lệ minSup (minimum support ratio) dùng trong mining
    public long runtimeMs;           // Tổng thời gian chạy (miliseconds) -> đổi từ giây sang ms để đo chính xác hơn
    public double memoryUsageMb;     // Bộ nhớ sử dụng (MB)
    public int closedPatterns;       // Số lượng closed patterns tìm được
    public int filteredPatterns;     // Số lượng patterns còn lại sau khi lọc theo similarity
    public int candidatesGenerated;  // Số lượng candidate patterns được sinh ra

    // ====== Các metrics chi tiết hơn ======
    public long miningTime;           // Thời gian thực hiện mining (ms)
    public long filterTime;           // Thời gian thực hiện bước lọc similarity (ms)
    public int miningCandidates;      // Số lượng candidate được sinh ra trong bước mining
    public int similarityComparisons; // Số lần so sánh similarity được thực hiện

    // ===== Constructor cơ bản =====
    public ResultRow(double minSupRatio, long runtimeMs, double memoryUsageMb,
                     int closedPatterns, int filteredPatterns, int candidatesGenerated) {
        this.minSupRatio = minSupRatio;
        this.runtimeMs = runtimeMs;
        this.memoryUsageMb = memoryUsageMb;
        this.closedPatterns = closedPatterns;
        this.filteredPatterns = filteredPatterns;
        this.candidatesGenerated = candidatesGenerated;

        // Các giá trị chi tiết mặc định là 0 nếu chưa đo
        this.miningTime = 0;
        this.filterTime = 0;
        this.miningCandidates = 0;
        this.similarityComparisons = 0;
    }

    // ===== Constructor đầy đủ (có cả thông tin chi tiết) =====
    public ResultRow(double minSupRatio, long runtimeMs, double memoryUsageMb,
                     int closedPatterns, int filteredPatterns, int candidatesGenerated,
                     long miningTime, long filterTime, int miningCandidates, int similarityComparisons) {
        this.minSupRatio = minSupRatio;
        this.runtimeMs = runtimeMs;
        this.memoryUsageMb = memoryUsageMb;
        this.closedPatterns = closedPatterns;
        this.filteredPatterns = filteredPatterns;
        this.candidatesGenerated = candidatesGenerated;
        this.miningTime = miningTime;
        this.filterTime = filterTime;
        this.miningCandidates = miningCandidates;
        this.similarityComparisons = similarityComparisons;
    }

    // ✅ Tính tỷ lệ lọc: filteredPatterns / closedPatterns
    public double getFilteringRatio() {
        return closedPatterns > 0 ? (double) filteredPatterns / closedPatterns : 0;
    }

    // ✅ Tính tỷ lệ thời gian mining / tổng runtime
    public double getMiningTimeRatio() {
        return runtimeMs > 0 ? (double) miningTime / runtimeMs : 0;
    }

    // ✅ Tính tỷ lệ thời gian filtering / tổng runtime
    public double getFilterTimeRatio() {
        return runtimeMs > 0 ? (double) filterTime / runtimeMs : 0;
    }

    // ✅ Lấy thời gian chạy theo đơn vị giây (để hiển thị dễ đọc hơn)
    public double getRuntimeSeconds() {
        return runtimeMs / 1000.0;
    }

    // ✅ Xuất thông tin ResultRow ra dạng chuỗi để debug hoặc in log
    @Override
    public String toString() {
        return String.format(
                "ResultRow{minSup=%.3f, runtime=%dms, memory=%.2fMB, " +
                        "closed=%d, filtered=%d, candidates=%d, " +
                        "miningTime=%dms, filterTime=%dms, " +
                        "miningCandidates=%d, similarityComparisons=%d}",
                minSupRatio, runtimeMs, memoryUsageMb,
                closedPatterns, filteredPatterns, candidatesGenerated,
                miningTime, filterTime, miningCandidates, similarityComparisons);
    }
}
