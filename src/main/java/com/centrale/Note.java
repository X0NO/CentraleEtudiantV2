package com.centrale;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
    public static Dotenv dotenv = Dotenv.load();

    private static final String urlDB = dotenv.get("DB_URL");
    private static final String USER = dotenv.get("DB_USERNAME");
    private static final String PASSWORD = dotenv.get("DB_PASSWORD");

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
    // MIROIR CSV LOCAL (2e TÉMOIN DES NOTES)
    //
    // Fichier notes_backup.csv, une ligne par matière ou par note :
    //     type;matiere;valeur;quotient;statut
    //   type   : M (matière) ou N (note)
    //   statut : SYNC = présent dans la BDD ET dans le fichier
    //            ADD  = ajouté hors-ligne, pas encore envoyé à la BDD
    //            DEL  = supprimé hors-ligne, pas encore supprimé de la BDD
    //
    // Règles :
    //  - chaque ajout/suppression met à jour la BDD ET le CSV en même temps ;
    //  - BDD injoignable : le CSV sert de témoin (affichage + modifications) ;
    //  - retour en ligne : s'il n'y a rien d'en attente dans le CSV, il est
    //    réécrit à partir de la BDD ; sinon les changements en attente sont
    //    appliqués à la BDD, puis le CSV est réaligné sur la BDD.
    // =====================================================================
    static final class CsvMirror {
        static final String FILE = "notes_backup.csv";
        static final String HEADER = "type;matiere;valeur;quotient;statut";
        static final String SYNC = "SYNC";
        static final String ADD = "ADD";
        static final String DEL = "DEL";

        static final class Entry {
            final String type;
            final String matiere;
            final int valeur;
            final int quotient;
            String statut;

            Entry(String type, String matiere, int valeur, int quotient, String statut) {
                this.type = type;
                this.matiere = matiere;
                this.valeur = valeur;
                this.quotient = quotient;
                this.statut = statut;
            }

            boolean estNote() {
                return type.equals("N");
            }

            boolean memeNote(String m, int v, int q) {
                return estNote() && matiere.equals(m) && valeur == v && quotient == q;
            }

            String toLine() {
                return type + ";" + matiere + ";" + valeur + ";" + quotient + ";" + statut;
            }
        }

        /** Lit le CSV. Si la lecture échoue, on lève une exception plutôt que d'écraser le fichier ensuite. */
        static synchronized List<Entry> lire() {
            List<Entry> res = new ArrayList<>();
            Path p = Paths.get(FILE);
            if (!Files.exists(p)) return res;
            try {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String[] parts = line.split(";");
                    if (parts.length != 5 || parts[0].equals("type")) continue;
                    try {
                        res.add(new Entry(parts[0], parts[1],
                                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), parts[4]));
                    } catch (NumberFormatException ignored) {
                        // ligne illisible : ignorée
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Lecture de " + FILE + " impossible", e);
            }
            return res;
        }

        /** Écriture atomique (fichier temporaire puis remplacement) pour ne jamais laisser un CSV à moitié écrit. */
        static synchronized void ecrire(List<Entry> entries) {
            List<String> lignes = new ArrayList<>();
            lignes.add(HEADER);
            for (Entry e : entries) lignes.add(e.toLine());
            try {
                Path tmp = Paths.get(FILE + ".tmp");
                Files.write(tmp, lignes, StandardCharsets.UTF_8);
                Files.move(tmp, Paths.get(FILE), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("Erreur d'écriture de " + FILE + " : " + e.getMessage());
            }
        }

        // ---------- Mises à jour appelées en même temps que la BDD ----------

        static synchronized void ajouterMatiere(String nom, boolean dbOk) {
            List<Entry> l = lire();
            l.add(new Entry("M", nom, 0, 0, dbOk ? SYNC : ADD));
            ecrire(l);
        }

        static synchronized void ajouterNote(String matiere, int valeur, int quotient, boolean dbOk) {
            List<Entry> l = lire();
            l.add(new Entry("N", matiere, valeur, quotient, dbOk ? SYNC : ADD));
            ecrire(l);
        }

        /**
         * Supprime la note la plus récente correspondante (comme la BDD).
         * Hors-ligne, une note déjà synchronisée est marquée DEL (à supprimer de la BDD plus tard),
         * une note ajoutée hors-ligne (ADD) est simplement retirée du fichier.
         * @return true si la note existait dans le fichier
         */
        static synchronized boolean supprimerNote(String matiere, int valeur, int quotient, boolean dbOk) {
            List<Entry> l = lire();
            for (int i = l.size() - 1; i >= 0; i--) {
                Entry e = l.get(i);
                if (e.memeNote(matiere, valeur, quotient) && !e.statut.equals(DEL)) {
                    if (dbOk || e.statut.equals(ADD)) l.remove(i);
                    else e.statut = DEL;
                    ecrire(l);
                    return true;
                }
            }
            return false;
        }

        // ---------- Lecture (utilisée pour l'affichage) ----------

        static synchronized boolean matiereExiste(String nom) {
            for (Entry e : lire()) {
                if (e.type.equals("M") && e.matiere.equalsIgnoreCase(nom)) return true;
            }
            return false;
        }

        static synchronized List<String> listerMatieres() {
            List<String> noms = new ArrayList<>();
            for (Entry e : lire()) {
                if (e.type.equals("M")) noms.add(e.matiere);
            }
            return noms;
        }

        static synchronized List<String> listerNotes(String matiere) {
            List<String> notes = new ArrayList<>();
            for (Entry e : lire()) {
                if (e.estNote() && e.matiere.equals(matiere) && !e.statut.equals(DEL)) {
                    notes.add(e.valeur + "/" + e.quotient);
                }
            }
            return notes;
        }

        // ---------- Comparaison / synchronisation au retour en ligne ----------

        static synchronized void reconcilier() {
            List<Entry> l = lire();

            boolean enAttente = false;
            for (Entry e : l) {
                if (!e.statut.equals(SYNC)) { enAttente = true; break; }
            }

            if (enAttente) {
                boolean toutOk = true;

                // 1) les matières créées hors-ligne d'abord
                for (Entry e : l) {
                    if (e.type.equals("M") && e.statut.equals(ADD)) {
                        boolean ok = MatiereDAO.trouverIdParNom(e.matiere) != null
                                || MatiereDAO.inserer(e.matiere, 1.0) != -1;
                        if (ok) e.statut = SYNC; else toutOk = false;
                    }
                }

                // 2) puis les notes ajoutées / supprimées hors-ligne
                Iterator<Entry> it = l.iterator();
                while (it.hasNext()) {
                    Entry e = it.next();
                    if (!e.estNote() || e.statut.equals(SYNC)) continue;

                    Integer id = MatiereDAO.trouverIdParNom(e.matiere);
                    if (id == null) { toutOk = false; continue; }

                    if (e.statut.equals(ADD)) {
                        if (NoteDAO.inserer(id, e.valeur, e.quotient)) e.statut = SYNC;
                        else toutOk = false;
                    } else if (e.statut.equals(DEL)) {
                        if (NoteDAO.supprimer(id, e.valeur, e.quotient) >= 0) it.remove();
                        else toutOk = false;
                    }
                }

                // On enregistre ce qui a pu être envoyé ; le reste reste en attente
                ecrire(l);
                if (!toutOk) return; // on ne touche pas au CSV tant que la BDD n'est pas à jour
            }

            // Plus rien en attente : le CSV est réaligné sur la BDD
            reconstruireDepuisBD();
        }

        /** Réécrit le CSV à partir de la BDD. Si la lecture BDD échoue, le CSV existant n'est pas touché. */
        static synchronized void reconstruireDepuisBD() {
            String sql = "SELECT m.nom, n.valeur, n.quotient FROM matieres m "
                       + "LEFT JOIN notes n ON n.matiere_id = m.id ORDER BY m.id, n.id";
            List<Entry> l = new ArrayList<>();
            String derniere = null;
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String nom = rs.getString("nom");
                    if (!nom.equals(derniere)) {
                        l.add(new Entry("M", nom, 0, 0, SYNC));
                        derniere = nom;
                    }
                    int valeur = rs.getInt("valeur");
                    if (!rs.wasNull()) {
                        l.add(new Entry("N", nom, valeur, rs.getInt("quotient"), SYNC));
                    }
                }
            } catch (SQLException e) {
                System.err.println("Lecture BDD impossible, CSV conservé tel quel : " + e.getMessage());
                return;
            }
            ecrire(l);
        }
    }

    // =====================================================================
    // ACCÈS BASE DE DONNÉES
    // =====================================================================
    static final class Database {

        static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(urlDB, USER, PASSWORD);
        }

        /** true si la BDD répond. */
        static boolean ping() {
            try (Connection conn = getConnection()) {
                return true;
            } catch (SQLException e) {
                return false;
            }
        }

        /** Si la BDD est joignable : compare BDD et CSV, et aligne l'un sur l'autre (voir CsvMirror). */
        static void synchroniser() {
            if (!ping()) return;
            try {
                CsvMirror.reconcilier();
            } catch (Exception e) {
                System.err.println("Synchronisation BDD/CSV impossible : " + e.getMessage());
            }
        }

        static boolean connecter() {
            try (Connection conn = getConnection()) {
                System.out.println("Connexion à la base de données réussie !");
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
         * Supprime la note la plus récente correspondant aux critères.
         * @return 1 = supprimée, 0 = note introuvable en BDD, -1 = erreur (BDD injoignable)
         */
        static int supprimer(int matiereId, int valeur, int quotient) {
            String sql = "DELETE FROM notes WHERE id IN (" +
                         "  SELECT id FROM notes WHERE matiere_id = ? AND valeur = ? AND quotient = ? " +
                         "  ORDER BY id DESC LIMIT 1" +
                         ")";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, matiereId);
                stmt.setInt(2, valeur);
                stmt.setInt(3, quotient);
                return stmt.executeUpdate() > 0 ? 1 : 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return -1;
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
     * Recharge complètement l'affichage de l'application (BDD si joignable, sinon CSV local).
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

            // Remet d'abord BDD et CSV d'accord si la BDD vient de revenir
            Database.synchroniser();

            // 1) BDD : 1 = supprimée, 0 = introuvable, -1 = BDD injoignable
            int res = -1;
            if (Database.ping()) {
                Integer matiereId = MatiereDAO.trouverIdParNom(matiereChoisie);
                if (matiereId != null) res = NoteDAO.supprimer(matiereId, val, quot);
            }
            if (res == 0) {
                JOptionPane.showMessageDialog(deleteFrame, "Impossible de trouver cette note en BDD.", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 2) CSV local : mis à jour en même temps, dans tous les cas
            boolean dbOk = (res == 1);
            boolean trouveCsv = CsvMirror.supprimerNote(matiereChoisie, val, quot, dbOk);
            if (!dbOk && !trouveCsv) {
                JOptionPane.showMessageDialog(deleteFrame, "Note introuvable dans le fichier local.", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (dbOk) {
                JOptionPane.showMessageDialog(deleteFrame, "Note supprimée avec succès !");
            } else {
                JOptionPane.showMessageDialog(deleteFrame, "Connexion BDD indisponible. La note a été supprimée du fichier local.\nElle sera supprimée de la BDD dès la reconnexion.", "Mode Hors-Ligne", JOptionPane.INFORMATION_MESSAGE);
            }
            rechargerApplication();
            deleteFrame.dispose();
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
            if (saisie.contains(";")) {
                JOptionPane.showMessageDialog(addMatiereFrame, "Le caractère ';' est interdit dans le nom (séparateur du CSV).", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Database.synchroniser();

            if (Database.ping()) {
                if (MatiereDAO.inserer(saisie, 1.0) == -1) {
                    JOptionPane.showMessageDialog(addMatiereFrame, "Erreur lors de l'enregistrement ou matière existante.", "Erreur", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                CsvMirror.ajouterMatiere(saisie, true);
            } else {
                if (CsvMirror.matiereExiste(saisie)) {
                    JOptionPane.showMessageDialog(addMatiereFrame, "Cette matière existe déjà.", "Erreur", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                CsvMirror.ajouterMatiere(saisie, false);
                JOptionPane.showMessageDialog(addMatiereFrame, "Connexion BDD indisponible. La matière a été enregistrée dans le fichier local.\nElle sera envoyée à la BDD dès la reconnexion.", "Mode Hors-Ligne", JOptionPane.INFORMATION_MESSAGE);
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

            // Remet d'abord BDD et CSV d'accord si la BDD vient de revenir
            Database.synchroniser();

            Integer matiereId = MatiereDAO.trouverIdParNom(matiereChoisie);

            // tentative d'insertion BDD
            boolean insertionOk = false;
            if (matiereId != null) {
                insertionOk = NoteDAO.inserer(matiereId, note, quotient);
            }

            // Le CSV local est mis à jour en même temps que la BDD
            // (statut SYNC si la BDD a accepté la note, ADD si elle reste à envoyer)
            CsvMirror.ajouterNote(matiereChoisie, note, quotient, insertionOk);

            if (!insertionOk) {
                JOptionPane.showMessageDialog(addNoteFrame, "Connexion BDD indisponible. La note a été sauvegardée dans le fichier local (CSV).\nElle sera envoyée à la BDD dès la reconnexion.", "Mode Hors-Ligne", JOptionPane.INFORMATION_MESSAGE);
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

    /**
     * Charge les données à afficher depuis le CSV local.
     * Juste avant, si la BDD est joignable, BDD et CSV sont comparés puis alignés
     * (le CSV reflète donc la BDD en ligne, et reste le témoin hors-ligne).
     */
    static void chargerDonneesInitiales() {
        Database.synchroniser();

        List<String> noms = CsvMirror.listerMatieres();
        List<List<String>> notesParMatiere = new ArrayList<>();
        int maxNotes = 0;

        for (String nom : noms) {
            List<String> notesMatiere = CsvMirror.listerNotes(nom);
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

        // Évite de figer l'appli plusieurs secondes à chaque test si le Raspberry Pi est injoignable
        DriverManager.setLoginTimeout(3);

        boolean isConnected = Database.connecter();
        if (isConnected) {
            Database.creerTables();
            Database.seedMatieresSiVide();
        }

        // Compare BDD/CSV (si en ligne) puis charge l'affichage
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