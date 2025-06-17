
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.util.List;

public class ExcelExporter {
    public static void export(List<ResultRow> results, String fileName) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Results");

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

        // Auto-size columns
        for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

        // Write to file
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            workbook.write(fos);
        }
        workbook.close();
    }
} 