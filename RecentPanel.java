package org.example.ui;

import org.example.storage.StorageManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * Displays recent files found under data/inputs and optionally reads DB for metadata.
 */
public class RecentPanel extends JPanel {
    private final DefaultTableModel model;
    private final JTable table;

    public RecentPanel() {
        setLayout(new BorderLayout(6,6));
        model = new DefaultTableModel(new String[] {"Filename","Label","Confidence","Date"}, 0) {
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh(StorageManager storage) {
        model.setRowCount(0);
        if (storage == null) return;
        try {
            Path inputs = Paths.get(storage.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().resolve("data").resolve("inputs");
            // Fallback: read storage.getStorageRoot? (we keep it simple: scan storage's base dir)
        } catch (Exception ignored) {}

        // Simpler approach: ask storage to scan files or read DB; here we scan data/inputs in working dir
        try {
            Path inputsRoot = Paths.get(System.getProperty("user.dir")).resolve("data").resolve("inputs");
            if (!Files.exists(inputsRoot)) return;
            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> days = Files.newDirectoryStream(inputsRoot)) {
                for (Path d : days) {
                    if (!Files.isDirectory(d)) continue;
                    try (DirectoryStream<Path> fs = Files.newDirectoryStream(d)) {
                        for (Path f : fs) if (Files.isRegularFile(f)) files.add(f);
                    }
                }
            }
            files.sort((a,b)->{
                try { return Long.compare(Files.getLastModifiedTime(b).toMillis(), Files.getLastModifiedTime(a).toMillis()); }
                catch (Exception e) { return 0; }
            });
            for (Path f : files) {
                model.addRow(new Object[] { f.getFileName().toString(), "-", "-", Files.getLastModifiedTime(f).toString() });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void filter(String q) {
        // quick and simple: rebuild with filter (in practice keep a cached list)
        // For brevity not implemented here; call refresh then remove non-matching rows.
    }

    public void onSelect(Consumer<Path> consumer) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0) {
                String fileName = (String) model.getValueAt(table.getSelectedRow(), 0);
                // find corresponding Path by scanning - for simplicity we re-scan dir
                try {
                    Path inputsRoot = Paths.get(System.getProperty("user.dir")).resolve("data").resolve("inputs");
                    for (Path day : Files.newDirectoryStream(inputsRoot)) {
                        Path candidate = day.resolve(fileName);
                        if (Files.exists(candidate)) { consumer.accept(candidate); break; }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    public Path getSelected() {
        if (table.getSelectedRow() < 0) return null;
        String fileName = (String) model.getValueAt(table.getSelectedRow(), 0);
        try {
            Path inputsRoot = Paths.get(System.getProperty("user.dir")).resolve("data").resolve("inputs");
            for (Path day : Files.newDirectoryStream(inputsRoot)) {
                Path candidate = day.resolve(fileName);
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
