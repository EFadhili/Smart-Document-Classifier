package org.example.ui.widgets;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.imageio.ImageIO;

public class FilePreviewAccessory extends JPanel implements PropertyChangeListener {
    private final JLabel name = new JLabel("No file");
    private final JLabel meta = new JLabel(" ");
    private BufferedImage img;

    public FilePreviewAccessory(JFileChooser chooser) {
        setLayout(new BorderLayout(8,8));
        setPreferredSize(new Dimension(220, 260));
        setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        add(name, BorderLayout.NORTH);
        add(meta, BorderLayout.SOUTH);
        chooser.addPropertyChangeListener(this);
    }

    @Override public void propertyChange(PropertyChangeEvent evt) {
        if (!JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) return;
        File f = (File) evt.getNewValue();
        img = null;
        if (f != null && f.isFile()) {
            name.setText(f.getName());
            meta.setText(humanMeta(f));
            try { img = ImageIO.read(f); } catch (Exception ignored) { img = null; }
        } else {
            name.setText("No file");
            meta.setText(" ");
        }
        repaint();
    }

    private String humanMeta(File f) {
        long bytes = f.length();
        String[] u = {"B","KB","MB","GB","TB"};
        int i = 0; double v = bytes;
        while (v >= 1024 && i < u.length-1) { v/=1024; i++; }
        return String.format("%,.1f %s", v, u[i]);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (img == null) {
            g.setColor(new Color(0,0,0,40));
            g.drawRect(8, 48, getWidth()-16, getHeight()-96);
            g.drawString("No preview", 16, 64);
            return;
        }
        int availW = getWidth() - 16;
        int availH = getHeight() - 96;
        double s = Math.min(availW/(double)img.getWidth(), availH/(double)img.getHeight());
        int w = (int)Math.round(img.getWidth()*s);
        int h = (int)Math.round(img.getHeight()*s);
        int x = (getWidth()-w)/2;
        int y = 56 + (availH - h)/2;
        g.drawImage(img, x, y, w, h, null);
    }
}
