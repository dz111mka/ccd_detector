package by.spectrometer.service;

import by.spectrometer.model.SpectrumData;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.BaseColor;
import com.opencsv.CSVWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

public class ExportService {

    public enum ExportFormat {
        CSV,
        EXCEL,
        PDF
    }

    public static void exportData(SpectrumData data, double[] capturedY, ExportFormat format, File file) throws IOException {
        switch (format) {
            case CSV -> exportToCSV(data, capturedY, file);
            case EXCEL -> exportToExcel(data, capturedY, file);
            case PDF -> exportToPDF(data, capturedY, file);
        }
    }

    private static void exportToCSV(SpectrumData data, double[] capturedY, File file) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            String[] header = {"Pixel", "Wavelength (nm)", "Raw", "Dark", "Reference", "Captured"};
            writer.writeNext(header);

            for (int i = 0; i < data.wavelength.length; i++) {
                String[] row = {
                        String.valueOf(i),
                        String.format("%.3f", data.wavelength[i]),
                        String.format("%.3f", data.raw[i]),
                        String.format("%.3f", data.dark[i]),
                        String.format("%.3f", data.reference[i]),
                        capturedY != null ? String.format("%.3f", capturedY[i]) : ""
                };
                writer.writeNext(row);
            }
        }
    }

    private static void exportToExcel(SpectrumData data, double[] capturedY, File file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fileOut = new FileOutputStream(file)) {
            Sheet sheet = workbook.createSheet("Spectrum Data");

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Pixel", "Wavelength (nm)", "Raw", "Dark", "Reference", "Captured"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                CellStyle style = workbook.createCellStyle();
                style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                org.apache.poi.ss.usermodel.Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            // Create data rows
            for (int i = 0; i < data.wavelength.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i);
                row.createCell(1).setCellValue(data.wavelength[i]);
                row.createCell(2).setCellValue(data.raw[i]);
                row.createCell(3).setCellValue(data.dark[i]);
                row.createCell(4).setCellValue(data.reference[i]);
                if (capturedY != null) {
                    row.createCell(5).setCellValue(capturedY[i]);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(fileOut);
        }
    }

    private static void exportToPDF(SpectrumData data, double[] capturedY, File file) throws IOException {
        Document document = new Document(PageSize.A4.rotate());
        try {
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph title = new Paragraph("Спектральные данные", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Create table
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);

            // Table header
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.WHITE);
            String[] headers = {"Pixel", "Wavelength (nm)", "Raw", "Dark", "Reference", "Captured"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(BaseColor.DARK_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(5);
                table.addCell(cell);
            }

            // Table data
            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.BLACK);
            for (int i = 0; i < data.wavelength.length; i++) {
                table.addCell(createCell(String.valueOf(i), dataFont));
                table.addCell(createCell(String.format("%.3f", data.wavelength[i]), dataFont));
                table.addCell(createCell(String.format("%.3f", data.raw[i]), dataFont));
                table.addCell(createCell(String.format("%.3f", data.dark[i]), dataFont));
                table.addCell(createCell(String.format("%.3f", data.reference[i]), dataFont));
                table.addCell(createCell(capturedY != null ? String.format("%.3f", capturedY[i]) : "", dataFont));
            }

            document.add(table);
        } catch (DocumentException e) {
            throw new IOException("Error creating PDF document: " + e.getMessage(), e);
        } finally {
            document.close();
        }
    }

    private static PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(3);
        return cell;
    }
}