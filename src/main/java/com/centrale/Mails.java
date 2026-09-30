package com.centrale;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;


import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.SearchTerm;

public class Mails {

    // =====================================================================
    // CONNEXION GMAIL (IMAP)
    //
    // Adresse Gmail et "mot de passe d'application" Google : demandés dans les
    // Paramètres (voir Config.java), PAS le mot de passe du compte Gmail.
    // =====================================================================

    private static final String SERVEUR_IMAP = "imap.gmail.com";
    private static final String DOSSIER = "INBOX";

    /** Expéditeurs gardés : @umontpellier.fr et ses sous-domaines (@etu.umontpellier.fr, ...). */
    private static final String DOMAINE = "umontpellier.fr";

    /** Nombre de mails récupérés au maximum (les plus récents). */
    private static final int MAX_MAILS = 50;

    static JButton themedButton(String text) {
        return Theme.bouton(text);
    }

    static void styleTable(JTable table) {
        Theme.styleTable(table);
    }

    // =====================================================================
    // MODÈLE
    // =====================================================================
    static final class Mail {
        final String expediteur;   // nom affiché (ou adresse s'il n'y a pas de nom)
        final String adresse;
        final String objet;
        final LocalDateTime date;
        final boolean lu;
        final String corps;

        Mail(String expediteur, String adresse, String objet, LocalDateTime date, boolean lu, String corps) {
            this.expediteur = expediteur;
            this.adresse = adresse;
            this.objet = objet;
            this.date = date;
            this.lu = lu;
            this.corps = corps;
        }
    }

    // =====================================================================
    // RÉCUPÉRATION DES MAILS (IMAP)
    // =====================================================================

    /**
     * Récupère les mails de la boîte de réception envoyés depuis une adresse umontpellier.fr,
     * du plus récent au plus ancien. Le dossier est ouvert en lecture seule :
     * les mails ne sont PAS marqués comme lus dans Gmail.
     */
    static List<Mail> recupererMails() throws MessagingException {
        return recupererMails(false, true);
    }

