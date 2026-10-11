package com.inventory.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Invoice implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Status { PAID, VOIDED }

    private final String invoiceNo, customer, soldBy;
    private final LocalDateTime dateTime;
    private final List<InvoiceItem> items = new ArrayList<>();
    private final double discountPercent, taxPercent;
    private Status status = Status.PAID;

    public Invoice(String invoiceNo, String customer, String soldBy, double discountPercent, double taxPercent) {
        this.invoiceNo = invoiceNo; this.customer = customer; this.soldBy = soldBy;
        this.discountPercent = discountPercent; this.taxPercent = taxPercent;
        this.dateTime = LocalDateTime.now();
    }

    /** Rebuilds a stored invoice (used when loading from the database). */
    public Invoice(String invoiceNo, String customer, String soldBy, LocalDateTime dateTime,
                   double discountPercent, double taxPercent, Status status) {
        this.invoiceNo = invoiceNo; this.customer = customer; this.soldBy = soldBy;
        this.dateTime = dateTime; this.discountPercent = discountPercent; this.taxPercent = taxPercent;
        this.status = status;
    }

    public void addItem(InvoiceItem i) { items.add(i); }
    public List<InvoiceItem> getItems() { return Collections.unmodifiableList(items); }
    public String getInvoiceNo() { return invoiceNo; }
    public String getCustomer()  { return customer; }
    public String getSoldBy()    { return soldBy; }
    public LocalDateTime getDateTime() { return dateTime; }
    public Status getStatus()    { return status; }
    public void markVoided()     { status = Status.VOIDED; }
    public double getDiscountPercent() { return discountPercent; }
    public double getTaxPercent()      { return taxPercent; }

    public double subtotal()       { return items.stream().mapToDouble(InvoiceItem::lineTotal).sum(); }
    public double discountAmount() { return subtotal() * discountPercent / 100.0; }
    public double taxAmount()      { return (subtotal() - discountAmount()) * taxPercent / 100.0; }
    public double total()          { return subtotal() - discountAmount() + taxAmount(); }
    public double profit()         { return items.stream().mapToDouble(InvoiceItem::lineProfit).sum() - discountAmount(); }

    public String toReceipt(String storeName) {
        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(60) + "\n";
        sb.append(line).append(String.format("%38s%n", storeName.toUpperCase()));
        sb.append(line);
        sb.append(String.format(" Invoice  : %s   [%s]%n", invoiceNo, status));
        sb.append(String.format(" Date     : %s%n", dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
        sb.append(String.format(" Customer : %s%n Billed by: %s%n", customer, soldBy));
        sb.append("-".repeat(60)).append('\n');
        sb.append(String.format(" %-8s %-22s %5s %10s %10s%n", "ID", "ITEM", "QTY", "PRICE", "TOTAL"));
        for (InvoiceItem i : items)
            sb.append(String.format(" %-8s %-22s %5d %10.2f %10.2f%n",
                    i.getProductId(), i.getName().length() > 22 ? i.getName().substring(0, 22) : i.getName(),
                    i.getQty(), i.getUnitPrice(), i.lineTotal()));
        sb.append("-".repeat(60)).append('\n');
        sb.append(String.format(" %-44s %10.2f%n", "Subtotal", subtotal()));
        sb.append(String.format(" %-44s %10.2f%n", "Discount (" + discountPercent + "%)", -discountAmount()));
        sb.append(String.format(" %-44s %10.2f%n", "Tax/GST (" + taxPercent + "%)", taxAmount()));
        sb.append(String.format(" %-44s %10.2f%n", "GRAND TOTAL (Rs.)", total()));
        sb.append(line).append("      Thank you for shopping with us!\n");
        return sb.toString();
    }
}
