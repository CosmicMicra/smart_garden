package com.example.ooad_project.SubSystems;

import com.example.ooad_project.Events.DayUpdateEvent;
import com.example.ooad_project.GardenGrid;
import com.example.ooad_project.GardenLog;
import com.example.ooad_project.GardenStateManager;
import com.example.ooad_project.Plant.Plant;
import com.example.ooad_project.ThreadUtils.EventBus;
import com.example.ooad_project.Events.RainEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.ooad_project.Events.SprinklerEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class WateringSystem implements Runnable {
    private final AtomicBoolean rainRequested = new AtomicBoolean(false);
    private static final Logger logger = LogManager.getLogger("WateringSystemLogger");
    private int rainAmount = 0;
    private final GardenGrid gardenGrid;
    private int currentDay;

    /** Water when plant's current water is below this fraction of its requirement (e.g. 0.5 = 50%). */
    private static final double LOW_WATER_THRESHOLD_RATIO = 0.5;
    /** Check for low-water plants every this many seconds. */
    private static final int AUTO_WATER_CHECK_INTERVAL_SEC = 15;

    private int tickCount = 0;

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(1000); // Every second
                tickConsumeWater();
                tickCount++;
                if (tickCount >= AUTO_WATER_CHECK_INTERVAL_SEC) {
                    tickCount = 0;
                    checkAndWaterLowPlants();
                }
            } catch (InterruptedException e) {
                logger.error("Watering System interrupted");
                return; // Exit if interrupted
            }
        }
    }

    /** Each plant consumes waterRequirement/300 units per second. */
    private void tickConsumeWater() {
        for (int i = 0; i < gardenGrid.getNumRows(); i++) {
            for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                Plant plant = gardenGrid.getPlant(i, j);
                if (plant != null) {
                    plant.tickConsumeWater();
                }
            }
        }
    }

    private static final int REPAIR_COST = 5;

    /** Auto-water plants below threshold unless manual sprinklers are off. Auto-repairs failure for $5. */
    private void checkAndWaterLowPlants() {
        GardenStateManager state = GardenStateManager.getInstance();
        if (state.getManualSprinklerOff()) {
            return;
        }
        if (state.getSprinklerFailureToday()) {
            FarmerShop shop = FarmerShop.getInstance();
            shop.deductMoney(REPAIR_COST);
            state.setSprinklerFailureToday(false);
            logger.info("Day: {} Sprinkler auto-repaired for ${}", currentDay, REPAIR_COST);
            GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Day %d: Sprinkler auto-repaired — $%d deducted (balance: $%d)", currentDay, REPAIR_COST, shop.getTotalMoney());
            EventBus.publish("SprinklerRepairEvent", REPAIR_COST);
        }
        int watered = 0;
        for (int i = 0; i < gardenGrid.getNumRows(); i++) {
            for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                Plant plant = gardenGrid.getPlant(i, j);
                if (plant != null) {
                    int threshold = (int) (plant.getWaterRequirement() * LOW_WATER_THRESHOLD_RATIO);
                    if (plant.getCurrentWater() < threshold) {
                        int waterNeeded = plant.getWaterRequirement() - plant.getCurrentWater();
                        if (waterNeeded > 0) {
                            EventBus.publish("SprinklerEvent", new SprinklerEvent(plant.getRow(), plant.getCol(), waterNeeded));
                            plant.addWater(waterNeeded);
                            plant.setIsWatered(true); // Sprinkler watered this plant
                            logger.info("Day: {} Auto-watered {} at ({}, {}) - was below threshold", currentDay, plant.getName(), i, j);
                            watered++;
                        }
                    }
                }
            }
        }
        if (watered > 0) {
            GardenLog.log(GardenLog.Category.SPRINKLER, "Day %d: Auto-watered %d plant(s) below threshold", currentDay, watered);
        }
    }

    public WateringSystem() {
        logger.info("Watering System Initialized");
//        So our watering system is subscribed to the RainEvent
//        When a rain event is published, the watering system will handle it
        EventBus.subscribe("RainEvent", event -> handleRain((RainEvent) event));
        EventBus.subscribe("SprinklerActivationEvent", event -> sprinkle());
        EventBus.subscribe("ManualSprinklerActivationEvent", event -> runManualSprinklers());
        EventBus.subscribe("DayUpdateEvent", event -> handleDayChangeEvent((DayUpdateEvent) event));
        //        Get the garden grid instance
//        This is the grid that holds all the plants
        this.gardenGrid = GardenGrid.getInstance();
    }

    private void handleDayChangeEvent(DayUpdateEvent event) {
        this.currentDay = event.getDay(); // Update currentDay
    }

    /** Rain adds water to all plants but does NOT set isWatered (that's sprinkler-only). */
    private void handleRain(RainEvent event) {
        GardenStateManager.getInstance().setRainedToday(true);
        GardenLog.log(GardenLog.Category.RAIN, "Day %d: Rain event - %d units.", currentDay, event.getAmount());

        for (int i = 0; i < gardenGrid.getNumRows(); i++) {
            for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                Plant plant = gardenGrid.getPlant(i, j);
                if (plant != null) {
                    plant.addWater(event.getAmount());
                    logger.info("Day: {} Rain gave {} water to {} at ({},{})", currentDay, event.getAmount(), plant.getName(), i, j);
                }
            }
        }
    }



    /** Manual sprinkler activation - user turned on sprinklers. Bypasses rainedToday/manualOff/failure. */
    private void runManualSprinklers() {
        GardenLog.log(GardenLog.Category.MANUAL_OVERRIDE, "Manual sprinklers activated by user");
        logger.info("Manual sprinklers activated");
        doSprinkle();
    }

    /** Automatic sprinkler activation at end of day. Skips if rained, manually off, or equipment failure. */
    private void sprinkle() {
        GardenStateManager state = GardenStateManager.getInstance();
        if (state.getManualSprinklerOff()) {
            GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Day %d: Sprinklers SKIPPED - manually disabled by user", currentDay);
            logger.info("Day: " + currentDay + " Sprinklers SKIPPED - manually disabled");
            return;
        }
        if (state.getRainedToday()) {
            GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Day %d: Sprinklers SKIPPED - it rained today (no need to water)", currentDay);
            logger.info("Day: " + currentDay + " Sprinklers SKIPPED - rained today");
            return;
        }
        if (state.getSprinklerFailureToday()) {
            GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Day %d: Sprinklers SKIPPED - random equipment failure", currentDay);
            logger.info("Day: " + currentDay + " Sprinklers SKIPPED - equipment failure");
            return;
        }

        doSprinkle();
    }

    /** Sprinkle all plants that need water. Sets isWatered on each. */
    private void doSprinkle() {
        GardenLog.log(GardenLog.Category.SPRINKLER, "Day %d: Sprinklers activated", currentDay);
        logger.info("Day: {} Sprinklers activated!", currentDay);
        int counter = 0;

        for (int i = 0; i < gardenGrid.getNumRows(); i++) {
            for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                Plant plant = gardenGrid.getPlant(i, j);
                if (plant != null) {
                    int waterNeeded = plant.getWaterRequirement() - plant.getCurrentWater();
                    if (waterNeeded > 0) {
                        EventBus.publish("SprinklerEvent", new SprinklerEvent(plant.getRow(), plant.getCol(), waterNeeded));
                        plant.addWater(waterNeeded);
                        plant.setIsWatered(true); // Sprinkler watered this plant
                        logger.info("Day: {} Sprinkled {} at ({},{}) with {} water", currentDay, plant.getName(), i, j, waterNeeded);
                        counter++;
                    } else {
                        plant.setIsWatered(true); // Already full, still counts as sprinkler-watered
                    }
                }
            }
        }
        logger.info("Day: {} Sprinkled {} plants total", currentDay, counter);
    }
}


