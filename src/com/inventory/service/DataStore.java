package com.inventory.service;

import com.inventory.model.*;

import java.io.Serializable;
import java.util.*;

/** Single container for all application data - this is what gets saved to disk. */
public class DataStore implements Serializable {
    private static final long serialVersionUID = 1L;

    public final Map<String, Product> products = new LinkedHashMap<>();
    public final Map<String, Supplier> suppliers = new LinkedHashMap<>();
    public final Map<String, User> users = new LinkedHashMap<>();
    public final List<Invoice> invoices = new ArrayList<>();
    public final Map<String, PurchaseOrder> purchaseOrders = new LinkedHashMap<>();
    public final Queue<PurchaseOrder> pendingOrders = new LinkedList<>();   // FIFO receiving
    public final List<Transaction> audit = new ArrayList<>();

    public String storeName = "Smart Mart";
    public double defaultTaxPercent = 18.0;
    public int nextInvoice = 1001;
    public int nextPurchaseOrder = 5001;
    public long nextTransaction = 1;
}
