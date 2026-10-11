# Smart Inventory Management System (Java Collections)

A desktop (Swing) application - with an optional console mode - for managing products, stock, sales, suppliers, purchase orders and reports.
Built to show practical use of the **Java Collections Framework** in a layered, multi-package design, with all data stored in a database through **JDBC (SQLite)**.

## Requirements
JDK 17 or newer.

## Run
```bash
./run.sh            # opens the graphical interface (Linux / macOS)
run.bat             # Windows
./run.sh --reset    # discard saved data and reload demo data
./run.sh --console  # the original menu-driven console version
```
Or manually:
```bash
mkdir out
javac -cp lib/sqlite-jdbc.jar -d out $(find src -name "*.java")
java -cp "out:lib/sqlite-jdbc.jar" com.inventory.Main     # on Windows use ; instead of :
```

## Demo logins
| User | Password | Role |
|---|---|---|
| admin | admin123 | ADMIN - everything |
| manager | manager123 | MANAGER - everything except user management and deleting products |
| staff | staff123 | STAFF - view, search and sell only (max 10 % discount) |

Demo data: 11 products in 4 categories, 3 suppliers, one expired and one soon-to-expire product, several low-stock items.

## Project structure
```
src/com/inventory/
  Main.java                    entry point
  model/       Product, Supplier, User, Role, Permission, Invoice, InvoiceItem,
               PurchaseOrder, POLine, Transaction
  exception/   InventoryException + 4 specific exceptions
  service/     DataStore, AuthService, InventoryService, SalesService, PurchaseService,
               SupplierService, ReportService, AuditLog, UndoManager, Command, SampleData
  db/          Database (JDBC connection + schema), JdbcStorage (load/save/backup)
  util/        Storage (interface), FileStorage (legacy), Console, CsvUtil, Fmt
  ui/          ConsoleApp (original console menus)
  gui/         Swing interface: Theme, Ui (components), LoginFrame, MainFrame,
               Dashboard / Products / Sales / Purchase / Suppliers / Reports / Audit / Users panels
FEATURES.md                    complete feature list
```

## Where each collection is used
| Collection | Class / purpose |
|---|---|
| `LinkedHashMap<String, Product>` | `DataStore` - O(1) lookup by ID, keeps insertion order (also suppliers, users, purchase orders) |
| `ArrayList` | Invoices, audit log, search results, sorted listings |
| `Comparator` chains + `List.sort` | `InventoryService.sorted`, sort menu (name, price, qty, value, expiry with `nullsLast`) |
| `TreeMap<String, List<Product>>` | `groupByCategory` - alphabetical, case-insensitive |
| `TreeSet` | `categories()` - unique sorted category names |
| `TreeMap<LocalDate, List<Product>>` (`NavigableMap`) | `expiryIndex` - `headMap` for expired, `subMap` for "expiring within N days" |
| `PriorityQueue<Product>` | `lowStockQueue` - most critical item first |
| `ArrayDeque` (two stacks) | `UndoManager` - undo / redo |
| `LinkedList` as `Queue<PurchaseOrder>` | `DataStore.pendingOrders` - FIFO receiving |
| `HashSet` | `ReportService.deadStock`, `autoReorder` (skip items already on order) |
| `HashMap` + `merge` | Top-selling and profit-by-product aggregation |
| `EnumMap` | `AuditLog.countByType` |
| `EnumSet` | `Role` permissions |
| `Iterator.remove()` | `purgeOutOfStock` |
| `Collections.unmodifiableList` | `Invoice.getItems`, `PurchaseOrder.getLines` |
| Streams + `Collectors.groupingBy` | Reports, auto purchase orders |

## Suggested walkthrough for a demo / viva
1. Login as **staff**, try *Add product* -> access denied. Make a sale with 15 % discount -> denied; with 5 % -> invoice printed.
2. Login as **manager**: void that invoice (stock returns), restock an item, then **Undo** / **Redo**.
3. Alerts -> low stock, expired, expiring in 30 days.
4. Purchase Orders -> *Auto-generate*, then *Receive next (FIFO)*.
5. Reports -> Dashboard, top sellers, dead stock.
6. Audit Log -> show every action traced to a user.
7. Exit and restart: all data is still there.

## Ideas for extension
Swing/JavaFX or Spring Boot front end, JDBC/SQLite storage, barcode lookup, multiple warehouses, e-mail alerts, JUnit tests.

## Graphical interface (Swing, JDK only - no external libraries)
* Split-screen **sign-in** with quick-fill demo accounts and shake animation on a wrong password.
* **Sidebar navigation** that shows only what the user's role may access; red badge on *Products* for low-stock/expired items.
* **Dashboard**: KPI cards, clickable "Needs attention" list (jumps to Products with the filter applied), category value chart, critical stock, top sellers.
* **Products**: live search, category and status filters, colour-coded status pills, add/edit with live margin preview, restock, remove stock, bulk price change, right-click menu.
* **Sales**: point-of-sale cart, discount/GST, live totals, printable receipt, invoice history with void.
* **Purchasing**, **Suppliers**, **Reports** (charts + tables), **Audit log** (search + filter), **Users**.
* Toast notifications, inline form validation, undo/redo buttons.
* Shortcuts: `Ctrl+Z` undo, `Ctrl+Y` redo, `Ctrl+F` search products, `Ctrl+1..9` switch screens, `F5` refresh.
* Top-right user menu: change password, CSV export/import, backup, sign out.

## Quick start (prebuilt)
`dist/SmartInventory.jar` is already built. Double-click it, or run `run-jar.sh` / `run-jar.bat` (needs JDK/JRE 17+).

## Database (JDBC)
Data is stored in an SQLite database file, `data/inventory.db`, accessed with plain JDBC
(`PreparedStatement`, transactions). The driver is `lib/sqlite-jdbc.jar` (it is loaded automatically;
`dist/SmartInventory.jar` references it, so keep the `lib` folder next to `dist`).

* **How it works:** the services keep using the in-memory collections (that is the point of the project);
  `JdbcStorage` loads them from the database at start-up and writes every change back in **one transaction**
  (all-or-nothing - a failed save rolls back). The audit log is append-only.
* **Tables:** `meta`, `suppliers`, `products`, `users`, `invoices`, `invoice_items`, `purchase_orders`,
  `po_lines`, `audit_log` - with primary keys, foreign keys, `CHECK` constraints and indexes
  (see `src/com/inventory/db/Database.java`).
* **Existing data:** if `data/inventory.dat` from the older version exists and the database is empty, it is
  imported automatically on first start.
* **Other file / location:** `java -Ddb.url=jdbc:sqlite:C:/data/shop.db -jar dist/SmartInventory.jar`
* **Backup:** user menu -> Backup writes a consistent copy to `data/backups/inventory-<time>.db`.
* **Inspect the data:** open `data/inventory.db` with *DB Browser for SQLite*, or the *SQLite Viewer* extension in VS Code.
* **Reset:** `--reset` wipes all tables and reloads the demo data.
