package com.inventory.gui;

import com.inventory.model.Transaction;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Searchable, filterable audit trail. */
public class AuditPanel extends JPanel implements Refreshable {
    private final AppContext ctx;
    private final JTextField search = Ui.field("Search user, product or note\u2026", 24);
    private final JComboBox<Object> type = Ui.combo((Object[]) new Object[]{"All actions"});
    private final Ui.Model model = new Ui.Model(new String[]{"#", "Time", "User", "Action", "Product", "Qty", "Note"},
            new Class<?>[]{String.class, String.class, String.class, Ui.Badge.class, String.class, Ui.Val.class, String.class});
    private final JLabel count = Ui.muted(" ");
    private boolean loading;

    public AuditPanel(AppContext ctx) {
        this.ctx = ctx;
        for (Transaction.Type t : Transaction.Type.values()) type.addItem(t);
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(8, 28, 20, 28));
        add(Ui.hbox(10, search, type), BorderLayout.NORTH);
        add(Ui.tableCard(Ui.table(model, 45, 190, 80, 140, 80, 60, 300)), BorderLayout.CENTER);
        add(count, BorderLayout.SOUTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        type.addActionListener(e -> refresh());
        refresh();
    }

    private static Color color(Transaction.Type t) {
        return switch (t) {
            case SALE, PRODUCT_ADDED, PO_RECEIVED -> Theme.GOOD;
            case RESTOCK, PO_CREATED, SUPPLIER, USER -> Theme.INFO;
            case RETURN, ADJUSTMENT, PRICE_CHANGE, UNDO, PRODUCT_UPDATED -> Theme.WARN;
            case PRODUCT_DELETED, PO_CANCELLED -> Theme.BAD;
        };
    }

    @Override public void refresh() {
        String q = search.getText().trim().toLowerCase();
        Object sel = type.getSelectedItem();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");
        List<Object[]> rows = new ArrayList<>();
        List<Transaction> all = ctx.audit.recent(Integer.MAX_VALUE);
        for (Transaction t : all) {
            if (sel instanceof Transaction.Type st && t.getType() != st) continue;
            if (!q.isEmpty() && !(t.getUser().toLowerCase().contains(q) || t.getProductId().toLowerCase().contains(q)
                    || t.getNote().toLowerCase().contains(q))) continue;
            if (rows.size() >= 1000) break;
            int qc = t.getQtyChange();
            rows.add(new Object[]{String.valueOf(t.getId()), t.getTime().format(f), t.getUser(),
                    new Ui.Badge(t.getType().name().replace('_', ' '), color(t.getType())), t.getProductId(),
                    Ui.num(qc, qc == 0 ? "\u2013" : String.format("%+d", qc)), t.getNote()});
        }
        model.setRows(rows);
        count.setText(rows.size() + " of " + all.size() + " entries" + (rows.size() >= 1000 ? " (showing the newest 1000)" : ""));
    }
}
