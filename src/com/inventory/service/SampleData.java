package com.inventory.service;

import com.inventory.model.*;

import java.time.LocalDate;

/** Creates the first-run data: users, suppliers and demo products. */
public final class SampleData {
    private SampleData() {}

    public static DataStore create() {
        DataStore s = new DataStore();
        LocalDate t = LocalDate.now();

        s.users.put("admin",   new User("admin",   "System Administrator", "admin123",   Role.ADMIN));
        s.users.put("manager", new User("manager", "Store Manager",        "manager123", Role.MANAGER));
        s.users.put("staff",   new User("staff",   "Counter Staff",        "staff123",   Role.STAFF));

        add(s, new Supplier("S01", "TechWorld Distributors", "9000000001", "sales@techworld.example", "Hyderabad"));
        add(s, new Supplier("S02", "PaperPlus Traders",      "9000000002", "orders@paperplus.example", "Chennai"));
        add(s, new Supplier("S03", "FreshFarm Foods",        "9000000003", "supply@freshfarm.example", "Bengaluru"));

        add(s, new Product("P101", "Laptop 15\"",       "Electronics", "S01", 48000, 55000, 12,  5,  null));
        add(s, new Product("P102", "Wireless Mouse",    "Electronics", "S01",   450,   799, 40, 10,  null));
        add(s, new Product("P103", "USB-C Cable",       "Electronics", "S01",   120,   299,  6, 15,  null));
        add(s, new Product("P201", "Notebook A4",       "Stationery",  "S02",    35,    60,  8, 20,  null));
        add(s, new Product("P202", "Ball Pen (Box)",    "Stationery",  "S02",    70,   120, 25, 10,  null));
        add(s, new Product("P203", "Stapler",           "Stationery",  "S02",    90,   180,  0,  5,  null));
        add(s, new Product("P301", "Office Chair",      "Furniture",   null,   4200,  6500,  3,  5,  null));
        add(s, new Product("P401", "Milk 1L",           "Grocery",     "S03",    48,    62, 30, 20,  t.plusDays(4)));
        add(s, new Product("P402", "Cheese Slices",     "Grocery",     "S03",    90,   130, 18, 10,  t.plusDays(20)));
        add(s, new Product("P403", "Yogurt Cup",        "Grocery",     "S03",    18,    30, 14, 15,  t.minusDays(2)));   // already expired
        add(s, new Product("P404", "Packaged Rice 5kg", "Grocery",     "S03",   260,   349, 50, 15,  t.plusDays(300)));
        return s;
    }

    private static void add(DataStore s, Supplier x) { s.suppliers.put(x.getId(), x); }
    private static void add(DataStore s, Product p)  { s.products.put(p.getId(), p); }
}
