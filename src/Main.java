import java.util.*;
public class Main {
    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            // 1. Nhập số lượng transaction
            System.out.print("Nhập số lượng transaction: ");
            int numTransactions = scanner.nextInt();
            scanner.nextLine(); // Consume newline
            
            // 2. Tạo database từ input người dùng
            List<Set<String>> database = new ArrayList<>();
            
            for (int i = 0; i < numTransactions; i++) {
                System.out.print("Nhập số lượng items cho transaction " + (i + 1) + ": ");
                int numItems = scanner.nextInt();
                scanner.nextLine(); // Consume newline
                
                Set<String> transaction = new HashSet<>();
                System.out.println("Nhập " + numItems + " items cho transaction " + (i + 1) + 
                                 " (có thể nhập cả chữ và số, cách nhau bởi dấu cách):");
                
                String[] items = scanner.nextLine().trim().split("\\s+");
                for (int j = 0; j < Math.min(numItems, items.length); j++) {
                    transaction.add(items[j]);
                }
                database.add(transaction);
            }

            // In database đã nhập
            System.out.println("\nDatabase đã nhập:");
            printDatabase(database);

            // 3. Nhập minimum support
            System.out.print("\nNhập minimum support (minSup): ");
            int minSup = scanner.nextInt();
            
            // 4. Chạy thuật toán DCI-Closed
            ClosedPatternMining miner = new ClosedPatternMining(minSup);
            Set<Set<String>> closedPatterns = miner.DCIClosed(database);

            // In kết quả closed patterns
            System.out.println("\nClosed Patterns tìm được:");
            printPatterns(closedPatterns);

            // 5. Nhập và kiểm tra similarity
            System.out.print("\nNhập ngưỡng similarity (0.0 - 1.0): ");
            double minSim = scanner.nextDouble();
            
            List<Set<String>> filteredPatterns = SimilarityChecker.checkSimilarity(closedPatterns, minSim);

            // In kết quả sau khi lọc
            System.out.println("\nPatterns sau khi lọc similarity:");
            printPatterns(filteredPatterns);

            scanner.close();

        } catch (InputMismatchException e) {
            System.err.println("Lỗi: Vui lòng nhập đúng định dạng!");
        } catch (Exception e) {
            System.err.println("Có lỗi xảy ra: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printDatabase(List<Set<String>> database) {
        for (int i = 0; i < database.size(); i++) {
            System.out.println("Transaction " + (i + 1) + ": " + database.get(i));
        }
    }

    private static void printPatterns(Collection<Set<String>> patterns) {
        if (patterns.isEmpty()) {
            System.out.println("Không tìm thấy patterns.");
            return;
        }

        int count = 1;
        for (Set<String> pattern : patterns) {
            System.out.println("Pattern " + count + ": " + pattern);
            count++;
        }
        System.out.println("Tổng số patterns: " + (count - 1));
    }
}