package com.inventory.gui;

import com.inventory.model.*;
import com.inventory.util.Fmt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Purchase orders: create, auto-generate for low stock, receive (FIFO or specific) and cancel. */
public class PurchasePanel extends JPanel implements Refreshable {
    private final AppContext ctx;
    private final Ui.Model model = new Ui.Model(new String[]{"PO", "Supplier", "Created", "Lines", "Total", "Status"},
            new Class<?>[]{String.class, String.class, String.class, Integer.class, Ui.Val.class, Ui.Badge.class});
    private final JTable table = Ui.table(model, 70, 200, 100, 60, 110, 100);
    private final Ui.Btn receive = Ui.success("Receive"), cancel = Ui.secondary("Cancel order"), view = Ui.secondary("View");

    public PurchasePanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(8, 28, 20, 28));
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JPanel l = Ui.hbox(8, view, receive, cancel);
        Ui.Btn fifo = Ui.secondary("Receive next (FIFO)"), auto = Ui.secondary("Auto-generate for low stock"), add = Ui.primary("+  New purchase order");
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        r.setOpaque(false);
        r.add(fifo); r.add(auto); r.add(add);
        bar.add(l, BorderLayout.WEST);
        bar.add(r, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);
        add(Ui.tableCard(table), BorderLayout.CENTER);

        add.addActionListener(e -> newOrder());
        auto.addActionListener(e -> {
            List<PurchaseOrder> made = new ArrayList<>();
            if (Ui.attempt(() -> made.addAll(ctx.purchases.autoReorder()))) {
                ctx.changed();
                if (made.isEmpty()) Toast.info("Nothing to order \u2013 low-stock items are already on order or have no supplier.");
                else Toast.success("Created " + made.size() + " purchase order(s).");
            }
        });
        fifo.addActionListener(e -> {
            PurchaseOrder[] po = new PurchaseOrder[1];
            if (Ui.attempt(() -> po[0] = ctx.purchases.receiveNext())) { ctx.changed(); Toast.success(po[0].getPoNo() + " received \u2013 stock updated."); }
        });
        receive.addActionListener(e -> with(po -> {
            if (Ui.attempt(() -> ctx.purchases.receive(po.getPoNo()))) { ctx.changed(); Toast.success(po.getPoNo() + " received \u2013 stock updated."); }
        }));
        cancel.addActionListener(e -> with(po -> {
            if (Ui.confirm(this, "Cancel order", "Cancel <b>" + po.getPoNo() + "</b>?", "Cancel order")
                    && Ui.attempt(() -> ctx.purchases.cancel(po.getPoNo()))) { ctx.changed(); Toast.success(po.getPoNo() + " cancelled."); }
        }));
        view.addActionListener(e -> with(this::details));
        table.getSelectionModel().addListSelectionListener(e -> update());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) with(p -> details(p)); }
        });
        update();
        refresh();
    }

    private void update() {
        String id = Ui.selectedKey(table);
        boolean pending = false;
        if (id != null) try { pending = ctx.purchases.find(id).getStatus() == PurchaseOrder.Status.PENDING; } catch (RuntimeException ignored) {}
        view.setEnabled(id != null); receive.setEnabled(pending); cancel.setEnabled(pending);
    }

    private void with(java.util.function.Consumer<PurchaseOrder> a) {
        String id = Ui.selectedKey(table);
        if (id == null) { Toast.info("Select an order first."); return; }
        Ui.attempt(() -> a.accept(ctx.purchases.find(id)));
    }

    private static Ui.Badge badge(PurchaseOrder.Status s) {
        return switch (s) {
            case PENDING -> new Ui.Badge("Pending", Theme.WARN);
            case RECEIVED -> new Ui.Badge("Received", Theme.GOOD);
            case CANCELLED -> new Ui.Badge("Cancelled", Theme.MUTED);
        };
    }

    @Override public void refresh() {
        String keep = Ui.selectedKey(table);
        List<Object[]> rows = new ArrayList<>();
        List<PurchaseOrder> all = ctx.purchases.all();
        for (int i = all.size() - 1; i >= 0; i--) {
            PurchaseOrder p = all.get(i);
            rows.add(new Object[]{p.getPoNo(), ctx.suppliers.nameOf(p.getSupplierId()), p.getCreatedOn().toString(),
                    p.getLines().size(), Ui.money(p.total()), badge(p.getStatus())});
        }
        model.setRows(rows);
        Ui.reselect(table, keep);
        update();
    }

    private void details(PurchaseOrder po) {
        Ui.Model m = new Ui.Model(new String[]{"ID", "Product", "Qty", "Unit cost", "Total"},
                new Class<?>[]{String.class, String.class, Integer.class, Ui.Val.class, Ui.Val.class});
        List<Object[]> rows = new ArrayList<>();
        for (POLine l : po.getLines()) rows.add(new Object[]{l.getProductId(), l.getProductName(), l.getQty(), Ui.money(l.getUnitCost()), Ui.money(l.total())});
        m.setRows(rows);
        JScrollPane sp = Ui.scroll(Ui.table(m, 60, 220, 60, 90, 100));
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setPreferredSize(new Dimension(560, 220));
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.add(Ui.muted(ctx.suppliers.nameOf(po.getSupplierId()) + "  \u00B7  created " + po.getCreatedOn()
                + (po.getReceivedOn() != null ? "  \u00B7  received " + po.getReceivedOn() : "")), BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        p.add(Ui.label("Total  " + Fmt.money(po.total()), 15, Font.BOLD, Theme.TEXT), BorderLayout.SOUTH);
        Ui.formDialog(this, po.getPoNo(), p, null, () -> {});
    }

    private void newOrder() {
        List<Ui.Opt> sup = new ArrayList<>();
        for (Supplier s : ctx.suppliers.all()) sup.add(new Ui.Opt(s.getId(), s.getId() + " \u2013 " + s.getName()));
        if (sup.isEmpty()) { Toast.error("Add a supplier first."); return; }
        JComboBox<Ui.Opt> supplier = Ui.combo(sup);
        Ui.Model m = new Ui.Model(new String[]{"ID", "Product", "In stock", "Order qty"},
                new Class<?>[]{String.class, String.class, Integer.class, Integer.class}, 3);
        JTable t = Ui.table(m, 60, 220, 70, 80);
        Runnable load = () -> {
            Ui.Opt o = (Ui.Opt) supplier.getSelectedItem();
            List<Object[]> rows = new ArrayList<>();
            for (Product p : ctx.suppliers.productsOf(o.id()))
                rows.add(new Object[]{p.getId(), p.getName(), p.getQuantity(), p.isLowStock() ? p.suggestedReorderQty() : 0});
            m.setRows(rows);
        };
        supplier.addActionListener(e -> load.run());
        load.run();
        JScrollPane sp = Ui.scroll(t);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setPreferredSize(new Dimension(560, 240));
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(supplier, BorderLayout.NORTH);
        body.add(sp, BorderLayout.CENTER);
        body.add(Ui.muted("Edit the Order qty column. Low-stock items are pre-filled with a suggested quantity; 0 = skip."), BorderLayout.SOUTH);
        PurchaseOrder[] made = new PurchaseOrder[1];
        if (Ui.formDialog(this, "New purchase order", body, "Create order", () -> {
            if (t.isEditing()) t.getCellEditor().stopCellEditing();
            Map<String, Integer> lines = new LinkedHashMap<>();
            for (int i = 0; i < m.getRowCount(); i++) {
                int q = ((Number) m.getValueAt(i, 3)).intValue();
                if (q > 0) lines.put((String) m.getValueAt(i, 0), q);
            }
            made[0] = ctx.purchases.create(((Ui.Opt) supplier.getSelectedItem()).id(), lines);
        })) { ctx.changed(); Toast.success(made[0].getPoNo() + " created \u2013 " + Fmt.money(made[0].total())); }
    }
}
