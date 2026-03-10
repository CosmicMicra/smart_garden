package com.example.ooad_project;

import com.example.ooad_project.API.SmartGardenAPI;
import com.example.ooad_project.GardenLog;
import com.example.ooad_project.SubSystems.PesticideSystem;
import com.example.ooad_project.SubSystems.TemperatureSystem;
import com.example.ooad_project.SubSystems.WateringSystem;
import com.example.ooad_project.ThreadUtils.ThreadManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;

import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
        stage.setTitle("Smart Garden");
        stage.setScene(scene) ;
        stage.show();

        initializeBackgroundServices();

        // Initialize garden; rain/weather/temp/parasite now triggered automatically by DaySystem each day
        runAPIScheduledTasks();
    }

    private void initializeBackgroundServices() {
        Runnable wateringSystem = new WateringSystem();
        Runnable temperatureSystem = new TemperatureSystem();
        Runnable pesticideSystem = new PesticideSystem();
        DaySystem.getInstance();
        PestSpawner pestSpawner = new PestSpawner();

        ThreadManager.run(wateringSystem);
        ThreadManager.run(temperatureSystem);
        ThreadManager.run(pesticideSystem);
        pestSpawner.start();
    }


    private void runAPIScheduledTasks() {
        SmartGardenAPI api = new SmartGardenAPI();
        api.initializeGarden();
        Random rand = new Random();
        boolean rain = rand.nextBoolean();
        int weatherTemp;
        if (rain) {
            api.rain(rand.nextDouble() < 0.05 ? 0 : 5 + rand.nextInt(70));
            weatherTemp = 45 + rand.nextInt(21);
        } else {
            weatherTemp = rand.nextDouble() < 0.05 ? 35 + rand.nextInt(12) : 40 + rand.nextInt(81);
        }
        api.temperature(weatherTemp);
    }

    private void runAPIScheduledTasksWithoutJavaFX() {
        SmartGardenAPI api = new SmartGardenAPI();
        api.initializeGarden();
        Random rand = new Random();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

        // Rain/temp every hour (pests handled by PestSpawner)
        scheduler.scheduleAtFixedRate(() -> {
            int choice = rand.nextInt(2);
            switch (choice) {
                case 0 -> {
                    int rainAmount = rand.nextDouble() < 0.05 ? 0 : 5 + rand.nextInt(70);
                    if (rainAmount == 0) GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Random DROUGHT - no rain this day");
                    System.out.println("Triggering rain with amount: " + rainAmount);
                    api.rain(rainAmount);
                }
                case 1 -> {
                    int temperature = rand.nextDouble() < 0.05 ? 35 + rand.nextInt(11) : 40 + rand.nextInt(81);
                    if (temperature < 46) GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Random FROST - unexpected cold %d F", temperature);
                    System.out.println("Changing temperature to: " + temperature);
                    api.temperature(temperature);
                }
            }
        }, 0, 1, TimeUnit.HOURS);

        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("\n------ GARDEN STATE ------");
            api.getState();
            System.out.println("-------------------------\n");
        }, 6, 6, TimeUnit.HOURS);
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--no-ui")) {
            // Run without JavaFX UI
            HelloApplication app = new HelloApplication();
            app.initializeBackgroundServices();
            app.runAPIScheduledTasksWithoutJavaFX();

            // Keep the main thread alive
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                System.out.println("Main thread interrupted: " + e.getMessage());
            }
        } else {
            // Regular JavaFX launch
            launch(args);
        }
    }
}






