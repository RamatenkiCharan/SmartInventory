# Smart Inventory Management System - Feature List

## 1. Security & Users
1. **Login screen** with 3 attempts; passwords stored as **SHA-256 hashes**.
2. **Role-based access control** - ADMIN, MANAGER, STAFF, backed by 11 permissions (`EnumSet`), enforced in the service layer (not just hidden in menus).
3. **User management** (admin): list, add, change role, reset password, activate/deactivate, delete.
4. Safeguards: cannot delete/deactivate/demote the last admin or yourself.
5. **Change my password** for every user.
6. **Discount limit** - staff can give at most 10 % discount; managers/admins can give more.
7. Session switch (Logout / Switch user) without restarting.

## 2. Product Management
8. Add product: ID, name, category, supplier, cost price, selling price, quantity, reorder level, optional expiry date.
9. View all products in a formatted table with status flags (`LOW`, `OUT`, `EXPIRED`, `EXPIRES<=7d`).
10. Product detail view: margin %, stock value (cost and retail), days to expiry, suggested order quantity.
11. Edit any field: name, category, cost, price, reorder level, supplier, expiry.
12. Delete product (admin only, with confirmation; blocked if on a pending purchase order).
13. **Bulk price change** by category (+/- %).
14. **Purge out-of-stock** products (uses `Iterator.remove()`).
15. Full input validation (negative values, duplicate IDs, unknown suppliers, bad dates).

## 3. Stock Operations
16. Restock (add stock) with optional note.
17. Remove stock for damage / loss / correction, with a reason.
18. Negative stock is never allowed.
19. **Undo / Redo** for restock, stock removal and price changes (Command pattern, two stacks, 50-step history).
20. Undo/redo status view.

## 4. Sales & Billing
21. **Cart-based sale**: add many items, live running subtotal, per-item stock check (including what is already in the cart).
22. **Discount % and GST/tax %** per invoice (default GST configurable in settings).
23. **Printable invoice/receipt**, automatically saved to `data/invoices/INV-xxxx.txt`.
24. Invoice list, view and reprint.
25. **Void invoice** - returns the items to stock and marks the invoice VOIDED (excluded from reports).
26. **Expired products cannot be sold.**
27. **Atomic checkout** - everything is validated first, so a failed sale never leaves partial changes.
28. Invoices keep a **price snapshot**, so later price edits never alter past invoices.

## 5. Suppliers
29. Add, list, edit (name / phone / email / city) and delete suppliers.
30. Delete is blocked while products or pending orders reference the supplier.
31. View all products of a supplier.

## 6. Purchase Orders
32. Create a purchase order manually (supplier + product lines, with suggested quantities).
33. **Auto-generate purchase orders** for all low-stock items - grouped per supplier, skipping items already on order.
34. **Pending-orders FIFO queue** (oldest first).
35. Receive a specific order, or **receive the next order in the queue**; stock updates automatically.
36. Cancel an order; list all orders with line details and totals.

## 7. Alerts
37. **Low-stock alert list** ordered by urgency (`PriorityQueue`).
38. Out-of-stock list.
39. Expired products list.
40. **Expiring within N days** (range query on a `TreeMap` keyed by date).
41. Alert summary shown automatically at login.

## 8. Search, Filter & Sort
42. Keyword search across ID, name and category.
43. Filter by category (with a list of existing categories) and by price range.
44. Sort by name, price (asc/desc), quantity, stock value, or expiry date.
45. View grouped by category.

## 9. Reports & Dashboard
46. **Dashboard**: product count, units, stock value (cost and retail), low/out/expired counts, pending POs, revenue, gross profit.
47. Top-selling products.
48. Daily sales report (invoice count and revenue per date).
49. Category summary.
50. Profit by product.
51. **Dead-stock report** (stock that has never sold).
52. Stock value by supplier.

## 10. Audit Trail
53. Every important action (sale, restock, price change, PO, login, user change...) is logged with user and timestamp.
54. View the last N entries, filter by action type, filter by product.
55. Summary count per action type (`EnumMap`).

## 11. Data Handling
56. **Auto-save** after every action; data reloads on the next start (Java serialization).
57. **CSV export** of products and invoices.
58. **CSV import** of products - bad rows are skipped and reported by line number; quoted fields with commas are supported.
59. **Backup** of the data file with a timestamp.
60. `--reset` option reloads the demo data.
61. Store settings: store name and default GST %.

## 12. Technical Features
- Layered architecture: `model`, `service`, `util`, `ui`, `exception`.
- Custom exception hierarchy (`NotFoundException`, `DuplicateException`, `InsufficientStockException`, `AccessDeniedException`).
- Design patterns: Command (undo/redo), Service layer, Repository-style `DataStore`.
- Java 17 features: records, switch expressions, text blocks, pattern-matching `instanceof`.
- Streams + `Collectors.groupingBy` for reports and auto-reorder.
- Robust console input (re-prompts on bad input, clean exit when input ends).

## 9. Database (JDBC)
* Persistent storage in SQLite via JDBC: 9 normalised tables with primary/foreign keys and constraints.
* Transactional saves (rollback on failure), append-only audit table, consistent `.db` backups.
* Automatic import of data saved by the earlier file-based version.
