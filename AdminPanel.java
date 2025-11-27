package org.example.ui;

import org.example.storage.CreditService;
import org.example.storage.StorageManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

import static org.example.storage.StorageManager.formatUserFriendlyDateTime;

public class AdminPanel extends JPanel {
    private final UIController controller;

    private JTabbedPane tabbedPane;
    private JTextArea statsArea;
    private JTable usersTable;
    private JTable documentsTable;
    private JTable creditTransactionsTable;
    private JButton refreshButton;
    private JButton addCreditsButton;
    private JButton suspendUserButton;
    private JButton unsuspendUserButton;
    private JButton viewTransactionsButton;

    public AdminPanel(UIController controller) {
        this.controller = controller;
        initializeUI();
        setupLayout();
        setupEvents();
        loadData();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();

        // Statistics tab
        statsArea = new JTextArea(20, 50);
        statsArea.setEditable(false);
        statsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Users tab
        usersTable = new JTable();
        usersTable.setAutoCreateRowSorter(true);
        usersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Documents tab
        documentsTable = new JTable();
        documentsTable.setAutoCreateRowSorter(true);

        // Credit Transactions tab
        creditTransactionsTable = new JTable();
        creditTransactionsTable.setAutoCreateRowSorter(true);

        // Buttons
        refreshButton = new JButton("Refresh Data");
        addCreditsButton = new JButton("Add Credits to User");
        suspendUserButton = new JButton("Suspend User");
        unsuspendUserButton = new JButton("Unsuspend User");
        viewTransactionsButton = new JButton("View User Transactions");
    }

