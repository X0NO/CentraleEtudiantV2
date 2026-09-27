package com.centrale;

import javax.swing.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import io.github.cdimascio.dotenv.Dotenv;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;

public class Calendar {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String urlICARL = dotenv.get("ICARL_URL");

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

        return new JTable(data, columns);
    }

    public static void main(String[] args) {
        List<Evenement> evenements = requestHttp();

        JFrame frame = new JFrame("CentraleEtudiant");
        frame.setSize(1000, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTable calendar = calendarWeek(evenements);
        frame.add(new JScrollPane(calendar));
        frame.setVisible(true);
    }
}