package org.example.ui.theme;

import javax.swing.*;
import java.awt.*;

public final class ComponentStyler {
    private ComponentStyler(){}

    public static void stylePrimary(JButton b) {
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.putClientProperty("JButton.background", UIManager.getColor("Component.focusColor"));
        b.setForeground(Color.white);
        b.setMargin(new Insets(8,16,8,16));
        b.setFocusable(false);
    }

    public static void styleSecondary(JButton b) {
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setMargin(new Insets(8,14,8,14));
        b.setFocusable(false);
    }

    public static void styleQuiet(JButton b) {
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setFocusable(false);
    }

    public static void raise(JComponent c) {
        c.putClientProperty("JComponent.roundRect", true);
        c.putClientProperty("JComponent.outline", "toolbar");
    }

    public static void styleTable(JTable t) {
        t.setRowHeight(32);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().putClientProperty("TableHeader.bottomSeparatorColor",
                UIManager.getColor("Component.borderColor"));
    }

    public static void styleTextArea(JTextArea ta) {
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.putClientProperty("JComponent.roundRect", true);
        ta.setMargin(new Insets(10, 10, 10, 10));
    }
}
