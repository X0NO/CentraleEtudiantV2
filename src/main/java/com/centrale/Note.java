package com.centrale;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import io.github.cdimascio.dotenv.Dotenv;

public class Note {

    // =====================================================================
    // CONNEXION BASE DE DONNÉES
    // =====================================================================
   public static  Dotenv dotenv = Dotenv.load();

    private static final String urlDB = dotenv.get("DB_URL");
    private static final String USER = dotenv.get("DB_USERNAME");
    private static final String PASSWORD =dotenv.get("DB_PASSWORD");

    private static final String FILE_OFFLINE_CACHE = "offline_notes.dat";

    private static final String[] MATIERES_PAR_DEFAUT = {
            "Intro_Syst", "Init_Dev", "Maths", "Intro_BD", "Anglais", "Commu", "PPP", "Dev_Web"
    };

    // =====================================================================
    // THEME
    // =====================================================================
    static final class Theme {
        static final Color BACKGROUND      = new Color(0x1E1E2E);
        static final Color SURFACE         = new Color(0x2A2A3C);
        static final Color SURFACE_ALT     = new Color(0x252536);
        static final Color ACCENT          = new Color(0x89B4FA);
        static final Color ACCENT_HOVER    = new Color(0x74A0F0);
        static final Color TEXT_PRIMARY    = new Color(0xE0E0E8);
        static final Color TEXT_ON_ACCENT  = new Color(0x1E1E2E);
        static final Color BORDER          = new Color(0x3A3A4E);

