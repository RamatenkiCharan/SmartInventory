package com.inventory.service;

import com.inventory.exception.*;
import com.inventory.model.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

/** Billing: checkout, GST/discount, void/return, receipts. */
public class SalesService {
    public static final double STAFF_MAX_DISCOUNT = 10.0;

    private final DataStore store;
    private final AuthService auth;
    private final AuditLog log;

    public SalesService(DataStore store, AuthService auth, AuditLog log) {
        this.store = store; this.auth = auth; this.log = log;
    }

    /** @param cart productId -> quantity (LinkedHashMap keeps the order items were added) */
    public Invoice checkout(String customer, Map<String, Integer> cart, double discountPct, double taxPct) {
        auth.require(Permission.SELL);
        if (cart.isEmpty()) throw new IllegalArgumentException("Cart is empty.");
        if (discountPct < 0 || discountPct > 100) throw new IllegalArgumentException("Discount must be 0-100%.");
        if (taxPct < 0 || taxPct > 100) throw new IllegalArgumentException("Tax must be 0-100%.");
        if (auth.current().getRole() == Role.STAFF && discountPct > STAFF_MAX_DISCOUNT)
            throw new AccessDeniedException("Staff may give at most " + STAFF_MAX_DISCOUNT + "% discount. Ask a manager.");

        // 1) validate everything first so a failed sale changes nothing
        LocalDate today = LocalDate.now();
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            Product p = store.products.get(e.getKey());
            if (p == null) throw new NotFoundException("Product", e.getKey());
            if (e.getValue() <= 0) throw new IllegalArgumentException("Invalid quantity for " + p.getName());
            if (p.isExpired(today)) throw new IllegalStateException("'" + p.getName() + "' is expired and cannot be sold.");
            if (p.getQuantity() < e.getValue()) throw new InsufficientStockException(p.getName(), p.getQuantity(), e.getValue());
        }
        // 2) apply
        Invoice inv = new Invoice("INV-" + store.nextInvoice++, customer.isBlank() ? "Walk-in" : customer,
                auth.currentUsername(), discountPct, taxPct);
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            Product p = store.products.get(e.getKey());
            p.setQuantity(p.getQuantity() - e.getValue());
            inv.addItem(new InvoiceItem(p, e.getValue()));
            log.log(Transaction.Type.SALE, p.getId(), -e.getValue(), inv.getInvoiceNo());
        }
        store.invoices.add(inv);
        return inv;
    }

    /** Cancel an invoice and put the items back in stock. */
    public Invoice voidInvoice(String invoiceNo, String reason) {
        auth.require(Permission.STOCK_MANAGE);
        Invoice inv = find(invoiceNo);
        if (inv.getStatus() == Invoice.Status.VOIDED) throw new IllegalStateException("Invoice is already voided.");
        for (InvoiceItem it : inv.getItems()) {
            Product p = store.products.get(it.getProductId());
            if (p != null) {
                p.setQuantity(p.getQuantity() + it.getQty());
                log.log(Transaction.Type.RETURN, p.getId(), it.getQty(), inv.getInvoiceNo() + " voided: " + reason);
            }
        }
        inv.markVoided();
        return inv;
    }

    public Invoice find(String invoiceNo) {
        auth.require(Permission.VIEW);
        String key = invoiceNo.trim().toUpperCase();
        if (!key.startsWith("INV-")) key = "INV-" + key;
        for (Invoice i : store.invoices) if (i.getInvoiceNo().equals(key)) return i;
        throw new NotFoundException("Invoice", invoiceNo);
    }

    public List<Invoice> all() {
        auth.require(Permission.VIEW);
        List<Invoice> copy = new ArrayList<>(store.invoices);
        Collections.reverse(copy);          // newest first
        return copy;
    }

    public Path saveReceipt(Invoice inv) {
        try {
            Path dir = Paths.get("data", "invoices");
            Files.createDirectories(dir);
            return Files.writeString(dir.resolve(inv.getInvoiceNo() + ".txt"), inv.toReceipt(store.storeName));
        } catch (IOException e) { throw new UncheckedIOException("Could not save receipt", e); }
    }
}
