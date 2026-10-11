package com.inventory.gui;

import com.inventory.model.*;
import com.inventory.util.Fmt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Supplier directory with add / edit / delete and a view of the products each supplier provides. */
public class SuppliersPanel extends JPanel implements Refreshable {
    private final AppContext ctx;
    private final Ui.Model model = new Ui.Model(new String[]{"ID", "Supplier", "Phone", "Email", "City", "Products"},
            new Class<?>[]{String.class, String.class, String.class, String.class, String.class, Integer.class});
    private final JTable table = Ui.table(model, 60, 200, 120, 220, 110, 70);
    private final Ui.Btn products = Ui.secondary("View products"), edit = Ui.secondary("Edit"), delete = Ui.secondary("Delete");

    public SuppliersPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(8, 28, 20, 28));
        JPanel bar = Ui.hbox(8, products);
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        wrap.setOpaque(false);
        wrap.add(products);
        if (ctx.can(Permission.SUPPLIER_MANAGE)) {
            wrap.add(edit); wrap.add(delete);
            Ui.Btn add = Ui.primary("+  Add supplier");
            add.addActionListener(e -> dialog(null));
            wrap.add(add);
        }
        add(wrap, BorderLayout.NORTH);
        add(Ui.tableCard(table), BorderLayout.CENTER);
        products.addActionListener(e -> with(this::showProducts));
        edit.addActionListener(e -> with(this::dialog));
        delete.addActionListener(e -> with(this::deleteSupplier));
        table.getSelectionModel().addListSelectionListener(e -> update());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) with(s -> showProducts(s)); }
        });
        update();
        refresh();
    }

    private void update() { boolean h = table.getSelectedRow() >= 0; products.setEnabled(h); edit.setEnabled(h); delete.setEnabled(h); }

    private void with(java.util.function.Consumer<Supplier> a) {
        String id = Ui.selectedKey(table);
        if (id == null) { Toast.info("Select a supplier first."); return; }
        Ui.attempt(() -> a.accept(ctx.suppliers.get(id)));
    }

    @Override public void refresh() {
        String keep = Ui.selectedKey(table);
        List<Object[]> rows = new ArrayList<>();
        for (Supplier s : ctx.suppliers.all())
            rows.add(new Object[]{s.getId(), s.getName(), nz(s.getPhone()), nz(s.getEmail()), nz(s.getCity()), ctx.suppliers.productsOf(s.getId()).size()});
        model.setRows(rows);
        Ui.reselect(table, keep);
    }
    private static String nz(String s) { return s == null || s.isBlank() ? "\u2013" : s; }

    private void dialog(Supplier ex) {
        boolean edit = ex != null;
        JTextField id = Ui.field("e.g. S004", 8), name = Ui.field("Company name", 16), phone = Ui.field("Phone", 10),
                email = Ui.field("name@company.com", 16), city = Ui.field("City", 10);
        if (edit) {
            id.setText(ex.getId()); id.setEnabled(false); name.setText(ex.getName());
            phone.setText(ex.getPhone() == null ? "" : ex.getPhone());
            email.setText(ex.getEmail() == null ? "" : ex.getEmail());
            city.setText(ex.getCity() == null ? "" : ex.getCity());
        }
        Ui.Form f = new Ui.Form();
        f.add2("Supplier ID", id, "Name", name);
        f.add2("Phone", phone, "Email", email);
        f.add("City", city);
        if (Ui.formDialog(this, edit ? "Edit supplier" : "Add supplier", f, edit ? "Save changes" : "Add supplier", () -> {
            String em = email.getText().trim();
            if (!em.isEmpty() && !em.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("Please enter a valid e-mail address.");
            if (!edit) ctx.suppliers.add(new Supplier(id.getText(), name.getText(), phone.getText().trim(), em, city.getText().trim()));
            else {
                ctx.suppliers.edit(ex.getId(), "name", name.getText());
                ctx.suppliers.edit(ex.getId(), "phone", phone.getText().trim());
                ctx.suppliers.edit(ex.getId(), "email", em);
                ctx.suppliers.edit(ex.getId(), "city", city.getText().trim());
            }
        })) { ctx.changed(); Toast.success(edit ? "Supplier updated." : "Supplier added."); }
    }

    private void deleteSupplier(Supplier s) {
        if (Ui.confirm(this, "Delete supplier", "Delete <b>" + s.getName() + "</b>?", "Delete"))
            if (Ui.attempt(() -> ctx.suppliers.delete(s.getId()))) { ctx.changed(); Toast.success("Supplier deleted."); }
    }

    private void showProducts(Supplier s) {
        Ui.Model m = new Ui.Model(new String[]{"ID", "Product", "Qty", "Cost"},
                new Class<?>[]{String.class, String.class, Integer.class, Ui.Val.class});
        List<Object[]> rows = new ArrayList<>();
        for (Product p : ctx.suppliers.productsOf(s.getId())) rows.add(new Object[]{p.getId(), p.getName(), p.getQuantity(), Ui.money(p.getCostPrice())});
        m.setRows(rows);
        JScrollPane sp = Ui.scroll(Ui.table(m, 60, 220, 60, 90));
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setPreferredSize(new Dimension(520, 260));
        Ui.formDialog(this, "Products from " + s.getName(), sp, null, () -> {});
    }
}
