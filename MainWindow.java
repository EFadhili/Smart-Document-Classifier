package org.example.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import com.google.gson.reflect.TypeToken;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.storage.StorageManager;
import java.nio.file.Path;
import java.sql.ResultSet;


public class MainWindow extends JFrame {
    private final Gson GSON = new Gson();

    // UI components
    private final JLabel fileLabel = new JLabel("No file selected");
    private final JTextArea extractedArea = new JTextArea();
    private final JTextArea preprocessedArea = new JTextArea();
    private final JLabel classificationLabel = new JLabel("Prediction: - (confidence: -)");
    private final JTextArea summaryArea = new JTextArea();
    private final JButton runButton = new JButton("Run Pipeline");
    private final JButton uploadButton = new JButton("Upload File");
    private final JButton saveButton = new JButton("Save Output");

    // Bridges / runners 
    private final String pythonExe = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\.venv\\Scripts\\python.exe";
    private final String preprocessScript = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\preprocess.py";
    private final String classifierScript = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\evaluate_single.py";
    private final String ocrScript = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\ocr_google.py";
    private final String summarizerScript = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\summarize_gemini.py";
    private final String credsJsonPath = "C:\\Users\\EFM\\gcloud_key\\legal-ocr-project-474912-7416611b42c0.json"; // optional

    private final OcrBridge ocrBridge = new OcrBridge(pythonExe, ocrScript);
    private final TextExtractor extractor = new TextExtractor(ocrBridge);
    private final ProcessRunner preprocessor = new ProcessRunner(pythonExe, preprocessScript);
    private final ProcessRunner classifierRunner = new ProcessRunner(pythonExe, classifierScript);
    private final SummarizationBridge summarizer = new SummarizationBridge(pythonExe, summarizerScript, credsJsonPath);

    private StorageManager storage = null;

    private File currentFile = null;

    public MainWindow() {
        setTitle("Legal Doc Processor — UI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        // Top controls
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        uploadButton.addActionListener(this::onUpload);
        top.add(uploadButton);
        top.add(fileLabel);

        runButton.addActionListener(this::onRunPipeline);
        runButton.setEnabled(false);
        top.add(runButton);

        saveButton.addActionListener(this::onSaveOutput);
        saveButton.setEnabled(false);
        top.add(saveButton);

        root.add(top, BorderLayout.NORTH);

        // Center split: left extraction/preprocess, right classification & summary
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.55);

        // Left panel
        JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
        leftPanel.add(new JLabel("Extracted Text"), BorderLayout.NORTH);
        extractedArea.setLineWrap(true);
        extractedArea.setWrapStyleWord(true);
        extractedArea.setEditable(false);
        JScrollPane extractedScroll = new JScrollPane(extractedArea);
        extractedScroll.setPreferredSize(new Dimension(600, 300));
        leftPanel.add(extractedScroll, BorderLayout.CENTER);

        JPanel preprocBox = new JPanel(new BorderLayout(4, 4));
        preprocBox.add(new JLabel("Preprocessed Tokens"), BorderLayout.NORTH);
        preprocessedArea.setLineWrap(true);
        preprocessedArea.setWrapStyleWord(true);
        preprocessedArea.setEditable(false);
        preprocBox.add(new JScrollPane(preprocessedArea), BorderLayout.CENTER);
        preprocBox.setPreferredSize(new Dimension(600, 200));
        leftPanel.add(preprocBox, BorderLayout.SOUTH);

        // Right panel
        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        JPanel classBox = new JPanel(new BorderLayout(4, 4));
        classBox.add(new JLabel("Classification"), BorderLayout.NORTH);
        classificationLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        classBox.add(classificationLabel, BorderLayout.CENTER);
        classBox.setPreferredSize(new Dimension(300, 80));
        rightPanel.add(classBox, BorderLayout.NORTH);

        JPanel summaryBox = new JPanel(new BorderLayout(4, 4));
        summaryBox.add(new JLabel("Summary (Gemini)"), BorderLayout.NORTH);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setEditable(false);
        summaryBox.add(new JScrollPane(summaryArea), BorderLayout.CENTER);
        rightPanel.add(summaryBox, BorderLayout.CENTER);

        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(rightPanel);
        root.add(mainSplit, BorderLayout.CENTER);

        // Footer status area (small)
        JTextArea status = new JTextArea();
        status.setEditable(false);
        status.setRows(2);
        root.add(new JScrollPane(status), BorderLayout.SOUTH);

        // make UI visible
        setVisible(true);

        try {
            String projectRoot = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project";


            // fallback if somehow null or empty
            if (projectRoot == null || projectRoot.trim().isEmpty()) {
                projectRoot = System.getProperty("user.dir");
            }

            storage = new StorageManager(projectRoot);
            System.out.println("🔍 StorageManager initialized at: " + projectRoot + "/data");
        } catch (Exception e) {
            storage = null;
            System.err.println("⚠️ Could not initialize StorageManager: " + e.getMessage());
            e.printStackTrace();
        }

    }

