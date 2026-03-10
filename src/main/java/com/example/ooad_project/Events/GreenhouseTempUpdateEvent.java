package com.example.ooad_project.Events;

/** Published by TemperatureSystem after updating weather and greenhouse temps. UI subscribes for header display. */
public class GreenhouseTempUpdateEvent {
    private final int weatherTemp;
    private final int greenhouseTemp;

    public GreenhouseTempUpdateEvent(int weatherTemp, int greenhouseTemp) {
        this.weatherTemp = weatherTemp;
        this.greenhouseTemp = greenhouseTemp;
    }

    public int getWeatherTemp() { return weatherTemp; }
    public int getGreenhouseTemp() { return greenhouseTemp; }
}
