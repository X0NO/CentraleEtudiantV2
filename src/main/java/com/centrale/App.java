package com.centrale;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class App {

    public static void main(String[] args) {
        // Remplace par l'IP de ton NAS, le port (3307 ou 3306) et le nom de ta base
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
}