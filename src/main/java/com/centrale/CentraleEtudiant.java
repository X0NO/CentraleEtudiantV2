package com.centrale;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.event.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.awt.Insets;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import java.sql.Connection;

public class CentraleEtudiant {

    static int[] columnNote = {2};
    static JTable tableNote;

    static String[] colonnes = {"Intro_Syst","Note 1"};

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
    
    static class NoteFilter extends DocumentFilter {

        static void ConnectNas(){
            //Connexion nas 

        String url = "jdbc:mariadb://192.168.1.142:3307/CentraleEtudiant"; 
        
        String user = "appli_java";
        String password = "MEyu+,AxZlW4P[WM";

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

    static void ListMatiere(){
        for (int i = 0; i < matière.length; i++) {
        nomsMatieres[i] = matière[i][0]; // récupère "Intro_Syst", "Init_Dev", etc.
        }
    } 

    static void AddMatiere()
    {
        //Nouvelle fenêtre pour ajouter une matière
        JFrame addMatiereFrame = new JFrame("Nouvelle Matière");
        addMatiereFrame.setSize(600, 300);
        addMatiereFrame.setLocationRelativeTo(null);
        addMatiereFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        //Contenu de la nouvelle fenêtre

        JTextField matiereField = new JTextField(20);

        JLabel labelMatiere = new JLabel("Nom de la matière :");

        // Panel avec GridBagLayout : une grille propre label/champ, ligne par ligne
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8); // espacement autour de chaque composant
        gbc.anchor = GridBagConstraints.WEST;

        // Ligne 0 : label + champ Matière
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(labelMatiere, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; // le champ s'étire proprement dans sa colonne
        centerPanel.add(matiereField, gbc);

        DefaultTableModel model = (DefaultTableModel) tableNote.getModel();

        JButton validButton = new JButton("Valider");
        validButton.addActionListener(new ActionListener() {
            @Override 
            public void actionPerformed(ActionEvent e){
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
    

        JButton annulButton = new JButton("Annuler");
        annulButton.addActionListener(new ActionListener() {
            @Override 
            public void actionPerformed(ActionEvent e) {
                addMatiereFrame.dispose();
            }
        });

        JPanel btnValidAnnulPanel = new JPanel();
        btnValidAnnulPanel.add(validButton);

        btnValidAnnulPanel.add(annulButton);
 
        //Panel principal de la fenêtre : le champ au centre, les boutons en bas
        JPanel newMatierePanel = new JPanel(new BorderLayout());
        newMatierePanel.add(centerPanel, BorderLayout.CENTER);
        newMatierePanel.add(btnValidAnnulPanel, BorderLayout.SOUTH);

        addMatiereFrame.getContentPane().add(newMatierePanel);

        addMatiereFrame.setVisible(true);
    }

    static void AddNote()
    {
        //Nouvelle fenêtre pour ajouter une note
        JFrame addNoteFrame = new JFrame("Nouvelle Note");
        addNoteFrame.setSize(600, 300);
        addNoteFrame.setLocationRelativeTo(null);
        addNoteFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
 
        //Contenu de la nouvelle fenêtre
 
        ListMatiere();
 
        JComboBox<String> comboMatiere = new JComboBox<>(nomsMatieres);
        JTextField noteField = new JTextField(10);
 
        JLabel labelMatiere = new JLabel("Matière :");
        JLabel labelNote = new JLabel("Note :");
        ((PlainDocument) noteField.getDocument()).setDocumentFilter(new NoteFilter());
 
        // Panel avec GridBagLayout : une grille propre label/champ, ligne par ligne
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8); // espacement autour de chaque composant
        gbc.anchor = GridBagConstraints.WEST;
 
        // Ligne 0 : label + combo Matière
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(labelMatiere, gbc);
 
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; // le combo s'étire proprement dans sa colonne
        centerPanel.add(comboMatiere, gbc);
 
        // Ligne 1 : label + champ Note
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        centerPanel.add(labelNote, gbc);
 
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        centerPanel.add(noteField, gbc);
 
        JButton validButton = new JButton("Valider");

        //Fonction du bouton Annuler : ferme la fenêtre

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

        int rowIndex = -1;

        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getValueAt(i, 0).equals(matiereChoisie)) {
                rowIndex = i;
                break;
            }
        }

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
            // Plus de case vide : on ajoute une nouvelle colonne "Note X" remplie de "N/A"
            // sauf pour notre ligne, où on met directement le résultat
            String[] nouvelleColonne = new String[model.getRowCount()];
            for (int i = 0; i < nouvelleColonne.length; i++) {
                nouvelleColonne[i] = (i == rowIndex) ? resultat : "";
            }
            model.addColumn("Note" + columnNote[0]++, nouvelleColonne);
            
        }

        addNoteFrame.dispose();

    }
});

        JButton annulButton = new JButton("Annuler");

        //Fonction du bouton Annuler : ferme la fenêtre

        annulButton.addActionListener(new ActionListener() {
            @Override 
            public void actionPerformed(ActionEvent e) {
            addNoteFrame.dispose();
            }
        });
 
        //Conteneur pour le champ de texte + button
        JPanel btnValidAnnulPanel = new JPanel();
        btnValidAnnulPanel.add(validButton);

        btnValidAnnulPanel.add(annulButton);
 
        //Panel principal de la fenêtre : le champ au centre, les boutons en bas
        JPanel newNotePanel = new JPanel(new BorderLayout());
        newNotePanel.add(centerPanel, BorderLayout.CENTER);
        newNotePanel.add(btnValidAnnulPanel, BorderLayout.SOUTH);
 
        //Ajoute Contenu à la fenêtre
        addNoteFrame.getContentPane().add(newNotePanel);
 
        //Afficher la fenêtre 
        addNoteFrame.setVisible(true);
 
    }

    public static void main(String[] args) {

        //ConnectNas();

        //Creation de la fenêtre principale
        JFrame frame = new JFrame("Centrale Etudiant"); 

        //Définir la taille de la fenêtre
        frame.setSize(1000, 800);

        //Configurer l'action de fermeture de la fenêtre
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Création du tableau + Unclickable
        DefaultTableModel tableUnClickable = new DefaultTableModel(matière, colonnes){
            @Override 
            public boolean isCellEditable(int row, int column) {
                return column == 1 && row == 0;
            }
        };

        tableNote = new JTable(tableUnClickable);
        JScrollPane scroll = new JScrollPane(tableNote);
        
        //Création boutons 
        JButton btnNote = new JButton("Ajouter Une Note");

        //Ajouter une fonction au bouton
        btnNote.addActionListener(new ActionListener() {
            @Override 
            public void actionPerformed(ActionEvent e) {
                AddNote();
            }
        });

        JButton btnMatiere = new JButton("Ajouter Une Matière");

        //Ajouter une fonction au bouton
        btnMatiere.addActionListener(new ActionListener() {
            @Override 
            public void actionPerformed(ActionEvent e) {
            AddMatiere();
            }
        });


        //Ajouter les composants à la fenêtre
        JPanel btnSurround = new JPanel();
        btnSurround.add(btnNote);
        btnSurround.add(btnMatiere);
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER); // le tableau prend tout l'espace disponible
        panel.add(btnSurround, BorderLayout.SOUTH);  // les boutons en bas, hauteur minimale

        frame.getContentPane().add(panel);

        //Afficher la fenêtre 
        frame.setVisible(true);
        
    }
}