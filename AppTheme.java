package org.example.ui.theme;

import java.awt.*;

public enum AppTheme {
    DAYLIGHT(new Palette(
            // brand
            new Color(0x2A6EF4), // primary
            new Color(0x12B886), // accent

            // surfaces
            new Color(0xF5F7FB), // background
            Color.WHITE,         // surface
            new Color(0xE6ECF5), // border

            // text
            new Color(0x0F172A), // textPrimary
            new Color(0x475569), // textSecondary
            new Color(0x64748B), // muted

            // states
            new Color(0x22C55E), // success
            new Color(0xF59E0B), // warning
            new Color(0xEF4444), // danger

            // selection / focus
            new Color(0x2A6EF4),           // selectionBg
            new Color(0xE8F0FF),           // selectionLight
            new Color(0x99C0FF)            // focusRing
    )),

    NIGHTFALL(new Palette(
            // brand
            new Color(0x6AA8FF), // primary
            new Color(0x4ADE80), // accent

            // surfaces
            new Color(0x0B1220), // background
            new Color(0x121A2A), // surface
            new Color(0x22314D), // border

            // text
            new Color(0xE5E7EB), // textPrimary
            new Color(0x9CA3AF), // textSecondary
            new Color(0x6B7280), // muted

            // states
            new Color(0x22C55E), // success
            new Color(0xFBBF24), // warning
            new Color(0xF87171), // danger

            // selection / focus
            new Color(0x1F50B5),           // selectionBg
            new Color(0x1B2A44),           // selectionLight
            new Color(0x6AA8FF)            // focusRing
    )),

    OCEAN(new Palette(
            // brand - ocean blue theme
            new Color(0x0066CC), // primary - deep blue
            new Color(0x00B3B3), // accent - teal

            // surfaces - ocean-inspired gradients
            new Color(0xF0F8FF), // background - alice blue
            new Color(0xE6F3FF), // surface - light ocean blue
            new Color(0xB3D9FF), // border - soft blue

            // text - deep ocean colors
            new Color(0x003366), // textPrimary - dark navy
            new Color(0x336699), // textSecondary - medium blue
            new Color(0x6699CC), // muted - light blue

            // states
            new Color(0x009900), // success - sea green
            new Color(0xFF9900), // warning - coral
            new Color(0xFF3333), // danger - red coral

            // selection / focus
            new Color(0x0066CC),           // selectionBg - primary blue
            new Color(0xE6F2FF),           // selectionLight - very light blue
            new Color(0x66B2FF)            // focusRing - bright blue
    )),

    HIGH_CONTRAST(new Palette(
            // brand - high contrast colors
            new Color(0x0000FF), // primary - pure blue
            new Color(0x00FF00), // accent - pure green

            // surfaces - black and white for maximum contrast
            new Color(0x000000), // background - pure black
            new Color(0x111111), // surface - near black
            new Color(0xFFFFFF), // border - pure white

            // text - high contrast
            new Color(0xFFFFFF), // textPrimary - white on dark
            new Color(0xCCCCCC), // textSecondary - light gray
            new Color(0x999999), // muted - medium gray

            // states - bright colors for visibility
            new Color(0x00FF00), // success - bright green
            new Color(0xFFFF00), // warning - bright yellow
            new Color(0xFF0000), // danger - bright red

            // selection / focus
            new Color(0x0000FF),           // selectionBg - bright blue
            new Color(0x333366),           // selectionLight - dark blue
            new Color(0xFFFFFF)            // focusRing - white
    ));

    public static class Palette {
        public final Color primary, accent;
        public final Color background, surface, border;
        public final Color textPrimary, textSecondary, muted;
        public final Color success, warning, danger;
        public final Color selectionBg, selectionLight, focusRing;

        public Palette(Color primary, Color accent,
                       Color background, Color surface, Color border,
                       Color textPrimary, Color textSecondary, Color muted,
                       Color success, Color warning, Color danger,
                       Color selectionBg, Color selectionLight, Color focusRing) {
            this.primary = primary; this.accent = accent;
            this.background = background; this.surface = surface; this.border = border;
            this.textPrimary = textPrimary; this.textSecondary = textSecondary; this.muted = muted;
            this.success = success; this.warning = warning; this.danger = danger;
            this.selectionBg = selectionBg; this.selectionLight = selectionLight; this.focusRing = focusRing;
        }
    }

    public final Palette c;
    AppTheme(Palette c) { this.c = c; }
}