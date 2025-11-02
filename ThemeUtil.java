package org.example.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Small theme helper:
 * - installs Nimbus L&F if available
 * - tweaks a few UI defaults (font sizes, button shapes)
 * - exposes a simple color palette
 */
public final class ThemeUtil {
    // palette - tweak these to taste
    public static final Color PRIMARY = new Color(0x2D6CDF);      // blue
    public static final Color ACCENT  = new Color(0xFFB547);      // warm orange
    public static final Color SURFACE = new Color(0xF6F8FA);      // light card
    public static final Color MUTED   = new Color(0x6B7280);      // gray text
    public static final Color SUCCESS = new Color(0x16A34A);

    private ThemeUtil() {}

    public static void install() {
        try {
            // prefer Nimbus (modern-ish), fall back gracefully
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        // global font
        Font base = new Font("Segoe UI", Font.PLAIN, 13);
        UIManager.put("Label.font", base);
        UIManager.put("Button.font", base.deriveFont(Font.BOLD, 13f));
        UIManager.put("TextArea.font", base);
        UIManager.put("TextField.font", base);
        UIManager.put("Table.font", base);
        UIManager.put("Table.rowHeight", 28);

        // Nimbus specific tweaks (if Nimbus is active)
        UIManager.put("nimbusBase", PRIMARY);
        UIManager.put("nimbusBlueGrey", new Color(0xE6EDF6));
        UIManager.put("control", SURFACE);

        // Button defaults
        UIManager.put("Button.contentAreaColor", PRIMARY);
        UIManager.put("Button.focus", PRIMARY.brighter());
        UIManager.put("ToggleButton.background", SURFACE);
    }

    // small helper to create an icon-like colored button
    public static JButton makeAccentButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.white);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
