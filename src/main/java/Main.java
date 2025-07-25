public class Main {
    public static void main(String[] args) {
        System.out.println("RUN MODEL 1 - Cố định minSup và thay đổi minSim");
        try {
            Model1.main(args);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi chạy Model1: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n\nRUN MODEL 2 - Thay đổi minSup và cố định minSim");
        try {
            Model2.main(args);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi chạy Model2: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n\n🎉 Hoàn thành chạy cả Model1 và Model2");
    }
}
