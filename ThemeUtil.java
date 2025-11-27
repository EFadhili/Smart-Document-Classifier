package org.example.ui.theme;

import com.formdev.flatlaf.util.UIScale;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.plaf.FontUIResource;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.util.Map;
import java.util.WeakHashMap;

public final class ThemeUtil {
    private ThemeUtil() {}

    private static AppTheme currentTheme = AppTheme.DAYLIGHT;
    private static final Map<Window, Boolean> registeredWindows = new WeakHashMap<>();

    public static void applyTheme(AppTheme theme) {
        currentTheme = theme;
        var c = theme.c;

        // Clear any existing UI defaults to ensure clean slate
        UIManager.getLookAndFeelDefaults().clear();
        UIManager.getDefaults().clear();

        // Frame / panels
        UIManager.put("Panel.background", c.background);
        UIManager.put("Panel.foreground", c.textPrimary);
        UIManager.put("OptionPane.background", c.background);
        UIManager.put("OptionPane.messageForeground", c.textPrimary);
        UIManager.put("Viewport.background", c.background);

        // Labels
        UIManager.put("Label.foreground", c.textPrimary);
        UIManager.put("Label.background", c.background);

        // Buttons
        UIManager.put("Button.background", c.surface);
        UIManager.put("Button.foreground", c.textPrimary);
        UIManager.put("Button.focus", c.focusRing);
        UIManager.put("Button.select", c.selectionLight);
        UIManager.put("Button.border", BorderFactory.createLineBorder(c.border));

        // Toggle buttons
        UIManager.put("ToggleButton.background", c.surface);
        UIManager.put("ToggleButton.foreground", c.textPrimary);

        // Text areas / fields
        UIManager.put("TextArea.background", c.surface);
        UIManager.put("TextArea.foreground", c.textPrimary);
        UIManager.put("TextArea.caretForeground", c.textPrimary);
        UIManager.put("TextArea.selectionBackground", c.selectionBg);
        UIManager.put("TextArea.selectionForeground", Color.WHITE);
        UIManager.put("TextArea.inactiveForeground", c.textSecondary);

        UIManager.put("TextField.background", c.surface);
        UIManager.put("TextField.foreground", c.textPrimary);
        UIManager.put("TextField.caretForeground", c.textPrimary);
        UIManager.put("TextField.selectionBackground", c.selectionBg);
        UIManager.put("TextField.selectionForeground", Color.WHITE);
        UIManager.put("TextField.inactiveForeground", c.textSecondary);
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c.border),
                new EmptyBorder(6,8,6,8)
        ));

        UIManager.put("FormattedTextField.background", c.surface);
        UIManager.put("FormattedTextField.foreground", c.textPrimary);

        // Password fields
        UIManager.put("PasswordField.background", c.surface);
        UIManager.put("PasswordField.foreground", c.textPrimary);

        // Tables
        UIManager.put("Table.background", c.surface);
        UIManager.put("Table.foreground", c.textPrimary);
        UIManager.put("Table.gridColor", c.border);
        UIManager.put("Table.selectionBackground", c.selectionBg);
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TableHeader.background", c.background);
        UIManager.put("TableHeader.foreground", c.textSecondary);
        UIManager.put("Table.alternateRowColor", new Color(c.background.getRGB() & 0x20FFFFFF, true));

        // Menus
        UIManager.put("Menu.background", c.surface);
        UIManager.put("Menu.foreground", c.textPrimary);
        UIManager.put("MenuItem.background", c.surface);
        UIManager.put("MenuItem.foreground", c.textPrimary);
        UIManager.put("MenuItem.selectionBackground", c.selectionBg);
        UIManager.put("MenuItem.selectionForeground", Color.WHITE);
        UIManager.put("PopupMenu.background", c.surface);
        UIManager.put("PopupMenu.foreground", c.textPrimary);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(c.border));

        // Scroll panes
        UIManager.put("ScrollPane.background", c.background);
        UIManager.put("ScrollPane.foreground", c.textPrimary);
        UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(c.border));

        // Scrollbars
        UIManager.put("ScrollBar.background", c.background);
        UIManager.put("ScrollBar.foreground", c.textSecondary);
        UIManager.put("ScrollBar.thumb", c.border);
        UIManager.put("ScrollBar.thumbDarkShadow", c.border.darker());
        UIManager.put("ScrollBar.thumbHighlight", c.border.brighter());
        UIManager.put("ScrollBar.width", 12);

        // Combo boxes
        UIManager.put("ComboBox.background", c.surface);
        UIManager.put("ComboBox.foreground", c.textPrimary);
        UIManager.put("ComboBox.selectionBackground", c.selectionBg);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);

        // Lists
        UIManager.put("List.background", c.surface);
        UIManager.put("List.foreground", c.textPrimary);
        UIManager.put("List.selectionBackground", c.selectionBg);
        UIManager.put("List.selectionForeground", Color.WHITE);

        // Checkboxes and radio buttons
        UIManager.put("CheckBox.background", c.background);
        UIManager.put("CheckBox.foreground", c.textPrimary);
        UIManager.put("RadioButton.background", c.background);
        UIManager.put("RadioButton.foreground", c.textPrimary);

        // Sliders
        UIManager.put("Slider.background", c.background);
        UIManager.put("Slider.foreground", c.textPrimary);

        // Progress bars
        UIManager.put("ProgressBar.background", c.background);
        UIManager.put("ProgressBar.foreground", c.primary);
        UIManager.put("ProgressBar.selectionBackground", c.textPrimary);
        UIManager.put("ProgressBar.selectionForeground", c.background);

        // Tabs
        UIManager.put("TabbedPane.background", c.background);
        UIManager.put("TabbedPane.foreground", c.textPrimary);
        UIManager.put("TabbedPane.selected", c.primary);
        UIManager.put("TabbedPane.border", BorderFactory.createLineBorder(c.border));

        // Separators
        UIManager.put("Separator.background", c.border);
        UIManager.put("Separator.foreground", c.border);

        // Tooltips
        UIManager.put("ToolTip.background", c.surface);
        UIManager.put("ToolTip.foreground", c.textPrimary);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(c.border));

        // Internal frames
        UIManager.put("InternalFrame.background", c.background);
        UIManager.put("InternalFrame.foreground", c.textPrimary);

        // File chooser
        UIManager.put("FileChooser.background", c.background);
        UIManager.put("FileChooser.foreground", c.textPrimary);

        // Rounded corners and consistent padding
        UIManager.put("Button.arc", 16);
        UIManager.put("Component.arc", 14);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 999);

        // Slight shadows on cards and text areas (optional)
        UIManager.put("Panel.shadow", c.border.brighter());

        // Smooth focus highlight
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.focusColor", c.focusRing);

        // Set the Look and Feel based on theme brightness
        try {
            if (theme == AppTheme.NIGHTFALL || theme == AppTheme.HIGH_CONTRAST) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Force complete UI update across all windows
        updateAllWindows();
    }

    private static void updateAllWindows() {
        // Update all existing windows
        for (Window window : Window.getWindows()) {
            if (window.isDisplayable()) {
                SwingUtilities.updateComponentTreeUI(window);
                window.repaint();
            }
        }

        // Also update the system tray if it exists
        if (SystemTray.isSupported()) {
            // System tray icons might need updating too
        }
    }

    public static void registerWindowForUpdates(Window window) {
        registeredWindows.put(window, true);
    }

    public static void stylePrimaryButton(AbstractButton b, AppTheme t) {
        b.setBackground(t.c.primary);
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createEmptyBorder(10,16,10,16));
        b.setFocusPainted(false);
        b.setOpaque(true);
    }

    public static void styleAccentButton(AbstractButton b, AppTheme t) {
        b.setBackground(t.c.accent);
        b.setForeground(Color.BLACK);
        b.setBorder(BorderFactory.createEmptyBorder(10,16,10,16));
        b.setFocusPainted(false);
        b.setOpaque(true);
    }

    public static void addHoverEffect(AbstractButton b, Color hoverColor, Color normalColor) {
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(hoverColor);
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(normalColor);
            }
        });
    }

    public static void styleCard(JComponent c, AppTheme t) {
        c.setBackground(t.c.surface);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1,1,2,1, t.c.border),
                new EmptyBorder(12,12,12,12)
        ));
        c.setOpaque(true);
        c.setBackground(t.c.surface);

        c.setDropTarget(null);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        c.putClientProperty("JComponent.roundRect", true);
    }

    public static void setGlobalFont(String familyList, int size) {
        // pick first available family
        String family = familyList;
        if (familyList != null && familyList.contains(",")) {
            for (String f : familyList.split(",")) {
                f = f.trim();
                if (isFontAvailable(f)) { family = f; break; }
            }
        }
        Font base = new Font(family == null ? "Dialog" : family, Font.PLAIN, UIScale.scale(size));
        FontUIResource fui = new FontUIResource(base);

        // Replace all Font/FontUIResource defaults with this one
        var defaults = UIManager.getLookAndFeelDefaults();
        for (Object key : defaults.keySet().toArray()) {
            Object val = defaults.get(key);
            if (val instanceof FontUIResource || val instanceof Font) {
                UIManager.put(key, fui);
            }
        }
        // Also set defaultFont key as many LAFs consult this
        UIManager.put("defaultFont", fui);

        // Refresh all windows
        updateAllWindows();
    }

    private static boolean isFontAvailable(String name) {
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (f.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static Object hex(AppTheme theme, String light, String dark) {
        return theme == AppTheme.NIGHTFALL ? Color.decode(dark) : Color.decode(light);
    }

    private static void putUI(Map<String, Object> kv) {
        kv.forEach(UIManager::put);
    }

    public static void installSlimScrollbars(JScrollPane sp, Object ignored) {
        JScrollBar v = sp.getVerticalScrollBar();
        if (v != null) v.setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
        JScrollBar h = sp.getHorizontalScrollBar();
        if (h != null) h.setPreferredSize(new Dimension(Integer.MAX_VALUE, 12));

        // Apply current theme colors to scrollbars
        if (v != null) {
            v.setBackground(currentTheme.c.background);
            v.setForeground(currentTheme.c.border);
        }
        if (h != null) {
            h.setBackground(currentTheme.c.background);
            h.setForeground(currentTheme.c.border);
        }
    }

    public static AppTheme getCurrentTheme() {
        return currentTheme;
    }

    public static void refreshComponent(JComponent component) {
        if (component != null) {
            SwingUtilities.updateComponentTreeUI(component);
            component.repaint();
        }
    }
}