    // Upload file
    private void onUpload(ActionEvent ev) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            currentFile = chooser.getSelectedFile();
            fileLabel.setText(currentFile.getName());
            runButton.setEnabled(true);
            saveButton.setEnabled(false);
            extractedArea.setText("");
            preprocessedArea.setText("");
            classificationLabel.setText("Prediction: - (confidence: -)");
            summaryArea.setText("");
        }
    }

    // Run pipeline in background
    private void onRunPipeline(ActionEvent ev) {
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this, "Select a file first.");
            return;
        }

        runButton.setEnabled(false);
        uploadButton.setEnabled(false);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    publish("Extracting text...");
                    String extracted = extractor.extract(currentFile);
                    if (extracted == null) extracted = "";

                    publish("Showing extracted text...");
                    extractedArea.setText(extracted);

                    publish("Preprocessing...");
                    Map<String, Object> prePayload = new HashMap<>();
                    prePayload.put("mode", "preprocess");
                    prePayload.put("text", extracted);
                    String preRaw = preprocessor.callBridge(prePayload).toString();

                    // try parse tokens array else show raw
                    String preForClassifier;
                    try {
                        java.lang.reflect.Type listType = new TypeToken<List<String>>() {}.getType();
                        List<String> tokens = GSON.fromJson(preRaw, listType);
                        preprocessedArea.setText(String.join(" ", tokens));
                        preForClassifier = String.join(" ", tokens);
                    } catch (Exception ex) {
                        preprocessedArea.setText(preRaw);
                        preForClassifier = preRaw;
                    }

                    publish("Classifying...");
                    Map<String, Object> classPayload = new HashMap<>();
                    classPayload.put("text", preForClassifier);
                    String classRaw = classifierRunner.callBridge(classPayload).toString();

                    Map<String, Object> classResp;
                    try {
                        classResp = GSON.fromJson(classRaw, Map.class);
                    } catch (Exception ex) {
                        classResp = Map.of("error", "Invalid JSON");
                    }

                    String predicted = (String) classResp.getOrDefault("prediction", "Unknown");
                    double conf = 0.0;
                    try {
                        Object c = classResp.get("confidence");
                        if (c instanceof Number) conf = ((Number) c).doubleValue();
                        else if (c != null) conf = Double.parseDouble(c.toString());
                    } catch (Exception ignored) {}

                    classificationLabel.setText(String.format("Prediction: %s (conf: %.3f)", predicted, conf));

                    publish("Summarizing (Gemini)...");
                    String summary = summarizer.summarize(extracted, "models/gemini-2.5-flash", 120);
                    summaryArea.setText(summary);

                    // store results in client properties for saving
                    putClientProperty("last_extracted", extracted);
                    putClientProperty("last_preprocessed", preForClassifier);
                    putClientProperty("last_prediction", predicted);
                    putClientProperty("last_confidence", conf);
                    putClientProperty("last_summary", summary);

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainWindow.this, "Error running pipeline: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    runButton.setEnabled(true);
                    uploadButton.setEnabled(true);
                    saveButton.setEnabled(true);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                // show progress messages (in status area or console)
                for (String s : chunks) {
                    System.out.println("[PIPELINE] " + s);
                }
            }
        };

        worker.execute();
    }

    private void onSaveOutput(ActionEvent ev) {
        Object exObj = getClientProperty("last_extracted");
        if (exObj == null) {
            JOptionPane.showMessageDialog(this, "No pipeline output to save. Run pipeline first.");
            return;
        }

        String extracted = exObj.toString();
        String pre = getClientProperty("last_preprocessed") != null ? getClientProperty("last_preprocessed").toString() : "";
        String pred = getClientProperty("last_prediction") != null ? getClientProperty("last_prediction").toString() : "unknown";
        double conf = getClientProperty("last_confidence") != null ? (double) getClientProperty("last_confidence") : 0.0;
        String summary = getClientProperty("last_summary") != null ? getClientProperty("last_summary").toString() : "";

        if (storage != null) {
            try {
                // 1) store original uploaded file (this.currentFile must be non-null)
                Path storedOriginal = storage.storeOriginal(currentFile.toPath().toFile());
                // 2) compute sha256 of original
                String sha = StorageManager.sha256(currentFile);

                // 3) store extracted text under label folder
                Path extractedPath = storage.storeExtracted(pred, currentFile.getName(), extracted);

                // 4) store summary as JSON
                String summaryJson = GSON.toJson(Map.of(
                        "summary", summary,
                        "prediction", pred,
                        "confidence", conf,
                        "file", currentFile.getName()
                ));
                Path summaryPath = storage.storeSummary(pred, currentFile.getName(), summaryJson);

                // 5) create metadata record and upsert
                Map<String, Object> rec = new java.util.HashMap<>();
                rec.put("filename", currentFile.getName());
                rec.put("original_path", currentFile.getAbsolutePath());
                rec.put("stored_input_path", storedOriginal.toString());
                rec.put("extracted_path", extractedPath.toString());
                rec.put("summary_path", summaryPath.toString());
                rec.put("predicted_label", pred);
                rec.put("confidence", conf);
                rec.put("sha256", sha);
                rec.put("processed", 1);
                rec.put("notes", "Saved from UI MainWindow");

                storage.upsertDocumentRecord(rec);

                JOptionPane.showMessageDialog(this, "Saved to storage and DB successfully.\nSummary path: " + summaryPath.toString());
                return;
            } catch (Exception e) {
                e.printStackTrace();
                // If storage fails, fall back to "save-as" local file option below
                int resp = JOptionPane.showConfirmDialog(this,
                        "Storage save failed: " + e.getMessage() + "\nWould you like to save locally instead?",
                        "Storage Error",
                        JOptionPane.YES_NO_OPTION);
                if (resp != JOptionPane.YES_OPTION) return;
            }
        }

        // Fallback: let user save JSON to disk (existing behavior)
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save pipeline result (.json)");
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File out = chooser.getSelectedFile();
            Map<String, Object> outMap = new HashMap<>();
            outMap.put("file", currentFile != null ? currentFile.getAbsolutePath() : "unknown");
            outMap.put("prediction", pred);
            outMap.put("confidence", conf);
            outMap.put("summary", summary);
            outMap.put("extracted_snippet", extracted.length() > 10000 ? extracted.substring(0, 10000) : extracted);
            try (FileWriter fw = new FileWriter(out, StandardCharsets.UTF_8)) {
                fw.write(GSON.toJson(outMap));
                JOptionPane.showMessageDialog(this, "Saved to " + out.getAbsolutePath());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Save error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    // small client property helpers
    private void putClientProperty(String k, Object v) {
        this.getRootPane().putClientProperty(k, v);
    }
    private Object getClientProperty(String k) {
        return this.getRootPane().getClientProperty(k);
    }

    // main launcher
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainWindow());
    }
}

