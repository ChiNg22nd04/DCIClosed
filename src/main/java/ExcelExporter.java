import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xddf.usermodel.*;

import java.io.FileOutputStream;
import java.util.*;

/**
 * ExcelExporter
 * -------------
 * Sinh file Excel tổng hợp kết quả:
 *  - Header gộp nhóm (Runtime / Memory / Patterns / Candidates)
 *  - Bảng dữ liệu (min ở cột 0; sau đó 4 nhóm * 3 cột = 12 cột)
 *  - Biểu đồ line (Runtime, Memory) & bar (Patterns)
 *
 * LƯU Ý:
 *  - autoSizeColumns() bên dưới thực ra đặt WIDTH cố định (12 ký tự), KHÔNG phải auto-size thật.
 *    Nếu muốn autosize chuẩn của POI, hãy gọi sheet.autoSizeColumn(i) SAU khi đã ghi toàn bộ dữ liệu.
 *  - Các hàm drawChart/drawBarChart dùng range hàng [3 .. 2 + rowCount] vì dữ liệu bắt đầu ở hàng index 3.
 *  - Ở Model1, trục X là minSim; ở Model2, trục X là minSup (đều nằm ở cột 0).
 */
public class ExcelExporter {

    /** Legacy: xuất 1 sheet tên "Summary" với trục X = minSup (cột 0) */
    public static void exportSummarySheet(Map<Double, Map<String, ResultRow>> summaryMap, String fileName, String datasetName) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Summary");

        // Header gộp nhóm + tiêu đề dataset (dòng 0..2)
        writeGroupedHeaderWithStyle(sheet, workbook, datasetName);
        // Ghi bảng dữ liệu (dòng bắt đầu từ 3)
        writeData(sheet, summaryMap);
        // Set width cố định 12 ký tự cho 13 cột (0..12)
        autoSizeColumns(sheet, 13);

        // rowCount = số hàng dữ liệu để vẽ biểu đồ
        int rowCount = summaryMap.size();

