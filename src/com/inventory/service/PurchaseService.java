package com.inventory.service;

import com.inventory.exception.*;
import com.inventory.model.*;

import java.util.*;
import java.util.stream.Collectors;

/** Purchase orders. Pending orders live in a FIFO Queue (LinkedList). */
public class PurchaseService {
    private final DataStore store;
    private final AuthService auth;
    private final AuditLog log;

    public PurchaseService(DataStore store, AuthService auth, AuditLog log) {
        this.store = store; this.auth = auth; this.log = log;
    }

    public PurchaseOrder create(String supplierId, Map<String, Integer> lines) {
        auth.require(Permission.PURCHASE);
        String sid = supplierId.trim().toUpperCase();
        if (!store.suppliers.containsKey(sid)) throw new NotFoundException("Supplier", supplierId);
        if (lines.isEmpty()) throw new IllegalArgumentException("A purchase order needs at least one line.");
        PurchaseOrder po = new PurchaseOrder("PO-" + store.nextPurchaseOrder, sid);
        for (Map.Entry<String, Integer> e : lines.entrySet()) {
            Product p = store.products.get(e.getKey());
            if (p == null) throw new NotFoundException("Product", e.getKey());
            if (e.getValue() <= 0) throw new IllegalArgumentException("Quantity must be positive for " + p.getName());
            po.addLine(new POLine(p, e.getValue()));
        }
        store.nextPurchaseOrder++;
        store.purchaseOrders.put(po.getPoNo(), po);
        store.pendingOrders.offer(po);
        log.log(Transaction.Type.PO_CREATED, null, 0, po.getPoNo() + " to " + sid + " total " + String.format("%.2f", po.total()));
        return po;
    }

    /**
     * Auto-reorder: groups low-stock products by supplier (Collectors.groupingBy) and raises one PO per supplier.
     * Products already on a pending order are skipped.
     */
    public List<PurchaseOrder> autoReorder() {
        auth.require(Permission.PURCHASE);
        Set<String> alreadyOrdered = new HashSet<>();
        for (PurchaseOrder po : store.pendingOrders) for (POLine l : po.getLines()) alreadyOrdered.add(l.getProductId());

        Map<String, List<Product>> bySupplier = store.products.values().stream()
                .filter(Product::isLowStock)
                .filter(p -> p.getSupplierId() != null && store.suppliers.containsKey(p.getSupplierId()))
                .filter(p -> !alreadyOrdered.contains(p.getId()))
                .collect(Collectors.groupingBy(Product::getSupplierId, LinkedHashMap::new, Collectors.toList()));

        List<PurchaseOrder> created = new ArrayList<>();
        for (Map.Entry<String, List<Product>> e : bySupplier.entrySet()) {
            Map<String, Integer> lines = new LinkedHashMap<>();
            for (Product p : e.getValue()) lines.put(p.getId(), p.suggestedReorderQty());
            created.add(create(e.getKey(), lines));
        }
        return created;
    }

    public PurchaseOrder find(String poNo) {
        auth.require(Permission.VIEW);
        String key = poNo.trim().toUpperCase();
        if (!key.startsWith("PO-")) key = "PO-" + key;
        PurchaseOrder po = store.purchaseOrders.get(key);
        if (po == null) throw new NotFoundException("Purchase order", poNo);
        return po;
    }

    public PurchaseOrder receive(String poNo) {
        auth.require(Permission.PURCHASE);
        return doReceive(find(poNo));
    }

    /** FIFO: receive the oldest pending order. */
    public PurchaseOrder receiveNext() {
        auth.require(Permission.PURCHASE);
        PurchaseOrder head = store.pendingOrders.peek();
        if (head == null) throw new IllegalStateException("No pending purchase orders.");
        return doReceive(head);
    }

    private PurchaseOrder doReceive(PurchaseOrder po) {
        if (po.getStatus() != PurchaseOrder.Status.PENDING) throw new IllegalStateException(po.getPoNo() + " is already " + po.getStatus() + ".");
        for (POLine l : po.getLines()) {
            Product p = store.products.get(l.getProductId());
            if (p != null) {
                p.setQuantity(p.getQuantity() + l.getQty());
                log.log(Transaction.Type.PO_RECEIVED, p.getId(), l.getQty(), po.getPoNo());
            }
        }
        po.markReceived();
        store.pendingOrders.remove(po);
        return po;
    }

    public PurchaseOrder cancel(String poNo) {
        auth.require(Permission.PURCHASE);
        PurchaseOrder po = find(poNo);
        if (po.getStatus() != PurchaseOrder.Status.PENDING) throw new IllegalStateException(po.getPoNo() + " is already " + po.getStatus() + ".");
        po.markCancelled();
        store.pendingOrders.remove(po);
        log.log(Transaction.Type.PO_CANCELLED, null, 0, po.getPoNo());
        return po;
    }

    public List<PurchaseOrder> all() {
        auth.require(Permission.VIEW);
        return new ArrayList<>(store.purchaseOrders.values());
    }

    public List<PurchaseOrder> pending() {
        auth.require(Permission.VIEW);
        return new ArrayList<>(store.pendingOrders);     // preserves FIFO order
    }
}
