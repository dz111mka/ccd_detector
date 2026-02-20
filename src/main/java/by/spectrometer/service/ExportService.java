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
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
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
            String[] header = {"Pixel", "Intensity"};
            writer.writeNext(header);

            for (int i = 0; i < data.wavelength.length; i++) {
                String[] row = {
                        String.valueOf(i),
                        capturedY != null ? String.format("%.3f", capturedY[i]) : String.format("%.3f", data.raw[i])
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
            String[] headers = {"Pixel", "Intensity"};
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
                double intensity = capturedY != null ? capturedY[i] : data.raw[i];
                row.createCell(1).setCellValue(intensity);
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
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph title = new Paragraph("Спектральные данные", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Draw spectrum graph
            if (capturedY != null) {
                document.add(createSpectrumGraph(data, capturedY));
                document.add(new Paragraph("\n"));
            }

            // Create compact table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            
            // Set column widths
            float[] columnWidths = {15, 85};
            table.setWidths(columnWidths);

            // Table header
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BaseColor.WHITE);
            String[] headers = {"Pixel", "Intensity"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(BaseColor.DARK_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(3);
                table.addCell(cell);
            }

            // Table data - export every 10th pixel to reduce size
            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 7, BaseColor.BLACK);
            int step = 10; // Export every 10th pixel
            for (int i = 0; i < data.wavelength.length; i += step) {
                double intensity = capturedY != null ? capturedY[i] : data.raw[i];
                table.addCell(createCell(String.valueOf(i), dataFont));
                table.addCell(createCell(String.format("%.1f", intensity), dataFont));
            }

            document.add(table);
        } catch (DocumentException e) {
            throw new IOException("Error creating PDF document: " + e.getMessage(), e);
        } finally {
            document.close();
        }
    }

    private static PdfPTable createSpectrumGraph(SpectrumData data, double[] capturedY) {
        int width = 500;
        int height = 200;
        int padding = 30;
        
        // Create image buffer
        java.awt.image.BufferedImage bufferedImage = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = bufferedImage.createGraphics();
        
        // Set white background
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // Draw axes
        g2d.setColor(java.awt.Color.BLACK);
        g2d.drawLine(padding, padding, padding, height - padding);
        g2d.drawLine(padding, height - padding, width - padding, height - padding);
        
        // Calculate scale for X and Y
        double xScale = (width - 2 * padding) / (double) (data.wavelength.length - 1);
        double minY = 0;
        double maxY = 4096;
        double yScale = (height - 2 * padding) / (maxY - minY);
        
        // Draw Y axis labels
        g2d.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 8));
        for (int i = 0; i <= 5; i++) {
            double yValue = minY + (maxY - minY) * i / 5;
            int yPos = height - padding - (int)(yValue * yScale);
            String label = String.format("%.0f", yValue);
            g2d.drawString(label, padding - 25, yPos + 4);
        }
        
        // Draw X axis labels (show pixel numbers)
        for (int i = 0; i <= 4; i++) {
            int xPixel = i * (data.wavelength.length - 1) / 4;
            int xPos = padding + (int)(xPixel * xScale);
            String label = String.format("%d", xPixel);
            g2d.drawString(label, xPos - 10, height - padding + 20);
        }
        
        // Draw spectrum line
        g2d.setColor(java.awt.Color.BLUE);
        g2d.setStroke(new java.awt.BasicStroke(2.0f));
        
        for (int i = 0; i < data.wavelength.length - 1; i++) {
            int x1 = padding + (int)(i * xScale);
            int y1 = height - padding - (int)(capturedY[i] * yScale);
            int x2 = padding + (int)((i + 1) * xScale);
            int y2 = height - padding - (int)(capturedY[i + 1] * yScale);
            g2d.drawLine(x1, y1, x2, y2);
        }
        
        // Draw chart title
        g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 10));
        g2d.drawString("Спектральный график", width / 2 - 40, padding - 10);
        
        // Clean up
        g2d.dispose();
        
        // Convert to iText Image
        Image chartImage = null;
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedImage, "png", baos);
            chartImage = Image.getInstance(baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Create table to center image
        PdfPTable imageTable = new PdfPTable(1);
        imageTable.setWidthPercentage(100);
        imageTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        PdfPCell cell = new PdfPCell(chartImage);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        imageTable.addCell(cell);
        
        return imageTable;
    }

    private static PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(3);
        return cell;
    }
}