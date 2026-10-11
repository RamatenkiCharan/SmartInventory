package com.inventory.gui;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.*;
import java.time.DateTimeException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Reusable, consistently styled Swing building blocks. */
public final class Ui {
    private Ui() {}

    // ------------------------------------------------------------------ value types for tables
    /** A number with a display text; sorts numerically, renders right-aligned. */
    public static final class Val implements Comparable<Val> {
        public final double v; public final String text;
        public Val(double v, String text) { this.v = v; this.text = text; }
        @Override public int compareTo(Val o) { return Double.compare(v, o.v); }
        @Override public String toString() { return text; }
    }
    public static Val money(double d) { return new Val(d, d == Math.rint(d) ? String.format("Rs.%,.0f", d) : com.inventory.util.Fmt.money(d)); }
    public static Val pct(double d)   { return new Val(d, String.format("%.1f%%", d)); }
    public static Val num(double d, String text) { return new Val(d, text); }

    /** Coloured status pill shown inside a table cell. */
    public static final class Badge implements Comparable<Badge> {
        public final String text; public final Color color;
        public Badge(String text, Color color) { this.text = text; this.color = color; }
        @Override public int compareTo(Badge o) { return text.compareTo(o.text); }
        @Override public String toString() { return text; }
    }

    /** Combo-box item with an id and a label. */
    public record Opt(String id, String label) { @Override public String toString() { return label; } }

    // ------------------------------------------------------------------ labels
    public static JLabel label(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }
    public static JLabel title(String text)  { return label(text, 18, Font.BOLD, Theme.TEXT); }
    public static JLabel muted(String text)  { return label(text, 13, Font.PLAIN, Theme.MUTED); }

    // ------------------------------------------------------------------ card
    public static class Card extends JPanel {
        public Card(LayoutManager lm) {
            super(lm);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(16, 18, 19, 18));
        }
        public Card() { this(new BorderLayout()); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight() - 3;
            g2.setColor(new Color(15, 23, 42, 9));
            g2.fillRoundRect(0, 2, w - 1, h, 16, 16);
            g2.setColor(Theme.SURFACE);
            g2.fillRoundRect(0, 0, w - 1, h, 16, 16);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h, 16, 16);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ buttons
    public enum Kind { PRIMARY, SECONDARY, DANGER, GHOST, SUCCESS }

    public static class Btn extends JButton {
        private final Kind kind;
        private boolean hover, press;
        public Btn(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setFont(Theme.font(Font.BOLD, 13));
            setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; press = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { press = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e){ press = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color bg, fg, line = null;
            boolean on = isEnabled();
            switch (kind) {
                case PRIMARY -> { bg = press ? Theme.ACCENT_HOVER.darker() : hover ? Theme.ACCENT_HOVER : Theme.ACCENT; fg = Color.WHITE; }
                case DANGER  -> { bg = hover ? Theme.BAD.darker() : Theme.BAD; fg = Color.WHITE; }
                case SUCCESS -> { bg = hover ? Theme.GOOD.darker() : Theme.GOOD; fg = Color.WHITE; }
                case GHOST   -> { bg = hover ? Theme.ACCENT_SOFT : Theme.alpha(Color.WHITE, 0); fg = Theme.TEXT; }
                default      -> { bg = hover ? new Color(0xF8FAFC) : Color.WHITE; fg = Theme.TEXT; line = Theme.BORDER; }
            }
            if (!on) { bg = new Color(0xF1F5F9); fg = new Color(0x94A3B8); line = null; }
            int h = getHeight(), w = getWidth();
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, 12, 12);
            if (line != null) { g2.setColor(line); g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12); }
            g2.setFont(getFont());
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
    public static Btn button(String text, Kind kind) { return new Btn(text, kind); }
    public static Btn primary(String text)   { return new Btn(text, Kind.PRIMARY); }
    public static Btn secondary(String text) { return new Btn(text, Kind.SECONDARY); }
    public static Btn danger(String text)    { return new Btn(text, Kind.DANGER); }
    public static Btn ghost(String text)     { return new Btn(text, Kind.GHOST); }
    public static Btn success(String text)   { return new Btn(text, Kind.SUCCESS); }

