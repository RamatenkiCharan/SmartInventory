package com.inventory.ui;

import com.inventory.exception.InventoryException;
import com.inventory.model.*;
import com.inventory.service.*;
import com.inventory.util.*;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.*;

/** Menu-driven console front end. All rules live in the services; this class only talks to the user. */
public class ConsoleApp {
    private final DataStore store;
    private final Storage storage;
    private final AuthService auth;
    private final AuditLog audit;
    private final InventoryService inventory;
    private final SupplierService suppliers;
    private final SalesService sales;
    private final PurchaseService purchases;
    private final ReportService reports;
    private final Console io = new Console();

    public ConsoleApp(DataStore store, Storage storage) {
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

    // =====================================================================
    //  Start-up / login
    // =====================================================================
    public void run() {
        System.out.println("""
                ============================================================
                   SMART INVENTORY MANAGEMENT SYSTEM  -  Java Collections
                ============================================================""");
        try {
            do {
                if (!login()) return;
            } while (mainMenu());
            System.out.println("Goodbye!");
        } catch (InputClosedException e) {
            System.out.println("\nInput closed. Exiting.");
        } finally {
            save();
        }
    }

    private boolean login() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            String u = io.required("\nUsername");
            String p = io.required("Password");
            try {
                User user = auth.login(u, p);
                audit.log(Transaction.Type.USER, null, 0, "Login");
                System.out.println("Welcome, " + user.getFullName() + " (" + user.getRole() + ")");
                showStartupAlerts();
                return true;
            } catch (InventoryException e) {
                System.out.println("  " + e.getMessage() + " (attempt " + attempt + "/3)");
            }
        }
        System.out.println("Too many failed attempts. Exiting.");
        return false;
    }

    private void showStartupAlerts() {
        try {
            int low = inventory.lowStockQueue().size();
            int exp = inventory.expired(LocalDate.now()).size();
            int soon = inventory.expiringWithin(LocalDate.now(), 7).size();
            if (low + exp + soon > 0)
                System.out.printf("ALERTS: %d low-stock | %d expired | %d expiring within 7 days  (see menu 6)%n", low, exp, soon);
        } catch (InventoryException ignored) { }
    }

    // =====================================================================
    //  Main menu
    // =====================================================================
    /** @return true if the user chose to switch accounts, false to exit. */
    private boolean mainMenu() {
        while (true) {
            User u = auth.current();
            System.out.printf("""

                    ================ MAIN MENU  [%s | %s] ================
                     1. Product Management        8. Search, Filter & Sort
                     2. Stock Operations (Undo)    9. Data: CSV / Backup
                     3. Sales & Billing           10. Audit Log
                     4. Suppliers                 11. User Management
                     5. Purchase Orders           12. Change My Password
                     6. Alerts                    13. Store Settings
                     7. Reports & Dashboard        L. Logout / Switch user
                                                   0. Exit
                    """, u.getUsername(), u.getRole());
            switch (io.required("Choose").toLowerCase()) {
                case "1"  -> productMenu();
                case "2"  -> stockMenu();
                case "3"  -> salesMenu();
                case "4"  -> supplierMenu();
                case "5"  -> purchaseMenu();
                case "6"  -> alertMenu();
                case "7"  -> reportMenu();
                case "8"  -> searchMenu();
                case "9"  -> dataMenu();
                case "10" -> auditMenu();
                case "11" -> userMenu();
                case "12" -> run(this::changePassword);
                case "13" -> run(this::settings);
                case "l"  -> { audit.log(Transaction.Type.USER, null, 0, "Logout"); auth.logout(); save(); return true; }
                case "0"  -> { audit.log(Transaction.Type.USER, null, 0, "Logout"); return false; }
                default   -> System.out.println("Invalid option.");
            }
        }
    }

    /** Runs one action, turning business errors into friendly messages, then auto-saves. */
    private void run(Runnable action) {
        try {
            action.run();
            save();
        } catch (InventoryException | IllegalArgumentException | IllegalStateException | DateTimeException e) {
            System.out.println("  Error: " + e.getMessage());
        } catch (UncheckedIOException e) {
            System.out.println("  File error: " + e.getCause().getMessage());
        }
    }

    private void save() {
        try { storage.save(store); } catch (UncheckedIOException e) { System.out.println("  Could not auto-save: " + e.getMessage()); }
    }

    private String choose(String title, String... options) {
        System.out.println("\n--- " + title + " ---");
        for (String o : options) System.out.println("  " + o);
        return io.required("Choose");
    }

    // =====================================================================
    //  1. Products
    // =====================================================================
    private void productMenu() {
        while (true) {
            switch (choose("PRODUCT MANAGEMENT", "1. Add product", "2. View all products", "3. Product details",
                    "4. Edit product", "5. Delete product", "6. Bulk price change by category",
                    "7. Purge out-of-stock products", "0. Back")) {
                case "1" -> run(this::addProduct);
                case "2" -> run(() -> printProducts(inventory.all()));
                case "3" -> run(this::productDetails);
                case "4" -> run(this::editProduct);
                case "5" -> run(() -> {
                    Product p = inventory.get(io.required("Product ID"));
                    if (io.confirm("Delete '" + p.getName() + "'")) { inventory.delete(p.getId()); System.out.println("Deleted."); }
                });
                case "6" -> run(() -> {
                    String cat = io.required("Category");
                    double pct = io.decimal("Percent change (e.g. 10 or -5)");
                    System.out.println(inventory.bulkPriceChange(cat, pct) + " product(s) updated.");
                });
                case "7" -> run(() -> System.out.println(inventory.purgeOutOfStock() + " product(s) removed."));
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    private void addProduct() {
        String id = io.required("Product ID");
        String name = io.required("Name");
        String cat = io.required("Category");
        String sup = io.optional("Supplier ID (blank = none)");
        double cost = io.decimal("Cost price");
        double price = io.decimal("Selling price");
        int qty = io.integer("Opening quantity");
        int reorder = io.integer("Reorder level");
        LocalDate exp = io.date("Expiry date yyyy-MM-dd (blank = none)", true);
        Product p = inventory.addProduct(new Product(id, name, cat, sup, cost, price, qty, reorder, exp));
        System.out.println("Added " + p.getId() + " - " + p.getName());
        if (price < cost) System.out.println("  Note: selling price is below cost price.");
    }

    private void productDetails() {
        Product p = inventory.get(io.required("Product ID"));
        LocalDate today = LocalDate.now();
        System.out.println("""
                  ID            : %s
                  Name          : %s
                  Category      : %s
                  Supplier      : %s
                  Cost / Price  : %s / %s   (margin %.1f%%)
                  Quantity      : %d   (reorder at %d, suggested order %d)
                  Expiry        : %s
                  Stock value   : %s at cost, %s at retail"""
                .formatted(p.getId(), p.getName(), p.getCategory(), suppliers.nameOf(p.getSupplierId()),
                        Fmt.money(p.getCostPrice()), Fmt.money(p.getPrice()), p.marginPercent(),
                        p.getQuantity(), p.getReorderLevel(), p.suggestedReorderQty(),
                        p.getExpiryDate() == null ? "-" : p.getExpiryDate() + (p.isExpired(today) ? "  (EXPIRED)" : "  (" + p.daysToExpiry(today) + " days left)"),
                        Fmt.money(p.costValue()), Fmt.money(p.retailValue())));
    }

    private void editProduct() {
        String id = io.required("Product ID");
        Product p = inventory.get(id);
        System.out.println("Editing " + p.getName() + ". Fields: name, category, cost, price, reorder, supplier, expiry");
        String field = io.required("Field to edit").toLowerCase();
        String value = field.equals("expiry") || field.equals("supplier")
                ? io.optional("New value (blank to clear)") : io.required("New value");
        inventory.edit(id, field, value);
        System.out.println("Updated." + (field.equals("price") ? " (undoable from Stock Operations)" : ""));
    }

    // =====================================================================
    //  2. Stock
    // =====================================================================
    private void stockMenu() {
        while (true) {
            switch (choose("STOCK OPERATIONS", "1. Restock (add stock)", "2. Remove stock (damage / loss / correction)",
                    "3. Undo last action", "4. Redo", "5. Show undo / redo status", "0. Back")) {
                case "1" -> run(() -> {
                    String id = io.required("Product ID");
                    inventory.restock(id, io.integer("Quantity to add"), io.optional("Note (optional)"));
                    System.out.println("New stock: " + inventory.get(id).getQuantity());
                });
                case "2" -> run(() -> {
                    String id = io.required("Product ID");
                    inventory.removeStock(id, io.integer("Quantity to remove"), io.required("Reason"));
                    System.out.println("New stock: " + inventory.get(id).getQuantity());
                });
                case "3" -> run(() -> System.out.println("Undone: " + inventory.undo()));
                case "4" -> run(() -> System.out.println("Redone: " + inventory.redo()));
                case "5" -> run(() -> {
                    UndoManager u = inventory.undoManager();
                    System.out.println("Undo stack: " + u.undoCount() + "  (next: " + u.peekUndo() + ")");
                    System.out.println("Redo stack: " + u.redoCount() + "  (next: " + u.peekRedo() + ")");
                });
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    // =====================================================================
    //  3. Sales
    // =====================================================================
    private void salesMenu() {
        while (true) {
            switch (choose("SALES & BILLING", "1. New sale (cart + invoice)", "2. List invoices", "3. View / reprint invoice",
                    "4. Void invoice (return stock)", "0. Back")) {
                case "1" -> run(this::newSale);
                case "2" -> run(this::listInvoices);
                case "3" -> run(() -> System.out.println(sales.find(io.required("Invoice no (e.g. 1001)")).toReceipt(store.storeName)));
                case "4" -> run(() -> {
                    Invoice inv = sales.find(io.required("Invoice no"));
                    if (io.confirm("Void " + inv.getInvoiceNo() + " (" + Fmt.money(inv.total()) + ")")) {
                        sales.voidInvoice(inv.getInvoiceNo(), io.required("Reason"));
                        System.out.println("Invoice voided, stock restored.");
                    }
                });
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    private void newSale() {
        Map<String, Integer> cart = new LinkedHashMap<>();
        String customer = io.optional("Customer name (blank = Walk-in)");
        while (true) {
            String id = io.optional("Add product ID (blank = finish)");
            if (id.isEmpty()) break;
            try {
                Product p = inventory.get(id);
                if (p.isExpired(LocalDate.now())) { System.out.println("  '" + p.getName() + "' is expired - cannot sell."); continue; }
                int qty = io.integer("Quantity");
                int inCart = cart.getOrDefault(p.getId(), 0);
                if (qty <= 0) { System.out.println("  Quantity must be positive."); continue; }
                if (inCart + qty > p.getQuantity()) { System.out.println("  Only " + p.getQuantity() + " in stock (" + inCart + " already in cart)."); continue; }
                cart.merge(p.getId(), qty, Integer::sum);
                System.out.printf("  Added %d x %s @ %.2f   | cart items: %d | running subtotal: %s%n",
                        qty, p.getName(), p.getPrice(), cart.size(), Fmt.money(cartTotal(cart)));
            } catch (InventoryException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
        if (cart.isEmpty()) { System.out.println("Cart is empty - sale cancelled."); return; }
        double disc = io.decimal("Discount % [0]", 0);
        double tax = io.decimal("Tax/GST % [" + store.defaultTaxPercent + "]", store.defaultTaxPercent);
        Invoice inv = sales.checkout(customer, cart, disc, tax);
        System.out.println();
        System.out.println(inv.toReceipt(store.storeName));
        Path saved = sales.saveReceipt(inv);
        System.out.println("Receipt saved to: " + saved);
    }

    private double cartTotal(Map<String, Integer> cart) {
        double t = 0;
        for (Map.Entry<String, Integer> e : cart.entrySet()) t += inventory.get(e.getKey()).getPrice() * e.getValue();
        return t;
    }

    private void listInvoices() {
        List<Invoice> list = sales.all();
        if (list.isEmpty()) { System.out.println("No invoices yet."); return; }
        System.out.printf("%-10s %-16s %-14s %5s %14s %-8s %s%n", "INVOICE", "DATE", "CUSTOMER", "ITEMS", "TOTAL", "STATUS", "BY");
        for (Invoice i : list)
            System.out.printf("%-10s %-16s %-14s %5d %14s %-8s %s%n", i.getInvoiceNo(),
                    i.getDateTime().toString().replace('T', ' ').substring(0, 16), Fmt.cut(i.getCustomer(), 14),
                    i.getItems().size(), Fmt.money(i.total()), i.getStatus(), i.getSoldBy());
    }

    // =====================================================================
    //  4. Suppliers
    // =====================================================================
    private void supplierMenu() {
        while (true) {
            switch (choose("SUPPLIERS", "1. Add supplier", "2. List suppliers", "3. Edit supplier",
                    "4. Delete supplier", "5. Products of a supplier", "0. Back")) {
                case "1" -> run(() -> {
                    Supplier s = suppliers.add(new Supplier(io.required("Supplier ID"), io.required("Name"),
                            io.optional("Phone"), io.optional("Email"), io.optional("City")));
                    System.out.println("Added supplier " + s.getId());
                });
                case "2" -> run(() -> {
                    System.out.printf("%-6s %-26s %-12s %-28s %s%n", "ID", "NAME", "PHONE", "EMAIL", "CITY");
                    for (Supplier s : suppliers.all())
                        System.out.printf("%-6s %-26s %-12s %-28s %s%n", s.getId(), s.getName(), s.getPhone(), s.getEmail(), s.getCity());
                });
                case "3" -> run(() -> {
                    String id = io.required("Supplier ID");
                    String field = io.required("Field (name/phone/email/city)").toLowerCase();
                    suppliers.edit(id, field, io.required("New value"));
                    System.out.println("Updated.");
                });
                case "4" -> run(() -> {
                    String id = io.required("Supplier ID");
                    if (io.confirm("Delete supplier " + id)) { suppliers.delete(id); System.out.println("Deleted."); }
                });
                case "5" -> run(() -> printProducts(suppliers.productsOf(io.required("Supplier ID"))));
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    // =====================================================================
    //  5. Purchase orders
    // =====================================================================
    private void purchaseMenu() {
        while (true) {
            switch (choose("PURCHASE ORDERS", "1. Create purchase order", "2. Auto-generate for low-stock items",
                    "3. List all orders", "4. Pending queue (oldest first)", "5. Receive an order",
                    "6. Receive next pending (FIFO)", "7. Cancel an order", "0. Back")) {
                case "1" -> run(this::createPO);
                case "2" -> run(() -> {
                    List<PurchaseOrder> created = purchases.autoReorder();
                    if (created.isEmpty()) System.out.println("Nothing to reorder (no low-stock items with a supplier, or already on order).");
                    for (PurchaseOrder po : created) printPO(po, true);
                });
                case "3" -> run(() -> { for (PurchaseOrder po : purchases.all()) printPO(po, false); if (purchases.all().isEmpty()) System.out.println("No purchase orders."); });
                case "4" -> run(() -> { for (PurchaseOrder po : purchases.pending()) printPO(po, true); if (purchases.pending().isEmpty()) System.out.println("Queue is empty."); });
                case "5" -> run(() -> { PurchaseOrder po = purchases.receive(io.required("PO number")); System.out.println(po.getPoNo() + " received; stock updated."); });
                case "6" -> run(() -> { PurchaseOrder po = purchases.receiveNext(); System.out.println(po.getPoNo() + " received; stock updated."); });
                case "7" -> run(() -> { PurchaseOrder po = purchases.cancel(io.required("PO number")); System.out.println(po.getPoNo() + " cancelled."); });
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    private void createPO() {
        String sid = io.required("Supplier ID");
        suppliers.get(sid);
        Map<String, Integer> lines = new LinkedHashMap<>();
        while (true) {
            String pid = io.optional("Product ID to order (blank = finish)");
            if (pid.isEmpty()) break;
            try {
                Product p = inventory.get(pid);
                lines.merge(p.getId(), io.integer("Quantity (suggested " + p.suggestedReorderQty() + ")"), Integer::sum);
            } catch (InventoryException e) { System.out.println("  Error: " + e.getMessage()); }
        }
        PurchaseOrder po = purchases.create(sid, lines);
        printPO(po, true);
    }

    private void printPO(PurchaseOrder po, boolean withLines) {
        System.out.printf("%s | %s | supplier %s | created %s | total %s%n", po.getPoNo(), po.getStatus(),
                suppliers.nameOf(po.getSupplierId()), po.getCreatedOn(), Fmt.money(po.total()));
        if (withLines)
            for (POLine l : po.getLines())
                System.out.printf("     %-8s %-22s qty %-5d @ %.2f = %.2f%n", l.getProductId(), l.getProductName(), l.getQty(), l.getUnitCost(), l.total());
    }

    // =====================================================================
    //  6. Alerts
    // =====================================================================
    private void alertMenu() {
        while (true) {
            switch (choose("ALERTS", "1. Low stock (most critical first)", "2. Out of stock", "3. Expired products",
                    "4. Expiring within N days", "0. Back")) {
                case "1" -> run(() -> {
                    PriorityQueue<Product> pq = inventory.lowStockQueue();
                    if (pq.isEmpty()) { System.out.println("All products are sufficiently stocked."); return; }
                    List<Product> ordered = new ArrayList<>();
                    while (!pq.isEmpty()) ordered.add(pq.poll());      // poll() yields priority order
                    printProducts(ordered);
                });
                case "2" -> run(() -> printProducts(inventory.outOfStock()));
                case "3" -> run(() -> printProducts(inventory.expired(LocalDate.now())));
                case "4" -> run(() -> printProducts(inventory.expiringWithin(LocalDate.now(), io.integer("Days"))));
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    // =====================================================================
    //  7. Reports
    // =====================================================================
    private void reportMenu() {
        while (true) {
            switch (choose("REPORTS & DASHBOARD", "1. Dashboard", "2. Top-selling products", "3. Daily sales",
                    "4. Category summary", "5. Profit by product", "6. Dead stock (never sold)",
                    "7. Stock value by supplier", "0. Back")) {
                case "1" -> run(this::dashboard);
                case "2" -> run(() -> {
                    List<Map.Entry<String, Integer>> top = reports.topSelling(io.integer("How many"));
                    if (top.isEmpty()) System.out.println("No sales yet.");
                    int rank = 1;
                    for (Map.Entry<String, Integer> e : top) System.out.printf(" %2d. %-34s %5d units%n", rank++, e.getKey(), e.getValue());
                });
                case "3" -> run(() -> {
                    Map<LocalDate, double[]> d = reports.dailySales();
                    if (d.isEmpty()) System.out.println("No sales yet.");
                    d.forEach((day, v) -> System.out.printf(" %s   invoices: %3d   revenue: %s%n", day, (int) v[0], Fmt.money(v[1])));
                });
                case "4" -> run(() -> {
                    System.out.printf("%-14s %9s %7s %16s %16s%n", "CATEGORY", "PRODUCTS", "UNITS", "COST VALUE", "RETAIL VALUE");
                    reports.categorySummary().forEach((c, s) -> System.out.printf("%-14s %9d %7d %16s %16s%n",
                            c, s.products(), s.units(), Fmt.money(s.costValue()), Fmt.money(s.retailValue())));
                });
                case "5" -> run(() -> {
                    List<Map.Entry<String, Double>> list = reports.profitByProduct();
                    if (list.isEmpty()) System.out.println("No sales yet.");
                    for (Map.Entry<String, Double> e : list) System.out.printf(" %-34s %s%n", e.getKey(), Fmt.money(e.getValue()));
                });
                case "6" -> run(() -> printProducts(reports.deadStock()));
                case "7" -> run(() -> reports.stockValueBySupplier().forEach((n, v) -> System.out.printf(" %-26s %s%n", n, Fmt.money(v))));
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    private void dashboard() {
        ReportService.Dashboard d = reports.dashboard();
        System.out.println("""

                ---------------- DASHBOARD ----------------
                 Products            : %d      Total units   : %d
                 Stock value (cost)  : %s
                 Stock value (retail): %s
                 Low stock           : %d      Out of stock  : %d      Expired : %d
                 Pending POs         : %d
                 Paid invoices       : %d
                 Revenue (incl. tax) : %s
                 Gross profit        : %s
                -------------------------------------------"""
                .formatted(d.products(), d.units(), Fmt.money(d.costValue()), Fmt.money(d.retailValue()),
                        d.lowStock(), d.outOfStock(), d.expired(), d.pendingOrders(), d.invoices(),
                        Fmt.money(d.revenue()), Fmt.money(d.profit())));
    }

    // =====================================================================
    //  8. Search / filter / sort
    // =====================================================================
    private void searchMenu() {
        while (true) {
            switch (choose("SEARCH, FILTER & SORT", "1. Search (ID / name / category)", "2. Filter by category",
                    "3. Filter by price range", "4. Sorted listing", "5. Grouped by category", "0. Back")) {
                case "1" -> run(() -> printProducts(inventory.search(io.required("Keyword"))));
                case "2" -> run(() -> {
                    System.out.println("Categories: " + String.join(", ", inventory.categories()));
                    printProducts(inventory.byCategory(io.required("Category")));
                });
                case "3" -> run(() -> printProducts(inventory.byPriceRange(io.decimal("Min price"), io.decimal("Max price"))));
                case "4" -> run(this::sortedListing);
                case "5" -> run(() -> inventory.groupByCategory().forEach((cat, list) -> {
                    System.out.println("\n== " + cat + " (" + list.size() + ") ==");
                    printProducts(list);
                }));
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    private void sortedListing() {
        System.out.println("Sort by: 1) Name  2) Price low->high  3) Price high->low  4) Quantity low->high  5) Stock value  6) Expiry date");
        Comparator<Product> cmp = switch (io.required("Choice")) {
            case "1" -> Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER);
            case "2" -> Comparator.comparingDouble(Product::getPrice);
            case "3" -> Comparator.comparingDouble(Product::getPrice).reversed();
            case "4" -> Comparator.comparingInt(Product::getQuantity);
            case "5" -> Comparator.comparingDouble(Product::costValue).reversed();
            case "6" -> Comparator.comparing(Product::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()));
            default  -> throw new IllegalArgumentException("Invalid sort option.");
        };
        printProducts(inventory.sorted(cmp));
    }

    // =====================================================================
    //  9. Data
    // =====================================================================
    private void dataMenu() {
        while (true) {
            switch (choose("DATA: CSV & BACKUP", "1. Export products to CSV", "2. Import products from CSV",
                    "3. Export invoices to CSV", "4. Backup data file", "0. Back")) {
                case "1" -> run(() -> {
                    auth.require(Permission.DATA_IO);
                    System.out.println("Exported to: " + CsvUtil.exportProducts(inventory.all(), Paths.get("data", "exports", "products.csv")).toAbsolutePath());
                });
                case "2" -> run(() -> {
                    auth.require(Permission.DATA_IO);
                    Path file = Paths.get(io.required("CSV file path"));
                    List<String> errors = new ArrayList<>();
                    int[] r = CsvUtil.importProducts(file, inventory, errors);
                    System.out.println("Imported: " + r[0] + ", skipped: " + r[1]);
                    errors.forEach(e -> System.out.println("   " + e));
                });
                case "3" -> run(() -> {
                    auth.require(Permission.DATA_IO);
                    System.out.println("Exported to: " + CsvUtil.exportInvoices(sales.all(), Paths.get("data", "exports", "invoices.csv")).toAbsolutePath());
                });
                case "4" -> run(() -> {
                    auth.require(Permission.DATA_IO);
                    storage.save(store);
                    System.out.println("Backup created: " + storage.backup().toAbsolutePath());
                    System.out.println("To restore, close the app and copy that file over data/inventory.dat");
                });
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    // =====================================================================
    //  10. Audit
    // =====================================================================
    private void auditMenu() {
        while (true) {
            switch (choose("AUDIT LOG", "1. Last N entries", "2. Filter by action type", "3. Filter by product",
                    "4. Summary (count per action)", "0. Back")) {
                case "1" -> run(() -> printTx(audit.recent(io.integer("How many"))));
                case "2" -> run(() -> {
                    System.out.println("Types: " + Arrays.toString(Transaction.Type.values()));
                    printTx(audit.byType(Transaction.Type.valueOf(io.required("Type").toUpperCase())));
                });
                case "3" -> run(() -> printTx(audit.byProduct(io.required("Product ID"))));
                case "4" -> run(() -> audit.countByType().forEach((t, n) -> System.out.printf(" %-16s %d%n", t, n)));
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    private void printTx(List<Transaction> list) {
        if (list.isEmpty()) System.out.println("No entries.");
        list.forEach(System.out::println);
    }

    // =====================================================================
    //  11-13. Users / password / settings
    // =====================================================================
    private void userMenu() {
        while (true) {
            switch (choose("USER MANAGEMENT (admin)", "1. List users", "2. Add user", "3. Change role",
                    "4. Reset password", "5. Activate / deactivate", "6. Delete user", "0. Back")) {
                case "1" -> run(() -> {
                    List<User> users = auth.listUsers();      // permission check happens before any output
                    System.out.printf("%-12s %-22s %-8s %s%n", "USERNAME", "NAME", "ROLE", "STATUS");
                    for (User u : users)
                        System.out.printf("%-12s %-22s %-8s %s%n", u.getUsername(), u.getFullName(), u.getRole(), u.isActive() ? "active" : "disabled");
                });
                case "2" -> run(() -> {
                    String un = io.required("Username");
                    String fn = io.required("Full name");
                    String pw = io.required("Password (min 6 chars)");
                    Role role = Role.valueOf(io.required("Role (ADMIN/MANAGER/STAFF)").toUpperCase());
                    auth.addUser(un, fn, pw, role);
                    System.out.println("User created.");
                });
                case "3" -> run(() -> {
                    auth.setRole(io.required("Username"), Role.valueOf(io.required("New role (ADMIN/MANAGER/STAFF)").toUpperCase()));
                    System.out.println("Role updated.");
                });
                case "4" -> run(() -> { auth.resetPassword(io.required("Username"), io.required("New password")); System.out.println("Password reset."); });
                case "5" -> run(() -> {
                    String un = io.required("Username");
                    boolean on = io.confirm("Activate (y) or deactivate (n)");
                    auth.setActive(un, on);
                    System.out.println(on ? "Activated." : "Deactivated.");
                });
                case "6" -> run(() -> {
                    String un = io.required("Username");
                    if (io.confirm("Delete user " + un)) { auth.deleteUser(un); System.out.println("Deleted."); }
                });
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    private void changePassword() {
        String old = io.required("Current password");
        String nw = io.required("New password (min 6 chars)");
        auth.changeOwnPassword(old, nw);
        System.out.println("Password changed.");
    }

    private void settings() {
        auth.require(Permission.USER_MANAGE);
        System.out.println("Store name: " + store.storeName + " | Default GST: " + store.defaultTaxPercent + "%");
        String name = io.optional("New store name (blank = keep)");
        if (!name.isEmpty()) store.storeName = name;
        double tax = io.decimal("New default GST % (blank = keep)", store.defaultTaxPercent);
        if (tax < 0 || tax > 100) throw new IllegalArgumentException("GST must be 0-100.");
        store.defaultTaxPercent = tax;
        System.out.println("Settings saved.");
    }

    // =====================================================================
    //  Table printing
    // =====================================================================
    private void printProducts(List<Product> list) {
        if (list.isEmpty()) { System.out.println("No products found."); return; }
        LocalDate today = LocalDate.now();
        String f = "%-7s %-20s %-12s %-18s %10s %10s %5s %7s %-11s %s%n";
        System.out.printf(f, "ID", "NAME", "CATEGORY", "SUPPLIER", "COST", "PRICE", "QTY", "REORDER", "EXPIRY", "FLAGS");
        System.out.println("-".repeat(112));
        for (Product p : list) {
            List<String> flags = new ArrayList<>();
            if (p.getQuantity() == 0) flags.add("OUT");
            else if (p.isLowStock()) flags.add("LOW");
            if (p.isExpired(today)) flags.add("EXPIRED");
            else if (p.daysToExpiry(today) <= 7) flags.add("EXPIRES<=7d");
            System.out.printf(f, p.getId(), Fmt.cut(p.getName(), 20), Fmt.cut(p.getCategory(), 12),
                    Fmt.cut(suppliers.nameOf(p.getSupplierId()), 18), String.format("%.2f", p.getCostPrice()),
                    String.format("%.2f", p.getPrice()), p.getQuantity(), p.getReorderLevel(),
                    Fmt.date(p.getExpiryDate()), String.join(",", flags));
        }
        System.out.println(list.size() + " product(s).");
    }
}
