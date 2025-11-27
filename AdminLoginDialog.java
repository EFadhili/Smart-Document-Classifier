package org.example.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class AdminLoginDialog extends JDialog {
    private final UIController controller;
    private boolean authenticated = false;

    private JTextField emailField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton cancelButton;
    private JCheckBox showPasswordCheckbox;
    private JLabel statusLabel;

    public AdminLoginDialog(Frame parent, UIController controller) {
        super(parent, "Admin Login", true);
        this.controller = controller;
        initializeUI();
        setupLayout();
        setupEvents();
        applyModernStyling();
    }

    private void initializeUI() {
        setSize(450, 300);
        setLocationRelativeTo(getParent());
        setResizable(false);

        emailField = new JTextField(25);
        passwordField = new JPasswordField(25);
        loginButton = new JButton("Login");
        cancelButton = new JButton("Cancel");
        showPasswordCheckbox = new JCheckBox("Show password");
        statusLabel = new JLabel("Enter admin credentials to access system controls");
        statusLabel.setForeground(new Color(100, 100, 100));

        // Set icons
        loginButton.setIcon(new FlatSVGIcon("Icons/login.svg", 16, 16));
        cancelButton.setIcon(new FlatSVGIcon("Icons/cancel.svg", 16, 16));
    }

    private void applyModernStyling() {
        // Modern text field styling
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "elton.mumalasi@strathmore.edu");
        emailField.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc: 10;" +
                "focusWidth: 1;" +
                "padding: 5,5,5,5");

        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter password");
        passwordField.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc: 10;" +
                "focusWidth: 1;" +
                "padding: 5,5,5,5");

        // Button styling
        loginButton.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc: 15;" +
                "background: #2e7d32;" +
                "foreground: white;" +
                "focusWidth: 1;" +
                "borderWidth: 0");

        cancelButton.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc: 15;" +
                "background: #757575;" +
                "foreground: white;" +
                "focusWidth: 1;" +
                "borderWidth: 0");

        // Checkbox styling
        showPasswordCheckbox.putClientProperty(FlatClientProperties.STYLE, "" +
                "focusWidth: 1");

        // Status label styling
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
    }

    private void setupLayout() {
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        mainPanel.setBackground(UIManager.getColor("Panel.background"));

        // Header with icon
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Form panel
        JPanel formPanel = createFormPanel();
        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
        headerPanel.setOpaque(false);

        // Icon and title
        JLabel titleLabel = new JLabel("Administrator Access");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titlePanel.setOpaque(false);
        titlePanel.add(titleLabel);

        headerPanel.add(titlePanel, BorderLayout.NORTH);
        headerPanel.add(statusLabel, BorderLayout.CENTER);

        return headerPanel;
    }

    private JPanel createFormPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Credentials"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Email row
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        formPanel.add(new JLabel("Email:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        formPanel.add(emailField, gbc);

        // Password row
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        formPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        formPanel.add(passwordField, gbc);

        // Show password checkbox
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.insets = new Insets(2, 8, 8, 8);
        formPanel.add(showPasswordCheckbox, gbc);

        return formPanel;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // Add hint about default credentials
        JLabel hintLabel = new JLabel("Default: elton.mumalasi@strathmore.edu / admin123");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 11f));
        hintLabel.setForeground(new Color(150, 150, 150));

        buttonPanel.add(hintLabel);
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(cancelButton);
        buttonPanel.add(loginButton);

        return buttonPanel;
    }

    private void setupEvents() {
        loginButton.addActionListener(e -> attemptLogin());
        cancelButton.addActionListener(e -> dispose());

        // Enter key to login
        passwordField.addActionListener(e -> attemptLogin());

        // Show password toggle
        showPasswordCheckbox.addActionListener(e -> {
            if (showPasswordCheckbox.isSelected()) {
                passwordField.setEchoChar((char) 0); // Show password
            } else {
                passwordField.setEchoChar('•'); // Hide password
            }
        });

        // Keyboard shortcuts
        setupKeyboardShortcuts();

        // Focus traversal
        setupFocusTraversal();
    }

    private void setupKeyboardShortcuts() {
        // ESC to cancel
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Ctrl+Enter to login
        getRootPane().registerKeyboardAction(
                e -> attemptLogin(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void setupFocusTraversal() {
        // Set focus traversal order
        java.util.List<Component> focusOrder = java.util.Arrays.asList(
                emailField, passwordField, showPasswordCheckbox, loginButton, cancelButton
        );

        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override
            public Component getComponentAfter(Container focusCycleRoot, Component aComponent) {
                int index = focusOrder.indexOf(aComponent);
                return focusOrder.get((index + 1) % focusOrder.size());
            }

            @Override
            public Component getComponentBefore(Container focusCycleRoot, Component aComponent) {
                int index = focusOrder.indexOf(aComponent);
                return focusOrder.get((index - 1 + focusOrder.size()) % focusOrder.size());
            }

            @Override
            public Component getFirstComponent(Container focusCycleRoot) {
                return focusOrder.get(0);
            }

            @Override
            public Component getLastComponent(Container focusCycleRoot) {
                return focusOrder.get(focusOrder.size() - 1);
            }

            @Override
            public Component getDefaultComponent(Container focusCycleRoot) {
                return getFirstComponent(focusCycleRoot);
            }
        });

        setFocusTraversalPolicyProvider(true);
    }

    private void attemptLogin() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter both email and password");
            return;
        }

        // Show loading state
        setLoginInProgress(true);

        // Simulate network delay for better UX (remove in production)
        Timer delayTimer = new Timer(500, e -> {
            try {
                boolean success = controller.validateAdminLogin(email, password);

                if (success) {
                    authenticated = true;
                    showSuccess("Admin login successful! Redirecting to admin panel...");

                    // Close after success message
                    Timer closeTimer = new Timer(1000, ev -> dispose());
                    closeTimer.setRepeats(false);
                    closeTimer.start();
                } else {
                    showError("Invalid admin credentials. Please check your email and password.");
                    setLoginInProgress(false);
                }
            } catch (Exception ex) {
                showError("Login error: " + ex.getMessage());
                setLoginInProgress(false);
            }
        });
        delayTimer.setRepeats(false);
        delayTimer.start();
    }

    private void setLoginInProgress(boolean inProgress) {
        loginButton.setEnabled(!inProgress);
        cancelButton.setEnabled(!inProgress);
        emailField.setEnabled(!inProgress);
        passwordField.setEnabled(!inProgress);
        showPasswordCheckbox.setEnabled(!inProgress);

        if (inProgress) {
            loginButton.setText("Logging in...");
            loginButton.setIcon(new FlatSVGIcon("Icons/loading.svg", 16, 16));
            statusLabel.setText("Verifying credentials...");
            statusLabel.setForeground(new Color(0, 100, 200));
        } else {
            loginButton.setText("Login");
            loginButton.setIcon(new FlatSVGIcon("Icons/login.svg", 16, 16));
            statusLabel.setText("Enter admin credentials to access system controls");
            statusLabel.setForeground(new Color(100, 100, 100));
        }
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setForeground(new Color(200, 0, 0));
        passwordField.setText("");
        passwordField.requestFocus();

        // Shake animation for error
        new Timer(50, null) {
            private int count = 0;
            private Point originalLocation = getLocation();

            {
                addActionListener(e -> {
                    if (count < 6) {
                        int offset = (count % 2 == 0) ? 5 : -5;
                        setLocation(originalLocation.x + offset, originalLocation.y);
                        count++;
                    } else {
                        setLocation(originalLocation);
                        ((Timer)e.getSource()).stop();
                    }
                });
            }
        }.start();
    }

    private void showSuccess(String message) {
        statusLabel.setText(message);
        statusLabel.setForeground(new Color(0, 150, 0));
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            // Pre-fill with default credentials for demo convenience
            emailField.setText("admin@legalai.com");
            passwordField.setText("");
            emailField.requestFocus();
        }
        super.setVisible(visible);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}