    private void setupLayout() {
        // Statistics tab
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.add(new JScrollPane(statsArea), BorderLayout.CENTER);

        // Users tab with management buttons
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.add(new JScrollPane(usersTable), BorderLayout.CENTER);

        // User management buttons panel
        JPanel userButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        userButtonsPanel.add(addCreditsButton);
        userButtonsPanel.add(suspendUserButton);
        userButtonsPanel.add(unsuspendUserButton);
        userButtonsPanel.add(viewTransactionsButton);

        JPanel usersTopPanel = new JPanel(new BorderLayout());
        usersTopPanel.add(new JLabel("User Management - Select a user and choose an action:"), BorderLayout.NORTH);
        usersTopPanel.add(userButtonsPanel, BorderLayout.CENTER);

        usersPanel.add(usersTopPanel, BorderLayout.NORTH);

        // Documents tab
        JPanel docsPanel = new JPanel(new BorderLayout());
        docsPanel.add(new JScrollPane(documentsTable), BorderLayout.CENTER);

        // Credit Transactions tab
        JPanel transactionsPanel = new JPanel(new BorderLayout());
        transactionsPanel.add(new JScrollPane(creditTransactionsTable), BorderLayout.CENTER);

        // Add tabs
        tabbedPane.addTab("System Statistics", statsPanel);
        tabbedPane.addTab("User Management", usersPanel);
        tabbedPane.addTab("Document Management", docsPanel);
        tabbedPane.addTab("Credit Transactions", transactionsPanel);

        // Main layout
        add(tabbedPane, BorderLayout.CENTER);

        // Refresh button at bottom
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(refreshButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupEvents() {
        refreshButton.addActionListener(e -> loadData());
        addCreditsButton.addActionListener(e -> addCreditsToUser());
        suspendUserButton.addActionListener(e -> suspendUser());
        unsuspendUserButton.addActionListener(e -> unsuspendUser());
        viewTransactionsButton.addActionListener(e -> viewUserTransactions());
    }

    private void loadData() {
        try {
            // Load statistics
            List<String> stats = controller.getSystemStatistics();
            statsArea.setText(String.join("\n", stats));

            // Load users with credit info
            List<CreditService.UserCreditInfo> users = controller.getAllUsersForAdmin();
            updateUsersTable(users);

            // Load documents
            List<Map<String, Object>> documents = controller.getAllDocumentsForAdmin();
            updateDocumentsTable(documents);

            // Load credit transactions for all users
            loadAllCreditTransactions();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error loading admin data: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void updateUsersTable(List<CreditService.UserCreditInfo> users) {
        String[] columns = {"User ID", "Credits Balance", "Status", "Suspension Reason", "Suspended At"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table non-editable
            }
        };

        for (CreditService.UserCreditInfo user : users) {
            String status = user.isSuspended() ? "SUSPENDED" : "ACTIVE";
            String suspensionReason = user.getSuspensionReason() != null ? user.getSuspensionReason() : "N/A";
            String suspendedAt = user.getSuspendedAt() != null ? user.getSuspendedAt() : "N/A";

            Object[] row = {
                    user.getUserId(),
                    user.getCreditsBalance(),
                    status,
                    suspensionReason,
                    suspendedAt
            };
            model.addRow(row);
        }

        usersTable.setModel(model);

        // Auto-adjust column widths
        for (int i = 0; i < usersTable.getColumnModel().getColumnCount(); i++) {
            usersTable.getColumnModel().getColumn(i).setPreferredWidth(150);
        }
    }

    private void updateDocumentsTable(List<Map<String, Object>> documents) {
        String[] columns = {"Document ID", "Filename", "Owner", "Label", "Confidence", "Processed Date"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (Map<String, Object> doc : documents) {
            Object dateObj = doc.get("processed_date");
            String formattedDate = formatUserFriendlyDateTime(dateObj);

            Object[] row = {
                    doc.get("doc_id"),
                    doc.get("filename"),
                    doc.get("owner_id"),
                    doc.get("predicted_label"),
                    doc.get("confidence"),
                    formattedDate
            };
            model.addRow(row);
        }

        documentsTable.setModel(model);
    }
    // Helper method for date formatting
    private String formatUserFriendlyDateTime(Object dateObj) {
        if (dateObj == null) return "N/A";

        if (dateObj instanceof java.sql.Timestamp) {
            // Convert SQL Timestamp to user-friendly format
            java.sql.Timestamp timestamp = (java.sql.Timestamp) dateObj;
            java.time.LocalDateTime localDateTime = timestamp.toLocalDateTime();
            return localDateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"));
        } else if (dateObj instanceof String) {
            // Handle string timestamps (ISO format)
            return StorageManager.formatUserFriendlyDateTime((String) dateObj);
        } else {
            return String.valueOf(dateObj);
        }
    }

    private void loadAllCreditTransactions() {
        try {
            // This would need a method in UIController to get all transactions
            // For now, we'll show transactions for the first user as an example
            List<CreditService.UserCreditInfo> users = controller.getAllUsersForAdmin();
            if (!users.isEmpty()) {
                List<CreditService.CreditTransaction> transactions =
                        controller.getUserTransactions(users.get(0).getUserId(), 50);
                updateTransactionsTable(transactions);
            }
        } catch (Exception ex) {
            // Silently fail for transactions - they're not critical
            System.err.println("Failed to load transactions: " + ex.getMessage());
        }
    }

    private void updateTransactionsTable(List<CreditService.CreditTransaction> transactions) {
        String[] columns = {"User Name","Amount", "Type", "Description", "Date"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (CreditService.CreditTransaction tx : transactions) {
            String formattedDate = formatUserFriendlyDateTime(tx.getCreatedAt());
            String amount = (tx.getAmount() >= 0 ? "+" : "") + tx.getAmount() + " credits";
            Object[] row = {
                    tx.getUserName(),
                    amount,
                    tx.getType(),
                    tx.getDescription(),
                    formattedDate
            };
            model.addRow(row);
        }

        creditTransactionsTable.setModel(model);
    }

    private void addCreditsToUser() {
        int selectedRow = usersTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a user first.",
                    "No User Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = usersTable.convertRowIndexToModel(selectedRow);
        String userId = (String) usersTable.getModel().getValueAt(modelRow, 0);

        // Show input dialog for credit amount
        String amountStr = JOptionPane.showInputDialog(this,
                "Enter number of credits to add to user: " + userId,
                "Add Credits",
                JOptionPane.QUESTION_MESSAGE);

        if (amountStr == null || amountStr.trim().isEmpty()) {
            return; // User cancelled
        }

        try {
            int amount = Integer.parseInt(amountStr.trim());
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a positive number of credits.",
                        "Invalid Amount",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String reason = JOptionPane.showInputDialog(this,
                    "Enter reason for adding credits:",
                    "Credit Reason",
                    JOptionPane.QUESTION_MESSAGE);

            if (reason == null) {
                return; // User cancelled
            }

            if (reason.trim().isEmpty()) {
                reason = "Admin manual credit addition";
            }

            // Call controller to add credits
            boolean success = controller.adminAddCredits(userId, amount, reason);
            if (success) {
                JOptionPane.showMessageDialog(this,
                        "Successfully added " + amount + " credits to user: " + userId,
                        "Credits Added",
                        JOptionPane.INFORMATION_MESSAGE);
                loadData(); // Refresh the data
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to add credits to user: " + userId,
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a valid number for credits.",
                    "Invalid Number",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error adding credits: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void suspendUser() {
        int selectedRow = usersTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a user first.",
                    "No User Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = usersTable.convertRowIndexToModel(selectedRow);
        String userId = (String) usersTable.getModel().getValueAt(modelRow, 0);
        String status = (String) usersTable.getModel().getValueAt(modelRow, 2);

        if ("SUSPENDED".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    "User " + userId + " is already suspended.",
                    "Already Suspended",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String reason = JOptionPane.showInputDialog(this,
                "Enter reason for suspending user: " + userId,
                "Suspend User",
                JOptionPane.QUESTION_MESSAGE);

        if (reason == null) {
            return; // User cancelled
        }

        if (reason.trim().isEmpty()) {
            reason = "Administrative suspension";
        }

        try {
            boolean success = controller.suspendUser(userId, reason);
            if (success) {
                JOptionPane.showMessageDialog(this,
                        "Successfully suspended user: " + userId,
                        "User Suspended",
                        JOptionPane.INFORMATION_MESSAGE);
                loadData(); // Refresh the data
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to suspend user: " + userId,
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error suspending user: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void unsuspendUser() {
        int selectedRow = usersTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a user first.",
                    "No User Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = usersTable.convertRowIndexToModel(selectedRow);
        String userId = (String) usersTable.getModel().getValueAt(modelRow, 0);
        String status = (String) usersTable.getModel().getValueAt(modelRow, 2);

        if (!"SUSPENDED".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    "User " + userId + " is not suspended.",
                    "Not Suspended",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to unsuspend user: " + userId + "?",
                "Confirm Unsuspend",
                JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            boolean success = controller.unsuspendUser(userId);
            if (success) {
                JOptionPane.showMessageDialog(this,
                        "Successfully unsuspended user: " + userId,
                        "User Unsuspended",
                        JOptionPane.INFORMATION_MESSAGE);
                loadData(); // Refresh the data
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to unsuspend user: " + userId,
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error unsuspending user: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void viewUserTransactions() {
        int selectedRow = usersTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a user first.",
                    "No User Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = usersTable.convertRowIndexToModel(selectedRow);
        String userId = (String) usersTable.getModel().getValueAt(modelRow, 0);

        try {
            List<CreditService.CreditTransaction> transactions =
                    controller.getUserTransactions(userId, 100); // Get last 100 transactions

            if (transactions.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No transactions found for user: " + userId,
                        "No Transactions",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Create a detailed transactions dialog
            JDialog transactionsDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                    "Credit Transactions - " + userId, true);
            transactionsDialog.setLayout(new BorderLayout());
            transactionsDialog.setSize(600, 400);
            transactionsDialog.setLocationRelativeTo(this);

            JTextArea transactionsArea = new JTextArea();
            transactionsArea.setEditable(false);
            transactionsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

            StringBuilder sb = new StringBuilder();
            sb.append("=== CREDIT TRANSACTIONS FOR USER: ").append(userId).append(" ===\n\n");

            int totalAdded = 0;
            int totalUsed = 0;

            for (CreditService.CreditTransaction tx : transactions) {
                String sign = tx.getAmount() >= 0 ? "+" : "";
                sb.append(String.format("%s%d credits", sign, tx.getAmount()))
                        .append(" - ").append(tx.getType())
                        .append("\nDescription: ").append(tx.getDescription())
                        .append("\nDate: ").append(tx.getCreatedAt())
                        .append("\n").append("-".repeat(60)).append("\n");

                if (tx.getAmount() > 0) {
                    totalAdded += tx.getAmount();
                } else {
                    totalUsed += Math.abs(tx.getAmount());
                }
            }

            sb.append("\n=== SUMMARY ===\n");
            sb.append("Total Credits Added: ").append(totalAdded).append("\n");
            sb.append("Total Credits Used: ").append(totalUsed).append("\n");
            sb.append("Net Balance Change: ").append(totalAdded - totalUsed).append("\n");

            transactionsArea.setText(sb.toString());

            JScrollPane scrollPane = new JScrollPane(transactionsArea);
            transactionsDialog.add(scrollPane, BorderLayout.CENTER);

            JButton closeButton = new JButton("Close");
            closeButton.addActionListener(e -> transactionsDialog.dispose());

            JPanel buttonPanel = new JPanel();
            buttonPanel.add(closeButton);
            transactionsDialog.add(buttonPanel, BorderLayout.SOUTH);

            transactionsDialog.setVisible(true);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error loading transactions: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

}