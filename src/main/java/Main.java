public class Main {
    public static void main(String[] args) {
        // ===== Chạy Model 1 =====
        System.out.println("RUN MODEL 1 - Cố định minSup và thay đổi minSim");
        try {
            // Gọi hàm main của Model1 (có thể truyền tham số qua args)
            // Model1 sẽ giữ minSup cố định, và thay đổi giá trị minSim để chạy thử nghiệm
            Model1.main(args);
        } catch (Exception e) {
            // Nếu xảy ra lỗi khi chạy Model1 thì in ra thông báo lỗi và stack trace
            System.err.println("❌ Lỗi khi chạy Model1: " + e.getMessage());
            e.printStackTrace();
        }

        // ===== Chạy Model 2 =====
        System.out.println("\n\nRUN MODEL 2 - Thay đổi minSup và cố định minSim");
        try {
            // Gọi hàm main của Model2
            // Model2 sẽ giữ minSim cố định, và thay đổi giá trị minSup để chạy thử nghiệm
            Model2.main(args);
        } catch (Exception e) {
            // Nếu xảy ra lỗi khi chạy Model2 thì in ra thông báo lỗi và stack trace
            System.err.println("❌ Lỗi khi chạy Model2: " + e.getMessage());
            e.printStackTrace();
        }

        // ===== Hoàn thành =====
        System.out.println("\n\n🎉 Hoàn thành chạy cả Model1 và Model2");
    }
}
