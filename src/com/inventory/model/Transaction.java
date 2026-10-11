package com.inventory.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** One entry in the audit trail. */
public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Type {
        PRODUCT_ADDED, PRODUCT_UPDATED, PRODUCT_DELETED, PRICE_CHANGE,
        RESTOCK, ADJUSTMENT, SALE, RETURN,
        PO_CREATED, PO_RECEIVED, PO_CANCELLED,
        UNDO, SUPPLIER, USER
    }
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final long id;
    private final LocalDateTime time;
    private final String user;
    private final Type type;
    private final String productId;
    private final int qtyChange;
    private final String note;

    public Transaction(long id, String user, Type type, String productId, int qtyChange, String note) {
        this.id = id; this.user = user; this.type = type;
        this.productId = productId == null ? "-" : productId;
        this.qtyChange = qtyChange; this.note = note == null ? "" : note;
        this.time = LocalDateTime.now();
    }

    /** Rebuilds a stored entry (used when loading from the database). */
    public Transaction(long id, LocalDateTime time, String user, Type type, String productId, int qtyChange, String note) {
        this.id = id; this.time = time; this.user = user; this.type = type;
        this.productId = productId; this.qtyChange = qtyChange; this.note = note == null ? "" : note;
    }
    public long getId()          { return id; }
    public Type getType()        { return type; }
    public String getProductId() { return productId; }
    public String getUser()      { return user; }
    public LocalDateTime getTime() { return time; }
    public int getQtyChange()    { return qtyChange; }
    public String getNote()      { return note; }

    @Override public String toString() {
        return String.format("#%-4d %s  %-8s %-16s %-8s %+5d  %s",
                id, time.format(FMT), user, type, productId, qtyChange, note);
    }
}
