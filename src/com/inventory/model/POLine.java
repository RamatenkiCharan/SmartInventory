package com.inventory.model;

import java.io.Serializable;

public class POLine implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String productId, productName;
    private final int qty;
    private final double unitCost;

    public POLine(Product p, int qty) {
        this.productId = p.getId(); this.productName = p.getName();
        this.qty = qty; this.unitCost = p.getCostPrice();
    }
    /** Rebuilds a stored line (used when loading from the database). */
    public POLine(String productId, String productName, int qty, double unitCost) {
        this.productId = productId; this.productName = productName; this.qty = qty; this.unitCost = unitCost;
    }
    public String getProductId()   { return productId; }
    public String getProductName() { return productName; }
    public int getQty()            { return qty; }
    public double getUnitCost()    { return unitCost; }
    public double total()          { return unitCost * qty; }
}