        static final Font FONT_TITLE  = new Font("Segoe UI", Font.BOLD, 20);
        static final Font FONT_LABEL  = new Font("Segoe UI", Font.PLAIN, 14);
        static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 13);
        static final Font FONT_TABLE  = new Font("Segoe UI", Font.PLAIN, 13);
        static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 13);
    }

    static int[] columnNote = {2};
    static JTable tableNote;
    static JTable tableMoyenne;
    static String[] nomsMatieres = new String[0];

    // =====================================================================
    // STOCKAGE HORS-LIGNE SÉCURISÉ (CHIFFREMENT AES)
    // =====================================================================
    static final class SecureOfflineStorage {
        private static final String SECRET_KEY = "CentraleStudentSecretKeyForCache";

        private static SecretKeySpec getKey() throws Exception {
            byte[] key = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // 128 bit key
            return new SecretKeySpec(key, "AES");
        }

        public static synchronized void sauvegarderNoteHorsLigne(String nomMatiere, int valeur, int quotient) {
            try {
                List<String> lignes = lireNotesHorsLigne();
                lignes.add(nomMatiere + ";" + valeur + ";" + quotient);

                StringBuilder sb = new StringBuilder();
                for (String line : lignes) {
                    sb.append(line).append("\n");
                }

                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, getKey());
                byte[] encryptedBytes = cipher.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));

                try (FileOutputStream fos = new FileOutputStream(FILE_OFFLINE_CACHE)) {
                    fos.write(encryptedBytes);
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de la sauvegarde sécurisée hors-ligne : " + e.getMessage());
            }
        }

        public static synchronized List<String> lireNotesHorsLigne() {
            List<String> resultats = new ArrayList<>();
            File file = new File(FILE_OFFLINE_CACHE);
            if (!file.exists()) return resultats;

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = fis.readAllBytes();
                if (data.length == 0) return resultats;

                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, getKey());
                byte[] decryptedBytes = cipher.doFinal(data);

                String content = new String(decryptedBytes, StandardCharsets.UTF_8);
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        resultats.add(line.trim());
                    }
                }
            } catch (Exception e) {
                System.err.println("Erreur de lecture du cache hors-ligne : " + e.getMessage());
            }
            return resultats;
        }

        public static synchronized void viderCache() {
            File file = new File(FILE_OFFLINE_CACHE);
            if (file.exists()) {
                file.delete();
            }
        }

        public static void synchroniserNotesHorsLigne() {
            List<String> notesEnAttente = lireNotesHorsLigne();
            if (notesEnAttente.isEmpty()) return;

            System.out.println("Synchronisation des notes saisies hors-ligne...");
            List<String> nonSynchro = new ArrayList<>();

            for (String ligne : notesEnAttente) {
                String[] parts = ligne.split(";");
                if (parts.length == 3) {
                    String matiere = parts[0];
                    int valeur = Integer.parseInt(parts[1]);
                    int quotient = Integer.parseInt(parts[2]);

                    Integer idMatiere = MatiereDAO.trouverIdParNom(matiere);
                    if (idMatiere != null) {
                        boolean ok = NoteDAO.inserer(idMatiere, valeur, quotient);
                        if (!ok) nonSynchro.add(ligne);
                    }
                }
            }

            viderCache();
            // Si certaines n'ont pas pu s'insérer, re-sauvegarder
            for (String line : nonSynchro) {
                String[] p = line.split(";");
                sauvegarderNoteHorsLigne(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
        }
    }

    // =====================================================================
    // ACCÈS BASE DE DONNÉES
    // =====================================================================
    static final class Database {

        static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(urlDB, USER, PASSWORD);
        }

        static boolean connecter() {
            try (Connection conn = getConnection()) {
                System.out.println("Connexion à la base de données réussie !");
                // Tente la synchro des notes stockées en local lors des échecs précédents
                SecureOfflineStorage.synchroniserNotesHorsLigne();
                return true;
            } catch (SQLException e) {
                System.err.println("Impossible de se connecter au Raspberry Pi : " + e.getMessage());
                return false;
            }
        }

        static void creerTables() {
            String sqlMatieres = "CREATE TABLE IF NOT EXISTS matieres (" +
                    "id SERIAL PRIMARY KEY," +
                    "nom VARCHAR(100) NOT NULL UNIQUE," +
                    "coefficient NUMERIC(4,2) NOT NULL DEFAULT 1" +
                    ")";

            String sqlNotes = "CREATE TABLE IF NOT EXISTS notes (" +
                    "id SERIAL PRIMARY KEY," +
                    "matiere_id INTEGER NOT NULL REFERENCES matieres(id) ON DELETE CASCADE," +
                    "valeur INTEGER NOT NULL," +
                    "quotient INTEGER NOT NULL," +
                    "date_creation TIMESTAMP DEFAULT NOW()" +
                    ")";

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sqlMatieres);
                stmt.execute(sqlNotes);
                System.out.println("Tables 'matieres' et 'notes' prêtes.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        static void seedMatieresSiVide() {
            if (!MatiereDAO.listerNoms().isEmpty()) return;
            for (String nom : MATIERES_PAR_DEFAUT) {
                MatiereDAO.inserer(nom, 1.0);
            }
        }
    }

    // =====================================================================
    // DAO MATIÈRES
    // =====================================================================
    static final class MatiereDAO {

        static Integer trouverIdParNom(String nom) {
            String sql = "SELECT id FROM matieres WHERE nom = ?";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nom);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("id");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        static int inserer(String nom, double coefficient) {
            String sql = "INSERT INTO matieres (nom, coefficient) VALUES (?, ?) RETURNING id";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nom);
                stmt.setDouble(2, coefficient);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("id");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return -1;
        }

        static List<String> listerNoms() {
            List<String> noms = new ArrayList<>();
            String sql = "SELECT nom FROM matieres ORDER BY id";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) noms.add(rs.getString("nom"));
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return noms;
        }
    }

    // =====================================================================
    // DAO NOTES (AVEC SUPPRESSION ET INSERTION)
    // =====================================================================
    static final class NoteDAO {

        static boolean inserer(int matiereId, int valeur, int quotient) {
            String sql = "INSERT INTO notes (matiere_id, valeur, quotient) VALUES (?, ?, ?)";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, matiereId);
                stmt.setInt(2, valeur);
                stmt.setInt(3, quotient);
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        /**
         * Supprime une note spécifique de la base de données.
         */
        static boolean supprimer(int matiereId, int valeur, int quotient) {
            // Supprime la note la plus récente correspondant aux critères
            String sql = "DELETE FROM notes WHERE id IN (" +
                         "  SELECT id FROM notes WHERE matiere_id = ? AND valeur = ? AND quotient = ? " +
                         "  ORDER BY id DESC LIMIT 1" +
                         ")";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, matiereId);
                stmt.setInt(2, valeur);
                stmt.setInt(3, quotient);
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        static List<String> listerPourMatiere(int matiereId) {
            List<String> resultats = new ArrayList<>();
            String sql = "SELECT valeur, quotient FROM notes WHERE matiere_id = ? ORDER BY id";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, matiereId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        resultats.add(rs.getInt("valeur") + "/" + rs.getInt("quotient"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return resultats;
        }
    }

    // =====================================================================
    // UTILITAIRES & THEME
    // =====================================================================
    static JButton themedButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.FONT_BUTTON);
        button.setBackground(Theme.ACCENT);
        button.setForeground(Theme.TEXT_ON_ACCENT);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 18, 8, 18));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

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

    static class NoteFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
            String proposedText = currentText.substring(0, offset) + text + currentText.substring(offset + length);

            if (proposedText.matches("\\d*(/\\d*)?")) {
                super.replace(fb, offset, length, text, attrs);
            }
        }
    }

    static void ListMatiere() {
        DefaultTableModel model = (DefaultTableModel) tableNote.getModel();
        nomsMatieres = new String[model.getRowCount()];
        for (int i = 0; i < model.getRowCount(); i++) {
            nomsMatieres[i] = (String) model.getValueAt(i, 0);
        }
    }

    /**
     * Recharge complètement l'affichage de l'application à partir de la BD.
     */
    static void rechargerApplication() {
        chargerDonneesInitiales();

        DefaultTableModel modelNote = new DefaultTableModel(donneesInitiales, colonnesInitiales) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableNote.setModel(modelNote);

        ListMatiere();
        List<Double> moyennes = calculMoyenneGenerale();

        DefaultTableModel modelMoyenne = new DefaultTableModel(new String[]{"Matière", "Moyenne"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (int i = 0; i < nomsMatieres.length; i++) {
            Double m = (i < moyennes.size()) ? moyennes.get(i) : null;
            String affichage = (m == null) ? "" : String.format("%.2f", m);
            modelMoyenne.addRow(new Object[]{ nomsMatieres[i], affichage });
        }
        tableMoyenne.setModel(modelMoyenne);
    }

    // =====================================================================
    // FENÊTRE : SUPPRIMER UNE NOTE
    // =====================================================================
    static void DeleteNote() {
        JFrame deleteFrame = new JFrame("Supprimer Une Note");
        deleteFrame.setSize(500, 250);
        deleteFrame.setLocationRelativeTo(null);
        deleteFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        deleteFrame.getContentPane().setBackground(Theme.BACKGROUND);

        ListMatiere();
        JComboBox<String> comboMatiere = new JComboBox<>(nomsMatieres);
        JTextField noteField = new JTextField(10);
        styleTextField(noteField);
        ((PlainDocument) noteField.getDocument()).setDocumentFilter(new NoteFilter());

        JLabel labelMatiere = new JLabel("Matière :");
        JLabel labelNote = new JLabel("Note à supprimer (ex 15/20) :");
        styleLabel(labelMatiere);
        styleLabel(labelNote);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(Theme.BACKGROUND);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        centerPanel.add(labelMatiere, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        centerPanel.add(comboMatiere, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        centerPanel.add(labelNote, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        centerPanel.add(noteField, gbc);

        JButton btnSupprimer = themedButton("Supprimer");
        btnSupprimer.addActionListener(e -> {
            String saisie = noteField.getText().trim();
            if (!saisie.matches("\\d+/\\d+")) {
                JOptionPane.showMessageDialog(deleteFrame, "Format attendu : note/quotient (ex: 15/20)", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String[] parts = saisie.split("/");
            int val = Integer.parseInt(parts[0]);
            int quot = Integer.parseInt(parts[1]);
            String matiereChoisie = (String) comboMatiere.getSelectedItem();

            Integer matiereId = MatiereDAO.trouverIdParNom(matiereChoisie);
            if (matiereId == null) {
                JOptionPane.showMessageDialog(deleteFrame, "Matière introuvable.", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }

            boolean succes = NoteDAO.supprimer(matiereId, val, quot);
            if (succes) {
                JOptionPane.showMessageDialog(deleteFrame, "Note supprimée avec succès !");
                rechargerApplication();
                deleteFrame.dispose();
            } else {
                JOptionPane.showMessageDialog(deleteFrame, "Impossible de trouver cette note en BDD.", "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton btnAnnuler = themedButton("Annuler");
        btnAnnuler.addActionListener(e -> deleteFrame.dispose());

        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(Theme.BACKGROUND);
        btnPanel.add(btnSupprimer);
        btnPanel.add(btnAnnuler);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Theme.BACKGROUND);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        deleteFrame.getContentPane().add(mainPanel);
        deleteFrame.setVisible(true);
    }

    // =====================================================================
    // FENÊTRES AJOUT (MATIERE & NOTE)
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

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(Theme.BACKGROUND);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        centerPanel.add(labelMatiere, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        centerPanel.add(matiereField, gbc);

        JButton validButton = themedButton("Valider");
        validButton.addActionListener(e -> {
            String saisie = matiereField.getText().trim();
            if (saisie.isEmpty()) {
                JOptionPane.showMessageDialog(addMatiereFrame, "Le nom ne peut pas être vide.", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int nouvelId = MatiereDAO.inserer(saisie, 1.0);
            if (nouvelId == -1) {
                JOptionPane.showMessageDialog(addMatiereFrame, "Erreur lors de l'enregistrement ou matière existante.", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }

            rechargerApplication();
            addMatiereFrame.dispose();
        });

        JButton annulButton = themedButton("Annuler");
        annulButton.addActionListener(e -> addMatiereFrame.dispose());

        JPanel btnValidAnnulPanel = new JPanel();
        btnValidAnnulPanel.setBackground(Theme.BACKGROUND);
        btnValidAnnulPanel.add(validButton);
        btnValidAnnulPanel.add(annulButton);

        JPanel newMatierePanel = new JPanel(new BorderLayout());
        newMatierePanel.setBackground(Theme.BACKGROUND);
        newMatierePanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        newMatierePanel.add(centerPanel, BorderLayout.CENTER);
        newMatierePanel.add(btnValidAnnulPanel, BorderLayout.SOUTH);

        addMatiereFrame.getContentPane().add(newMatierePanel);
        addMatiereFrame.setVisible(true);
    }

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
        ((PlainDocument) noteField.getDocument()).setDocumentFilter(new NoteFilter());

        JLabel labelMatiere = new JLabel("Matière :");
        JLabel labelNote = new JLabel("Note :");
        styleLabel(labelMatiere);
        styleLabel(labelNote);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(Theme.BACKGROUND);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        centerPanel.add(labelMatiere, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        centerPanel.add(comboMatiere, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        centerPanel.add(labelNote, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        centerPanel.add(noteField, gbc);

        JButton validButton = themedButton("Valider");
        validButton.addActionListener(e -> {
            String saisie = noteField.getText();
            if (!saisie.matches("\\d+/\\d+")) {
                JOptionPane.showMessageDialog(addNoteFrame, "Format attendu : note/quotient (ex : 15/20)", "Format invalide", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String[] parts = saisie.split("/");
            int note = Integer.parseInt(parts[0]);
            int quotient = Integer.parseInt(parts[1]);

            if (note > quotient) {
                JOptionPane.showMessageDialog(addNoteFrame, "La note ne peut pas être supérieure au quotient", "Note invalide", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String matiereChoisie = (String) comboMatiere.getSelectedItem();
            Integer matiereId = MatiereDAO.trouverIdParNom(matiereChoisie);

            // tentative d'insertion BDD
            boolean insertionOk = false;
            if (matiereId != null) {
                insertionOk = NoteDAO.inserer(matiereId, note, quotient);
            }

            if (!insertionOk) {
                // Échec de la connexion au Raspberry Pi : stockage local chiffré
                SecureOfflineStorage.sauvegarderNoteHorsLigne(matiereChoisie, note, quotient);
                JOptionPane.showMessageDialog(addNoteFrame, "Connexion BDD indisponible. La note a été sauvegardée en local de façon sécurisée.\nElle sera synchronisée dès la reconnexion.", "Mode Hors-Ligne", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(addNoteFrame, "Note enregistrée avec succès !");
            }

            rechargerApplication();
            addNoteFrame.dispose();
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

    static List<Double> calculMoyenneGenerale() {
        DefaultTableModel model = (DefaultTableModel) tableNote.getModel();
        List<Double> moyennesParMatiere = new ArrayList<>();

        for (int row = 0; row < model.getRowCount(); row++) {
            double sommeNotes = 0;
            double sommeQuotients = 0;

            for (int col = 1; col < model.getColumnCount(); col++) {
                Object valeur = model.getValueAt(row, col);
                String texte = (valeur == null) ? "" : valeur.toString();

                if (texte.matches("\\d+/\\d+")) {
                    String[] parts = texte.split("/");
                    sommeNotes += Double.parseDouble(parts[0]);
                    sommeQuotients += Double.parseDouble(parts[1]);
                }
            }

            if (sommeQuotients > 0) {
                moyennesParMatiere.add((sommeNotes / sommeQuotients) * 20);
            } else {
                moyennesParMatiere.add(null);
            }
        }
        return moyennesParMatiere;
    }

    static void styleTextField(JTextField field) {
        field.setFont(Theme.FONT_LABEL);
        field.setBackground(Theme.SURFACE);
        field.setForeground(Theme.TEXT_PRIMARY);
        field.setCaretColor(Theme.TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(4, 6, 4, 6)));
    }

    static void styleLabel(JLabel label) {
        label.setFont(Theme.FONT_LABEL);
        label.setForeground(Theme.TEXT_PRIMARY);
    }

    static Object[][] donneesInitiales;
    static String[] colonnesInitiales;

    static void chargerDonneesInitiales() {
        List<String> noms = MatiereDAO.listerNoms();
        List<List<String>> notesParMatiere = new ArrayList<>();
        int maxNotes = 0;

        for (String nom : noms) {
            Integer id = MatiereDAO.trouverIdParNom(nom);
            List<String> notesMatiere = (id != null) ? NoteDAO.listerPourMatiere(id) : new ArrayList<>();
            
            // Inclut également les notes présentes dans le cache local hors-ligne s'il existe
            List<String> offlineLines = SecureOfflineStorage.lireNotesHorsLigne();
            for (String line : offlineLines) {
                String[] p = line.split(";");
                if (p.length == 3 && p[0].equals(nom)) {
                    notesMatiere.add(p[1] + "/" + p[2] + " (Local)");
                }
            }

            notesParMatiere.add(notesMatiere);
            maxNotes = Math.max(maxNotes, notesMatiere.size());
        }
        if (maxNotes == 0) maxNotes = 1;

        colonnesInitiales = new String[maxNotes + 1];
        colonnesInitiales[0] = "Matière";
        for (int i = 1; i <= maxNotes; i++) colonnesInitiales[i] = "Note " + i;

        donneesInitiales = new Object[noms.size()][maxNotes + 1];
        for (int i = 0; i < noms.size(); i++) {
            donneesInitiales[i][0] = noms.get(i);
            List<String> notesMatiere = notesParMatiere.get(i);
            for (int c = 1; c <= maxNotes; c++) {
                donneesInitiales[i][c] = (c - 1 < notesMatiere.size()) ? notesMatiere.get(c - 1) : "";
            }
        }

        columnNote[0] = maxNotes + 1;
    }

    public static void main(String[] args) {

        boolean isConnected = Database.connecter();
        if (isConnected) {
            Database.creerTables();
            Database.seedMatieresSiVide();
        }

        chargerDonneesInitiales();

        UIManager.put("Panel.background", Theme.BACKGROUND);
        UIManager.put("OptionPane.background", Theme.BACKGROUND);
        UIManager.put("OptionPane.messageForeground", Theme.TEXT_PRIMARY);
        UIManager.put("Button.background", Theme.ACCENT);
        UIManager.put("Button.foreground", Theme.TEXT_ON_ACCENT);

        JFrame frame = new JFrame("Centrale Étudiant");
        frame.setSize(1000, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(Theme.BACKGROUND);

        JLabel titleLabel = new JLabel("Centrale Étudiant", SwingConstants.CENTER);
        titleLabel.setFont(Theme.FONT_TITLE);
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setBorder(new EmptyBorder(16, 0, 16, 0));

        DefaultTableModel tableUnClickable = new DefaultTableModel(donneesInitiales, colonnesInitiales) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tableNote = new JTable(tableUnClickable);
        styleTable(tableNote);

        JScrollPane scroll = new JScrollPane(tableNote);
        scroll.setOpaque(true);
        scroll.setBackground(Theme.BACKGROUND);
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setBorder(new EmptyBorder(0, 20, 0, 20));

        DefaultTableModel modelMoyenne = new DefaultTableModel(new String[]{"Matière", "Moyenne"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        ListMatiere();
        List<Double> moyennesInitiales = calculMoyenneGenerale();
        for (int i = 0; i < nomsMatieres.length; i++) {
            Double m = (i < moyennesInitiales.size()) ? moyennesInitiales.get(i) : null;
            String affichage = (m == null) ? "" : String.format("%.2f", m);
            modelMoyenne.addRow(new Object[]{ nomsMatieres[i], affichage });
        }

        tableMoyenne = new JTable(modelMoyenne);
        styleTable(tableMoyenne);

        JScrollPane scrollMoyenne = new JScrollPane(tableMoyenne);
        scrollMoyenne.setOpaque(true);
        scrollMoyenne.setBackground(Theme.BACKGROUND);
        scrollMoyenne.getViewport().setBackground(Theme.SURFACE);
        scrollMoyenne.setBorder(new EmptyBorder(0, 20, 0, 20));

        JPanel tablesPanel = new JPanel(new GridLayout(2, 1, 0, 12));
        tablesPanel.setBackground(Theme.BACKGROUND);
        tablesPanel.add(scroll);
        tablesPanel.add(scrollMoyenne);

        JButton btnNote = themedButton("Ajouter Une Note");
        btnNote.addActionListener(e -> AddNote());

        JButton btnDeleteNote = themedButton("Supprimer Une Note");
        btnDeleteNote.addActionListener(e -> DeleteNote());

        JButton btnMatiere = themedButton("Ajouter Une Matière");
        btnMatiere.addActionListener(e -> AddMatiere());

        JPanel btnSurround = new JPanel();
        btnSurround.setBackground(Theme.BACKGROUND);
        btnSurround.setBorder(new EmptyBorder(16, 0, 16, 0));
        btnSurround.add(btnNote);
        btnSurround.add(btnDeleteNote);
        btnSurround.add(btnMatiere);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BACKGROUND);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(tablesPanel, BorderLayout.CENTER);
        panel.add(btnSurround, BorderLayout.SOUTH);

        frame.getContentPane().add(panel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

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