package com.inventory.gui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Small, self-dismissing notifications in the bottom-right corner of the main window. */
public final class Toast extends JComponent {
    public enum Type { SUCCESS, ERROR, INFO }

    private static JFrame host;
    private static final List<Toast> active = new ArrayList<>();

    private final String text;
    private final Color accent;
    private float alpha = 1f;

    public static void setHost(JFrame frame) { host = frame; }
    public static void success(String m) { show(m, Type.SUCCESS); }
    public static void error(String m)   { show(m, Type.ERROR); }
    public static void info(String m)    { show(m, Type.INFO); }

    public static void show(String message, Type type) {
        if (host == null) { System.out.println(message); return; }
        SwingUtilities.invokeLater(() -> new Toast(message, type).display());
    }

    private Toast(String text, Type type) {
        this.text = text;
        this.accent = type == Type.SUCCESS ? Theme.GOOD : type == Type.ERROR ? Theme.BAD : Theme.INFO;
        setFont(Theme.font(Font.PLAIN, 14));
        FontMetrics fm = getFontMetrics(getFont());
        int w = Math.min(520, fm.stringWidth(text) + 70);
        setSize(w, 48);
    }

    private void display() {
        JLayeredPane lp = host.getRootPane().getLayeredPane();
        active.add(this);
        reposition(lp);
        lp.add(this, JLayeredPane.POPUP_LAYER);
        lp.repaint();
        Timer hold = new Timer(type() == Type.ERROR ? 4200 : 2600, null);
        hold.setRepeats(false);
        hold.addActionListener(e -> fade(lp));
        hold.start();
    }

    private Type type() { return accent == Theme.BAD ? Type.ERROR : Type.INFO; }

    private void reposition(JLayeredPane lp) {
        int idx = active.indexOf(this);
        int y = lp.getHeight() - 24 - (idx + 1) * 56;
        setLocation(lp.getWidth() - getWidth() - 24, y);
    }

    private void fade(JLayeredPane lp) {
        Timer t = new Timer(30, null);
        t.addActionListener(e -> {
            alpha -= 0.12f;
            if (alpha <= 0) {
                t.stop();
                lp.remove(this);
                active.remove(this);
                for (Toast o : active) o.reposition(lp);
                lp.repaint();
            } else repaint();
        });
        t.start();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        g2.setColor(new Color(15, 23, 42, 40));
        g2.fillRoundRect(2, 4, getWidth() - 4, getHeight() - 4, 14, 14);
        g2.setColor(new Color(0x0F172A));
        g2.fillRoundRect(0, 0, getWidth() - 2, getHeight() - 4, 14, 14);
        g2.setColor(accent);
        g2.fillOval(16, (getHeight() - 4) / 2 - 5, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String t = text;
        int max = getWidth() - 56;
        while (fm.stringWidth(t) > max && t.length() > 4) t = t.substring(0, t.length() - 2);
        if (!t.equals(text)) t += "\u2026";
        g2.drawString(t, 36, (getHeight() - 4 - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
