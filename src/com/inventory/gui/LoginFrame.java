package com.inventory.gui;

import com.inventory.exception.InventoryException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/** Split-screen sign-in with quick-fill demo accounts and a shake animation on failure. */
public class LoginFrame extends JFrame {
    private final AppContext ctx;
    private final JTextField user = Ui.field("Username", 18);
    private final JPasswordField pass = Ui.password(18);
    private final JLabel error = Ui.label(" ", 13, Font.PLAIN, Theme.BAD);

    public LoginFrame(AppContext ctx) {
        super("Smart Inventory \u2013 Sign in");
        this.ctx = ctx;
        setIconImage(Theme.appIcon());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(960, 600);
        setResizable(false);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new GridLayout(1, 2));
        root.add(brand());
        root.add(form());
        setContentPane(root);
        getRootPane().setDefaultButton(null);
    }

    private JComponent brand() {
        JPanel p = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x4F46E5), getWidth(), getHeight(), new Color(0x1E1B4B)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fillOval(-120, getHeight() - 260, 420, 420);
                g2.fillOval(getWidth() - 200, -120, 360, 360);
                g2.dispose();
            }
        };
        JLabel logo = new JLabel(new ImageIcon(Theme.appIcon().getScaledInstance(52, 52, Image.SCALE_SMOOTH)));
        logo.setBounds(48, 56, 52, 52);
        JLabel name = Ui.label("Smart Inventory", 34, Font.BOLD, Color.WHITE);
        name.setBounds(48, 128, 400, 44);
        JLabel tag = Ui.label("<html>Stock, sales and purchasing<br>in one clear workspace.</html>", 17, Font.PLAIN, new Color(0xC7D2FE));
        tag.setBounds(48, 178, 400, 60);
        p.add(logo); p.add(name); p.add(tag);
        String[] points = {"Live low-stock and expiry alerts", "Fast billing with GST, discounts and receipts", "Undo / redo, audit trail and role-based access"};
        int y = 330;
        for (String s : points) {
            JLabel l = Ui.label("\u2713   " + s, 15, Font.PLAIN, Color.WHITE);
            l.setBounds(48, y, 420, 26);
            p.add(l);
            y += 38;
        }
        return p;
    }

    private JComponent form() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Color.WHITE);
        JPanel f = new JPanel();
        f.setOpaque(false);
        f.setLayout(new BoxLayout(f, BoxLayout.Y_AXIS));
        f.setPreferredSize(new Dimension(340, 420));

        JLabel h = Ui.label("Welcome back", 28, Font.BOLD, Theme.TEXT);
        JLabel sub = Ui.muted("Sign in to continue to your store.");
        Ui.Btn go = Ui.primary("Sign in");
        go.setFont(Theme.font(Font.BOLD, 15));
        go.setBorder(BorderFactory.createEmptyBorder(13, 16, 13, 16));
        go.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        go.addActionListener(e -> login());
        user.addActionListener(e -> pass.requestFocusInWindow());
        pass.addActionListener(e -> login());

        JCheckBox show = new JCheckBox("Show password");
        show.setOpaque(false);
        show.setFont(Theme.font(Font.PLAIN, 13));
        show.setForeground(Theme.MUTED);
        show.setFocusPainted(false);
        show.addActionListener(e -> pass.setEchoChar(show.isSelected() ? (char) 0 : '\u2022'));

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chips.setOpaque(false);
        for (String[] a : new String[][]{{"Admin", "admin", "admin123"}, {"Manager", "manager", "manager123"}, {"Staff", "staff", "staff123"}}) {
            Ui.Btn b = Ui.secondary(a[0]);
            b.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            b.setFont(Theme.font(Font.BOLD, 12));
            b.addActionListener(e -> { user.setText(a[1]); pass.setText(a[2]); pass.requestFocusInWindow(); });
            chips.add(b);
        }

        Component[] parts = {h, Box.createVerticalStrut(4), sub, Box.createVerticalStrut(26), Ui.label("USERNAME", 11, Font.BOLD, Theme.MUTED),
                Box.createVerticalStrut(5), user, Box.createVerticalStrut(16), Ui.label("PASSWORD", 11, Font.BOLD, Theme.MUTED),
                Box.createVerticalStrut(5), pass, Box.createVerticalStrut(6), show, Box.createVerticalStrut(4), error,
                Box.createVerticalStrut(8), go, Box.createVerticalStrut(26), Ui.muted("Quick fill a demo account"), Box.createVerticalStrut(8), chips};
        for (Component c : parts) { ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT); f.add(c); }
        outer.add(f);
        SwingUtilities.invokeLater(() -> user.requestFocusInWindow());
        return outer;
    }

    private void login() {
        try {
            ctx.auth.login(user.getText(), new String(pass.getPassword()));
            MainFrame m = new MainFrame(ctx);
            dispose();
            m.setVisible(true);
        } catch (InventoryException ex) {
            error.setText(ex.getMessage());
            pass.setText("");
            pass.requestFocusInWindow();
            shake();
        }
    }

    private void shake() {
        Point origin = getLocation();
        int[] step = {0};
        Timer t = new Timer(25, null);
        t.addActionListener(e -> {
            int dx = (int) (Math.sin(step[0] * 1.6) * (14 - step[0]));
            setLocation(origin.x + dx, origin.y);
            if (++step[0] > 12) { setLocation(origin); t.stop(); }
        });
        t.start();
    }
}
