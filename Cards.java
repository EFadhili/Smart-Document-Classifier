package org.example.ui.theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class Cards {
    private Cards(){}

    public static JPanel card(String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        p.putClientProperty("JComponent.roundRect", true);
        p.setOpaque(true);

        if (title != null && !title.isBlank()) {
            JLabel h = new JLabel(title);
            h.setFont(h.getFont().deriveFont(Font.BOLD, 13f));
            h.setBorder(new EmptyBorder(0, 0, 8, 0));
            p.add(h, BorderLayout.NORTH);
        }
        return p;
    }

    /** Gradient header band */
    public static JComponent header(String text, Icon leftIcon, JComponent rightContent) {
        JPanel band = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color a = getBackground().brighter();
                Color b = getBackground().darker();
                g2.setPaint(new GradientPaint(0, 0, a, 0, getHeight(), b));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        band.setBorder(new EmptyBorder(8, 12, 8, 12));
        band.putClientProperty("JComponent.roundRect", true);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        if (leftIcon != null) left.add(new JLabel(leftIcon));
        JLabel title = new JLabel(text);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        left.add(title);

        band.add(left, BorderLayout.WEST);
        if (rightContent != null) {
            rightContent.setOpaque(false);
            band.add(rightContent, BorderLayout.EAST);
        }
        return band;
    }
}
