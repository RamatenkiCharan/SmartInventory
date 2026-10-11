package com.inventory.gui;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/** Design tokens (colours, fonts) and global look-and-feel set-up. */
public final class Theme {
    private Theme() {}

    public static final Color BG            = new Color(0xF3F4F8);
    public static final Color SURFACE       = Color.WHITE;
    public static final Color ZEBRA         = new Color(0xFAFBFD);
    public static final Color SIDEBAR       = new Color(0x0F172A);
    public static final Color SIDEBAR_HOVER = new Color(0x1E293B);
    public static final Color SIDEBAR_TEXT  = new Color(0xCBD5E1);
    public static final Color ACCENT        = new Color(0x4F46E5);
    public static final Color ACCENT_HOVER  = new Color(0x4338CA);
    public static final Color ACCENT_SOFT   = new Color(0xEEF2FF);
    public static final Color TEXT          = new Color(0x0F172A);
    public static final Color MUTED         = new Color(0x64748B);
    public static final Color BORDER        = new Color(0xE2E8F0);
    public static final Color GOOD          = new Color(0x10B981);
    public static final Color WARN          = new Color(0xF59E0B);
    public static final Color BAD           = new Color(0xEF4444);
    public static final Color INFO          = new Color(0x0EA5E9);

    public static final String FAMILY = pickFamily();

    private static String pickFamily() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Roboto", "Noto Sans", "DejaVu Sans"})
            if (available.contains(f)) return f;
        return Font.SANS_SERIF;
    }

    public static Font font(int style, int size) { return new Font(FAMILY, style, size); }
    public static Font mono(int size)            { return new Font(Font.MONOSPACED, Font.PLAIN, size); }

    public static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    /** Call once, before creating any window. */
    public static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}

        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            if (k.toString().endsWith(".font") && UIManager.get(k) instanceof Font f)
                UIManager.put(k, new FontUIResource(font(f.getStyle() & ~Font.BOLD, 14)));
        }
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("ToolTip.background", new Color(0x1E293B));
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 10, 6, 10));
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("PopupMenu.background", SURFACE);
        UIManager.put("MenuItem.selectionBackground", ACCENT_SOFT);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("MenuItem.background", SURFACE);
        UIManager.put("Spinner.arrowButtonBorder", BorderFactory.createEmptyBorder());
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
    }

    /** Small generated application icon (indigo rounded square with a box). */
    public static Image appIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x6366F1), 64, 64, new Color(0x4338CA)));
        g.fillRoundRect(0, 0, 64, 64, 18, 18);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawRoundRect(15, 22, 34, 26, 4, 4);
        g.drawLine(15, 30, 49, 30);
        g.drawLine(28, 22, 28, 30);
        g.drawLine(36, 22, 36, 30);
        g.dispose();
        return img;
    }
}
