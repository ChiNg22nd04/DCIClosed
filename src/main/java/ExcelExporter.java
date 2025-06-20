import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;

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
        drawChart(sheet, rowCount, "Runtime", 1, 4, 13, 30);
        drawChart(sheet, rowCount, "Memory Usage", 4, 7, 13, 48);
        drawBarChart(sheet, rowCount, "Candidates Generated", 10, 13, 13, 66);

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }

    private static void writeGroupedHeaderWithStyle(XSSFSheet sheet, XSSFWorkbook workbook, String datasetName) {
        Map<String, CellStyle> styles = createGroupStyles(workbook);
    
        // Tiêu đề dataset (dòng 0)
        Row datasetRow = sheet.createRow(0);
        CellRangeAddress titleRegion = new CellRangeAddress(0, 0, 0, 12);
        sheet.addMergedRegion(titleRegion);
        applyBorderToMergedRegion(sheet, titleRegion, styles.get("datasetTitle"));
        datasetRow.getCell(0).setCellValue(datasetName);
    
        // Dòng 1: Nhóm tiêu đề
        Row groupRow = sheet.createRow(1);
    
        // Runtime
        groupRow.createCell(1).setCellValue("Runtime (ms)");
        CellRangeAddress runtimeRegion = new CellRangeAddress(1, 1, 1, 3);
        sheet.addMergedRegion(runtimeRegion);
        applyBorderToMergedRegion(sheet, runtimeRegion, styles.get("runtime"));
    
        // Memory
        groupRow.createCell(4).setCellValue("Peak memory");
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
        map.put("runtime", createStyle(workbook, IndexedColors.LIGHT_ORANGE));
        map.put("memory", createStyle(workbook, IndexedColors.LIGHT_GREEN));
        map.put("pattern", createStyle(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE));
        map.put("candidates", createStyle(workbook, IndexedColors.YELLOW1));
        map.put("default", createStyle(workbook, IndexedColors.GREY_25_PERCENT));
        map.put("datasetTitle", createTitleStyle(workbook));
        return map;
    }

    private static CellStyle createStyle(XSSFWorkbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static void writeData(XSSFSheet sheet, Map<Double, Map<String, ResultRow>> summaryMap) {
        int rowIdx = 3;
        String[] order = {"Jaccard", "Dice", "Kulczynski"};
    
        // Tạo style có border
        CellStyle borderedStyle = sheet.getWorkbook().createCellStyle();
        borderedStyle.setBorderTop(BorderStyle.THIN);
        borderedStyle.setBorderBottom(BorderStyle.THIN);
        borderedStyle.setBorderLeft(BorderStyle.THIN);
        borderedStyle.setBorderRight(BorderStyle.THIN);
    
        for (Map.Entry<Double, Map<String, ResultRow>> entry : summaryMap.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            int col = 0;
    
            Cell minSupCell = row.createCell(col++);
            minSupCell.setCellValue(entry.getKey());
            minSupCell.setCellStyle(borderedStyle);
    
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).runtimeMs);
                cell.setCellStyle(borderedStyle);
            }
    
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).memoryUsageMb);
                cell.setCellStyle(borderedStyle);
            }
    
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).filteredPatterns);
                cell.setCellStyle(borderedStyle);
            }
    
            for (String sim : order) {
                Cell cell = row.createCell(col++);
                cell.setCellValue(entry.getValue().get(sim).candidatesGenerated);
                cell.setCellStyle(borderedStyle);
            }
        }
    }
    
    private static void autoSizeColumns(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) sheet.setColumnWidth(i, 4000);
    }

    private static void drawChart(XSSFSheet sheet, int rowCount, String chartTitle, int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
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
        leftAxis.setTitle("VALUE");
    
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
    
private static void drawBarChart(XSSFSheet sheet, int rowCount, String chartTitle,
                                  int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
    XSSFDrawing drawing = sheet.createDrawingPatriarch();
    XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
            anchorColStart, anchorRowStart, anchorColStart + 4, anchorRowStart + 13);

    XSSFChart chart = drawing.createChart(anchor);
    chart.setTitleText(chartTitle);
    chart.setTitleOverlay(false);

    XDDFChartLegend legend = chart.getOrAddLegend();
    legend.setPosition(LegendPosition.BOTTOM);

    XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
    bottomAxis.setTitle("MINSUP");

    XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
    leftAxis.setTitle("CANDIDATES");

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
    }

    data.setVaryColors(true);
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
            drawChart(sheet, rowCount, "Runtime", 1, 4, 1, chartStartRow); // col 1,2,3
            drawChart(sheet, rowCount, "Memory Usage", 4, 7, 6, chartStartRow); // col 4,5,6
            drawBarChart(sheet, rowCount, "Candidates Generated", 10, 13, 11, chartStartRow); // col 10,11,12

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
    
    
    
}