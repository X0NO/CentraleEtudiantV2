package com.centrale;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;

/**
 * Apparence commune à toutes les apps de Centrale Étudiant (Note, Calendar, Devoirs, Mails, Main).
 *
 * - Deux modes : sombre et clair. Le choix est mémorisé et partagé par toutes les apps.
 * - Les couleurs ci-dessous changent toutes seules quand on bascule de mode :
 *   un composant coloré avec Theme.SURFACE (par exemple) n'a pas besoin d'être recréé.
 * - L'apparence des composants Swing (boutons, champs, menus, barres de défilement...) vient de FlatLaf.
 */
public final class Theme {

    private Theme() {}

    public enum Mode { SOMBRE, CLAIR }

    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    private static volatile Mode mode = lireMode();
    private static boolean installe = false;
    private static final List<Runnable> ecouteurs = new CopyOnWriteArrayList<>();

    static {
        FlatInterFont.install(); // police Inter (fournie avec l'appli : même rendu sur tous les ordinateurs)
    }

    // =====================================================================
    // COULEURS (valeur en mode sombre, valeur en mode clair)
    // =====================================================================

    /** Couleur qui suit le mode : sa valeur est lue au moment où elle est dessinée. */
    static final class Couleur extends Color {
        private final int sombre;
        private final int clair;

        Couleur(int sombre, int clair) {
            super(sombre);
            this.sombre = 0xFF000000 | sombre;
            this.clair = 0xFF000000 | clair;
        }

        @Override
        public int getRGB() {
            return (mode == Mode.SOMBRE) ? sombre : clair;
        }
    }

    static final Color BACKGROUND      = new Couleur(0x161622, 0xF2F3F7);
    static final Color SURFACE         = new Couleur(0x1F1F2E, 0xFFFFFF);
    static final Color SURFACE_ALT     = new Couleur(0x262638, 0xF6F7FA);
    static final Color SURFACE_HOVER   = new Couleur(0x2F2F46, 0xECEEF4);
    static final Color BORDER          = new Couleur(0x34344A, 0xE0E2EA);
    static final Color GRILLE_LEGERE   = new Couleur(0x2A2A3D, 0xEFF0F4);
    static final Color TEXT_PRIMARY    = new Couleur(0xE6E6F0, 0x1E2130);
    static final Color TEXT_DISCRET    = new Couleur(0x8C8CA8, 0x6A6E84);
    static final Color ACCENT          = new Couleur(0x89B4FA, 0x3B6FE0);
    static final Color ACCENT_HOVER    = new Couleur(0xA6C6FB, 0x2E5CC7);
    static final Color TEXT_ON_ACCENT  = new Couleur(0x11111B, 0xFFFFFF);
    static final Color FOND_AUJOURDHUI = new Couleur(0x222944, 0xEAF0FD);
    static final Color SUCCES          = new Couleur(0xA6E3A1, 0x2F9E5A);
    static final Color ATTENTION       = new Couleur(0xF9E2AF, 0xC27C0E);
    static final Color RETARD          = new Couleur(0xF38BA8, 0xD4405E);
    static final Color ERREUR          = RETARD;

    /** Couleur d'une matière (teinte 0..1) : pastel en sombre, plus soutenue en clair pour rester lisible. */
    static Color teinte(float h) {
        return new Couleur(Color.HSBtoRGB(h, 0.42f, 0.96f) & 0xFFFFFF, Color.HSBtoRGB(h, 0.62f, 0.74f) & 0xFFFFFF);
    }

