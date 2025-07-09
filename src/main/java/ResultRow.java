public class ResultRow {
    public double minSupRatio;
    public long runtimeMs; // ✅ đổi lại từ runtimeSec → runtimeMs
    public double memoryUsageMb;
    public int closedPatterns;
    public int filteredPatterns;
    public int candidatesGenerated;

    // ✅ Metrics chi tiết (đã là mili giây rồi)
    public long miningTime;
    public long filterTime;
    public int miningCandidates;
    public int similarityComparisons;

    // ✅ Constructor
    public ResultRow(double minSupRatio, long runtimeMs, double memoryUsageMb,
                     int closedPatterns, int filteredPatterns, int candidatesGenerated) {
        this.minSupRatio = minSupRatio;
        this.runtimeMs = runtimeMs;
        this.memoryUsageMb = memoryUsageMb;
        this.closedPatterns = closedPatterns;
        this.filteredPatterns = filteredPatterns;
        this.candidatesGenerated = candidatesGenerated;
        this.miningTime = 0;
        this.filterTime = 0;
        this.miningCandidates = 0;
        this.similarityComparisons = 0;
    }

    // ✅ Constructor đầy đủ
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

    // ✅ Tỷ lệ lọc (filtered / closed)
    public double getFilteringRatio() {
        return closedPatterns > 0 ? (double) filteredPatterns / closedPatterns : 0;
    }

    // ✅ Tỷ lệ thời gian mining so với runtime
    public double getMiningTimeRatio() {
        return runtimeMs > 0 ? (double) miningTime / runtimeMs : 0;
    }

    // ✅ Tỷ lệ thời gian filter so với runtime
    public double getFilterTimeRatio() {
        return runtimeMs > 0 ? (double) filterTime / runtimeMs : 0;
    }

    // ✅ Trả về thời gian chạy theo đơn vị giây (nếu cần hiển thị)
    public double getRuntimeSeconds() {
        return runtimeMs / 1000.0;
    }

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
