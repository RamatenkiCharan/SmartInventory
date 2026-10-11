package com.inventory.service;

import com.inventory.exception.*;
import com.inventory.model.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Product and stock management.
 * Collections in use: LinkedHashMap (store), TreeMap/TreeSet (grouping, expiry index),
 * PriorityQueue (low stock), ArrayDeque via UndoManager, Iterator (purge), Comparator sorting.
 */
public class InventoryService {
    private final DataStore store;
    private final AuthService auth;
    private final AuditLog log;
    private final UndoManager undo;

    public InventoryService(DataStore store, AuthService auth, AuditLog log, UndoManager undo) {
        this.store = store; this.auth = auth; this.log = log; this.undo = undo;
    }

    // ================= CRUD =================
    public Product addProduct(Product p) {
        auth.require(Permission.PRODUCT_EDIT);
        if (store.products.containsKey(p.getId())) throw new DuplicateException("Product ID already exists: " + p.getId());
        if (p.getSupplierId() != null && !store.suppliers.containsKey(p.getSupplierId()))
            throw new NotFoundException("Supplier", p.getSupplierId());
        store.products.put(p.getId(), p);
        log.log(Transaction.Type.PRODUCT_ADDED, p.getId(), p.getQuantity(), p.getName());
        return p;
    }

    public Product get(String id) {
        auth.require(Permission.VIEW);
        Product p = store.products.get(id.trim().toUpperCase());
        if (p == null) throw new NotFoundException("Product", id);
        return p;
    }

    public void delete(String id) {
        auth.require(Permission.PRODUCT_DELETE);
        Product p = get(id);
        boolean onOrder = store.pendingOrders.stream().anyMatch(po -> po.getLines().stream().anyMatch(l -> l.getProductId().equals(p.getId())));
        if (onOrder) throw new IllegalStateException("Product is on a pending purchase order.");
        store.products.remove(p.getId());
        log.log(Transaction.Type.PRODUCT_DELETED, p.getId(), -p.getQuantity(), p.getName());
    }

    /** Edit a single field. Price changes are undoable. */
    public void edit(String id, String field, String value) {
        auth.require(Permission.PRODUCT_EDIT);
        Product p = get(id);
        switch (field) {
            case "name"     -> p.setName(value);
            case "category" -> p.setCategory(value);
            case "cost"     -> p.setCostPrice(Double.parseDouble(value));
            case "reorder"  -> p.setReorderLevel(Integer.parseInt(value));
            case "expiry"   -> p.setExpiryDate(value.isBlank() ? null : LocalDate.parse(value));
            case "supplier" -> {
                if (!value.isBlank() && !store.suppliers.containsKey(value.trim().toUpperCase()))
                    throw new NotFoundException("Supplier", value);
                p.setSupplierId(value);
            }
            case "price"    -> { changePrice(p, Double.parseDouble(value)); return; }
            default -> throw new IllegalArgumentException("Unknown field: " + field);
        }
        log.log(Transaction.Type.PRODUCT_UPDATED, p.getId(), 0, "Edited " + field);
    }

    private void changePrice(Product p, double newPrice) {
        if (newPrice < 0) throw new IllegalArgumentException("Price cannot be negative.");
        final double oldPrice = p.getPrice();
        undo.execute(new Command() {
            @Override public void execute() {
                sameProduct(p); p.setPrice(newPrice);
                log.log(Transaction.Type.PRICE_CHANGE, p.getId(), 0, String.format("%.2f -> %.2f", oldPrice, newPrice));
            }
            @Override public void undo() {
                sameProduct(p); p.setPrice(oldPrice);
                log.log(Transaction.Type.UNDO, p.getId(), 0, String.format("Price restored to %.2f", oldPrice));
            }
            @Override public String description() { return String.format("Price of %s: %.2f -> %.2f", p.getName(), oldPrice, newPrice); }
        });
    }

    /** Percentage price change for every product in a category (e.g. +10 or -5). */
    public int bulkPriceChange(String category, double percent) {
        auth.require(Permission.PRODUCT_EDIT);
        if (percent <= -100) throw new IllegalArgumentException("Percent must be greater than -100.");
        int n = 0;
        for (Product p : store.products.values()) {
            if (p.getCategory().equalsIgnoreCase(category.trim())) {
                double old = p.getPrice();
                p.setPrice(Math.round(old * (1 + percent / 100.0) * 100.0) / 100.0);
                log.log(Transaction.Type.PRICE_CHANGE, p.getId(), 0, String.format("Bulk %+.1f%%: %.2f -> %.2f", percent, old, p.getPrice()));
                n++;
            }
        }
        if (n == 0) throw new NotFoundException("Category", category);
        return n;
    }

    /** Iterator-based removal of every product with zero stock. */
    public int purgeOutOfStock() {
        auth.require(Permission.PRODUCT_DELETE);
        int count = 0;
        Iterator<Product> it = store.products.values().iterator();
        while (it.hasNext()) {
            Product p = it.next();
            boolean onOrder = store.pendingOrders.stream().anyMatch(po -> po.getLines().stream().anyMatch(l -> l.getProductId().equals(p.getId())));
            if (p.getQuantity() == 0 && !onOrder) {
                it.remove();
                log.log(Transaction.Type.PRODUCT_DELETED, p.getId(), 0, "Purged (out of stock)");
                count++;
            }
        }
        return count;
    }

