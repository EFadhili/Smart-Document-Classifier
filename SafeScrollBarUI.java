package org.example.ui.theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * Slim, themed scrollbar UI that guards against NPE during uninstall.
 */
public class SafeScrollBarUI extends BasicScrollBarUI {
    private final Color thumb;
    private final Color track;

    public SafeScrollBarUI(Color thumb, Color track) {
        this.thumb = thumb;
        this.track = track;
    }

    @Override
    protected void configureScrollBarColors() {
        this.thumbColor = thumb;
        this.trackColor = track;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zero();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zero();
    }

    private JButton zero() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        b.setBorder(new EmptyBorder(0,0,0,0));
        b.setOpaque(false);
        b.setFocusable(false);
        return b;
    }

    // Guard against scrollTimer NPE from superclass on some L&F transitions
    @Override
    protected void uninstallListeners() {
        try {
            super.uninstallListeners();
        } catch (NullPointerException ignore) {
            // Some L&Fs never initialize scrollTimer; ignore safe to proceed.
        }
    }
}
