package org.example.ui;

import org.example.auth.TokenManager;
import org.example.storage.StorageManager;
import org.example.storage.CreditService;
import org.example.ui.components.FileTableModel;
import org.example.ui.theme.*;
import org.example.ui.theme.ThemeUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.sql.ResultSet;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import javax.swing.Timer;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.util.List;

public class MainWindowUI extends JFrame {

    // --- Core refs ---
    private final UIController controller;
    private final TokenManager tokenManager;
    private DropTarget extractedAreaDropTarget;
    private DropTarget mainWindowDropTarget;

    // --- Top bar ---
    private final JButton navButton = new JButton("Menu");
    private final JButton profileButton = new JButton();

    // --- Credit system components ---
    private final JLabel creditBalanceLabel = new JLabel("Credits: --");
    private final RoundedButton freeCreditsBtn = new RoundedButton("Get Free Credits", 14);
    private final JPopupMenu creditMenu = new JPopupMenu();
    private final Timer creditUpdateTimer;

    // --- Pages ---
    private final JPanel pages = new JPanel(new CardLayout());
    private static final String PAGE_DASH = "dashboard";
    private static final String PAGE_DB = "database";
    private static final String PAGE_SETTINGS = "settings";
    private static final String PAGE_STORAGE = "storage";

    // --- Dashboard widgets ---
    private final JTextArea extractedArea = new JTextArea();
    private final JTextArea metadataArea  = new JTextArea();
    private final JLabel docNameLabel = new JLabel("Document: -");
    private final JLabel docTypeLabel = new JLabel("Type: -");
    private final RoundedButton uploadBtn = new RoundedButton("Upload", 14);
    private final RoundedButton runBtn    = new RoundedButton("Run", 14);
    private final RoundedButton saveBtn   = new RoundedButton("Save Summary", 14);
    private final RoundedButton batchBtn = new RoundedButton("Batch", 14);

    // --- Database widgets ---
    private final JTextField dbSearch = new JTextField();
    private final JTable fileTable = new JTable();
    private final FileTableModel fileTableModel = new FileTableModel();

    // --- Settings ---
    private final JComboBox<String> fontCombo = new JComboBox<>(new String[]{"SansSerif", "Serif", "Monospaced", "Segoe UI", "Inter", "Roboto"});
    private final JComboBox<String> fontSizeCombo = new JComboBox<>(new String[]{"12", "13", "14", "15", "16"});
    private final JComboBox<String> themeCombo = new JComboBox<>(new String[]{"Light", "Dark", "Blue", "High Contrast"});
    private final JButton clearDbBtn = new JButton("Clear Database");

    private JLabel storageUserLabel;
    // --- Storage page widgets ---
    private final JTable tInputs    = new JTable();
    private final JTable tExtracted = new JTable();
    private final JTable tSummaries = new JTable();
    private final JButton storageRefreshBtn = new JButton("Refresh");
    private final JTextField storageSearch = new JTextField();
    private final JButton storageSearchBtn = new JButton("Search");
    // Backing lists to map view rows -> real paths
    private java.util.List<Path> inputsBacking    = new java.util.ArrayList<>();
    private java.util.List<Path> extractedBacking = new java.util.ArrayList<>();
    private java.util.List<Path> summariesBacking = new java.util.ArrayList<>();

    // --- Theme management ---
    private AppTheme currentTheme = AppTheme.DAYLIGHT;
    private String currentFontFamily = "SansSerif";
    private int currentFontSize = 13;

    // --- Profile menu ---
    private final JPopupMenu profileMenu = new JPopupMenu();

    // --- Admin menu ---
    private final JPopupMenu adminMenu = new JPopupMenu();

    // --- Status line ---
    private final JLabel statusLabel = new JLabel("Ready.");

    // --- State ---
    private Path currentSelectedPath = null;

