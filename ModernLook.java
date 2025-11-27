package org.example.ui.theme;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;

public final class ModernLook {
    private ModernLook() {}

    public enum Mode { LIGHT, DARK }

    public static void install(Mode mode) {
        // Smooth animated theme switch
        FlatAnimatedLafChange.showSnapshot();

        // Base theme
        FlatLaf.setup(mode == Mode.DARK ? new FlatDarculaLaf() : new FlatLightLaf());

        // Global tweaks
        UIManager.put( "Component.arc", 14 );         // rounded corners
        UIManager.put( "ProgressBar.arc", 10 );
        UIManager.put( "Button.arc", 18 );
        UIManager.put( "TextComponent.arc", 12 );
        UIManager.put( "ScrollBar.thumbArc", 999 );

        // Density
        UIManager.put( "Component.arrowType", "chevron");  // nicer arrows
        UIManager.put( "ScrollBar.showButtons", false );   // slim scrollbars

        // Accent color (pick one palette)
        UIManager.put( "Component.focusWidth", 1 );
        UIManager.put( "Button.focusedBackground", null );
        UIManager.put( "Component.focusColor", new Color(0x4C8BF5) );      // blue
        UIManager.put( "Component.focusedBorderColor", new Color(0x4C8BF5) );

        // Table styling
        UIManager.put( "Table.showHorizontalLines", Boolean.FALSE );
        UIManager.put( "Table.showVerticalLines", Boolean.FALSE );
        UIManager.put( "Table.selectionBackground", new Color(0xE6F0FF) );
        UIManager.put( "Table.selectionForeground", new Color(0x0B132B) );
        UIManager.put( "Table.rowHeight", 30 );

        // Header
        UIManager.put( "TitlePane.unifiedBackground", true );

        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    /** Simple helper to set 13pt system-like font across the app. */
    public static void setGlobalFont(String family, int size) {
        Font f = new Font(family, Font.PLAIN, size);
        UIManager.getDefaults().forEach((k, v) -> {
            if (v instanceof Font) UIManager.put(k, f);
        });
    }

    /** Load SVG icons consistently (put your .svg under /resources/icons). */
    public static Icon icon(String path, int size) {
        FlatSVGIcon ico = new FlatSVGIcon("icons/upload.svg").derive(18, 18);
        return ico;
    }
}
