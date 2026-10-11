package com.inventory.service;

import com.inventory.model.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Analytics built with streams, TreeMap, HashSet and sorted Map entries. */
public class ReportService {
    public record Dashboard(int products, int units, double costValue, double retailValue,
                            int lowStock, int outOfStock, int expired, int invoices,
                            double revenue, double profit, int pendingOrders) {}

    public record CategoryStat(int products, int units, double costValue, double retailValue) {}

    private final DataStore store;
    private final AuthService auth;

    public ReportService(DataStore store, AuthService auth) { this.store = store; this.auth = auth; }

    private List<Invoice> paid() {
        return store.invoices.stream().filter(i -> i.getStatus() == Invoice.Status.PAID).collect(Collectors.toList());
    }

    public Dashboard dashboard() {
        auth.require(Permission.REPORTS);
        LocalDate today = LocalDate.now();
        Collection<Product> ps = store.products.values();
        List<Invoice> paid = paid();
        return new Dashboard(
                ps.size(),
                ps.stream().mapToInt(Product::getQuantity).sum(),
                ps.stream().mapToDouble(Product::costValue).sum(),
                ps.stream().mapToDouble(Product::retailValue).sum(),
                (int) ps.stream().filter(Product::isLowStock).count(),
                (int) ps.stream().filter(p -> p.getQuantity() == 0).count(),
                (int) ps.stream().filter(p -> p.isExpired(today)).count(),
                paid.size(),
                paid.stream().mapToDouble(Invoice::total).sum(),
                paid.stream().mapToDouble(Invoice::profit).sum(),
                store.pendingOrders.size());
    }

    /** Units sold per product, best sellers first. */
    public List<Map.Entry<String, Integer>> topSelling(int n) {
        auth.require(Permission.REPORTS);
        Map<String, Integer> units = new HashMap<>();
        for (Invoice inv : paid())
            for (InvoiceItem it : inv.getItems()) units.merge(it.getProductId() + " - " + it.getName(), it.getQty(), Integer::sum);
        return units.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n).collect(Collectors.toList());
    }

    /** Date -> {invoice count, revenue}, sorted by date (TreeMap). */
    public Map<LocalDate, double[]> dailySales() {
        auth.require(Permission.REPORTS);
        Map<LocalDate, double[]> byDay = new TreeMap<>();
        for (Invoice inv : paid()) {
            double[] v = byDay.computeIfAbsent(inv.getDateTime().toLocalDate(), d -> new double[2]);
            v[0] += 1;
            v[1] += inv.total();
        }
        return byDay;
    }

    public Map<String, CategoryStat> categorySummary() {
        auth.require(Permission.REPORTS);
        Map<String, CategoryStat> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, List<Product>> g = store.products.values().stream().collect(Collectors.groupingBy(Product::getCategory));
        g.forEach((cat, list) -> out.put(cat, new CategoryStat(list.size(),
                list.stream().mapToInt(Product::getQuantity).sum(),
                list.stream().mapToDouble(Product::costValue).sum(),
                list.stream().mapToDouble(Product::retailValue).sum())));
        return out;
    }

    /** Stock value (at cost) per supplier. */
    public Map<String, Double> stockValueBySupplier() {
        auth.require(Permission.REPORTS);
        Map<String, Double> out = new TreeMap<>();
        for (Product p : store.products.values()) {
            String name = p.getSupplierId() == null ? "(no supplier)"
                    : store.suppliers.containsKey(p.getSupplierId()) ? store.suppliers.get(p.getSupplierId()).getName() : p.getSupplierId();
            out.merge(name, p.costValue(), Double::sum);
        }
        return out;
    }

    /** Products that have stock but have never been sold (HashSet difference). */
    public List<Product> deadStock() {
        auth.require(Permission.REPORTS);
        Set<String> sold = new HashSet<>();
        for (Invoice inv : paid()) for (InvoiceItem it : inv.getItems()) sold.add(it.getProductId());
        return store.products.values().stream()
                .filter(p -> p.getQuantity() > 0 && !sold.contains(p.getId()))
                .collect(Collectors.toList());
    }

    /** Per-product profit contribution across all paid invoices, best first. */
    public List<Map.Entry<String, Double>> profitByProduct() {
        auth.require(Permission.REPORTS);
        Map<String, Double> profit = new HashMap<>();
        for (Invoice inv : paid())
            for (InvoiceItem it : inv.getItems()) profit.merge(it.getProductId() + " - " + it.getName(), it.lineProfit(), Double::sum);
        return profit.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
    }
}
