package com.inventory.db;

import java.sql.*;

/** Opens the JDBC connection and creates the schema. Default: an SQLite file at data/inventory.db. */
public final class Database {
    /** Override with  -Ddb.url=jdbc:sqlite:C:/path/inventory.db  */
    public static final String DEFAULT_URL = "jdbc:sqlite:data/inventory.db";

    private Database() {}

    public static String url() { return System.getProperty("db.url", DEFAULT_URL); }

    public static Connection connect() throws SQLException {
        String url = url();
        if (url.startsWith("jdbc:sqlite:")) {
            String file = url.substring("jdbc:sqlite:".length());
            java.nio.file.Path parent = java.nio.file.Paths.get(file).toAbsolutePath().getParent();
            try { if (parent != null) java.nio.file.Files.createDirectories(parent); }
            catch (java.io.IOException e) { throw new SQLException("Cannot create folder " + parent, e); }
        }
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) { st.execute("PRAGMA foreign_keys = ON"); }
        createSchema(c);
        return c;
    }

    static void createSchema(Connection c) throws SQLException {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS meta (" +
            "  key TEXT PRIMARY KEY, value TEXT NOT NULL)",

            "CREATE TABLE IF NOT EXISTS suppliers (" +
            "  id TEXT PRIMARY KEY, seq INTEGER NOT NULL," +
            "  name TEXT NOT NULL, phone TEXT, email TEXT, city TEXT)",

            "CREATE TABLE IF NOT EXISTS products (" +
            "  id TEXT PRIMARY KEY, seq INTEGER NOT NULL," +
            "  name TEXT NOT NULL, category TEXT NOT NULL," +
            "  supplier_id TEXT REFERENCES suppliers(id)," +
            "  cost_price REAL NOT NULL CHECK (cost_price >= 0)," +
            "  price REAL NOT NULL CHECK (price >= 0)," +
            "  quantity INTEGER NOT NULL CHECK (quantity >= 0)," +
            "  reorder_level INTEGER NOT NULL CHECK (reorder_level >= 0)," +
            "  expiry_date TEXT)",

            "CREATE TABLE IF NOT EXISTS users (" +
            "  username TEXT PRIMARY KEY, seq INTEGER NOT NULL," +
            "  full_name TEXT NOT NULL, password_hash TEXT NOT NULL," +
            "  role TEXT NOT NULL CHECK (role IN ('ADMIN','MANAGER','STAFF'))," +
            "  active INTEGER NOT NULL)",

            "CREATE TABLE IF NOT EXISTS invoices (" +
            "  invoice_no TEXT PRIMARY KEY, seq INTEGER NOT NULL," +
            "  customer TEXT, sold_by TEXT NOT NULL, date_time TEXT NOT NULL," +
            "  discount_percent REAL NOT NULL, tax_percent REAL NOT NULL," +
            "  status TEXT NOT NULL CHECK (status IN ('PAID','VOIDED')))",

            "CREATE TABLE IF NOT EXISTS invoice_items (" +
            "  invoice_no TEXT NOT NULL REFERENCES invoices(invoice_no) ON DELETE CASCADE," +
            "  line_no INTEGER NOT NULL, product_id TEXT NOT NULL, name TEXT NOT NULL," +
            "  unit_price REAL NOT NULL, cost_price REAL NOT NULL, qty INTEGER NOT NULL," +
            "  PRIMARY KEY (invoice_no, line_no))",

            "CREATE TABLE IF NOT EXISTS purchase_orders (" +
            "  po_no TEXT PRIMARY KEY, seq INTEGER NOT NULL," +
            "  supplier_id TEXT NOT NULL, created_on TEXT NOT NULL, received_on TEXT," +
            "  status TEXT NOT NULL CHECK (status IN ('PENDING','RECEIVED','CANCELLED')))",

            "CREATE TABLE IF NOT EXISTS po_lines (" +
            "  po_no TEXT NOT NULL REFERENCES purchase_orders(po_no) ON DELETE CASCADE," +
            "  line_no INTEGER NOT NULL, product_id TEXT NOT NULL, product_name TEXT NOT NULL," +
            "  qty INTEGER NOT NULL, unit_cost REAL NOT NULL," +
            "  PRIMARY KEY (po_no, line_no))",

            "CREATE TABLE IF NOT EXISTS audit_log (" +
            "  id INTEGER PRIMARY KEY, time TEXT NOT NULL, username TEXT NOT NULL," +
            "  type TEXT NOT NULL, product_id TEXT NOT NULL, qty_change INTEGER NOT NULL, note TEXT)",

            "CREATE INDEX IF NOT EXISTS idx_products_category ON products(category)",
            "CREATE INDEX IF NOT EXISTS idx_items_product ON invoice_items(product_id)",
            "CREATE INDEX IF NOT EXISTS idx_audit_product ON audit_log(product_id)"
        };
        try (Statement st = c.createStatement()) { for (String s : ddl) st.execute(s); }
    }
}
