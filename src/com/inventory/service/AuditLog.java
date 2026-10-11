package com.inventory.service;

import com.inventory.model.Permission;
import com.inventory.model.Transaction;

import java.util.*;
import java.util.stream.Collectors;

/** Append-only audit trail of every important action. */
public class AuditLog {
    private final DataStore store;
    private final AuthService auth;

    public AuditLog(DataStore store, AuthService auth) { this.store = store; this.auth = auth; }

    public void log(Transaction.Type type, String productId, int qtyChange, String note) {
        store.audit.add(new Transaction(store.nextTransaction++, auth.currentUsername(), type, productId, qtyChange, note));
    }

    /** Newest first. */
    public List<Transaction> recent(int n) {
        auth.require(Permission.AUDIT_VIEW);
        List<Transaction> copy = new ArrayList<>(store.audit);
        Collections.reverse(copy);
        return copy.subList(0, Math.min(n, copy.size()));
    }

    public List<Transaction> byType(Transaction.Type type) {
        auth.require(Permission.AUDIT_VIEW);
        return store.audit.stream().filter(t -> t.getType() == type).collect(Collectors.toList());
    }

    public List<Transaction> byProduct(String productId) {
        auth.require(Permission.AUDIT_VIEW);
        String id = productId.trim().toUpperCase();
        return store.audit.stream().filter(t -> t.getProductId().equals(id)).collect(Collectors.toList());
    }

    /** EnumMap: how many times each action type occurred. */
    public Map<Transaction.Type, Integer> countByType() {
        auth.require(Permission.AUDIT_VIEW);
        Map<Transaction.Type, Integer> counts = new EnumMap<>(Transaction.Type.class);
        for (Transaction t : store.audit) counts.merge(t.getType(), 1, Integer::sum);
        return counts;
    }
}