        // Vẽ 3 biểu đồ (đặt ở các toạ độ ô khác nhau cho khỏi chồng)
        drawChart(sheet, rowCount, "Runtime", 1, 4, 13, 30, "RUNTIME (s)");     // runtime: cột 1..3
        drawChart(sheet, rowCount, "Memory Usage", 4, 7, 13, 48, "MEMORY (MB)");// memory : cột 4..6
        drawBarChart(sheet, rowCount, "Patterns Found", 7, 10, 13, 66);         // patterns: cột 7..9

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }

    /** Tạo header gộp nhóm (Runtime/Memory/Patterns/Candidates) cho sheet mặc định (X = minSup) */
    private static void writeGroupedHeaderWithStyle(XSSFSheet sheet, XSSFWorkbook workbook, String datasetName) {
        Map<String, CellStyle> styles = createGroupStyles(workbook);

        // Dòng 0: tiêu đề dataset, merge từ cột 0..12
        Row datasetRow = sheet.createRow(0);
        datasetRow.setHeightInPoints(35); // cho title nhìn thoáng
        CellRangeAddress titleRegion = new CellRangeAddress(0, 0, 0, 12);
        sheet.addMergedRegion(titleRegion);
        // Áp style vào TỪNG ô trong vùng merge (đồng thời tạo cell nếu chưa có)
        applyBorderToMergedRegion(sheet, titleRegion, styles.get("datasetTitle"));
        datasetRow.getCell(0).setCellValue(datasetName);

        // Dòng 1: các nhóm cột
        Row groupRow = sheet.createRow(1);
        groupRow.setHeightInPoints(30);

        // Runtime: cột 1..3
        groupRow.createCell(1).setCellValue("Runtime (s)");
        CellRangeAddress runtimeRegion = new CellRangeAddress(1, 1, 1, 3);
        sheet.addMergedRegion(runtimeRegion);
        applyBorderToMergedRegion(sheet, runtimeRegion, styles.get("runtime"));

        // Memory: cột 4..6
        groupRow.createCell(4).setCellValue("Used Memory (MB)");
        CellRangeAddress memoryRegion = new CellRangeAddress(1, 1, 4, 6);
        sheet.addMergedRegion(memoryRegion);
        applyBorderToMergedRegion(sheet, memoryRegion, styles.get("memory"));

        // Patterns: cột 7..9
        groupRow.createCell(7).setCellValue("Patterns Found");
        CellRangeAddress patternRegion = new CellRangeAddress(1, 1, 7, 9);
        sheet.addMergedRegion(patternRegion);
        applyBorderToMergedRegion(sheet, patternRegion, styles.get("pattern"));

        // Candidates: cột 10..12
        groupRow.createCell(10).setCellValue("Candidates Generated");
        CellRangeAddress candidatesRegion = new CellRangeAddress(1, 1, 10, 12);
        sheet.addMergedRegion(candidatesRegion);
        applyBorderToMergedRegion(sheet, candidatesRegion, styles.get("candidates"));

        // Dòng 2: tiêu đề chi tiết từng cột
        Row methodRow = sheet.createRow(2);
        methodRow.setHeightInPoints(25);
        String[] titles = {"minSup", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc"};
        for (int i = 0; i < titles.length; i++) {
            Cell cell = methodRow.createCell(i);
            cell.setCellValue(titles[i]);
            // Áp style theo nhóm (để in ấn trên nền trắng đẹp, fill màu đang tắt trong style)
            if (i >= 1 && i <= 3) cell.setCellStyle(styles.get("runtime"));
            else if (i >= 4 && i <= 6) cell.setCellStyle(styles.get("memory"));
            else if (i >= 7 && i <= 9) cell.setCellStyle(styles.get("pattern"));
            else if (i >= 10 && i <= 12) cell.setCellStyle(styles.get("candidates"));
            else cell.setCellStyle(styles.get("default"));
        }
    }

    /** Tạo tập các style dùng cho header/tiêu đề */
    private static Map<String, CellStyle> createGroupStyles(XSSFWorkbook workbook) {
        Map<String, CellStyle> map = new HashMap<>();
        map.put("runtime", createStyle(workbook, IndexedColors.WHITE));
        map.put("memory", createStyle(workbook, IndexedColors.WHITE));
        map.put("pattern", createStyle(workbook, IndexedColors.WHITE));
        map.put("candidates", createStyle(workbook, IndexedColors.WHITE));
        map.put("default", createStyle(workbook, IndexedColors.WHITE));
        map.put("datasetTitle", createTitleStyle(workbook));
        map.put("jaccard", createStyle(workbook, IndexedColors.WHITE));
        return map;
    }

    /** Style header nhóm: đậm, canh giữa, viền mảnh; fill đang tắt để nền trắng */
    private static CellStyle createStyle(XSSFWorkbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        // Nếu muốn nền màu, bật 2 dòng dưới:
        // style.setFillForegroundColor(color.getIndex());
        // style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 15);
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /** Style cho tiêu đề dataset (font to hơn chút) */
    private static CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * Ghi bảng dữ liệu:
     *  - cột 0: min (minSup hoặc minSim tuỳ sheet)
     *  - cột 1..3: Runtime (Jaccard/Dice/Kulczynski) — đã đổi ms -> giây (ResultRow.getRuntimeSeconds)
     *  - cột 4..6: Memory (MB)
     *  - cột 7..9: Patterns (sau lọc)
     *  - cột 10..12: Candidates (miningCandidates + similarityComparisons)
     */
    private static void writeData(XSSFSheet sheet, Map<Double, Map<String, ResultRow>> summaryMap) {
        int rowIdx = 3; // dữ liệu bắt đầu từ hàng 3 (0-based)
        String[] order = {"Jaccard", "Dice", "Kulczynski"};

        Workbook workbook = sheet.getWorkbook();

        // Style mặc định cho ô dữ liệu (viền, wrap, font 15)
        CellStyle defaultStyle = workbook.createCellStyle();
        defaultStyle.setBorderTop(BorderStyle.THIN);
        defaultStyle.setBorderBottom(BorderStyle.THIN);
        defaultStyle.setBorderLeft(BorderStyle.THIN);
        defaultStyle.setBorderRight(BorderStyle.THIN);
        defaultStyle.setWrapText(true);
        Font defaultFont = workbook.createFont();
        defaultFont.setFontHeightInPoints((short) 15);
        defaultStyle.setFont(defaultFont);

        // Style riêng cho từng thuật toán (clone từ default, fill đang tắt)
        Map<String, CellStyle> algoStyles = new HashMap<>();
        algoStyles.put("Jaccard", createAlgoStyle(workbook, IndexedColors.LIGHT_TURQUOISE, defaultStyle));
        algoStyles.put("Dice", createAlgoStyle(workbook, IndexedColors.LIGHT_YELLOW, defaultStyle));
        algoStyles.put("Kulczynski", createAlgoStyle(workbook, IndexedColors.LIGHT_GREEN, defaultStyle));

        // Duyệt theo thứ tự key của summaryMap (TreeMap => tăng dần)
        for (Map.Entry<Double, Map<String, ResultRow>> entry : summaryMap.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(30);
            int col = 0;

            // (0) minX
            Cell minSupCell = row.createCell(col++);
            minSupCell.setCellValue(entry.getKey()); // POI sẽ ghi dạng numeric nếu Double
            minSupCell.setCellStyle(defaultStyle);

            // (1..3) Runtime (s)
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).getRuntimeSeconds());
                cell.setCellStyle(algoStyles.get(sim));
            }

            // (4..6) Memory (MB)
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).memoryUsageMb);
                cell.setCellStyle(algoStyles.get(sim));
            }

            // (7..9) Patterns
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).filteredPatterns);
                cell.setCellStyle(algoStyles.get(sim));
            }

            // (10..12) Candidates
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).candidatesGenerated);
                cell.setCellStyle(algoStyles.get(sim));
            }
        }
    }

    /** Style cho từng thuật toán (có thể bật fill nếu cần), clone từ baseStyle */
    private static CellStyle createAlgoStyle(Workbook workbook, IndexedColors color, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        // Nếu muốn phân màu nền theo thuật toán, bật 2 dòng sau:
        // style.setFillForegroundColor(color.getIndex());
        // style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        // Đảm bảo font size 15
        Font algoFont = workbook.createFont();
        algoFont.setFontHeightInPoints((short) 15);
        style.setFont(algoFont);
        return style;
    }

    /** Đặt width cố định cho N cột đầu (12 ký tự). Nếu muốn autosize thật, dùng sheet.autoSizeColumn(i). */
    private static void autoSizeColumns(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) sheet.setColumnWidth(i, 12 * 256);
    }

    // =========================
    //  LINE CHART (X = minSup)
    // =========================

    /** Tiện ích: yAxisTitle mặc định "VALUE" nếu không truyền */
    private static void drawChart(XSSFSheet sheet, int rowCount, String chartTitle, int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
        drawChart(sheet, rowCount, chartTitle, colStart, colEnd, anchorColStart, anchorRowStart, "VALUE");
    }

    /**
     * Vẽ line chart với:
     *  - X = cột 0 (minSup), range hàng [3 .. 2 + rowCount]
     *  - Y = cột colStart .. colEnd-1 (3 series ứng với Jaccard/Dice/Kulc)
     *  - Marker khác nhau để phân biệt
     */
    private static void drawChart(XSSFSheet sheet, int rowCount, String chartTitle, int colStart, int colEnd, int anchorColStart, int anchorRowStart, String yAxisTitle) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                anchorColStart, anchorRowStart, anchorColStart + 4, anchorRowStart + 13);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM); // legend dưới

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("MINSUP");             // trục X

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle(yAxisTitle);             // trục Y

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

        // X đọc từ cột 0 (minSup)
        XDDFDataSource<Double> minSup = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(3, rowCount + 2, 0, 0));
        String[] names = {"Jaccard", "Dice", "Kulczynski"};
        MarkerStyle[] styles = {MarkerStyle.CIRCLE, MarkerStyle.STAR, MarkerStyle.SQUARE};

        for (int i = colStart; i < colEnd; i++) {
            // Y đọc từ cột i
            XDDFNumericalDataSource<Double> y = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(3, rowCount + 2, i, i));
            XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(minSup, y);
            series.setTitle(names[i - colStart], null);
            series.setMarkerStyle(styles[(i - colStart) % styles.length]);

            // Nếu POI có shapeProperties -> bỏ fill line (đỡ sặc sỡ khi in)
            if (series.getShapeProperties() != null) {
                series.getShapeProperties().setFillProperties(new XDDFNoFillProperties());
            }
        }

        chart.plot(data);
    }

    // =========================
    //   BAR CHART (X = minSup)
    // =========================

    /**
     * Vẽ bar chart cho Patterns:
     *  - Dùng category labels “đệm” đầu/cuối (chuỗi rỗng) để cột không dính sát mép
     *  - Mặc định không set màu riêng từng series (data.setVaryColors(false))
     */
    private static void drawBarChart(XSSFSheet sheet, int rowCount, String chartTitle,
                                     int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                anchorColStart, anchorRowStart, anchorColStart + 5, anchorRowStart + 13);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("MINSUP");

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("PATTERNS");

        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        data.setBarDirection(BarDirection.COL);

        int firstRow = 3;                // hàng dữ liệu đầu
        int lastRow = firstRow + rowCount - 1;

        // Tạo labels cho trục X: thêm "" ở đầu & cuối để “đệm”
        List<String> categoryLabels = new ArrayList<>();
        categoryLabels.add(""); // đầu ẩn

        for (int r = firstRow; r <= lastRow; r++) {
            Cell cell = sheet.getRow(r).getCell(0);
            categoryLabels.add(cell.toString()); // thí dụ "0.005"
        }

        categoryLabels.add(""); // cuối ẩn

        XDDFDataSource<String> minSup = XDDFDataSourcesFactory.fromArray(
                categoryLabels.toArray(new String[0])
        );

        String[] names = {"Jaccard", "Dice", "Kulczynski"};

        for (int i = colStart; i < colEnd; i++) {
            List<Double> yValues = new ArrayList<>();
            yValues.add(0.0); // đệm đầu (cột ảo)

            for (int r = firstRow; r <= lastRow; r++) {
                Cell cell = sheet.getRow(r).getCell(i);
                // NÊN check null để tránh NPE nếu ô rỗng
                yValues.add(cell != null ? cell.getNumericCellValue() : 0.0);
            }

            yValues.add(0.0); // đệm cuối (cột ảo)

            XDDFNumericalDataSource<Double> ySeries = XDDFDataSourcesFactory.fromArray(
                    yValues.toArray(new Double[0]), null
            );

            XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(minSup, ySeries);
            series.setTitle(names[i - colStart], null);

            // Tuỳ POI version, shapeProperties có thể null -> check trước khi bỏ fill
            if (series.getShapeProperties() != null) {
                series.getShapeProperties().setFillProperties(new XDDFNoFillProperties());
            }
        }

        data.setVaryColors(false); // không random màu giữa series
        chart.plot(data);
    }

    /**
     * Xuất nhiều sheet — mỗi dataset 1 sheet (X = minSup).
     * Mỗi sheet đều có 3 biểu đồ như exportSummarySheet().
     */
    public static void exportMultipleSheetsSummary(Map<String, Map<Double, Map<String, ResultRow>>> allResults, String fileName) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        for (Map.Entry<String, Map<Double, Map<String, ResultRow>>> entry : allResults.entrySet()) {
            String datasetName = entry.getKey();
            XSSFSheet sheet = workbook.createSheet(datasetName);
            writeGroupedHeaderWithStyle(sheet, workbook, datasetName);
            writeData(sheet, entry.getValue());
            autoSizeColumns(sheet, 13);

            int rowCount = entry.getValue().size();

            int chartStartRow = rowCount + 5; // neo biểu đồ phía dưới bảng
            drawChart(sheet, rowCount, "Runtime", 1, 4, 1, chartStartRow, "RUNTIME (s)");
            drawChart(sheet, rowCount, "Memory Usage", 4, 7, 6, chartStartRow, "MEMORY (MB)");
            drawBarChart(sheet, rowCount, "Patterns Found", 7, 10, 11, chartStartRow);
        }
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }

    // =========================
    //   DÀNH CHO MODEL 1 (X = minSim)
    // =========================

    /** Xuất mỗi dataset 1 sheet với trục X = minSim (cột 0), vẽ 3 biểu đồ tương tự */
    public static void exportModel1Summary(Map<String, Map<Double, Map<String, ResultRow>>> allResults, String fileName) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        for (Map.Entry<String, Map<Double, Map<String, ResultRow>>> entry : allResults.entrySet()) {
            String datasetName = entry.getKey();
            XSSFSheet sheet = workbook.createSheet(datasetName);
            writeGroupedHeaderForModel1(sheet, workbook, datasetName);
            writeData(sheet, entry.getValue());
            autoSizeColumns(sheet, 13);

            int rowCount = entry.getValue().size();
            int chartStartRow = rowCount + 5;
            drawChartForModel1(sheet, rowCount, "Runtime", 1, 4, 1, chartStartRow, "RUNTIME (s)");
            drawChartForModel1(sheet, rowCount, "Memory Usage", 4, 7, 6, chartStartRow, "MEMORY (MB)");
            drawBarChartForModel1(sheet, rowCount, "Patterns Found", 7, 10, 11, chartStartRow);
        }
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }

    /** Header riêng cho Model1 (cột 0 là "minSim") */
    private static void writeGroupedHeaderForModel1(XSSFSheet sheet, XSSFWorkbook workbook, String datasetName) {
        Map<String, CellStyle> styles = createGroupStyles(workbook);

        Row datasetRow = sheet.createRow(0);
        datasetRow.setHeightInPoints(35);
        CellRangeAddress titleRegion = new CellRangeAddress(0, 0, 0, 12);
        sheet.addMergedRegion(titleRegion);
        applyBorderToMergedRegion(sheet, titleRegion, styles.get("datasetTitle"));
        datasetRow.createCell(0).setCellValue(datasetName);

        Row groupRow = sheet.createRow(1);
        groupRow.setHeightInPoints(30);

        groupRow.createCell(1).setCellValue("Runtime (s)");
        CellRangeAddress runtimeRegion = new CellRangeAddress(1, 1, 1, 3);
        sheet.addMergedRegion(runtimeRegion);
        applyBorderToMergedRegion(sheet, runtimeRegion, styles.get("runtime"));

        groupRow.createCell(4).setCellValue("Used Memory (MB)");
        CellRangeAddress memoryRegion = new CellRangeAddress(1, 1, 4, 6);
        sheet.addMergedRegion(memoryRegion);
        applyBorderToMergedRegion(sheet, memoryRegion, styles.get("memory"));

        groupRow.createCell(7).setCellValue("Patterns Found");
        CellRangeAddress patternRegion = new CellRangeAddress(1, 1, 7, 9);
        sheet.addMergedRegion(patternRegion);
        applyBorderToMergedRegion(sheet, patternRegion, styles.get("pattern"));

        groupRow.createCell(10).setCellValue("Candidates Generated");
        CellRangeAddress candidatesRegion = new CellRangeAddress(1, 1, 10, 12);
        sheet.addMergedRegion(candidatesRegion);
        applyBorderToMergedRegion(sheet, candidatesRegion, styles.get("candidates"));

        Row methodRow = sheet.createRow(2);
        methodRow.setHeightInPoints(25);
        String[] titles = {"minSim", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc"};
        for (int i = 0; i < titles.length; i++) {
            Cell cell = methodRow.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(styles.get("default"));
        }
    }

    /** Line chart cho Model1 (X = minSim) */
    private static void drawChartForModel1(XSSFSheet sheet, int rowCount, String chartTitle, int colStart, int colEnd, int anchorColStart, int anchorRowStart, String yAxisTitle) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                anchorColStart, anchorRowStart, anchorColStart + 4, anchorRowStart + 13);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("MIN SIMILARITY"); // Model1: X = minSim

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle(yAxisTitle);

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

        XDDFDataSource<Double> minSim = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(3, rowCount + 2, 0, 0));
        String[] names = {"Jaccard", "Dice", "Kulczynski"};
        MarkerStyle[] styles = {MarkerStyle.CIRCLE, MarkerStyle.STAR, MarkerStyle.SQUARE};

        for (int i = colStart; i < colEnd; i++) {
            XDDFNumericalDataSource<Double> y = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(3, rowCount + 2, i, i));
            XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(minSim, y);
            series.setTitle(names[i - colStart], null);
            series.setMarkerStyle(styles[(i - colStart) % styles.length]);
        }

        chart.plot(data);
    }

    /**
     * Bar chart cho Model1 (X = minSim):
     *  - Dùng thang LOG cho trục Y để biểu diễn chênh lệch lớn (ép giá trị 0 -> 1.0 cho hợp lệ)
     *  - Thêm 2 nhãn đệm "0.0"/"1.0" ở trục X để cột không dính sát mép
     *  - Set màu fill riêng cho mỗi series (SteelBlue/DarkOrange/ForestGreen)
     */
    private static void drawBarChartForModel1(XSSFSheet sheet, int rowCount, String chartTitle,
                                              int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                anchorColStart, anchorRowStart, anchorColStart + 6, anchorRowStart + 15); // chart rộng hơn

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("MIN SIMILARITY");

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("PATTERNS");
        leftAxis.setLogBase(10.0); // log10
        leftAxis.setMinimum(1.0);  // tránh log(0)

        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        data.setBarDirection(BarDirection.COL);
        data.setGapWidth(200); // giãn cột cho dễ nhìn

        int firstRow = 3;
        int lastRow = firstRow + rowCount - 1;

        // Category labels với 2 điểm đệm “0.0” và “1.0”
        List<String> categoryLabels = new ArrayList<>();
        categoryLabels.add("0.0");
        for (int r = firstRow; r <= lastRow; r++) {
            Cell cell = sheet.getRow(r).getCell(0);
            categoryLabels.add(cell.toString());
        }
        categoryLabels.add("1.0");

        XDDFDataSource<String> minSim = XDDFDataSourcesFactory.fromArray(
                categoryLabels.toArray(new String[0])
        );

        String[] names = {"Jaccard", "Dice", "Kulczynski"};
        byte[][] colors = {
                {(byte)70, (byte)130, (byte)180}, // SteelBlue
                {(byte)255, (byte)140, (byte)0},  // DarkOrange
                {(byte)34, (byte)139, (byte)34}   // ForestGreen
        };

        for (int i = colStart; i < colEnd; i++) {
            List<Double> yValues = new ArrayList<>();
            yValues.add(1.0); // đệm đầu (hợp lệ log)

            for (int r = firstRow; r <= lastRow; r++) {
                Cell cell = sheet.getRow(r).getCell(i);
                double value = cell != null ? cell.getNumericCellValue() : 0.0;
                yValues.add(value > 0 ? value : 1.0); // ép 0 -> 1.0
            }
            yValues.add(1.0); // đệm cuối

            XDDFNumericalDataSource<Double> ySeries = XDDFDataSourcesFactory.fromArray(
                    yValues.toArray(new Double[0]), null
            );

            XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(minSim, ySeries);
            series.setTitle(names[i - colStart], null);

            if (series.getShapeProperties() == null) {
                series.setShapeProperties(new XDDFShapeProperties());
            }
            XDDFSolidFillProperties fill = new XDDFSolidFillProperties(
                    XDDFColor.from(colors[i - colStart])
            );
            series.getShapeProperties().setFillProperties(fill);
        }

        data.setVaryColors(false);
        chart.plot(data);
    }

    /** Header riêng cho Model2 (cột 0 là "minSup") */
    private static void writeGroupedHeaderForModel2(XSSFSheet sheet, XSSFWorkbook workbook, String datasetName) {
        Map<String, CellStyle> styles = createGroupStyles(workbook);

        Row datasetRow = sheet.createRow(0);
        datasetRow.setHeightInPoints(35);
        CellRangeAddress titleRegion = new CellRangeAddress(0, 0, 0, 12);
        sheet.addMergedRegion(titleRegion);
        applyBorderToMergedRegion(sheet, titleRegion, styles.get("datasetTitle"));
        datasetRow.createCell(0).setCellValue(datasetName);

        Row groupRow = sheet.createRow(1);
        groupRow.setHeightInPoints(30);

        groupRow.createCell(1).setCellValue("Runtime (s)");
        CellRangeAddress runtimeRegion = new CellRangeAddress(1, 1, 1, 3);
        sheet.addMergedRegion(runtimeRegion);
        applyBorderToMergedRegion(sheet, runtimeRegion, styles.get("runtime"));

        groupRow.createCell(4).setCellValue("Used Memory (MB)");
        CellRangeAddress memoryRegion = new CellRangeAddress(1, 1, 4, 6);
        sheet.addMergedRegion(memoryRegion);
        applyBorderToMergedRegion(sheet, memoryRegion, styles.get("memory"));

        groupRow.createCell(7).setCellValue("Patterns Found");
        CellRangeAddress patternRegion = new CellRangeAddress(1, 1, 7, 9);
        sheet.addMergedRegion(patternRegion);
        applyBorderToMergedRegion(sheet, patternRegion, styles.get("pattern"));

        groupRow.createCell(10).setCellValue("Candidates Generated");
        CellRangeAddress candidatesRegion = new CellRangeAddress(1, 1, 10, 12);
        sheet.addMergedRegion(candidatesRegion);
        applyBorderToMergedRegion(sheet, candidatesRegion, styles.get("candidates"));

        Row methodRow = sheet.createRow(2);
        methodRow.setHeightInPoints(25);
        String[] titles = {"minSup", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc"};
        for (int i = 0; i < titles.length; i++) {
            Cell cell = methodRow.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(styles.get("default"));
        }
    }

    /** Line chart cho Model2 (X = minSup) */
    private static void drawChartForModel2(XSSFSheet sheet, int rowCount, String chartTitle, int colStart, int colEnd, int anchorColStart, int anchorRowStart, String yAxisTitle) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                anchorColStart, anchorRowStart, anchorColStart + 4, anchorRowStart + 13);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("MIN SUPPORT"); // Model2: X = minSup

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle(yAxisTitle);

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

        XDDFDataSource<Double> minSup = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(3, rowCount + 2, 0, 0));
        String[] names = {"Jaccard", "Dice", "Kulczynski"};
        MarkerStyle[] styles = {MarkerStyle.CIRCLE, MarkerStyle.STAR, MarkerStyle.SQUARE};

        for (int i = colStart; i < colEnd; i++) {
            XDDFNumericalDataSource<Double> y = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(3, rowCount + 2, i, i));
            XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(minSup, y);
            series.setTitle(names[i - colStart], null);
            series.setMarkerStyle(styles[(i - colStart) % styles.length]);
        }

        chart.plot(data);
    }

    /** Bar chart cho Model2 (X = minSup), không dùng log, có nhãn “đệm” chuỗi rỗng ở hai đầu */
    private static void drawBarChartForModel2(XSSFSheet sheet, int rowCount, String chartTitle,
                                              int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                anchorColStart, anchorRowStart, anchorColStart + 5, anchorRowStart + 13);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("MIN SUPPORT");

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("PATTERNS");

        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        data.setBarDirection(BarDirection.COL);

        int firstRow = 3;
        int lastRow = firstRow + rowCount - 1;

        // Nhãn trục X: thêm "" đầu/cuối để tạo khoảng đệm
        List<String> categoryLabels = new ArrayList<>();
        categoryLabels.add("");

        for (int r = firstRow; r <= lastRow; r++) {
            Cell cell = sheet.getRow(r).getCell(0);
            categoryLabels.add(cell.toString());
        }

        categoryLabels.add("");

        XDDFDataSource<String> minSup = XDDFDataSourcesFactory.fromArray(
                categoryLabels.toArray(new String[0])
        );

        String[] names = {"Jaccard", "Dice", "Kulczynski"};

        for (int i = colStart; i < colEnd; i++) {
            List<Double> yValues = new ArrayList<>();
            yValues.add(0.0); // cột đệm đầu

            for (int r = firstRow; r <= lastRow; r++) {
                Cell cell = sheet.getRow(r).getCell(i);
                yValues.add(cell != null ? cell.getNumericCellValue() : 0.0);
            }

            yValues.add(0.0); // cột đệm cuối

            XDDFNumericalDataSource<Double> ySeries = XDDFDataSourcesFactory.fromArray(
                    yValues.toArray(new Double[0]), null
            );

            XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(minSup, ySeries);
            series.setTitle(names[i - colStart], null);
        }

        data.setVaryColors(false);
        chart.plot(data);
    }

    /** Áp style/viền cho toàn bộ cells trong một vùng merge (đồng thời đảm bảo cell tồn tại) */
    private static void applyBorderToMergedRegion(XSSFSheet sheet, CellRangeAddress region, CellStyle style) {
        for (int row = region.getFirstRow(); row <= region.getLastRow(); row++) {
            Row sheetRow = sheet.getRow(row);
            if (sheetRow == null) sheetRow = sheet.createRow(row);
            for (int col = region.getFirstColumn(); col <= region.getLastColumn(); col++) {
                Cell cell = sheetRow.getCell(col);
                if (cell == null) cell = sheetRow.createCell(col);
                cell.setCellStyle(style);
            }
        }
    }
}
