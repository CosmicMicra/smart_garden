package com.example.ooad_project.Plant;


import com.example.ooad_project.Events.PlantHealthUpdateEvent;
import com.example.ooad_project.Events.PlantImageUpdateEvent;
import com.example.ooad_project.ThreadUtils.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/**
 * This is abstract class that represents a plant in the garden.
 * It is the parent class for all the plants in the garden.
 * i.e. flowers, trees, shrubs, etc.
 */
public abstract class Plant {

    private final String name;
    private final int waterRequirement;
    private String currentImage;
    private Boolean isWatered = false;
    private int currentWater = 0;
    private final int temperatureRequirement;
    private static final Logger logger = LogManager.getLogger("PesticideSystemLogger");
    private ArrayList<String> allImages;

    private final int healthSmall;
    private final int healthMedium;
    private final int healthFull;
    private int currentHealth;

    private ArrayList<String> vulnerableTo;

//    Default row and col are -1
//    i.e. the plant is not in the garden
    private int row = -1;
    private int col = -1;

    /** Days without water (no rain, no sprinklers). Causes health damage when >= threshold. */
    private int consecutiveDaysUnwatered = 0;

    /** Days without water before dehydration damage starts. Varies by plant type (trees sustain longer). */
    private final int dehydrationThresholdDays;
    /** Health damage per day over threshold. Flowers/bushes take more damage. */
    private final int dehydrationDamagePerDay;

    /** Accumulator for fractional per-second water consumption (waterRequirement/300 per sec). */
    private double consumptionAccumulator = 0;

    public Plant(String name, int waterRequirement, String imageName, int temperatureRequirement, ArrayList<String> vulnerableTo, int healthSmall, int healthMedium, int healthFull, ArrayList<String> allImages) {
        this(name, waterRequirement, imageName, temperatureRequirement, vulnerableTo, healthSmall, healthMedium, healthFull, allImages, 3, 5);
    }

    public Plant(String name, int waterRequirement, String imageName, int temperatureRequirement, ArrayList<String> vulnerableTo, int healthSmall, int healthMedium, int healthFull, ArrayList<String> allImages, int dehydrationThresholdDays, int dehydrationDamagePerDay) {
        this.name = name;
        this.waterRequirement = waterRequirement;
        this.currentImage = imageName;
        this.temperatureRequirement = temperatureRequirement;
        this.vulnerableTo = vulnerableTo;
        this.healthSmall = healthSmall;
        this.healthMedium = healthMedium;
        this.healthFull = healthFull;
        this.allImages = allImages;
        this.dehydrationThresholdDays = dehydrationThresholdDays;
        this.dehydrationDamagePerDay = dehydrationDamagePerDay;
        this.currentHealth = healthSmall;
    }

//    Heal plant function

    /** Max water a plant can hold — generous cap to prevent unbounded growth. */
    public int getMaxWaterCapacity() { return waterRequirement * 3; }

    /**
     * Add water to the pool. Does NOT set isWatered — only sprinklers do that.
     */
    public synchronized void addWater(int amount) {
        this.currentWater = Math.min(currentWater + amount, getMaxWaterCapacity());
    }

    /** Seconds per game day (5 minutes). */
    private static final int SECONDS_PER_DAY = 300;

    /**
     * Consume water for one second. Drains waterRequirement over the full day (300s).
     * If the plant consumes water successfully, it heals (drinking = growing).
     * Does NOT touch isWatered — that is sprinkler-only.
     */
    public synchronized void tickConsumeWater() {
        if (harvested || this.currentWater <= 0) {
            return;
        }
        consumptionAccumulator += waterRequirement / (double) SECONDS_PER_DAY;
        int take = (int) consumptionAccumulator;
        if (take > 0) {
            consumptionAccumulator -= take;
            this.currentWater = Math.max(0, this.currentWater - take);
            healPlant(1); // consuming water = growing
        }
    }

    private boolean harvested = false;

    public boolean isHarvested() { return harvested; }

    public synchronized void healPlant(int healAmount) {
        if (harvested) return;
        int previousStage = getHealthStage();
        this.currentHealth = Math.min(this.currentHealth + healAmount, this.healthFull);

        int currentStage = getHealthStage();

        if (previousStage != currentStage) {
            updatePlantImage(currentStage);
            logger.info("Plant: {} at position ({}, {}) health stage changed to {}, updated image to {}",
                    this.name, this.row, this.col, currentStage, this.currentImage);
        }

        if (this.currentHealth >= this.healthFull && !harvested) {
            harvested = true;
            EventBus.publish("PlantFullyGrownEvent", this);
        }

        logger.info("Plant: {} at position ({}, {}) healed by {} points, new health: {}",
                this.name, this.row, this.col, healAmount, this.currentHealth);
    }


