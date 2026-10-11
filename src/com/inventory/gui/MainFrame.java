package com.inventory.gui;

import com.inventory.model.Permission;
import com.inventory.util.CsvUtil;
import com.inventory.util.Fmt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/** Application shell: sidebar navigation, top bar (undo/redo, user menu) and the screens. */
public class MainFrame extends JFrame {
    private final AppContext ctx;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel pageTitle = Ui.label("", 22, Font.BOLD, Theme.TEXT);
    private final JLabel pageSub = Ui.muted("");
    private final Map<String, Nav> navs = new LinkedHashMap<>();
    private final Map<String, String[]> meta = new HashMap<>();
    private final List<Refreshable> pages = new ArrayList<>();
    private final Ui.Btn undo = Ui.ghost("Undo"), redo = Ui.ghost("Redo");
    private ProductsPanel products;

    // ------------------------------------------------------------------ sidebar button
    private static final class Nav extends JButton {
        boolean selected, hover; int badge;
        Nav(String text) {
            super(text);
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setFont(Theme.font(Font.PLAIN, 15));
            setBorder(BorderFactory.createEmptyBorder(11, 24, 11, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (selected || hover) {
                g2.setColor(selected ? Theme.SIDEBAR_HOVER : new Color(0x16213A));
                g2.fillRoundRect(10, 2, getWidth() - 20, getHeight() - 4, 12, 12);
            }
            if (selected) { g2.setColor(Theme.ACCENT); g2.fillRoundRect(10, 10, 4, getHeight() - 20, 4, 4); }
            g2.setFont(Theme.font(selected ? Font.BOLD : Font.PLAIN, 15));
            g2.setColor(selected ? Color.WHITE : Theme.SIDEBAR_TEXT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), 28, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            if (badge > 0) {
                String s = String.valueOf(badge);
                g2.setFont(Theme.font(Font.BOLD, 11));
                FontMetrics bf = g2.getFontMetrics();
                int w = Math.max(22, bf.stringWidth(s) + 12);
                int x = getWidth() - w - 12, y = (getHeight() - 20) / 2;
                g2.setColor(Theme.BAD);
                g2.fillRoundRect(x, y, w, 20, 20, 20);
                g2.setColor(Color.WHITE);
                g2.drawString(s, x + (w - bf.stringWidth(s)) / 2, y + (20 - bf.getHeight()) / 2 + bf.getAscent());
            }
            g2.dispose();
        }
    }

    public MainFrame(AppContext ctx) {
        super("Smart Inventory \u2013 " + ctx.store.storeName);
        this.ctx = ctx;
        setIconImage(Theme.appIcon());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { ctx.save(); System.exit(0); }
        });
        setSize(1320, 820);
        setMinimumSize(new Dimension(1120, 700));
        setLocationRelativeTo(null);
        Toast.setHost(this);

        JPanel side = new JPanel();
        side.setBackground(Theme.SIDEBAR);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(236, 0));
        side.add(brand());

