import java.util.Set;
public interface SimilarityMeasure {
    double compute(Set<String> A, Set<String> B);
}
