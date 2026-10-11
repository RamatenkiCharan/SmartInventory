package com.inventory.model;

import java.io.Serializable;

/** Snapshot of a product at the time of sale (so later price edits don't change old invoices). */
public class InvoiceItem implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String productId, name;
    private final double unitPrice, costPrice;
    private final int qty;

    public InvoiceItem(Product p, int qty) {
        this.productId = p.getId(); this.name = p.getName();
        this.unitPrice = p.getPrice(); this.costPrice = p.getCostPrice(); this.qty = qty;
    }
    /** Rebuilds a stored line (used when loading from the database). */
    public InvoiceItem(String productId, String name, double unitPrice, double costPrice, int qty) {
        this.productId = productId; this.name = name; this.unitPrice = unitPrice;
        this.costPrice = costPrice; this.qty = qty;
    }
    public double getCostPrice() { return costPrice; }
    public String getProductId() { return productId; }
    public String getName()      { return name; }
    public double getUnitPrice() { return unitPrice; }
    public int getQty()          { return qty; }
    public double lineTotal()    { return unitPrice * qty; }
    public double lineProfit()   { return (unitPrice - costPrice) * qty; }
}
