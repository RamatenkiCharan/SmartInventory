package com.inventory.gui;

import com.inventory.model.Permission;
import com.inventory.model.Product;
import com.inventory.model.Supplier;
import com.inventory.util.Fmt;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Product catalogue: live search, filters, add/edit/delete, restock and stock adjustments. */
public class ProductsPanel extends JPanel implements Refreshable {
    public static final String[] STATUS = {"All statuses", "In stock", "Low stock", "Out of stock", "Expired", "Expiring in 30 days"};
    private static final String ALL_CATS = "All categories";

    private final AppContext ctx;
    private final JTextField search = Ui.field("Search by ID, name or category\u2026", 24);
    private final JComboBox<String> category = Ui.combo(ALL_CATS);
    private final JComboBox<String> status = Ui.combo(STATUS);
    private final Ui.Model model = new Ui.Model(
            new String[]{"ID", "Product", "Category", "Cost", "Price", "Qty", "Reorder", "Margin", "Expiry", "Status"},
            new Class<?>[]{String.class, String.class, String.class, Ui.Val.class, Ui.Val.class,
                    Integer.class, Integer.class, Ui.Val.class, String.class, Ui.Badge.class});
    private final JTable table = Ui.table(model, 55, 165, 100, 115, 115, 50, 80, 75, 105, 125);
    private final JLabel summary = Ui.muted(" ");
    private final Ui.Btn edit = Ui.secondary("Edit"), restock = Ui.secondary("Restock"),
            remove = Ui.secondary("Remove stock"), delete = Ui.secondary("Delete");
    private boolean updating;

