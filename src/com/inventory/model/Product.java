package com.inventory.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class Product implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private String name;
    private String category;
    private String supplierId;      // optional
    private double costPrice;
    private double price;
    private int quantity;
    private int reorderLevel;
    private LocalDate expiryDate;   // optional

    public Product(String id, String name, String category, String supplierId,
                   double costPrice, double price, int quantity, int reorderLevel, LocalDate expiryDate) {
        if (id == null || !id.trim().matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Product ID may contain only letters, digits, '-' and '_'.");
        this.id = id.trim().toUpperCase();
        setName(name);
        setCategory(category);
        setCostPrice(costPrice);
        setPrice(price);
        setQuantity(quantity);
        setReorderLevel(reorderLevel);
        setSupplierId(supplierId);
        this.expiryDate = expiryDate;
    }

    public String getId()          { return id; }
    public String getName()        { return name; }
    public String getCategory()    { return category; }
    public String getSupplierId()  { return supplierId; }
    public double getCostPrice()   { return costPrice; }
    public double getPrice()       { return price; }
    public int getQuantity()       { return quantity; }
    public int getReorderLevel()   { return reorderLevel; }
    public LocalDate getExpiryDate() { return expiryDate; }

    public void setName(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Name is required.");
        name = v.trim();
    }
    public void setCategory(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Category is required.");
        category = v.trim();
    }
    public void setSupplierId(String v) { supplierId = (v == null || v.isBlank()) ? null : v.trim().toUpperCase(); }
    public void setCostPrice(double v) {
        if (v < 0) throw new IllegalArgumentException("Cost price cannot be negative.");
        costPrice = v;
    }
    public void setPrice(double v) {
        if (v < 0) throw new IllegalArgumentException("Price cannot be negative.");
        price = v;
    }
    public void setQuantity(int v) {
        if (v < 0) throw new IllegalArgumentException("Quantity cannot be negative.");
        quantity = v;
    }
    public void setReorderLevel(int v) {
        if (v < 0) throw new IllegalArgumentException("Reorder level cannot be negative.");
        reorderLevel = v;
    }
    public void setExpiryDate(LocalDate d) { expiryDate = d; }

    // ---- derived values ----
    public boolean isLowStock()                 { return quantity <= reorderLevel; }
    public boolean isExpired(LocalDate today)   { return expiryDate != null && expiryDate.isBefore(today); }
    public long daysToExpiry(LocalDate today)   { return expiryDate == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(today, expiryDate); }
    public double costValue()                   { return costPrice * quantity; }
    public double retailValue()                 { return price * quantity; }
    public double marginPercent()               { return price == 0 ? 0 : (price - costPrice) / price * 100.0; }
    /** Suggested order quantity to bring stock back to twice the reorder level. */
    public int suggestedReorderQty()            { return Math.max(reorderLevel * 2 - quantity, 1); }

    @Override public boolean equals(Object o) { return this == o || (o instanceof Product p && id.equals(p.id)); }
    @Override public int hashCode()           { return Objects.hash(id); }
}
