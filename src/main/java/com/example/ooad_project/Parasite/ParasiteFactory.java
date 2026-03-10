package com.example.ooad_project.Parasite;

import com.example.ooad_project.Parasite.Children.*;

import java.util.ArrayList;

public class ParasiteFactory {

    public static Parasite createParasite(String name, int damage, String imageName, ArrayList<String> affectedPlants, int roundsToKill) {
        switch (name.toLowerCase()) {
            case "rat":
                return new Rat(name, damage, imageName, affectedPlants, roundsToKill);
            case "crow":
                return new Crow(name, damage, imageName, affectedPlants, roundsToKill);
            case "locust":
                return new Locust(name, damage, imageName, affectedPlants, roundsToKill);
            case "aphids":
                return new Aphids(name, damage, imageName, affectedPlants, roundsToKill);
            case "slugs":
                return new Slugs(name, damage, imageName, affectedPlants, roundsToKill);
            default:
                throw new IllegalArgumentException("Unknown parasite type: " + name);
        }
    }

    /** Backward-compatible overload (defaults roundsToKill=2) */
    public static Parasite createParasite(String name, int damage, String imageName, ArrayList<String> affectedPlants) {
        return createParasite(name, damage, imageName, affectedPlants, 2);
    }
}
