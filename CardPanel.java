package org.example.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Reusable rounded panel (card) with light drop effect.
 * Put content using card.add(component).
 */
public class CardPanel extends JPanel {
    private final int arc = 14;

    public CardPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // subtle outer shadow
        g2.setColor(new Color(0,0,0,8));
        g2.fillRoundRect(4, 6, w-8, h-8, arc, arc);

        // gradient surface
        GradientPaint grad = new GradientPaint(0, 0, ThemeUtil.SURFACE, 0, h, Color.WHITE);
        g2.setPaint(grad);
        g2.fillRoundRect(0, 0, w-8, h-10, arc, arc);

        // border
        g2.setColor(new Color(210,210,210,180));
        g2.drawRoundRect(0, 0, w-9, h-11, arc, arc);
        g2.dispose();

        super.paintComponent(g);
    }
}
