package com.inventory.exception;

public class NotFoundException extends InventoryException {
    public NotFoundException(String what, String id) { super(what + " not found: " + id); }
}
