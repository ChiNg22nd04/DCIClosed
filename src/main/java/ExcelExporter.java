
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;

import java.io.FileOutputStream;
import java.util.List;

public class ExcelExporter {
    public static void export(List<ResultRow> results, String fileName) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Results");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("minSup");
        header.createCell(1).setCellValue("runtime(ms)");
        header.createCell(2).setCellValue("closedPatterns");
        header.createCell(3).setCellValue("filtered");

        // Data
        int rowIdx = 1;
        for (ResultRow row : results) {
            Row excelRow = sheet.createRow(rowIdx++);
            excelRow.createCell(0).setCellValue(row.minSupRatio);
            excelRow.createCell(1).setCellValue(row.runtimeMs);
            excelRow.createCell(2).setCellValue(row.closedPatterns);
            excelRow.createCell(3).setCellValue(row.filteredPatterns);
        }

        // Auto-size
        for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

        // Thêm biểu đồ
        drawChart(sheet, results.size(), workbook);

        // Save file
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private static void drawChart(XSSFSheet sheet, int rowCount, XSSFWorkbook workbook) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();

        // Vị trí biểu đồ (Cột 5 đến 12, dòng 1 đến 20)
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 5, 1, 12, 20);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Runtime & Closed Patterns vs minSup");
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);

        // Trục X - minSup
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("minSup");

        // Trục Y - Giá trị
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Value");

        // Dữ liệu trục X (Category)
        XDDFDataSource<Double> minSup = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, rowCount, 0, 0));

        // Dữ liệu cho Runtime
        XDDFNumericalDataSource<Double> runtime = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, rowCount, 1, 1));

        // Dữ liệu cho closedPatterns
        XDDFNumericalDataSource<Double> patterns = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, rowCount, 2, 2));

        // Tạo biểu đồ đường
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

        XDDFLineChartData.Series series1 = (XDDFLineChartData.Series) data.addSeries(minSup, runtime);
        series1.setTitle("Runtime (ms)", null);
        series1.setSmooth(false);
        series1.setMarkerStyle(MarkerStyle.CIRCLE);

        XDDFLineChartData.Series series2 = (XDDFLineChartData.Series) data.addSeries(minSup, patterns);
        series2.setTitle("Closed Patterns", null);
        series2.setSmooth(false);
        series2.setMarkerStyle(MarkerStyle.SQUARE);

        chart.plot(data);
    }
}