    public synchronized void setCurrentHealth(int health) {
        int previousStage = getHealthStage();

        int oldHealth = this.currentHealth;

        this.currentHealth = health;

        if (this.currentHealth <= 0) {
            this.currentHealth = 0;
            EventBus.publish("PlantDeathEvent", this);
            return;
        }


        EventBus.publish("PlantHealthUpdateEvent", new PlantHealthUpdateEvent(this.row, this.col, oldHealth, this.currentHealth));

        int currentStage = getHealthStage();

        // Check if the health stage has changed, then update the image
        if (previousStage != currentStage) {
            updatePlantImage(currentStage);
            logger.info("Plant: {} at position ({}, {}) updated to new health stage: {}, image updated to {}", this.name, this.row, this.col, currentStage, this.currentImage);
        }
    }

    /**
     * Updates the current image based on the health stage.
     * @param stage the current health stage of the plant.
     */
    private void updatePlantImage(int stage) {
        if (stage >= 0 && stage < this.allImages.size()) {
            this.currentImage = this.allImages.get(stage);
            EventBus.publish("PlantImageUpdateEvent", new PlantImageUpdateEvent(this));
        }
    }

    /**
     * Determines the health stage of the plant.
     * @return an integer representing the stage: 0 for small, 1 for medium, 2 for full health.
     */
    private int getHealthStage() {
        if (this.currentHealth < this.healthMedium) {
            return 0; // Small
        } else if (this.currentHealth < this.healthFull) {
            return 1; // Medium
        } else {
            return 2; // Full
        }
    }

    public String getGrowthStageDescription() {
        if (this.getCurrentHealth() < this.getHealthMedium()) {
            return "Small";
        } else if (this.getCurrentHealth() < this.getHealthFull()) {
            return "Medium";
        } else {
            return "Full";
        }
    }

    // Standard getters and setters

    public ArrayList<String> getVulnerableTo() {
        return vulnerableTo;
    }

    public String getName() {
        return name;
    }

    public Boolean getIsWatered() {
        return isWatered;
    }

    public synchronized void  setIsWatered(Boolean isWatered) {
        this.isWatered = isWatered;
    }

    public int getCurrentWater() {
        return currentWater;
    }

    public void setCurrentWater(int currentWater) {
        this.currentWater = currentWater;
    }



    public int getWaterRequirement() {
        return waterRequirement;
    }


    public String getCurrentImage() {
        return currentImage;
    }

    public void setCurrentImage(String currentImage) {
        this.currentImage = currentImage;
    }

    public int getTemperatureRequirement() {
        return temperatureRequirement;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public int getHealthSmall() {
        return healthSmall;
    }

    public int getHealthMedium() {
        return healthMedium;
    }

    public int getHealthFull() {
        return healthFull;
    }

    public ArrayList<String> getAllImages() {
        return allImages;
    }



    /**
     * Retrieves the current health of the plant.
     * This method is synchronized to ensure thread safety.
     * @return the current health of the plant.
     */
    public synchronized int getCurrentHealth() {
        return currentHealth;
    }

    /**
     * Process dehydration at end of day.
     * If the plant has water OR was sprinkler-watered today: reset dry streak.
     * Otherwise: increment dry streak, apply damage after threshold.
     * @return damage applied (0 if none)
     */
    public synchronized int processDehydration() {
        if (this.currentWater > 0 || this.isWatered) {
            this.consecutiveDaysUnwatered = 0;
            return 0;
        }
        this.consecutiveDaysUnwatered++;
        if (this.consecutiveDaysUnwatered < dehydrationThresholdDays) {
            return 0;
        }
        int daysOver = this.consecutiveDaysUnwatered - dehydrationThresholdDays + 1;
        int damage = daysOver * dehydrationDamagePerDay;
        int newHealth = Math.max(0, this.currentHealth - damage);
        setCurrentHealth(newHealth);
        return damage;
    }

    public int getConsecutiveDaysUnwatered() {
        return consecutiveDaysUnwatered;
    }

    public int getDehydrationThresholdDays() {
        return dehydrationThresholdDays;
    }

    public int getDehydrationDamagePerDay() {
        return dehydrationDamagePerDay;
    }

//    /**
//     * Sets the current health of the plant.
//     * This method is synchronized to ensure that updates are atomic and changes
//     * are visible to other threads.
//     * @param health the new health value for the plant.
//     */
//    public synchronized void setCurrentHealth(int health) {
//        this.currentHealth = health;
//    }





}
