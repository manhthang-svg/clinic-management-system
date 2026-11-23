package clinic.backend.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// UC-33: ghi file Excel bang Apache POI: tieu de, cac dong nhan: gia tri, roi cac bang
// so van la so trong Excel (cong, loc duoc); tien dung dinh dang tien
final class StatisticsWorkbook implements AutoCloseable {

    // so tien (ghi voi dinh dang tien)
    record Money(BigDecimal amount) {
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final CellStyle title;
    private final CellStyle header;
    private final CellStyle bold;
    private final CellStyle number;
    private final CellStyle money;

    StatisticsWorkbook() {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        title = workbook.createCellStyle();
        title.setFont(titleFont);

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        bold = workbook.createCellStyle();
        bold.setFont(boldFont);

        header = workbook.createCellStyle();
        header.setFont(boldFont);
        header.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setBorderBottom(BorderStyle.THIN);
        header.setWrapText(true);

        number = workbook.createCellStyle();
        number.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        money = workbook.createCellStyle();
        money.setDataFormat(workbook.createDataFormat().getFormat("#,##0 \"₫\""));
    }

    SheetWriter sheet(String name) {
        return new SheetWriter(workbook.createSheet(name));
    }

    byte[] toBytes() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the statistics workbook", e);
        }
    }

    @Override
    public void close() {
        try {
            workbook.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String day(LocalDate date) {
        return date == null ? "" : date.format(DAY);
    }

    // ghi 1 sheet tu tren xuong
    final class SheetWriter {
        private final Sheet sheet;
        private int next;
        // do dai chu dai nhat moi cot, de tu chinh do rong cot
        private final java.util.Map<Integer, Integer> widths = new java.util.HashMap<>();

        private SheetWriter(Sheet sheet) {
            this.sheet = sheet;
        }

        SheetWriter title(String text) {
            Cell cell = sheet.createRow(next++).createCell(0);
            cell.setCellValue(text);
            cell.setCellStyle(title);
            return this;
        }

        // 1 dong "nhan: gia tri"
        SheetWriter line(String label, Object value) {
            Row row = sheet.createRow(next++);
            Cell labelCell = row.createCell(0);
            labelCell.setCellValue(label);
            labelCell.setCellStyle(bold);
            write(row.createCell(1), value);
            return this;
        }

        SheetWriter blank() {
            next++;
            return this;
        }

        // tieu de muc + dong tieu de bang
        SheetWriter table(String heading, String... columns) {
            if (heading != null) {
                Cell cell = sheet.createRow(next++).createCell(0);
                cell.setCellValue(heading);
                cell.setCellStyle(bold);
            }
            Row row = sheet.createRow(next++);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = row.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(header);
                measure(i, columns[i]);
            }
            return this;
        }

        SheetWriter row(Object... values) {
            Row row = sheet.createRow(next++);
            for (int i = 0; i < values.length; i++) {
                write(row.createCell(i), values[i]);
            }
            return this;
        }

        // dong tong (in dam)
        SheetWriter totalRow(Object... values) {
            row(values);
            Row row = sheet.getRow(next - 1);
            for (Cell cell : row) {
                CellStyle style = workbook.createCellStyle();
                style.cloneStyleFrom(cell.getCellStyle());
                style.setFont(workbook.getFontAt(bold.getFontIndex()));
                cell.setCellStyle(style);
            }
            return this;
        }

        // chinh do rong cot theo noi dung (10 - 60 ky tu)
        void fit() {
            widths.forEach((column, length) ->
                    sheet.setColumnWidth(column, Math.min(Math.max(length + 2, 10), 60) * 256));
        }

        private void measure(int column, String text) {
            widths.merge(column, text.length(), Math::max);
        }

        private void write(Cell cell, Object value) {
            if (value != null) {
                int column = cell.getColumnIndex();
                measure(column, value instanceof Money(BigDecimal amount)
                        ? String.format("%,.0f ₫", amount == null ? 0.0 : amount.doubleValue())
                        : value instanceof LocalDate date ? day(date) : value.toString());
            }
            if (value == null) {
                cell.setBlank();
            } else if (value instanceof Money(BigDecimal amount)) {
                cell.setCellValue(amount == null ? 0 : amount.doubleValue());
                cell.setCellStyle(money);
            } else if (value instanceof Number numeric) {
                cell.setCellValue(numeric.doubleValue());
                cell.setCellStyle(number);
            } else if (value instanceof LocalDate date) {
                cell.setCellValue(day(date));
            } else {
                cell.setCellValue(value.toString());
            }
        }
    }
}
