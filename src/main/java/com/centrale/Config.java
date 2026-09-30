package com.centrale;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.formdev.flatlaf.FlatClientProperties;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Réglages de l'utilisateur (lien iCal, Gmail, base de données facultative) et dossier de ses données.
 *
 * Chaque utilisateur a ses propres réglages et fichiers, dans son dossier personnel :
 *   Linux   : ~/.config/centrale-etudiant/
 *   Windows : %APPDATA%\CentraleEtudiant\
 *   macOS   : ~/Library/Application Support/CentraleEtudiant/
 * On y trouve config.properties (réglages) et les CSV (notes, devoirs).
 *
 * Pour le développement, un fichier .env dans le dossier courant est encore lu en secours.
 */
final class Config {

    private Config() {}

    static final String ICAL          = "ICARL_URL";
    static final String GMAIL_ADRESSE = "GMAIL_ADRESSE";
    static final String GMAIL_MDP     = "GMAIL_MOT_DE_PASSE_APP";
    static final String DB_URL        = "DB_URL";
    static final String DB_USER       = "DB_USERNAME";
    static final String DB_PASSWORD   = "DB_PASSWORD";

    static final String LIEN_MOTS_DE_PASSE_APP = "https://myaccount.google.com/apppasswords";

    private static final Path DOSSIER = dossierUtilisateur();
    private static final Path FICHIER = DOSSIER.resolve("config.properties");
    private static final Properties props = new Properties();
    private static final Dotenv env = Dotenv.configure().ignoreIfMissing().ignoreIfMalformed().load();

    static {
        if (Files.exists(FICHIER)) {
            try (InputStream in = Files.newInputStream(FICHIER)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Lecture de " + FICHIER + " impossible : " + e.getMessage());
            }
        }
    }