    public MainWindowUI(UIController controller, TokenManager tokenManager) {
        this.controller = controller;
        this.tokenManager = tokenManager;

        // Initialize credit update timer (update every 30 seconds)
        creditUpdateTimer = new Timer(30000, e -> updateCreditDisplay());
        creditUpdateTimer.setRepeats(true);
        creditUpdateTimer.start();

        setTitle("Smart Document Processor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Set window icon
        setWindowIcon();

        // Modern app-wide defaults
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Button.arc", 16);
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

        initLayout();
        initActions();
        initIcons();
        applyModernStyling();

        initDragAndDrop();
        addDragAndDropVisualFeedback();

        loadUserProfile();
        showPage(PAGE_DASH);

        ThemeUtil.registerWindowForUpdates(this);

        // Initial credit display update
        updateCreditDisplay();
    }

    /**
     * Set window icon
     */
    /**
     * Set window icon with forced specific sizes
     */
    /**
     * Set window icon with proper scaling
     */
    private void setWindowIcon() {
        try {
            URL iconUrl = getClass().getClassLoader().getResource("Icons/app-icon.png");
            if (iconUrl != null) {
                ImageIcon originalIcon = new ImageIcon(iconUrl);
                Image originalImage = originalIcon.getImage();

                // Create properly scaled icons for different sizes
                java.util.List<Image> icons = new java.util.ArrayList<>();

                // Standard icon sizes for Windows
                int[] iconSizes = {16, 32, 48, 64, 128, 256};

                for (int size : iconSizes) {
                    try {
                        // Scale the image to the appropriate size
                        Image scaledImage = originalImage.getScaledInstance(
                                size, size, Image.SCALE_SMOOTH
                        );
                        icons.add(scaledImage);
                    } catch (Exception e) {
                        System.err.println("Failed to scale icon to size " + size + ": " + e.getMessage());
                    }
                }

                // If scaling failed, use the original image but scale it down
                if (icons.isEmpty()) {
                    // Force scale to reasonable sizes
                    Image smallImage = originalImage.getScaledInstance(32, 32, Image.SCALE_SMOOTH);
                    Image mediumImage = originalImage.getScaledInstance(64, 64, Image.SCALE_SMOOTH);
                    Image largeImage = originalImage.getScaledInstance(128, 128, Image.SCALE_SMOOTH);

                    icons.add(smallImage);
                    icons.add(mediumImage);
                    icons.add(largeImage);
                }

                setIconImages(icons);
                System.out.println("✅ Window icon set with " + icons.size() + " sizes");

            } else {
                System.err.println("❌ Icon not found: Icons/app-icon.png");
                // Create a fallback programmatic icon
                createFallbackIcon();
            }
        } catch (Exception e) {
            System.err.println("❌ Could not set window icon: " + e.getMessage());
            // Create a fallback programmatic icon
            createFallbackIcon();
        }
    }

    private void initDragAndDrop() {
        // Create a single DropTargetAdapter to handle both visual feedback and drops.
        DropTargetAdapter dtListener = new DropTargetAdapter() {
            @Override
            public void dragEnter(java.awt.dnd.DropTargetDragEvent dtde) {
                // Show visual cue
                showDragOverlay(true);
                // Accept drag if files are present
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }

            @Override
            public void dragExit(java.awt.dnd.DropTargetEvent dte) {
                showDragOverlay(false);
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                showDragOverlay(false);
                handleDrop(dtde);
            }
        };

        // Attach the same listener as the drop target to the components we want to accept files.
        try {
            // extractedArea
            extractedArea.setDropTarget(new DropTarget(extractedArea, DnDConstants.ACTION_COPY, dtListener, true, null));

            // metadataArea
            metadataArea.setDropTarget(new DropTarget(metadataArea, DnDConstants.ACTION_COPY, dtListener, true, null));

            // whole frame (so dropping anywhere in the window works)
            this.setDropTarget(new DropTarget(this, DnDConstants.ACTION_COPY, dtListener, true, null));
        } catch (Exception ex) {
            // fall back - but don't overwrite later
            ex.printStackTrace();
        }
    }


    // Main drop handler method
    private void handleDrop(DropTargetDropEvent dtde) {
        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);

            // Get the dropped files
            java.util.List<File> droppedFiles = (java.util.List<File>)
                    dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

            if (droppedFiles != null && !droppedFiles.isEmpty()) {
                processDroppedFiles(droppedFiles);
                dtde.dropComplete(true);
            } else {
                dtde.dropComplete(false);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            dtde.dropComplete(false);
            JOptionPane.showMessageDialog(this,
                    "Error processing dropped files: " + ex.getMessage(),
                    "Drag & Drop Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Process the dropped files/folders
    private void processDroppedFiles(java.util.List<File> droppedFiles) {
        java.util.List<File> allFiles = new java.util.ArrayList<>();

        for (File droppedFile : droppedFiles) {
            if (droppedFile.isDirectory()) {
                // If it's a folder, collect all supported documents from it
                allFiles.addAll(collectDocs(droppedFile));
            } else {
                // If it's a file, check if it's supported
                if (isSupportedFile(droppedFile)) {
                    allFiles.add(droppedFile);
                }
            }
        }

        if (allFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No supported documents found in the dropped files/folders.\n\n" +
                            "Supported formats: PDF, DOCX, DOC, PNG, JPG, JPEG, TIFF, BMP",
                    "No Supported Files",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (allFiles.size() == 1) {
            // Single file - process immediately
            processSingleFile(allFiles.get(0));
        } else {
            // Multiple files - ask if user wants batch processing
            handleMultipleFiles(allFiles);
        }
    }

    // Check if a file is supported
    private boolean isSupportedFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".doc") ||
                name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".tiff") || name.endsWith(".bmp");
    }

    // Process a single dropped file
    private void processSingleFile(File file) {
        try {
            setStatus("Processing dropped file: " + file.getName());

            // Upload the file
            Path storedPath = controller.uploadFile(file);
            currentSelectedPath = storedPath;

            // Auto-run the pipeline if user is signed in
            if (controller.isSignedIn()) {
                // Check credits before processing
                if (!controller.canProcessDocuments()) {
                    CreditService.UserCreditInfo info = controller.getUserCreditInfo();
                    if (info != null && info.isSuspended()) {
                        JOptionPane.showMessageDialog(this,
                                "Account suspended: " + info.getSuspensionReason(),
                                "Account Suspended",
                                JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Insufficient credits. Available: " + info.getCreditsBalance() + " credits\n\n" +
                                        "Click 'Get Free Credits' to add more credits.",
                                "Insufficient Credits",
                                JOptionPane.WARNING_MESSAGE);
                    }
                    return;
                }

                // Auto-run the pipeline
                runPipeline();
            } else {
                JOptionPane.showMessageDialog(this,
                        "File uploaded successfully: " + file.getName() + "\n\n" +
                                "Sign in to process the document.",
                        "Upload Complete",
                        JOptionPane.INFORMATION_MESSAGE);
            }

            refreshFileTable();
            refreshStorageTables();

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to process dropped file: " + ex.getMessage(),
                    "Processing Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Handle multiple dropped files
    private void handleMultipleFiles(java.util.List<File> files) {
        int result = JOptionPane.showConfirmDialog(this,
                "You dropped " + files.size() + " files.\n\n" +
                        "What would you like to do?\n" +
                        "• Upload all files\n" +
                        "• Process all files (batch mode)\n" +
                        "• Cancel",
                "Multiple Files Detected",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        switch (result) {
            case JOptionPane.YES_OPTION:
                // Upload all files
                uploadMultipleFiles(files);
                break;
            case JOptionPane.NO_OPTION:
                // Process all files (batch mode)
                processMultipleFiles(files);
                break;
            case JOptionPane.CANCEL_OPTION:
            default:
                // Cancel
                setStatus("Drag & drop cancelled");
                break;
        }
    }

    // Upload multiple files without processing
    private void uploadMultipleFiles(java.util.List<File> files) {
        int successCount = 0;
        int failCount = 0;

        for (File file : files) {
            try {
                controller.uploadFile(file);
                successCount++;
            } catch (Exception ex) {
                System.err.println("Failed to upload: " + file.getName() + " - " + ex.getMessage());
                failCount++;
            }
        }

        String message = "Upload completed:\n" +
                "• Successful: " + successCount + " files\n" +
                "• Failed: " + failCount + " files";

        if (failCount > 0) {
            JOptionPane.showMessageDialog(this, message, "Upload Results", JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, message, "Upload Complete", JOptionPane.INFORMATION_MESSAGE);
        }

        refreshFileTable();
        refreshStorageTables();
        setStatus("Uploaded " + successCount + " files");
    }

    // Process multiple files in batch mode
    private void processMultipleFiles(java.util.List<File> files) {
        // Check credits before batch processing
        if (!controller.canProcessDocuments()) {
            CreditService.UserCreditInfo info = controller.getUserCreditInfo();
            if (info != null && info.isSuspended()) {
                JOptionPane.showMessageDialog(this,
                        "Account suspended: " + info.getSuspensionReason(),
                        "Account Suspended",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Insufficient credits. Available: " + info.getCreditsBalance() + " credits\n\n" +
                                "Click 'Get Free Credits' to add more credits.",
                        "Insufficient Credits",
                        JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        // Estimate credits needed
        int estimatedCredits = files.size() * 10;
        CreditService.UserCreditInfo info = controller.getUserCreditInfo();
        if (info != null && info.getCreditsBalance() < estimatedCredits) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Estimated credits needed: " + estimatedCredits +
                            "\nYour balance: " + info.getCreditsBalance() +
                            "\n\nYou may run out of credits during processing. Continue?",
                    "Low Credit Warning",
                    JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) return;
        }

        // Confirm batch processing
        int confirm = JOptionPane.showConfirmDialog(this,
                "Process " + files.size() + " documents in batch mode?",
                "Confirm Batch Processing",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            controller.runBatch(
                    files,
                    msg -> SwingUtilities.invokeLater(() -> setStatus(msg)),
                    failures -> SwingUtilities.invokeLater(() -> {
                        if (failures.isEmpty()) {
                            JOptionPane.showMessageDialog(this,
                                    "Batch processing completed successfully!",
                                    "Batch Complete",
                                    JOptionPane.INFORMATION_MESSAGE);
                            updateCreditDisplay();
                        } else {
                            StringBuilder sb = new StringBuilder("Batch finished with some failures:\n\n");
                            failures.forEach((f, ex) -> sb.append("• ").append(f.getName()).append(": ")
                                    .append(ex.getMessage()).append("\n"));
                            JOptionPane.showMessageDialog(this, sb.toString(),
                                    "Batch Report", JOptionPane.WARNING_MESSAGE);
                        }
                        refreshFileTable();
                        refreshStorageTables();
                        setStatus("Batch processing completed");
                    })
            );
        }
    }

    private void addDragAndDropVisualFeedback() {
        // Add mouse listeners to show drag feedback
        if (dragOverlay == null) createDragOverlay();
    }

    private JPanel dragOverlay;
    private void createDragOverlay() {
        dragOverlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Semi-transparent background
                g2.setColor(new Color(70, 130, 180, 150));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Border
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawRoundRect(10, 10, getWidth() - 20, getHeight() - 20, 20, 20);

                // Text
                g2.setColor(Color.WHITE);
                g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
                String text = "Drop files here to process";
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                g2.drawString(text, (getWidth() - textWidth) / 2, getHeight() / 2);

                g2.dispose();
            }
        };

        // Make it fill entire frame and be transparent to mouse events when invisible
        dragOverlay.setOpaque(false);
        dragOverlay.setVisible(false);
        dragOverlay.setLayout(null); // we only paint on it

        // Use frame glass pane — this overlays the entire window without touching contentPane layout
        JRootPane root = getRootPane();
        root.setGlassPane(dragOverlay);
        // glass pane is invisible until we show it; showDragOverlay will toggle visibility
    }


    private void showDragOverlay(boolean show) {
        if (dragOverlay == null) return;
        // Because it is the glass pane, setVisible(true) will intercept mouse events — that's desired while dragging.
        dragOverlay.setVisible(show);
        dragOverlay.repaint();
    }

    /**
     * Create a simple fallback icon if the image file is not found
     */
    private void createFallbackIcon() {
        try {
            // Create a simple programmatic icon
            java.util.List<Image> icons = new java.util.ArrayList<>();
            int[] sizes = {16, 32, 48, 64};

            for (int size : sizes) {
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                        size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB
                );

                java.awt.Graphics2D g2d = image.createGraphics();

                // Enable anti-aliasing
                g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw background
                g2d.setColor(new Color(70, 130, 180)); // Steel blue
                g2d.fillRoundRect(0, 0, size, size, size/4, size/4);

                // Draw document symbol
                g2d.setColor(Color.WHITE);
                g2d.fillRoundRect(size/8, size/8, size-size/4, size-size/4, size/8, size/8);

                // Draw lines to represent text (only for larger icons)
                if (size >= 32) {
                    g2d.setColor(new Color(70, 130, 180));
                    int lineCount = Math.max(3, size / 10);
                    for (int i = 0; i < lineCount; i++) {
                        g2d.fillRect(size/6, size/4 + i * (size/8), size - size/3, Math.max(1, size/16));
                    }
                }

                g2d.dispose();
                icons.add(image);
            }

            setIconImages(icons);
            System.out.println("✅ Fallback icon created");

        } catch (Exception e) {
            System.err.println("❌ Could not create fallback icon: " + e.getMessage());
        }
    }

    /**
     * Helper method to create scaled image
     */
    private Image createScaledImage(Image original, int width, int height) {
        return original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    }

    private void initIcons() {
        // Icons with modern sizing
        navButton.setIcon(new FlatSVGIcon("Icons/menu.svg").derive(18, 18));
        uploadBtn.setIcon(new FlatSVGIcon("Icons/upload.svg").derive(18, 18));
        profileButton.setIcon(new FlatSVGIcon("Icons/profile.svg").derive(18, 18));
        runBtn.setIcon(new FlatSVGIcon("Icons/run.svg").derive(18, 18));
        saveBtn.setIcon(new FlatSVGIcon("Icons/save.svg").derive(18, 18));
        batchBtn.setIcon(new FlatSVGIcon("Icons/batch.svg").derive(18, 18));
        storageRefreshBtn.setIcon(new FlatSVGIcon("Icons/refresh.svg").derive(16, 16));
        storageSearchBtn.setIcon(new FlatSVGIcon("Icons/search.svg").derive(16, 16));
        freeCreditsBtn.setIcon(new FlatSVGIcon("Icons/credits.svg").derive(16, 16));
    }

    private void applyModernStyling() {
        // Style buttons consistently
        ComponentStyler.styleSecondary(runBtn);
        ComponentStyler.styleSecondary(saveBtn);
        ComponentStyler.styleSecondary(uploadBtn);
        ComponentStyler.styleSecondary(freeCreditsBtn);

        // Text areas with modern styling
        ComponentStyler.styleTextArea(extractedArea);
        ComponentStyler.styleTextArea(metadataArea);

        // Tables with modern styling
        ComponentStyler.styleTable(fileTable);
        ComponentStyler.styleTable(tInputs);
        ComponentStyler.styleTable(tExtracted);
        ComponentStyler.styleTable(tSummaries);

        // Modern placeholders & pill fields
        dbSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search by name, label, or notes…");
        dbSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("Icons/search.svg", 16, 16));
        dbSearch.putClientProperty(FlatClientProperties.STYLE, "arc: 20;");

        storageSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search in storage…");
        storageSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("Icons/search.svg", 16, 16));
        storageSearch.putClientProperty(FlatClientProperties.STYLE, "arc: 20;");

        // Modern button styling
        navButton.putClientProperty(FlatClientProperties.STYLE, "buttonType: borderless;");
        profileButton.putClientProperty(FlatClientProperties.STYLE, "buttonType: borderless;");
        uploadBtn.putClientProperty(FlatClientProperties.STYLE, "arc: 20;");
        runBtn.putClientProperty(FlatClientProperties.STYLE, "arc: 20;");
        saveBtn.putClientProperty(FlatClientProperties.STYLE, "arc: 20;");
        batchBtn.putClientProperty(FlatClientProperties.STYLE, "arc: 20;");
        freeCreditsBtn.putClientProperty(FlatClientProperties.STYLE, "arc: 20;");

        // Credit label styling
        creditBalanceLabel.setFont(creditBalanceLabel.getFont().deriveFont(Font.BOLD));
        creditBalanceLabel.setForeground(new Color(0, 100, 0));

        extractedArea.setToolTipText("<html>Drag and drop files or folders here to process<br>Supported: PDF, DOCX, DOC, PNG, JPG, JPEG, TIFF, BMP</html>");
        metadataArea.setToolTipText("<html>Drag and drop files or folders here to process<br>Supported: PDF, DOCX, DOC, PNG, JPG, JPEG, TIFF, BMP</html>");

        // Apply current theme to scrollbars
        applyThemeToScrollbars();
    }

