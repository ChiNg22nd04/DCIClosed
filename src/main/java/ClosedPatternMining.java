import java.util.*;

/**
 * ClosedPatternMining
 * -------------------
 * Khai thác các MẪU ĐÓNG (Closed Frequent Itemsets) bằng hướng tiếp cận VERTICAL (TID-list),
 * gần với phong cách DCI-Closed/CHARM đơn giản hoá:
 *  - Xài verticalDB: Map<Item, TID-set>
 *  - Duyệt theo "postset" (các item có thể mở rộng), "preset" (các item đã đi qua để tránh lặp)
 *  - Tính closure: nếu T(next) chứa hết T(X) => next nằm trong closure của X
 *  - Pruning:
 *      + Ngưỡng minSup
 *      + Depth limit (độ sâu đệ quy)
 *      + MaxRuntime (timeout)
 *      + MaxPatterns (giới hạn số mẫu để tránh tràn bộ nhớ)
 *
 * LƯU Ý:
 *  - Dùng HashSet<Integer> cho TID-set: dễ hiểu nhưng tốn bộ nhớ/hơi chậm khi intersect.
 *    Có thể tối ưu bằng:
 *      + IntOpenHashSet (fastutil) hoặc RoaringBitmap/BitSet để intersect nhanh và tiết kiệm RAM.
 *  - Các giới hạn depth (10, 8) là "heuristic": nên cho cấu hình được nếu cần.
 */
class ClosedPatternMining {
    private final int minSup;                                   // Ngưỡng support tuyệt đối (số giao dịch)
    private final Map<String, Set<Integer>> verticalDB = new HashMap<>(); // VDB: item -> TID-set
    private final Set<Set<String>> closedPatterns = new HashSet<>();      // Kết quả: tập các mẫu đóng
    private int candidatesGenerated = 0;                        // Đếm số "ứng viên" (mở rộng đã xét)
    private long maxRuntime = Long.MAX_VALUE;                   // Timeout (ms)
    private long startTime;
    private int maxPatterns = 50000;                            // Giới hạn số patterns để tránh OOM

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

    /**
     * Điểm vào chính:
     *  1) Tạo verticalDB (item -> TID-set)
     *  2) Lọc các 1-item frequent vào "postset" (ứng viên khởi đầu)
     *  3) Sắp xếp postset theo support tăng dần (heuristic để giảm chi phí)
     *  4) Gọi đệ quy DCI_Closed_Recursive để khai thác
     *
     * @param transactions List<Set<String>> database dạng horizontal
     * @return Set các closed patterns (mỗi pattern là Set<String> items); null nếu timeout sớm ở đầu
     */
    public Set<Set<String>> run(List<Set<String>> transactions) {
        startTime = System.currentTimeMillis();

        try {
            createVerticalDB(transactions);

            // Kiểm tra timeout sớm sau khi build VDB
            if (isTimeout()) return null;

            List<String> postset = new ArrayList<>();
            // Lấy các 1-item frequent (|T(item)| >= minSup) làm hạt giống mở rộng
            for (String item : verticalDB.keySet()) {
                if (verticalDB.get(item).size() >= minSup) {
                    postset.add(item);
                }
            }

            // Heuristic: sort theo support tăng dần để tối ưu intersect/closure
            postset.sort(Comparator.comparingInt(i -> verticalDB.get(i).size()));

            System.out.println("   📋 Frequent 1-itemsets: " + postset.size());

            // Pruning cứng: nếu quá nhiều frequent items, cắt xuống 1000 đầu tiên
            // (giảm chi phí/ram; đổi số này thành config nếu cần linh hoạt)
            if (postset.size() > 1000) {
                System.out.println("   ⚠️ Quá nhiều frequent items, chỉ xử lý 1000 items đầu");
                postset = postset.subList(0, 1000);
            }

            // Bắt đầu đệ quy.
            // P     = tập hiện tại (prefix)
            // TP    = TID-set của P (nếu null => đang ở cấp 1-item)
            // postset = các item còn có thể thử nối vào P
            // preset  = các item đã xét qua (để tránh lặp/dupe)
            // firstCall = true ở bước đầu giúp tối ưu (khỏi intersect với TP=null)
            // depth     = độ sâu đệ quy (đặt limit để tránh nổ)
            DCI_Closed_Recursive(new HashSet<>(), null, postset, new HashSet<>(), true, 0);

            System.out.println("   ✅ Tìm được " + closedPatterns.size() + " closed patterns");
            return closedPatterns;

        } catch (OutOfMemoryError e) {
            // Dự phòng khi thiếu RAM: trả về phần đã tìm được
            System.err.println("   ❌ Hết memory, trả về patterns hiện tại: " + closedPatterns.size());
            return closedPatterns;
        } catch (Exception e) {
            // Dự phòng lỗi bất ngờ: vẫn trả về phần đã có
            System.err.println("   ❌ Lỗi: " + e.getMessage());
            return closedPatterns;
        }
    }

    /** Kiểm tra timeout toàn cục */
    private boolean isTimeout() {
        return System.currentTimeMillis() - startTime > maxRuntime;
    }

