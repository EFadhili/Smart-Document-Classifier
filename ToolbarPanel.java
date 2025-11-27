package org.example.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class ToolbarPanel extends CardPanel {
    private final JButton upload = ThemeUtil.makeAccentButton("\uD83D\uDCC2 Upload", ThemeUtil.PRIMARY);
    private final JButton run = ThemeUtil.makeAccentButton("\u25B6 Run", new Color(0x10B981)); // green
    private final JButton save = ThemeUtil.makeAccentButton("\uD83D\uDCBE Save", ThemeUtil.ACCENT);
    private final JTextField search = new JTextField();

    public ToolbarPanel() {
        super();
        setLayout(new BorderLayout(8,8));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        left.setOpaque(false);
        left.add(upload);
        left.add(run);
        left.add(save);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        right.setOpaque(false);
        search.setColumns(18);
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDDDDDD)),
                BorderFactory.createEmptyBorder(6,8,6,8)
        ));
        right.add(search);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    public void onUpload(Runnable r) { upload.addActionListener(e -> r.run()); }
    public void onRun(Runnable r) { run.addActionListener(e -> r.run()); }
    public void onSave(Runnable r) { save.addActionListener(e -> r.run()); }
    public void onSearch(Consumer<String> c) {
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { c.accept(search.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { c.accept(search.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { c.accept(search.getText()); }
        });
    }
}
