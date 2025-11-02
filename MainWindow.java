package org.example.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * MainWindow is now presentation-only: it lays out panels and delegates actions to UIController.
 */
public class MainWindow extends JFrame {
    private final UIController controller;
    private final ToolbarPanel toolbar;
    private final RecentPanel recentPanel;
    private final PreviewPanel preview;
    private final StatusBar statusBar;

    public MainWindow(UIController controller) {
        this.controller = controller;
        setTitle("Smart Document Processor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 760);
        setLocationRelativeTo(null);

        // panels
        toolbar = new ToolbarPanel();
        recentPanel = new RecentPanel();
        preview = new PreviewPanel();
        statusBar = new StatusBar();

        // layout
        setLayout(new BorderLayout(8,8));
        add(toolbar, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, recentPanel, preview);
        split.setResizeWeight(0.28);
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        // wire toolbar actions to controller
        toolbar.onUpload(this::handleUpload);
        toolbar.onRun(this::handleRun);
        toolbar.onSave(this::handleSave);
        toolbar.onSearch(q -> recentPanel.filter(q));

        // when user selects a recent record, load preview
        recentPanel.onSelect(path -> {
            try {
                preview.loadFromStoredFile(path, controller.getStorage());
                statusBar.setMessage("Loaded: " + path.getFileName().toString());
            } catch (Exception e) {
                e.printStackTrace();
                statusBar.setMessage("Preview load failed: " + e.getMessage());
            }
        });

        // populate recent files initially
        recentPanel.refresh(controller.getStorage());
    }

    private void handleUpload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int r = chooser.showOpenDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try {
                Path stored = controller.uploadFile(f);
                statusBar.setMessage("Uploaded: " + stored.getFileName());
                recentPanel.refresh(controller.getStorage());
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Upload failed: " + e.getMessage());
            }
        }
    }

    private void handleRun() {
        Path selected = recentPanel.getSelected();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a file first.");
            return;
        }

        File file = selected.toFile();
        statusBar.setMessage("Running pipeline...");
        controller.runPipeline(file, result -> {
            SwingUtilities.invokeLater(() -> {
                preview.loadFromPipelineResult(result, file.getName());
                statusBar.setMessage("Pipeline finished");
            });
        }, ex -> {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Pipeline error: " + ex.getMessage());
                statusBar.setMessage("Pipeline failed");
            });
        });
    }

    private void handleSave() {
        Path selected = recentPanel.getSelected();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a file first.");
            return;
        }
        File file = selected.toFile();
        Map<String,Object> pipelineResult = preview.getCurrentPipelineResult();
        if (pipelineResult == null) {
            JOptionPane.showMessageDialog(this, "No pipeline results available. Run pipeline first or paste results into preview.");
            return;
        }
        try {
            controller.saveResults(file, pipelineResult);
            JOptionPane.showMessageDialog(this, "Saved.");
            statusBar.setMessage("Saved.");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Save failed: " + e.getMessage());
            statusBar.setMessage("Save failed");
        }
    }
}
