package com.centrale;

import java.time.LocalDateTime;

public class Evenement {
    private String titre;
    private LocalDateTime debut;
    private LocalDateTime fin;
    private String salle;

    public Evenement(String titre, LocalDateTime debut, LocalDateTime fin, String salle) {
        this.titre = titre;
        this.debut = debut;
        this.fin = fin;
        this.salle = salle;
    }

    public String getTitre() { return titre; }
    public LocalDateTime getDebut() { return debut; }
    public LocalDateTime getFin() { return fin; }
    public String getSalle() { return salle; }
}