package com.inventory.gui;

import com.inventory.model.*;
import com.inventory.service.SalesService;
import com.inventory.util.Fmt;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PrinterException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Point of sale: pick products, build an order, apply discount/GST, print the receipt; plus invoice history. */
public class SalesPanel extends JPanel implements Refreshable {
    private final AppContext ctx;
    private final CardLayout cards = new CardLayout();
    private final JPanel stack = new JPanel(cards);

    // ---- new sale
    private final JTextField search = Ui.field("Search products\u2026", 18);
    private final Ui.Model prodModel = new Ui.Model(new String[]{"ID", "Product", "Price", "Stock", "Status"},
            new Class<?>[]{String.class, String.class, Ui.Val.class, Integer.class, Ui.Badge.class});
    private final JTable prodTable = Ui.table(prodModel, 70, 170, 90, 70, 120);
    private final JSpinner addQty = Ui.intSpinner(1, 1, 10_000);
    private final Map<String, Integer> cart = new LinkedHashMap<>();
    private final Ui.Model cartModel = new Ui.Model(new String[]{"ID", "Item", "Qty", "Price", "Total"},
            new Class<?>[]{String.class, String.class, Integer.class, Ui.Val.class, Ui.Val.class});
    private final JTable cartTable = Ui.table(cartModel, 50, 110, 55, 80, 90);
    private final JTextField customer = Ui.field("Walk-in customer", 14);
    private final JSpinner discount, tax;
    private final JLabel subL = Ui.label("", 14, Font.PLAIN, Theme.TEXT), discL = Ui.label("", 14, Font.PLAIN, Theme.TEXT),
            taxL = Ui.label("", 14, Font.PLAIN, Theme.TEXT), totalL = Ui.label("", 26, Font.BOLD, Theme.TEXT),
            cartCount = Ui.muted("Cart is empty");
    private final Ui.Btn checkout = Ui.primary("Checkout");

    // ---- invoices
    private final Ui.Model invModel = new Ui.Model(
            new String[]{"Invoice", "Date", "Customer", "Billed by", "Items", "Total", "Profit", "Status"},
            new Class<?>[]{String.class, String.class, String.class, String.class, Integer.class, Ui.Val.class, Ui.Val.class, Ui.Badge.class});
    private final JTable invTable = Ui.table(invModel, 80, 130, 140, 90, 50, 100, 100, 90);

