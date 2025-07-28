import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xddf.usermodel.*;

import java.io.FileOutputStream;
import java.util.*;

public class ExcelExporter {

    public static void exportSummarySheet(Map<Double, Map<String, ResultRow>> summaryMap, String fileName, String datasetName) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Summary");

        writeGroupedHeaderWithStyle(sheet, workbook, datasetName);
        writeData(sheet, summaryMap);
        autoSizeColumns(sheet, 13);

        int rowCount = summaryMap.size();
        drawChart(sheet, rowCount, "Runtime", 1, 4, 13, 30, "RUNTIME (s)");
        drawChart(sheet, rowCount, "Memory Usage", 4, 7, 13, 48, "MEMORY (MB)");
        drawBarChart(sheet, rowCount, "Patterns Found", 7, 10, 13, 66);

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }

    private static void writeGroupedHeaderWithStyle(XSSFSheet sheet, XSSFWorkbook workbook, String datasetName) {
        Map<String, CellStyle> styles = createGroupStyles(workbook);
    
        // Tiêu đề dataset (dòng 0)
        Row datasetRow = sheet.createRow(0);
        datasetRow.setHeightInPoints(35); // Tăng chiều cao cho font size lớn hơn
        CellRangeAddress titleRegion = new CellRangeAddress(0, 0, 0, 12);
        sheet.addMergedRegion(titleRegion);
        applyBorderToMergedRegion(sheet, titleRegion, styles.get("datasetTitle"));
        datasetRow.getCell(0).setCellValue(datasetName);
    
        // Dòng 1: Nhóm tiêu đề
        Row groupRow = sheet.createRow(1);
        groupRow.setHeightInPoints(30); // Tăng chiều cao cho font size lớn hơn
    
        // Runtime
        groupRow.createCell(1).setCellValue("Runtime (s)");
        CellRangeAddress runtimeRegion = new CellRangeAddress(1, 1, 1, 3);
        sheet.addMergedRegion(runtimeRegion);
        applyBorderToMergedRegion(sheet, runtimeRegion, styles.get("runtime"));
    
        // Memory
        groupRow.createCell(4).setCellValue("Used Memory (MB)");
        CellRangeAddress memoryRegion = new CellRangeAddress(1, 1, 4, 6);
        sheet.addMergedRegion(memoryRegion);
        applyBorderToMergedRegion(sheet, memoryRegion, styles.get("memory"));
    
        // Patterns
        groupRow.createCell(7).setCellValue("Patterns Found");
        CellRangeAddress patternRegion = new CellRangeAddress(1, 1, 7, 9);
        sheet.addMergedRegion(patternRegion);
        applyBorderToMergedRegion(sheet, patternRegion, styles.get("pattern"));
    
        // Candidates
        groupRow.createCell(10).setCellValue("Candidates Generated");
        CellRangeAddress candidatesRegion = new CellRangeAddress(1, 1, 10, 12);
        sheet.addMergedRegion(candidatesRegion);
        applyBorderToMergedRegion(sheet, candidatesRegion, styles.get("candidates"));
    
        // Dòng 2: Tiêu đề thuật toán
        Row methodRow = sheet.createRow(2);
        methodRow.setHeightInPoints(25); // Tăng chiều cao cho font size lớn hơn
        String[] titles = {"minSup", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc"};
        for (int i = 0; i < titles.length; i++) {
            Cell cell = methodRow.createCell(i);
            cell.setCellValue(titles[i]);
            if (i >= 1 && i <= 3) cell.setCellStyle(styles.get("runtime"));
            else if (i >= 4 && i <= 6) cell.setCellStyle(styles.get("memory"));
            else if (i >= 7 && i <= 9) cell.setCellStyle(styles.get("pattern"));
            else if (i >= 10 && i <= 12) cell.setCellStyle(styles.get("candidates"));
            else cell.setCellStyle(styles.get("default"));
        }
    }
    
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

    private static CellStyle createStyle(XSSFWorkbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        // Bỏ màu fill
        // style.setFillForegroundColor(color.getIndex());
        // style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true); // Tự động xuống dòng
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 15); // Tăng font size lên 15
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        // Bỏ hoàn toàn màu fill
        // style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        // style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true); // Tự động xuống dòng
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16); // Title lớn hơn một chút
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static void writeData(XSSFSheet sheet, Map<Double, Map<String, ResultRow>> summaryMap) {
        int rowIdx = 3;
        String[] order = {"Jaccard", "Dice", "Kulczynski"};

        Workbook workbook = sheet.getWorkbook();

        // Style mặc định có border
        CellStyle defaultStyle = workbook.createCellStyle();
        defaultStyle.setBorderTop(BorderStyle.THIN);
        defaultStyle.setBorderBottom(BorderStyle.THIN);
        defaultStyle.setBorderLeft(BorderStyle.THIN);
        defaultStyle.setBorderRight(BorderStyle.THIN);
        defaultStyle.setWrapText(true); // Tự động xuống dòng
        
        // Thêm font size 15 cho default style
        Font defaultFont = workbook.createFont();
        defaultFont.setFontHeightInPoints((short) 15);
        defaultStyle.setFont(defaultFont);

        // Style riêng cho từng thuật toán
        Map<String, CellStyle> algoStyles = new HashMap<>();
        algoStyles.put("Jaccard", createAlgoStyle(workbook, IndexedColors.LIGHT_TURQUOISE, defaultStyle));
        algoStyles.put("Dice", createAlgoStyle(workbook, IndexedColors.LIGHT_YELLOW, defaultStyle));
        algoStyles.put("Kulczynski", createAlgoStyle(workbook, IndexedColors.LIGHT_GREEN, defaultStyle));

        for (Map.Entry<Double, Map<String, ResultRow>> entry : summaryMap.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(30); // Tăng chiều cao cho font size lớn hơn
            int col = 0;

            // Ghi minSup
            Cell minSupCell = row.createCell(col++);
            minSupCell.setCellValue(entry.getKey());
            minSupCell.setCellStyle(defaultStyle);

            // Runtime (chuyển từ ms sang s)
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).getRuntimeSeconds());
                cell.setCellStyle(algoStyles.get(sim));
            }

            // Memory
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).memoryUsageMb);
                cell.setCellStyle(algoStyles.get(sim));
            }

            // Patterns
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).filteredPatterns);
                cell.setCellStyle(algoStyles.get(sim));
            }

            // Candidates
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).candidatesGenerated);
                cell.setCellStyle(algoStyles.get(sim));
            }
        }
    }

    private static CellStyle createAlgoStyle(Workbook workbook, IndexedColors color, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        // Bỏ màu fill
        // style.setFillForegroundColor(color.getIndex());
        // style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true); // Tự động xuống dòng
        
        // Đảm bảo font size 15 cho algo styles
        Font algoFont = workbook.createFont();
        algoFont.setFontHeightInPoints((short) 15);
        style.setFont(algoFont);
        
        return style;
    }


    private static void autoSizeColumns(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) sheet.setColumnWidth(i, 12 * 256); // 12 characters
    }

    private static void drawChart(XSSFSheet sheet, int rowCount, String chartTitle, int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
        drawChart(sheet, rowCount, chartTitle, colStart, colEnd, anchorColStart, anchorRowStart, "VALUE");
    }

    private static void drawChart(XSSFSheet sheet, int rowCount, String chartTitle, int colStart, int colEnd, int anchorColStart, int anchorRowStart, String yAxisTitle) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                anchorColStart, anchorRowStart, anchorColStart + 4, anchorRowStart + 13);
    
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);
    
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM); // Nằm ngang dưới biểu đồ
    
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("MINSUP");
    
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
            
            // Bỏ màu fill cho line chart
            if (series.getShapeProperties() != null) {
                series.getShapeProperties().setFillProperties(new XDDFNoFillProperties());
            }
        }
    
        chart.plot(data);
    }
    
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

        int firstRow = 3;                // bắt đầu dữ liệu thực
        int lastRow = firstRow + rowCount - 1;

        // ⬇ Chuẩn bị danh sách minSup để hiển thị trên trục X (dư 2 dòng)
        List<String> categoryLabels = new ArrayList<>();
        categoryLabels.add(""); // dòng đầu ẩn

        for (int r = firstRow; r <= lastRow; r++) {
            Cell cell = sheet.getRow(r).getCell(0);
            categoryLabels.add(cell.toString()); // ví dụ "0.005"
        }

        categoryLabels.add(""); // dòng cuối ẩn

        XDDFDataSource<String> minSup = XDDFDataSourcesFactory.fromArray(
                categoryLabels.toArray(new String[0])
        );

        String[] names = {"Jaccard", "Dice", "Kulczynski"};

        for (int i = colStart; i < colEnd; i++) {
            List<Double> yValues = new ArrayList<>();
            yValues.add(0.0); // dòng đầu ẩn

            for (int r = firstRow; r <= lastRow; r++) {
                Cell cell = sheet.getRow(r).getCell(i);
                yValues.add(cell.getNumericCellValue());
            }

            yValues.add(0.0); // dòng cuối ẩn

            XDDFNumericalDataSource<Double> ySeries = XDDFDataSourcesFactory.fromArray(
                    yValues.toArray(new Double[0]), null
            );

            XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(minSup, ySeries);
            series.setTitle(names[i - colStart], null);
            
            // Bỏ màu fill cho bar chart
            if (series.getShapeProperties() != null) {
                series.getShapeProperties().setFillProperties(new XDDFNoFillProperties());
            }
        }

        data.setVaryColors(false); // Bỏ màu đa dạng
        chart.plot(data);
    }

    public static void exportMultipleSheetsSummary(Map<String, Map<Double, Map<String, ResultRow>>> allResults, String fileName) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        for (Map.Entry<String, Map<Double, Map<String, ResultRow>>> entry : allResults.entrySet()) {
            String datasetName = entry.getKey();
            XSSFSheet sheet = workbook.createSheet(datasetName);
            writeGroupedHeaderWithStyle(sheet, workbook, datasetName);
            writeData(sheet, entry.getValue());
            autoSizeColumns(sheet, 13);

            int rowCount = entry.getValue().size();
            
            int chartStartRow = rowCount + 5;
            drawChart(sheet, rowCount, "Runtime", 1, 4, 1, chartStartRow, "RUNTIME (s)"); // col 1,2,3
            drawChart(sheet, rowCount, "Memory Usage", 4, 7, 6, chartStartRow, "MEMORY (MB)"); // col 4,5,6
            drawBarChart(sheet, rowCount, "Patterns Found", 7, 10, 11, chartStartRow); // col 7,8,9

        }
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }

    // ✅ Phương thức riêng cho Model1 - trục X là minSim
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
            drawChartForModel1(sheet, rowCount, "Runtime", 1, 4, 1, chartStartRow, "RUNTIME (s)"); // col 1,2,3
            drawChartForModel1(sheet, rowCount, "Memory Usage", 4, 7, 6, chartStartRow, "MEMORY (MB)"); // col 4,5,6
            drawBarChartForModel1(sheet, rowCount, "Patterns Found", 7, 10, 11, chartStartRow); // col 7,8,9

        }
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }

    // ✅ Phương thức riêng cho Model2 - trục X là minSup
    public static void exportModel2Summary(Map<String, Map<Double, Map<String, ResultRow>>> allResults, String fileName) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        for (Map.Entry<String, Map<Double, Map<String, ResultRow>>> entry : allResults.entrySet()) {
            String datasetName = entry.getKey();
            XSSFSheet sheet = workbook.createSheet(datasetName);
            writeGroupedHeaderForModel2(sheet, workbook, datasetName);
            writeData(sheet, entry.getValue());
            autoSizeColumns(sheet, 13);

            int rowCount = entry.getValue().size();
            
            int chartStartRow = rowCount + 5;
            drawChartForModel2(sheet, rowCount, "Runtime", 1, 4, 1, chartStartRow, "RUNTIME (s)"); // col 1,2,3
            drawChartForModel2(sheet, rowCount, "Memory Usage", 4, 7, 6, chartStartRow, "MEMORY (MB)"); // col 4,5,6
            drawBarChartForModel2(sheet, rowCount, "Patterns Found", 7, 10, 11, chartStartRow); // col 7,8,9

        }
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }
    
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

    // ========== PHƯƠNG THỨC RIÊNG CHO MODEL1 (trục X = minSim) ==========
    
    private static void writeGroupedHeaderForModel1(XSSFSheet sheet, XSSFWorkbook workbook, String datasetName) {
        Map<String, CellStyle> styles = createGroupStyles(workbook);
    
        // Tiêu đề dataset (dòng 0)
        Row datasetRow = sheet.createRow(0);
        datasetRow.setHeightInPoints(35);
        CellRangeAddress titleRegion = new CellRangeAddress(0, 0, 0, 12);
        sheet.addMergedRegion(titleRegion);
        applyBorderToMergedRegion(sheet, titleRegion, styles.get("datasetTitle"));
        datasetRow.createCell(0).setCellValue(datasetName);
    
        // Dòng 1: Nhóm tiêu đề
        Row groupRow = sheet.createRow(1);
        groupRow.setHeightInPoints(30);
    
        // Runtime
        groupRow.createCell(1).setCellValue("Runtime (s)");
        CellRangeAddress runtimeRegion = new CellRangeAddress(1, 1, 1, 3);
        sheet.addMergedRegion(runtimeRegion);
        applyBorderToMergedRegion(sheet, runtimeRegion, styles.get("runtime"));
    
        // Memory
        groupRow.createCell(4).setCellValue("Used Memory (MB)");
        CellRangeAddress memoryRegion = new CellRangeAddress(1, 1, 4, 6);
        sheet.addMergedRegion(memoryRegion);
        applyBorderToMergedRegion(sheet, memoryRegion, styles.get("memory"));
    
        // Patterns
        groupRow.createCell(7).setCellValue("Patterns Found");
        CellRangeAddress patternRegion = new CellRangeAddress(1, 1, 7, 9);
        sheet.addMergedRegion(patternRegion);
        applyBorderToMergedRegion(sheet, patternRegion, styles.get("pattern"));
    
        // Candidates
        groupRow.createCell(10).setCellValue("Candidates Generated");
        CellRangeAddress candidatesRegion = new CellRangeAddress(1, 1, 10, 12);
        sheet.addMergedRegion(candidatesRegion);
        applyBorderToMergedRegion(sheet, candidatesRegion, styles.get("candidates"));
    
        // Dòng 2: Tiêu đề thuật toán
        Row methodRow = sheet.createRow(2);
        methodRow.setHeightInPoints(25);
        String[] titles = {"minSim", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc"};
        for (int i = 0; i < titles.length; i++) {
            Cell cell = methodRow.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(styles.get("default"));
        }
    }

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
        bottomAxis.setTitle("MIN SIMILARITY"); // ✅ Model1 trục X là minSim
    
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

    private static void drawBarChartForModel1(XSSFSheet sheet, int rowCount, String chartTitle,
                                    int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                anchorColStart, anchorRowStart, anchorColStart + 6, anchorRowStart + 15); // Tăng kích thước chart

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("MIN SIMILARITY"); // ✅ Model1 trục X là minSim

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("PATTERNS");
        
        // Sử dụng thang logarith để hiển thị tốt hơn các giá trị có độ lệch lớn
        leftAxis.setLogBase(10.0); 
        leftAxis.setMinimum(1.0); // Giá trị tối thiểu để tránh log(0)

        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        data.setBarDirection(BarDirection.COL);
        
        // Điều chỉnh khoảng cách giữa các cột để dễ nhìn hơn
        data.setGapWidth(200); // Tăng khoảng cách giữa các nhóm cột

        int firstRow = 3;
        int lastRow = firstRow + rowCount - 1;

        // Tạo category labels với 2 điểm 0 ở hai đầu cho chart
        List<String> categoryLabels = new ArrayList<>();
        
        // Thêm điểm 0.0 ở đầu
        categoryLabels.add("0.0");
        
        // Thêm dữ liệu thực từ table
        for (int r = firstRow; r <= lastRow; r++) {
            Cell cell = sheet.getRow(r).getCell(0);
            categoryLabels.add(cell.toString());
        }
        
        // Thêm điểm 1.0 ở cuối
        categoryLabels.add("1.0");

        XDDFDataSource<String> minSim = XDDFDataSourcesFactory.fromArray(
                categoryLabels.toArray(new String[0])
        );

        String[] names = {"Jaccard", "Dice", "Kulczynski"};
        
        // Định nghĩa màu sắc khác nhau cho từng thuật toán
        byte[][] colors = {
            {(byte)70, (byte)130, (byte)180}, // SteelBlue cho Jaccard
            {(byte)255, (byte)140, (byte)0}, // DarkOrange cho Dice  
            {(byte)34, (byte)139, (byte)34} // ForestGreen cho Kulczynski
        };

        for (int i = colStart; i < colEnd; i++) {
            List<Double> yValues = new ArrayList<>();

            // Thêm giá trị 0 ở đầu (cho minSim = 0.0)
            yValues.add(1.0); // Đặt 1.0 thay vì 0 để tránh lỗi với thang logarith

            // Thêm dữ liệu thực từ table
            for (int r = firstRow; r <= lastRow; r++) {
                Cell cell = sheet.getRow(r).getCell(i);
                double value = cell != null ? cell.getNumericCellValue() : 0.0;
                // Đảm bảo giá trị >= 1 cho thang logarith, nếu 0 thì set thành 1
                yValues.add(value > 0 ? value : 1.0);
            }
            
            // Thêm giá trị 0 ở cuối (cho minSim = 1.0)
            yValues.add(1.0); // Đặt 1.0 thay vì 0 để tránh lỗi với thang logarith

            XDDFNumericalDataSource<Double> ySeries = XDDFDataSourcesFactory.fromArray(
                    yValues.toArray(new Double[0]), null
            );

            XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(minSim, ySeries);
            series.setTitle(names[i - colStart], null);
            
            // Thiết lập màu sắc cho series
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

    // ========== PHƯƠNG THỨC RIÊNG CHO MODEL2 (trục X = minSup) ==========
    
    private static void writeGroupedHeaderForModel2(XSSFSheet sheet, XSSFWorkbook workbook, String datasetName) {
        Map<String, CellStyle> styles = createGroupStyles(workbook);
    
        // Tiêu đề dataset (dòng 0)
        Row datasetRow = sheet.createRow(0);
        datasetRow.setHeightInPoints(35);
        CellRangeAddress titleRegion = new CellRangeAddress(0, 0, 0, 12);
        sheet.addMergedRegion(titleRegion);
        applyBorderToMergedRegion(sheet, titleRegion, styles.get("datasetTitle"));
        datasetRow.createCell(0).setCellValue(datasetName);
    
        // Dòng 1: Nhóm tiêu đề
        Row groupRow = sheet.createRow(1);
        groupRow.setHeightInPoints(30);
    
        // Runtime
        groupRow.createCell(1).setCellValue("Runtime (s)");
        CellRangeAddress runtimeRegion = new CellRangeAddress(1, 1, 1, 3);
        sheet.addMergedRegion(runtimeRegion);
        applyBorderToMergedRegion(sheet, runtimeRegion, styles.get("runtime"));
    
        // Memory
        groupRow.createCell(4).setCellValue("Used Memory (MB)");
        CellRangeAddress memoryRegion = new CellRangeAddress(1, 1, 4, 6);
        sheet.addMergedRegion(memoryRegion);
        applyBorderToMergedRegion(sheet, memoryRegion, styles.get("memory"));
    
        // Patterns
        groupRow.createCell(7).setCellValue("Patterns Found");
        CellRangeAddress patternRegion = new CellRangeAddress(1, 1, 7, 9);
        sheet.addMergedRegion(patternRegion);
        applyBorderToMergedRegion(sheet, patternRegion, styles.get("pattern"));
    
        // Candidates
        groupRow.createCell(10).setCellValue("Candidates Generated");
        CellRangeAddress candidatesRegion = new CellRangeAddress(1, 1, 10, 12);
        sheet.addMergedRegion(candidatesRegion);
        applyBorderToMergedRegion(sheet, candidatesRegion, styles.get("candidates"));
    
        // Dòng 2: Tiêu đề thuật toán
        Row methodRow = sheet.createRow(2);
        methodRow.setHeightInPoints(25);
        String[] titles = {"minSup", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc", "Jaccard", "Dice", "Kulc"};
        for (int i = 0; i < titles.length; i++) {
            Cell cell = methodRow.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(styles.get("default"));
        }
    }

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
        bottomAxis.setTitle("MIN SUPPORT"); // ✅ Model2 trục X là minSup
    
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
        bottomAxis.setTitle("MIN SUPPORT"); // ✅ Model2 trục X là minSup

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("PATTERNS");

        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        data.setBarDirection(BarDirection.COL);

        int firstRow = 3;
        int lastRow = firstRow + rowCount - 1;

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
            yValues.add(0.0);

            for (int r = firstRow; r <= lastRow; r++) {
                Cell cell = sheet.getRow(r).getCell(i);
                yValues.add(cell != null ? cell.getNumericCellValue() : 0.0);
            }

            yValues.add(0.0);

            XDDFNumericalDataSource<Double> ySeries = XDDFDataSourcesFactory.fromArray(
                    yValues.toArray(new Double[0]), null
            );

            XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(minSup, ySeries);
            series.setTitle(names[i - colStart], null);
        }

        data.setVaryColors(false);
        chart.plot(data);
    }
    
    
    
}