
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;

import java.io.FileOutputStream;
import java.util.*;


public class ExcelExporter {

    // Ghi nhiều sheet cho từng độ đo tương đồng
    public static void exportMultipleSheets(Map<String, List<ResultRow>> allResults, String fileName) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();

        for (Map.Entry<String, List<ResultRow>> entry : allResults.entrySet()) {
            String similarityName = entry.getKey();
            List<ResultRow> rows = entry.getValue();

            XSSFSheet sheet = workbook.createSheet(similarityName);

            writeHeader(sheet);
            writeData(sheet, rows);
            autoSizeColumns(sheet, 5); // 5 cột vì thêm Memory
            drawChart(sheet, rows.size(), similarityName);
        }

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }

        workbook.close();
    }

    // Viết tiêu đề bảng
    private static void writeHeader(XSSFSheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("minSup");
        header.createCell(1).setCellValue("runtime(ms)");
        header.createCell(2).setCellValue("memory(MB)");
        header.createCell(3).setCellValue("closedPatterns");
        header.createCell(4).setCellValue("filteredPatterns");
    }

    // Ghi dữ liệu thực nghiệm
    private static void writeData(XSSFSheet sheet, List<ResultRow> rows) {
        int rowIdx = 1;
        for (ResultRow row : rows) {
            Row excelRow = sheet.createRow(rowIdx++);
            excelRow.createCell(0).setCellValue(row.minSupRatio);
            excelRow.createCell(1).setCellValue(row.runtimeMs);
            excelRow.createCell(2).setCellValue(row.memoryUsageMb); // Thêm memory
            excelRow.createCell(3).setCellValue(row.closedPatterns);
            excelRow.createCell(4).setCellValue(row.filteredPatterns);
        }
    }

    // Tự động điều chỉnh độ rộng cột
    private static void autoSizeColumns(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // Vẽ biểu đồ Line Chart
    private static void drawChart(XSSFSheet sheet, int rowCount, String similarityName) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 6, 1, 16, 20);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(similarityName + " - Runtime, Memory & Patterns vs minSup");
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);

        // Trục
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("minSup");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Value");

        // Dữ liệu trục X
        XDDFDataSource<Double> minSup = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, rowCount, 0, 0));

        // Dữ liệu các series
        XDDFNumericalDataSource<Double> runtime = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, rowCount, 1, 1));
        XDDFNumericalDataSource<Double> memory = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, rowCount, 2, 2));
        XDDFNumericalDataSource<Double> closed = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, rowCount, 3, 3));
        XDDFNumericalDataSource<Double> filtered = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, rowCount, 4, 4));

        // Tạo biểu đồ đường
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

        // Thêm các series
        addSeries(data, minSup, runtime, "Runtime (ms)", MarkerStyle.CIRCLE);
        addSeries(data, minSup, memory, "Memory (MB)", MarkerStyle.DIAMOND);
        addSeries(data, minSup, closed, "Closed Patterns", MarkerStyle.SQUARE);
        addSeries(data, minSup, filtered, "Filtered Patterns", MarkerStyle.TRIANGLE);

        chart.plot(data);
    }

    // Thêm từng series vào biểu đồ
    private static void addSeries(XDDFLineChartData data, XDDFDataSource<?> x,
                                  XDDFNumericalDataSource<? extends Number> y,
                                  String title, MarkerStyle style) {
        XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(x, y);
        series.setTitle(title, null);
        series.setSmooth(false);
        series.setMarkerStyle(style);
    }
}
