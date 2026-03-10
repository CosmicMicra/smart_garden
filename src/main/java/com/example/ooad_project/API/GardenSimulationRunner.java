package com.example.ooad_project.API;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Standalone runner for GardenSimulationAPI (no GUI).
 * Simulates 24 days: each "hour" = 1 day, randomly invokes rain(), temperature(), or parasite().
 * After 24 days, calls getState() to assess performance.
 */
public class GardenSimulationRunner {

    public static void main(String[] args) {
        GardenSimulationAPI api = new GardenSimulationAPI();

        api.initializeGarden();
        System.out.println("Garden initialized. Plants: " + api.getPlants());

        Random rand = new Random();
        List<String> parasites = List.of("Rat", "Crow", "Locust", "Aphids", "Slugs");

        for (int hour = 1; hour <= 24; hour++) {
            int choice = rand.nextInt(3);
            switch (choice) {
                case 0 -> {
                    int amount = 5 + rand.nextInt(70);
                    api.rain(amount);
                    System.out.println("Day " + hour + ": Rain " + amount);
                }
                case 1 -> {
                    int temp = 40 + rand.nextInt(81);
                    api.temperature(temp);
                    System.out.println("Day " + hour + ": Temperature " + temp + " F");
                }
                case 2 -> {
                    String parasite = parasites.get(rand.nextInt(parasites.size()));
                    api.parasite(parasite);
                    System.out.println("Day " + hour + ": Parasite " + parasite);
                }
            }

            try {
                Thread.sleep(3600_000); // 1 hour = 3600 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("\n--- Final State ---");
        api.getState();
        Map<String, Object> plants = api.getPlants();
        System.out.println("Alive plants: " + ((List<?>) plants.get("plants")).size());
    }
}
