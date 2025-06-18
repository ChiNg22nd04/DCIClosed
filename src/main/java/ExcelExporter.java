import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;

import java.io.FileOutputStream;
import java.util.*;

public class ExcelExporter {

    public static void exportSummarySheet(Map<Double, Map<String, ResultRow>> summaryMap, String fileName) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Summary");

        writeHeader(sheet);
        writeData(sheet, summaryMap);
        autoSizeColumns(sheet, 10);

        int rowCount = summaryMap.size();

        drawChart(sheet, rowCount, "Runtime Comparison", 1, 4, 13, 30);
        drawChart(sheet, rowCount, "Memory Usage Comparison", 4, 7, 13, 48);
        drawChart(sheet, rowCount, "Filtered Patterns Comparison", 7, 10, 13, 66);

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }

        workbook.close();
    }

    private static void writeHeader(XSSFSheet sheet) {
        Row header = sheet.createRow(0);
        String[] titles = {
            "minSup",
            "Jaccard_Runtime", "Dice_Runtime", "Kulc_Runtime",
            "Jaccard_Mem", "Dice_Mem", "Kulc_Mem",
            "Jaccard_Patterns", "Dice_Patterns", "Kulc_Patterns"
        };
        for (int i = 0; i < titles.length; i++) {
            header.createCell(i).setCellValue(titles[i]);
        }
    }

    private static void writeData(XSSFSheet sheet, Map<Double, Map<String, ResultRow>> summaryMap) {
        int rowIdx = 1;
        String[] order = {"Jaccard", "Dice", "Kulczynski"};

        for (Map.Entry<Double, Map<String, ResultRow>> entry : summaryMap.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            double minSup = entry.getKey();
            Map<String, ResultRow> data = entry.getValue();

            row.createCell(0).setCellValue(minSup);
            int col = 1;

            for (String sim : order) {
                row.createCell(col++).setCellValue(data.get(sim).runtimeMs);
            }
            for (String sim : order) {
                row.createCell(col++).setCellValue(data.get(sim).memoryUsageMb);
            }
            for (String sim : order) {
                row.createCell(col++).setCellValue(data.get(sim).filteredPatterns);
            }
        }
    }

    private static void autoSizeColumns(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void drawChart(XSSFSheet sheet, int rowCount, String chartTitle,
                                  int colStart, int colEnd, int anchorColStart, int anchorRowStart) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(
                0, 0, 0, 0,
                anchorColStart, anchorRowStart,
                anchorColStart + 8, anchorRowStart + 15
        );

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("minSup");

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Value");

        XDDFDataSource<Double> minSup = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, rowCount, 0, 0));

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

        String[] names = {"Jaccard", "Dice", "Kulczynski"};
        MarkerStyle[] styles = {
                MarkerStyle.CIRCLE,
                MarkerStyle.STAR,
                MarkerStyle.SQUARE
        };

        for (int i = colStart; i < colEnd; i++) {
            XDDFNumericalDataSource<Double> y = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(1, rowCount, i, i));
            XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(minSup, y);
            series.setTitle(names[i - colStart], null);
            series.setSmooth(false);
            series.setMarkerStyle(styles[(i - colStart) % styles.length]);
        }

        chart.plot(data);
    }
}
