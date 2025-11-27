package org.example.ui;

import org.example.storage.StorageManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Displays recent files for the signed-in user.
 * Expects per-user layout: data/inputs/<ownerId>/<yyyy-mm-dd>/<filename>
 * Uses StorageManager.listStoredInputsByOwner(ownerId) to populate.
 */
public class RecentPanel extends JPanel {
    private final DefaultTableModel model;
    private final JTable table;

    // Full list (all rows from storage for current owner)
    private List<Path> allPaths = new ArrayList<>();
    // Currently displayed list after filtering/sorting
    private List<Path> shownPaths = new ArrayList<>();

    public RecentPanel() {
        setLayout(new BorderLayout(6, 6));
        model = new DefaultTableModel(new String[] {"Filename", "Label", "Confidence", "Date"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Load entries for the given owner. If ownerId is null, show nothing (or change to "show all" if you prefer).
     */
    public void refresh(StorageManager storage, String ownerId) {
        model.setRowCount(0);
        allPaths.clear();
        shownPaths.clear();

        if (storage == null) return;
        try {
            // Ask storage for the user’s stored input paths
            List<Path> paths = storage.listStoredInputsByOwner(ownerId);
            // Sort newest first by file modified time
            paths.sort((a, b) -> {
                try {
                    long mb = Files.getLastModifiedTime(b).toMillis();
                    long ma = Files.getLastModifiedTime(a).toMillis();
                    return Long.compare(mb, ma);
                } catch (Exception e) {
                    return 0;
                }
            });

            allPaths.addAll(paths);
            shownPaths.addAll(paths);

            // Populate table
            for (Path p : shownPaths) {
                String filename = p.getFileName().toString();
                String label = "-";       // optional: look up from DB if you add a method
                String conf = "-";        // optional: look up from DB if you add a method
                String date = "";
                try { date = Files.getLastModifiedTime(p).toString(); } catch (Exception ignored) {}

                model.addRow(new Object[]{ filename, label, conf, date });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Simple filename filter (case-insensitive). Call with empty string to clear filter.
     */
    public void filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        model.setRowCount(0);
        shownPaths.clear();

        if (q.isEmpty()) {
            shownPaths.addAll(allPaths);
        } else {
            for (Path p : allPaths) {
                if (p.getFileName().toString().toLowerCase(Locale.ROOT).contains(q)) {
                    shownPaths.add(p);
                }
            }
        }

        // (Re)fill rows
        for (Path p : shownPaths) {
            String filename = p.getFileName().toString();
            String label = "-";
            String conf = "-";
            String date = "";
            try { date = Files.getLastModifiedTime(p).toString(); } catch (Exception ignored) {}
            model.addRow(new Object[]{ filename, label, conf, date });
        }
    }

    public void onSelect(Consumer<Path> consumer) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = table.getSelectedRow();
                if (idx >= 0 && idx < shownPaths.size()) {
                    consumer.accept(shownPaths.get(idx));
                }
            }
        });
    }

    public Path getSelected() {
        int idx = table.getSelectedRow();
        if (idx >= 0 && idx < shownPaths.size()) {
            return shownPaths.get(idx);
        }
        return null;
    }
}
