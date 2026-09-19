package com.centrale;

import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

public class Note {

    // =====================================================================
    // THEME : toutes les couleurs / polices de l'appli sont définies ICI.
    // Pour changer le look de tout le logiciel, il suffit de modifier ces
    // constantes — aucun autre endroit du code n'a de couleur "en dur".
    // =====================================================================
    static final class Theme {
        static final Color BACKGROUND      = new Color(0x1E1E2E); // fond général (gris-bleu foncé)
        static final Color SURFACE         = new Color(0x2A2A3C); // fond des panels / tableau
        static final Color SURFACE_ALT     = new Color(0x252536); // lignes alternées du tableau
        static final Color ACCENT          = new Color(0x89B4FA); // couleur d'accent (boutons, sélection)
        static final Color ACCENT_HOVER    = new Color(0x74A0F0); // accent au survol
        static final Color TEXT_PRIMARY    = new Color(0xE0E0E8); // texte principal (clair)
        static final Color TEXT_ON_ACCENT  = new Color(0x1E1E2E); // texte sur fond accent (foncé)
        static final Color BORDER          = new Color(0x3A3A4E); // bordures / grille du tableau

        static final Font FONT_TITLE  = new Font("Segoe UI", Font.BOLD, 20);
        static final Font FONT_LABEL  = new Font("Segoe UI", Font.PLAIN, 14);
        static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 13);
        static final Font FONT_TABLE  = new Font("Segoe UI", Font.PLAIN, 13);
        static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 13);
    }

    static int[] columnNote = {2};
    static JTable tableNote;
    static JTable tableMoyenne;

    static String[] colonnes = {"Matière", "Note 1"};
    static double[] notes = {};

    static String[][] matière = {
            {"Intro_Syst", ""},
            {"Init_Dev",   ""},
            {"Maths",      ""},
            {"Intro_BD",   ""},
            {"Anglais",    ""},
            {"Commu",      ""},
            {"PPP",        ""},
            {"Dev_Web",    ""}
        };

    static String[] nomsMatieres = new String[matière.length];

    // =====================================================================
    // OUTIL : crée un bouton déjà habillé avec le thème (couleur, police,
    // curseur main, effet de survol). Utilisé partout pour éviter de
    // répéter le style à chaque création de bouton.
    // =====================================================================
    static JButton themedButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.FONT_BUTTON);
        button.setBackground(Theme.ACCENT);
        button.setForeground(Theme.TEXT_ON_ACCENT);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 18, 8, 18)); // padding interne
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Petit effet visuel au survol de la souris
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(Theme.ACCENT_HOVER);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(Theme.ACCENT);
            }
        });
        return button;
    }

    // Filtre de saisie : n'autorise que des chiffres et un seul "/" dans le champ Note
    static class NoteFilter extends DocumentFilter {

        static void ConnectNas() {
            // Connexion au NAS (base de données des notes)
            String url = "jdbc:mariadb://192.168.1.142:3307/CentraleEtudiant";
            String user = "appli_java";
            String password = "MEyu+,AxZlW4[WM";

            System.out.println("Tentative de connexion au NAS...");

            try (Connection conn = DriverManager.getConnection(url, user, password)) {
                if (conn != null && !conn.isClosed()) {
                    System.out.println("Connexion réussie à MariaDB sur le NAS !");
                }
            } catch (SQLException e) {
                System.err.println("Échec de la connexion. Vérifie l'IP, le port, le pare-feu et les identifiants.");
                e.printStackTrace();
            }
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
            String proposedText = currentText.substring(0, offset) + text + currentText.substring(offset + length);

            // N'accepte que : chiffres, et au maximum un seul '/'
            if (proposedText.matches("\\d*(/\\d*)?")) {
                super.replace(fb, offset, length, text, attrs);
            }
            // Sinon, la saisie est simplement ignorée (rien ne s'affiche)
        }
    }

    // Recopie les noms de matières (colonne 0 du tableau `matière`) dans `nomsMatieres`
    static void ListMatiere() {
        for (int i = 0; i < matière.length; i++) {
            nomsMatieres[i] = matière[i][0];
        }
    }

    // =====================================================================
    // Fenêtre "Nouvelle Matière"
    // =====================================================================
    static void AddMatiere() {
        JFrame addMatiereFrame = new JFrame("Nouvelle Matière");
        addMatiereFrame.setSize(600, 300);
        addMatiereFrame.setLocationRelativeTo(null);
        addMatiereFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addMatiereFrame.getContentPane().setBackground(Theme.BACKGROUND);

        JTextField matiereField = new JTextField(20);
        styleTextField(matiereField);

        JLabel labelMatiere = new JLabel("Nom de la matière :");
        styleLabel(labelMatiere);

        // Panel avec GridBagLayout : une grille propre label/champ, ligne par ligne
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(Theme.BACKGROUND);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8); // espacement autour de chaque composant
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(labelMatiere, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; // le champ s'étire proprement dans sa colonne
        centerPanel.add(matiereField, gbc);

        DefaultTableModel model = (DefaultTableModel) tableNote.getModel();

        JButton validButton = themedButton("Valider");
        validButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String saisie = matiereField.getText();

                for (int i = 0; i < model.getRowCount(); i++) {
                    if (model.getValueAt(i, 0).equals(saisie)) {
                        JOptionPane.showMessageDialog(addMatiereFrame,
                            "Cette matière existe déjà.",
                            "Erreur", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                System.out.println(saisie);
                ListMatiere();

                if (!Arrays.asList(nomsMatieres).contains(saisie)) {
                    // saisie n'est pas dans le tableau → on peut l'ajouter
                    model.addRow(new Object[model.getColumnCount()]); // ajoute une ligne vide
                    model.setValueAt(saisie, model.getRowCount() - 1, 0); // met le nom de la matière dans la première colonne
                }
                addMatiereFrame.dispose();
            }
        });

        JButton annulButton = themedButton("Annuler");
        annulButton.addActionListener(e -> addMatiereFrame.dispose());

        JPanel btnValidAnnulPanel = new JPanel();
        btnValidAnnulPanel.setBackground(Theme.BACKGROUND);
        btnValidAnnulPanel.add(validButton);
        btnValidAnnulPanel.add(annulButton);

        // Panel principal de la fenêtre : le champ au centre, les boutons en bas
        JPanel newMatierePanel = new JPanel(new BorderLayout());
        newMatierePanel.setBackground(Theme.BACKGROUND);
        newMatierePanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        newMatierePanel.add(centerPanel, BorderLayout.CENTER);
        newMatierePanel.add(btnValidAnnulPanel, BorderLayout.SOUTH);

        addMatiereFrame.getContentPane().add(newMatierePanel);
        addMatiereFrame.setVisible(true);
    }

    // =====================================================================
    // Fenêtre "Nouvelle Note"
    // =====================================================================
    static void AddNote() {
        JFrame addNoteFrame = new JFrame("Nouvelle Note");
        addNoteFrame.setSize(600, 300);
        addNoteFrame.setLocationRelativeTo(null);
        addNoteFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addNoteFrame.getContentPane().setBackground(Theme.BACKGROUND);

        ListMatiere();

        JComboBox<String> comboMatiere = new JComboBox<>(nomsMatieres);
        comboMatiere.setFont(Theme.FONT_LABEL);

        JTextField noteField = new JTextField(10);
        styleTextField(noteField);

        JLabel labelMatiere = new JLabel("Matière :");
        JLabel labelNote = new JLabel("Note :");
        styleLabel(labelMatiere);
        styleLabel(labelNote);

        ((PlainDocument) noteField.getDocument()).setDocumentFilter(new NoteFilter());

        // Panel avec GridBagLayout : une grille propre label/champ, ligne par ligne
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(Theme.BACKGROUND);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Ligne 0 : label + combo Matière
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(labelMatiere, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        centerPanel.add(comboMatiere, gbc);

        // Ligne 1 : label + champ Note
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        centerPanel.add(labelNote, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        centerPanel.add(noteField, gbc);

        JButton validButton = themedButton("Valider");

        validButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String saisie = noteField.getText();

                if (!saisie.matches("\\d+/\\d+")) {
                    JOptionPane.showMessageDialog(addNoteFrame,
                        "Le format attendu est : note/quotient (ex : 15/20)",
                        "Format invalide", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String[] parts = saisie.split("/");
                int note = Integer.parseInt(parts[0]);
                int quotient = Integer.parseInt(parts[1]);

                if (note > quotient) {
                    JOptionPane.showMessageDialog(addNoteFrame,
                        "La note ne peut pas être supérieure au quotient (ex : 15/20, pas 25/20)",
                        "Note invalide", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String matiereChoisie = (String) comboMatiere.getSelectedItem();
                System.out.println(matiereChoisie + " : " + note + "/" + quotient);

                DefaultTableModel model = (DefaultTableModel) tableNote.getModel();

                // Cherche la ligne correspondant à la matière choisie
                int rowIndex = -1;
                for (int i = 0; i < model.getRowCount(); i++) {
                    if (model.getValueAt(i, 0).equals(matiereChoisie)) {
                        rowIndex = i;
                        break;
                    }
                }

                // Cherche la première colonne "Note X" vide sur cette ligne
                int colIndex = -1;
                for (int col = 1; col < model.getColumnCount(); col++) {
                    if (model.getValueAt(rowIndex, col).equals("")) {
                        colIndex = col;
                        break;
                    }
                }

                String resultat = note + "/" + quotient;

                if (colIndex != -1) {
                    // Une case vide existait : on l'utilise
                    model.setValueAt(resultat, rowIndex, colIndex);
                } else {
                    // Plus de case vide : on ajoute une nouvelle colonne "Note X"
                    // remplie de "" sauf pour notre ligne, où on met directement le résultat
                    String[] nouvelleColonne = new String[model.getRowCount()];
                    for (int i = 0; i < nouvelleColonne.length; i++) {
                        nouvelleColonne[i] = (i == rowIndex) ? resultat : "";
                    }
                    model.addColumn("Note" + columnNote[0]++, nouvelleColonne);
                }

                // ---- Mise à jour du tableau des moyennes (colonnes fixes, jamais de nouvelle colonne) ----
                List<Double> moyennes = calculMoyenneGenerale();

                DefaultTableModel modelMoyenne = (DefaultTableModel) tableMoyenne.getModel();
                modelMoyenne.setRowCount(0); // on reconstruit entièrement le tableau à chaque fois
                for (int i = 0; i < moyennes.size(); i++) {
                    Double m = moyennes.get(i);
                    String affichage = (m == null) ? "" : String.format("%.2f", m);
                    modelMoyenne.addRow(new Object[]{ nomsMatieres[i], affichage });
                }

                addNoteFrame.dispose();
            }
        });

        JButton annulButton = themedButton("Annuler");
        annulButton.addActionListener(e -> addNoteFrame.dispose());

        JPanel btnValidAnnulPanel = new JPanel();
        btnValidAnnulPanel.setBackground(Theme.BACKGROUND);
        btnValidAnnulPanel.add(validButton);
        btnValidAnnulPanel.add(annulButton);

        JPanel newNotePanel = new JPanel(new BorderLayout());
        newNotePanel.setBackground(Theme.BACKGROUND);
        newNotePanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        newNotePanel.add(centerPanel, BorderLayout.CENTER);
        newNotePanel.add(btnValidAnnulPanel, BorderLayout.SOUTH);

        addNoteFrame.getContentPane().add(newNotePanel);
        addNoteFrame.setVisible(true);
    }

    // Calcule la moyenne de chaque matière (colonnes "Note X" de tableNote)
    // et renvoie la liste (une valeur par matière, dans l'ordre des lignes).
    // Une matière sans note reçoit `null` dans la liste.
    static List<Double> calculMoyenneGenerale() {
        DefaultTableModel model = (DefaultTableModel) tableNote.getModel();
        List<Double> moyennesParMatiere = new ArrayList<>();

        // Parcourt chaque matière (chaque ligne du tableau)
        for (int row = 0; row < model.getRowCount(); row++) {
            double sommeNotes = 0;
            double sommeQuotients = 0;

            // Parcourt toutes les colonnes "Note X" de cette matière (à partir de la colonne 1)
            for (int col = 1; col < model.getColumnCount(); col++) {
                Object valeur = model.getValueAt(row, col);
                String texte = (valeur == null) ? "" : valeur.toString();

                if (texte.matches("\\d+/\\d+")) {
                    String[] parts = texte.split("/");
                    sommeNotes += Double.parseDouble(parts[0]);
                    sommeQuotients += Double.parseDouble(parts[1]);
                }
            }

            // Si la matière a au moins une note, on calcule sa moyenne pondérée
            if (sommeQuotients > 0) {
                moyennesParMatiere.add((sommeNotes / sommeQuotients) * 20); // ramenée sur 20
            } else {
                moyennesParMatiere.add(null); // pas encore de note pour cette matière
            }
        }

        // Moyenne générale = moyenne des moyennes de chaque matière (on ignore les matières sans note)
        double moyenneGenerale = moyennesParMatiere.stream()
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        System.out.format("Moyenne générale : %.2f/20%n", moyenneGenerale);
        return moyennesParMatiere;
    }

    // Applique le style "thème" à un JTextField (fond, texte, bordure, curseur)
    static void styleTextField(JTextField field) {
        field.setFont(Theme.FONT_LABEL);
        field.setBackground(Theme.SURFACE);
        field.setForeground(Theme.TEXT_PRIMARY);
        field.setCaretColor(Theme.TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(4, 6, 4, 6)));
    }

    // Applique le style "thème" à un JLabel (police + couleur de texte)
    static void styleLabel(JLabel label) {
        label.setFont(Theme.FONT_LABEL);
        label.setForeground(Theme.TEXT_PRIMARY);
    }

    public static void main(String[] args) {

        // ConnectNas();

        // Applique un thème sombre global à tous les composants Swing
        // créés par la suite (JFrame, JOptionPane, etc.), pour garder
        // une cohérence visuelle même dans les fenêtres secondaires.
        UIManager.put("Panel.background", Theme.BACKGROUND);
        UIManager.put("OptionPane.background", Theme.BACKGROUND);
        UIManager.put("OptionPane.messageForeground", Theme.TEXT_PRIMARY);
        UIManager.put("Button.background", Theme.ACCENT);
        UIManager.put("Button.foreground", Theme.TEXT_ON_ACCENT);

        // Création de la fenêtre principale
        JFrame frame = new JFrame("Centrale Étudiant");
        frame.setSize(1000, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(Theme.BACKGROUND);

        // Titre en haut de la fenêtre
        JLabel titleLabel = new JLabel("Centrale Étudiant", SwingConstants.CENTER);
        titleLabel.setFont(Theme.FONT_TITLE);
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setBorder(new EmptyBorder(16, 0, 16, 0));

        // Modèle du tableau des notes : seule la case (ligne 0, colonne 1) est éditable directement.
        // C'est CE modèle qui gagne des colonnes "Note X" à chaque nouvelle note.
        DefaultTableModel tableUnClickable = new DefaultTableModel(matière, colonnes) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1 && row == 0;
            }
        };

        tableNote = new JTable(tableUnClickable);
        styleTable(tableNote);

        JScrollPane scroll = new JScrollPane(tableNote);
        scroll.setOpaque(true);
        scroll.setBackground(Theme.BACKGROUND);
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setBorder(new EmptyBorder(0, 20, 0, 20));

        // Modèle SÉPARÉ pour le tableau des moyennes : 2 colonnes FIXES ("Matière", "Moyenne").
        // Il n'est jamais touché par model.addColumn(...) — seul tableNote grandit.
        DefaultTableModel modelMoyenne = new DefaultTableModel(new String[]{"Matière", "Moyenne"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // lecture seule : c'est un affichage calculé
            }
        };
        // Pré-remplit une ligne par matière, moyenne vide au départ
        ListMatiere();
        for (String nom : nomsMatieres) {
            modelMoyenne.addRow(new Object[]{ nom, "" });
        }

        tableMoyenne = new JTable(modelMoyenne);
        styleTable(tableMoyenne);

        JScrollPane scrollMoyenne = new JScrollPane(tableMoyenne);
        scrollMoyenne.setOpaque(true);
        scrollMoyenne.setBackground(Theme.BACKGROUND);
        scrollMoyenne.getViewport().setBackground(Theme.SURFACE);
        scrollMoyenne.setBorder(new EmptyBorder(0, 20, 0, 20));

        //Table pour les 2 tableaux 
        JPanel tablesPanel = new JPanel(new GridLayout(2, 1, 0, 12)); // 2 lignes, 1 colonne, espacement vertical de 12px
        tablesPanel.setBackground(Theme.BACKGROUND);
        tablesPanel.add(scroll);          // tableau des notes en haut
        tablesPanel.add(scrollMoyenne);   // tableau des moyennes en dessous

        // Boutons d'action, stylés via themedButton()
        JButton btnNote = themedButton("Ajouter Une Note");
        btnNote.addActionListener(e -> AddNote());

        JButton btnMatiere = themedButton("Ajouter Une Matière");
        btnMatiere.addActionListener(e -> AddMatiere());

        JPanel btnSurround = new JPanel();
        btnSurround.setBackground(Theme.BACKGROUND);
        btnSurround.setBorder(new EmptyBorder(16, 0, 16, 0));
        btnSurround.add(btnNote);
        btnSurround.add(btnMatiere);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BACKGROUND);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(tablesPanel, BorderLayout.CENTER);   // le tableau prend tout l'espace disponible
        panel.add(btnSurround, BorderLayout.SOUTH); // les boutons en bas, hauteur minimale

        frame.getContentPane().add(panel);
        frame.setLocationRelativeTo(null); // centre la fenêtre à l'écran
        frame.setVisible(true);
    }

    // Applique le style "thème" au tableau : couleurs, police,
    // hauteur de ligne, style de l'en-tête et de la sélection.
    static void styleTable(JTable table) {
        table.setFont(Theme.FONT_TABLE);
        table.setForeground(Theme.TEXT_PRIMARY);
        table.setBackground(Theme.SURFACE);
        table.setGridColor(Theme.BORDER);
        table.setRowHeight(30);
        table.setSelectionBackground(Theme.ACCENT);
        table.setSelectionForeground(Theme.TEXT_ON_ACCENT);
        table.setShowGrid(true);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(Theme.FONT_HEADER);
        header.setBackground(Theme.SURFACE_ALT);
        header.setForeground(Theme.TEXT_PRIMARY);
        header.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
    }
}