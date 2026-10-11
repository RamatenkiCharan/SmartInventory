package com.inventory.gui;

import com.inventory.model.Role;
import com.inventory.model.User;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** User administration (ADMIN only). */
public class UsersPanel extends JPanel implements Refreshable {
    private final AppContext ctx;
    private final Ui.Model model = new Ui.Model(new String[]{"Username", "Full name", "Role", "Status"},
            new Class<?>[]{String.class, String.class, Ui.Badge.class, Ui.Badge.class});
    private final JTable table = Ui.table(model, 120, 220, 110, 100);
    private final Ui.Btn pw = Ui.secondary("Reset password"), role = Ui.secondary("Change role"),
            toggle = Ui.secondary("Enable / disable"), del = Ui.secondary("Delete");

    public UsersPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(8, 28, 20, 28));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bar.setOpaque(false);
        Ui.Btn add = Ui.primary("+  Add user");
        bar.add(pw); bar.add(role); bar.add(toggle); bar.add(del); bar.add(add);
        add(bar, BorderLayout.NORTH);
        add(Ui.tableCard(table), BorderLayout.CENTER);
        add.addActionListener(e -> addUser());
        pw.addActionListener(e -> with(this::resetPw));
        role.addActionListener(e -> with(this::changeRole));
        toggle.addActionListener(e -> with(u -> run(() -> ctx.auth.setActive(u.getUsername(), !u.isActive()),
                u.getUsername() + (u.isActive() ? " disabled." : " enabled."))));
        del.addActionListener(e -> with(u -> {
            if (Ui.confirm(this, "Delete user", "Delete <b>" + u.getUsername() + "</b>?", "Delete"))
                run(() -> ctx.auth.deleteUser(u.getUsername()), "User deleted.");
        }));
        table.getSelectionModel().addListSelectionListener(e -> {
            boolean h = table.getSelectedRow() >= 0;
            pw.setEnabled(h); role.setEnabled(h); toggle.setEnabled(h); del.setEnabled(h);
        });
        pw.setEnabled(false); role.setEnabled(false); toggle.setEnabled(false); del.setEnabled(false);
        refresh();
    }

    private void run(Runnable r, String msg) { if (Ui.attempt(r)) { ctx.changed(); Toast.success(msg); } }

    private void with(java.util.function.Consumer<User> a) {
        String name = Ui.selectedKey(table);
        if (name == null) return;
        ctx.auth.listUsers().stream().filter(u -> u.getUsername().equals(name)).findFirst().ifPresent(a);
    }

    @Override public void refresh() {
        String keep = Ui.selectedKey(table);
        List<Object[]> rows = new ArrayList<>();
        for (User u : ctx.auth.listUsers())
            rows.add(new Object[]{u.getUsername(), u.getFullName(),
                    new Ui.Badge(u.getRole().name(), u.getRole() == Role.ADMIN ? Theme.ACCENT : u.getRole() == Role.MANAGER ? Theme.INFO : Theme.MUTED),
                    new Ui.Badge(u.isActive() ? "Active" : "Disabled", u.isActive() ? Theme.GOOD : Theme.BAD)});
        model.setRows(rows);
        Ui.reselect(table, keep);
    }

    private void addUser() {
        JTextField user = Ui.field("username", 12), full = Ui.field("Full name", 14);
        JPasswordField pass = Ui.password(12);
        JComboBox<Role> r = Ui.combo(Role.values());
        r.setSelectedItem(Role.STAFF);
        Ui.Form f = new Ui.Form();
        f.add2("Username", user, "Full name", full);
        f.add2("Password (min 6)", pass, "Role", r);
        if (Ui.formDialog(this, "Add user", f, "Create user", () ->
                ctx.auth.addUser(user.getText(), full.getText().isBlank() ? user.getText() : full.getText(), new String(pass.getPassword()), (Role) r.getSelectedItem())))
        { ctx.changed(); Toast.success("User created."); }
    }

    private void resetPw(User u) {
        JPasswordField pass = Ui.password(16);
        Ui.Form f = new Ui.Form();
        f.add("New password for " + u.getUsername(), pass);
        if (Ui.formDialog(this, "Reset password", f, "Reset", () -> ctx.auth.resetPassword(u.getUsername(), new String(pass.getPassword()))))
        { ctx.changed(); Toast.success("Password reset."); }
    }

    private void changeRole(User u) {
        JComboBox<Role> r = Ui.combo(Role.values());
        r.setSelectedItem(u.getRole());
        Ui.Form f = new Ui.Form();
        f.add("Role for " + u.getUsername(), r);
        if (Ui.formDialog(this, "Change role", f, "Save", () -> ctx.auth.setRole(u.getUsername(), (Role) r.getSelectedItem())))
        { ctx.changed(); Toast.success("Role updated."); }
    }
}
