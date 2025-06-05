import java.util.*;
class ClosedPatternMining {
    private Set<Set<String>> closedPatterns;
    private Map<String, Set<Integer>> verticalDB;
    private int minSup;

    public ClosedPatternMining(int minSup) {
        this.minSup = minSup;
        this.closedPatterns = new HashSet<>();
        this.verticalDB = new HashMap<>();
    }

    public void createVerticalDB(List<Set<String>> transactions) {
        for (int i = 0; i < transactions.size(); i++) {
            for (String item : transactions.get(i)) {
                verticalDB.computeIfAbsent(item, k -> new HashSet<>()).add(i);
            }
        }
    }

    public Set<Set<String>> DCIClosed(List<Set<String>> database) {
        createVerticalDB(database);

        List<String> postset = new ArrayList<>();
        for (Map.Entry<String, Set<Integer>> entry : verticalDB.entrySet()) {
            if (entry.getValue().size() >= minSup) {
                postset.add(entry.getKey());
            }
        }

        postset.sort((a, b) -> verticalDB.get(a).size() - verticalDB.get(b).size());

        DCI_Closed_Recursive(new HashSet<>(), null, postset, new HashSet<>(), true);

        return closedPatterns;
    }

    private void DCI_Closed_Recursive(Set<String> P, Set<Integer> TP,
                                      List<String> postset, Set<String> preset,
                                      boolean firstCall) {
        for (int i = 0; i < postset.size(); i++) {
            String item = postset.get(i);
            Set<Integer> T_new;

            if (firstCall) {
                T_new = new HashSet<>(verticalDB.get(item));
            } else {
                T_new = new HashSet<>(TP);
                T_new.retainAll(verticalDB.get(item));
            }

            if (T_new.size() >= minSup) {
                Set<String> X = new HashSet<>(P);
                X.add(item);

                boolean isValid = true;
                for (String j : preset) {
                    if (verticalDB.get(j).containsAll(T_new)) {
                        isValid = false;
                        break;
                    }
                }

                if (isValid) {
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
                    DCI_Closed_Recursive(X_ext, T_X, postsetNew, preset, false);
                }
            }
            preset.add(item);
        }
    }
}