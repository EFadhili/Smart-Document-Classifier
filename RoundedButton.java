package org.example.ui.theme;

import javax.swing.*;
import java.awt.*;

public class RoundedButton extends JButton {
    private final int arc;

    public RoundedButton(String text, int arc) {
        super(text);
        this.arc = arc;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setMargin(new Insets(10,16,10,16));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        var bg = getBackground();
        var w = getWidth(); var h = getHeight();
        g2.setColor(bg);
        g2.fillRoundRect(0,0,w,h, arc, arc);

        super.paintComponent(g);
        g2.dispose();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setForeground(getForeground() == null ? Color.WHITE : getForeground());
    }
}