    // ================= STOCK =================
    public void restock(String id, int qty, String note) {
        auth.require(Permission.STOCK_MANAGE);
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive.");
        applyStock(get(id), qty, Transaction.Type.RESTOCK, note);
    }

    /** Remove stock for damage / loss / correction. */
    public void removeStock(String id, int qty, String reason) {
        auth.require(Permission.STOCK_MANAGE);
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive.");
        applyStock(get(id), -qty, Transaction.Type.ADJUSTMENT, reason);
    }

    private void applyStock(Product p, int delta, Transaction.Type type, String note) {
        if (p.getQuantity() + delta < 0) throw new InsufficientStockException(p.getName(), p.getQuantity(), -delta);
        undo.execute(new Command() {
            @Override public void execute() {
                sameProduct(p);
                if (p.getQuantity() + delta < 0) throw new InsufficientStockException(p.getName(), p.getQuantity(), -delta);
                p.setQuantity(p.getQuantity() + delta);
                log.log(type, p.getId(), delta, note);
            }
            @Override public void undo() {
                sameProduct(p);
                if (p.getQuantity() - delta < 0) throw new IllegalStateException("Cannot undo: stock would become negative.");
                p.setQuantity(p.getQuantity() - delta);
                log.log(Transaction.Type.UNDO, p.getId(), -delta, "Undo: " + description());
            }
            @Override public String description() { return (delta > 0 ? "Add " : "Remove ") + Math.abs(delta) + " x " + p.getName(); }
        });
    }

    private void sameProduct(Product p) {
        if (store.products.get(p.getId()) != p) throw new IllegalStateException("Product no longer exists.");
    }

    public String undo() { auth.require(Permission.STOCK_MANAGE); return undo.undo(); }
    public String redo() { auth.require(Permission.STOCK_MANAGE); return undo.redo(); }
    public UndoManager undoManager() { return undo; }

    // ================= QUERIES =================
    public List<Product> all() {
        auth.require(Permission.VIEW);
        return new ArrayList<>(store.products.values());
    }

    public List<Product> search(String keyword) {
        auth.require(Permission.VIEW);
        String k = keyword.trim().toLowerCase();
        return store.products.values().stream()
                .filter(p -> p.getId().toLowerCase().contains(k) || p.getName().toLowerCase().contains(k)
                        || p.getCategory().toLowerCase().contains(k))
                .collect(Collectors.toList());
    }

    public List<Product> byCategory(String category) {
        auth.require(Permission.VIEW);
        return store.products.values().stream()
                .filter(p -> p.getCategory().equalsIgnoreCase(category.trim())).collect(Collectors.toList());
    }

    public List<Product> byPriceRange(double min, double max) {
        auth.require(Permission.VIEW);
        return store.products.values().stream()
                .filter(p -> p.getPrice() >= min && p.getPrice() <= max).collect(Collectors.toList());
    }

    public List<Product> sorted(Comparator<Product> cmp) {
        List<Product> list = all();
        list.sort(cmp);
        return list;
    }

    /** Category -> products. TreeMap keeps categories alphabetical. */
    public Map<String, List<Product>> groupByCategory() {
        auth.require(Permission.VIEW);
        Map<String, List<Product>> grouped = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Product p : store.products.values())
            grouped.computeIfAbsent(p.getCategory(), c -> new ArrayList<>()).add(p);
        return grouped;
    }

    public Set<String> categories() {
        auth.require(Permission.VIEW);
        Set<String> cats = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Product p : store.products.values()) cats.add(p.getCategory());
        return cats;
    }

    // ================= ALERTS =================
    /** PriorityQueue: the most critical (lowest quantity, then lowest ratio) product comes out first. */
    public PriorityQueue<Product> lowStockQueue() {
        auth.require(Permission.VIEW);
        PriorityQueue<Product> pq = new PriorityQueue<>(
                Comparator.comparingInt(Product::getQuantity).thenComparing(Product::getName));
        for (Product p : store.products.values()) if (p.isLowStock()) pq.offer(p);
        return pq;
    }

    public List<Product> outOfStock() {
        auth.require(Permission.VIEW);
        return store.products.values().stream().filter(p -> p.getQuantity() == 0).collect(Collectors.toList());
    }

    /** TreeMap keyed by expiry date: gives sorted order plus fast range queries. */
    public NavigableMap<LocalDate, List<Product>> expiryIndex() {
        NavigableMap<LocalDate, List<Product>> index = new TreeMap<>();
        for (Product p : store.products.values())
            if (p.getExpiryDate() != null) index.computeIfAbsent(p.getExpiryDate(), d -> new ArrayList<>()).add(p);
        return index;
    }

    public List<Product> expired(LocalDate today) {
        auth.require(Permission.VIEW);
        return flatten(expiryIndex().headMap(today, false));
    }

    public List<Product> expiringWithin(LocalDate today, int days) {
        auth.require(Permission.VIEW);
        return flatten(expiryIndex().subMap(today, true, today.plusDays(days), true));
    }

    private List<Product> flatten(Map<LocalDate, List<Product>> m) {
        List<Product> out = new ArrayList<>();
        m.values().forEach(out::addAll);
        return out;
    }
}
