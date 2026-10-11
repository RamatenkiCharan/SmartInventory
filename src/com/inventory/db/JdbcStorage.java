package com.inventory.db;

import com.inventory.model.*;
import com.inventory.service.DataStore;
import com.inventory.util.Storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * JDBC persistence. The services keep working on the in-memory collections in {@link DataStore};
 * this class reads them from the database at start-up and writes every change back to it
 * in a single transaction (rolled back completely if anything fails).
 */
public class JdbcStorage implements Storage {
    private Connection con;

    public JdbcStorage() {
        try {
            con = Database.connect();
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        } catch (SQLException e) {
            throw new UncheckedIOException("Cannot open database " + Database.url(), new IOException(e));
        }
    }

    public void close() { try { if (con != null && !con.isClosed()) con.close(); } catch (SQLException ignored) {} }

    /** True when the database has never been filled. */
    public boolean isEmpty() {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM meta")) {
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) { throw wrap(e); }
    }

    /** Empties every table (used by --reset). */
    public synchronized void clearAll() {
        try (Statement st = con.createStatement()) {
            for (String t : new String[]{"invoice_items", "invoices", "po_lines", "purchase_orders", "audit_log",
                                         "products", "suppliers", "users", "meta"})
                st.executeUpdate("DELETE FROM " + t);
        } catch (SQLException e) { throw wrap(e); }
    }

    // ================================================================== LOAD
    @Override public synchronized DataStore load() {
        if (isEmpty()) return null;
        try {
            DataStore s = new DataStore();
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT key, value FROM meta")) {
                while (rs.next()) {
                    String v = rs.getString(2);
                    switch (rs.getString(1)) {
                        case "storeName" -> s.storeName = v;
                        case "defaultTaxPercent" -> s.defaultTaxPercent = Double.parseDouble(v);
                        case "nextInvoice" -> s.nextInvoice = Integer.parseInt(v);
                        case "nextPurchaseOrder" -> s.nextPurchaseOrder = Integer.parseInt(v);
                        case "nextTransaction" -> s.nextTransaction = Long.parseLong(v);
                        default -> { }
                    }
                }
            }
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM suppliers ORDER BY seq")) {
                while (rs.next()) s.suppliers.put(rs.getString("id"), new Supplier(rs.getString("id"), rs.getString("name"),
                        rs.getString("phone"), rs.getString("email"), rs.getString("city")));
            }
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM products ORDER BY seq")) {
                while (rs.next()) {
                    String exp = rs.getString("expiry_date");
                    s.products.put(rs.getString("id"), new Product(rs.getString("id"), rs.getString("name"),
                            rs.getString("category"), rs.getString("supplier_id"), rs.getDouble("cost_price"),
                            rs.getDouble("price"), rs.getInt("quantity"), rs.getInt("reorder_level"),
                            exp == null ? null : LocalDate.parse(exp)));
                }
            }
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM users ORDER BY seq")) {
                while (rs.next()) s.users.put(rs.getString("username"), User.restore(rs.getString("username"),
                        rs.getString("full_name"), rs.getString("password_hash"),
                        Role.valueOf(rs.getString("role")), rs.getInt("active") == 1));
            }
            Map<String, Invoice> inv = new LinkedHashMap<>();
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM invoices ORDER BY seq")) {
                while (rs.next()) inv.put(rs.getString("invoice_no"), new Invoice(rs.getString("invoice_no"),
                        rs.getString("customer"), rs.getString("sold_by"), LocalDateTime.parse(rs.getString("date_time")),
                        rs.getDouble("discount_percent"), rs.getDouble("tax_percent"), Invoice.Status.valueOf(rs.getString("status"))));
            }
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM invoice_items ORDER BY invoice_no, line_no")) {
                while (rs.next()) inv.get(rs.getString("invoice_no")).addItem(new InvoiceItem(rs.getString("product_id"),
                        rs.getString("name"), rs.getDouble("unit_price"), rs.getDouble("cost_price"), rs.getInt("qty")));
            }
            s.invoices.addAll(inv.values());
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM purchase_orders ORDER BY seq")) {
                while (rs.next()) {
                    String rec = rs.getString("received_on");
                    s.purchaseOrders.put(rs.getString("po_no"), new PurchaseOrder(rs.getString("po_no"), rs.getString("supplier_id"),
                            LocalDate.parse(rs.getString("created_on")), rec == null ? null : LocalDate.parse(rec),
                            PurchaseOrder.Status.valueOf(rs.getString("status"))));
                }
            }
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM po_lines ORDER BY po_no, line_no")) {
                while (rs.next()) s.purchaseOrders.get(rs.getString("po_no")).addLine(new POLine(rs.getString("product_id"),
                        rs.getString("product_name"), rs.getInt("qty"), rs.getDouble("unit_cost")));
            }
            for (PurchaseOrder po : s.purchaseOrders.values())          // FIFO queue = pending orders, oldest first
                if (po.getStatus() == PurchaseOrder.Status.PENDING) s.pendingOrders.offer(po);
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM audit_log ORDER BY id")) {
                while (rs.next()) s.audit.add(new Transaction(rs.getLong("id"), LocalDateTime.parse(rs.getString("time")),
                        rs.getString("username"), Transaction.Type.valueOf(rs.getString("type")), rs.getString("product_id"),
                        rs.getInt("qty_change"), rs.getString("note")));
            }
            return s;
        } catch (SQLException | RuntimeException e) {
            System.out.println("Warning: could not read the database (" + e.getMessage() + "). Starting fresh.");
            return null;
        }
    }

    // ================================================================== SAVE
    @Override public synchronized void save(DataStore s) {
        try {
            con.setAutoCommit(false);
            try {
                saveMeta(s); deleteMissing("products", "id", s.products.keySet()); saveSuppliers(s); saveProducts(s); saveUsers(s);
                saveInvoices(s); savePurchaseOrders(s); saveAudit(s);
                con.commit();
            } catch (SQLException | RuntimeException e) { con.rollback(); throw e; }
            finally { con.setAutoCommit(true); }
        } catch (SQLException e) { throw wrap(e); }
    }

    private void saveMeta(DataStore s) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)")) {
            put(ps, "storeName", s.storeName); put(ps, "defaultTaxPercent", String.valueOf(s.defaultTaxPercent));
            put(ps, "nextInvoice", String.valueOf(s.nextInvoice)); put(ps, "nextPurchaseOrder", String.valueOf(s.nextPurchaseOrder));
            put(ps, "nextTransaction", String.valueOf(s.nextTransaction));
        }
    }
    private static void put(PreparedStatement ps, String k, String v) throws SQLException { ps.setString(1, k); ps.setString(2, v); ps.executeUpdate(); }

    private void saveSuppliers(DataStore s) throws SQLException {
        deleteMissing("suppliers", "id", s.suppliers.keySet());
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO suppliers(id, seq, name, phone, email, city) VALUES (?,?,?,?,?,?) " +
                "ON CONFLICT(id) DO UPDATE SET seq=excluded.seq, name=excluded.name, phone=excluded.phone, email=excluded.email, city=excluded.city")) {
            int n = 0;
            for (Supplier x : s.suppliers.values()) {
                ps.setString(1, x.getId()); ps.setInt(2, n++); ps.setString(3, x.getName());
                ps.setString(4, x.getPhone()); ps.setString(5, x.getEmail()); ps.setString(6, x.getCity());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void saveProducts(DataStore s) throws SQLException {
        deleteMissing("products", "id", s.products.keySet());
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO products(id, seq, name, category, supplier_id, cost_price, price, quantity, reorder_level, expiry_date) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET seq=excluded.seq, name=excluded.name, " +
                "category=excluded.category, supplier_id=excluded.supplier_id, cost_price=excluded.cost_price, price=excluded.price, " +
                "quantity=excluded.quantity, reorder_level=excluded.reorder_level, expiry_date=excluded.expiry_date")) {
            int n = 0;
            for (Product p : s.products.values()) {
                ps.setString(1, p.getId()); ps.setInt(2, n++); ps.setString(3, p.getName()); ps.setString(4, p.getCategory());
                ps.setString(5, p.getSupplierId()); ps.setDouble(6, p.getCostPrice()); ps.setDouble(7, p.getPrice());
                ps.setInt(8, p.getQuantity()); ps.setInt(9, p.getReorderLevel());
                ps.setString(10, p.getExpiryDate() == null ? null : p.getExpiryDate().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void saveUsers(DataStore s) throws SQLException {
        deleteMissing("users", "username", s.users.keySet());
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO users(username, seq, full_name, password_hash, role, active) VALUES (?,?,?,?,?,?) " +
                "ON CONFLICT(username) DO UPDATE SET seq=excluded.seq, full_name=excluded.full_name, " +
                "password_hash=excluded.password_hash, role=excluded.role, active=excluded.active")) {
            int n = 0;
            for (User u : s.users.values()) {
                ps.setString(1, u.getUsername()); ps.setInt(2, n++); ps.setString(3, u.getFullName());
                ps.setString(4, u.getPasswordHash()); ps.setString(5, u.getRole().name()); ps.setInt(6, u.isActive() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Invoices only ever change by being voided, so existing rows just get their status updated. */
    private void saveInvoices(DataStore s) throws SQLException {
        Set<String> known = keys("invoices", "invoice_no");
        try (PreparedStatement ins = con.prepareStatement(
                 "INSERT INTO invoices(invoice_no, seq, customer, sold_by, date_time, discount_percent, tax_percent, status) VALUES (?,?,?,?,?,?,?,?)");
             PreparedStatement item = con.prepareStatement(
                 "INSERT INTO invoice_items(invoice_no, line_no, product_id, name, unit_price, cost_price, qty) VALUES (?,?,?,?,?,?,?)");
             PreparedStatement upd = con.prepareStatement("UPDATE invoices SET status=? WHERE invoice_no=?")) {
            int n = 0;
            for (Invoice i : s.invoices) {
                n++;
                if (known.contains(i.getInvoiceNo())) {
                    upd.setString(1, i.getStatus().name()); upd.setString(2, i.getInvoiceNo()); upd.addBatch();
                    continue;
                }
                ins.setString(1, i.getInvoiceNo()); ins.setInt(2, n); ins.setString(3, i.getCustomer());
                ins.setString(4, i.getSoldBy()); ins.setString(5, i.getDateTime().toString());
                ins.setDouble(6, i.getDiscountPercent()); ins.setDouble(7, i.getTaxPercent()); ins.setString(8, i.getStatus().name());
                ins.addBatch();
            }
            ins.executeBatch();
            n = 0;
            for (Invoice i : s.invoices) {
                if (known.contains(i.getInvoiceNo())) continue;
                int line = 0;
                for (InvoiceItem it : i.getItems()) {
                    item.setString(1, i.getInvoiceNo()); item.setInt(2, ++line); item.setString(3, it.getProductId());
                    item.setString(4, it.getName()); item.setDouble(5, it.getUnitPrice()); item.setDouble(6, it.getCostPrice());
                    item.setInt(7, it.getQty()); item.addBatch();
                }
            }
            item.executeBatch(); upd.executeBatch();
        }
    }

    /** Orders change status (PENDING -> RECEIVED / CANCELLED); lines never change. */
    private void savePurchaseOrders(DataStore s) throws SQLException {
        Set<String> known = keys("purchase_orders", "po_no");
        try (PreparedStatement ins = con.prepareStatement(
                 "INSERT INTO purchase_orders(po_no, seq, supplier_id, created_on, received_on, status) VALUES (?,?,?,?,?,?)");
             PreparedStatement line = con.prepareStatement(
                 "INSERT INTO po_lines(po_no, line_no, product_id, product_name, qty, unit_cost) VALUES (?,?,?,?,?,?)");
             PreparedStatement upd = con.prepareStatement("UPDATE purchase_orders SET status=?, received_on=? WHERE po_no=?")) {
            int n = 0;
            for (PurchaseOrder po : s.purchaseOrders.values()) {
                n++;
                if (known.contains(po.getPoNo())) {
                    upd.setString(1, po.getStatus().name());
                    upd.setString(2, po.getReceivedOn() == null ? null : po.getReceivedOn().toString());
                    upd.setString(3, po.getPoNo()); upd.addBatch();
                    continue;
                }
                ins.setString(1, po.getPoNo()); ins.setInt(2, n); ins.setString(3, po.getSupplierId());
                ins.setString(4, po.getCreatedOn().toString());
                ins.setString(5, po.getReceivedOn() == null ? null : po.getReceivedOn().toString());
                ins.setString(6, po.getStatus().name()); ins.addBatch();
            }
            ins.executeBatch();
            for (PurchaseOrder po : s.purchaseOrders.values()) {
                if (known.contains(po.getPoNo())) continue;
                int ln = 0;
                for (POLine l : po.getLines()) {
                    line.setString(1, po.getPoNo()); line.setInt(2, ++ln); line.setString(3, l.getProductId());
                    line.setString(4, l.getProductName()); line.setInt(5, l.getQty()); line.setDouble(6, l.getUnitCost());
                    line.addBatch();
                }
            }
            line.executeBatch(); upd.executeBatch();
        }
    }

    /** The audit trail is append-only: only entries newer than what is stored are inserted. */
    private void saveAudit(DataStore s) throws SQLException {
        long max = 0;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM audit_log")) {
            if (rs.next()) max = rs.getLong(1);
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO audit_log(id, time, username, type, product_id, qty_change, note) VALUES (?,?,?,?,?,?,?)")) {
            for (Transaction t : s.audit) {
                if (t.getId() <= max) continue;
                ps.setLong(1, t.getId()); ps.setString(2, t.getTime().toString()); ps.setString(3, t.getUser());
                ps.setString(4, t.getType().name()); ps.setString(5, t.getProductId()); ps.setInt(6, t.getQtyChange());
                ps.setString(7, t.getNote()); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ------------------------------------------------------------------ helpers
    private Set<String> keys(String table, String col) throws SQLException {
        Set<String> out = new HashSet<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT " + col + " FROM " + table)) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    private void deleteMissing(String table, String col, Set<String> keep) throws SQLException {
        Set<String> stale = keys(table, col);
        stale.removeAll(keep);
        if (stale.isEmpty()) return;
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + table + " WHERE " + col + " = ?")) {
            for (String k : stale) { ps.setString(1, k); ps.addBatch(); }
            ps.executeBatch();
        }
    }

    // ------------------------------------------------------------------ backup
    @Override public synchronized Path backup() {
        String url = Database.url();
        if (!url.startsWith("jdbc:sqlite:")) throw new UnsupportedOperationException("Backup is only implemented for SQLite.");
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path dir = Paths.get(url.substring("jdbc:sqlite:".length())).toAbsolutePath().getParent().resolve("backups");
            Files.createDirectories(dir);
            Path target = dir.resolve("inventory-" + stamp + ".db");
            try (Statement st = con.createStatement()) { st.execute("VACUUM INTO '" + target.toString().replace("'", "''") + "'"); }
            return target;
        } catch (IOException | SQLException e) { throw new UncheckedIOException("Backup failed", new IOException(e)); }
    }

    private static UncheckedIOException wrap(SQLException e) { return new UncheckedIOException("Database error: " + e.getMessage(), new IOException(e)); }
}