    /** Segmented / pill selector. */
    public static JPanel segmented(String[] names, int selected, Consumer<Integer> onSelect) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        ButtonGroup grp = new ButtonGroup();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            JToggleButton b = new JToggleButton(names[i]) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    boolean sel = isSelected();
                    if (sel) { g2.setColor(Theme.ACCENT); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); }
                    else if (getModel().isRollover()) { g2.setColor(Theme.ACCENT_SOFT); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); }
                    g2.setFont(getFont());
                    g2.setColor(sel ? Color.WHITE : Theme.MUTED);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    g2.dispose();
                }
            };
            b.setFont(Theme.font(Font.BOLD, 13));
            b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false); b.setRolloverEnabled(true);
            b.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setSelected(i == selected);
            b.addActionListener(e -> onSelect.accept(idx));
            grp.add(b);
            p.add(b);
        }
        return p;
    }

    // ------------------------------------------------------------------ inputs
    static class FieldBorder extends AbstractBorder {
        private final Insets in;
        FieldBorder(Insets in) { this.in = in; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            boolean focus = owner != null && SwingUtilities.isDescendingFrom(owner, c);
            g2.setColor(focus ? Theme.ACCENT : new Color(0xCBD5E1));
            g2.setStroke(new BasicStroke(focus ? 1.8f : 1f));
            g2.drawRoundRect(x, y, w - 1, h - 1, 10, 10);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return (Insets) in.clone(); }
        @Override public Insets getBorderInsets(Component c, Insets i) { i.set(in.top, in.left, in.bottom, in.right); return i; }
    }

    public static class HintField extends JTextField {
        private final String hint;
        public HintField(String hint, int cols) { super(cols); this.hint = hint; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && hint != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(new Color(0x94A3B8));
                g2.setFont(getFont());
                Insets in = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, in.left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }

    private static void styleText(JTextField f) {
        f.setFont(Theme.font(Font.PLAIN, 14));
        f.setForeground(Theme.TEXT);
        f.setBackground(Color.WHITE);
        f.setCaretColor(Theme.ACCENT);
        f.setSelectionColor(Theme.alpha(Theme.ACCENT, 70));
        f.setBorder(new FieldBorder(new Insets(8, 12, 8, 12)));
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { f.repaint(); }
            @Override public void focusLost(FocusEvent e)   { f.repaint(); }
        });
    }
    public static JTextField field(String hint, int cols) { HintField f = new HintField(hint, cols); styleText(f); return f; }
    public static JTextField field(int cols)              { return field(null, cols); }
    public static JPasswordField password(int cols) {
        JPasswordField f = new JPasswordField(cols); styleText(f); f.setEchoChar('\u2022'); return f;
    }

    public static <T> JComboBox<T> combo(Collection<? extends T> items) {
        JComboBox<T> c = new JComboBox<>();
        for (T t : items) c.addItem(t);
        c.setUI(new BasicComboBoxUI() {
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle r, boolean hasFocus) {
                g.setColor(Color.WHITE); g.fillRect(r.x, r.y, r.width, r.height);
            }
            @Override protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(Theme.MUTED);
                        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int cx = getWidth() / 2, cy = getHeight() / 2;
                        g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                        g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                        g2.dispose();
                    }
                };
                b.setContentAreaFilled(false); b.setBorder(BorderFactory.createEmptyBorder()); b.setFocusable(false);
                b.setPreferredSize(new Dimension(28, 28));
                return b;
            }
        });
        c.setFont(Theme.font(Font.PLAIN, 14));
        c.setBackground(Color.WHITE);
        c.setBorder(new FieldBorder(new Insets(3, 8, 3, 2)));
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                super.getListCellRendererComponent(l, v, i, sel, f);
                setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
                setBackground(sel ? Theme.ACCENT_SOFT : Color.WHITE);
                setForeground(Theme.TEXT);
                return this;
            }
        });
        return c;
    }
    @SafeVarargs public static <T> JComboBox<T> combo(T... items) { return combo(java.util.List.of(items)); }

    public static JSpinner spinner(double value, double min, double max, double step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        s.setFont(Theme.font(Font.PLAIN, 14));
        s.setBorder(new FieldBorder(new Insets(3, 6, 3, 2)));
        JTextField tf = ((JSpinner.DefaultEditor) s.getEditor()).getTextField();
        tf.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        tf.setFont(Theme.font(Font.PLAIN, 14));
        return s;
    }
    public static JSpinner intSpinner(int value, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        s.setFont(Theme.font(Font.PLAIN, 14));
        s.setBorder(new FieldBorder(new Insets(3, 6, 3, 2)));
        JTextField tf = ((JSpinner.DefaultEditor) s.getEditor()).getTextField();
        tf.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        tf.setFont(Theme.font(Font.PLAIN, 14));
        return s;
    }

    // ------------------------------------------------------------------ tables
    public static class Model extends DefaultTableModel {
        private final Class<?>[] types;
        private final Set<Integer> editable = new HashSet<>();
        public Model(String[] cols, Class<?>[] types, int... editableCols) {
            super(cols, 0);
            this.types = types;
            for (int c : editableCols) editable.add(c);
        }
        @Override public boolean isCellEditable(int r, int c) { return editable.contains(c); }
        @Override public Class<?> getColumnClass(int c) { return types[c]; }
        public void setRows(Collection<Object[]> rows) {
            setRowCount(0);
            for (Object[] r : rows) addRow(r);
        }
    }

    private static final class BadgeCell extends JPanel {
        Badge badge; Color bg = Color.WHITE;
        BadgeCell() { setOpaque(true); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (badge != null) {
                g2.setFont(Theme.font(Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(badge.text) + 20, h = 22, y = (getHeight() - h) / 2;
                g2.setColor(Theme.alpha(badge.color, 34));
                g2.fillRoundRect(12, y, w, h, h, h);
                g2.setColor(badge.color.darker());
                g2.drawString(badge.text, 22, y + (h - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    private static final class CellRenderer extends DefaultTableCellRenderer {
        private final BadgeCell badgeCell = new BadgeCell();
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            Color bg = sel ? Theme.ACCENT_SOFT : (row % 2 == 0 ? Theme.SURFACE : Theme.ZEBRA);
            if (v instanceof Badge b) { badgeCell.badge = b; badgeCell.bg = bg; return badgeCell; }
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setBackground(bg);
            setForeground(Theme.TEXT);
            setFont(Theme.font(Font.PLAIN, 14));
            setHorizontalAlignment(v instanceof Val || v instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            return this;
        }
    }

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v == null ? "" : v.toString().toUpperCase(), false, false, row, col);
            Class<?> c = t.getColumnClass(col);
            setHorizontalAlignment(Number.class.isAssignableFrom(c) || c == Val.class ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setFont(Theme.font(Font.BOLD, 11));
            setForeground(Theme.MUTED);
            setBackground(new Color(0xF8FAFC));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(0, 12, 0, 12)));
            return this;
        }
    }

    public static JTable table(Model model, int... widths) {
        JTable t = new JTable(model) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Object hint = getClientProperty("emptyText");
                if (getRowCount() == 0 && hint != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(Theme.font(Font.PLAIN, 14));
                    g2.setColor(new Color(0x94A3B8));
                    FontMetrics fm = g2.getFontMetrics();
                    String txt = hint.toString();
                    g2.drawString(txt, Math.max(8, (getWidth() - fm.stringWidth(txt)) / 2), 60);
                    g2.dispose();
                }
            }
        };
        t.putClientProperty("emptyText", "Nothing to show yet");
        t.setRowHeight(40);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setSelectionBackground(Theme.ACCENT_SOFT);
        t.setSelectionForeground(Theme.TEXT);
        t.setBackground(Theme.SURFACE);
        t.setFocusable(true);
        t.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        CellRenderer r = new CellRenderer();
        t.setDefaultRenderer(Object.class, r);
        t.setDefaultRenderer(Number.class, r);
        JTableHeader h = t.getTableHeader();
        h.setDefaultRenderer(new HeaderRenderer());
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(100, 38));
        for (int i = 0; i < widths.length && i < t.getColumnCount(); i++) t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        return t;
    }

    /** Value in the first column of the selected row, or null. */
    public static String selectedKey(JTable t) {
        int r = t.getSelectedRow();
        return r < 0 ? null : String.valueOf(t.getModel().getValueAt(t.convertRowIndexToModel(r), 0));
    }

    /** Re-select the row whose first column equals key (after a refresh). */
    public static void reselect(JTable t, String key) {
        if (key == null) return;
        for (int i = 0; i < t.getRowCount(); i++)
            if (key.equals(String.valueOf(t.getValueAt(i, 0)))) { t.setRowSelectionInterval(i, i); return; }
    }

    private static final class SlimScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = new Color(0xCBD5E1); trackColor = Color.WHITE; }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? new Color(0x94A3B8) : thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new SlimScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 12));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    /** A table inside a rounded card (no inner padding, clipped corners look). */
    public static JPanel tableCard(JTable t) {
        Card card = new Card();
        card.setBorder(BorderFactory.createEmptyBorder(1, 1, 4, 1));
        JScrollPane sp = scroll(t);
        sp.setBorder(BorderFactory.createEmptyBorder());
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------ forms and dialogs
    public static class Form extends JPanel {
        private int row = 0;
        public Form() { super(new GridBagLayout()); setOpaque(false); }
        private JPanel cell(String label, JComponent c) {
            JPanel p = new JPanel(new BorderLayout(0, 5));
            p.setOpaque(false);
            p.add(label(label, 12, Font.BOLD, Theme.MUTED), BorderLayout.NORTH);
            p.add(c, BorderLayout.CENTER);
            return p;
        }
        public Form add(String label, JComponent c) { return add2(label, c, null, null); }
        public Form add2(String l1, JComponent c1, String l2, JComponent c2) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = row++; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(0, 0, 14, 0);
            g.gridx = 0; g.gridwidth = c2 == null ? 2 : 1;
            if (c2 != null) g.insets = new Insets(0, 0, 14, 7);
            super.add(cell(l1, c1), g);
            if (c2 != null) {
                g.gridx = 1; g.gridwidth = 1; g.insets = new Insets(0, 7, 14, 0);
                super.add(cell(l2, c2), g);
            }
            return this;
        }
    }

    public static String friendly(Throwable e) {
        if (e instanceof NumberFormatException) return "Please enter a valid number.";
        if (e instanceof java.time.format.DateTimeParseException) return "Use the date format yyyy-MM-dd (e.g. 2026-12-31).";
        if (e instanceof DateTimeException) return "That date is not valid.";
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    /** Runs an action; any business error is shown as a toast. Returns true on success. */
    public static boolean attempt(Runnable r) {
        try { r.run(); return true; }
        catch (RuntimeException e) { Toast.error(friendly(e)); return false; }
    }

    /**
     * Modal dialog with a title, body, error line and Cancel/OK buttons.
     * {@code submit} may throw; the message is shown inline and the dialog stays open.
     */
    public static boolean formDialog(Component owner, String title, JComponent body, String okText, Runnable submit) {
        return formDialog(owner, title, body, okText, "Cancel", submit);
    }

    public static boolean formDialog(Component owner, String title, JComponent body, String okText, String cancelText, Runnable submit) {
        Window w = owner instanceof Window ww ? ww : SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(w, title, Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] ok = {false};

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.SURFACE);
        JLabel head = title(title);
        head.setBorder(BorderFactory.createEmptyBorder(22, 26, 6, 26));
        root.add(head, BorderLayout.NORTH);

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.setBorder(BorderFactory.createEmptyBorder(8, 26, 4, 26));
        bodyWrap.add(body, BorderLayout.CENTER);
        root.add(bodyWrap, BorderLayout.CENTER);

        JLabel err = label(" ", 13, Font.PLAIN, Theme.BAD);
        Btn cancel = secondary(cancelText);
        Btn okBtn = primary(okText);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        if (okText != null) { buttons.add(cancel); buttons.add(okBtn); } else { Btn close = primary("Close"); close.addActionListener(e -> d.dispose()); buttons.add(close); }
        JPanel foot = new JPanel(new BorderLayout(12, 0));
        foot.setBackground(new Color(0xF8FAFC));
        foot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 26, 14, 26)));
        foot.add(err, BorderLayout.CENTER);
        foot.add(buttons, BorderLayout.EAST);
        root.add(foot, BorderLayout.SOUTH);

        cancel.addActionListener(e -> d.dispose());
        okBtn.addActionListener(e -> {
            try { submit.run(); ok[0] = true; d.dispose(); }
            catch (RuntimeException ex) { err.setText(friendly(ex)); d.pack(); }
        });
        if (okText != null) d.getRootPane().setDefaultButton(okBtn);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        d.setContentPane(root);
        d.pack();
        d.setMinimumSize(new Dimension(Math.max(460, d.getWidth()), d.getHeight()));
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return ok[0];
    }

    public static boolean confirm(Component owner, String title, String message, String yes) {
        Component body = new JLabel("<html><div style='width:320px'>" + message + "</div></html>");
        ((JLabel) body).setFont(Theme.font(Font.PLAIN, 14));
        return formDialog(owner, title, (JComponent) body, yes, () -> {});
    }

    public static JPanel hbox(int gap, Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, -gap, 0, 0));
        for (Component c : parts) p.add(c);
        return p;
    }

    /** Standard page scaffold: transparent panel with padding. */
    public static JPanel page() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setBackground(Theme.BG);
        p.setBorder(BorderFactory.createEmptyBorder(8, 28, 24, 28));
        return p;
    }
}
