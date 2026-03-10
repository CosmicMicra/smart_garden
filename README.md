# Smart Garden

Self-contained JavaFX garden simulation. **Copy this folder anywhere and run.**

## Run

```bash
cd Garden
mvn javafx:run
```

## Requirements

- Java 22
- Maven (or use included `mvnw`)

## Structure

- **config/** – plants.json, parasites.json, garden_config.json
- **logs/** – All log files (created at runtime)
- **src/main/java/** – Application code
- **src/main/resources/** – FXML, CSS, images

## Standalone simulation (no GUI)

```bash
mvn exec:java -Dexec.mainClass="com.example.ooad_project.API.GardenSimulationRunner"
```