    public ProductsPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(8, 28, 20, 28));

        // ---- toolbar
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        JPanel left = Ui.hbox(10, search, category, status);
        bar.add(left, BorderLayout.WEST);
        JPanel right = Ui.hbox(8);
        if (ctx.can(Permission.PRODUCT_EDIT)) {
            Ui.Btn more = Ui.secondary("More");
            more.addActionListener(e -> morePopup().show(more, 0, more.getHeight() + 4));
            right.add(more);
            Ui.Btn add = Ui.primary("+  Add product");
            add.addActionListener(e -> productDialog(null));
            right.add(add);
        }
        bar.add(right, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);
        add(Ui.tableCard(table), BorderLayout.CENTER);
        JPanel actions = Ui.hbox(8);
        if (ctx.can(Permission.STOCK_MANAGE)) { actions.add(restock); actions.add(remove); }
        if (ctx.can(Permission.PRODUCT_EDIT)) actions.add(edit);
        if (ctx.can(Permission.PRODUCT_DELETE)) actions.add(delete);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(summary, BorderLayout.WEST);
        south.add(actions, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        // ---- behaviour
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        category.addActionListener(e -> { if (!updating) refresh(); });
        status.addActionListener(e -> { if (!updating) refresh(); });
        table.putClientProperty("emptyText", "No products match your filters");
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        edit.addActionListener(e -> withSelected(this::productDialog));
        restock.addActionListener(e -> withSelected(this::restockDialog));
        remove.addActionListener(e -> withSelected(this::removeStockDialog));
        delete.addActionListener(e -> withSelected(this::deleteProduct));
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { popupMaybe(e); }
            @Override public void mouseReleased(MouseEvent e) { popupMaybe(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0 && ctx.can(Permission.PRODUCT_EDIT)) withSelected(p -> productDialog(p));
            }
        });
        updateButtons();
        refresh();
    }

    /** Called from the dashboard to jump here with a preset filter. */
    public void showFilter(String statusName) {
        updating = true;
        search.setText("");
        category.setSelectedItem(ALL_CATS);
        status.setSelectedItem(statusName);
        updating = false;
        refresh();
    }

    public void focusSearch() { search.requestFocusInWindow(); search.selectAll(); }

    // ------------------------------------------------------------------ data
    public static Ui.Badge badge(Product p) {
        LocalDate today = LocalDate.now();
        if (p.isExpired(today))            return new Ui.Badge("Expired", Theme.BAD);
        if (p.getQuantity() == 0)          return new Ui.Badge("Out of stock", Theme.BAD);
        if (p.isLowStock())                return new Ui.Badge("Low stock", Theme.WARN);
        if (p.daysToExpiry(today) <= 30)   return new Ui.Badge("Expiring soon", Theme.WARN);
        return new Ui.Badge("In stock", Theme.GOOD);
    }

    private boolean matches(Product p, String q, String cat, String st) {
        if (!q.isEmpty() && !(p.getId().toLowerCase().contains(q) || p.getName().toLowerCase().contains(q)
                || p.getCategory().toLowerCase().contains(q))) return false;
        if (!ALL_CATS.equals(cat) && !p.getCategory().equalsIgnoreCase(cat)) return false;
        LocalDate today = LocalDate.now();
        return switch (st) {
            case "In stock"            -> !p.isLowStock() && !p.isExpired(today);
            case "Low stock"           -> p.isLowStock();
            case "Out of stock"        -> p.getQuantity() == 0;
            case "Expired"             -> p.isExpired(today);
            case "Expiring in 30 days" -> !p.isExpired(today) && p.daysToExpiry(today) <= 30;
            default -> true;
        };
    }

    @Override public void refresh() {
        String keep = Ui.selectedKey(table);
        updating = true;
        Object selCat = category.getSelectedItem();
        category.removeAllItems();
        category.addItem(ALL_CATS);
        for (String c : ctx.inventory.categories()) category.addItem(c);
        category.setSelectedItem(selCat != null ? selCat : ALL_CATS);
        if (category.getSelectedItem() == null) category.setSelectedItem(ALL_CATS);
        updating = false;

        String q = search.getText().trim().toLowerCase();
        String cat = String.valueOf(category.getSelectedItem());
        String st = String.valueOf(status.getSelectedItem());
        List<Product> all = ctx.inventory.all();
        List<Object[]> rows = new ArrayList<>();
        double cost = 0, retail = 0;
        for (Product p : all) {
            if (!matches(p, q, cat, st)) continue;
            cost += p.costValue(); retail += p.retailValue();
            rows.add(new Object[]{p.getId(), p.getName(), p.getCategory(),
                    Ui.money(p.getCostPrice()), Ui.money(p.getPrice()), p.getQuantity(), p.getReorderLevel(),
                    Ui.pct(p.marginPercent()), Fmt.date(p.getExpiryDate()), badge(p)});
        }
        model.setRows(rows);
        Ui.reselect(table, keep);
        summary.setText(rows.isEmpty()
                ? "No products match your filters."
                : "Showing " + rows.size() + " of " + all.size() + " products   \u00B7   Stock value at cost " + Fmt.money(cost)
                  + "   \u00B7   at retail " + Fmt.money(retail));
        updateButtons();
    }

    private void updateButtons() {
        boolean has = table.getSelectedRow() >= 0;
        edit.setEnabled(has); restock.setEnabled(has); remove.setEnabled(has); delete.setEnabled(has);
    }

    private void withSelected(java.util.function.Consumer<Product> action) {
        String id = Ui.selectedKey(table);
        if (id == null) { Toast.info("Select a product first."); return; }
        Ui.attempt(() -> action.accept(ctx.inventory.get(id)));
    }

    // ------------------------------------------------------------------ menus
    private void popupMaybe(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int r = table.rowAtPoint(e.getPoint());
        if (r < 0) return;
        table.setRowSelectionInterval(r, r);
        JPopupMenu m = new JPopupMenu();
        if (ctx.can(Permission.PRODUCT_EDIT))  m.add(item("Edit product\u2026", () -> withSelected(this::productDialog)));
        if (ctx.can(Permission.STOCK_MANAGE))  { m.add(item("Restock\u2026", () -> withSelected(this::restockDialog)));
                                                 m.add(item("Remove stock\u2026", () -> withSelected(this::removeStockDialog))); }
        if (ctx.can(Permission.PRODUCT_DELETE)) { m.addSeparator(); m.add(item("Delete\u2026", () -> withSelected(this::deleteProduct))); }
        if (m.getComponentCount() > 0) m.show(table, e.getX(), e.getY());
    }

    private JPopupMenu morePopup() {
        JPopupMenu m = new JPopupMenu();
        m.add(item("Bulk price change by category\u2026", this::bulkPriceDialog));
        if (ctx.can(Permission.PRODUCT_DELETE)) m.add(item("Purge out-of-stock products\u2026", this::purge));
        return m;
    }

    private JMenuItem item(String text, Runnable r) {
        JMenuItem mi = new JMenuItem(text);
        mi.setFont(Theme.font(Font.PLAIN, 14));
        mi.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 24));
        mi.addActionListener(e -> r.run());
        return mi;
    }

    // ------------------------------------------------------------------ dialogs
    private void productDialog(Product existing) {
        boolean edit = existing != null;
        JTextField id = Ui.field("e.g. P012", 10), name = Ui.field("Product name", 16),
                cost = Ui.field("0.00", 8), price = Ui.field("0.00", 8), expiry = Ui.field("yyyy-MM-dd (optional)", 10);
        JComboBox<String> cat = Ui.combo(new ArrayList<>(ctx.inventory.categories()));
        cat.setEditable(true);
        cat.setSelectedItem("");
        List<Ui.Opt> sup = new ArrayList<>();
        sup.add(new Ui.Opt(null, "(no supplier)"));
        for (Supplier s : ctx.suppliers.all()) sup.add(new Ui.Opt(s.getId(), s.getId() + " \u2013 " + s.getName()));
        JComboBox<Ui.Opt> supplier = Ui.combo(sup);
        JSpinner qty = Ui.intSpinner(0, 0, 1_000_000), reorder = Ui.intSpinner(10, 0, 1_000_000);
        JLabel margin = Ui.label("\u2013", 14, Font.BOLD, Theme.MUTED);

        if (edit) {
            id.setText(existing.getId()); id.setEditable(false); id.setEnabled(false);
            name.setText(existing.getName());
            cat.setSelectedItem(existing.getCategory());
            for (Ui.Opt o : sup) if (o.id() != null && o.id().equals(existing.getSupplierId())) supplier.setSelectedItem(o);
            cost.setText(String.valueOf(existing.getCostPrice())); price.setText(String.valueOf(existing.getPrice()));
            reorder.setValue(existing.getReorderLevel());
            expiry.setText(existing.getExpiryDate() == null ? "" : existing.getExpiryDate().toString());
        }
        DocumentListener dl = new DocumentListener() {
            void upd() {
                try {
                    double c = Double.parseDouble(cost.getText().trim()), p = Double.parseDouble(price.getText().trim());
                    double m = p == 0 ? 0 : (p - c) / p * 100;
                    margin.setText(String.format("%.1f%%  (profit %s per unit)", m, Fmt.money(p - c)));
                    margin.setForeground(p - c < 0 ? Theme.BAD : Theme.GOOD);
                } catch (NumberFormatException ex) { margin.setText("\u2013"); margin.setForeground(Theme.MUTED); }
            }
            public void insertUpdate(DocumentEvent e) { upd(); }
            public void removeUpdate(DocumentEvent e) { upd(); }
            public void changedUpdate(DocumentEvent e) { upd(); }
        };
        cost.getDocument().addDocumentListener(dl);
        price.getDocument().addDocumentListener(dl);
        dl.changedUpdate(null);

        Ui.Form f = new Ui.Form();
        f.add2("Product ID", id, "Name", name);
        f.add2("Category", cat, "Supplier", supplier);
        f.add2("Cost price (Rs.)", cost, "Selling price (Rs.)", price);
        f.add("Profit margin", margin);
        if (edit) f.add2("Reorder level", reorder, "Expiry date", expiry);
        else      f.add2("Opening stock", qty, "Reorder level", reorder);
        if (!edit) f.add("Expiry date", expiry);

        boolean saved = Ui.formDialog(this, edit ? "Edit product" : "Add product", f, edit ? "Save changes" : "Add product", () -> {
            String catText = String.valueOf(cat.getEditor().getItem()).trim();
            String supId = ((Ui.Opt) supplier.getSelectedItem()).id();
            double c = Double.parseDouble(cost.getText().trim()), p = Double.parseDouble(price.getText().trim());
            String exp = expiry.getText().trim();
            LocalDate expDate = exp.isEmpty() ? null : LocalDate.parse(exp);
            if (p < c) throw new IllegalArgumentException("Selling price is below cost price \u2013 please check the prices.");
            if (!edit) {
                ctx.inventory.addProduct(new Product(id.getText(), name.getText(), catText, supId, c, p,
                        (Integer) qty.getValue(), (Integer) reorder.getValue(), expDate));
            } else {
                String pid = existing.getId();
                if (!name.getText().trim().equals(existing.getName()))   ctx.inventory.edit(pid, "name", name.getText());
                if (!catText.equals(existing.getCategory()))             ctx.inventory.edit(pid, "category", catText);
                if (!java.util.Objects.equals(supId, existing.getSupplierId())) ctx.inventory.edit(pid, "supplier", supId == null ? "" : supId);
                if (c != existing.getCostPrice())                        ctx.inventory.edit(pid, "cost", String.valueOf(c));
                if (p != existing.getPrice())                            ctx.inventory.edit(pid, "price", String.valueOf(p));
                if ((Integer) reorder.getValue() != existing.getReorderLevel()) ctx.inventory.edit(pid, "reorder", String.valueOf(reorder.getValue()));
                if (!java.util.Objects.equals(expDate, existing.getExpiryDate())) ctx.inventory.edit(pid, "expiry", exp);
            }
        });
        if (saved) { ctx.changed(); Toast.success(edit ? "Product updated." : "Product added."); }
    }

    private void restockDialog(Product p) {
        JSpinner qty = Ui.intSpinner(p.suggestedReorderQty(), 1, 1_000_000);
        JTextField note = Ui.field("e.g. Delivery from supplier", 20);
        Ui.Form f = new Ui.Form();
        f.add("Product", Ui.label(p.getName() + "   (in stock: " + p.getQuantity() + ")", 14, Font.BOLD, Theme.TEXT));
        f.add2("Quantity to add", qty, "Note", note);
        if (Ui.formDialog(this, "Restock product", f, "Add stock", () ->
                ctx.inventory.restock(p.getId(), (Integer) qty.getValue(), note.getText().isBlank() ? "Manual restock" : note.getText().trim()))) {
            ctx.changed();
            Toast.success("Added " + qty.getValue() + " \u00D7 " + p.getName() + ". Undo with Ctrl+Z.");
        }
    }

    private void removeStockDialog(Product p) {
        JSpinner qty = Ui.intSpinner(1, 1, Math.max(1, p.getQuantity()));
        JComboBox<String> reason = Ui.combo("Damaged", "Expired", "Lost / stolen", "Stock count correction");
        Ui.Form f = new Ui.Form();
        f.add("Product", Ui.label(p.getName() + "   (in stock: " + p.getQuantity() + ")", 14, Font.BOLD, Theme.TEXT));
        f.add2("Quantity to remove", qty, "Reason", reason);
        if (Ui.formDialog(this, "Remove stock", f, "Remove stock", () ->
                ctx.inventory.removeStock(p.getId(), (Integer) qty.getValue(), String.valueOf(reason.getSelectedItem())))) {
            ctx.changed();
            Toast.success("Removed " + qty.getValue() + " \u00D7 " + p.getName() + ". Undo with Ctrl+Z.");
        }
    }

    private void deleteProduct(Product p) {
        if (Ui.confirm(this, "Delete product", "Delete <b>" + p.getName() + "</b> (" + p.getId() + ")? This cannot be undone.", "Delete")) {
            if (Ui.attempt(() -> ctx.inventory.delete(p.getId()))) { ctx.changed(); Toast.success("Product deleted."); }
        }
    }

    private void bulkPriceDialog() {
        JComboBox<String> cat = Ui.combo(new ArrayList<>(ctx.inventory.categories()));
        JTextField pct = Ui.field("e.g. 10 or -5", 8);
        Ui.Form f = new Ui.Form();
        f.add2("Category", cat, "Change (%)", pct);
        int[] count = {0};
        if (Ui.formDialog(this, "Bulk price change", f, "Apply", () ->
                count[0] = ctx.inventory.bulkPriceChange(String.valueOf(cat.getSelectedItem()), Double.parseDouble(pct.getText().trim())))) {
            ctx.changed();
            Toast.success("Updated prices of " + count[0] + " products.");
        }
    }

    private void purge() {
        if (Ui.confirm(this, "Purge products", "Remove every product with zero stock (that is not on a pending order)?", "Purge")) {
            int[] n = {0};
            if (Ui.attempt(() -> n[0] = ctx.inventory.purgeOutOfStock())) { ctx.changed(); Toast.success("Removed " + n[0] + " products."); }
        }
    }
}
