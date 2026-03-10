package com.example.ooad_project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Unified garden log. All occurrences, interactions, and state changes
 * are written here for easy navigation. Single file: garden.log
 */
public class GardenLog {
    private static final String LOG_FILE = "logs/garden.log";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum Category {
        INIT, DAY, RAIN, TEMPERATURE, PARASITE, SPRINKLER, HEATER, PESTICIDE, PEST,
        PLANT, MANUAL_OVERRIDE, WARNING, PLAN, STATE, RANDOM_EVENT
    }

    public static void log(Category category, String message) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String line = String.format("[%s] [%s] %s%n", timestamp, category, message);
        append(line);
    }

    public static void log(Category category, String format, Object... args) {
        log(category, String.format(format, args));
    }

    public static void logSection(String title) {
        String line = String.format("%n========== %s ==========%n", title);
        append(line);
    }

    private static void append(String content) {
        try {
            Path path = Paths.get(LOG_FILE);
            Files.write(path, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("GardenLog write failed: " + e.getMessage());
        }
    }
}