    private void setDragDropStatus(String message) {
        setStatus("🔄 " + message);
        // Optional: Change status color temporarily
        statusLabel.setForeground(new Color(0, 100, 200));

        // Reset color after delay
        Timer colorResetTimer = new Timer(2000, e -> {
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        });
        colorResetTimer.setRepeats(false);
        colorResetTimer.start();
    }

    private void applyThemeToScrollbars() {
        // Find and update scrollbars for text areas
        Component parent = extractedArea.getParent();
        while (parent != null) {
            if (parent instanceof JScrollPane) {
                ThemeUtil.installSlimScrollbars((JScrollPane) parent, currentTheme);
                break;
            }
            parent = parent.getParent();
        }

        parent = metadataArea.getParent();
        while (parent != null) {
            if (parent instanceof JScrollPane) {
                ThemeUtil.installSlimScrollbars((JScrollPane) parent, currentTheme);
                break;
            }
            parent = parent.getParent();
        }
    }

    private void initLayout() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        // --- Modern Top bar ---
        JPanel topBar = createModernTopBar();
        root.add(topBar, BorderLayout.NORTH);

        // --- Card container for pages (modern rounded) ---
        CardPanel pagesCard = new CardPanel(new BorderLayout(10, 10));
        pagesCard.setBackground(UIManager.getColor("Panel.background"));
        pagesCard.add(pages, BorderLayout.CENTER);
        pagesCard.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(pagesCard, BorderLayout.CENTER);

        // --- Modern Navigation Menu ---
        initNavigationMenu();

        // --- Pages ---
        pages.add(createDashboardPanel(), PAGE_DASH);
        pages.add(createDatabasePanel(), PAGE_DB);
        pages.add(createStoragePanel(), PAGE_STORAGE);
        pages.add(createSettingsPanel(), PAGE_SETTINGS);

        // --- Profile popup ---
        initProfileMenu();

        // --- Admin popup ---
        initAdminMenu();

        // --- Credit popup ---
        initCreditMenu();

