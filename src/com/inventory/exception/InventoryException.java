package com.inventory.exception;

/** Base class for all business-rule errors in the application. */
public class InventoryException extends RuntimeException {
    public InventoryException(String message) { super(message); }
}
