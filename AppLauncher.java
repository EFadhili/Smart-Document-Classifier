package org.example.ui;

import org.example.auth.AuthManager;
import org.example.auth.TokenManager;
import org.example.core.SummarizationBridgeRest;
import org.example.ui.theme.AppTheme;
import org.example.ui.theme.ThemeUtil;
import org.example.ui.theme.ModernLook;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.net.URL;

public class AppLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeUtil.applyTheme(AppTheme.DAYLIGHT);
            ModernLook.install(ModernLook.Mode.LIGHT);
            ModernLook.setGlobalFont("Inter, Segoe UI, SansSerif", 13);
            FlatLightLaf.setup();
            UIManager.put("ScrollBar.width", 12);

            try {
                // Set application icon
                setApplicationIcon();

                String credentialsPath = "C:\\Users\\EFM\\gcloud_key\\credentials.json";
                List<String> scopes = List.of(
                        "https://www.googleapis.com/auth/userinfo.email",
                        "https://www.googleapis.com/auth/cloud-platform",
                        "https://www.googleapis.com/auth/cloud-vision",
                        "https://www.googleapis.com/auth/generative-language.retriever",
                        "openid",
                        "email",
                        "profile"
                );

                String tokenStoreDir = "tokens";
                AuthManager auth = new AuthManager(credentialsPath, scopes, tokenStoreDir);
                TokenManager tokenManager = new TokenManager();

                tokenManager.setTokenStoreDir(auth.getTokenStoreDirPath().toString());

                UIController controller = new UIController(tokenManager, auth);
                MainWindowUI ui = new MainWindowUI(controller, tokenManager);
                controller.setMainWindow(ui);

                // Set icon for the main window too
                setWindowIcon(ui);

                ui.showUI();

                // OAuth flow
                var credential = auth.authorizeInstalledApp("user");
                tokenManager.setCredential(credential);

                String email = tokenManager.fetchUserEmail();
                tokenManager.setCurrentUserId(
                        (email != null && !email.isBlank()) ? email : "unknown@" + System.currentTimeMillis()
                );

                ui.onSignedIn(email);

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "Startup failed: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
                System.exit(1);
            }
        });
    }

    /**
     * Set application icon for all frames
     */
    private static void setApplicationIcon() {
        try {
            // Load icon from resources
            URL iconUrl = AppLauncher.class.getClassLoader().getResource("Icons/app-icon.png");
            if (iconUrl != null) {
                ImageIcon icon = new ImageIcon(iconUrl);

                // Set for all frames
                // UIManager.put("OptionPane.errorIcon", icon);
                // UIManager.put("OptionPane.informationIcon", icon);
                // UIManager.put("OptionPane.warningIcon", icon);
                // UIManager.put("OptionPane.questionIcon", icon);

                // Set taskbar icon (Java 9+)
                try {
                    if (Taskbar.isTaskbarSupported()) {
                        Taskbar taskbar = Taskbar.getTaskbar();
                        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                            taskbar.setIconImage(icon.getImage());
                        }
                    }
                } catch (Exception e) {
                    // Taskbar features not supported, continue silently
                }
            }
        } catch (Exception e) {
            System.err.println("Could not load application icon: " + e.getMessage());
        }
    }

    /**
     * Set icon for a specific window
     */
    private static void setWindowIcon(JFrame window) {
        try {
            URL iconUrl = AppLauncher.class.getClassLoader().getResource("Icons/app-icon.png");
            if (iconUrl != null) {
                ImageIcon icon = new ImageIcon(iconUrl);
                window.setIconImage(icon.getImage());

                // Set multiple icon sizes for better display
                java.util.List<Image> icons = new java.util.ArrayList<>();
                icons.add(icon.getImage());

                // Try to load different sizes
                String[] sizes = {"16x16", "32x32", "48x48", "128x128"};
                for (String size : sizes) {
                    URL sizeUrl = AppLauncher.class.getClassLoader().getResource("Icons/app-icon-" + size + ".png");
                    if (sizeUrl != null) {
                        icons.add(new ImageIcon(sizeUrl).getImage());
                    }
                }

                window.setIconImages(icons);
            }
        } catch (Exception e) {
            System.err.println("Could not set window icon: " + e.getMessage());
        }
    }
}