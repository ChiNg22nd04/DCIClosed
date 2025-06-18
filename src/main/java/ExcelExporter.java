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

        drawChart(sheet, rowCount, "Runtime Comparison", 1, 4, 13, 30);
        drawChart(sheet, rowCount, "Memory Usage Comparison", 4, 7, 13, 48);
        drawBarChart(sheet, rowCount, "Candidates Generated Comparison", 10, 13, 13, 66);

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }

    private static void writeGroupedHeaderWithStyle(XSSFSheet sheet, XSSFWorkbook workbook, String datasetName) {
        Row datasetRow = sheet.createRow(0);
        Cell datasetCell = datasetRow.createCell(0);
        datasetCell.setCellValue(datasetName);
        CellStyle titleStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        titleStyle.setFont(font);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        datasetCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));

        Row groupRow = sheet.createRow(1);
        Map<String, CellStyle> styles = createGroupStyles(workbook);

        groupRow.createCell(0).setCellValue("");
        groupRow.createCell(1).setCellValue("Runtime (ms)");
        groupRow.getCell(1).setCellStyle(styles.get("runtime"));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 1, 3));

        groupRow.createCell(4).setCellValue("Peak memory");
        groupRow.getCell(4).setCellStyle(styles.get("memory"));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 4, 6));

        groupRow.createCell(7).setCellValue("Patterns Found");
        groupRow.getCell(7).setCellStyle(styles.get("pattern"));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 7, 9));

        groupRow.createCell(10).setCellValue("Candidates Generated");
        groupRow.getCell(10).setCellStyle(styles.get("candidates"));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 10, 12));

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

    private static void writeData(XSSFSheet sheet, Map<Double, Map<String, ResultRow>> summaryMap) {
        int rowIdx = 3;
        String[] order = {"Jaccard", "Dice", "Kulczynski"};
        for (Map.Entry<Double, Map<String, ResultRow>> entry : summaryMap.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            int col = 1;
            for (String sim : order) row.createCell(col++).setCellValue(entry.getValue().get(sim).runtimeMs);
            for (String sim : order) row.createCell(col++).setCellValue(entry.getValue().get(sim).memoryUsageMb);
            for (String sim : order) row.createCell(col++).setCellValue(entry.getValue().get(sim).filteredPatterns);
            for (String sim : order) row.createCell(col++).setCellValue(entry.getValue().get(sim).candidatesGenerated);
        }
    }

    private static void autoSizeColumns(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) sheet.setColumnWidth(i, 4000);
    }

    private static void drawChart(XSSFSheet sheet, int rowCount, String chartTitle,
    int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, anchorColStart, anchorRowStart, anchorColStart + 8, anchorRowStart + 15);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("minSup");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Value");

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        XDDFDataSource<Double> minSup = XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(3, rowCount + 2, 0, 0));

        String[] names = {"Jaccard", "Dice", "Kulczynski"};
        MarkerStyle[] styles = {MarkerStyle.CIRCLE, MarkerStyle.STAR, MarkerStyle.SQUARE};

        for (int i = colStart; i < colEnd; i++) {
        XDDFNumericalDataSource<Double> y = XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(3, rowCount + 2, i, i));
        XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(minSup, y);
        series.setTitle(names[i - colStart], null);
        series.setMarkerStyle(styles[(i - colStart) % styles.length]);
        }

        chart.plot(data);
        }

        private static void drawBarChart(XSSFSheet sheet, int rowCount, String chartTitle,
            int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, anchorColStart, anchorRowStart, anchorColStart + 8, anchorRowStart + 15);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("minSup");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Candidates");

        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        data.setBarDirection(BarDirection.COL);

        XDDFDataSource<Double> minSup = XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(3, rowCount + 2, 0, 0));
        String[] names = {"Jaccard", "Dice", "Kulczynski"};

        for (int i = colStart; i < colEnd; i++) {
        XDDFNumericalDataSource<Double> y = XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(3, rowCount + 2, i, i));
        XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(minSup, y);
        series.setTitle(names[i - colStart], null);
        }

        chart.plot(data);
        }

   
    private static XSSFChart createChart(XSSFSheet sheet, String title, int col1, int row1) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col1 + 8, row1 + 15);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);
        chart.createCategoryAxis(AxisPosition.BOTTOM).setTitle("minSup");
        chart.createValueAxis(AxisPosition.LEFT).setTitle("Value");
        return chart;
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
            drawChart(sheet, rowCount, "Runtime", 1, 4, 13, 30);
            drawChart(sheet, rowCount, "Memory", 4, 7, 13, 48);
            drawBarChart(sheet, rowCount, "Candidates", 10, 13, 13, 66);
        }
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
        workbook.close();
    }
}
