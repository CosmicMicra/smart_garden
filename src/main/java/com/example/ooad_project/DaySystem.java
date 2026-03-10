package com.example.ooad_project;

import com.example.ooad_project.API.SmartGardenAPI;
import com.example.ooad_project.Events.DayUpdateEvent;
import com.example.ooad_project.GardenLog;
import com.example.ooad_project.GardenStateManager;
import com.example.ooad_project.Plant.Plant;
import com.example.ooad_project.ThreadUtils.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DaySystem {
    private static DaySystem instance = null;
    private final ScheduledExecutorService scheduler;
    private volatile int currentDay;
    private final Logger logger = LogManager.getLogger("DayLogger");
    private final GardenGrid gardenGrid = GardenGrid.getInstance();
    private final Random random = new Random();

    private DaySystem() {
        logger.info("Day System Initialized");
        scheduler = Executors.newScheduledThreadPool(1);
        currentDay = 0;
        // 1 day = 5 mins
        scheduler.scheduleAtFixedRate(this::endOfDayActions, 300, 300, TimeUnit.SECONDS);
    }

    public static synchronized DaySystem getInstance() {
        if (instance == null) {
            instance = new DaySystem();
        }
        return instance;
    }

    public synchronized int getCurrentDay() {
        return currentDay;
    }

    private synchronized void incrementDay() {
        currentDay++;
    }

    private void endOfDayActions() {
        try {
            // Trigger daily weather (rain/temp/parasite) - runs before sprinklers so rainedToday is set
            triggerDailyWeather();

            // RANDOM: 5% chance of equipment failure this end-of-day
            if (random.nextDouble() < 0.05) {
                GardenStateManager.getInstance().setSprinklerFailureToday(true);
                GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Day %d: Random equipment failure - sprinklers will not run", getCurrentDay());
            }

            GardenLog.log(GardenLog.Category.DAY, "End of Day %d - processing", getCurrentDay());
            logger.info("End of Day: {}", getCurrentDay());

            EventBus.publish("SprinklerActivationEvent", null);

            for (int i = 0; i < gardenGrid.getNumRows(); i++) {
                for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                    Plant plant = gardenGrid.getPlant(i, j);
                    if (plant != null) {
                        int damage = plant.processDehydration();
                        if (damage > 0) {
                            GardenLog.log(GardenLog.Category.PLANT, "Day %d: %s at (%d,%d) dehydration damage: %d (unwatered %d days)",
                                    getCurrentDay(), plant.getName(), i, j, damage, plant.getConsecutiveDaysUnwatered());
                            logger.info("Dehydration: {} at ({},{}) took {} damage after {} days unwatered",
                                    plant.getName(), i, j, damage, plant.getConsecutiveDaysUnwatered());
                        }
                        // Growth handled by per-second water consumption (tickConsumeWater)
                        // Reset isWatered for the new day — sprinklers must water again tomorrow
                        plant.setIsWatered(false);
                    }
                }
            }

            GardenStateManager.getInstance().resetDailyFlags();
            incrementDay();
            EventBus.publish("DayUpdateEvent", new DayUpdateEvent(getCurrentDay()));
            GardenLog.log(GardenLog.Category.DAY, "New day: %d", getCurrentDay());
            logger.info("Changed day to: {}", getCurrentDay());

        } catch (Exception e) {
            logger.error("Error during end of day processing: ", e);
        }
    }

    private void triggerDailyWeather() {
        SmartGardenAPI api = new SmartGardenAPI();
        int weatherTemp;
        if (random.nextBoolean()) {
            // Rain: cooler range 45-65°F
            int amount = random.nextDouble() < 0.05 ? 0 : 5 + random.nextInt(70);
            if (amount == 0) GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Random DROUGHT - no rain this day");
            api.rain(amount);
            weatherTemp = 45 + random.nextInt(21);
        } else {
            // No rain: full range — 5% frost 35-46°F, else 40-120°F
            weatherTemp = random.nextDouble() < 0.05 ? 35 + random.nextInt(12) : 40 + random.nextInt(81);
            if (weatherTemp < 46) GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Random FROST - unexpected cold %d F (heaters will activate)", weatherTemp);
        }
        api.temperature(weatherTemp);
    }
}
