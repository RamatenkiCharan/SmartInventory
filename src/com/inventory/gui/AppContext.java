package com.inventory.gui;

import com.inventory.model.Permission;
import com.inventory.model.User;
import com.inventory.service.*;
import com.inventory.util.Storage;

import java.io.UncheckedIOException;

/** Wires the existing service layer together and tells the GUI when data changed. */
public final class AppContext {
    public final DataStore store;
    public final Storage storage;
    public final AuthService auth;
    public final AuditLog audit;
    public final InventoryService inventory;
    public final SupplierService suppliers;
    public final SalesService sales;
    public final PurchaseService purchases;
    public final ReportService reports;
    private Runnable onChange = () -> {};

    public AppContext(DataStore store, Storage storage) {
        this.store = store;
        this.storage = storage;
        this.auth = new AuthService(store);
        this.audit = new AuditLog(store, auth);
        UndoManager undo = new UndoManager();
        this.inventory = new InventoryService(store, auth, audit, undo);
        this.suppliers = new SupplierService(store, auth, audit);
        this.sales = new SalesService(store, auth, audit);
        this.purchases = new PurchaseService(store, auth, audit);
        this.reports = new ReportService(store, auth);
    }

    public void setOnChange(Runnable r) { this.onChange = r; }

    public boolean can(Permission p) {
        User u = auth.current();
        return u != null && u.getRole().can(p);
    }

    public void save() {
        try { storage.save(store); }
        catch (UncheckedIOException e) { Toast.error("Could not save: " + e.getMessage()); }
    }

    /** Call after any successful change: saves to disk and refreshes every screen. */
    public void changed() {
        save();
        onChange.run();
    }
}