        // Modern Status Line
        JPanel status = createModernStatusBar();
        root.add(status, BorderLayout.SOUTH);
    }

    private JPanel createModernTopBar() {
        JPanel topBar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                var c1 = UIManager.getColor("Panel.background");
                var c2 = UIManager.getColor("Component.accentColor");
                if (c2 == null) c2 = new Color(80, 140, 255, 30);

                // Modern gradient with better blending
                var grad = new GradientPaint(0, 0,
                        new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 220),
                        getWidth(), getHeight(),
                        new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 80));
                g2.setPaint(grad);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftTop.setOpaque(false);
        leftTop.add(navButton);
        leftTop.add(uploadBtn);

        JLabel title = new JLabel("Smart Document Processor", SwingConstants.CENTER);
        Font baseLabel = UIManager.getFont("Label.font");
        if (baseLabel != null) {
            title.setFont(baseLabel.deriveFont(Font.BOLD, baseLabel.getSize2D() + 4f));
        }
        title.setForeground(UIManager.getColor("Label.foreground"));

        JPanel centerTop = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerTop.setOpaque(false);
        centerTop.add(title);

        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightTop.setOpaque(false);

        // Add credit components to right top
        rightTop.add(creditBalanceLabel);
        rightTop.add(freeCreditsBtn);

        profileButton.setPreferredSize(new Dimension(200, 40));
        profileButton.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc: 20;"
                + "background: $Component.accentColor;"
                + "foreground: white;"
                + "borderWidth: 0;");
        rightTop.add(profileButton);

        topBar.add(leftTop, BorderLayout.WEST);
        topBar.add(centerTop, BorderLayout.CENTER);
        topBar.add(rightTop, BorderLayout.EAST);

        topBar.putClientProperty("JComponent.roundRect", true);
        return topBar;
    }

    private void initNavigationMenu() {
        JPopupMenu navMenu = new JPopupMenu();
        navMenu.putClientProperty(FlatClientProperties.STYLE, ""
                + "background: $Menu.background;"
                + "borderColor: $Component.borderColor;"
                + "arc: 12;");

        JMenuItem mDashboard = createModernMenuItem("Dashboard", "Icons/dashboard.svg");
        JMenuItem mDatabase = createModernMenuItem("Database", "Icons/database.svg");
        JMenuItem mStorage = createModernMenuItem("Storage", "Icons/storage.svg");
        JMenuItem mSettings = createModernMenuItem("Settings", "Icons/settings.svg");

        navMenu.add(mDashboard);
        navMenu.add(mDatabase);
        navMenu.add(mStorage);
        navMenu.add(mSettings);

        navButton.addActionListener(e -> navMenu.show(navButton, 0, navButton.getHeight()));
        mDashboard.addActionListener(e -> showPage(PAGE_DASH));
        mDatabase.addActionListener(e -> { showPage(PAGE_DB); refreshFileTable(); });
        mStorage.addActionListener(e -> {
            showPage(PAGE_STORAGE);
            updateStorageHeader();
            refreshStorageTables(storageSearch.getText());
        });
        mSettings.addActionListener(e -> showPage(PAGE_SETTINGS));
    }

    private JMenuItem createModernMenuItem(String text, String iconPath) {
        JMenuItem menuItem = new JMenuItem(text);
        try {
            menuItem.setIcon(new FlatSVGIcon(iconPath).derive(16, 16));
        } catch (Exception e) {
            // Fallback if icon doesn't exist
        }
        menuItem.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc: 8;"
                + "selectionBackground: $Component.accentColor;"
                + "selectionForeground: white;");
        return menuItem;
    }

    private void initProfileMenu() {
        JMenuItem miProfile = createModernMenuItem("View profile", "Icons/profile.svg");
        JMenuItem miSignIn = createModernMenuItem("Sign in", "Icons/signin.svg");
        JMenuItem miSignOut = createModernMenuItem("Sign out", "Icons/signout.svg");

        profileMenu.add(miProfile);
        profileMenu.add(miSignIn);
        profileMenu.add(miSignOut);
        profileMenu.putClientProperty("JPopupMenu.translucentBackground", true);

        profileButton.addActionListener(e -> profileMenu.show(profileButton, 0, profileButton.getHeight()));

        miProfile.addActionListener(e -> showProfileDialog());
        miSignIn.addActionListener(e -> controller.signIn(this));
        miSignOut.addActionListener(e -> doSignOut());
    }

    private void initAdminMenu() {
        JMenuItem miAdminLogin = createModernMenuItem("Admin Login", "Icons/admin.svg");
        JMenuItem miAdminPanel = createModernMenuItem("Admin Panel", "Icons/dashboard.svg");
        JMenuItem miSystemStats = createModernMenuItem("System Statistics", "Icons/stats.svg");

        adminMenu.add(miAdminLogin);
        adminMenu.add(miAdminPanel);
        adminMenu.add(miSystemStats);
        adminMenu.putClientProperty("JPopupMenu.translucentBackground", true);

        miAdminLogin.addActionListener(e -> showAdminLogin());
        miAdminPanel.addActionListener(e -> showAdminPanel());
        miSystemStats.addActionListener(e -> showSystemStatistics());

        // Initially hide admin menu until admin logs in
        updateAdminMenuVisibility();
    }

    private void initCreditMenu() {
        JMenuItem miViewCredits = createModernMenuItem("View Credit History", "Icons/history.svg");
        JMenuItem miRefreshCredits = createModernMenuItem("Refresh Balance", "Icons/refresh.svg");

        creditMenu.add(miViewCredits);
        creditMenu.add(miRefreshCredits);
        creditMenu.putClientProperty("JPopupMenu.translucentBackground", true);

        // Add right-click to credit balance label
        creditBalanceLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    creditMenu.show(creditBalanceLabel, e.getX(), e.getY());
                }
            }
        });

        miViewCredits.addActionListener(e -> showCreditHistory());
        miRefreshCredits.addActionListener(e -> updateCreditDisplay());
    }

    private JPanel createModernStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, uiColorOr("Separator.foreground", new Color(0, 0, 0, 40))),
                new EmptyBorder(8, 16, 8, 16)
        ));

        // Modern status with optional progress indicator area
        JPanel statusContent = new JPanel(new BorderLayout());
        statusContent.setOpaque(false);
        statusContent.add(statusLabel, BorderLayout.WEST);

        status.add(statusContent, BorderLayout.CENTER);
        status.setBackground(UIManager.getColor("Panel.background"));

        return status;
    }

    // === CREDIT SYSTEM METHODS ===

    private void updateCreditDisplay() {
        if (!controller.isSignedIn()) {
            creditBalanceLabel.setText("Credits: --");
            creditBalanceLabel.setForeground(Color.GRAY);
            freeCreditsBtn.setEnabled(false);
            return;
        }

        try {
            CreditService.UserCreditInfo creditInfo = controller.getUserCreditInfo();
            if (creditInfo != null) {
                int credits = creditInfo.getCreditsBalance();
                boolean suspended = creditInfo.isSuspended();

                if (suspended) {
                    creditBalanceLabel.setText("SUSPENDED: " + credits + " credits");
                    creditBalanceLabel.setForeground(Color.RED);
                    freeCreditsBtn.setEnabled(false);
                } else if (credits <= 0) {
                    creditBalanceLabel.setText("Credits: 0 (Get more!)");
                    creditBalanceLabel.setForeground(Color.ORANGE);
                    freeCreditsBtn.setEnabled(true);
                } else if (credits < 20) {
                    creditBalanceLabel.setText("Credits: " + credits + " (Low)");
                    creditBalanceLabel.setForeground(Color.ORANGE);
                    freeCreditsBtn.setEnabled(true);
                } else {
                    creditBalanceLabel.setText("Credits: " + credits);
                    creditBalanceLabel.setForeground(new Color(0, 100, 0));
                    freeCreditsBtn.setEnabled(true);
                }
            }
        } catch (Exception e) {
            creditBalanceLabel.setText("Credits: Error");
            creditBalanceLabel.setForeground(Color.RED);
        }
    }

    private void addFreeCredits() {
        if (!controller.isSignedIn()) {
            JOptionPane.showMessageDialog(this, "Please sign in first.");
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "Get 100 free credits? This will add to your current balance.",
                "Free Credits",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            boolean success = controller.addFreeCredits();
            if (success) {
                JOptionPane.showMessageDialog(this,
                        "✅ 100 free credits added to your account!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                updateCreditDisplay();
                setStatus("Added 100 free credits");
            } else {
                JOptionPane.showMessageDialog(this,
                        "❌ Failed to add credits. Please try again.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showCreditHistory() {
        if (!controller.isSignedIn()) {
            JOptionPane.showMessageDialog(this, "Please sign in first.");
            return;
        }

        try {
            java.util.List<CreditService.CreditTransaction> transactions =
                    controller.getUserTransactions(controller.getCurrentUserId(), 20);

            if (transactions.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No transaction history found.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== CREDIT TRANSACTION HISTORY ===\n\n");

            for (CreditService.CreditTransaction tx : transactions) {
                String sign = tx.getAmount() >= 0 ? "+" : "";
                sb.append(String.format("%s%d credits", sign, tx.getAmount()))
                        .append(" - ").append(tx.getDescription())
                        .append("\n").append("Date: ").append(tx.getCreatedAt())
                        .append("\n\n");
            }

            JTextArea historyArea = new JTextArea(sb.toString());
            historyArea.setEditable(false);
            historyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

            JScrollPane scrollPane = new JScrollPane(historyArea);
            scrollPane.setPreferredSize(new Dimension(500, 300));

            JOptionPane.showMessageDialog(this, scrollPane,
                    "Credit History", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error loading credit history: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createDashboardPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));

        // Left: extracted text with modern styling
        extractedArea.setLineWrap(true);
        extractedArea.setWrapStyleWord(true);
        extractedArea.setFont(UIManager.getFont("TextArea.font"));
        JScrollPane extractedScroll = new JScrollPane(extractedArea);
        ThemeUtil.installSlimScrollbars(extractedScroll, currentTheme);
        extractedScroll.setBorder(BorderFactory.createTitledBorder("Extracted Text (.txt)"));
        extractedScroll.setPreferredSize(new Dimension(700, 500));
        extractedScroll.putClientProperty("JComponent.roundRect", true);
        extractedScroll.getViewport().setOpaque(false);
        extractedScroll.setOpaque(false);

        // Right: info + summary with modern styling
        JPanel metaPanel = new CardPanel(new BorderLayout(6, 6));
        metaPanel.setBackground(UIManager.getColor("Panel.background"));

        JPanel infoTop = new JPanel(new GridLayout(2, 1, 0, 4));
        infoTop.setOpaque(false);
        docNameLabel.setFont(docNameLabel.getFont().deriveFont(Font.BOLD));
        docTypeLabel.setFont(docTypeLabel.getFont().deriveFont(Font.BOLD));
        infoTop.add(docNameLabel);
        infoTop.add(docTypeLabel);
        metaPanel.add(infoTop, BorderLayout.NORTH);

        metadataArea.setLineWrap(true);
        metadataArea.setWrapStyleWord(true);
        metadataArea.setFont(UIManager.getFont("TextArea.font"));
        JScrollPane metaScroll = new JScrollPane(metadataArea);
        ThemeUtil.installSlimScrollbars(metaScroll, currentTheme);
        metaScroll.setBorder(BorderFactory.createTitledBorder("Summary / Details (.txt)"));
        metaPanel.add(metaScroll, BorderLayout.CENTER);
        metaScroll.putClientProperty("JComponent.roundRect", true);
        metaScroll.getViewport().setOpaque(false);
        metaScroll.setOpaque(false);

        // Modern control buttons
        JPanel controls = new JPanel(new GridLayout(1, 4, 8, 8));
        controls.setOpaque(false);
        runBtn.setMnemonic(KeyEvent.VK_R);
        saveBtn.setMnemonic(KeyEvent.VK_S);
        batchBtn.setMnemonic(KeyEvent.VK_B);
        controls.add(runBtn);
        controls.add(saveBtn);
        controls.add(batchBtn);
        metaPanel.add(controls, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, extractedScroll, metaPanel);
        split.setDividerSize(8);
        split.setContinuousLayout(true);
        p.add(split, BorderLayout.CENTER);

        // Wire actions
        uploadBtn.addActionListener(e -> doUpload());
        runBtn.addActionListener(e -> runPipeline());
        saveBtn.addActionListener(e -> saveResults());
        batchBtn.addActionListener(e -> runBatchProcessing());
        freeCreditsBtn.addActionListener(e -> addFreeCredits());

        return p;
    }

    private void runPipeline() {
        if (currentSelectedPath == null) {
            JOptionPane.showMessageDialog(this, "Select or upload a file first.");
            return;
        }

        // Check credits before processing
        if (!controller.canProcessDocuments()) {
            CreditService.UserCreditInfo info = controller.getUserCreditInfo();
            if (info != null && info.isSuspended()) {
                JOptionPane.showMessageDialog(this,
                        "Account suspended: " + info.getSuspensionReason(),
                        "Account Suspended",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Insufficient credits. Available: " + info.getCreditsBalance() + " credits\n\n" +
                                "Click 'Get Free Credits' to add more credits.",
                        "Insufficient Credits",
                        JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        runBtn.setEnabled(false);
        setStatus("Running pipeline…");
        controller.runPipeline(currentSelectedPath.toFile(), result -> {
            SwingUtilities.invokeLater(() -> {
                String sum = String.valueOf(result.getOrDefault("summary", "")).trim();
                if (sum.isEmpty()) sum = "[No summary returned]";
                extractedArea.setText(result.getOrDefault("extracted", "").toString());

                // Add credit information to the summary display
                int creditsUsed = (Integer) result.getOrDefault("credits_used", 0);
                int remainingCredits = (Integer) result.getOrDefault("remaining_credits", 0);

                String creditInfo = String.format("\n\n--- Credit Information ---\nUsed: %d credits\nRemaining: %d credits",
                        creditsUsed, remainingCredits);

                metadataArea.setText("Summary:\n" + sum + creditInfo);
                docNameLabel.setText("Document: " + currentSelectedPath.getFileName());
                docTypeLabel.setText("Type: " + result.getOrDefault("prediction", "Unknown"));
                runBtn.setEnabled(true);
                updateCreditDisplay(); // Refresh credit display
                setStatus("Done. Used " + creditsUsed + " credits.");
            });
        }, ex -> {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Pipeline error: " + ex.getMessage());
                runBtn.setEnabled(true);
                setStatus("Failed.");
            });
        });
    }

    private void saveResults() {
        if (currentSelectedPath == null) {
            JOptionPane.showMessageDialog(this, "No file selected to save.");
            return;
        }
        java.util.Map<String, Object> pipelineResult = new java.util.HashMap<>();
        pipelineResult.put("extracted", extractedArea.getText());
        pipelineResult.put("summary", metadataArea.getText());
        pipelineResult.put("prediction", docTypeLabel.getText().replace("Type: ", ""));
        pipelineResult.put("confidence", 1.0);

        try {
            controller.saveResults(currentSelectedPath.toFile(), pipelineResult);
            JOptionPane.showMessageDialog(this, "Saved.");
            refreshFileTable();
            refreshStorageTables();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
        }
    }

    private void runBatchProcessing() {
        // Check credits before batch processing
        if (!controller.canProcessDocuments()) {
            CreditService.UserCreditInfo info = controller.getUserCreditInfo();
            if (info != null && info.isSuspended()) {
                JOptionPane.showMessageDialog(this,
                        "Account suspended: " + info.getSuspensionReason(),
                        "Account Suspended",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Insufficient credits. Available: " + info.getCreditsBalance() + " credits\n\n" +
                                "Click 'Get Free Credits' to add more credits.",
                        "Insufficient Credits",
                        JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File folderOrFile = chooser.getSelectedFile();
        lastChooserDir = folderOrFile;

        java.util.List<File> docs = collectDocs(folderOrFile);
        if (docs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No supported documents found.");
            return;
        }

        // Estimate credits needed
        int estimatedCredits = docs.size() * 10; // Rough estimate
        CreditService.UserCreditInfo info = controller.getUserCreditInfo();
        if (info != null && info.getCreditsBalance() < estimatedCredits) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Estimated credits needed: " + estimatedCredits +
                            "\nYour balance: " + info.getCreditsBalance() +
                            "\n\nYou may run out of credits during processing. Continue?",
                    "Low Credit Warning",
                    JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) return;
        }

        if (JOptionPane.showConfirmDialog(this, "Process " + docs.size() + " documents?",
                "Confirm Batch", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        controller.runBatch(
                docs,
                msg -> SwingUtilities.invokeLater(() -> setStatus(msg)),
                failures -> SwingUtilities.invokeLater(() -> {
                    if (failures.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Batch completed successfully.");
                        updateCreditDisplay(); // Refresh credit display
                    } else {
                        StringBuilder sb = new StringBuilder("Batch finished with failures:\n");
                        failures.forEach((f, ex) -> sb.append(f.getName()).append(": ")
                                .append(ex.getMessage()).append("\n"));
                        JOptionPane.showMessageDialog(this, sb.toString(), "Batch Report", JOptionPane.WARNING_MESSAGE);
                    }
                    refreshFileTable();
                    refreshStorageTables();
                    setStatus("Ready.");
                })
        );
    }

    private JPanel createDatabasePanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));

        // Modern search panel
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setBorder(new EmptyBorder(0, 0, 8, 0));

        dbSearch.setToolTipText("Search files");
        top.add(dbSearch, BorderLayout.CENTER);

        JButton searchBtn = new JButton("Search");
        searchBtn.putClientProperty(FlatClientProperties.STYLE, "arc: 20;");
        top.add(searchBtn, BorderLayout.EAST);

        searchBtn.addActionListener(e -> refreshFileTable(dbSearch.getText()));
        dbSearch.addActionListener(e -> refreshFileTable(dbSearch.getText()));

        p.add(top, BorderLayout.NORTH);

        fileTable.setModel(fileTableModel);
        styleTableModern(fileTable);
        hidePathColumnIfPresent(fileTable);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane tscroll = new JScrollPane(fileTable);
        ThemeUtil.installSlimScrollbars(tscroll, currentTheme);
        p.add(tscroll, BorderLayout.CENTER);

        initFileTableContextMenu();

        return p;
    }

    private void initFileTableContextMenu() {
        JPopupMenu fileMenu = new JPopupMenu();
        fileMenu.putClientProperty(FlatClientProperties.STYLE, "arc: 12;");

        JMenuItem miOpen = createModernMenuItem("Open", "Icons/open.svg");
        JMenuItem miEdit = createModernMenuItem("Edit", "Icons/edit.svg");
        JMenuItem miRename = createModernMenuItem("Rename", "Icons/rename.svg");
        JMenuItem miDelete = createModernMenuItem("Delete", "Icons/delete.svg");
        JMenuItem miOpenLoc = createModernMenuItem("Open location", "Icons/location.svg");
        JMenuItem miRun = createModernMenuItem("Run", "Icons/run.svg");

        fileMenu.add(miOpen);
        fileMenu.add(miEdit);
        fileMenu.add(miRename);
        fileMenu.add(miDelete);
        fileMenu.addSeparator();
        fileMenu.add(miRun);
        fileMenu.add(miOpenLoc);

        fileTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = fileTable.rowAtPoint(e.getPoint());
                if (row < 0) return;
                fileTable.getSelectionModel().setSelectionInterval(row, row);
                currentSelectedPath = fileTableModel.getPathAt(row);
                if (e.getClickCount() == 2) {
                    doOpenFile(currentSelectedPath);
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    fileMenu.show(fileTable, e.getX(), e.getY());
                }
            }
        });

        miOpen.addActionListener(e -> { if (currentSelectedPath != null) doOpenFile(currentSelectedPath); });
        miEdit.addActionListener(e -> doEditMetadata());
        miRename.addActionListener(e -> doRenameFile());
        miDelete.addActionListener(e -> doDeleteFile());
        miRun.addActionListener(e -> runPipeline());
        miOpenLoc.addActionListener(e -> {
            if (currentSelectedPath != null) openContainingFolder(currentSelectedPath);
        });
    }

    private JPanel createStoragePanel() {
        JPanel root = new JPanel(new BorderLayout(6, 6));

        // Modern header with user info and controls
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setBorder(new EmptyBorder(0, 0, 8, 0));

        storageUserLabel = new JLabel();
        storageUserLabel.setFont(storageUserLabel.getFont().deriveFont(Font.BOLD));
        updateStorageHeader();

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        storageSearch.setColumns(22);
        right.add(storageSearch);
        right.add(storageSearchBtn);
        right.add(storageRefreshBtn);

        top.add(storageUserLabel, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        // Modern tabbed pane
        JTabbedPane tabs = new JTabbedPane();
        tabs.putClientProperty(FlatClientProperties.STYLE, ""
                + "tab.background: $Card.background;"
                + "tab.foreground: $Text.primary;"
                + "tab.selectedBackground: $Component.accentColor;"
                + "tab.selectedForeground: white;"
                + "tab.arc: 12;");

        tabs.addTab("Inputs", new CardPanel(new BorderLayout()) {{ add(new JScrollPane(tInputs), BorderLayout.CENTER); }});
        tabs.addTab("Extracted", new CardPanel(new BorderLayout()) {{ add(new JScrollPane(tExtracted), BorderLayout.CENTER); }});
        tabs.addTab("Summaries", new CardPanel(new BorderLayout()) {{ add(new JScrollPane(tSummaries), BorderLayout.CENTER); }});
        root.add(tabs, BorderLayout.CENTER);

        initStorageTable(tInputs);
        initStorageTable(tExtracted);
        initStorageTable(tSummaries);

        styleTableModern(tInputs);
        styleTableModern(tExtracted);
        styleTableModern(tSummaries);

        // Wire actions
        storageRefreshBtn.addActionListener(e -> refreshStorageTables());
        storageSearchBtn.addActionListener(e -> refreshStorageTables(storageSearch.getText()));
        storageSearch.addActionListener(e -> refreshStorageTables(storageSearch.getText()));

        addFileTableInteractions(tInputs);
        addFileTableInteractions(tExtracted);
        addFileTableInteractions(tSummaries);

        return root;
    }

    private JPanel createSettingsPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Theme selection
        JPanel themePanel = createModernSettingPanel("Appearance", "Icons/theme.svg");
        themeCombo.setSelectedItem("Light");
        themeCombo.addActionListener(e -> applyThemeSettings());
        themePanel.add(themeCombo);

        // Font selection
        JPanel fontPanel = createModernSettingPanel("Font", "Icons/font.svg");
        fontCombo.setSelectedItem(currentFontFamily);
        fontSizeCombo.setSelectedItem(String.valueOf(currentFontSize));

        JPanel fontControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fontControls.setOpaque(false);
        fontControls.add(fontCombo);
        fontControls.add(fontSizeCombo);
        fontPanel.add(fontControls);

        // Density settings
        JPanel densityPanel = createModernSettingPanel("Density", "Icons/density.svg");
        JSlider density = new JSlider(0, 2, 1);
        density.setPaintTicks(true);
        density.setPaintLabels(true);
        density.setMajorTickSpacing(1);
        density.setSnapToTicks(true);
        density.addChangeListener(e -> {
            int v = density.getValue();
            int row = (v == 0 ? 26 : v == 1 ? 32 : 38);
            fileTable.setRowHeight(row);
            tInputs.setRowHeight(row);
            tExtracted.setRowHeight(row);
            tSummaries.setRowHeight(row);
        });
        densityPanel.add(density);

        // Danger zone
        JPanel dangerPanel = createModernSettingPanel("Danger Zone", "Icons/warning.svg");
        clearDbBtn.putClientProperty(FlatClientProperties.STYLE, ""
                + "background: #dc3545;"
                + "foreground: white;"
                + "arc: 20;");
        dangerPanel.add(clearDbBtn);

        // Layout all panels
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(themePanel);
        content.add(Box.createVerticalStrut(16));
        content.add(fontPanel);
        content.add(Box.createVerticalStrut(16));
        content.add(densityPanel);
        content.add(Box.createVerticalStrut(24));
        content.add(dangerPanel);

        p.add(content, BorderLayout.NORTH);

        // Clear DB action
        clearDbBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this, "Are you sure? This deletes the local database.", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;
            int r2 = JOptionPane.showConfirmDialog(this, "Really sure? This is irreversible.", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r2 != JOptionPane.YES_OPTION) return;
            try {
                var s = controller.getStorage();
                if (s != null) {
                    s.clearDatabase();
                    JOptionPane.showMessageDialog(this, "Database cleared.");
                    refreshFileTable("");
                    refreshStorageTables("");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Clear failed: " + ex.getMessage());
            }
        });

        return p;
    }

    private JPanel createModernSettingPanel(String title, String iconPath) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        try {
            titleLabel.setIcon(new FlatSVGIcon(iconPath).derive(16, 16));
        } catch (Exception e) {
            // Icon not available
        }

        panel.add(titleLabel, BorderLayout.NORTH);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        controlPanel.setOpaque(false);
        controlPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        panel.add(controlPanel, BorderLayout.CENTER);

        return panel;
    }

    private void applyThemeSettings() {
        String themeName = (String) themeCombo.getSelectedItem();
        String fontFamily = (String) fontCombo.getSelectedItem();
        int fontSize = Integer.parseInt((String) fontSizeCombo.getSelectedItem());

        // Apply theme
        AppTheme newTheme;
        switch (themeName) {
            case "Dark":
                newTheme = AppTheme.NIGHTFALL;
                break;
            case "Blue":
                newTheme = AppTheme.OCEAN;
                break;
            case "High Contrast":
                newTheme = AppTheme.HIGH_CONTRAST;
                break;
            default:
                newTheme = AppTheme.DAYLIGHT;
        }

        currentTheme = newTheme;
        currentFontFamily = fontFamily;
        currentFontSize = fontSize;

        // Apply theme first
        ThemeUtil.applyTheme(newTheme);

        // Then apply font
        ThemeUtil.setGlobalFont(fontFamily + ", Segoe UI, SansSerif", fontSize);

        // Re-apply component-specific styling
        reapplyComponentStyling();

        // Force refresh of all tables and text areas
        refreshComponentStyles();

        setStatus("Theme updated to " + themeName + " with " + fontFamily + " font");
    }

    private void reapplyComponentStyling() {
        // Re-apply modern styling to ensure consistency
        applyModernStyling();

        // Refresh text areas
        ComponentStyler.styleTextArea(extractedArea);
        ComponentStyler.styleTextArea(metadataArea);

        // Refresh tables
        ComponentStyler.styleTable(fileTable);
        ComponentStyler.styleTable(tInputs);
        ComponentStyler.styleTable(tExtracted);
        ComponentStyler.styleTable(tSummaries);

        // Refresh buttons
        ComponentStyler.styleSecondary(runBtn);
        ComponentStyler.styleSecondary(saveBtn);
        ComponentStyler.styleSecondary(uploadBtn);
        ComponentStyler.styleSecondary(freeCreditsBtn);
    }

    private void refreshComponentStyles() {
        // Force refresh of all components
        SwingUtilities.invokeLater(() -> {
            // Update all major containers
            pages.revalidate();
            pages.repaint();

            // Update text areas
            extractedArea.revalidate();
            extractedArea.repaint();
            metadataArea.revalidate();
            metadataArea.repaint();

            // Update tables
            fileTable.revalidate();
            fileTable.repaint();
            tInputs.revalidate();
            tInputs.repaint();
            tExtracted.revalidate();
            tExtracted.repaint();
            tSummaries.revalidate();
            tSummaries.repaint();

            // Update the main frame
            revalidate();
            repaint();
        });
    }

    // === ADMIN FEATURES ===

    private void showAdminLogin() {
        AdminLoginDialog loginDialog = new AdminLoginDialog(this, controller);
        loginDialog.setVisible(true);

        if (loginDialog.isAuthenticated()) {
            // Set admin email in token manager temporarily
            controller.getTokenManager().setCurrentUserId("admin@legalapp.com");
            updateAdminMenuVisibility();
            JOptionPane.showMessageDialog(this,
                    "Admin mode activated!",
                    "Admin Access",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showAdminPanel() {
        if (!controller.isCurrentUserAdmin()) {
            JOptionPane.showMessageDialog(this,
                    "Please login as admin first",
                    "Admin Access Required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFrame adminFrame = new JFrame("Admin Panel - Legal Document Classifier");
        adminFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        adminFrame.setSize(800, 600);
        adminFrame.setLocationRelativeTo(this);

        AdminPanel adminPanel = new AdminPanel(controller);
        adminFrame.add(adminPanel);
        adminFrame.setVisible(true);
    }

    private void showSystemStatistics() {
        if (!controller.isCurrentUserAdmin()) {
            JOptionPane.showMessageDialog(this,
                    "Please login as admin first",
                    "Admin Access Required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            java.util.List<String> stats = controller.getSystemStatistics();
            StringBuilder sb = new StringBuilder();
            sb.append("=== SYSTEM STATISTICS ===\n\n");
            for (String stat : stats) {
                sb.append(stat).append("\n");
            }

            JTextArea statsArea = new JTextArea(sb.toString());
            statsArea.setEditable(false);
            statsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

            JScrollPane scrollPane = new JScrollPane(statsArea);
            scrollPane.setPreferredSize(new Dimension(500, 300));

            JOptionPane.showMessageDialog(this, scrollPane,
                    "System Statistics", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error loading statistics: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateAdminMenuVisibility() {
        boolean isAdmin = controller.isCurrentUserAdmin();

        // Show/hide admin menu items in profile menu
        JMenuBar menuBar = getJMenuBar();
        if (menuBar != null) {
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu menu = menuBar.getMenu(i);
                if ("Admin".equals(menu.getText())) {
                    menu.setVisible(isAdmin);
                    break;
                }
            }
        }
    }

    private static Color uiColorOr(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private void initStorageTable(JTable t) {
        t.setModel(new javax.swing.table.DefaultTableModel(
                new Object[][]{},
                new String[]{"Name", "Modified", "Size"}
        ) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setAutoCreateRowSorter(true);
        t.setRowHeight(32);
    }

    private void updateStorageHeader() {
        String email = tokenManager.getCurrentUserId();
        storageUserLabel.setText("User: " + (email == null || email.isBlank() ? "(not signed in)" : email));
    }

    private void addFileTableInteractions(JTable t) {
        JPopupMenu menu = new JPopupMenu();
        menu.putClientProperty(FlatClientProperties.STYLE, "arc: 12;");

        JMenuItem miOpen = createModernMenuItem("Open", "Icons/open.svg");
        JMenuItem miRun = createModernMenuItem("Run", "Icons/run.svg");
        JMenuItem miRename = createModernMenuItem("Rename", "Icons/rename.svg");
        JMenuItem miDelete = createModernMenuItem("Delete", "Icons/delete.svg");
        JMenuItem miOpenLoc = createModernMenuItem("Open location", "Icons/location.svg");

        menu.add(miOpen);
        menu.add(miRun);
        menu.addSeparator();
        menu.add(miRename);
        menu.add(miDelete);
        menu.addSeparator();
        menu.add(miOpenLoc);

        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = t.rowAtPoint(e.getPoint());
                if (row >= 0) t.getSelectionModel().setSelectionInterval(row, row);
                if (e.getClickCount() == 2 && row >= 0) {
                    Path p = pathFromStorageTable(t, row);
                    if (p != null) doOpenFile(p);
                }
                if (SwingUtilities.isRightMouseButton(e) && row >= 0) {
                    menu.show(t, e.getX(), e.getY());
                }
            }
        });

        miOpen.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row >= 0) {
                Path p = pathFromStorageTable(t, row);
                if (p != null) doOpenFile(p);
            }
        });

        miRun.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row >= 0) {
                Path p = pathFromStorageTable(t, row);
                if (p == null) return;

                // Check credits before running
                if (!controller.canProcessDocuments()) {
                    CreditService.UserCreditInfo info = controller.getUserCreditInfo();
                    if (info != null && info.isSuspended()) {
                        JOptionPane.showMessageDialog(this,
                                "Account suspended: " + info.getSuspensionReason(),
                                "Account Suspended",
                                JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Insufficient credits. Available: " + info.getCreditsBalance() + " credits",
                                "Insufficient Credits",
                                JOptionPane.WARNING_MESSAGE);
                    }
                    return;
                }

                runBtn.setEnabled(false);
                controller.runPipeline(p.toFile(), result -> {
                    SwingUtilities.invokeLater(() -> {
                        extractedArea.setText(String.valueOf(result.getOrDefault("extracted","")));
                        String sum = String.valueOf(result.getOrDefault("summary","")).trim();
                        if (sum.isEmpty()) sum = "[No summary returned]";

                        // Add credit information
                        int creditsUsed = (Integer) result.getOrDefault("credits_used", 0);
                        int remainingCredits = (Integer) result.getOrDefault("remaining_credits", 0);
                        String creditInfo = String.format("\n\n--- Credit Information ---\nUsed: %d credits\nRemaining: %d credits",
                                creditsUsed, remainingCredits);

                        metadataArea.setText("Summary:\n" + sum + creditInfo);
                        docNameLabel.setText("Document: " + p.getFileName());
                        docTypeLabel.setText("Type: " + result.getOrDefault("prediction","Unknown"));
                        runBtn.setEnabled(true);
                        updateCreditDisplay();
                        showPage(PAGE_DASH);
                    });
                }, ex -> SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Pipeline error: " + ex.getMessage());
                    runBtn.setEnabled(true);
                }));
            }
        });

        miRename.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row >= 0) {
                Path p = pathFromStorageTable(t, row);
                if (p != null) doRenamePath(p);
            }
        });
        miDelete.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row >= 0) {
                Path p = pathFromStorageTable(t, row);
                if (p != null) doDeleteByPath(p);
            }
        });
        miOpenLoc.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row >= 0) {
                Path p = pathFromStorageTable(t, row);
                if (p != null) openContainingFolder(p);
            }
        });
    }

    private Path pathFromStorageTable(JTable t, int viewRow) {
        int modelRow = t.convertRowIndexToModel(viewRow);
        if (t == tInputs) {
            if (modelRow >= 0 && modelRow < inputsBacking.size()) return inputsBacking.get(modelRow);
        } else if (t == tExtracted) {
            if (modelRow >= 0 && modelRow < extractedBacking.size()) return extractedBacking.get(modelRow);
        } else if (t == tSummaries) {
            if (modelRow >= 0 && modelRow < summariesBacking.size()) return summariesBacking.get(modelRow);
        }
        return null;
    }

    private void doRenamePath(Path p) {
        String name = JOptionPane.showInputDialog(this, "New name:", p.getFileName().toString());
        if (name == null || name.isBlank()) return;
        try {
            controller.getStorage().renameFile(p, name);
            refreshStorageTables();
            refreshFileTable();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Rename failed: " + ex.getMessage());
        }
    }

    private void doDeleteByPath(Path p) {
        int r = JOptionPane.showConfirmDialog(this, "Delete this file and its DB record?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        try {
            controller.getStorage().deleteByPath(p.toString());
            refreshStorageTables();
            refreshFileTable();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Delete failed: " + ex.getMessage());
        }
    }

    private void refreshStorageTables(String query) {
        try {
            StorageManager s = controller.getStorage();
            if (s == null) return;
            String owner = controller.getCurrentUserId();

            java.util.List<Path> inputs    = (query == null || query.isBlank())
                    ? s.listStoredInputsByOwner(owner)
                    : s.searchStoredInputsByOwner(owner, query);

            java.util.List<Path> extracted = (query == null || query.isBlank())
                    ? s.listExtractedByOwner(owner)
                    : s.searchExtractedByOwner(owner, query);

            java.util.List<Path> summaries = (query == null || query.isBlank())
                    ? s.listSummariesByOwner(owner)
                    : s.searchSummariesByOwner(owner, query);

            populateInputsTable(inputs);
            populateExtractedTable(extracted);
            populateSummariesTable(summaries);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Load storage failed: " + e.getMessage());
        }
    }

    private void populateInputsTable(java.util.List<Path> paths) {
        inputsBacking = new java.util.ArrayList<>(paths);
        populateGenericTable(tInputs, paths);
    }

    private void populateExtractedTable(java.util.List<Path> paths) {
        extractedBacking = new java.util.ArrayList<>(paths);
        populateGenericTable(tExtracted, paths);
    }

    private void populateSummariesTable(java.util.List<Path> paths) {
        summariesBacking = new java.util.ArrayList<>(paths);
        populateGenericTable(tSummaries, paths);
    }

    private void populateGenericTable(JTable table, java.util.List<Path> paths) {
        var model = (javax.swing.table.DefaultTableModel) table.getModel();
        model.setRowCount(0);
        for (Path p : paths) {
            try {
                String name = p.getFileName().toString();
                String mod = formatUserFriendlyDateTime(
                        java.time.Instant.ofEpochMilli(java.nio.file.Files.getLastModifiedTime(p).toMillis()).toString()
                );

                long sizeBytes = java.nio.file.Files.exists(p) ? java.nio.file.Files.size(p) : -1;
                String sizeFormatted = sizeBytes >= 0 ? formatFileSize(sizeBytes) : "N/A";

                model.addRow(new Object[]{name, mod, sizeFormatted});
            } catch (Exception ignored) {
                model.addRow(new Object[]{p.getFileName().toString(), "N/A", "N/A"});
            }
        }
    }

    // Add these helper methods to MainWindowUI.java
    private String formatUserFriendlyDateTime(String isoTimestamp) {
        // Use the same method as above or call StorageManager.formatUserFriendlyDateTime
        try {
            java.time.Instant instant = java.time.Instant.parse(isoTimestamp);
            java.time.LocalDateTime localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
            return localDateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"));
        } catch (Exception e) {
            return isoTimestamp;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private void initActions() {
        profileButton.setText(tokenManager.getCurrentUserId() != null ? tokenManager.getCurrentUserId() : "Sign in");
      //  extractedArea.setDropTarget(new java.awt.dnd.DropTarget());

        // Add admin menu to profile button right-click
        profileButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    adminMenu.show(profileButton, e.getX(), e.getY());
                }
            }
        });
    }

    private void loadUserProfile() {
        boolean signedIn = tokenManager.getCurrentUserId() != null && !tokenManager.getCurrentUserId().isBlank();
        profileButton.setText(signedIn ? tokenManager.getCurrentUserId() : "Sign in");
        uploadBtn.setEnabled(signedIn);
        runBtn.setEnabled(signedIn);
        saveBtn.setEnabled(signedIn);
        batchBtn.setEnabled(signedIn);
        freeCreditsBtn.setEnabled(signedIn);
        dbSearch.setEnabled(signedIn);
        fileTable.setEnabled(signedIn);

        updateAdminMenuVisibility();
        updateCreditDisplay();
    }

    private void showProfileDialog() {
        String email = tokenManager.getCurrentUserId();
        CreditService.UserCreditInfo creditInfo = controller.getUserCreditInfo();

        String message = "Signed in as: " + (email == null ? "(not signed in)" : email);
        if (creditInfo != null) {
            message += "\nCredits: " + creditInfo.getCreditsBalance();
            if (creditInfo.isSuspended()) {
                message += "\nStatus: SUSPENDED - " + creditInfo.getSuspensionReason();
            } else {
                message += "\nStatus: Active";
            }
        }

        // FIX: Create a properly sized dialog
        JTextArea textArea = new JTextArea(message);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(UIManager.getColor("OptionPane.background"));

        // Set reasonable size
        textArea.setPreferredSize(new Dimension(300, 120));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(350, 150));
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JOptionPane.showMessageDialog(this, scrollPane,
                "Profile Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void doSignOut() {
        // FIX: Use a properly sized confirmation dialog
        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to sign out?",
                "Confirm Sign Out",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            controller.signOut();
        }
    }

    private void doUpload() {
        JFileChooser chooser = new JFileChooser(lastChooserDir);
        chooser.setDialogTitle("Upload document");
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);

        String[] ALL_EXT = { "pdf","docx","doc","png","jpg","jpeg","tiff","bmp" };
        javax.swing.filechooser.FileNameExtensionFilter all =
                new javax.swing.filechooser.FileNameExtensionFilter(
                        "Supported docs (*.pdf, *.docx, *.doc, *.png, *.jpg, *.jpeg, *.tiff, *.bmp)", ALL_EXT);
        javax.swing.filechooser.FileNameExtensionFilter pdf =
                new javax.swing.filechooser.FileNameExtensionFilter("PDF documents (*.pdf)", "pdf");
        javax.swing.filechooser.FileNameExtensionFilter word =
                new javax.swing.filechooser.FileNameExtensionFilter("Word documents (*.docx, *.doc)", "docx","doc");
        javax.swing.filechooser.FileNameExtensionFilter images =
                new javax.swing.filechooser.FileNameExtensionFilter(
                        "Images (*.png, *.jpg, *.jpeg, *.tiff, *.bmp)", "png","jpg","jpeg","tiff","bmp");

        chooser.resetChoosableFileFilters();
        chooser.addChoosableFileFilter(all);
        chooser.addChoosableFileFilter(pdf);
        chooser.addChoosableFileFilter(word);
        chooser.addChoosableFileFilter(images);
        chooser.setFileFilter(all);

        chooser.setAccessory(new org.example.ui.widgets.FilePreviewAccessory(chooser));

        int r = chooser.showOpenDialog(this);
        if (r != JFileChooser.APPROVE_OPTION) return;

        File f = chooser.getSelectedFile();
        lastChooserDir = f.getParentFile();

        try {
            Path stored = controller.uploadFile(f);
            currentSelectedPath = stored;
            JOptionPane.showMessageDialog(this, "Uploaded: " + stored.getFileName());
            refreshFileTable();
            refreshStorageTables();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Upload failed: " + e.getMessage());
        }
    }

    private void doOpenFile(Path p) {
        try { Desktop.getDesktop().open(p.toFile()); }
        catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Open failed: " + e.getMessage());
        }
    }

    private void doEditMetadata() {
        if (currentSelectedPath == null) { JOptionPane.showMessageDialog(this, "Select a file first."); return; }
        String notes = JOptionPane.showInputDialog(this, "Edit notes/metadata:", "", JOptionPane.PLAIN_MESSAGE);
        if (notes == null) return;
        try {
            controller.getStorage().updateNotesByPath(currentSelectedPath.toString(), notes);
            refreshFileTable();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Edit failed: " + e.getMessage());
        }
    }

    private void doRenameFile() {
        if (currentSelectedPath == null) { JOptionPane.showMessageDialog(this, "Select a file first."); return; }
        String name = JOptionPane.showInputDialog(this, "New name:", currentSelectedPath.getFileName().toString());
        if (name == null || name.isBlank()) return;
        try {
            controller.getStorage().renameFile(currentSelectedPath, name);
            refreshFileTable();
            refreshStorageTables();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Rename failed: " + e.getMessage());
        }
    }

    private void doDeleteFile() {
        if (currentSelectedPath == null) { JOptionPane.showMessageDialog(this, "Select a file first."); return; }
        if (JOptionPane.showConfirmDialog(this, "Delete this file? This removes stored copies and the DB record.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            controller.getStorage().deleteByPath(currentSelectedPath.toString());
            currentSelectedPath = null;
            refreshFileTable();
            refreshStorageTables();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Delete failed: " + e.getMessage());
        }
    }

    private void refreshFileTable(String query) {
        StorageManager s = controller.getStorage();
        if (s == null) { fileTableModel.setRowsEmpty(); return; }

        String owner = tokenManager.getCurrentUserId();
        if (owner == null || owner.isBlank()) {
            fileTableModel.setRowsEmpty();
            return;
        }
        try {
            ResultSet rs = (query == null || query.isBlank())
                    ? s.listByOwner(owner)
                    : s.searchDocumentsByOwner(owner, query);
            fileTableModel.loadFromResultSet(rs);
        } catch (Exception e) {
            e.printStackTrace();
            fileTableModel.setRowsEmpty();
        }
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    private void showPage(String page) {
        CardLayout cl = (CardLayout) pages.getLayout();
        cl.show(pages, page);
    }

    public void showUI() { setVisible(true); }

    public void onSignedOut() {
        profileButton.setText("Sign in");
        uploadBtn.setEnabled(false);
        runBtn.setEnabled(false);
        saveBtn.setEnabled(false);
        batchBtn.setEnabled(false);
        freeCreditsBtn.setEnabled(false);
        dbSearch.setEnabled(false);
        fileTable.setEnabled(false);

        updateStorageHeader();
        updateAdminMenuVisibility();
        updateCreditDisplay();

        extractedArea.setText("");
        metadataArea.setText("");
        docNameLabel.setText("Document: -");
        docTypeLabel.setText("Type: -");

        fileTableModel.setRowsEmpty();
        ((javax.swing.table.DefaultTableModel) tInputs.getModel()).setRowCount(0);
        ((javax.swing.table.DefaultTableModel) tExtracted.getModel()).setRowCount(0);
        ((javax.swing.table.DefaultTableModel) tSummaries.getModel()).setRowCount(0);

        inputsBacking.clear();
        extractedBacking.clear();
        summariesBacking.clear();

        setStatus("Signed out.");
    }

    public void onSignedIn(String email) {
        profileButton.setText(email != null && !email.isBlank() ? email : "Signed in");
        uploadBtn.setEnabled(true);
        runBtn.setEnabled(true);
        saveBtn.setEnabled(true);
        batchBtn.setEnabled(true);
        freeCreditsBtn.setEnabled(true);
        dbSearch.setEnabled(true);
        fileTable.setEnabled(true);

        updateStorageHeader();
        updateAdminMenuVisibility();
        updateCreditDisplay();
        refreshFileTable();
        refreshStorageTables();
        setStatus("Welcome " + (email == null ? "" : email) + ".");
    }

    private static final java.util.Set<String> SUPPORTED_EXTS =
            new java.util.HashSet<>(java.util.Arrays.asList("pdf", "png", "jpg", "jpeg", "tiff", "bmp", "docx", "doc"));

    private java.util.List<File> collectDocs(File root) {
        java.util.List<File> out = new java.util.ArrayList<>();
        if (root == null || !root.exists()) return out;

        if (root.isFile()) {
            String name = root.getName().toLowerCase();
            int dot = name.lastIndexOf('.');
            String ext = (dot >= 0) ? name.substring(dot + 1) : "";
            if (SUPPORTED_EXTS.contains(ext)) out.add(root);
            return out;
        }
        File[] children = root.listFiles();
        if (children == null) return out;
        for (File child : children) out.addAll(collectDocs(child));
        return out;
    }

    private void refreshFileTable() {
        refreshFileTable(null);
    }

    private void refreshStorageTables() {
        refreshStorageTables(null);
    }

    private void hidePathColumnIfPresent(JTable t) {
        var cm = t.getColumnModel();
        for (int i = cm.getColumnCount() - 1; i >= 0; i--) {
            var col = cm.getColumn(i);
            String name = String.valueOf(col.getHeaderValue());
            if ("path".equalsIgnoreCase(name)) {
                cm.removeColumn(col);
                break;
            }
        }
    }

    private void openContainingFolder(Path p) {
        try {
            if (p == null) return;
            File f = p.toFile();
            File dir = f.getParentFile();
            if (dir == null) dir = f;

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", f.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", f.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
            }
        } catch (Exception ex) {
            try {
                Desktop.getDesktop().open(p.getParent() != null ? p.getParent().toFile() : p.toFile());
            } catch (Exception ignored) {
                JOptionPane.showMessageDialog(this, "Could not open location.");
            }
        }
    }

    private void styleTableModern(JTable t) {
        t.setRowHeight(32);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setPreferredSize(new Dimension(10, 40));
        t.setFont(UIManager.getFont("Table.font"));
        t.getTableHeader().setFont(UIManager.getFont("TableHeader.font"));

        Color selBg = UIManager.getColor("Table.selectionBackground");
        if (selBg == null) selBg = new Color(65, 120, 255);
        Color selFg = UIManager.getColor("Table.selectionForeground");
        if (selFg == null) selFg = Color.WHITE;
        t.setSelectionBackground(selBg);
        t.setSelectionForeground(selFg);

        final Color alt = UIManager.getColor("Table.alternateRowColor") != null
                ? UIManager.getColor("Table.alternateRowColor")
                : new Color(0,0,0,8);

        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                                                                     boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground((row % 2 == 0) ? table.getBackground() : alt);
                }
                return c;
            }
        });
    }

    private File lastChooserDir = new File(System.getProperty("user.home"), "Documents");

    private static class CardPanel extends JPanel {
        private final int arc = 16;
        private final Color border = UIManager.getColor("Separator.foreground") != null
                ? UIManager.getColor("Separator.foreground")
                : new Color(0,0,0,30);

        CardPanel(LayoutManager lm) { super(lm); setOpaque(false); setBorder(new EmptyBorder(8,8,8,8)); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground() != null ? getBackground() : UIManager.getColor("Panel.background"));
            g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private void setLoggedInState(boolean loggedIn) {
        if (batchBtn != null) batchBtn.setEnabled(loggedIn);
        if (uploadBtn != null) uploadBtn.setEnabled(loggedIn);
        if (runBtn != null) runBtn.setEnabled(loggedIn);
        if (saveBtn != null) saveBtn.setEnabled(loggedIn);
        if (freeCreditsBtn != null) freeCreditsBtn.setEnabled(loggedIn);
        if (fileTable != null) fileTable.setEnabled(loggedIn);
        if (tInputs != null) tInputs.setEnabled(loggedIn);
        if (tExtracted != null) tExtracted.setEnabled(loggedIn);
        if (tSummaries != null) tSummaries.setEnabled(loggedIn);
        if (extractedArea != null) extractedArea.setEditable(loggedIn);
        if (metadataArea != null) metadataArea.setEditable(loggedIn);
        if (profileButton != null) profileButton.setVisible(loggedIn);
    }

    private void refreshTableStyles() {
        styleTableModern(fileTable);
        styleTableModern(tInputs);
        styleTableModern(tExtracted);
        styleTableModern(tSummaries);

        // Force table repaints
        fileTable.repaint();
        tInputs.repaint();
        tExtracted.repaint();
        tSummaries.repaint();
    }
}