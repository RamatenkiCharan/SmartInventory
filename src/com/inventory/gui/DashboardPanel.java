package com.inventory.gui;

import com.inventory.model.*;
import com.inventory.service.ReportService;
import com.inventory.util.Fmt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Overview: KPI cards, things that need attention, stock value by category, critical stock, top sellers. */
public class DashboardPanel extends JPanel implements Refreshable {
    private final AppContext ctx;
    private final Consumer<String> openProducts;   // jump to Products with a status filter
    private final Runnable openPurchasing;
    private final JPanel content = new ScrollContent();

    /** Content panel that always matches the viewport width (no sideways scrolling). */
    private static final class ScrollContent extends JPanel implements Scrollable {
        ScrollContent() { super(new GridBagLayout()); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport v && v.getHeight() > getPreferredSize().height;
        }
    }

    public DashboardPanel(AppContext ctx, Consumer<String> openProducts, Runnable openPurchasing) {
        this.ctx = ctx; this.openProducts = openProducts; this.openPurchasing = openPurchasing;
        setLayout(new BorderLayout());
        setBackground(Theme.BG);
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(8, 28, 24, 28));
        JScrollPane sp = Ui.scroll(content);
        sp.getViewport().setBackground(Theme.BG);
        sp.setBorder(BorderFactory.createEmptyBorder());
        add(sp, BorderLayout.CENTER);
        refresh();
    }

    private static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e7) return String.format("Rs.%.2f Cr", v / 1e7);
        if (a >= 1e5) return String.format("Rs.%.2f L", v / 1e5);
        return String.format("Rs.%,.0f", v);
    }

    private JPanel kpi(String label, String value, String sub, Color accent) {
        Ui.Card c = new Ui.Card(new BorderLayout(0, 6));
        JLabel l = Ui.label(label.toUpperCase(), 11, Font.BOLD, Theme.MUTED);
        JLabel v = Ui.label(value, 25, Font.BOLD, Theme.TEXT);
        JLabel s = Ui.label(sub, 12, Font.PLAIN, accent);
        JPanel dot = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g; g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(accent, 40)); g2.fillRoundRect(0, 0, 8, 8, 8, 8);
            }
        };
        dot.setOpaque(false);
        c.add(l, BorderLayout.NORTH); c.add(v, BorderLayout.CENTER); c.add(s, BorderLayout.SOUTH);
        return c;
    }

    private JPanel alertRow(String text, int n, Color color, Runnable go) {
        JPanel row = new JPanel(new BorderLayout(10, 0)) {
            boolean hover;
            { addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { go.run(); }
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (hover) { g2.setColor(Theme.ACCENT_SOFT); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); }
                g2.setColor(n == 0 ? Theme.GOOD : color);
                g2.fillOval(12, getHeight() / 2 - 5, 10, 10);
                g2.dispose();
            }
        };
        row.setOpaque(false);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setBorder(BorderFactory.createEmptyBorder(9, 34, 9, 12));
        row.add(Ui.label(text, 14, Font.PLAIN, Theme.TEXT), BorderLayout.CENTER);
        row.add(Ui.label(n == 0 ? "All clear" : String.valueOf(n), 14, Font.BOLD, n == 0 ? Theme.GOOD : color.darker()), BorderLayout.EAST);
        return row;
    }

    private void place(JComponent c, int x, int y, int w, double wx, double wy) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x; g.gridy = y; g.gridwidth = w; g.weightx = wx; g.weighty = wy; g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(0, x == 0 ? 0 : 8, 16, x + w >= 6 ? 0 : 8);
        content.add(c, g);
    }

    @Override public void refresh() {
        content.removeAll();
        LocalDate today = LocalDate.now();
        List<Product> all = ctx.inventory.all();
        boolean rep = ctx.can(Permission.REPORTS);
        int units = 0, low = 0, out = 0, exp = 0, soon = 0;
        double cost = 0, retail = 0;
        for (Product p : all) {
            units += p.getQuantity(); cost += p.costValue(); retail += p.retailValue();
            if (p.isLowStock()) low++;
            if (p.getQuantity() == 0) out++;
            if (p.isExpired(today)) exp++; else if (p.daysToExpiry(today) <= 30) soon++;
        }

        // ---- KPI row (6 columns grid)
        JPanel kpis = new JPanel(new GridLayout(1, 0, 16, 0));
        kpis.setOpaque(false);
        kpis.add(kpi("Products", String.valueOf(all.size()), ctx.inventory.categories().size() + " categories", Theme.INFO));
        kpis.add(kpi("Units in stock", String.format("%,d", units), "across all products", Theme.INFO));
        kpis.add(kpi("Stock value", compact(retail), "cost " + compact(cost), Theme.ACCENT));
        if (rep) {
            ReportService.Dashboard d = ctx.reports.dashboard();
            kpis.add(kpi("Revenue", compact(d.revenue()), d.invoices() + " paid invoices", Theme.GOOD));
            kpis.add(kpi("Profit", compact(d.profit()), d.revenue() > 0 ? String.format("%.1f%% margin", d.profit() / d.revenue() * 100) : "no sales yet", Theme.GOOD));
        }
        GridBagConstraints k = new GridBagConstraints();
        k.gridx = 0; k.gridy = 0; k.gridwidth = 2; k.weightx = 1; k.fill = GridBagConstraints.BOTH; k.insets = new Insets(0, 0, 16, 0);
        content.add(kpis, k);

        // ---- needs attention
        final int lowF = low, outF = out, expF = exp, soonF = soon;
        Ui.Card attn = new Ui.Card(new BorderLayout(0, 8));
        attn.add(Ui.label("Needs attention", 16, Font.BOLD, Theme.TEXT), BorderLayout.NORTH);
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 2));
        rows.setOpaque(false);
        rows.add(alertRow("Low stock products", lowF, Theme.WARN, () -> openProducts.accept("Low stock")));
        rows.add(alertRow("Out of stock", outF, Theme.BAD, () -> openProducts.accept("Out of stock")));
        rows.add(alertRow("Expired products", expF, Theme.BAD, () -> openProducts.accept("Expired")));
        rows.add(alertRow("Expiring in 30 days", soonF, Theme.WARN, () -> openProducts.accept("Expiring in 30 days")));
        if (ctx.can(Permission.PURCHASE)) rows.add(alertRow("Pending purchase orders", ctx.purchases.pending().size(), Theme.INFO, openPurchasing));
        attn.add(rows, BorderLayout.CENTER);

        // ---- value by category
        Ui.Card cat = new Ui.Card(new BorderLayout(0, 8));
        cat.add(Ui.label("Stock value by category (retail)", 16, Font.BOLD, Theme.TEXT), BorderLayout.NORTH);
        BarChart chart = new BarChart();
        List<String> labels = new ArrayList<>(); List<Double> vals = new ArrayList<>();
        Map<String, Double> byCat = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Product p : all) byCat.merge(p.getCategory(), p.retailValue(), Double::sum);
        byCat.forEach((c, v) -> { labels.add(c); vals.add(v); });
        chart.setData(labels, vals, Fmt::money, Theme.ACCENT);
        cat.add(chart, BorderLayout.CENTER);

        JPanel mid = new JPanel(new GridBagLayout());
        mid.setOpaque(false);
        GridBagConstraints m = new GridBagConstraints();
        m.fill = GridBagConstraints.BOTH; m.weighty = 1; m.gridy = 0;
        m.gridx = 0; m.weightx = 0.42; m.insets = new Insets(0, 0, 0, 8); mid.add(attn, m);
        m.gridx = 1; m.weightx = 0.58; m.insets = new Insets(0, 8, 0, 0); mid.add(cat, m);
        GridBagConstraints mg = new GridBagConstraints();
        mg.gridx = 0; mg.gridy = 1; mg.weightx = 1; mg.fill = GridBagConstraints.BOTH; mg.insets = new Insets(0, 0, 16, 0);
        content.add(mid, mg);

        // ---- critical stock + top sellers
        Ui.Card crit = new Ui.Card(new BorderLayout(0, 12));
        crit.add(Ui.label("Most critical stock", 16, Font.BOLD, Theme.TEXT), BorderLayout.NORTH);
        PriorityQueue<Product> pq = ctx.inventory.lowStockQueue();
        List<String> cl = new ArrayList<>(); List<Double> cv = new ArrayList<>();
        for (int i = 0; i < 6 && !pq.isEmpty(); i++) {
            Product p = pq.poll();
            cl.add(p.getName() + "  (reorder at " + p.getReorderLevel() + ")");
            cv.add((double) p.getQuantity());
        }
        crit.add(cl.isEmpty() ? Ui.label("Nothing is running low \u2013 nice.", 14, Font.PLAIN, Theme.GOOD)
                : wrapTop(BarChart.rankList(cl, cv, v -> (int) v + " left", Theme.WARN)), BorderLayout.CENTER);

        Ui.Card top = new Ui.Card(new BorderLayout(0, 12));
        top.add(Ui.label(rep ? "Top sellers" : "Expiring soon", 16, Font.BOLD, Theme.TEXT), BorderLayout.NORTH);
        List<String> tl = new ArrayList<>(); List<Double> tv = new ArrayList<>();
        if (rep) {
            for (Map.Entry<String, Integer> e : ctx.reports.topSelling(6)) { tl.add(e.getKey()); tv.add((double) e.getValue()); }
            top.add(tl.isEmpty() ? Ui.label("No sales yet.", 14, Font.PLAIN, Theme.MUTED)
                    : wrapTop(BarChart.rankList(tl, tv, v -> (int) v + " sold", Theme.GOOD)), BorderLayout.CENTER);
        } else {
            List<Product> soonList = ctx.inventory.expiringWithin(today, 30);
            for (Product p : soonList) { tl.add(p.getName()); tv.add((double) Math.max(0, p.daysToExpiry(today))); }
            top.add(tl.isEmpty() ? Ui.label("Nothing expires in the next 30 days.", 14, Font.PLAIN, Theme.GOOD)
                    : wrapTop(BarChart.rankList(tl, tv, v -> (int) v + " days", Theme.WARN)), BorderLayout.CENTER);
        }
        JPanel bot = new JPanel(new GridLayout(1, 2, 16, 0));
        bot.setOpaque(false);
        bot.add(crit); bot.add(top);
        GridBagConstraints bg = new GridBagConstraints();
        bg.gridx = 0; bg.gridy = 2; bg.weightx = 1; bg.weighty = 1; bg.fill = GridBagConstraints.BOTH;
        content.add(bot, bg);

        content.revalidate();
        content.repaint();
    }

    private static JComponent wrapTop(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c, BorderLayout.NORTH);
        return p;
    }
}
