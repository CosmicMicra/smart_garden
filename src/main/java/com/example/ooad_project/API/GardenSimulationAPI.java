package com.example.ooad_project.API;

import com.example.ooad_project.Events.*;
import com.example.ooad_project.GardenGrid;
import com.example.ooad_project.Parasite.Parasite;
import com.example.ooad_project.Parasite.ParasiteManager;
import com.example.ooad_project.Plant.Plant;
import com.example.ooad_project.Plant.PlantManager;
import com.example.ooad_project.GardenLog;
import com.example.ooad_project.SubSystems.PesticideSystem;
import com.example.ooad_project.SubSystems.TemperatureSystem;
import com.example.ooad_project.SubSystems.WateringSystem;
import com.example.ooad_project.ThreadUtils.EventBus;
import com.example.ooad_project.ThreadUtils.ThreadManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Standalone Garden Simulation API for script-driven simulation.
 * No GUI dependency. Uses relative paths for config and logs.
 * Each call to rain(), temperature(), or parasite() represents a new simulated day.
 */
public class GardenSimulationAPI {

    private static final String CONFIG_FILE = "config/garden_config.json";
    private static final String LOG_FILE = "log.txt";

    private final GardenGrid gardenGrid;
    private final PlantManager plantManager;
    private final ParasiteManager parasiteManager;

    private int currentDay = 0;
    private boolean previousDayWasRainy = false;
    private boolean subsystemsStarted = false;

    public GardenSimulationAPI() {
        this.gardenGrid = GardenGrid.getInstance();
        this.plantManager = PlantManager.getInstance();
        this.parasiteManager = ParasiteManager.getInstance();
    }

    /**
     * Initializes the garden with plants from the config file.
     * Must be called first. Ensures at least 10 plants and all varieties.
     * Simulation clock starts (day 0).
     */
    public void initializeGarden() {
        gardenGrid.clearGrid();

        try {
            String content = new String(Files.readAllBytes(Paths.get(CONFIG_FILE)));
            JSONObject config = new JSONObject(content);
            JSONArray plantsConfig = config.getJSONArray("plants");

            List<int[]> emptySlots = new ArrayList<>();
            for (int r = 0; r < gardenGrid.getNumRows(); r++) {
                for (int c = 0; c < gardenGrid.getNumCols(); c++) {
                    emptySlots.add(new int[]{r, c});
                }
            }
            Collections.shuffle(emptySlots);

            int slotIndex = 0;
            Set<String> varietiesPlaced = new HashSet<>();
            int totalPlaced = 0;

            for (int i = 0; i < plantsConfig.length(); i++) {
                JSONObject entry = plantsConfig.getJSONObject(i);
                String name = entry.getString("name");
                int amount = entry.getInt("amount");

                Plant template = plantManager.getPlantByName(name);
                if (template == null) {
                    throw new IllegalArgumentException("Unknown plant type: " + name);
                }

                for (int j = 0; j < amount && slotIndex < emptySlots.size(); j++) {
                    Plant plant = plantManager.getPlantByName(name);
                    int[] slot = emptySlots.get(slotIndex++);
                    plant.setRow(slot[0]);
                    plant.setCol(slot[1]);
                    gardenGrid.addPlant(plant, slot[0], slot[1]);
                    varietiesPlaced.add(name);
                    totalPlaced++;
                }
            }

            if (totalPlaced < 10) {
                throw new IllegalStateException("Garden must have at least 10 plants. Only " + totalPlaced + " placed.");
            }

            Set<String> allVarieties = getAllPlantVarieties();
            Set<String> missing = new HashSet<>(allVarieties);
            missing.removeAll(varietiesPlaced);
            if (!missing.isEmpty()) {
                throw new IllegalStateException("All plant varieties must be present. Missing: " + missing);
            }

            currentDay = 0;
            previousDayWasRainy = false;

            startSubsystemsIfNeeded();
            GardenLog.log(GardenLog.Category.INIT, "Garden initialized with %d plants. All varieties present.", totalPlaced);
            logEvent(0, "INIT", "Garden initialized", countAlivePlants());
            EventBus.publish("InitializeGarden", null);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load config from " + CONFIG_FILE, e);
        }
    }

    private Set<String> getAllPlantVarieties() {
        Set<String> varieties = new HashSet<>();
        plantManager.getFlowers().forEach(f -> varieties.add(f.getName()));
        plantManager.getTrees().forEach(t -> varieties.add(t.getName()));
        plantManager.getVegetables().forEach(v -> varieties.add(v.getName()));
        return varieties;
    }

    private void startSubsystemsIfNeeded() {
        if (!subsystemsStarted) {
            ThreadManager.run(new WateringSystem());
            ThreadManager.run(new TemperatureSystem());
            ThreadManager.run(new PesticideSystem());
            subsystemsStarted = true;
        }
    }

