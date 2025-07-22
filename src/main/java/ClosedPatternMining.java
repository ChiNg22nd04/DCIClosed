import java.util.*;

class ClosedPatternMining {
    private final int minSup;
    private final Map<String, Set<Integer>> verticalDB = new HashMap<>();
    private final Set<Set<String>> closedPatterns = new HashSet<>();
    private int candidatesGenerated = 0;
    private long maxRuntime = Long.MAX_VALUE; // Timeout mechanism
    private long startTime;
    private int maxPatterns = 50000; // Giới hạn số patterns để tránh memory overflow
    
    public ClosedPatternMining(int minSup) {
        this.minSup = minSup;
    }
    
    public void setMaxRuntime(long maxRuntimeMs) {
        this.maxRuntime = maxRuntimeMs;
    }
    
    public void setMaxPatterns(int maxPatterns) {
        this.maxPatterns = maxPatterns;
    }

    public int getCandidatesGenerated() {
        return candidatesGenerated;
    }

    public Set<Set<String>> run(List<Set<String>> transactions) {
        startTime = System.currentTimeMillis();
        
        try {
            createVerticalDB(transactions);
            
            // Kiểm tra timeout
            if (isTimeout()) return null;

            List<String> postset = new ArrayList<>();
            for (String item : verticalDB.keySet()) {
                if (verticalDB.get(item).size() >= minSup) {
                    postset.add(item);
                }
            }

            // Sort postset theo support tăng dần (optimization)
            postset.sort(Comparator.comparingInt(i -> verticalDB.get(i).size()));

            System.out.println("   📋 Frequent 1-itemsets: " + postset.size());
            
            // Pruning: Chỉ xử lý một số lượng hợp lý items
            if (postset.size() > 1000) {
                System.out.println("   ⚠️ Quá nhiều frequent items, chỉ xử lý 1000 items đầu");
                postset = postset.subList(0, 1000);
            }

            // Bắt đầu đệ quy với depth limit
            DCI_Closed_Recursive(new HashSet<>(), null, postset, new HashSet<>(), true, 0);

            System.out.println("   ✅ Tìm được " + closedPatterns.size() + " closed patterns");
            return closedPatterns;
            
        } catch (OutOfMemoryError e) {
            System.err.println("   ❌ Hết memory, trả về patterns hiện tại: " + closedPatterns.size());
            return closedPatterns;
        } catch (Exception e) {
            System.err.println("   ❌ Lỗi: " + e.getMessage());
            return closedPatterns;
        }
    }

    private boolean isTimeout() {
        return System.currentTimeMillis() - startTime > maxRuntime;
    }

    private void createVerticalDB(List<Set<String>> transactions) {
        for (int i = 0; i < transactions.size(); i++) {
            if (i % 10000 == 0 && isTimeout()) {
                System.out.println("   ⚠️ Timeout during vertical DB creation");
                break;
            }
            
            for (String item : transactions.get(i)) {
                verticalDB.computeIfAbsent(item, k -> new HashSet<>()).add(i);
            }
        }
        
        System.out.println("   📊 Vertical DB created: " + verticalDB.size() + " items");
    }

    private void DCI_Closed_Recursive(Set<String> P, Set<Integer> TP,
                                      List<String> postset, Set<String> preset, 
                                      boolean firstCall, int depth) {
        
        // Kiểm tra các điều kiện dừng
        if (isTimeout() || closedPatterns.size() >= maxPatterns || depth > 10) {
            return;
        }

        for (int i = 0; i < postset.size(); i++) {
            String item = postset.get(i);
            
            Set<Integer> T_new = firstCall
                    ? new HashSet<>(verticalDB.get(item))
                    : intersect(TP, verticalDB.get(item));

            if (T_new.size() >= minSup) {
                Set<String> X = new HashSet<>(P);
                X.add(item);

                // Optimized duplicate checking
                boolean isDuplicate = false;
                for (String j : preset) {
                    if (verticalDB.get(j).size() >= T_new.size() && 
                        verticalDB.get(j).containsAll(T_new)) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (!isDuplicate) {
                    Set<String> X_ext = new HashSet<>(X);
                    Set<Integer> T_X = new HashSet<>(T_new);
                    List<String> postsetNew = new ArrayList<>();

                    // Closure computation với optimization
                    for (int j = i + 1; j < postset.size(); j++) {
                        String nextItem = postset.get(j);
                        Set<Integer> nextItemTids = verticalDB.get(nextItem);
                        
                        if (nextItemTids.size() >= T_new.size() && 
                            nextItemTids.containsAll(T_new)) {
                            X_ext.add(nextItem);
                        } else if (intersect(T_new, nextItemTids).size() >= minSup) {
                            postsetNew.add(nextItem);
                        }
                    }

                    candidatesGenerated++;
                    
                    // Thêm vào closed patterns với size limit
                    if (closedPatterns.size() < maxPatterns) {
                        closedPatterns.add(new HashSet<>(X_ext));
                    }

                    // Pruning: Chỉ đệ quy nếu còn potential
                    if (!postsetNew.isEmpty() && depth < 8) {
                        DCI_Closed_Recursive(X_ext, T_X, postsetNew, 
                                           new HashSet<>(preset), false, depth + 1);
                    }
                }
            }

            preset.add(item);
        }
    }

    private Set<Integer> intersect(Set<Integer> a, Set<Integer> b) {
        // Optimization: luôn iterate qua set nhỏ hơn
        if (a.size() > b.size()) {
            Set<Integer> temp = a;
            a = b;
            b = temp;
        }
        
        Set<Integer> result = new HashSet<>();
        for (Integer item : a) {
            if (b.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }
}