package com.example.ooad_project.SubSystems;

import com.example.ooad_project.Events.DayUpdateEvent;
import com.example.ooad_project.Events.ParasiteDisplayEvent;
import com.example.ooad_project.Events.PesticideApplicationEvent;
import com.example.ooad_project.Events.ManualOverrideEvent;
import com.example.ooad_project.Events.ParasiteEvent;
import com.example.ooad_project.GardenGrid;
import com.example.ooad_project.GardenLog;
import com.example.ooad_project.Parasite.Parasite;
import com.example.ooad_project.Plant.Plant;
import com.example.ooad_project.ThreadUtils.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PesticideSystem implements Runnable {
    private int currentDay;
    private final GardenGrid gardenGrid;
    private static final Logger logger = LogManager.getLogger("PesticideSystemLogger");

    // 1 game day = 300 real seconds
    private static final int SECONDS_PER_DAY = 300;
    private static final long CYCLE_PERIOD_MS   = 3L * SECONDS_PER_DAY * 1000;    // 3 days
    private static final long EFFECT_DURATION_MS = (long)(2.5 * SECONDS_PER_DAY * 1000); // 2.5 days
    private static final long MANUAL_DELAY_MS    = (long)((5.0/24.0) * SECONDS_PER_DAY * 1000); // 5 game hours
    private static final double EXTRA_DAMAGE_MULTIPLIER = 2.0;

    private long nextApplicationTime;
    private long effectExpiresAt;
    private volatile boolean pesticideActive = false;

    private final Map<String, ActiveInfestation> activeInfestations = new ConcurrentHashMap<>();

    public PesticideSystem() {
        this.gardenGrid = GardenGrid.getInstance();
        long now = System.currentTimeMillis();
        this.nextApplicationTime = now;
        this.effectExpiresAt = 0;
        logger.info("Pesticide System Initialized — cycle: {}s, effect: {}s, manual delay: {}s",
                CYCLE_PERIOD_MS/1000, EFFECT_DURATION_MS/1000, MANUAL_DELAY_MS/1000);

        EventBus.subscribe("DayUpdateEvent", event -> handleDayChangeEvent((DayUpdateEvent) event));
        EventBus.subscribe("ParasiteEvent", event -> handlePesticideEvent((ParasiteEvent) event));
        EventBus.subscribe("ManualOverrideEvent", event -> handleManualOverride((ManualOverrideEvent) event));
    }

    public boolean isPesticideActive() { return pesticideActive; }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(1000);
                long now = System.currentTimeMillis();

                if (now >= nextApplicationTime) {
                    applyScheduledPesticide();
                    effectExpiresAt = now + EFFECT_DURATION_MS;
                    nextApplicationTime = now + CYCLE_PERIOD_MS;
                    pesticideActive = true;
                    EventBus.publish("PesticideScheduleEvent", "APPLIED");
                    logger.info("Scheduled pesticide applied — active for {}s, next in {}s",
                            EFFECT_DURATION_MS/1000, CYCLE_PERIOD_MS/1000);
                    GardenLog.log(GardenLog.Category.PEST,
                            "Scheduled pesticide applied — protection active for %.1f days", (double)EFFECT_DURATION_MS / (SECONDS_PER_DAY * 1000));
                }

                if (pesticideActive && now >= effectExpiresAt) {
                    pesticideActive = false;
                    EventBus.publish("PesticideScheduleEvent", "EXPIRED");
                    logger.info("Pesticide effect expired — plants unprotected until next cycle");
                    GardenLog.log(GardenLog.Category.PEST, "Pesticide effect EXPIRED — extra pest damage until next cycle");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void applyScheduledPesticide() {
        for (Iterator<Map.Entry<String, ActiveInfestation>> it = activeInfestations.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, ActiveInfestation> entry = it.next();
            ActiveInfestation inf = entry.getValue();
            Plant plant = gardenGrid.getPlant(inf.row, inf.col);
            if (plant != null) {
                int maxHeal = Math.max(0, plant.getHealthFull() - plant.getCurrentHealth() - 1);
                plant.healPlant(Math.min(5, maxHeal));
            }
        }
    }

    private void handleDayChangeEvent(DayUpdateEvent event) {
        this.currentDay = event.getDay();
    }

    private void handleManualOverride(ManualOverrideEvent event) {
        if (event.getType() != ManualOverrideEvent.Type.PESTICIDE) return;
        int healAmount = event.getValue();

        for (Iterator<Map.Entry<String, ActiveInfestation>> it = activeInfestations.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, ActiveInfestation> entry = it.next();
            ActiveInfestation inf = entry.getValue();
            Plant plant = gardenGrid.getPlant(inf.row, inf.col);
            if (plant != null) {
                int maxHeal = Math.max(0, plant.getHealthFull() - plant.getCurrentHealth() - 1);
                plant.healPlant(Math.min(healAmount, maxHeal));
            }
            it.remove();
        }
        for (int i = 0; i < gardenGrid.getNumRows(); i++) {
            for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                Plant plant = gardenGrid.getPlant(i, j);
                if (plant != null) {
                    int maxHeal = Math.max(0, plant.getHealthFull() - plant.getCurrentHealth() - 1);
                    plant.healPlant(Math.min(healAmount, maxHeal));
                }
            }
        }

        // Activate effect immediately + delay next scheduled cycle by 5 game hours
        long now = System.currentTimeMillis();
        pesticideActive = true;
        effectExpiresAt = now + EFFECT_DURATION_MS;
        nextApplicationTime = nextApplicationTime + MANUAL_DELAY_MS;
        EventBus.publish("PesticideScheduleEvent", "MANUAL");
        logger.info("Manual pesticide — effect active, next scheduled cycle delayed by {}s", MANUAL_DELAY_MS/1000);
        GardenLog.log(GardenLog.Category.MANUAL_OVERRIDE,
                "Manual pesticide applied — protection active, next scheduled cycle delayed by ~5 game hours");
    }

    private void handlePesticideEvent(ParasiteEvent event) {
        Parasite parasite = event.getParasite();
        boolean isCrow = parasite.getName().equalsIgnoreCase("Crow");
        boolean hasTarget = event.hasTarget();

        for (int i = 0; i < gardenGrid.getNumRows(); i++) {
            for (int j = 0; j < gardenGrid.getNumCols(); j++) {
                if (hasTarget && (i != event.getTargetRow() || j != event.getTargetCol())) continue;
                Plant plant = gardenGrid.getPlant(i, j);
                if (plant != null && parasite.getAffectedPlants().contains(plant.getName())) {
                    String cellKey = i + "," + j;

                    EventBus.publish("ParasiteDisplayEvent", new ParasiteDisplayEvent(parasite, i, j));

                    // Apply base damage
                    parasite.affectPlant(plant);

                    // Extra damage when pesticide is NOT active
                    if (!pesticideActive && !isCrow) {
                        int extraDmg = (int)(parasite.getDamage() * (EXTRA_DAMAGE_MULTIPLIER - 1));
                        int newHp = Math.max(0, plant.getCurrentHealth() - extraDmg);
                        plant.setCurrentHealth(newHp);
                        logger.info("Day {} - {} at ({},{}) took {} EXTRA damage (no pesticide protection)",
                                currentDay, parasite.getName(), i, j, extraDmg);
                        GardenLog.log(GardenLog.Category.PEST,
                                "⚠ %s at (%d,%d) — no pesticide protection! Extra %d damage", parasite.getName(), i, j, extraDmg);
                    }

                    logger.info("Day {} - {} attacked {} at ({},{})", currentDay, parasite.getName(), plant.getName(), i, j);

                    if (isCrow) {
                        EventBus.publish("ScarecrowEvent", new PesticideApplicationEvent(i, j, "scarecrow"));
                        int healAmount = parasite.getDamage();
                        int maxHeal = Math.max(0, plant.getHealthFull() - plant.getCurrentHealth() - 1);
                        plant.healPlant(Math.min(healAmount, maxHeal));
                        logger.info("Day {} - Scarecrow deployed at ({},{}), crow scared away! Plant healed +{}", currentDay, i, j, Math.min(healAmount, maxHeal));
                        GardenLog.log(GardenLog.Category.PEST, "Scarecrow scared away Crow at (%d,%d), plant recovered", i, j);

                    } else {
                        ActiveInfestation existing = activeInfestations.get(cellKey);
                        if (existing != null && existing.parasiteName.equals(parasite.getName())) {
                            existing.roundsRemaining--;
                            logger.info("Day {} - Pesticide round applied to {} at ({},{}). Rounds left: {}", currentDay, parasite.getName(), i, j, existing.roundsRemaining);

                            int roundHeal = pesticideActive ? parasite.getDamage() / 2 : parasite.getDamage() / 4;
                            int maxHeal = Math.max(0, plant.getHealthFull() - plant.getCurrentHealth() - 1);
                            plant.healPlant(Math.min(roundHeal, maxHeal));

                            EventBus.publish("PesticideApplicationEvent", new PesticideApplicationEvent(i, j, "standard"));

                            if (existing.roundsRemaining <= 0) {
                                activeInfestations.remove(cellKey);
                                int killHeal = parasite.getDamage();
                                maxHeal = Math.max(0, plant.getHealthFull() - plant.getCurrentHealth() - 1);
                                plant.healPlant(Math.min(killHeal, maxHeal));
                                EventBus.publish("PestKilledEvent", new PesticideApplicationEvent(i, j, "killed"));
                                logger.info("Day {} - {} KILLED at ({},{})!", currentDay, parasite.getName(), i, j);
                                GardenLog.log(GardenLog.Category.PEST, "%s eliminated at (%d,%d) after %d rounds", parasite.getName(), i, j, parasite.getRoundsToKill());
                            }
                        } else {
                            int roundsNeeded = parasite.getRoundsToKill();
                            activeInfestations.put(cellKey, new ActiveInfestation(parasite.getName(), i, j, roundsNeeded - 1));
                            logger.info("Day {} - New {} infestation at ({},{}). Rounds needed: {}", currentDay, parasite.getName(), i, j, roundsNeeded - 1);

                            int roundHeal = pesticideActive ? parasite.getDamage() / 2 : parasite.getDamage() / 4;
                            int maxHeal = Math.max(0, plant.getHealthFull() - plant.getCurrentHealth() - 1);
                            plant.healPlant(Math.min(roundHeal, maxHeal));

                            EventBus.publish("PesticideApplicationEvent", new PesticideApplicationEvent(i, j, "standard"));
                            GardenLog.log(GardenLog.Category.PEST, "%s infestation at (%d,%d) - pesticide round 1/%d applied", parasite.getName(), i, j, roundsNeeded);

                            if (roundsNeeded - 1 <= 0) {
                                activeInfestations.remove(cellKey);
                                int killHeal = parasite.getDamage();
                                maxHeal = Math.max(0, plant.getHealthFull() - plant.getCurrentHealth() - 1);
                                plant.healPlant(Math.min(killHeal, maxHeal));
                                EventBus.publish("PestKilledEvent", new PesticideApplicationEvent(i, j, "killed"));
                                logger.info("Day {} - {} killed in 1 round at ({},{})", currentDay, parasite.getName(), i, j);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Tracks an active pest infestation at a specific grid cell. */
    private static class ActiveInfestation {
        final String parasiteName;
        final int row;
        final int col;
        int roundsRemaining;

        ActiveInfestation(String parasiteName, int row, int col, int roundsRemaining) {
            this.parasiteName = parasiteName;
            this.row = row;
            this.col = col;
            this.roundsRemaining = roundsRemaining;
        }
    }
}
