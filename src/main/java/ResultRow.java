public class ResultRow {
    public double minSupRatio;
    public long runtimeMs;
    public double memoryUsageMb;
    public int closedPatterns;
    public int filteredPatterns;

    public ResultRow(double minSupRatio, long runtimeMs, double memoryUsageMb, int closedPatterns, int filteredPatterns) {
        this.minSupRatio = minSupRatio;
        this.runtimeMs = runtimeMs;
        this.memoryUsageMb = memoryUsageMb;
        this.closedPatterns = closedPatterns;
        this.filteredPatterns = filteredPatterns;
    }
}
