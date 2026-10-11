package com.inventory.gui;

import com.inventory.model.Product;
import com.inventory.service.ReportService;
import com.inventory.util.Fmt;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Analytics: daily sales chart, best sellers, profit, category and supplier value, dead stock. */
public class ReportsPanel extends JPanel implements Refreshable {
    private final AppContext ctx;
    private final CardLayout cards = new CardLayout();
    private final JPanel stack = new JPanel(cards);
    private final BarChart daily = new BarChart();
    private final Ui.Model dailyM = new Ui.Model(new String[]{"Date", "Invoices", "Revenue"}, new Class<?>[]{String.class, Integer.class, Ui.Val.class});
    private final Ui.Model topM = new Ui.Model(new String[]{"Product", "Units sold"}, new Class<?>[]{String.class, Integer.class});
    private final Ui.Model profitM = new Ui.Model(new String[]{"Product", "Profit"}, new Class<?>[]{String.class, Ui.Val.class});
    private final Ui.Model catM = new Ui.Model(new String[]{"Category", "Products", "Units", "Cost value", "Retail value"},
            new Class<?>[]{String.class, Integer.class, Integer.class, Ui.Val.class, Ui.Val.class});
    private final Ui.Model supM = new Ui.Model(new String[]{"Supplier", "Stock value (cost)"}, new Class<?>[]{String.class, Ui.Val.class});
    private final Ui.Model deadM = new Ui.Model(new String[]{"ID", "Product", "Category", "Qty", "Value (cost)"},
            new Class<?>[]{String.class, String.class, String.class, Integer.class, Ui.Val.class});

    public ReportsPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(8, 28, 20, 28));
        String[] names = {"Sales by day", "Top sellers", "Profit", "Categories", "Suppliers", "Dead stock"};
        add(Ui.segmented(names, 0, i -> cards.show(stack, names[i])), BorderLayout.NORTH);
        stack.setOpaque(false);

        JPanel d = new JPanel(new BorderLayout(0, 14));
        d.setOpaque(false);
        Ui.Card chart = new Ui.Card();
        chart.setPreferredSize(new Dimension(100, 250));
        chart.add(Ui.label("Revenue per day", 15, Font.BOLD, Theme.TEXT), BorderLayout.NORTH);
        chart.add(daily, BorderLayout.CENTER);
        d.add(chart, BorderLayout.NORTH);
        d.add(Ui.tableCard(Ui.table(dailyM, 140, 80, 140)), BorderLayout.CENTER);
        stack.add(d, names[0]);
        stack.add(Ui.tableCard(Ui.table(topM, 320, 100)), names[1]);
        stack.add(Ui.tableCard(Ui.table(profitM, 320, 120)), names[2]);
        stack.add(Ui.tableCard(Ui.table(catM, 160, 80, 80, 120, 120)), names[3]);
        stack.add(Ui.tableCard(Ui.table(supM, 260, 140)), names[4]);
        stack.add(Ui.tableCard(Ui.table(deadM, 70, 220, 120, 70, 120)), names[5]);
        add(stack, BorderLayout.CENTER);
        refresh();
    }

    @Override public void refresh() {
        ReportService r = ctx.reports;
        List<Object[]> rows = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM");
        for (Map.Entry<LocalDate, double[]> e : r.dailySales().entrySet()) {
            rows.add(0, new Object[]{e.getKey().toString(), (int) e.getValue()[0], Ui.money(e.getValue()[1])});
            labels.add(e.getKey().format(f)); vals.add(e.getValue()[1]);
        }
        dailyM.setRows(rows);
        daily.setEmptyText("No sales yet \u2013 make a sale to see revenue here");
        daily.setData(labels, vals, Fmt::money, Theme.ACCENT);

        rows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : r.topSelling(50)) rows.add(new Object[]{e.getKey(), e.getValue()});
        topM.setRows(rows);
        rows = new ArrayList<>();
        for (Map.Entry<String, Double> e : r.profitByProduct()) rows.add(new Object[]{e.getKey(), Ui.money(e.getValue())});
        profitM.setRows(rows);
        rows = new ArrayList<>();
        final List<Object[]> cr = new ArrayList<>();
        r.categorySummary().forEach((c, s) -> cr.add(new Object[]{c, s.products(), s.units(), Ui.money(s.costValue()), Ui.money(s.retailValue())}));
        catM.setRows(cr);
        final List<Object[]> sr = new ArrayList<>();
        r.stockValueBySupplier().forEach((n, v) -> sr.add(new Object[]{n, Ui.money(v)}));
        supM.setRows(sr);
        rows = new ArrayList<>();
        for (Product p : r.deadStock()) rows.add(new Object[]{p.getId(), p.getName(), p.getCategory(), p.getQuantity(), Ui.money(p.costValue())});
        deadM.setRows(rows);
    }
}
