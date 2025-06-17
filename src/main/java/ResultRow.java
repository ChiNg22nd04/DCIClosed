
public class ResultRow {
    public double minSupRatio;
    public long runtimeMs;
    public int closedPatterns;
    public int filteredPatterns;

    public ResultRow(double minSupRatio, long runtimeMs, int closedPatterns, int filteredPatterns) {
        this.minSupRatio = minSupRatio;
        this.runtimeMs = runtimeMs;
        this.closedPatterns = closedPatterns;
        this.filteredPatterns = filteredPatterns;
    }
} 