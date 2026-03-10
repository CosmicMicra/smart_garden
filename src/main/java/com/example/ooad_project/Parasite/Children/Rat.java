package com.example.ooad_project.Parasite.Children;

import com.example.ooad_project.Events.ParasiteDamageEvent;
import com.example.ooad_project.Parasite.Parasite;
import com.example.ooad_project.Plant.Plant;

import java.util.ArrayList;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Rat extends Parasite {

    private Random random = new Random();
    private static final double MISS_CHANCE = 0.15;  // 15% chance to miss
    private static final Logger logger = LogManager.getLogger("PesticideSystemLogger");

    public Rat(String name, int damage, String imageName, ArrayList<String> affectedPlants, int roundsToKill) {
        super(name, damage, imageName, affectedPlants, roundsToKill);
    }

    @Override
    public void affectPlant(Plant plant) {
        if (random.nextDouble() >= MISS_CHANCE) {
            int oldHealth = plant.getCurrentHealth();
            int newHealth = Math.max(0, plant.getCurrentHealth() - this.getDamage());
            super.publishDamageEvent(new ParasiteDamageEvent(plant.getRow(), plant.getCol(), this.getDamage()));
            plant.setCurrentHealth(newHealth);
            logger.info("Rat damaged {} at ({},{}). {} -> {}", plant.getName(), plant.getRow(), plant.getCol(), oldHealth, newHealth);
        } else {
            logger.info("Rat missed {} at ({},{})", plant.getName(), plant.getRow(), plant.getCol());
        }
    }
}
