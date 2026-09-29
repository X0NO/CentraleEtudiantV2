package com.centrale;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.Component;
import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.HashMap;
import java.time.LocalDate;

import io.github.cdimascio.dotenv.Dotenv;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;

public class Calendar {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String urlICARL = dotenv.get("ICARL_URL");

    static LocalDate jourActuelle(){
        LocalDate dateActuelle = LocalDate.now();
        return dateActuelle;
    }

    static List<Evenement> requestHttp() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlICARL))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            List<VEvent> vevents = parseIcal(response.body());
            return convertirEvents(vevents);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("HTTP request failed", e);
        }
    }

    static List<VEvent> parseIcal(String icalContent) {
        ICalendar ical = Biweekly.parse(icalContent).first();
        return ical.getEvents();
    }

    static List<Evenement> convertirEvents(List<VEvent> vevents) {
        List<Evenement> evenements = new ArrayList<>();
        for (VEvent v : vevents) {
            evenements.add(new Evenement(
                    v.getSummary().getValue(),
                    v.getDateStart().getValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    v.getDateEnd().getValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    v.getLocation() != null ? v.getLocation().getValue() : ""
            ));
        }
        return evenements;
    }

    static JTable calendarWeek(List<Evenement> evenements) {
        String[] columns = {"Titre", "Début", "Fin", "Salle"};
        Object[][] data = new Object[evenements.size()][4];

        for (int i = 0; i < evenements.size(); i++) {
            Evenement e = evenements.get(i);
            data[i][0] = e.getTitre();
            data[i][1] = e.getDebut();
            data[i][2] = e.getFin();
            data[i][3] = e.getSalle();
        }
        JTable table = new JTable(data,columns);
        table.setDefaultRenderer(Object.class, new CouleurMatiereRenderer());
        return table;
    }

    private static final Map<String, Color> couleurs = new HashMap<>();
    static void attribuerCouleurs(List<Evenement> evenements) {
    List<String> titres = evenements.stream()
            .map(Evenement::getTitre)
            .distinct()
            .sorted()
            .toList();

    for (int i = 0; i < titres.size(); i++) {
        float teinte = (float) i / titres.size();
        couleurs.put(titres.get(i), Color.getHSBColor(teinte, 0.4f, 1.0f));
    }
}

    static class CouleurMatiereRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {

        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (!isSelected) {
            String titre = (String) table.getValueAt(row, 0); // titre = colonne 0
            c.setBackground(couleurs.getOrDefault(titre, Color.WHITE));
            c.setForeground(Color.BLACK);
        }
        return c;
    }
}

    public static void main(String[] args) {
        
        List<Evenement> evenements = requestHttp();
        evenements.sort(Comparator.comparing(Evenement::getDebut));

        LocalDate DateActuelle = jourActuelle();

        List<Evenement> evenementsFiltres = evenements.stream()
            .filter(e -> e.getDebut().isAfter(DateActuelle.atStartOfDay()))
            .collect(Collectors.toList());
          
       
        JFrame frame = new JFrame("CentraleEtudiant");
        frame.setSize(1000, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        IO.println(DateActuelle);
        attribuerCouleurs(evenementsFiltres);
        JTable calendar = calendarWeek(evenementsFiltres);
        calendar.setDefaultRenderer(Object.class, new CouleurMatiereRenderer());
        frame.add(new JScrollPane(calendar));
        frame.setVisible(true);
    }
}