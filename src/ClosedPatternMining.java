import java.util.*;
class ClosedPatternMining {
    private final int minSup;
    private final Map<String, Set<Integer>> verticalDB = new HashMap<>();
    private final Set<Set<String>> closedPatterns = new HashSet<>();

    public ClosedPatternMining(int minSup) {
        this.minSup = minSup;
    }

    public Set<Set<String>> run(List<Set<String>> transactions) {
        createVerticalDB(transactions);

        List<String> postset = new ArrayList<>();
        for (String item : verticalDB.keySet()) {
            if (verticalDB.get(item).size() >= minSup) {
                postset.add(item);
            }
        }

        // Sort postset in ascending order of support
        postset.sort(Comparator.comparingInt(i -> verticalDB.get(i).size()));

        // Begin recursion
        DCI_Closed_Recursive(new HashSet<>(), null, postset, new HashSet<>(), true);
        return closedPatterns;
    }

    private void createVerticalDB(List<Set<String>> transactions) {
        for (int i = 0; i < transactions.size(); i++) {
            for (String item : transactions.get(i)) {
                verticalDB.computeIfAbsent(item, k -> new HashSet<>()).add(i);
            }
        }
    }

    private void DCI_Closed_Recursive(Set<String> P, Set<Integer> TP,
                                      List<String> postset, Set<String> preset, boolean firstCall) {
        for (int i = 0; i < postset.size(); i++) {
            String item = postset.get(i);
            Set<Integer> T_new = firstCall ? new HashSet<>(verticalDB.get(item)) : intersect(TP, verticalDB.get(item));

            if (T_new.size() >= minSup) {
                Set<String> X = new HashSet<>(P);
                X.add(item);

                boolean isDuplicate = false;
                for (String j : preset) {
                    if (verticalDB.get(j).containsAll(T_new)) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (!isDuplicate) {
                    Set<String> X_ext = new HashSet<>(X);
                    Set<Integer> T_X = new HashSet<>(T_new);
                    List<String> postsetNew = new ArrayList<>();

                    for (int j = i + 1; j < postset.size(); j++) {
                        String nextItem = postset.get(j);
                        if (verticalDB.get(nextItem).containsAll(T_new)) {
                            X_ext.add(nextItem);
                        } else {
                            postsetNew.add(nextItem);
                        }
                    }

                    closedPatterns.add(X_ext);
                    DCI_Closed_Recursive(X_ext, T_X, postsetNew, new HashSet<>(preset), false);
                }
            }

            preset.add(item);
        }
    }

    private Set<Integer> intersect(Set<Integer> a, Set<Integer> b) {
        Set<Integer> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }
}