    /**
     * Returns plant info. Dead plants are excluded.
     */
    public Map<String, Object> getPlants() {
        List<Plant> alivePlants = gardenGrid.getPlants();
        List<String> plantNames = new ArrayList<>();
        List<Integer> waterRequirements = new ArrayList<>();
        List<List<String>> parasiteLists = new ArrayList<>();

        for (Plant plant : alivePlants) {
            plantNames.add(plant.getName());
            waterRequirements.add(plant.getWaterRequirement());
            parasiteLists.add(plant.getVulnerableTo());
        }

        return Map.of(
                "plants", plantNames,
                "waterRequirement", waterRequirements,
                "parasites", parasiteLists
        );
    }

    /**
     * Simulates rainfall. Water resets after 1 day if that day isn't rainy.
     * Input range based on getPlants().waterRequirement values.
     */
    public void rain(int amount) {
        advanceDayAndProcessEvent("RAIN", String.valueOf(amount), true, () ->
                EventBus.publish("RainEvent", new RainEvent(amount)));
    }

    /**
     * Sets daily temperature (40-120 F). Resets after day ends.
     */
    public void temperature(int amount) {
        advanceDayAndProcessEvent("TEMPERATURE", String.valueOf(amount), false, () -> {
            int clampedTemp = Math.max(40, Math.min(120, amount));
            EventBus.publish("TemperatureEvent", new TemperatureEvent(clampedTemp));
        });
    }

    /**
     * Triggers parasite infestation. Pest control allowed but cannot heal to full.
     */
    public void parasite(String name) {
        Parasite parasite = parasiteManager.getParasiteByName(name);
        if (parasite == null) {
            logEvent(currentDay, "PARASITE", name + " (not found)", countAlivePlants());
            return;
        }
        advanceDayAndProcessEvent("PARASITE", name, false, () ->
                EventBus.publish("ParasiteEvent", new ParasiteEvent(parasite)));
    }

    /**
     * Logs garden state. Format: DAY, EVENT, EVENT_VALUE, PLANTS_ALIVE
     */
    public void getState() {
        int alive = countAlivePlants();
        StringBuilder state = new StringBuilder();
        state.append(String.format("DAY=%d, EVENT=STATE, EVENT_VALUE=snapshot, PLANTS_ALIVE=%d%n", currentDay, alive));
        state.append(String.format("Current Garden State as of Day %d:%n", currentDay));

        List<Plant> plants = gardenGrid.getPlants();
        if (plants.isEmpty()) {
            state.append("No plants alive.\n");
        } else {
            for (Plant plant : plants) {
                state.append(String.format("  %s at (%d,%d): health=%d/%d, stage=%s, watered=%s%n",
                        plant.getName(), plant.getRow(), plant.getCol(),
                        plant.getCurrentHealth(), plant.getHealthFull(),
                        plant.getGrowthStageDescription(),
                        plant.getIsWatered()));
            }
        }
        logEvent(currentDay, "STATE", "snapshot", alive);
        appendToLog(state.toString());
        GardenLog.logSection("GARDEN STATE");
        GardenLog.log(GardenLog.Category.STATE, "Day %d: %d plants alive", currentDay, alive);
    }

    private void advanceDayAndProcessEvent(String eventType, String eventValue, boolean isRainEvent, Runnable eventAction) {
        currentDay++;
        endOfPreviousDay();
        EventBus.publish("DayUpdateEvent", new DayUpdateEvent(currentDay));
        eventAction.run();
        previousDayWasRainy = isRainEvent;
        logEvent(currentDay, eventType, eventValue, countAlivePlants());
    }

    private void endOfPreviousDay() {
        for (int i = 0; i < gardenGrid.getNumRows(); i++) {
            for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                Plant plant = gardenGrid.getPlant(i, j);
                if (plant != null) {
                    int damage = plant.processDehydration();
                    if (damage > 0) {
                        GardenLog.log(GardenLog.Category.PLANT, "Day %d: %s at (%d,%d) dehydration damage: %d (no rain, sprinklers off)",
                                currentDay, plant.getName(), i, j, damage);
                    }
                    plant.setIsWatered(false); // Reset for new day
                }
            }
        }
    }

    private int countAlivePlants() {
        return gardenGrid.getPlants().size();
    }

    private void logEvent(int day, String event, String eventValue, int plantsAlive) {
        String line = String.format("DAY=%d, EVENT=%s, EVENT_VALUE=%s, PLANTS_ALIVE=%d%n",
                day, event, eventValue, plantsAlive);
        appendToLog(line);
    }

    private void appendToLog(String content) {
        try {
            Path logPath = Paths.get(LOG_FILE);
            Files.write(logPath, content.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write to " + LOG_FILE + ": " + e.getMessage());
        }
    }

    public int getCurrentDay() {
        return currentDay;
    }
}