    public SalesPanel(AppContext ctx) {
        this.ctx = ctx;
        boolean staff = ctx.auth.current().getRole() == Role.STAFF;
        discount = Ui.spinner(0, 0, staff ? SalesService.STAFF_MAX_DISCOUNT : 100, 1);
        tax = Ui.spinner(ctx.store.defaultTaxPercent, 0, 100, 0.5);

        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(8, 28, 20, 28));
        add(Ui.segmented(new String[]{"New sale", "Invoices"}, 0, i -> cards.show(stack, i == 0 ? "sale" : "inv")), BorderLayout.NORTH);
        stack.setOpaque(false);
        stack.add(buildSale(staff), "sale");
        stack.add(buildInvoices(), "inv");
        add(stack, BorderLayout.CENTER);
        refresh();
    }

    // ================================================================== new sale
    private JComponent buildSale(boolean staff) {
        // ---- left: product picker
        Ui.Card left = new Ui.Card(new BorderLayout(0, 12));
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.add(Ui.label("Products", 16, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        head.add(search, BorderLayout.CENTER);
        left.add(head, BorderLayout.NORTH);
        left.add(Ui.scroll(prodTable), BorderLayout.CENTER);
        Ui.Btn add = Ui.primary("Add to cart");
        add.addActionListener(e -> addSelected((Integer) addQty.getValue()));
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        foot.setOpaque(false);
        foot.add(Ui.muted("Quantity"));
        foot.add(addQty);
        foot.add(add);
        left.add(foot, BorderLayout.SOUTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { loadProducts(); }
            public void removeUpdate(DocumentEvent e) { loadProducts(); }
            public void changedUpdate(DocumentEvent e) { loadProducts(); }
        });
        prodTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) addSelected(1); }
        });

        // ---- right: order
        Ui.Card right = new Ui.Card(new BorderLayout(0, 12));
        right.setPreferredSize(new Dimension(410, 0));
        cartTable.putClientProperty("emptyText", "Cart is empty \u2013 double-click a product to add it");
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        JPanel t1 = new JPanel(new BorderLayout());
        t1.setOpaque(false);
        t1.add(Ui.label("Current order", 16, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        t1.add(cartCount, BorderLayout.EAST);
        top.add(t1, BorderLayout.NORTH);
        top.add(customer, BorderLayout.CENTER);
        right.add(top, BorderLayout.NORTH);
        right.add(Ui.scroll(cartTable), BorderLayout.CENTER);

        Ui.Btn minus = Ui.secondary("\u2212"), plus = Ui.secondary("+"), rem = Ui.secondary("Remove"), clear = Ui.ghost("Clear");
        minus.addActionListener(e -> changeQty(-1));
        plus.addActionListener(e -> changeQty(+1));
        rem.addActionListener(e -> { String id = Ui.selectedKey(cartTable); if (id != null) { cart.remove(id); recalc(); } });
        clear.addActionListener(e -> { cart.clear(); recalc(); });
        JPanel cartBtns = Ui.hbox(6, minus, plus, rem, clear);

        Ui.Form f = new Ui.Form();
        f.add2(staff ? "Discount % (max " + (int) SalesService.STAFF_MAX_DISCOUNT + ")" : "Discount %", discount, "Tax / GST %", tax);
        discount.addChangeListener(e -> recalc());
        tax.addChangeListener(e -> recalc());

        JPanel totals = new JPanel(new GridLayout(0, 2, 0, 6));
        totals.setOpaque(false);
        totals.add(Ui.muted("Subtotal"));       totals.add(right(subL));
        totals.add(Ui.muted("Discount"));       totals.add(right(discL));
        totals.add(Ui.muted("Tax / GST"));      totals.add(right(taxL));
        JPanel grand = new JPanel(new BorderLayout());
        grand.setOpaque(false);
        grand.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), BorderFactory.createEmptyBorder(10, 0, 0, 0)));
        grand.add(Ui.label("Total", 16, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        grand.add(right(totalL), BorderLayout.EAST);
        checkout.setFont(Theme.font(Font.BOLD, 15));
        checkout.setBorder(BorderFactory.createEmptyBorder(13, 16, 13, 16));
        checkout.addActionListener(e -> doCheckout());

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        Component[] parts = {cartBtns, Box.createVerticalStrut(12), f, totals, Box.createVerticalStrut(8), grand, Box.createVerticalStrut(12), checkout};
        for (Component c : parts) {
            ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            bottom.add(c);
        }
        checkout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        right.add(bottom, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(16, 0));
        root.setOpaque(false);
        root.add(left, BorderLayout.CENTER);
        root.add(right, BorderLayout.EAST);
        return root;
    }

    private static JComponent right(JLabel l) {
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    private void loadProducts() {
        String q = search.getText().trim().toLowerCase();
        String keep = Ui.selectedKey(prodTable);
        List<Object[]> rows = new ArrayList<>();
        for (Product p : ctx.inventory.all()) {
            if (!q.isEmpty() && !(p.getId().toLowerCase().contains(q) || p.getName().toLowerCase().contains(q)
                    || p.getCategory().toLowerCase().contains(q))) continue;
            rows.add(new Object[]{p.getId(), p.getName(), Ui.money(p.getPrice()), p.getQuantity(), ProductsPanel.badge(p)});
        }
        prodModel.setRows(rows);
        Ui.reselect(prodTable, keep);
    }

    private void addSelected(int qty) {
        String id = Ui.selectedKey(prodTable);
        if (id == null) { Toast.info("Select a product first."); return; }
        Ui.attempt(() -> {
            Product p = ctx.inventory.get(id);
            if (p.isExpired(LocalDate.now())) throw new IllegalStateException("'" + p.getName() + "' is expired and cannot be sold.");
            int already = cart.getOrDefault(id, 0);
            if (already + qty > p.getQuantity())
                throw new IllegalStateException("Only " + p.getQuantity() + " \u00D7 " + p.getName() + " in stock"
                        + (already > 0 ? " (" + already + " already in the cart)." : "."));
            cart.merge(id, qty, Integer::sum);
            recalc();
        });
    }

    private void changeQty(int delta) {
        String id = Ui.selectedKey(cartTable);
        if (id == null) { Toast.info("Select a cart line first."); return; }
        Ui.attempt(() -> {
            int now = cart.get(id) + delta;
            if (now <= 0) cart.remove(id);
            else {
                Product p = ctx.inventory.get(id);
                if (now > p.getQuantity()) throw new IllegalStateException("Only " + p.getQuantity() + " in stock.");
                cart.put(id, now);
            }
            recalc();
            Ui.reselect(cartTable, id);
        });
    }

    private void recalc() {
        List<Object[]> rows = new ArrayList<>();
        double sub = 0;
        int items = 0;
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            Product p = ctx.inventory.get(e.getKey());
            double line = p.getPrice() * e.getValue();
            sub += line; items += e.getValue();
            rows.add(new Object[]{p.getId(), p.getName(), e.getValue(), Ui.money(p.getPrice()), Ui.money(line)});
        }
        String keep = Ui.selectedKey(cartTable);
        cartModel.setRows(rows);
        Ui.reselect(cartTable, keep);
        double d = sub * ((Number) discount.getValue()).doubleValue() / 100.0;
        double t = (sub - d) * ((Number) tax.getValue()).doubleValue() / 100.0;
        subL.setText(Fmt.money(sub));
        discL.setText("\u2212 " + Fmt.money(d));
        taxL.setText(Fmt.money(t));
        totalL.setText(Fmt.money(sub - d + t));
        cartCount.setText(items == 0 ? "Cart is empty" : items + (items == 1 ? " item" : " items"));
        checkout.setEnabled(!cart.isEmpty());
    }

    private void doCheckout() {
        Invoice[] result = new Invoice[1];
        boolean ok = Ui.attempt(() -> result[0] = ctx.sales.checkout(customer.getText().trim(), new LinkedHashMap<>(cart),
                ((Number) discount.getValue()).doubleValue(), ((Number) tax.getValue()).doubleValue()));
        if (!ok) return;
        Invoice inv = result[0];
        try { ctx.sales.saveReceipt(inv); } catch (RuntimeException ignored) {}
        cart.clear();
        customer.setText("");
        discount.setValue(0.0);
        ctx.changed();
        Toast.success(inv.getInvoiceNo() + " created \u2013 " + Fmt.money(inv.total()));
        showReceipt(inv);
    }

    // ================================================================== invoices
    private JComponent buildInvoices() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        Ui.Btn view = Ui.secondary("View receipt"), voidBtn = Ui.danger("Void invoice");
        view.addActionListener(e -> withInvoice(this::showReceipt));
        voidBtn.addActionListener(e -> withInvoice(this::voidDialog));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bar.setOpaque(false);
        bar.add(view);
        if (ctx.can(Permission.STOCK_MANAGE)) bar.add(voidBtn);
        p.add(bar, BorderLayout.NORTH);
        p.add(Ui.tableCard(invTable), BorderLayout.CENTER);
        invTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) withInvoice(inv -> showReceipt(inv)); }
        });
        return p;
    }

    private void withInvoice(java.util.function.Consumer<Invoice> action) {
        String no = Ui.selectedKey(invTable);
        if (no == null) { Toast.info("Select an invoice first."); return; }
        Ui.attempt(() -> action.accept(ctx.sales.find(no)));
    }

    private void loadInvoices() {
        String keep = Ui.selectedKey(invTable);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        boolean profit = ctx.can(Permission.REPORTS);
        List<Object[]> rows = new ArrayList<>();
        for (Invoice i : ctx.sales.all())
            rows.add(new Object[]{i.getInvoiceNo(), i.getDateTime().format(f), i.getCustomer(), i.getSoldBy(),
                    i.getItems().stream().mapToInt(InvoiceItem::getQty).sum(), Ui.money(i.total()),
                    profit ? Ui.money(i.profit()) : Ui.num(0, "\u2013"),
                    new Ui.Badge(i.getStatus() == Invoice.Status.PAID ? "Paid" : "Voided",
                            i.getStatus() == Invoice.Status.PAID ? Theme.GOOD : Theme.BAD)});
        invModel.setRows(rows);
        Ui.reselect(invTable, keep);
    }

    private void voidDialog(Invoice inv) {
        JTextField reason = Ui.field("e.g. Customer returned the goods", 24);
        Ui.Form f = new Ui.Form();
        f.add("Invoice", Ui.label(inv.getInvoiceNo() + "  \u00B7  " + Fmt.money(inv.total()), 14, Font.BOLD, Theme.TEXT));
        f.add("Reason", reason);
        if (Ui.formDialog(this, "Void invoice", f, "Void and restock", () ->
                ctx.sales.voidInvoice(inv.getInvoiceNo(), reason.getText().isBlank() ? "No reason given" : reason.getText().trim()))) {
            ctx.changed();
            Toast.success(inv.getInvoiceNo() + " voided \u2013 items returned to stock.");
        }
    }

    private void showReceipt(Invoice inv) {
        JTextArea area = new JTextArea(inv.toReceipt(ctx.store.storeName));
        area.setEditable(false);
        area.setFont(Theme.mono(13));
        area.setBackground(new Color(0xFFFEF8));
        area.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JScrollPane sp = Ui.scroll(area);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setPreferredSize(new Dimension(600, 380));
        Ui.formDialog(this, "Receipt \u2013 " + inv.getInvoiceNo(), sp, "Print", "Close", () -> {
            try { area.print(); } catch (PrinterException e) { throw new IllegalStateException("Printing failed: " + e.getMessage()); }
        });
    }

    // ================================================================== refresh
    @Override public void refresh() {
        loadProducts();
        // drop cart lines whose product vanished or has less stock now
        cart.entrySet().removeIf(e -> {
            try { return ctx.inventory.get(e.getKey()).getQuantity() < e.getValue(); } catch (RuntimeException ex) { return true; }
        });
        recalc();
        loadInvoices();
    }
}
