package com.example.ooad_project;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared state for garden simulation. Tracks rain, random events, temps, etc.
 * Used for randomness: rain→no sprinklers, equipment failure, etc.
 */
public class GardenStateManager {
    private static GardenStateManager instance;
    private final AtomicBoolean rainedToday = new AtomicBoolean(false);
    private final AtomicBoolean sprinklerFailureToday = new AtomicBoolean(false);
    private final AtomicBoolean frostOccurred = new AtomicBoolean(false);
    private final AtomicBoolean manualSprinklerOff = new AtomicBoolean(false);
    private final AtomicInteger currentWeatherTemp = new AtomicInteger(55);
    private final AtomicInteger currentGreenhouseTemp = new AtomicInteger(65);

    private GardenStateManager() {}

    public static synchronized GardenStateManager getInstance() {
        if (instance == null) {
            instance = new GardenStateManager();
        }
        return instance;
    }

    public void setRainedToday(boolean rained) {
        rainedToday.set(rained);
    }

    public boolean getRainedToday() {
        return rainedToday.get();
    }

    public void setSprinklerFailureToday(boolean failed) {
        sprinklerFailureToday.set(failed);
    }

    public boolean getSprinklerFailureToday() {
        return sprinklerFailureToday.get();
    }

    public void setFrostOccurred(boolean occurred) {
        frostOccurred.set(occurred);
    }

    public boolean getFrostOccurred() {
        return frostOccurred.get();
    }

    public void setManualSprinklerOff(boolean off) {
        manualSprinklerOff.set(off);
    }

    public boolean getManualSprinklerOff() {
        return manualSprinklerOff.get();
    }

    public int getCurrentWeatherTemp() { return currentWeatherTemp.get(); }
    public void setCurrentWeatherTemp(int temp) { currentWeatherTemp.set(temp); }
    public int getCurrentGreenhouseTemp() { return currentGreenhouseTemp.get(); }
    public void setCurrentGreenhouseTemp(int temp) { currentGreenhouseTemp.set(temp); }

    /** Reset daily flags at end of day (manualSprinklerOff and temps are NOT reset - user-controlled / persistent) */
    public void resetDailyFlags() {
        rainedToday.set(false);
        sprinklerFailureToday.set(false);
        frostOccurred.set(false);
    }
}
