package com.inventory.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/** Dependency-free vertical bar chart with gridlines and hover highlight. */
public class BarChart extends JComponent {
    private List<String> labels = new ArrayList<>();
    private List<Double> values = new ArrayList<>();
    private DoubleFunction<String> fmt = v -> String.format("%,.0f", v);
    private Color color = Theme.ACCENT;
    private int hover = -1;
    private String emptyText = "No data yet";

    public BarChart() {
        setPreferredSize(new Dimension(300, 240));
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hover = indexAt(e.getX()); repaint(); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
        });
    }

    public void setData(List<String> labels, List<Double> values, DoubleFunction<String> fmt, Color color) {
        this.labels = labels; this.values = values; this.fmt = fmt; this.color = color;
        repaint();
    }
    public void setEmptyText(String t) { emptyText = t; }

    private static final int L = 56, R = 12, T = 22, B = 30;

    private int indexAt(int x) {
        int n = values.size();
        if (n == 0) return -1;
        double slot = (getWidth() - L - R) / (double) n;
        int i = (int) ((x - L) / slot);
        return i >= 0 && i < n ? i : -1;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setFont(Theme.font(Font.PLAIN, 12));
        FontMetrics fm = g2.getFontMetrics();
        if (values.isEmpty()) {
            g2.setColor(Theme.MUTED);
            g2.drawString(emptyText, (w - fm.stringWidth(emptyText)) / 2, h / 2);
            g2.dispose();
            return;
        }
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) max = 1;
        double nice = niceMax(max);
        int plotH = h - T - B, plotW = w - L - R;
        for (int i = 0; i <= 4; i++) {
            int y = T + plotH - (int) (plotH * i / 4.0);
            g2.setColor(new Color(0xEEF1F6));
            g2.drawLine(L, y, w - R, y);
            g2.setColor(Theme.MUTED);
            String s = compact(nice * i / 4.0);
            g2.drawString(s, L - 8 - fm.stringWidth(s), y + 4);
        }
        int n = values.size();
        double slot = plotW / (double) n;
        int barW = (int) Math.min(56, slot * 0.58);
        for (int i = 0; i < n; i++) {
            int bh = (int) (plotH * values.get(i) / nice);
            int x = L + (int) (slot * i + (slot - barW) / 2);
            int y = T + plotH - bh;
            boolean hi = i == hover;
            g2.setPaint(new GradientPaint(0, y, hi ? color.darker() : color, 0, y + Math.max(bh, 1),
                    Theme.alpha(color, hi ? 220 : 150)));
            g2.fillRoundRect(x, y, barW, Math.max(bh, 2), 8, 8);
            g2.fillRect(x, y + Math.max(bh, 2) - 4, barW, 4);
            g2.setColor(hi ? Theme.TEXT : Theme.MUTED);
            String lab = labels.get(i);
            while (fm.stringWidth(lab) > slot - 4 && lab.length() > 3) lab = lab.substring(0, lab.length() - 2);
            if (!lab.equals(labels.get(i))) lab += "\u2026";
            g2.drawString(lab, x + barW / 2 - fm.stringWidth(lab) / 2, h - 10);
            if (hi) {
                String v = fmt.apply(values.get(i));
                int tw = fm.stringWidth(v) + 16;
                int tx = Math.max(2, Math.min(w - tw - 2, x + barW / 2 - tw / 2));
                int ty = Math.max(2, y - 28);
                g2.setColor(Theme.TEXT);
                g2.fillRoundRect(tx, ty, tw, 22, 8, 8);
                g2.setColor(Color.WHITE);
                g2.drawString(v, tx + 8, ty + 15);
            }
        }
        g2.dispose();
    }

    private static double niceMax(double v) {
        double p = Math.pow(10, Math.floor(Math.log10(v)));
        for (double m : new double[]{1, 2, 2.5, 5, 10}) if (v <= m * p) return m * p;
        return 10 * p;
    }
    private static String compact(double v) {
        if (v >= 10_000_000) return String.format("%.1fCr", v / 10_000_000);
        if (v >= 100_000)    return String.format("%.1fL", v / 100_000);
        if (v >= 1000)       return String.format("%.0fk", v / 1000);
        return String.format("%.0f", v);
    }

    /** Horizontal ranked bars: label on the left, bar, value on the right. */
    public static JPanel rankList(List<String> labels, List<Double> values, DoubleFunction<String> fmt, Color color) {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 10)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) max = 1;
        for (int i = 0; i < labels.size(); i++) {
            final double ratio = values.get(i) / max;
            final String val = fmt.apply(values.get(i));
            JPanel row = new JPanel(new BorderLayout(0, 4));
            row.setOpaque(false);
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(Ui.label(labels.get(i), 13, Font.PLAIN, Theme.TEXT), BorderLayout.WEST);
            top.add(Ui.label(val, 13, Font.BOLD, Theme.TEXT), BorderLayout.EAST);
            JComponent bar = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0xEEF1F6));
                    g2.fillRoundRect(0, 0, getWidth(), 8, 8, 8);
                    g2.setColor(color);
                    g2.fillRoundRect(0, 0, Math.max(8, (int) (getWidth() * ratio)), 8, 8, 8);
                    g2.dispose();
                }
                @Override public Dimension getPreferredSize() { return new Dimension(10, 8); }
            };
            row.add(top, BorderLayout.NORTH);
            row.add(bar, BorderLayout.CENTER);
            p.add(row);
        }
        return p;
    }
}