    static String hex(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

    // =====================================================================
    // POLICES
    // =====================================================================
    static final String POLICE = FlatInterFont.FAMILY;

    static final Font FONT_TITLE   = new Font(POLICE, Font.BOLD, 22);
    static final Font FONT_SECTION = new Font(POLICE, Font.BOLD, 15);
    static final Font FONT_LABEL   = new Font(POLICE, Font.PLAIN, 14);
    static final Font FONT_BUTTON  = new Font(POLICE, Font.BOLD, 13);
    static final Font FONT_TABLE   = new Font(POLICE, Font.PLAIN, 13);
    static final Font FONT_HEADER  = new Font(POLICE, Font.BOLD, 13);
    static final Font FONT_PETIT   = new Font(POLICE, Font.PLAIN, 12);
    static final Font FONT_SURTITRE = new Font(POLICE, Font.BOLD, 11);

    // =====================================================================
    // MODE SOMBRE / CLAIR
    // =====================================================================
    static Mode mode() {
        return mode;
    }

    static boolean estSombre() {
        return mode == Mode.SOMBRE;
    }

    private static Mode lireMode() {
        try {
            return Mode.valueOf(PREFS.get("mode", Mode.SOMBRE.name()));
        } catch (Exception e) {
            return Mode.SOMBRE;
        }
    }

    /** À appeler au tout début de chaque main(), avant de créer une fenêtre. Sans effet la 2e fois. */
    static synchronized void installer() {
        if (installe) return;
        installe = true;

        // barre de titre des fenêtres aux couleurs du thème (au lieu de celle du système)
        JFrame.setDefaultLookAndFeelDecorated(true);
        appliquerLookAndFeel();

        // icône de l'appli sur toutes les fenêtres (barre des tâches, Alt+Tab)
        List<Image> icones = new ArrayList<>();
        for (String nom : new String[]{"/icone-64.png", "/icone-256.png"}) {
            URL url = Theme.class.getResource(nom);
            if (url != null) icones.add(new ImageIcon(url).getImage());
        }
        if (!icones.isEmpty()) {
            Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
                if (e.getID() == WindowEvent.WINDOW_OPENED && e.getSource() instanceof Window w) w.setIconImages(icones);
            }, AWTEvent.WINDOW_EVENT_MASK);
        }
    }

    /** Passe du mode sombre au mode clair (et inversement) dans toutes les fenêtres ouvertes. */
    static void basculer() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(Theme::basculer);
            return;
        }
        mode = estSombre() ? Mode.CLAIR : Mode.SOMBRE;
        try {
            PREFS.put("mode", mode.name());
        } catch (Exception ignored) {
            // préférence non sauvegardée : le mode sera celui par défaut au prochain lancement
        }
        appliquerLookAndFeel();
        FlatLaf.updateUI();
        for (Runnable r : ecouteurs) r.run();
        for (Window w : Window.getWindows()) w.repaint();
    }

    /** Action à lancer après chaque changement de mode (ex : réafficher un texte HTML coloré). */
    static void auChangement(Runnable r) {
        ecouteurs.add(r);
    }

    private static void appliquerLookAndFeel() {
        FlatLaf.setPreferredFontFamily(POLICE);
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", hex(ACCENT),
                "@background", hex(BACKGROUND),
                "@foreground", hex(TEXT_PRIMARY)));
        if (estSombre()) FlatDarkLaf.setup();
        else FlatLightLaf.setup();

        UIManager.put("defaultFont", new Font(POLICE, Font.PLAIN, 13));
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("ScrollPane.arc", 12);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.track", SURFACE);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("TitlePane.background", BACKGROUND);
        UIManager.put("MenuBar.background", BACKGROUND);
        UIManager.put("PopupMenu.borderCornerRadius", 10);
        UIManager.put("MenuItem.selectionArc", 8);
        UIManager.put("MenuItem.selectionInsets", new Insets(0, 4, 0, 4));
        UIManager.put("Table.alternateRowColor", SURFACE_ALT);
        UIManager.put("Table.cellMargins", new Insets(0, 12, 0, 12));
        UIManager.put("Table.selectionBackground", ACCENT);
        UIManager.put("Table.selectionForeground", TEXT_ON_ACCENT);
        UIManager.put("Table.selectionInactiveBackground", ACCENT);
        UIManager.put("Table.selectionInactiveForeground", TEXT_ON_ACCENT);
        UIManager.put("SplitPaneDivider.gripColor", BORDER);
        UIManager.put("ToolTip.background", SURFACE_ALT);
        UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
    }

    // =====================================================================
    // COMPOSANTS
    // =====================================================================

    /** Bouton principal : fond d'accent, coins arrondis. */
    static JButton bouton(String texte) {
        return bouton(texte, ACCENT, ACCENT_HOVER, TEXT_ON_ACCENT);
    }

    /** Bouton secondaire (Annuler, navigation...) : discret, même forme. */
    static JButton boutonSecondaire(String texte) {
        return bouton(texte, SURFACE_ALT, SURFACE_HOVER, TEXT_PRIMARY);
    }

    private static JButton bouton(String texte, Color fond, Color survol, Color encre) {
        JButton b = new JButton(texte);
        b.setFont(FONT_BUTTON);
        b.setBackground(fond);
        b.setForeground(encre);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 12; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 8,18,8,18");
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(survol); }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(fond); }
        });
        return b;
    }

    /** Bouton lune / soleil qui bascule entre mode sombre et mode clair. */
    static JButton boutonMode() {
        JButton b = boutonSecondaire("");
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 999; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 7,14,7,14");
        b.setIcon(new IconeMode());
        b.setIconTextGap(8);
        Runnable maj = () -> {
            b.setText(estSombre() ? "Mode clair" : "Mode sombre");
            b.setToolTipText("Passer en " + (estSombre() ? "mode clair" : "mode sombre") + " (toutes les fenêtres)");
        };
        maj.run();
        auChangement(maj);
        b.addActionListener(e -> basculer());
        return b;
    }

    /** Soleil en mode sombre (on propose le clair), lune en mode clair. */
    static final class IconeMode implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);
            g.setColor(c.getForeground());
            if (estSombre()) {
                g.fill(new Ellipse2D.Double(4.5, 4.5, 7, 7));
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    g.draw(new Line2D.Double(8 + Math.cos(a) * 5.6, 8 + Math.sin(a) * 5.6,
                                             8 + Math.cos(a) * 7.4, 8 + Math.sin(a) * 7.4));
                }
            } else {
                Area lune = new Area(new Ellipse2D.Double(1.5, 1.5, 13, 13));
                lune.subtract(new Area(new Ellipse2D.Double(5.5, -1.5, 12, 12)));
                g.fill(lune);
            }
            g.dispose();
        }
    }

    /**
     * En-tête commun des apps : petit surtitre "CENTRALE ÉTUDIANT", titre de la page,
     * infos facultatives en dessous, et le bouton de mode à droite.
     */
    static JPanel entete(String page, JComponent infos) {
        JLabel surtitre = new JLabel("CENTRALE ÉTUDIANT");
        surtitre.setFont(FONT_SURTITRE);
        surtitre.setForeground(ACCENT);

        JLabel titre = new JLabel(page);
        titre.setFont(FONT_TITLE);
        titre.setForeground(TEXT_PRIMARY);

        JPanel gauche = new JPanel(new GridLayout(infos == null ? 2 : 3, 1, 0, 2));
        gauche.setOpaque(false);
        gauche.add(surtitre);
        gauche.add(titre);
        if (infos != null) gauche.add(infos);

        JPanel droite = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        droite.setOpaque(false);
        droite.add(boutonMode());

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BACKGROUND);
        p.setBorder(new EmptyBorder(16, 24, 14, 24));
        p.add(gauche, BorderLayout.CENTER);
        p.add(droite, BorderLayout.EAST);
        return p;
    }

    /** Petite ligne d'information grise (sous le titre d'une page). */
    static JLabel info(String texte) {
        JLabel l = new JLabel(texte);
        l.setFont(FONT_TABLE);
        l.setForeground(TEXT_DISCRET);
        return l;
    }

    static void styleLabel(JLabel label) {
        label.setFont(FONT_LABEL);
        label.setForeground(TEXT_PRIMARY);
    }

    /** Champ de saisie : bordure arrondie FlatLaf, couleurs du thème. */
    static void styleChamp(JTextField field) {
        field.setFont(FONT_LABEL);
        field.setBackground(SURFACE_ALT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.putClientProperty(FlatClientProperties.STYLE, "margin: 5,8,5,8");
    }

    /** Tableau épuré : lignes alternées, pas de traits verticaux, en-tête discret. */
    static void styleTable(JTable table) {
        table.setFont(FONT_TABLE);
        table.setForeground(TEXT_PRIMARY);
        table.setBackground(SURFACE);
        table.setGridColor(BORDER);
        table.setRowHeight(34);
        table.setSelectionBackground(ACCENT);
        table.setSelectionForeground(TEXT_ON_ACCENT);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_HEADER);
        header.setBackground(SURFACE);
        header.setForeground(TEXT_DISCRET);
        header.putClientProperty(FlatClientProperties.STYLE, "height: 34");
    }

    /**
     * Met un contenu (tableau, zone de texte, dessin...) dans un cadre arrondi.
     * Les scrolls et leurs contenus deviennent transparents pour que les coins arrondis restent propres.
     */
    static PanneauArrondi encadrer(JComponent contenu) {
        PanneauArrondi cadre = new PanneauArrondi(new BorderLayout(), 16);
        cadre.setBackground(SURFACE);
        if (contenu instanceof JScrollPane scroll) {
            scroll.setBorder(new EmptyBorder(1, 1, 1, 1));
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            if (scroll.getViewport().getView() instanceof JComponent vue) vue.setOpaque(false);
            if (scroll.getColumnHeader() != null) scroll.getColumnHeader().setOpaque(false);
            if (scroll.getViewport().getView() instanceof JTable table) table.getTableHeader().setOpaque(false);
        } else {
            contenu.setOpaque(false);
        }
        cadre.add(contenu, BorderLayout.CENTER);
        return cadre;
    }

    /**
     * Panneau à coins arrondis, avec bordure fine et bande de couleur à gauche (facultative).
     * Les composants qu'il contient sont coupés aux coins arrondis.
     */
    static class PanneauArrondi extends JPanel {
        private final int arc;
        private Color bande;
        private int largeurBande = 5;
        private boolean bordure = true;

        PanneauArrondi(LayoutManager layout, int arc) {
            super(layout);
            this.arc = arc;
            setOpaque(false);
            setBackground(SURFACE);
        }

        void setBande(Color couleur, int largeur) {
            this.bande = couleur;
            this.largeurBande = largeur;
            repaint();
        }

        void setBordure(boolean bordure) {
            this.bordure = bordure;
            repaint();
        }

        private Shape forme() {
            return new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape f = forme();
            g.setColor(getBackground());
            g.fill(f);
            if (bande != null) {
                g.clip(f);
                g.setColor(bande);
                g.fillRect(0, 0, largeurBande, getHeight());
            }
            g.dispose();
        }

        @Override
        protected void paintChildren(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.clip(forme());
            super.paintChildren(g);
            g.dispose();
        }

        @Override
        protected void paintBorder(Graphics g0) {
            super.paintBorder(g0);
            if (!bordure) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BORDER);
            g.draw(forme());
            g.dispose();
        }
    }
}
