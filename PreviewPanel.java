package org.example.ui;

import org.example.storage.StorageManager;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Shows Extracted / Preprocessed / Summary in tabs plus metadata on the side.
 */
public class PreviewPanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextArea extracted = new JTextArea();
    private final JTextArea preprocessed = new JTextArea();
    private final JTextArea summary = new JTextArea();
    private final JTextArea metadata = new JTextArea();

    // last pipeline result (for Save)
    private Map<String,Object> lastPipelineResult = null;

    public PreviewPanel() {
        setLayout(new BorderLayout(6,6));
        extracted.setEditable(false); extracted.setLineWrap(true); extracted.setWrapStyleWord(true);
        preprocessed.setEditable(false); preprocessed.setLineWrap(true); preprocessed.setWrapStyleWord(true);
        summary.setEditable(false); summary.setLineWrap(true); summary.setWrapStyleWord(true);
        metadata.setEditable(false);
        tabs.add("Extracted", new JScrollPane(extracted));
        tabs.add("Preprocessed", new JScrollPane(preprocessed));
        tabs.add("Summary", new JScrollPane(summary));

        add(tabs, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(4,4));
        right.add(new JLabel("Metadata"), BorderLayout.NORTH);
        right.add(new JScrollPane(metadata), BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    public void loadFromStoredFile(Path storedOriginal, StorageManager storage) throws Exception {
        // compute sha and load metadata/extracted/summary if present
        String sha = StorageManager.sha256(storedOriginal.toFile());
        Map<String,Object> row = storage == null ? null : storage.getBySha256(sha);

        extracted.setText("");
        preprocessed.setText("");
        summary.setText("");
        metadata.setText("");

        if (row != null) {
            metadata.setText("Filename: " + row.get("filename") + "\nLabel: " + row.get("predicted_label") + "\nConfidence: " + row.get("confidence"));
            Object extPath = row.get("extracted_path");
            if (extPath != null) {
                Path p = Path.of(extPath.toString());
                if (Files.exists(p)) extracted.setText(Files.readString(p));
            }
            Object sumPath = row.get("summary_path");
            if (sumPath != null) {
                Path p = Path.of(sumPath.toString());
                if (Files.exists(p)) summary.setText(Files.readString(p));
            }
        } else {
            metadata.setText("No DB record for this file.");
        }
    }

    public void loadFromPipelineResult(Map<String,Object> result, String filename) {
        this.lastPipelineResult = result;
        extracted.setText(result.getOrDefault("extracted","").toString());
        preprocessed.setText(result.getOrDefault("preprocessed","").toString());
        summary.setText(result.getOrDefault("summary","").toString());
        metadata.setText("Filename: " + filename + "\nPrediction: " + result.getOrDefault("prediction","-") + "\nConfidence: " + result.getOrDefault("confidence","-"));
    }

    public Map<String,Object> getCurrentPipelineResult() { return lastPipelineResult; }
}
