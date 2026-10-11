package com.inventory.util;

import com.inventory.exception.InventoryException;
import com.inventory.model.Invoice;
import com.inventory.model.InvoiceItem;
import com.inventory.model.Product;
import com.inventory.service.InventoryService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Minimal CSV import/export (handles quoted fields containing commas). */
public final class CsvUtil {
    private CsvUtil() {}
    private static final String PRODUCT_HEADER = "id,name,category,supplierId,costPrice,price,quantity,reorderLevel,expiryDate";

    public static Path exportProducts(Collection<Product> products, Path file) {
        List<String> lines = new ArrayList<>();
        lines.add(PRODUCT_HEADER);
        for (Product p : products)
            lines.add(String.join(",", esc(p.getId()), esc(p.getName()), esc(p.getCategory()),
                    esc(p.getSupplierId() == null ? "" : p.getSupplierId()),
                    String.valueOf(p.getCostPrice()), String.valueOf(p.getPrice()),
                    String.valueOf(p.getQuantity()), String.valueOf(p.getReorderLevel()),
                    p.getExpiryDate() == null ? "" : p.getExpiryDate().toString()));
        return write(file, lines);
    }

    public static Path exportInvoices(Collection<Invoice> invoices, Path file) {
        List<String> lines = new ArrayList<>();
        lines.add("invoiceNo,date,customer,status,productId,item,qty,unitPrice,lineTotal,discount%,tax%,billedBy");
        for (Invoice inv : invoices)
            for (InvoiceItem it : inv.getItems())
                lines.add(String.join(",", inv.getInvoiceNo(), inv.getDateTime().toLocalDate().toString(),
                        esc(inv.getCustomer()), inv.getStatus().name(), it.getProductId(), esc(it.getName()),
                        String.valueOf(it.getQty()), String.valueOf(it.getUnitPrice()),
                        String.format("%.2f", it.lineTotal()), String.valueOf(inv.getDiscountPercent()),
                        String.valueOf(inv.getTaxPercent()), inv.getSoldBy()));
        return write(file, lines);
    }

    /** @return int[]{added, skipped}; skipped rows are reported in 'errors'. */
    public static int[] importProducts(Path file, InventoryService inventory, List<String> errors) {
        List<String> lines;
        try { lines = Files.readAllLines(file); }
        catch (IOException e) { throw new UncheckedIOException("Cannot read file", e); }
        int added = 0, skipped = 0;
        for (int i = 1; i < lines.size(); i++) {           // skip header
            String line = lines.get(i);
            if (line.isBlank()) continue;
            try {
                List<String> c = split(line);
                if (c.size() < 8) throw new IllegalArgumentException("expected at least 8 columns");
                LocalDate exp = (c.size() > 8 && !c.get(8).isBlank()) ? LocalDate.parse(c.get(8).trim()) : null;
                inventory.addProduct(new Product(c.get(0), c.get(1), c.get(2), c.get(3),
                        Double.parseDouble(c.get(4).trim()), Double.parseDouble(c.get(5).trim()),
                        Integer.parseInt(c.get(6).trim()), Integer.parseInt(c.get(7).trim()), exp));
                added++;
            } catch (InventoryException | IllegalArgumentException | java.time.DateTimeException e) {
                skipped++;
                errors.add("Line " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new int[]{added, skipped};
    }

    private static Path write(Path file, List<String> lines) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            return Files.write(file, lines);
        } catch (IOException e) { throw new UncheckedIOException("Cannot write file", e); }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return (s.contains(",") || s.contains("\"")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (ch == '"') quoted = false;
                else cur.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == ',') { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }
}