    /**
     * @param nonLusSeulement true = seulement les mails non lus (recherche faite par Gmail)
     * @param avecCorps       false = en-têtes seulement (beaucoup plus rapide, utilisé par le tableau de bord)
     */
    static List<Mail> recupererMails(boolean nonLusSeulement, boolean avecCorps) throws MessagingException {
        String adresse = Config.get(Config.GMAIL_ADRESSE);
        String motDePasse = Config.get(Config.GMAIL_MDP);
        if (adresse == null || motDePasse == null) {
            throw new MessagingException("Adresse Gmail et mot de passe d'application non renseignés : ouvre les Paramètres.");
        }

        List<Mail> mails = new ArrayList<>();

        try (Store store = connecter(adresse, motDePasse)) {

            try (Folder inbox = store.getFolder(DOSSIER)) {
                inbox.open(Folder.READ_ONLY);

                // Recherche faite par Gmail (rapide), puis vérification exacte du domaine ici
                SearchTerm critere = new FromStringTerm(DOMAINE);
                if (nonLusSeulement) critere = new AndTerm(critere, new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                Message[] trouves = inbox.search(critere);

                // Les numéros de message suivent l'ordre d'arrivée : on garde les plus récents
                int debut = Math.max(0, trouves.length - MAX_MAILS);
                Message[] recents = new Message[trouves.length - debut];
                System.arraycopy(trouves, debut, recents, 0, recents.length);

                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                fp.add(FetchProfile.Item.FLAGS);
                inbox.fetch(recents, fp);

                for (Message m : recents) {
                    InternetAddress from = premiereAdresse(m.getFrom());
                    if (from == null || !estUniversite(from.getAddress())) continue;

                    String nom = (from.getPersonal() != null && !from.getPersonal().isBlank())
                            ? from.getPersonal() : from.getAddress();
                    String objet = (m.getSubject() == null || m.getSubject().isBlank()) ? "(sans objet)" : m.getSubject();
                    java.util.Date d = (m.getReceivedDate() != null) ? m.getReceivedDate() : m.getSentDate();
                    LocalDateTime date = (d == null) ? null
                            : d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    boolean lu = m.isSet(Flags.Flag.SEEN);

                    String corps = "";
                    if (avecCorps) {
                        try {
                            corps = extraireTexte(m);
                        } catch (IOException | MessagingException e) {
                            corps = "(contenu illisible : " + e.getMessage() + ")";
                        }
                    }
                    mails.add(new Mail(nom, from.getAddress(), objet, date, lu, corps == null ? "" : corps.trim()));
                }
            }
        }

        mails.sort(Comparator.comparing((Mail m) -> m.date, Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        return mails;
    }

    /** Connexion IMAP à Gmail (à fermer après usage). */
    static Store connecter(String adresse, String motDePasse) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", SERVEUR_IMAP);
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "20000");

        Store store = Session.getInstance(props).getStore("imaps");
        // mot de passe d'application : Google l'affiche avec des espaces, on les enlève
        store.connect(SERVEUR_IMAP, adresse, motDePasse.replace(" ", ""));
        return store;
    }

    /** Vérifie l'adresse et le mot de passe d'application (bouton "Tester" des Paramètres). */
    static void testerConnexion(String adresse, String motDePasse) throws MessagingException {
        if (adresse.isBlank() || motDePasse.isBlank()) {
            throw new MessagingException("remplis l'adresse et le mot de passe d'application");
        }
        try (Store store = connecter(adresse, motDePasse)) {
            // connexion réussie
        } catch (jakarta.mail.AuthenticationFailedException e) {
            throw new MessagingException("adresse ou mot de passe d'application refusé par Google", e);
        }
    }

    static InternetAddress premiereAdresse(Address[] adresses) {
        if (adresses == null) return null;
        for (Address a : adresses) {
            if (a instanceof InternetAddress ia) return ia;
        }
        return null;
    }

    /** true pour x@umontpellier.fr et x@sous.domaine.umontpellier.fr. */
    static boolean estUniversite(String adresse) {
        if (adresse == null) return false;
        String a = adresse.toLowerCase(Locale.ROOT);
        return a.endsWith("@" + DOMAINE) || a.endsWith("." + DOMAINE);
    }

    // =====================================================================
    // EXTRACTION DU TEXTE D'UN MAIL
    // =====================================================================

    /** Texte du mail : on préfère la partie texte brut, sinon le HTML converti en texte. */
    static String extraireTexte(Part p) throws MessagingException, IOException {
        if (p.isMimeType("text/plain")) {
            return (String) p.getContent();
        }
        if (p.isMimeType("text/html")) {
            return htmlVersTexte((String) p.getContent());
        }
        if (p.isMimeType("multipart/alternative")) {
            Multipart mp = (Multipart) p.getContent();
            String html = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part partie = mp.getBodyPart(i);
                if (partie.isMimeType("text/plain")) return (String) partie.getContent();
                if (html == null) html = extraireTexte(partie);
            }
            return html;
        }
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            StringBuilder sb = new StringBuilder();
            List<String> piecesJointes = new ArrayList<>();
            for (int i = 0; i < mp.getCount(); i++) {
                Part partie = mp.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(partie.getDisposition()) || partie.getFileName() != null) {
                    piecesJointes.add(partie.getFileName() == null ? "(sans nom)" : partie.getFileName());
                    continue;
                }
                String texte = extraireTexte(partie);
                if (texte != null && !texte.isBlank()) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(texte.trim());
                }
            }
            if (!piecesJointes.isEmpty()) {
                sb.append("\n\n────────────\nPièces jointes : ").append(String.join(", ", piecesJointes));
            }
            return sb.toString();
        }
        return null; // image, pièce jointe seule, etc.
    }

    private static final Pattern ENTITE_NUMERIQUE = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    /** Conversion simple HTML -> texte (retours à la ligne gardés, balises retirées). */
    static String htmlVersTexte(String html) {
        String t = html
                .replaceAll("(?is)<(style|script|head)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|h[1-6]|li)>", "\n")
                .replaceAll("(?i)<li[^>]*>", "• ")
                .replaceAll("(?s)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&eacute;", "é").replace("&egrave;", "è").replace("&ecirc;", "ê")
                .replace("&agrave;", "à").replace("&ccedil;", "ç").replace("&ugrave;", "ù")
                .replace("&ocirc;", "ô").replace("&icirc;", "î").replace("&euro;", "€");

        Matcher m = ENTITE_NUMERIQUE.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int code;
            try {
                code = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                code = '?';
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        m.appendTail(sb);

        return sb.toString()
                .replace("&amp;", "&")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");
    }

    // =====================================================================
    // AFFICHAGE : liste des mails à gauche, contenu du mail à droite
    // =====================================================================
    static List<Mail> mails = new ArrayList<>();
    static JTable tableMails;
    static JLabel statut;
    static JLabel enteteMail;
    static JTextArea corpsMail;
    static JButton btnActualiser;

    static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** "14:32" aujourd'hui, "Hier", sinon "03/09/2026" (comme dans Gmail). */
    static String dateCourte(LocalDateTime d) {
        if (d == null) return "";
        LocalDate aujourdhui = LocalDate.now();
        if (d.toLocalDate().equals(aujourdhui)) return d.format(HEURE);
        if (d.toLocalDate().equals(aujourdhui.minusDays(1))) return "Hier";
        return d.format(JOUR);
    }

    /** Mails non lus en gras et en couleur d'accent, comme dans Gmail. */
    static class RenduMail extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(new EmptyBorder(0, 8, 0, 8));
            int i = table.convertRowIndexToModel(row);
            boolean nonLu = i < mails.size() && !mails.get(i).lu;
            setFont(nonLu ? Theme.FONT_TABLE.deriveFont(Font.BOLD) : Theme.FONT_TABLE);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? Theme.SURFACE : Theme.SURFACE_ALT);
                setForeground(nonLu && column == 0 ? Theme.ACCENT : Theme.TEXT_PRIMARY);
            }
            return this;
        }
    }

    static void afficherMail(Mail m) {
        String date = (m.date == null) ? "" : m.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRENCH));
        enteteMail.setText("<html><b style='font-size:13px'>" + echapper(m.objet) + "</b><br>"
                + "<span style='color:" + Theme.hex(Theme.ACCENT) + "'>" + echapper(m.expediteur) + "</span> &lt;" + echapper(m.adresse) + "&gt;<br>"
                + "<span style='color:" + Theme.hex(Theme.TEXT_DISCRET) + "'>" + date + "</span></html>");
        corpsMail.setText(m.corps.isEmpty() ? "(mail vide)" : m.corps);
        corpsMail.setCaretPosition(0);
    }

    private static String echapper(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Récupère les mails en arrière-plan pour ne pas figer la fenêtre pendant la connexion à Gmail. */
    static void chargerMails() {
        btnActualiser.setEnabled(false);
        statut.setForeground(Theme.TEXT_DISCRET);
        statut.setText("Connexion à Gmail...");

        new SwingWorker<List<Mail>, Void>() {
            @Override
            protected List<Mail> doInBackground() throws Exception {
                return recupererMails();
            }

            @Override
            protected void done() {
                btnActualiser.setEnabled(true);
                try {
                    mails = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                    statut.setForeground(Theme.ERREUR);
                    statut.setText("Impossible de récupérer les mails : " + cause.getMessage());
                    return;
                }

                DefaultTableModel model = (DefaultTableModel) tableMails.getModel();
                model.setRowCount(0);
                for (Mail m : mails) {
                    model.addRow(new Object[]{ m.expediteur, m.objet, dateCourte(m.date) });
                }

                long nonLus = mails.stream().filter(m -> !m.lu).count();
                statut.setForeground(Theme.TEXT_DISCRET);
                statut.setText(mails.size() + " mail" + (mails.size() > 1 ? "s" : "") + " de @" + DOMAINE
                        + "   ·   " + nonLus + " non lu" + (nonLus > 1 ? "s" : "")
                        + "   ·   mis à jour à " + LocalDateTime.now().format(HEURE));

                if (!mails.isEmpty()) {
                    tableMails.setRowSelectionInterval(0, 0);
                } else {
                    enteteMail.setText("");
                    corpsMail.setText("Aucun mail reçu d'une adresse @" + DOMAINE + ".");
                }
            }
        }.execute();
    }

    // Fenêtre ouverte (utilisée par le tableau de bord Main.java pour ne pas l'ouvrir deux fois).
    // Lancé seul, fermer la fenêtre quitte l'appli ; ouvert depuis Main, seule la fenêtre se ferme.
    static volatile JFrame fenetre;
    static int fermeture = JFrame.EXIT_ON_CLOSE;

    public static void main(String[] args) {

        Theme.installer();
        Config.demanderSiPremierLancement();

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Centrale Étudiant · Mails");
            frame.setSize(1100, 800);
            frame.setDefaultCloseOperation(fermeture);
            fenetre = frame;
            frame.getContentPane().setBackground(Theme.BACKGROUND);

            JPanel haut = Theme.entete("Mails de l'université", Theme.info("Expéditeurs @" + DOMAINE + " · boîte de réception Gmail"));

            // --- liste des mails ---
            DefaultTableModel model = new DefaultTableModel(new String[]{"De", "Objet", "Date"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            tableMails = new JTable(model);
            styleTable(tableMails);
            tableMails.setShowVerticalLines(false);
            tableMails.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            tableMails.setDefaultRenderer(Object.class, new RenduMail());
            tableMails.getColumnModel().getColumn(0).setPreferredWidth(150);
            tableMails.getColumnModel().getColumn(1).setPreferredWidth(260);
            tableMails.getColumnModel().getColumn(2).setPreferredWidth(80);
            tableMails.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int row = tableMails.getSelectedRow();
                if (row >= 0) afficherMail(mails.get(tableMails.convertRowIndexToModel(row)));
            });

            JScrollPane scrollListe = new JScrollPane(tableMails);

            // --- lecture d'un mail ---
            enteteMail = new JLabel();
            enteteMail.setFont(Theme.FONT_TABLE);
            enteteMail.setForeground(Theme.TEXT_PRIMARY);
            enteteMail.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    new EmptyBorder(12, 14, 12, 14)));

            corpsMail = new JTextArea();
            corpsMail.setFont(Theme.FONT_LABEL);
            corpsMail.setForeground(Theme.TEXT_PRIMARY);
            corpsMail.setCaretColor(Theme.TEXT_PRIMARY);
            corpsMail.setEditable(false);
            corpsMail.setLineWrap(true);
            corpsMail.setWrapStyleWord(true);
            corpsMail.setBorder(new EmptyBorder(12, 14, 12, 14));

            JScrollPane scrollCorps = new JScrollPane(corpsMail);
            scrollCorps.getVerticalScrollBar().setUnitIncrement(16);

            Theme.PanneauArrondi lecture = Theme.encadrer(scrollCorps);
            lecture.add(enteteMail, BorderLayout.NORTH);

            // Changement de mode : l'en-tête du mail (HTML coloré) est réaffiché avec les nouvelles couleurs
            Theme.auChangement(() -> {
                int row = tableMails.getSelectedRow();
                if (row >= 0) afficherMail(mails.get(tableMails.convertRowIndexToModel(row)));
            });

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Theme.encadrer(scrollListe), lecture);
            split.setResizeWeight(0.45);
            split.setDividerSize(14);
            split.setBorder(BorderFactory.createEmptyBorder());
            split.setBackground(Theme.BACKGROUND);
            split.setContinuousLayout(true);

            JPanel centre = new JPanel(new BorderLayout());
            centre.setBackground(Theme.BACKGROUND);
            centre.setBorder(new EmptyBorder(0, 24, 0, 24));
            centre.add(split, BorderLayout.CENTER);

            // --- barre du bas ---
            statut = new JLabel(" ");
            statut.setFont(Theme.FONT_TABLE);
            statut.setForeground(Theme.TEXT_DISCRET);

            btnActualiser = themedButton("Actualiser");
            btnActualiser.addActionListener(e -> chargerMails());

            JPanel bas = new JPanel(new BorderLayout());
            bas.setBackground(Theme.BACKGROUND);
            bas.setBorder(new EmptyBorder(14, 24, 18, 24));
            bas.add(statut, BorderLayout.CENTER);
            bas.add(btnActualiser, BorderLayout.EAST);

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(Theme.BACKGROUND);
            panel.add(haut, BorderLayout.NORTH);
            panel.add(centre, BorderLayout.CENTER);
            panel.add(bas, BorderLayout.SOUTH);

            frame.getContentPane().add(panel);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            SwingUtilities.invokeLater(() -> split.setDividerLocation(0.45));
            chargerMails();
        });
    }
}
