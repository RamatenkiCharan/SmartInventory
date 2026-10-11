package com.inventory.service;

import com.inventory.exception.*;
import com.inventory.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class SupplierService {
    private final DataStore store;
    private final AuthService auth;
    private final AuditLog log;

    public SupplierService(DataStore store, AuthService auth, AuditLog log) {
        this.store = store; this.auth = auth; this.log = log;
    }

    public Supplier add(Supplier s) {
        auth.require(Permission.SUPPLIER_MANAGE);
        if (store.suppliers.containsKey(s.getId())) throw new DuplicateException("Supplier ID already exists: " + s.getId());
        store.suppliers.put(s.getId(), s);
        log.log(Transaction.Type.SUPPLIER, null, 0, "Added supplier " + s.getId() + " - " + s.getName());
        return s;
    }

    public Supplier get(String id) {
        auth.require(Permission.VIEW);
        Supplier s = store.suppliers.get(id.trim().toUpperCase());
        if (s == null) throw new NotFoundException("Supplier", id);
        return s;
    }

    public void edit(String id, String field, String value) {
        auth.require(Permission.SUPPLIER_MANAGE);
        Supplier s = get(id);
        switch (field) {
            case "name"  -> s.setName(value);
            case "phone" -> s.setPhone(value);
            case "email" -> s.setEmail(value);
            case "city"  -> s.setCity(value);
            default -> throw new IllegalArgumentException("Unknown field: " + field);
        }
        log.log(Transaction.Type.SUPPLIER, null, 0, "Edited supplier " + s.getId() + " (" + field + ")");
    }

    public void delete(String id) {
        auth.require(Permission.SUPPLIER_MANAGE);
        Supplier s = get(id);
        boolean inUse = store.products.values().stream().anyMatch(p -> s.getId().equals(p.getSupplierId()));
        if (inUse) throw new IllegalStateException("Supplier still has products linked to it.");
        boolean pending = store.pendingOrders.stream().anyMatch(po -> po.getSupplierId().equals(s.getId()));
        if (pending) throw new IllegalStateException("Supplier has pending purchase orders.");
        store.suppliers.remove(s.getId());
        log.log(Transaction.Type.SUPPLIER, null, 0, "Deleted supplier " + s.getId());
    }

    public List<Supplier> all() {
        auth.require(Permission.VIEW);
        return new ArrayList<>(store.suppliers.values());
    }

    public List<Product> productsOf(String id) {
        Supplier s = get(id);
        return store.products.values().stream().filter(p -> s.getId().equals(p.getSupplierId())).collect(Collectors.toList());
    }

    public String nameOf(String id) {
        if (id == null) return "-";
        Supplier s = store.suppliers.get(id);
        return s == null ? id : s.getName();
    }
}