    /**
     * Tạo Vertical DB: item -> tập các TID (ID giao dịch) chứa item đó.
     * Phương pháp vertical giúp:
     *   - Tính support: |T(itemset)| = |∩ TID-sets|
     *   - Tính closure: nếu T(next) ⊇ T(current) thì next nằm trong closure của current
     */
    private void createVerticalDB(List<Set<String>> transactions) {
        for (int i = 0; i < transactions.size(); i++) {
            // Cho phép nghỉ sớm nếu timeout trong lúc build VDB
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

    /**
     * Đệ quy kiểu DCI-Closed (mang tính gần đúng/đơn giản hoá)
     *
     * @param P        prefix hiện tại (pattern cơ sở)
     * @param TP       TID-set của P (null nếu firstCall để tối ưu)
     * @param postset  danh sách items có thể thử nối vào (sau vị trí hiện tại)
     * @param preset   các items đã đi qua (dùng để kiểm tra trùng lặp/bao hàm)
     * @param firstCall true nếu đang ở mức gốc (để không intersect với TP null)
     * @param depth    độ sâu đệ quy hiện tại
     */
    private void DCI_Closed_Recursive(Set<String> P, Set<Integer> TP,
                                      List<String> postset, Set<String> preset,
                                      boolean firstCall, int depth) {

        // Điều kiện dừng toàn cục: timeout, quá nhiều patterns, hoặc quá sâu
        if (isTimeout() || closedPatterns.size() >= maxPatterns || depth > 10) {
            // NOTE: depth limit=10 là heuristic; có thể cấu hình tuỳ dataset
            return;
        }

        for (int i = 0; i < postset.size(); i++) {
            String item = postset.get(i);

            // T_new = TID-set(P ∪ {item})
            // firstCall => TP=null => lấy thẳng T(item) để đỡ intersect
            Set<Integer> T_new = firstCall
                    ? new HashSet<>(verticalDB.get(item))
                    : intersect(TP, verticalDB.get(item));

            // Nếu support đủ minSup thì có tiềm năng
            if (T_new.size() >= minSup) {
                // X = P ∪ {item}
                Set<String> X = new HashSet<>(P);
                X.add(item);

                // === Duplicate/Containment pruning (preset) ===
                // Nếu tồn tại j trong preset sao cho:
                //   |T(j)| >= |T_new| và T(j) ⊇ T_new
                // => j "bao" T_new => X có thể là bản lặp tương đương về TID-set (không cần xét)
                boolean isDuplicate = false;
                for (String j : preset) {
                    if (verticalDB.get(j).size() >= T_new.size() &&
                            verticalDB.get(j).containsAll(T_new)) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (!isDuplicate) {
                    // X_ext = closure(X): thêm các nextItem có T(next) ⊇ T_new
                    Set<String> X_ext = new HashSet<>(X);
                    Set<Integer> T_X = new HashSet<>(T_new); // TID-set của X (dùng để đào sâu)
                    List<String> postsetNew = new ArrayList<>();

                    // Duyệt các mục phía sau i để:
                    //  - nếu T(next) ⊇ T_new => next thuộc CLOSURE -> add thẳng vào X_ext
                    //  - ngược lại nếu |T_new ∩ T(next)| >= minSup => next là ứng viên mở rộng -> đưa vào postsetNew
                    for (int j = i + 1; j < postset.size(); j++) {
                        String nextItem = postset.get(j);
                        Set<Integer> nextItemTids = verticalDB.get(nextItem);

                        if (nextItemTids.size() >= T_new.size() &&
                                nextItemTids.containsAll(T_new)) {
                            // Bao phủ hoàn toàn: nằm trong closure
                            X_ext.add(nextItem);
                        } else if (intersect(T_new, nextItemTids).size() >= minSup) {
                            // Có tiềm năng mở rộng (không nằm trong closure)
                            postsetNew.add(nextItem);
                        }
                    }

                    // Đếm thêm 1 "ứng viên" đã được xử lý
                    candidatesGenerated++;

                    // Lưu pattern đóng (X_ext) nếu chưa vượt giới hạn
                    if (closedPatterns.size() < maxPatterns) {
                        // NOTE: Không check trùng lặp X_ext với patterns trước đó theo nghĩa "đặt chuẩn",
                        // HashSet<Set<String>> sẽ dựa trên equals() của Set để loại trùng nội dung.
                        closedPatterns.add(new HashSet<>(X_ext));
                    }

                    // Đệ quy đào sâu nếu còn khả năng mở rộng (postsetNew không rỗng) và còn trong giới hạn depth
                    // Ở đây depth < 8 chặt hơn điều kiện dừng tổng (depth > 10) -> heuristic để kìm nén nhánh sâu.
                    if (!postsetNew.isEmpty() && depth < 8) {
                        DCI_Closed_Recursive(X_ext, T_X, postsetNew,
                                new HashSet<>(preset), false, depth + 1);
                    }
                }
            }

            // Thêm 'item' vào preset để lần sau dùng cho duplicate pruning
            preset.add(item);
        }
    }

    /**
     * Giao 2 TID-set: tối ưu bằng cách luôn lặp qua set nhỏ hơn
     * (đơn giản, dễ hiểu; nếu dùng BitSet/RoaringBitmap sẽ nhanh hơn nhiều).
     */
    private Set<Integer> intersect(Set<Integer> a, Set<Integer> b) {
        // Optimization: luôn iterate qua set nhỏ hơn để giảm contains() calls
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