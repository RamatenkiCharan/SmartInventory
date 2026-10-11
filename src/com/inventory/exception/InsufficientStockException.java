package com.inventory.exception;

public class InsufficientStockException extends InventoryException {
    public InsufficientStockException(String product, int available, int requested) {
        super("Insufficient stock for '" + product + "'. Available: " + available + ", requested: " + requested);
    }
}
