package com.example.ooad_project;

import com.example.ooad_project.API.SmartGardenAPI;
import com.example.ooad_project.Parasite.Parasite;
import com.example.ooad_project.Parasite.ParasiteManager;
import com.example.ooad_project.Plant.Plant;
import com.example.ooad_project.ThreadUtils.ThreadManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Independent pest spawner: each pest type runs on its own daemon thread with random attack intervals.
 * Each attack picks ONE random vulnerable plant on the grid and calls api.parasite(name, row, col).
 */
public class PestSpawner {

    private static final Logger logger = LogManager.getLogger("PestSpawnerLogger");

    private static final class PestConfig {
        final String name;
        final int minSec;
        final int maxSec;

        PestConfig(String name, int minSec, int maxSec) {
            this.name = name;
            this.minSec = minSec;
            this.maxSec = maxSec;
        }
    }

    private static final PestConfig[] PEST_CONFIGS = {
            new PestConfig("Aphids", 6, 12),
            new PestConfig("Slugs", 10, 18),
            new PestConfig("Rat", 15, 25),
            new PestConfig("Crow", 20, 35),
            new PestConfig("Locust", 25, 40),
    };

    private final GardenGrid gardenGrid = GardenGrid.getInstance();
    private final ParasiteManager parasiteManager = ParasiteManager.getInstance();
    private final Random random = new Random();

    public void start() {
        logger.info("PestSpawner starting - launching {} independent pest threads", PEST_CONFIGS.length);
        for (PestConfig config : PEST_CONFIGS) {
            ThreadManager.run(() -> runPestLoop(config));
        }
    }

    private void runPestLoop(PestConfig config) {
        String pestName = config.name;
        while (true) {
            try {
                int delaySec = config.minSec + random.nextInt(config.maxSec - config.minSec + 1);
                Thread.sleep(delaySec * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Pest thread {} interrupted", pestName);
                return;
            }

            Parasite parasite = parasiteManager.getParasiteByName(pestName);
            if (parasite == null) continue;

            List<Plant> vulnerable = getVulnerablePlants(parasite);
            if (vulnerable.isEmpty()) continue;

            Plant target = vulnerable.get(random.nextInt(vulnerable.size()));
            SmartGardenAPI api = new SmartGardenAPI();
            api.parasite(pestName, target.getRow(), target.getCol());
            logger.debug("{} attacked {} at ({},{})", pestName, target.getName(), target.getRow(), target.getCol());
        }
    }

    private List<Plant> getVulnerablePlants(Parasite parasite) {
        List<Plant> out = new ArrayList<>();
        List<Plant> plants = gardenGrid.getPlants();
        for (Plant p : plants) {
            if (p != null && parasite.getAffectedPlants().contains(p.getName())) {
                out.add(p);
            }
        }
        return out;
    }
}
