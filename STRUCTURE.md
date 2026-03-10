# Garden – Self-Contained Project

**Copy this folder anywhere and run:** `mvn javafx:run`

```
Garden/
├── config/                    # Configuration files
│   ├── garden_config.json     # Plant initialization (amounts)
│   ├── plants.json            # Plant definitions (trees, vegetables, flowers)
│   └── parasites.json         # Parasite definitions (targets, damage)
│
├── logs/                      # All log output (created at runtime)
│   ├── garden.log
│   ├── DayLogger.log
│   ├── GardenAPI.log
│   ├── GardenUIController.log
│   ├── PesticideSystem.log
│   ├── TemperatureSystem.log
│   └── WateringSystem.log
├── pom.xml
├── mvnw, mvnw.cmd
└── src/main/                  # Java source & resources
```

## Source Code (src/main/java/com/example/ooad_project/)

| Package      | Contents                                      |
|-------------|------------------------------------------------|
| **API**     | SmartGardenAPI, GardenSimulationAPI, interfaces |
| **Events**  | RainEvent, TemperatureEvent, ParasiteEvent, etc. |
| **Parasite**| Parasite, ParasiteManager, ParasiteFactory, Children (Rat, Crow, Locust, Aphids, Slugs) |
| **Plant**   | Plant, PlantManager, Children (Tree, Flower, Vegetable) |
| **SubSystems** | WateringSystem, TemperatureSystem, PesticideSystem |
| **ThreadUtils** | EventBus, ThreadManager |
| **Core**    | DaySystem, GardenGrid, GardenStateManager, GardenLog |

## Run

```bash
cd Garden
mvn javafx:run
```

All paths (config/, logs/) are relative to the Garden directory.
