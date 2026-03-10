package com.example.ooad_project.Parasite.Children;

import com.example.ooad_project.Events.ParasiteDamageEvent;
import com.example.ooad_project.Parasite.Parasite;
import com.example.ooad_project.Plant.Plant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Random;

public class Slugs extends Parasite {
    private static final double MISS_CHANCE = 0.25;  // 25% chance to miss
    private Random random = new Random();
    private static final Logger logger = LogManager.getLogger("PesticideSystemLogger");

    public Slugs(String name, int damage, String imageName, ArrayList<String> affectedPlants, int roundsToKill) {
        super(name, damage, imageName, affectedPlants, roundsToKill);
    }

    @Override
    public void affectPlant(Plant plant) {
        if (random.nextDouble() >= MISS_CHANCE) {
            int oldHealth = plant.getCurrentHealth();
            int newHealth = Math.max(0, plant.getCurrentHealth() - this.getDamage());
            super.publishDamageEvent(new ParasiteDamageEvent(plant.getRow(), plant.getCol(), this.getDamage()));
            plant.setCurrentHealth(newHealth);
            logger.info("Slug damaged {} at ({},{}). {} -> {}", plant.getName(), plant.getRow(), plant.getCol(), oldHealth, newHealth);
        } else {
            logger.info("Slug missed {} at ({},{})", plant.getName(), plant.getRow(), plant.getCol());
        }
    }
}