        content.setOpaque(false);
        products = new ProductsPanel(ctx);
        group(side, "OVERVIEW");
        page(side, "dash", "Dashboard", "How your store is doing today", new DashboardPanel(ctx, this::openProducts, () -> show("po")), Permission.VIEW);
        group(side, "OPERATIONS");
        page(side, "products", "Products", "Catalogue, stock levels and prices", products, Permission.VIEW);
        page(side, "sales", "Sales", "Bill customers and review invoices", new SalesPanel(ctx), Permission.SELL);
        page(side, "po", "Purchasing", "Order from suppliers and receive stock", new PurchasePanel(ctx), Permission.PURCHASE);
        page(side, "suppliers", "Suppliers", "Who you buy from", new SuppliersPanel(ctx), Permission.VIEW);
        if (ctx.can(Permission.REPORTS) || ctx.can(Permission.AUDIT_VIEW) || ctx.can(Permission.USER_MANAGE)) group(side, "ADMIN");
        page(side, "reports", "Reports", "Sales, profit and stock analytics", new ReportsPanel(ctx), Permission.REPORTS);
        page(side, "audit", "Audit log", "Every action, traced to a user", new AuditPanel(ctx), Permission.AUDIT_VIEW);
        page(side, "users", "Users", "Accounts and roles", new UsersPanel(ctx), Permission.USER_MANAGE);
        side.add(Box.createVerticalGlue());
        JLabel foot = Ui.label("  " + ctx.store.storeName, 12, Font.PLAIN, new Color(0x64748B));
        foot.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 8));
        side.add(foot);

        // ---- top bar
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(pageTitle); titles.add(pageSub);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);
        if (ctx.can(Permission.STOCK_MANAGE)) { tools.add(undo); tools.add(redo); }
        undo.addActionListener(e -> doUndo(true));
        redo.addActionListener(e -> doUndo(false));
        tools.add(userChip());
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(22, 28, 14, 28));
        top.add(titles, BorderLayout.WEST);
        top.add(tools, BorderLayout.EAST);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.BG);
        main.add(top, BorderLayout.NORTH);
        main.add(content, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.add(side, BorderLayout.WEST);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);

        ctx.setOnChange(this::refreshAll);
        shortcuts();
        refreshAll();
        show("dash");
        Toast.info("Signed in as " + ctx.auth.current().getFullName() + " (" + ctx.auth.current().getRole() + ")");
    }

    // ------------------------------------------------------------------ building blocks
    private JComponent brand() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(24, 22, 20, 16));
        p.add(new JLabel(new ImageIcon(Theme.appIcon().getScaledInstance(36, 36, Image.SCALE_SMOOTH))), BorderLayout.WEST);
        JPanel t = new JPanel(new GridLayout(2, 1));
        t.setOpaque(false);
        t.add(Ui.label("Smart Inventory", 16, Font.BOLD, Color.WHITE));
        t.add(Ui.label("Management System", 12, Font.PLAIN, new Color(0x94A3B8)));
        p.add(t, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        return p;
    }

    private void group(JPanel side, String name) {
        JLabel l = Ui.label(name, 11, Font.BOLD, new Color(0x64748B));
        l.setBorder(BorderFactory.createEmptyBorder(16, 26, 6, 8));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(l);
    }

    private void page(JPanel side, String key, String title, String sub, JComponent panel, Permission need) {
        if (!ctx.can(need)) return;
        content.add(panel, key);
        if (panel instanceof Refreshable r) pages.add(r);
        meta.put(key, new String[]{title, sub});
        Nav n = new Nav(title);
        n.addActionListener(e -> show(key));
        n.setAlignmentX(Component.LEFT_ALIGNMENT);
        navs.put(key, n);
        side.add(n);
    }

    private JComponent userChip() {
        var u = ctx.auth.current();
        String initials = Arrays.stream(u.getFullName().trim().split("\\s+")).limit(2)
                .map(s -> s.substring(0, 1).toUpperCase()).reduce("", String::concat);
        JButton chip = new JButton() {
            boolean hover;
            { addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(hover ? new Color(0xF8FAFC) : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
                g2.setColor(Theme.ACCENT);
                g2.fillOval(6, 6, getHeight() - 12, getHeight() - 12);
                g2.setFont(Theme.font(Font.BOLD, 12));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(initials, 6 + (getHeight() - 12 - fm.stringWidth(initials)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.setFont(Theme.font(Font.BOLD, 13));
                g2.setColor(Theme.TEXT);
                g2.drawString(u.getFullName(), getHeight() + 2, getHeight() / 2 - 1);
                g2.setFont(Theme.font(Font.PLAIN, 11));
                g2.setColor(Theme.MUTED);
                g2.drawString(u.getRole().name() + "", getHeight() + 2, getHeight() / 2 + 13);
                g2.dispose();
            }
        };
        chip.setContentAreaFilled(false); chip.setBorderPainted(false); chip.setFocusPainted(false);
        FontMetrics fm = chip.getFontMetrics(Theme.font(Font.BOLD, 13));
        chip.setPreferredSize(new Dimension(44 + Math.max(90, fm.stringWidth(u.getFullName())) + 16, 44));
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.addActionListener(e -> userMenu().show(chip, chip.getWidth() - 230, chip.getHeight() + 6));
        return chip;
    }

    private JPopupMenu userMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(mi("Change password\u2026", this::changePassword));
        if (ctx.can(Permission.DATA_IO)) {
            m.addSeparator();
            m.add(mi("Export products to CSV", () -> export(true)));
            m.add(mi("Export invoices to CSV", () -> export(false)));
            m.add(mi("Import products from CSV\u2026", this::importCsv));
            m.add(mi("Backup data file", () -> Ui.attempt(() -> {
                ctx.save();
                Toast.success("Backup saved: " + ctx.storage.backup().toAbsolutePath());
            })));
        }
        m.addSeparator();
        m.add(mi("Sign out", this::signOut));
        return m;
    }

    private JMenuItem mi(String text, Runnable r) {
        JMenuItem i = new JMenuItem(text);
        i.setFont(Theme.font(Font.PLAIN, 14));
        i.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 30));
        i.addActionListener(e -> r.run());
        return i;
    }

    // ------------------------------------------------------------------ behaviour
    private void show(String key) {
        if (!navs.containsKey(key)) return;
        navs.forEach((k, n) -> { n.selected = k.equals(key); n.repaint(); });
        pageTitle.setText(meta.get(key)[0]);
        pageSub.setText(meta.get(key)[1]);
        cards.show(content, key);
    }

    private void openProducts(String filter) { products.showFilter(filter); show("products"); }

    private void refreshAll() {
        for (Refreshable r : pages) r.refresh();
        long alerts = ctx.inventory.all().stream().filter(p -> p.isLowStock() || p.isExpired(LocalDate.now())).count();
        Nav n = navs.get("products");
        if (n != null) { n.badge = (int) alerts; n.repaint(); }
        if (ctx.can(Permission.STOCK_MANAGE)) {
            var um = ctx.inventory.undoManager();
            undo.setEnabled(um.undoCount() > 0); redo.setEnabled(um.redoCount() > 0);
            undo.setToolTipText(um.undoCount() > 0 ? "Undo: " + um.peekUndo() + "  (Ctrl+Z)" : "Nothing to undo");
            redo.setToolTipText(um.redoCount() > 0 ? "Redo: " + um.peekRedo() + "  (Ctrl+Y)" : "Nothing to redo");
        }
    }

    private void doUndo(boolean undoIt) {
        if (!ctx.can(Permission.STOCK_MANAGE)) return;
        String[] d = new String[1];
        if (Ui.attempt(() -> d[0] = undoIt ? ctx.inventory.undo() : ctx.inventory.redo())) {
            ctx.changed();
            Toast.success((undoIt ? "Undone: " : "Redone: ") + d[0]);
        }
    }

    private void shortcuts() {
        JRootPane rp = getRootPane();
        InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rp.getActionMap();
        bind(im, am, "undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), () -> doUndo(true));
        bind(im, am, "redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), () -> doUndo(false));
        bind(im, am, "find", KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), () -> { show("products"); products.focusSearch(); });
        bind(im, am, "refresh", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::refreshAll);
        int i = 1;
        for (String key : navs.keySet()) {
            final String k = key;
            bind(im, am, "nav" + i, KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, InputEvent.CTRL_DOWN_MASK), () -> show(k));
            if (++i > 9) break;
        }
    }

    private static void bind(InputMap im, ActionMap am, String name, KeyStroke ks, Runnable r) {
        im.put(ks, name);
        am.put(name, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { r.run(); } });
    }

    private void signOut() {
        ctx.save();
        ctx.auth.logout();
        dispose();
        new LoginFrame(ctx).setVisible(true);
    }

    private void changePassword() {
        JPasswordField old = Ui.password(16), n1 = Ui.password(16), n2 = Ui.password(16);
        Ui.Form f = new Ui.Form();
        f.add("Current password", old);
        f.add2("New password (min 6)", n1, "Confirm new password", n2);
        if (Ui.formDialog(this, "Change password", f, "Update password", () -> {
            if (!new String(n1.getPassword()).equals(new String(n2.getPassword()))) throw new IllegalArgumentException("The new passwords do not match.");
            ctx.auth.changeOwnPassword(new String(old.getPassword()), new String(n1.getPassword()));
        })) { ctx.changed(); Toast.success("Password changed."); }
    }

    private void export(boolean prods) {
        Ui.attempt(() -> {
            Path p = prods ? CsvUtil.exportProducts(ctx.inventory.all(), Paths.get("data", "exports", "products.csv"))
                           : CsvUtil.exportInvoices(ctx.sales.all(), Paths.get("data", "exports", "invoices.csv"));
            Toast.success("Exported to " + p.toAbsolutePath());
        });
    }

    private void importCsv() {
        JFileChooser fc = new JFileChooser(".");
        fc.setDialogTitle("Choose a products CSV file");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        List<String> errors = new ArrayList<>();
        int[][] r = new int[1][];
        if (Ui.attempt(() -> r[0] = CsvUtil.importProducts(fc.getSelectedFile().toPath(), ctx.inventory, errors))) {
            ctx.changed();
            if (errors.isEmpty()) Toast.success("Imported " + r[0][0] + " products.");
            else {
                Toast.info("Imported " + r[0][0] + ", skipped " + r[0][1] + ".");
                JTextArea a = new JTextArea(String.join("\n", errors), 8, 50);
                a.setEditable(false); a.setFont(Theme.mono(12));
                Ui.formDialog(this, "Skipped rows", Ui.scroll(a), null, () -> {});
            }
        }
    }
}