    // =====================================================================
    // DOSSIER DES DONNÉES
    // =====================================================================
    static Path dossierUtilisateur() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            Path base = (appdata != null && !appdata.isBlank()) ? Paths.get(appdata) : Paths.get(home, "AppData", "Roaming");
            return base.resolve("CentraleEtudiant");
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "CentraleEtudiant");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = (xdg != null && !xdg.isBlank()) ? Paths.get(xdg) : Paths.get(home, ".config");
        return base.resolve("centrale-etudiant");
    }

    /**
     * Chemin d'un fichier de données (ex : notes_backup.csv) dans le dossier de l'utilisateur.
     * S'il n'y est pas encore mais existe dans le dossier courant (ancienne version), il y est copié.
     */
    static Path fichier(String nom) {
        Path cible = DOSSIER.resolve(nom);
        try {
            Files.createDirectories(DOSSIER);
            Path ancien = Paths.get(nom).toAbsolutePath();
            if (!Files.exists(cible) && Files.exists(ancien) && !ancien.equals(cible.toAbsolutePath())) {
                Files.copy(ancien, cible, StandardCopyOption.COPY_ATTRIBUTES);
                System.out.println(nom + " copié dans " + DOSSIER);
            }
        } catch (IOException e) {
            System.err.println("Dossier " + DOSSIER + " inaccessible : " + e.getMessage());
        }
        return cible;
    }

    // =====================================================================
    // LECTURE / ÉCRITURE DES RÉGLAGES
    // =====================================================================

    /** Valeur d'un réglage (config.properties, sinon .env / variable d'environnement), null si vide. */
    static String get(String cle) {
        String v = props.getProperty(cle);
        if (v == null || v.isBlank()) v = env.get(cle);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    static boolean bddConfiguree() {
        return get(DB_URL) != null;
    }

    /** Premier lancement : aucun fichier de réglages et ni iCal ni Gmail connus. */
    static boolean premierLancement() {
        return !Files.exists(FICHIER) && (get(ICAL) == null || get(GMAIL_ADRESSE) == null);
    }

    static synchronized void enregistrer(Map<String, String> valeurs) throws IOException {
        for (Map.Entry<String, String> e : valeurs.entrySet()) {
            String v = (e.getValue() == null) ? "" : e.getValue().trim();
            props.setProperty(e.getKey(), v);
        }
        Files.createDirectories(DOSSIER);
        Path tmp = FICHIER.resolveSibling("config.properties.tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            props.store(out, "Centrale Etudiant - reglages (ne pas partager : contient des mots de passe)");
        }
        // Fichier lisible par l'utilisateur seulement (Linux / macOS)
        try {
            Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows : le dossier %APPDATA% est déjà personnel
        }
        Files.move(tmp, FICHIER, StandardCopyOption.REPLACE_EXISTING);
    }

    /** webcal://... -> https://... (les liens ADE / Google Agenda sont parfois donnés en webcal). */
    static String normaliserIcal(String url) {
        String u = url.trim();
        if (u.toLowerCase(Locale.ROOT).startsWith("webcal://")) u = "https://" + u.substring("webcal://".length());
        return u;
    }

    // =====================================================================
    // FENÊTRE "PARAMÈTRES" (assistant du premier lancement)
    // =====================================================================

    /** Au premier lancement, ouvre les paramètres avant d'afficher l'appli. */
    static void demanderSiPremierLancement() {
        if (!premierLancement()) return;
        if (SwingUtilities.isEventDispatchThread()) {
            ouvrirParametres(null, true);
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> ouvrirParametres(null, true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException e) {
            System.err.println("Paramètres : " + e.getCause());
        }
    }

    /**
     * Ouvre la fenêtre des paramètres (bloquante).
     * @return true si l'utilisateur a enregistré
     */
    static boolean ouvrirParametres(Window parent, boolean bienvenue) {
        JDialog d = new JDialog(parent, "Centrale Étudiant · Paramètres", JDialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        d.getContentPane().setBackground(Theme.BACKGROUND);
        boolean[] enregistre = { false };

        // --- champs ---
        JTextField ical = champ(get(ICAL));
        JTextField gmail = champ(get(GMAIL_ADRESSE));
        JPasswordField mdp = motDePasse(get(GMAIL_MDP));
        JTextField dbUrl = champ(get(DB_URL));
        JTextField dbUser = champ(get(DB_USER));
        JPasswordField dbMdp = motDePasse(get(DB_PASSWORD));
        ical.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "https://... ou webcal://...");
        gmail.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "prenom.nom@gmail.com");
        mdp.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "xxxx xxxx xxxx xxxx");
        dbUrl.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "jdbc:postgresql://adresse:5432/base");

        // --- section 1 : emploi du temps ---
        JLabel resultatIcal = Theme.info(" ");
        JButton testerIcal = Theme.boutonSecondaire("Tester");
        testerIcal.addActionListener(e -> tester(testerIcal, resultatIcal, () -> {
            int n = Calendar.requestHttp(ical.getText()).size();
            return "OK : " + n + " cours trouvés";
        }));
        JPanel carteIcal = section("Emploi du temps",
                "Le lien iCal (.ics) de ton emploi du temps. Sur ADE : icône « Export agenda » "
                + "→ « Générer URL », puis copie le lien.",
                List.of(ligne("Lien iCal", ical)), testerIcal, resultatIcal, null);

        // --- section 2 : Gmail ---
        JLabel resultatGmail = Theme.info(" ");
        JButton testerGmail = Theme.boutonSecondaire("Tester");
        testerGmail.addActionListener(e -> tester(testerGmail, resultatGmail, () -> {
            Mails.testerConnexion(gmail.getText().trim(), new String(mdp.getPassword()));
            return "OK : connexion à Gmail réussie";
        }));
        JButton lienGoogle = Theme.boutonSecondaire("Créer un mot de passe d'application");
        lienGoogle.addActionListener(e -> ouvrirLien(d, LIEN_MOTS_DE_PASSE_APP));
        JPanel carteGmail = section("Mails (Gmail)",
                "Ton adresse Gmail et un « mot de passe d'application » Google (16 lettres), "
                + "PAS ton mot de passe habituel. Il faut que la validation en deux étapes soit activée sur ton compte Google. "
                + "Seuls les mails venant d'une adresse umontpellier.fr sont affichés, et rien n'est marqué comme lu.",
                List.of(ligne("Adresse Gmail", gmail), ligne("Mot de passe d'application", mdp)),
                testerGmail, resultatGmail, lienGoogle);

        // --- section 3 : base de données (facultatif) ---
        JLabel resultatBdd = Theme.info(" ");
        JButton testerBdd = Theme.boutonSecondaire("Tester");
        testerBdd.addActionListener(e -> tester(testerBdd, resultatBdd, () -> {
            DriverManager.setLoginTimeout(5);
            try (Connection c = DriverManager.getConnection(dbUrl.getText().trim(), dbUser.getText().trim(),
                    new String(dbMdp.getPassword()))) {
                return "OK : base de données joignable";
            }
        }));
        JPanel carteBdd = section("Base de données (facultatif)",
                "Pour synchroniser tes notes et devoirs sur un serveur PostgreSQL à toi. "
                + "Laisse vide : tout reste enregistré sur cet ordinateur, et l'appli marche pareil.",
                List.of(ligne("URL JDBC", dbUrl), ligne("Utilisateur", dbUser), ligne("Mot de passe", dbMdp)),
                testerBdd, resultatBdd, null);

        // --- mise en page ---
        JPanel formulaire = new SuitLargeur(new GridBagLayout());
        formulaire.setBackground(Theme.BACKGROUND);
        formulaire.setBorder(new EmptyBorder(0, 24, 8, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 14, 0);
        gbc.gridy = 0;
        formulaire.add(carteIcal, gbc);
        gbc.gridy = 1;
        formulaire.add(carteGmail, gbc);
        gbc.gridy = 2;
        formulaire.add(carteBdd, gbc);
        gbc.gridy = 3;
        gbc.weighty = 1;
        JPanel vide = new JPanel();
        vide.setOpaque(false);
        formulaire.add(vide, gbc);

        JScrollPane scroll = new JScrollPane(formulaire, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton annuler = Theme.boutonSecondaire(bienvenue ? "Plus tard" : "Annuler");
        annuler.addActionListener(e -> d.dispose());
        JButton valider = Theme.bouton("Enregistrer");
        valider.addActionListener(e -> {
            String lien = ical.getText().trim();
            if (!lien.isEmpty() && !normaliserIcal(lien).toLowerCase(Locale.ROOT).startsWith("http")) {
                JOptionPane.showMessageDialog(d, "Le lien iCal doit commencer par https:// (ou webcal://).",
                        "Lien invalide", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String adresse = gmail.getText().trim();
            if (!adresse.isEmpty() && !adresse.contains("@")) {
                JOptionPane.showMessageDialog(d, "L'adresse Gmail n'est pas valide.", "Adresse invalide", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Map<String, String> valeurs = new LinkedHashMap<>();
            valeurs.put(ICAL, lien.isEmpty() ? "" : normaliserIcal(lien));
            valeurs.put(GMAIL_ADRESSE, adresse);
            valeurs.put(GMAIL_MDP, new String(mdp.getPassword()).replace(" ", ""));
            valeurs.put(DB_URL, dbUrl.getText());
            valeurs.put(DB_USER, dbUser.getText());
            valeurs.put(DB_PASSWORD, new String(dbMdp.getPassword()));
            try {
                enregistrer(valeurs);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(d, "Impossible d'enregistrer les paramètres :\n" + ex.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }
            enregistre[0] = true;
            d.dispose();
        });

        JPanel bas = new JPanel(new BorderLayout());
        bas.setBackground(Theme.BACKGROUND);
        bas.setBorder(new EmptyBorder(8, 24, 18, 24));
        bas.add(Theme.info("Enregistré dans " + FICHIER), BorderLayout.CENTER);
        JPanel boutons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        boutons.setOpaque(false);
        boutons.add(annuler);
        boutons.add(valider);
        bas.add(boutons, BorderLayout.EAST);

        String titre = bienvenue ? "Bienvenue !" : "Paramètres";
        String info = bienvenue ? "Quelques infos pour configurer l'appli. Elles restent sur cet ordinateur."
                                : "Ces informations restent sur cet ordinateur.";
        d.add(Theme.entete(titre, Theme.info(info)), BorderLayout.NORTH);
        d.add(scroll, BorderLayout.CENTER);
        d.add(bas, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(valider);

        d.setSize(760, 820);
        d.setMinimumSize(new Dimension(620, 500));
        d.setLocationRelativeTo(parent);
        d.setVisible(true); // bloque jusqu'à la fermeture
        return enregistre[0];
    }

    /** Panneau qui prend la largeur de la fenêtre (pas de défilement horizontal, le texte passe à la ligne). */
    private static final class SuitLargeur extends JPanel implements Scrollable {
        SuitLargeur(java.awt.LayoutManager layout) {
            super(layout);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height - 32; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static JTextField champ(String valeur) {
        JTextField f = new JTextField(valeur == null ? "" : valeur, 10);
        Theme.styleChamp(f);
        return f;
    }

    private static JPasswordField motDePasse(String valeur) {
        JPasswordField f = new JPasswordField(valeur == null ? "" : valeur, 10);
        Theme.styleChamp(f);
        f.putClientProperty(FlatClientProperties.STYLE, "margin: 5,8,5,8; showRevealButton: true");
        return f;
    }

    private static JPanel ligne(String libelle, JComponent champ) {
        JLabel l = new JLabel(libelle);
        l.setFont(Theme.FONT_TABLE);
        l.setForeground(Theme.TEXT_PRIMARY);
        l.setPreferredSize(new Dimension(190, l.getPreferredSize().height));
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.add(l, BorderLayout.WEST);
        p.add(champ, BorderLayout.CENTER);
        return p;
    }

    /** Carte d'une section : titre, explication, champs, puis bouton "Tester" et son résultat. */
    private static JPanel section(String titre, String explication, List<JPanel> champs,
                                  JButton tester, JLabel resultat, JButton extra) {
        Theme.PanneauArrondi carte = new Theme.PanneauArrondi(new GridBagLayout(), 16);
        carte.setBorder(new EmptyBorder(16, 18, 16, 18));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel t = new JLabel(titre);
        t.setFont(Theme.FONT_SECTION);
        t.setForeground(Theme.TEXT_PRIMARY);
        gbc.insets = new Insets(0, 0, 6, 0);
        carte.add(t, gbc);

        JTextArea e = new JTextArea(explication);
        e.setFont(Theme.FONT_TABLE);
        e.setForeground(Theme.TEXT_DISCRET);
        e.setOpaque(false);
        e.setEditable(false);
        e.setFocusable(false);
        e.setLineWrap(true);
        e.setWrapStyleWord(true);
        e.setBorder(null);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 12, 0);
        carte.add(e, gbc);

        for (JPanel c : champs) {
            gbc.gridy++;
            gbc.insets = new Insets(0, 0, 8, 0);
            carte.add(c, gbc);
        }

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(tester);
        if (extra != null) actions.add(extra);
        actions.add(resultat);
        gbc.gridy++;
        gbc.insets = new Insets(4, -8, 0, 0);
        carte.add(actions, gbc);
        return carte;
    }

    /** Lance un test en arrière-plan et affiche le résultat (vert si OK, rouge sinon). */
    private static void tester(JButton bouton, JLabel resultat, Callable<String> test) {
        bouton.setEnabled(false);
        resultat.setForeground(Theme.TEXT_DISCRET);
        resultat.setText("Test en cours...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return test.call();
            }

            @Override
            protected void done() {
                bouton.setEnabled(true);
                Color couleur;
                String texte;
                try {
                    texte = get();
                    couleur = Theme.SUCCES;
                } catch (Exception e) {
                    Throwable t = e;
                    while (t.getCause() != null) t = t.getCause();
                    texte = "Échec : " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                    couleur = Theme.RETARD;
                }
                resultat.setForeground(couleur);
                resultat.setText(texte.length() > 70 ? texte.substring(0, 70) + "..." : texte);
                resultat.setToolTipText(texte);
            }
        }.execute();
    }

    static void ouvrirLien(Window parent, String lien) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(lien));
                return;
            }
        } catch (Exception ignored) {
            // on affiche le lien à la place
        }
        JOptionPane.showMessageDialog(parent, "Ouvre ce lien dans ton navigateur :\n" + lien,
                "Lien", JOptionPane.INFORMATION_MESSAGE);
    }
}
