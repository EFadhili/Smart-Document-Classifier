package org.example.ui;

import javax.swing.*;

public class AppLauncher {
    public static void main(String[] args) {
        // set nicer L&F
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        ThemeUtil.install();
        SwingUtilities.invokeLater(() -> {
            UIController controller = new UIController();
            MainWindow window = new MainWindow(controller);
            controller.setMainWindow(window);
            window.setVisible(true);
        });
    }
}
