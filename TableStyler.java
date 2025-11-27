package org.example.ui.theme;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public final class TableStyler {
    private TableStyler(){}

    public static void apply(JTable table, AppTheme t) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        // Zebra
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                var c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? t.c.surface : t.c.selectionLight);
                    c.setForeground(t.c.textPrimary);
                }
                setBorder(BorderFactory.createEmptyBorder(0,12,0,12));
                return c;
            }
        });

        var header = table.getTableHeader();
        header.setBackground(t.c.background);
        header.setForeground(t.c.textSecondary);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
    }
}
