package com.example.ooad_project.SubSystems;

import com.example.ooad_project.Events.*;
import com.example.ooad_project.GardenGrid;
import com.example.ooad_project.GardenLog;
import com.example.ooad_project.GardenStateManager;
import com.example.ooad_project.Plant.Plant;
import com.example.ooad_project.ThreadUtils.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

public class TemperatureSystem implements Runnable {
    private static final int HEATER_CAPACITY = 15;
    private static final int COOLER_CAPACITY = 15;
    private static final int HEAT_THRESHOLD = 55;
    private static final int COOL_THRESHOLD = 75;
    private static final double DRIFT_MIN = 0.30;
    private static final double DRIFT_MAX = 0.40;
    private static final int FROST_DAMAGE_PER_PLANT = 5;
    private static final int HEAT_DAMAGE_THRESHOLD = 10;

    private int currentDay;
    private final GardenGrid gardenGrid;
    private final GardenStateManager stateManager;
    private final Random random = new Random();
    private static final Logger logger = LogManager.getLogger("TemperatureSystemLogger");

    public TemperatureSystem() {
        this.gardenGrid = GardenGrid.getInstance();
        this.stateManager = GardenStateManager.getInstance();
        logger.info("Temperature System Initialized");
        EventBus.subscribe("DayUpdateEvent", event -> handleDayChangeEvent((DayUpdateEvent) event));
        EventBus.subscribe("TemperatureEvent", event -> handleTemperatureEvent((TemperatureEvent) event));
        EventBus.subscribe("ManualOverrideEvent", event -> handleManualHeater((ManualOverrideEvent) event));
    }

    private void handleDayChangeEvent(DayUpdateEvent event) {
        this.currentDay = event.getDay();
    }

    private void handleManualHeater(ManualOverrideEvent event) {
        if (event.getType() != ManualOverrideEvent.Type.HEATER) return;
        int heatAmount = event.getValue();
        int newGreenhouse = stateManager.getCurrentGreenhouseTemp() + heatAmount;
        stateManager.setCurrentGreenhouseTemp(newGreenhouse);
        for (int i = 0; i < gardenGrid.getNumRows(); i++) {
            for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                Plant plant = gardenGrid.getPlant(i, j);
                if (plant != null) {
                    EventBus.publish("HeatTemperatureEvent", new HeatTemperatureEvent(i, j, heatAmount));
                    plant.healPlant(1);
                }
            }
        }
        EventBus.publish("GreenhouseTempUpdateEvent", new GreenhouseTempUpdateEvent(stateManager.getCurrentWeatherTemp(), newGreenhouse));
        logger.info("Day {} - Manual heater +{}°F, greenhouse now {}°F", currentDay, heatAmount, newGreenhouse);
    }

    private void handleTemperatureEvent(TemperatureEvent event) {
        int weatherTemp = event.getAmount();
        stateManager.setCurrentWeatherTemp(weatherTemp);
        int greenhouseTemp = stateManager.getCurrentGreenhouseTemp();

        double driftFactor = DRIFT_MIN + random.nextDouble() * (DRIFT_MAX - DRIFT_MIN);
        int drift = (int) Math.round((weatherTemp - greenhouseTemp) * driftFactor);
        greenhouseTemp += drift;
        stateManager.setCurrentGreenhouseTemp(greenhouseTemp);
        logger.info("Day {} - Weather {}°F, greenhouse drifted to {}°F (factor {})", currentDay, weatherTemp, greenhouseTemp, String.format("%.2f", driftFactor));

        if (greenhouseTemp < HEAT_THRESHOLD) {
            int heatAdd = Math.min(HEATER_CAPACITY, HEAT_THRESHOLD - greenhouseTemp);
            greenhouseTemp += heatAdd;
            stateManager.setCurrentGreenhouseTemp(greenhouseTemp);
            logger.info("Day {} - Heater +{}°F, greenhouse now {}°F", currentDay, heatAdd, greenhouseTemp);
        } else if (greenhouseTemp > COOL_THRESHOLD) {
            int coolSub = Math.min(COOLER_CAPACITY, greenhouseTemp - COOL_THRESHOLD);
            greenhouseTemp -= coolSub;
            stateManager.setCurrentGreenhouseTemp(greenhouseTemp);
            logger.info("Day {} - Cooler -{}°F, greenhouse now {}°F", currentDay, coolSub, greenhouseTemp);
        }

        stateManager.setFrostOccurred(greenhouseTemp < 46);

        for (int i = 0; i < gardenGrid.getNumRows(); i++) {
            for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                Plant plant = gardenGrid.getPlant(i, j);
                if (plant != null) {
                    int req = plant.getTemperatureRequirement();
                    int tempDiff = greenhouseTemp - req;

                    if (tempDiff > 0) {
                        // Too hot — apply cooling
                        EventBus.publish("CoolTemperatureEvent", new CoolTemperatureEvent(i, j, tempDiff));
                        if (tempDiff > 15) {
                            plant.addWater(tempDiff);
                        }
                        // Heat stress damage when significantly above requirement
                        if (tempDiff > HEAT_DAMAGE_THRESHOLD) {
                            int heatDmg = (tempDiff - HEAT_DAMAGE_THRESHOLD) / 3 + 1;
                            int newHealth = Math.max(0, plant.getCurrentHealth() - heatDmg);
                            plant.setCurrentHealth(newHealth);
                            GardenLog.log(GardenLog.Category.PLANT,
                                    "Day %d: %s at (%d,%d) heat stress: -%d HP (greenhouse %d°F, needs %d°F)",
                                    currentDay, plant.getName(), i, j, heatDmg, greenhouseTemp, req);
                            logger.info("Day {} - {} at ({},{}) heat stress -{} HP (greenhouse {}°F > req {}°F by {})",
                                    currentDay, plant.getName(), i, j, heatDmg, greenhouseTemp, req, tempDiff);
                        }
                    } else if (tempDiff < 0) {
                        // Too cold — apply heating
                        EventBus.publish("HeatTemperatureEvent", new HeatTemperatureEvent(i, j, -tempDiff));
                        int damage = FROST_DAMAGE_PER_PLANT;
                        int newHealth = Math.max(0, plant.getCurrentHealth() - damage);
                        plant.setCurrentHealth(newHealth);
                        GardenLog.log(GardenLog.Category.PLANT,
                                "Day %d: %s at (%d,%d) frost damage: %d (greenhouse %d°F < req %d°F)",
                                currentDay, plant.getName(), i, j, damage, greenhouseTemp, req);
                        logger.info("Day {} - {} at ({},{}) frost damage {} (greenhouse {}°F < req {}°F)",
                                currentDay, plant.getName(), i, j, damage, greenhouseTemp, req);
                    }
                }
            }
        }

        EventBus.publish("GreenhouseTempUpdateEvent", new GreenhouseTempUpdateEvent(weatherTemp, greenhouseTemp));
    }

    @Override
    public void run() {
        // Purely event-driven — no periodic work needed
    }
}
