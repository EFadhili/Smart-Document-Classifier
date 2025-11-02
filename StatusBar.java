package org.example.ui;

import javax.swing.*;
import java.awt.*;

public class StatusBar extends JPanel {
    private final JLabel label = new JLabel("Ready");

    public StatusBar() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        add(label, BorderLayout.CENTER);
    }

    public void setMessage(String m) { label.setText(m); }
}
