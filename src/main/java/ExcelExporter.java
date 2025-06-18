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

        writeGroupedHeaderWithStyle(sheet, workbook);
        writeData(sheet, summaryMap);
        autoSizeColumns(sheet, 13);

        int rowCount = summaryMap.size();

        drawChart(sheet, rowCount, "Runtime Comparison", 1, 4, 13, 30);
        drawChart(sheet, rowCount, "Memory Usage Comparison", 4, 7, 13, 48);
        drawChart(sheet, rowCount, "Filtered Patterns Comparison", 7, 10, 13, 66);
        drawChart(sheet, rowCount, "Candidates Generated Comparison", 10, 13, 13, 84);

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }

        workbook.close();
    }

    private static void writeGroupedHeaderWithStyle(XSSFSheet sheet, XSSFWorkbook workbook) {
        // ===== DÒNG 0: Tiêu đề dataset =====
        Row datasetRow = sheet.createRow(0);
        Cell datasetCell = datasetRow.createCell(0);
        datasetCell.setCellValue("CHESS");
        CellStyle titleStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        titleStyle.setFont(font);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        datasetCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));
    
        // ===== DÒNG 1: Nhóm thuộc tính =====
        Row groupRow = sheet.createRow(1);
        Map<String, CellStyle> styles = createGroupStyles(workbook);
    
        groupRow.createCell(0).setCellValue(""); // minSup không merge
    
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
    
        // ===== DÒNG 2: Tên độ đo (Jaccard, Dice, Kulc) =====
        Row methodRow = sheet.createRow(2);
        String[] titles = {
            "minSup",
            "Jaccard", "Dice", "Kulc",
            "Jaccard", "Dice", "Kulc",
            "Jaccard", "Dice", "Kulc",
            "Jaccard", "Dice", "Kulc"
        };
    
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
        int rowIdx = 3; // dữ liệu bắt đầu từ hàng 2
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
            for (String sim : order) {
                row.createCell(col++).setCellValue(data.get(sim).candidatesGenerated);
            }
        }
    }

    private static void autoSizeColumns(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.setColumnWidth(i, 4000); // hoặc 5000 tùy bạn muốn rộng bao nhiêu
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
                new CellRangeAddress(2, rowCount + 1, 0, 0)); // start from row 2

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

        String[] names = {"Jaccard", "Dice", "Kulczynski"};
        MarkerStyle[] styles = {
                MarkerStyle.CIRCLE,
                MarkerStyle.STAR,
                MarkerStyle.SQUARE
        };

        for (int i = colStart; i < colEnd; i++) {
            XDDFNumericalDataSource<Double> y = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(2, rowCount + 1, i, i));
            XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(minSup, y);
            series.setTitle(names[i - colStart], null);
            series.setSmooth(false);
            series.setMarkerStyle(styles[(i - colStart) % styles.length]);
        }

        chart.plot(data);
    }
}
