package com.example.ooad_project;

import com.example.ooad_project.API.SmartGardenAPI;
import com.example.ooad_project.Events.*;
import com.example.ooad_project.Parasite.Parasite;
import com.example.ooad_project.Parasite.ParasiteManager;
import com.example.ooad_project.Plant.Children.Flower;
import com.example.ooad_project.Plant.Plant;
import com.example.ooad_project.Plant.Children.Tree;
import com.example.ooad_project.Plant.Children.Vegetable;
import com.example.ooad_project.Plant.PlantManager;
import com.example.ooad_project.SubSystems.FarmerShop;
import com.example.ooad_project.ThreadUtils.EventBus;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * GardenUIController — "Monitor" layout (Concept C + Plant Detail Panel)
 *
 * Fully integrated with:
 *  - GardenLog unified logging
 *  - GardenStateManager (rain flags, equipment failure, frost)
 *  - ManualOverrideEvent with confirmation dialogs + warnings
 *  - Plant dehydration system (consecutiveDaysUnwatered, thresholds)
 *  - Time scale: 1 game day = 5 real minutes
 *
 * Layout:
 * ┌──────────────────────────────────────────────────────────────┐
 * │  HEADER BAR  (day, temp, weather, plant count, uptime)       │
 * ├────────────────────────────────────┬─────────────────────────┤
 * │  LEFT: TabPane                     │  RIGHT: detail/metrics  │
 * │   ├ Grid tab  (garden grid)        │   ├ Plant detail panel  │
 * │   ├ List tab  (table view)         │   ├ Weather widget      │
 * │   └ Logs tab  (event log)          │   ├ Systems status      │
 * │                                    │   └ Garden health avg   │
 * ├────────────────────────────────────┤                         │
 * │  TOOLBAR (Add Plant, Overrides)    │                         │
 * └────────────────────────────────────┴─────────────────────────┘
 */
public class GardenUIController {

    // ────────── Color Constants (cozy garden theme) ──────────
    private static final String BG_DARK       = "#c4b088";
    private static final String BG_CARD       = "rgba(245,240,225,0.9)";
    private static final String BORDER        = "#b89e6a";
    private static final String TEXT_DIM      = "#6d5535";
    private static final String TEXT_LIGHT    = "#4a2e14";
    private static final String TEXT_ON_LIGHT = "#3E2723";
    private static final String ACCENT_BLUE   = "#72b4d4";
    private static final String ACCENT_GREEN  = "#6aaf4e";
    private static final String ACCENT_ORANGE = "#d4a04a";
    private static final String ACCENT_PURPLE = "#9a7bb5";
    private static final String ACCENT_YELLOW = "#d4a04a";
    private static final String ACCENT_RED    = "#d46a5a";
    private static final String SEC_TITLE     = "#4a2e14";

    // ────────── FXML injections (kept so hello-view.fxml doesn't crash) ──────────
    @javafx.fxml.FXML private AnchorPane anchorPane;
    @javafx.fxml.FXML private GridPane gridPane;
    @javafx.fxml.FXML private Label currentDay;
    @javafx.fxml.FXML private Label rainStatusLabel;
    @javafx.fxml.FXML private Label temperatureStatusLabel;
    @javafx.fxml.FXML private Label parasiteStatusLabel;
    @javafx.fxml.FXML private MenuButton vegetableMenuButton;
    @javafx.fxml.FXML private MenuButton flowerMenuButton;
    @javafx.fxml.FXML private MenuButton treeMenuButton;
    @javafx.fxml.FXML private HBox menuBar;
    @javafx.fxml.FXML private javafx.scene.shape.Rectangle treePlaceholder;
    @javafx.fxml.FXML private javafx.scene.shape.Rectangle treeTrunk;
    @javafx.fxml.FXML private javafx.scene.shape.Line rightBranch1;
    @javafx.fxml.FXML private javafx.scene.shape.Line rightBranch2;
    @javafx.fxml.FXML private javafx.scene.shape.Line leftBranch;
    @javafx.fxml.FXML private Button manualWaterButton;
    @javafx.fxml.FXML private Button manualPesticideButton;
    @javafx.fxml.FXML private Button manualHeaterButton;

    // ────────── Programmatic UI nodes ──────────
    private BorderPane rootLayout;
    private GridPane gardenGridPane;
    private TabPane tabPane;
    private ListView<String> logListView;
    private ObservableList<String> logItems;
    private VBox plantDetailBox;
    private Label headerMoneyLabel, headerDayLabel, headerTempLabel, headerWeatherLabel, headerPlantCountLabel, headerUptimeLabel;
    private Label shopCountLabel;
    private StackPane shopContainer;
    private Label avgHealthLabel, healthyCountLabel, stressedCountLabel, criticalCountLabel;
    private Label heatingStatusLabel, sprinklerStatusLabel;
    private Label pesticideNameLabel, pesticideStatusLabel;
    private Label weatherIconLabel, weatherTempLabel, weatherHumidityLabel;
    private Button sprinklerToggleBtn;
    private StackPane scarecrowContainer;

    // Rain overlay (falling drops - used only for RainEvent)
    private Canvas rainCanvas;
    private List<double[]> rainDrops;
    private AnimationTimer rainAnimation;

    // (Watering uses sprinkler_on.png per cell — no canvas animation needed)

    // ────────── State ──────────
    private int logDay = 0;
    private int currentTemperature = 55;
    private Plant selectedPlant = null;
    private final Random random = new Random();
    private long startTimeMillis;
    private volatile boolean pesticideScheduleActive = false;

    // ────────── Planting Mode ──────────
    private boolean plantingMode = false;
    private String pendingPlantName = null;
    private HBox toolbarBox;
    private Button cancelPlantBtn;
    private Label plantingModeLabel;
    private ImageView farmerToolbarIcon;

    // ────────── Singletons ──────────
    private GardenGrid gardenGrid;
    private PlantManager plantManager = PlantManager.getInstance();
    private ParasiteManager parasiteManager = ParasiteManager.getInstance();
    private SmartGardenAPI smartGardenAPI = new SmartGardenAPI();
    private GardenStateManager stateManager = GardenStateManager.getInstance();

    private static final Logger logger = LogManager.getLogger("GardenUIControllerLogger");

    // ════════════════════════════════════════════
    //  CONSTRUCTOR & INIT
    // ════════════════════════════════════════════

    public GardenUIController() { gardenGrid = GardenGrid.getInstance(); }

    @javafx.fxml.FXML
    public void initialize() {
        startTimeMillis = System.currentTimeMillis();
        List<Node> originalChildren = new ArrayList<>(anchorPane.getChildren());
        Platform.runLater(() -> {
            for (Node n : originalChildren) { n.setVisible(false); n.setManaged(false); }
            buildUI();
        });
        subscribeToEvents();
        startUptimeTimer();
        GardenLog.log(GardenLog.Category.INIT, "Monitor UI initialized");
        logger.info("GardenUIController (Monitor UI) initialized");
    }

    // ─── FXML manual override handlers (kept for FXML compat) ───
    @javafx.fxml.FXML public void onManualWater() {
        confirmOverride("Manual Water", "Turn on sprinklers now?",
            () -> { smartGardenAPI.manualWater(0); addLogEntry("WARN", "⚡ MANUAL OVERRIDE: Watering turned on"); });
    }
    @javafx.fxml.FXML public void onManualPesticide() {
        confirmOverride("Manual Pesticide", "This alters the planned pest control schedule. Continue?",
            () -> { smartGardenAPI.manualPesticide(); addLogEntry("WARN", "⚡ MANUAL OVERRIDE: Preventive pesticide applied"); });
    }
    @javafx.fxml.FXML public void onManualHeater() {
        confirmOverride("Manual Heater", "This may affect temperature-based automation. Continue?",
            () -> { smartGardenAPI.manualHeater(10); addLogEntry("WARN", "⚡ MANUAL OVERRIDE: Heater +10°F"); });
    }
    private void confirmOverride(String header, String content, Runnable onOk) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Manual Override"); a.setHeaderText(header); a.setContentText(content);
            a.showAndWait().ifPresent(r -> { if (r == ButtonType.OK) onOk.run(); });
        });
    }

    @javafx.fxml.FXML public void printGrid() { gardenGrid.printGrid(); }
    @javafx.fxml.FXML public void getPLantButtonPressed() { smartGardenAPI.getState(); }

    private void startUptimeTimer() {
        Timeline t = new Timeline(new KeyFrame(Duration.seconds(30), ev -> {
            long s = (System.currentTimeMillis() - startTimeMillis) / 1000;
            updateHeaderStat(headerUptimeLabel, (s / 3600 > 0 ? s / 3600 + "h " : "") + (s % 3600) / 60 + "m");
        }));
        t.setCycleCount(Timeline.INDEFINITE); t.play();
        // Poll day counter every 1 sec (robust fallback if DayUpdateEvent is missed)
        Timeline dayPoll = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            int d = DaySystem.getInstance().getCurrentDay();
            updateHeaderStat(headerDayLabel, d + "/24");
            updateHeaderStat(headerTempLabel, "W" + stateManager.getCurrentWeatherTemp() + "°F / G" + stateManager.getCurrentGreenhouseTemp() + "°F");
            updateHeaderStat(headerMoneyLabel, "$" + FarmerShop.getInstance().getTotalMoney());
        }));
        dayPoll.setCycleCount(Timeline.INDEFINITE); dayPoll.play();
    }

    // ════════════════════════════════════════════
    //  BUILD UI
    // ════════════════════════════════════════════

    private void buildUI() {
        rootLayout = new BorderPane();
        rootLayout.setStyle("-fx-background-color: #e8dbb5;");
        AnchorPane.setTopAnchor(rootLayout, 0.0); AnchorPane.setBottomAnchor(rootLayout, 0.0);
        AnchorPane.setLeftAnchor(rootLayout, 0.0); AnchorPane.setRightAnchor(rootLayout, 0.0);

        rootLayout.setTop(buildHeaderBar());
        rootLayout.setLeft(buildLeftSidebar());
        VBox center = new VBox();
        center.setStyle("-fx-background-color: #e8dbb5;");
        tabPane = buildTabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        center.getChildren().addAll(tabPane, buildToolbar());
        rootLayout.setCenter(center);
        rootLayout.setRight(buildRightPanel());

        rainCanvas = new Canvas(); rainCanvas.setMouseTransparent(true); rainDrops = new ArrayList<>();
        anchorPane.getChildren().add(rootLayout);
        anchorPane.getChildren().add(rainCanvas);
        rainCanvas.widthProperty().bind(anchorPane.widthProperty()); rainCanvas.heightProperty().bind(anchorPane.heightProperty());
        rainCanvas.toFront();
    }

    // ── Header ──
    private HBox buildHeaderBar() {
        HBox h = new HBox(); h.setAlignment(Pos.CENTER_LEFT); h.setPadding(new Insets(10, 20, 10, 20)); h.setSpacing(10);
        h.setStyle("-fx-background-color: #c4b088; -fx-border-color: #a08860; -fx-border-width: 0 0 2 0;");
        Circle dot = new Circle(4, Color.web(ACCENT_GREEN)); dot.setEffect(new DropShadow(6, Color.web(ACCENT_GREEN)));
        Label title = new Label("Smart Garden"); title.setFont(Font.font("Verdana", FontWeight.BOLD, 15)); title.setTextFill(Color.web("#3E2723"));
        VBox titleBox = new VBox(0); titleBox.getChildren().add(title);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        headerMoneyLabel = hstat("💰", "$0"); headerDayLabel = hstat("DAY", "0/24"); headerTempLabel = hstat("🌡️", "W55°F / G65°F");
        headerWeatherLabel = hstat("WEATHER", "☀️ Sunny"); headerPlantCountLabel = hstat("PLANTS", "0"); headerUptimeLabel = hstat("UPTIME", "0m");
        h.getChildren().addAll(dot, titleBox, sp, headerMoneyLabel, sep(), headerDayLabel, sep(), headerTempLabel, sep(), headerWeatherLabel, sep(), headerPlantCountLabel, sep(), headerUptimeLabel);
        return h;
    }
    private Label hstat(String k, String v) { Label l = new Label(k + "  " + v); l.setFont(Font.font("Verdana", FontWeight.BOLD, 12)); l.setTextFill(Color.web("#3E2723")); l.setUserData(k); return l; }
    private void updateHeaderStat(Label l, String v) { if (l == null) return; Platform.runLater(() -> { if (l != null) l.setText(l.getUserData() + "  " + v); }); }
    private Region sep() { Region r = new Region(); r.setPrefWidth(8); return r; }

    // ── TabPane ──
    private TabPane buildTabPane() {
        TabPane tp = new TabPane(); tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tp.setStyle("-fx-background-color: #e8dbb5;");
        tp.getTabs().addAll(new Tab("GRID", buildGridTab()), new Tab("LIST", buildListTab()), new Tab("LOGS", buildLogsTab()));
        return tp;
    }

    private Node buildGridTab() {
        gardenGridPane = new GridPane(); gardenGridPane.setHgap(3); gardenGridPane.setVgap(3);
        gardenGridPane.setPadding(new Insets(16)); gardenGridPane.setStyle("-fx-background-color: #4E8A3E;");
        for (int r = 0; r < gardenGrid.getNumRows(); r++)
            for (int c = 0; c < gardenGrid.getNumCols(); c++)
                gardenGridPane.add(createGridCell(r, c), c, r);
        for (int c = 0; c < gardenGrid.getNumCols(); c++) { ColumnConstraints cc = new ColumnConstraints(); cc.setPrefWidth(80); cc.setHgrow(Priority.SOMETIMES); gardenGridPane.getColumnConstraints().add(cc); }
        for (int r = 0; r < gardenGrid.getNumRows(); r++) { RowConstraints rc = new RowConstraints(); rc.setPrefHeight(80); rc.setVgrow(Priority.SOMETIMES); gardenGridPane.getRowConstraints().add(rc); }
        ScrollPane s = new ScrollPane(gardenGridPane); s.setFitToWidth(true); s.setFitToHeight(true);
        s.setStyle("-fx-background: #4E8A3E; -fx-background-color: #4E8A3E;"); return s;
    }

    private static final String CELL_BASE_STYLE = "-fx-background-color: rgba(255,253,240,0.9); -fx-border-color: #d4c49a; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;";
    private static final String CELL_PLANTING_EMPTY = "-fx-background-color: rgba(106,175,78,0.15); -fx-border-color: #6aaf4e; -fx-border-width: 2; -fx-border-style: dashed; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;";
    private static final String CELL_HOVER_PLANT = "-fx-background-color: rgba(200,230,200,0.95); -fx-border-color: #72b4d4; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;";
    private static final String CELL_HOVER_PLANTING = "-fx-background-color: rgba(106,175,78,0.35); -fx-border-color: #6aaf4e; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;";

    private StackPane createGridCell(int row, int col) {
        StackPane cell = new StackPane(); cell.setPrefSize(80, 80);
        cell.setStyle(CELL_BASE_STYLE);
        cell.setOnMouseClicked(e -> onCellClicked(row, col));
        cell.setOnMouseEntered(e -> {
            if (plantingMode) {
                if (!gardenGrid.isSpotOccupied(row, col)) {
                    cell.setStyle(CELL_HOVER_PLANTING);
                    showFarmerInCell(cell);
                }
            } else {
                if (gardenGrid.getPlant(row, col) != null) cell.setStyle(CELL_HOVER_PLANT);
            }
        });
        cell.setOnMouseExited(e -> {
            removeFarmerFromCell(cell);
            if (plantingMode && !gardenGrid.isSpotOccupied(row, col)) cell.setStyle(CELL_PLANTING_EMPTY);
            else cell.setStyle(CELL_BASE_STYLE);
        });
        return cell;
    }

    private void showFarmerInCell(StackPane cell) {
        removeFarmerFromCell(cell);
        try {
            ImageView farmer = new ImageView(new Image(getClass().getResourceAsStream("/images/farmer_sitting.png")));
            farmer.setUserData("farmer_cursor");
            farmer.setFitWidth(40); farmer.setFitHeight(40); farmer.setPreserveRatio(true);
            farmer.setOpacity(0.8); farmer.setMouseTransparent(true);
            StackPane.setAlignment(farmer, Pos.CENTER);
            cell.getChildren().add(farmer);
        } catch (Exception ignored) {}
    }

    private void removeFarmerFromCell(StackPane cell) {
        cell.getChildren().removeIf(n -> "farmer_cursor".equals(n.getUserData()));
    }

    private void removeFarmerFromAllCells() {
        if (gardenGridPane == null) return;
        for (Node n : gardenGridPane.getChildren()) {
            if (n instanceof StackPane cell) removeFarmerFromCell(cell);
        }
    }

    private void onCellClicked(int row, int col) {
        if (plantingMode) {
            if (gardenGrid.isSpotOccupied(row, col)) {
                exitPlantingMode();
                Plant p = gardenGrid.getPlant(row, col);
                if (p != null) { selectedPlant = p; updatePlantDetailPanel(p); }
                return;
            }
            placePlantAt(pendingPlantName, row, col);
            exitPlantingMode();
        } else {
            Plant p = gardenGrid.getPlant(row, col);
            if (p != null) { selectedPlant = p; updatePlantDetailPanel(p); }
        }
    }

    // ── List Tab ──
    @SuppressWarnings("unchecked")
    private Node buildListTab() {
        TableView<PlantRow> t = new TableView<>();
        t.setStyle("-fx-background-color: #e8dbb5; -fx-control-inner-background: #faf5e8; -fx-control-inner-background-alt: #f0e8d2;");
        t.getColumns().addAll(tc("PLANT","name",90), tc("TYPE","type",70), tc("POS","position",55),
            tc("HEALTH","health",65), tc("WATER","water",65), tc("STAGE","stage",65), tc("DRY","daysUnwatered",50));
        t.setOnMouseClicked(e -> { PlantRow s = t.getSelectionModel().getSelectedItem(); if (s != null) { Plant p = gardenGrid.getPlant(s.getRow(), s.getCol()); if (p != null) { selectedPlant = p; updatePlantDetailPanel(p); } } });
        Timeline ref = new Timeline(new KeyFrame(Duration.seconds(3), ev -> {
            ObservableList<PlantRow> rows = FXCollections.observableArrayList();
            for (Plant p : gardenGrid.getPlants()) { String ty = (p instanceof Tree) ? "Tree" : (p instanceof Flower) ? "Flower" : "Veg"; rows.add(new PlantRow(p.getName(), ty, p.getRow(), p.getCol(), p.getCurrentHealth(), p.getCurrentWater(), p.getGrowthStageDescription(), p.getConsecutiveDaysUnwatered())); }
            t.setItems(rows);
        })); ref.setCycleCount(Timeline.INDEFINITE); ref.play(); return t;
    }
    private <S,T> TableColumn<S,T> tc(String title, String prop, double w) { TableColumn<S,T> c = new TableColumn<>(title); c.setCellValueFactory(new PropertyValueFactory<>(prop)); c.setPrefWidth(w); return c; }

    public static class PlantRow {
        private final String name, type, position, stage; private final int health, water, row, col, daysUnwatered;
        public PlantRow(String n, String ty, int r, int c, int h, int w, String s, int d) { name=n; type=ty; row=r; col=c; position="("+r+","+c+")"; health=h; water=w; stage=s; daysUnwatered=d; }
        public String getName(){return name;} public String getType(){return type;} public String getPosition(){return position;}
        public int getHealth(){return health;} public int getWater(){return water;} public String getStage(){return stage;}
        public int getRow(){return row;} public int getCol(){return col;} public int getDaysUnwatered(){return daysUnwatered;}
    }

    // ── Logs Tab ──
    private Node buildLogsTab() {
        logItems = FXCollections.observableArrayList(); logListView = new ListView<>(logItems);
        logListView.setStyle("-fx-background-color: #e8dbb5; -fx-control-inner-background: #faf5e8; -fx-font-family: 'Verdana'; -fx-font-size: 11;");
        logListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); if (empty || item == null) { setText(null); setStyle("-fx-background-color: #faf5e8;"); }
                else { setText(item); String c = TEXT_ON_LIGHT; if (item.contains("[WARN]")) c = "#B45309"; else if (item.contains("[ERROR]")) c = "#B91C1C";
                    setStyle("-fx-background-color: #faf5e8; -fx-text-fill: " + c + ";"); }
            }
        }); return logListView;
    }
    private void addLogEntry(String level, String msg) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> { logItems.add(0, time + "  [" + level + "]  " + msg); if (logItems.size() > 500) logItems.remove(logItems.size() - 1); });
        GardenLog.Category cat = switch (level) { case "WARN" -> GardenLog.Category.WARNING; case "ERROR" -> GardenLog.Category.PLANT; default -> GardenLog.Category.STATE; };
        GardenLog.log(cat, msg);
    }

    // ── Toolbar ──
    private HBox buildToolbar() {
        toolbarBox = new HBox(8); toolbarBox.setPadding(new Insets(8, 16, 8, 16)); toolbarBox.setAlignment(Pos.CENTER_LEFT);
        toolbarBox.setStyle("-fx-background-color: #c4b088; -fx-border-color: #a08860; -fx-border-width: 1 0 0 0;");

        MenuButton addBtn = toolMenuBtn("🌱 Add Plant");
        Menu tm = new Menu("🌳 Trees"); for (Tree t : plantManager.getTrees()) { MenuItem mi = new MenuItem(t.getName()); mi.setOnAction(e -> enterPlantingMode(t.getName())); tm.getItems().add(mi); }
        Menu fm = new Menu("🌸 Flowers"); for (Flower f : plantManager.getFlowers()) { MenuItem mi = new MenuItem(f.getName()); mi.setOnAction(e -> enterPlantingMode(f.getName())); fm.getItems().add(mi); }
        Menu vm = new Menu("🥕 Vegetables"); for (Vegetable v : plantManager.getVegetables()) { MenuItem mi = new MenuItem(v.getName()); mi.setOnAction(e -> enterPlantingMode(v.getName())); vm.getItems().add(mi); }
        addBtn.getItems().addAll(tm, fm, vm);

        farmerToolbarIcon = new ImageView();
        farmerToolbarIcon.setFitWidth(32); farmerToolbarIcon.setFitHeight(32); farmerToolbarIcon.setPreserveRatio(true);
        farmerToolbarIcon.setVisible(false);
        try { farmerToolbarIcon.setImage(new Image(getClass().getResourceAsStream("/images/farmer_standing.png"))); } catch (Exception ignored) {}

        plantingModeLabel = new Label();
        plantingModeLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        plantingModeLabel.setTextFill(Color.web(SEC_TITLE));
        plantingModeLabel.setVisible(false);

        cancelPlantBtn = new Button("❌ Cancel");
        cancelPlantBtn.setStyle("-fx-background-color: " + ACCENT_RED + "; -fx-text-fill: white; -fx-font-family: 'Verdana'; -fx-font-size: 11; -fx-font-weight: bold; -fx-padding: 5 12; -fx-background-radius: 8; -fx-cursor: hand;");
        cancelPlantBtn.setOnAction(e -> exitPlantingMode());
        cancelPlantBtn.setVisible(false);

        toolbarBox.getChildren().addAll(addBtn, farmerToolbarIcon, plantingModeLabel, cancelPlantBtn); return toolbarBox;
    }
    private MenuButton toolMenuBtn(String t) { MenuButton b = new MenuButton(t); b.setStyle(toolStyle()); return b; }
    private Button toolBtn(String t) { Button b = new Button(t); String n = toolStyle(); b.setStyle(n); b.setOnMouseEntered(e -> b.setStyle(n.replace("transparent", BG_CARD))); b.setOnMouseExited(e -> b.setStyle(n)); return b; }
    private String toolStyle() { return "-fx-background-color: rgba(255,253,245,0.85); -fx-border-color: " + BORDER + "; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: " + SEC_TITLE + "; -fx-font-family: 'Verdana'; -fx-font-size: 12; -fx-font-weight: bold; -fx-padding: 5 12; -fx-cursor: hand;"; }

    // ── Left Sidebar (SUBSYSTEMS, GARDEN HEALTH, MANUAL OVERRIDE) ──
    private VBox buildLeftSidebar() {
        VBox left = new VBox(12); left.setPrefWidth(260); left.setMinWidth(260);
        left.setPadding(new Insets(12)); left.setStyle("-fx-background-color: linear-gradient(to bottom, #d4c49a, #c4b088); -fx-border-color: " + BORDER + "; -fx-border-width: 0 1 0 0;");
        ScrollPane sp = new ScrollPane(); sp.setFitToWidth(true); sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox content = new VBox(12); content.setPadding(new Insets(0));
        content.getChildren().addAll(buildSystemsStatus(), buildHealthSummary(), buildManualOverridePanel(), buildShopPanel());
        sp.setContent(content); VBox.setVgrow(sp, Priority.ALWAYS); left.getChildren().add(sp); return left;
    }

    private VBox buildManualOverridePanel() {
        VBox b = card(); b.setAlignment(Pos.TOP_CENTER);
        Label sec = secLabel("MANUAL OVERRIDE"); b.getChildren().add(sec);
        Button waterBtn = overrideBtn("💧 Manually Water Now", ACCENT_BLUE); waterBtn.setOnAction(e -> onManualWater());
        Button heatBtn = overrideBtn("🔥 Heat Boost", ACCENT_RED); heatBtn.setOnAction(e -> onManualHeater());
        Button pestBtn = overrideBtn("🧪 Pesticide", ACCENT_PURPLE); pestBtn.setOnAction(e -> onManualPesticide());

        // Sprinkler toggle with YES/NO image icons
        boolean initiallyOff = stateManager.getManualSprinklerOff();
        ImageView toggleIcon = new ImageView();
        toggleIcon.setFitWidth(32); toggleIcon.setFitHeight(32); toggleIcon.setPreserveRatio(true);
        try { toggleIcon.setImage(new Image(getClass().getResourceAsStream(initiallyOff ? "/images/manual_override_no.png" : "/images/manual_override_yes.png"))); } catch (Exception ignored) {}

        sprinklerToggleBtn = new Button(initiallyOff ? "Watering OFF" : "Watering ON", toggleIcon);
        sprinklerToggleBtn.setMaxWidth(Double.MAX_VALUE);
        sprinklerToggleBtn.setStyle(overrideBtnStyle(initiallyOff ? ACCENT_RED : ACCENT_GREEN));
        sprinklerToggleBtn.setCursor(javafx.scene.Cursor.HAND);
        sprinklerToggleBtn.setContentDisplay(ContentDisplay.LEFT);
        sprinklerToggleBtn.setOnAction(e -> {
            boolean nowOff = !stateManager.getManualSprinklerOff();
            stateManager.setManualSprinklerOff(nowOff);
            sprinklerToggleBtn.setText(nowOff ? "Watering OFF" : "Watering ON");
            sprinklerToggleBtn.setStyle(overrideBtnStyle(nowOff ? ACCENT_RED : ACCENT_GREEN));
            try { toggleIcon.setImage(new Image(getClass().getResourceAsStream(nowOff ? "/images/manual_override_no.png" : "/images/manual_override_yes.png"))); } catch (Exception ignored) {}
            updSys(sprinklerStatusLabel, nowOff ? "OFF" : "ON", nowOff ? ACCENT_RED : ACCENT_GREEN);
            if (sprinklerStatusIcon != null) sprinklerStatusIcon.setVisible(false);
            addLogEntry("WARN", nowOff ? "⚡ Watering disabled - plants may dehydrate" : "⚡ Watering re-enabled");
        });
        b.getChildren().addAll(waterBtn, heatBtn, pestBtn, sprinklerToggleBtn);
        return b;
    }
    private Button overrideBtn(String t, String c) { Button b = new Button(t); b.setMaxWidth(Double.MAX_VALUE); b.setStyle(overrideBtnStyle(c)); b.setCursor(javafx.scene.Cursor.HAND); return b; }
    private String overrideBtnStyle(String c) { return "-fx-background-color: " + c + "; -fx-text-fill: white; -fx-font-family: 'Verdana'; -fx-font-size: 12; -fx-font-weight: bold; -fx-padding: 10 14; -fx-background-radius: 10;"; }

    // ── Right Panel (PLANT DETAIL + SCARECROW corner) ──
    private VBox buildRightPanel() {
        VBox r = new VBox(0); r.setPrefWidth(260); r.setMinWidth(260);
        r.setStyle("-fx-background-color: linear-gradient(to bottom, #d4c49a, #c4b088); -fx-border-color: " + BORDER + "; -fx-border-width: 0 0 0 1;");
        ScrollPane sp = new ScrollPane(); sp.setFitToWidth(true); sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox content = new VBox(12); content.setPadding(new Insets(12));
        plantDetailBox = buildDetailPlaceholder();
        content.getChildren().addAll(plantDetailBox, buildWeatherWidget());
        // Scarecrow display: right bottom corner (not on grid)
        scarecrowContainer = new StackPane();
        scarecrowContainer.setMinHeight(80); scarecrowContainer.setPrefHeight(80);
        scarecrowContainer.setAlignment(Pos.BOTTOM_RIGHT);
        scarecrowContainer.setPadding(new Insets(8));
        VBox.setMargin(scarecrowContainer, new Insets(12, 0, 0, 0));
        content.getChildren().add(scarecrowContainer);
        sp.setContent(content); VBox.setVgrow(sp, Priority.ALWAYS); r.getChildren().add(sp); return r;
    }

    // ─── Plant Detail ───
    private VBox buildDetailPlaceholder() {
        VBox b = card(); b.setAlignment(Pos.TOP_CENTER);
        b.getChildren().addAll(secLabel("PLANT DETAIL"), dim("Click a plant to view details")); return b;
    }

    private void updatePlantDetailPanel(Plant p) {
        Platform.runLater(() -> {
            plantDetailBox.getChildren().clear();
            VBox id = new VBox(4); id.setAlignment(Pos.CENTER); id.setPadding(new Insets(8));
            id.setStyle("-fx-background-color: rgba(240,230,200,0.6); -fx-background-radius: 8;");
            try { ImageView iv = new ImageView(new Image(getClass().getResourceAsStream("/images/" + p.getCurrentImage()))); iv.setFitWidth(48); iv.setFitHeight(48); id.getChildren().add(iv); } catch (Exception ignored) {}
            Label nm = new Label(p.getName()); nm.setFont(Font.font("Verdana", FontWeight.BOLD, 16)); nm.setTextFill(Color.web(SEC_TITLE));
            String ty = (p instanceof Tree) ? "Tree" : (p instanceof Flower) ? "Flower" : "Vegetable";
            Label pos = new Label("(" + p.getRow() + ", " + p.getCol() + ")"); pos.setFont(Font.font("Verdana", 11)); pos.setTextFill(Color.web(SEC_TITLE));
            id.getChildren().addAll(nm, dim(ty), pos);

            VBox hp = statBar("Health", p.getCurrentHealth(), p.getHealthFull(), hpColor(p.getCurrentHealth(), p.getHealthFull()));
            VBox wp = statBar("Water", p.getCurrentWater(), p.getMaxWaterCapacity(), ACCENT_BLUE);

            int dry = p.getConsecutiveDaysUnwatered(), thresh = p.getDehydrationThresholdDays();
            Label dryL = new Label("Dry streak: " + dry + "/" + thresh + " days"); dryL.setFont(Font.font("Verdana", 11)); dryL.setTextFill(Color.web(SEC_TITLE));
            Label ddL = dim("Dehydration dmg: " + p.getDehydrationDamagePerDay() + "/day over threshold");
            Label stL = dim("Stage: " + p.getGrowthStageDescription());
            Label waL = new Label("Watered: " + (p.getIsWatered() ? "✅ Yes" : "❌ No")); waL.setFont(Font.font("Verdana", 11)); waL.setTextFill(Color.web(SEC_TITLE));
            Label tpL = dim("Ideal temp: " + p.getTemperatureRequirement() + "°F");
            String vulns = p.getVulnerableTo() != null ? String.join(", ", p.getVulnerableTo()) : "None";
            Label vuL = new Label("Vulnerable to: " + vulns); vuL.setFont(Font.font("Verdana", 10)); vuL.setTextFill(Color.web(SEC_TITLE)); vuL.setWrapText(true);

            VBox acts = new VBox(4);
            Button wBtn = actBtn("💧 Water Now", ACCENT_BLUE); wBtn.setOnAction(e -> { p.addWater(10); updatePlantDetailPanel(p); addLogEntry("INFO", "💧 Watered " + p.getName()); });
            Button hBtn = actBtn("💚 Heal +10", ACCENT_GREEN); hBtn.setOnAction(e -> { p.healPlant(10); updatePlantDetailPanel(p); addLogEntry("INFO", "💚 Healed " + p.getName()); });
            Button closeBtn = actBtn("✕ Close", TEXT_DIM); closeBtn.setOnAction(e -> { selectedPlant = null; plantDetailBox.getChildren().clear(); plantDetailBox.getChildren().addAll(secLabel("PLANT DETAIL"), dim("Click a plant to view details")); });
            acts.getChildren().addAll(wBtn, hBtn, closeBtn);

            plantDetailBox.getChildren().addAll(secLabel("PLANT DETAIL"), id, hp, wp, dryL, ddL, stL, waL, tpL, vuL, acts);
        });
    }

    private VBox statBar(String label, int cur, int max, String color) {
        VBox b = new VBox(2); b.setPadding(new Insets(4, 0, 4, 0)); double pct = max > 0 ? (double) cur / max : 0;
        HBox hd = new HBox(); hd.setAlignment(Pos.CENTER_LEFT); Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label v = new Label(cur + "/" + max + " (" + (int)(pct*100) + "%)"); v.setFont(Font.font("Verdana", FontWeight.BOLD, 10)); v.setTextFill(Color.web(SEC_TITLE));
        hd.getChildren().addAll(dim(label), sp, v);
        ProgressBar pb = new ProgressBar(pct); pb.setPrefWidth(220); pb.setPrefHeight(8);
        pb.setStyle("-fx-accent: " + color + "; -fx-control-inner-background: #e8dbb5;");
        b.getChildren().addAll(hd, pb); return b;
    }
    private String hpColor(int h, int max) { double p = max > 0 ? (double) h / max : 0; return p > 0.6 ? ACCENT_GREEN : p > 0.3 ? ACCENT_YELLOW : ACCENT_RED; }
    private Button actBtn(String t, String c) { Button b = new Button(t); b.setMaxWidth(Double.MAX_VALUE); b.setStyle("-fx-background-color: " + c + "20; -fx-border-color: " + c + "50; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: " + c + "; -fx-font-family: 'Verdana'; -fx-font-size: 11; -fx-padding: 6; -fx-cursor: hand;"); return b; }

    // ─── Weather ───
    private VBox buildWeatherWidget() {
        VBox b = new VBox(4); b.setPadding(new Insets(12));
        b.setStyle("-fx-background-color: " + BG_CARD + "; -fx-background-radius: 12; -fx-border-color: " + BORDER + "; -fx-border-radius: 12; -fx-border-width: 1;");
        weatherIconLabel = new Label("☀️ Sunny"); weatherIconLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 20)); weatherIconLabel.setTextFill(Color.web(SEC_TITLE));
        weatherTempLabel = new Label("W55°F / G65°F"); weatherTempLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 18)); weatherTempLabel.setTextFill(Color.web(ACCENT_ORANGE));
        weatherHumidityLabel = dim("");
        b.getChildren().addAll(secLabel("WEATHER"), weatherIconLabel, weatherTempLabel, weatherHumidityLabel); return b;
    }

    // ─── Systems ───
    // Sprinkler status image in subsystems panel
    private ImageView sprinklerStatusIcon;

    private VBox buildSystemsStatus() {
        VBox b = card(); b.setAlignment(Pos.TOP_LEFT);

        boolean wOff = stateManager.getManualSprinklerOff();
        sprinklerStatusLabel = sysPill(wOff ? "OFF" : "ON", wOff ? ACCENT_RED : ACCENT_GREEN);
        sprinklerStatusIcon = new ImageView();
        sprinklerStatusIcon.setFitWidth(24); sprinklerStatusIcon.setFitHeight(24); sprinklerStatusIcon.setPreserveRatio(true);
        sprinklerStatusIcon.setVisible(false);
        try { sprinklerStatusIcon.setImage(new Image(getClass().getResourceAsStream("/images/sprinkler_on.png"))); } catch (Exception ignored) {}
        HBox waterPillRow = new HBox(6); waterPillRow.setAlignment(Pos.CENTER_LEFT);
        waterPillRow.getChildren().addAll(sprinklerStatusIcon, sprinklerStatusLabel);
        Label waterName = sysLabel("💧 Watering"); waterName.setTextFill(Color.web(ACCENT_BLUE));
        VBox wateringBlock = new VBox(3); wateringBlock.setPadding(new Insets(6, 0, 6, 0));
        wateringBlock.setStyle("-fx-border-color: " + BORDER + "; -fx-border-width: 0 0 1 0;");
        wateringBlock.getChildren().addAll(waterName, waterPillRow);

        heatingStatusLabel = sysPill("MONITORING", ACCENT_ORANGE);
        VBox heatingBlock = sysBlock("🌡️ Temperature", heatingStatusLabel);

        pesticideNameLabel = sysLabel("🧪 Pesticide");
        pesticideStatusLabel = sysPill("PROTECTED", ACCENT_GREEN);
        VBox pesticideBlock = new VBox(3);
        pesticideBlock.setPadding(new Insets(6, 0, 6, 0));
        pesticideBlock.setStyle("-fx-border-color: " + BORDER + "; -fx-border-width: 0 0 1 0;");
        pesticideBlock.getChildren().addAll(pesticideNameLabel, pesticideStatusLabel);

        b.getChildren().addAll(secLabel("SUBSYSTEMS"), wateringBlock, heatingBlock, pesticideBlock); return b;
    }
    private Label sysLabel(String t) { Label l = new Label(t); l.setFont(Font.font("Verdana", FontWeight.BOLD, 12)); l.setTextFill(Color.web(SEC_TITLE)); return l; }
    private Label sysPill(String s, String c) {
        Label l = new Label(s); l.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        l.setTextFill(Color.WHITE); l.setMaxWidth(Double.MAX_VALUE); l.setAlignment(Pos.CENTER);
        l.setStyle("-fx-background-color: " + c + "; -fx-background-radius: 10; -fx-padding: 5 12 5 12;");
        return l;
    }
    private VBox sysBlock(String name, javafx.scene.Node pillNode) {
        VBox v = new VBox(3); v.setPadding(new Insets(6, 0, 6, 0));
        v.setStyle("-fx-border-color: " + BORDER + "; -fx-border-width: 0 0 1 0;");
        v.getChildren().addAll(sysLabel(name), pillNode); return v;
    }
    private void updSys(Label l, String s, String c) {
        if (l == null) return;
        Platform.runLater(() -> { if (l == null) return; l.setText(s); l.setTextFill(Color.WHITE); l.setStyle("-fx-background-color: " + c + "; -fx-background-radius: 10; -fx-padding: 5 12 5 12;"); });
    }
    private void updPesticide(String statusText, String statusColor) {
        String nameColor = pesticideScheduleActive ? ACCENT_GREEN : ACCENT_RED;
        Platform.runLater(() -> {
            if (pesticideNameLabel != null) pesticideNameLabel.setTextFill(Color.web(nameColor));
            if (pesticideStatusLabel != null) {
                pesticideStatusLabel.setText(statusText);
                pesticideStatusLabel.setTextFill(Color.WHITE);
                pesticideStatusLabel.setStyle("-fx-background-color: " + statusColor + "; -fx-background-radius: 10; -fx-padding: 5 12 5 12;");
            }
        });
    }

    // ─── Farmer Shop Panel ───
    private VBox buildShopPanel() {
        VBox b = card(); b.setAlignment(Pos.TOP_CENTER);
        shopContainer = new StackPane();
        shopContainer.setMinHeight(90); shopContainer.setPrefHeight(90);
        shopContainer.setAlignment(Pos.CENTER);
        try {
            ImageView iv = new ImageView(new Image(getClass().getResourceAsStream("/images/farmer_shop.png")));
            iv.setFitWidth(80); iv.setFitHeight(80); iv.setPreserveRatio(true);
            iv.setEffect(new DropShadow(8, Color.rgb(0, 0, 0, 0.5)));
            shopContainer.getChildren().add(iv);
        } catch (Exception ignored) {}
        shopCountLabel = new Label("Shop: 0/10 items");
        shopCountLabel.setFont(Font.font("Verdana", FontWeight.SEMI_BOLD, 11));
        shopCountLabel.setTextFill(Color.web(SEC_TITLE));
        b.getChildren().addAll(secLabel("FARMER'S SHOP"), shopContainer, shopCountLabel);
        return b;
    }

    // ─── Health Summary ───
    private VBox buildHealthSummary() {
        VBox b = card(); b.setAlignment(Pos.CENTER);
        avgHealthLabel = new Label("—%"); avgHealthLabel.setFont(Font.font("Verdana", FontWeight.EXTRA_BOLD, 36)); avgHealthLabel.setTextFill(Color.web(ACCENT_ORANGE));
        HBox cn = new HBox(8); cn.setAlignment(Pos.CENTER);
        healthyCountLabel = tiny("0 ok", SEC_TITLE); stressedCountLabel = tiny("0 stress", SEC_TITLE); criticalCountLabel = tiny("0 crit", SEC_TITLE);
        cn.getChildren().addAll(healthyCountLabel, stressedCountLabel, criticalCountLabel);
        b.getChildren().addAll(secLabel("GARDEN HEALTH"), avgHealthLabel, cn);
        Timeline t = new Timeline(new KeyFrame(Duration.seconds(5), ev -> refreshHealth())); t.setCycleCount(Timeline.INDEFINITE); t.play(); return b;
    }

    private void refreshHealth() {
        Platform.runLater(() -> {
            if (headerPlantCountLabel == null || avgHealthLabel == null) return;
            ArrayList<Plant> plants = gardenGrid.getPlants(); int cnt = plants.size();
            updateHeaderStat(headerPlantCountLabel, String.valueOf(cnt));
            if (cnt == 0) { avgHealthLabel.setText("—%"); healthyCountLabel.setText("0 ok"); stressedCountLabel.setText("0 stress"); criticalCountLabel.setText("0 crit"); return; }
            int tot = 0; int h = 0, s = 0, c = 0;
            for (Plant p : plants) { double pct = p.getHealthFull() > 0 ? (double) p.getCurrentHealth() / p.getHealthFull() : 0; tot += (int)(pct*100); if (pct > 0.6) h++; else if (pct > 0.3) s++; else c++; }
            int avg = tot / cnt; avgHealthLabel.setText(avg + "%"); avgHealthLabel.setTextFill(Color.web(ACCENT_ORANGE));
            healthyCountLabel.setText(h + " ok"); stressedCountLabel.setText(s + " stress"); criticalCountLabel.setText(c + " crit");
            if (stateManager.getSprinklerFailureToday()) updSys(sprinklerStatusLabel, "⚠️ FAILURE", ACCENT_RED);
            else if (stateManager.getManualSprinklerOff()) updSys(sprinklerStatusLabel, "OFF", ACCENT_RED);
            else updSys(sprinklerStatusLabel, "ON", ACCENT_GREEN);
            if (stateManager.getFrostOccurred()) { weatherHumidityLabel.setText("⚠️ Frost warning"); weatherHumidityLabel.setTextFill(Color.web(ACCENT_BLUE)); } else weatherHumidityLabel.setText("");
        });
    }

    // ─── Helpers ───
    private VBox card() { VBox b = new VBox(8); b.setPadding(new Insets(12)); b.setStyle("-fx-background-color: " + BG_CARD + "; -fx-background-radius: 12; -fx-border-color: " + BORDER + "; -fx-border-radius: 12; -fx-border-width: 1;"); return b; }
    private Label secLabel(String t) { Label l = new Label(t); l.setFont(Font.font("Verdana", FontWeight.BOLD, 14)); l.setTextFill(Color.web(SEC_TITLE)); l.setPadding(new Insets(0,0,6,0)); return l; }
    private Label dim(String t) { Label l = new Label(t); l.setFont(Font.font("Verdana", 11)); l.setTextFill(Color.web(SEC_TITLE)); return l; }
    private Label tiny(String t, String c) { Label l = new Label(t); l.setFont(Font.font("Verdana", 10)); l.setTextFill(Color.web(c)); return l; }

    // ════════════════════════════════════════════
    //  EVENT SUBSCRIPTIONS
    // ════════════════════════════════════════════

    private void subscribeToEvents() {
        EventBus.subscribe("RainEvent", ev -> handleRain((RainEvent) ev));
        EventBus.subscribe("GreenhouseTempUpdateEvent", ev -> handleGreenhouseTempUpdate((GreenhouseTempUpdateEvent) ev));
        EventBus.subscribe("ParasiteEvent", ev -> handleParasite((ParasiteEvent) ev));
        EventBus.subscribe("ParasiteDisplayEvent", ev -> handleDisplayParasite((ParasiteDisplayEvent) ev));
        EventBus.subscribe("PlantImageUpdateEvent", ev -> handleImgUpdate((PlantImageUpdateEvent) ev));
        EventBus.subscribe("DayUpdateEvent", ev -> handleDayChange((DayUpdateEvent) ev));
        EventBus.subscribe("SprinklerEvent", ev -> handleSprinkler((SprinklerEvent) ev));
        EventBus.subscribe("SprinklerRepairEvent", ev -> {
            int cost = (int) ev;
            addLogEntry("WARN", "🔧 Sprinkler auto-repaired — $" + cost + " deducted");
            Platform.runLater(() -> {
                updSys(sprinklerStatusLabel, "REPAIRED", ACCENT_ORANGE);
                updateHeaderStat(headerMoneyLabel, "$" + FarmerShop.getInstance().getTotalMoney());
                PauseTransition p = new PauseTransition(Duration.seconds(5));
                p.setOnFinished(e -> updSys(sprinklerStatusLabel, stateManager.getManualSprinklerOff() ? "OFF" : "ON", stateManager.getManualSprinklerOff() ? ACCENT_RED : ACCENT_GREEN));
                p.play();
            });
        });
        EventBus.subscribe("CoolTemperatureEvent", ev -> { CoolTemperatureEvent e = (CoolTemperatureEvent) ev; addLogEntry("INFO", "💨 Cooling at (" + e.getRow() + "," + e.getCol() + ") +" + e.getTempDiff() + "°F over"); showCellIcon(e.getRow(), e.getCol(), "💨", 5, 12, Pos.BOTTOM_LEFT, "heat_cool"); });
        EventBus.subscribe("HeatTemperatureEvent", ev -> { HeatTemperatureEvent e = (HeatTemperatureEvent) ev; addLogEntry("INFO", "🔥 Heater at (" + e.getRow() + "," + e.getCol() + ")"); showCellIcon(e.getRow(), e.getCol(), "🔥", 5, 12, Pos.BOTTOM_LEFT, "heat_cool"); });
        EventBus.subscribe("ParasiteDamageEvent", ev -> { ParasiteDamageEvent e = (ParasiteDamageEvent) ev; addLogEntry("WARN", "💀 -" + e.getDamage() + " at (" + e.getRow() + "," + e.getCol() + ")"); Platform.runLater(() -> showDmg(e.getRow(), e.getCol(), e.getDamage())); });
        EventBus.subscribe("InitializeGarden", ev -> handleInit());
        EventBus.subscribe("PlantHealthUpdateEvent", ev -> { PlantHealthUpdateEvent e = (PlantHealthUpdateEvent) ev; if (selectedPlant != null && selectedPlant.getRow() == e.getRow() && selectedPlant.getCol() == e.getCol()) updatePlantDetailPanel(selectedPlant); });
        EventBus.subscribe("PlantDeathUIChangeEvent", ev -> handleDeath((Plant) ev));
        EventBus.subscribe("PesticideApplicationEvent", ev -> { if (ev instanceof PesticideApplicationEvent pe) { showCellIcon(pe.getRow(), pe.getCol(), "🧪", 5, 12, Pos.TOP_RIGHT, "pesticide"); addLogEntry("INFO", "🧪 Pesticide round at (" + pe.getRow() + "," + pe.getCol() + ")"); } });
        EventBus.subscribe("ManualOverrideEvent", ev -> { if (ev instanceof ManualOverrideEvent moe) addLogEntry("WARN", "⚡ Override: " + moe.getType() + " val=" + moe.getValue()); });

        EventBus.subscribe("PesticideScheduleEvent", ev -> {
            String status = (String) ev;
            switch (status) {
                case "APPLIED" -> {
                    pesticideScheduleActive = true;
                    addLogEntry("INFO", "🧪 Scheduled pesticide applied — plants protected for 2.5 days");
                    updPesticide("PROTECTED", ACCENT_GREEN);
                    showPesticideOnAllPlants();
                }
                case "EXPIRED" -> {
                    pesticideScheduleActive = false;
                    addLogEntry("WARN", "⚠️ Pesticide expired — plants vulnerable to extra damage!");
                    updPesticide("EXPIRED ⚠", ACCENT_RED);
                }
                case "MANUAL" -> {
                    pesticideScheduleActive = true;
                    addLogEntry("INFO", "🧪 Manual pesticide — protection active, next cycle delayed 5h");
                    updPesticide("PROTECTED (manual)", ACCENT_GREEN);
                    showPesticideOnAllPlants();
                }
            }
        });

        // Scarecrow: crow-specific — wait for fly-in, then fly crow away + show scarecrow
        EventBus.subscribe("ScarecrowEvent", ev -> {
            if (ev instanceof PesticideApplicationEvent pe) {
                addLogEntry("INFO", "🧙‍♂️ Scarecrow deployed — crow scared away!");
                Platform.runLater(() -> {
                    PauseTransition scarecrowDelay = new PauseTransition(Duration.seconds(3));
                    scarecrowDelay.setOnFinished(e -> showScarecrowInCorner());
                    scarecrowDelay.play();
                    PauseTransition crowDelay = new PauseTransition(Duration.seconds(5));
                    crowDelay.setOnFinished(e -> showCrowFlyAway(pe.getRow(), pe.getCol()));
                    crowDelay.play();
                });
            }
        });

        // Harvest: plant fully grown — show +1 animation, clear cell, update shop/money
        EventBus.subscribe("HarvestEvent", ev -> handleHarvestUI((Plant) ev));
        EventBus.subscribe("ShopSaleEvent", ev -> handleShopSale((int) ev));

        // Pest killed: green tick replaces the pest in the same corner (TOP_LEFT)
        EventBus.subscribe("PestKilledEvent", ev -> {
            if (ev instanceof PesticideApplicationEvent pe) {
                addLogEntry("INFO", "✅ Pest eliminated at (" + pe.getRow() + "," + pe.getCol() + ") — plant recovering!");
                Platform.runLater(() -> { showCellIconReplacing(pe.getRow(), pe.getCol(), "✅", 6, 20, Pos.TOP_LEFT, "pestkilled", "pest"); hlCell(pe.getRow(), pe.getCol(), ACCENT_GREEN, 6); });
            }
        });
    }

    // ════════════════════════════════════════════
    //  EVENT HANDLERS
    // ════════════════════════════════════════════

    private void handleDayChange(DayUpdateEvent ev) {
        logDay = ev.getDay(); addLogEntry("INFO", "📅 Day " + logDay); updateHeaderStat(headerDayLabel, logDay + "/24");
        if (selectedPlant != null) updatePlantDetailPanel(selectedPlant);
    }

    private void handleRain(RainEvent ev) {
        addLogEntry("INFO", "🌧️ Rain " + ev.getAmount() + " units"); stateManager.setRainedToday(true);
        Platform.runLater(() -> {
            weatherIconLabel.setText("🌧️ Rain"); updateHeaderStat(headerWeatherLabel, "🌧️ Rain " + ev.getAmount() + " units");
            updSys(sprinklerStatusLabel, "PAUSED (rain)", ACCENT_YELLOW); startRain();
            PauseTransition p = new PauseTransition(Duration.seconds(5));
            p.setOnFinished(e -> { stopRain(); syncWeatherWithTemp(); updSys(sprinklerStatusLabel, stateManager.getManualSprinklerOff() ? "OFF" : "ON", stateManager.getManualSprinklerOff() ? ACCENT_RED : ACCENT_GREEN); });
            p.play();
        });
    }

    private void handleGreenhouseTempUpdate(GreenhouseTempUpdateEvent ev) {
        int weatherTemp = ev.getWeatherTemp();
        int greenhouseTemp = ev.getGreenhouseTemp();
        currentTemperature = greenhouseTemp;
        if (greenhouseTemp < 46) stateManager.setFrostOccurred(true); else stateManager.setFrostOccurred(false);
        String c, hs;
        if (greenhouseTemp <= 50) { c = ACCENT_BLUE; hs = "ON"; addLogEntry("WARN", "🌡️ Cold greenhouse " + greenhouseTemp + "°F — heater ON"); }
        else if (greenhouseTemp >= 90) { c = ACCENT_RED; hs = "COOLING"; addLogEntry("WARN", "🌡️ Hot greenhouse " + greenhouseTemp + "°F"); }
        else if (greenhouseTemp >= 60) { c = ACCENT_ORANGE; hs = "COOLING"; addLogEntry("INFO", "🌡️ Warm greenhouse " + greenhouseTemp + "°F"); }
        else { c = ACCENT_GREEN; hs = "MONITORING"; addLogEntry("INFO", "🌡️ Greenhouse " + greenhouseTemp + "°F — optimal"); }
        Platform.runLater(() -> {
            weatherTempLabel.setText("W" + weatherTemp + "°F / G" + greenhouseTemp + "°F"); weatherTempLabel.setTextFill(Color.web(c));
            updateHeaderStat(headerTempLabel, "W" + weatherTemp + "°F / G" + greenhouseTemp + "°F");
            updSys(heatingStatusLabel, hs, greenhouseTemp <= 50 ? ACCENT_ORANGE : TEXT_DIM);
            syncWeatherWithTemp();
        });
        Platform.runLater(() -> {
            PauseTransition r = new PauseTransition(Duration.seconds(5)); r.setOnFinished(e -> updSys(heatingStatusLabel, "MONITORING", TEXT_DIM)); r.play();
        });
    }

    /** Sync weather icon and header with current temperature. Preserves rain display if it rained today. */
    private void syncWeatherWithTemp() {
        if (stateManager.getRainedToday()) {
            if (weatherIconLabel != null) weatherIconLabel.setText("🌧️ Rain");
            updateHeaderStat(headerWeatherLabel, "🌧️ Rain " + currentTemperature + "°F");
            return;
        }
        String icon, header;
        if (currentTemperature < 46) { icon = "❄️ Frost"; header = "❄️ Frost " + currentTemperature + "°F"; }
        else if (currentTemperature >= 90) { icon = "🌡️ Hot"; header = "🌡️ Hot " + currentTemperature + "°F"; }
        else if (currentTemperature >= 60) { icon = "🌤️ Warm"; header = "🌤️ " + currentTemperature + "°F"; }
        else { icon = "☀️ Sunny"; header = "☀️ " + currentTemperature + "°F"; }
        if (weatherIconLabel != null) weatherIconLabel.setText(icon);
        updateHeaderStat(headerWeatherLabel, header);
    }

    private void handleParasite(ParasiteEvent ev) {
        addLogEntry("WARN", "🐛 " + ev.getParasite().getName() + " detected!"); updPesticide("FIGHTING", ACCENT_PURPLE);
        Platform.runLater(() -> {
            PauseTransition p = new PauseTransition(Duration.seconds(5)); p.setOnFinished(e -> restorePesticideScheduleLabel()); p.play();
        });
    }

    private void restorePesticideScheduleLabel() {
        if (pesticideScheduleActive) updPesticide("PROTECTED", ACCENT_GREEN);
        else updPesticide("EXPIRED ⚠", ACCENT_RED);
    }

    private static final java.util.Map<String, String> PARASITE_EMOJI = java.util.Map.of(
        "Rat", "🐀", "Crow", "🐦‍⬛", "Locust", "🦗", "Aphids", "🐛", "Slugs", "🐌");
    private void handleDisplayParasite(ParasiteDisplayEvent ev) {
        String name = ev.getParasite().getName();
        String emoji = PARASITE_EMOJI.getOrDefault(name, "🐛");
        int row = ev.getRow(), col = ev.getColumn();
        addLogEntry("INFO", emoji + " " + name + " at (" + row + "," + col + ")");
        if (name.equals("Crow")) {
            Platform.runLater(() -> { showCrowFlyIn(row, col); hlCell(row, col, ACCENT_RED, 10); });
        } else {
            Platform.runLater(() -> { showCellIcon(row, col, emoji, 10, 28, Pos.TOP_LEFT, "pest"); hlCell(row, col, ACCENT_RED, 10); });
        }
    }

    private void showCrowFlyIn(int row, int col) {
        if (gardenGridPane == null) return;
        for (Node n : gardenGridPane.getChildren()) {
            Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
            if (r != null && c != null && r == row && c == col && n instanceof StackPane cell) {
                removeOverlayByTag(cell, "crow_fly");
                Label crow = new Label("🐦");
                crow.setUserData("crow_fly");
                crow.setFont(Font.font("System", FontWeight.BOLD, 30));
                crow.setStyle("-fx-text-fill: #1a1a1a; -fx-background-color: rgba(255,255,200,0.95); -fx-background-radius: 6; -fx-padding: 4 6;");
                crow.setEffect(new DropShadow(6, Color.BLACK));
                StackPane.setAlignment(crow, Pos.TOP_LEFT);
                StackPane.setMargin(crow, new Insets(4));
                crow.setScaleX(0.3); crow.setScaleY(0.3);
                crow.setTranslateY(-20); crow.setTranslateX(-20);
                cell.getChildren().add(crow);
                crow.toFront();

                ScaleTransition scale = new ScaleTransition(Duration.millis(500), crow);
                scale.setToX(1.0); scale.setToY(1.0);
                TranslateTransition slideY = new TranslateTransition(Duration.millis(500), crow);
                slideY.setToY(0);
                TranslateTransition slideX = new TranslateTransition(Duration.millis(500), crow);
                slideX.setToX(0);
                ParallelTransition flyIn = new ParallelTransition(scale, slideY, slideX);
                flyIn.play();
                return;
            }
        }
    }

    private void showCrowFlyAway(int row, int col) {
        Platform.runLater(() -> {
            if (gardenGridPane == null) return;
            for (Node n : gardenGridPane.getChildren()) {
                Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
                if (r != null && c != null && r == row && c == col && n instanceof StackPane cell) {
                    for (Node child : cell.getChildren()) {
                        if ("crow_fly".equals(child.getUserData())) {
                            Node crowRef = child;
                            FadeTransition fade = new FadeTransition(Duration.millis(300), child);
                            fade.setToValue(0);
                            fade.setOnFinished(e -> cell.getChildren().remove(crowRef));
                            fade.play();
                            return;
                        }
                    }
                    return;
                }
            }
        });
    }

    private void handleSprinkler(SprinklerEvent ev) {
        if (stateManager.getManualSprinklerOff()) return;
        addLogEntry("INFO", "🚿 Watering (" + ev.getRow() + "," + ev.getCol() + ")"); updSys(sprinklerStatusLabel, "ACTIVE", ACCENT_BLUE);
        showCellImage(ev.getRow(), ev.getCol(), "/images/sprinkler_on.png", 4, 40, Pos.BOTTOM_RIGHT, "sprinkler");
        Platform.runLater(() -> {
            sprinklerStatusIcon.setVisible(true);
            PauseTransition p = new PauseTransition(Duration.seconds(4));
            p.setOnFinished(e -> {
                updSys(sprinklerStatusLabel, stateManager.getManualSprinklerOff() ? "OFF" : "ON", stateManager.getManualSprinklerOff() ? ACCENT_RED : ACCENT_GREEN);
                sprinklerStatusIcon.setVisible(false);
            });
            p.play();
        });
    }

    private void handleImgUpdate(PlantImageUpdateEvent ev) {
        Plant p = ev.getPlant(); Platform.runLater(() -> { refreshGrid(p);
            if (selectedPlant != null && selectedPlant.getRow() == p.getRow() && selectedPlant.getCol() == p.getCol()) updatePlantDetailPanel(p); });
    }

    private void handleDeath(Plant p) {
        addLogEntry("ERROR", "🪦 " + p.getName() + " died at (" + p.getRow() + "," + p.getCol() + ")");
        Platform.runLater(() -> { clearCell(p.getRow(), p.getCol());
            if (selectedPlant != null && selectedPlant.getRow() == p.getRow() && selectedPlant.getCol() == p.getCol()) { selectedPlant = null; plantDetailBox.getChildren().clear(); plantDetailBox.getChildren().addAll(secLabel("PLANT DETAIL"), dim("Plant died. Select another.")); } });
    }

    private void handleHarvestUI(Plant p) {
        int value = FarmerShop.getValueForPlant(p);
        addLogEntry("INFO", "🌾 Harvested " + p.getName() + " ($" + value + ") at (" + p.getRow() + "," + p.getCol() + ")");
        Platform.runLater(() -> {
            showHarvestAnimation(p.getRow(), p.getCol(), value);
            PauseTransition delay = new PauseTransition(Duration.millis(800));
            delay.setOnFinished(e -> clearCell(p.getRow(), p.getCol()));
            delay.play();
            if (selectedPlant != null && selectedPlant.getRow() == p.getRow() && selectedPlant.getCol() == p.getCol()) {
                selectedPlant = null; plantDetailBox.getChildren().clear();
                plantDetailBox.getChildren().addAll(secLabel("PLANT DETAIL"), dim("Harvested! Select another."));
            }
            FarmerShop shop = FarmerShop.getInstance();
            if (shopCountLabel != null) shopCountLabel.setText("Shop: " + shop.getInventoryCount() + "/10 items");
            updateHeaderStat(headerMoneyLabel, "$" + shop.getTotalMoney());
        });
    }

    private void handleShopSale(int saleTotal) {
        addLogEntry("INFO", "💰 Sold 10 items for $" + saleTotal + " — Total: $" + FarmerShop.getInstance().getTotalMoney());
        Platform.runLater(() -> {
            FarmerShop shop = FarmerShop.getInstance();
            updateHeaderStat(headerMoneyLabel, "$" + shop.getTotalMoney());
            if (shopCountLabel != null) shopCountLabel.setText("Shop: " + shop.getInventoryCount() + "/10 items");
            showSaleFlash();
        });
    }

    private void showHarvestAnimation(int row, int col, int value) {
        Platform.runLater(() -> {
            if (gardenGridPane == null) return;
            for (Node n : gardenGridPane.getChildren()) {
                Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
                if (r != null && c != null && r == row && c == col && n instanceof StackPane cell) {
                    Label lbl = new Label("+$" + value + " 🌾");
                    lbl.setFont(Font.font("Verdana", FontWeight.EXTRA_BOLD, 16));
                    lbl.setTextFill(Color.web(ACCENT_ORANGE));
                    lbl.setEffect(new DropShadow(4, Color.rgb(0,0,0,0.3)));
                    StackPane.setAlignment(lbl, Pos.CENTER);
                    cell.getChildren().add(lbl);
                    TranslateTransition mv = new TranslateTransition(Duration.millis(1200), lbl);
                    mv.setByY(-40);
                    FadeTransition fd = new FadeTransition(Duration.millis(1200), lbl);
                    fd.setFromValue(1); fd.setToValue(0); fd.setDelay(Duration.millis(400));
                    fd.setOnFinished(e -> cell.getChildren().remove(lbl));
                    ScaleTransition sc = new ScaleTransition(Duration.millis(400), lbl);
                    sc.setFromX(0.5); sc.setFromY(0.5); sc.setToX(1.2); sc.setToY(1.2);
                    mv.play(); fd.play(); sc.play();
                    return;
                }
            }
        });
    }

    private void showSaleFlash() {
        if (headerMoneyLabel == null) return;
        ScaleTransition pulse = new ScaleTransition(Duration.millis(300), headerMoneyLabel);
        pulse.setFromX(1); pulse.setFromY(1); pulse.setToX(1.4); pulse.setToY(1.4);
        pulse.setAutoReverse(true); pulse.setCycleCount(2);
        pulse.play();
    }

    private void handleInit() {
        Object[][] layout = { {"Oak",0,1},{"Maple",0,5},{"Pine",0,6},{"Carrot",2,2},{"Rose",4,4},{"Oak",4,6},{"Lily",3,1},{"Tulip",4,3},{"Spinach",1,0},{"Zucchini",3,0} };
        Platform.runLater(() -> {
            for (Object[] pi : layout) { String n = (String) pi[0]; int r = (int) pi[1]; int c = (int) pi[2];
                if (r >= gardenGrid.getNumRows() || c >= gardenGrid.getNumCols()) continue;
                Plant p = plantManager.getPlantByName(n); if (p != null) { p.setRow(r); p.setCol(c);
                    p.addWater(p.getWaterRequirement()); // Start with full water
                    try { gardenGrid.addPlant(p, r, c); gridAddPlant(p, r, c); addLogEntry("INFO", "🌱 " + n + " at (" + r + "," + c + ")"); } catch (Exception e) { logger.error("Place fail: {}", e.getMessage()); } } }
            refreshHealth();
        });
    }

    // ════════════════════════════════════════════
    //  GRID OPS
    // ════════════════════════════════════════════

    private void gridAddPlant(Plant p, int row, int col) {
        Platform.runLater(() -> {
            for (Node node : gardenGridPane.getChildren()) {
                Integer r = GridPane.getRowIndex(node); Integer c = GridPane.getColumnIndex(node);
                if (r != null && c != null && r == row && c == col && node instanceof StackPane cell) {
                    cell.getChildren().clear();
                    try {
                        ImageView iv = new ImageView(new Image(getClass().getResourceAsStream("/images/" + p.getCurrentImage())));
                        iv.setFitHeight(45); iv.setFitWidth(45); iv.setEffect(new DropShadow(5, Color.rgb(0,0,0,0.4)));
                        cell.getChildren().add(iv);
                        Label nm = new Label(p.getName()); nm.setFont(Font.font("Verdana", 8)); nm.setTextFill(Color.web(TEXT_DIM)); StackPane.setAlignment(nm, Pos.BOTTOM_CENTER); cell.getChildren().add(nm);
                        double pct = p.getHealthFull() > 0 ? (double) p.getCurrentHealth() / p.getHealthFull() : 0;
                        ProgressBar hp = new ProgressBar(pct); hp.setPrefWidth(50); hp.setPrefHeight(3); hp.setMaxHeight(3);
                        hp.setStyle("-fx-accent: " + hpColor(p.getCurrentHealth(), p.getHealthFull()) + "; -fx-control-inner-background: " + BG_CARD + ";");
                        StackPane.setAlignment(hp, Pos.BOTTOM_CENTER); StackPane.setMargin(hp, new Insets(0,5,12,5)); cell.getChildren().add(hp);
                        ScaleTransition g = new ScaleTransition(Duration.millis(400), iv); g.setFromX(0.3); g.setFromY(0.3); g.setToX(1); g.setToY(1); g.play();
                    } catch (Exception e) { Label fb = new Label(p.getName()); fb.setTextFill(Color.web(ACCENT_GREEN)); fb.setFont(Font.font("Verdana", 10)); cell.getChildren().add(fb); }
                    break;
                }
            }
        });
    }

    private void refreshGrid(Plant p) { gridAddPlant(p, p.getRow(), p.getCol()); }
    private void clearCell(int r, int c) { for (Node n : gardenGridPane.getChildren()) { Integer nr = GridPane.getRowIndex(n); Integer nc = GridPane.getColumnIndex(n); if (nr != null && nc != null && nr == r && nc == c && n instanceof StackPane sp) { sp.getChildren().clear(); break; } } }

    private void enterPlantingMode(String plantName) {
        plantingMode = true;
        pendingPlantName = plantName;
        Platform.runLater(() -> {
            farmerToolbarIcon.setVisible(true);
            plantingModeLabel.setText("Placing: " + plantName);
            plantingModeLabel.setVisible(true);
            cancelPlantBtn.setVisible(true);
            highlightEmptyCells(true);
            if (rootLayout.getScene() != null) {
                rootLayout.getScene().setOnKeyPressed(ke -> {
                    if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) exitPlantingMode();
                });
            }
        });
        addLogEntry("INFO", "🌱 Select an empty cell to plant " + plantName);
    }

    private void exitPlantingMode() {
        plantingMode = false;
        pendingPlantName = null;
        Platform.runLater(() -> {
            farmerToolbarIcon.setVisible(false);
            plantingModeLabel.setVisible(false);
            cancelPlantBtn.setVisible(false);
            highlightEmptyCells(false);
            removeFarmerFromAllCells();
            if (rootLayout.getScene() != null) rootLayout.getScene().setOnKeyPressed(null);
        });
    }

    private void highlightEmptyCells(boolean highlight) {
        if (gardenGridPane == null) return;
        for (Node n : gardenGridPane.getChildren()) {
            Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
            if (r != null && c != null && n instanceof StackPane cell) {
                if (!gardenGrid.isSpotOccupied(r, c)) {
                    cell.setStyle(highlight ? CELL_PLANTING_EMPTY : CELL_BASE_STYLE);
                }
            }
        }
    }

    private void placePlantAt(String name, int row, int col) {
        Plant p = plantManager.getPlantByName(name); if (p == null) return;
        if (gardenGrid.isSpotOccupied(row, col)) { addLogEntry("ERROR", "Cell (" + row + "," + col + ") is occupied"); return; }
        p.setRow(row); p.setCol(col);
        p.addWater(p.getWaterRequirement());
        gardenGrid.addPlant(p, row, col); gridAddPlant(p, row, col);
        addLogEntry("INFO", "🌱 Planted " + name + " at (" + row + "," + col + ")");
        GardenLog.log(GardenLog.Category.PLANT, "Planted %s at (%d,%d)", name, row, col);
    }

    /** Shows scarecrow in right bottom corner when crow is detected (not on grid). */
    private void showScarecrowInCorner() {
        Platform.runLater(() -> {
            if (scarecrowContainer == null) return;
            scarecrowContainer.getChildren().clear();
            try {
                ImageView iv = new ImageView(new Image(getClass().getResourceAsStream("/images/scarecrow.png")));
                iv.setFitWidth(64); iv.setFitHeight(64); iv.setPreserveRatio(true);
                iv.setEffect(new DropShadow(8, Color.rgb(0, 0, 0, 0.5)));
                scarecrowContainer.getChildren().add(iv);
                ScaleTransition pop = new ScaleTransition(Duration.millis(300), iv);
                pop.setFromX(0.2); pop.setFromY(0.2); pop.setToX(1); pop.setToY(1); pop.play();
                PauseTransition p = new PauseTransition(Duration.seconds(8));
                p.setOnFinished(e -> {
                    FadeTransition fade = new FadeTransition(Duration.millis(400), iv);
                    fade.setFromValue(1); fade.setToValue(0);
                    fade.setOnFinished(f -> scarecrowContainer.getChildren().remove(iv));
                    fade.play();
                });
                p.play();
            } catch (Exception ex) {
                Label fallback = new Label("🧙‍♂️"); fallback.setFont(Font.font("System", FontWeight.BOLD, 48));
                scarecrowContainer.getChildren().add(fallback);
                PauseTransition p = new PauseTransition(Duration.seconds(8));
                p.setOnFinished(e -> scarecrowContainer.getChildren().remove(fallback));
                p.play();
            }
        });
    }

    /** Removes any overlay in the cell with the given tag before adding a new one. */
    private void removeOverlayByTag(StackPane cell, String tag) {
        cell.getChildren().removeIf(node -> tag.equals(node.getUserData()));
    }

    private void showCellIcon(int row, int col, String emoji, int sec) { showCellIcon(row, col, emoji, sec, 12, Pos.TOP_LEFT, "overlay"); }
    private void showCellIcon(int row, int col, String emoji, int sec, int fontSize) { showCellIcon(row, col, emoji, sec, fontSize, Pos.TOP_LEFT, "overlay"); }
    /** Like showCellIcon but removes overlay with replaceTag first (e.g. pestkilled replaces pest). */
    private void showCellIconReplacing(int row, int col, String emoji, int sec, int fontSize, Pos position, String tag, String replaceTag) {
        Platform.runLater(() -> {
            if (gardenGridPane == null) return;
            for (Node n : gardenGridPane.getChildren()) {
                Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
                if (r != null && c != null && r == row && c == col && n instanceof StackPane cell) {
                    removeOverlayByTag(cell, replaceTag);
                    removeOverlayByTag(cell, tag);
                    Label ic = new Label(emoji);
                    ic.setUserData(tag);
                    ic.setFont(Font.font("System", FontWeight.BOLD, fontSize));
                    ic.setStyle("-fx-text-fill: #1a1a1a; -fx-background-color: rgba(255,255,200,0.95); -fx-background-radius: 6; -fx-padding: 4 6;");
                    ic.setEffect(new DropShadow(6, Color.BLACK));
                    StackPane.setAlignment(ic, position);
                    StackPane.setMargin(ic, new Insets(4));
                    cell.getChildren().add(ic);
                    ic.toFront();
                    PauseTransition p = new PauseTransition(Duration.seconds(sec)); p.setOnFinished(e -> cell.getChildren().remove(ic)); p.play();
                    return;
                }
            }
            logger.warn("showCellIconReplacing: no cell found at ({},{})", row, col);
        });
    }
    private void showCellIcon(int row, int col, String emoji, int sec, int fontSize, Pos position, String tag) {
        Platform.runLater(() -> {
            if (gardenGridPane == null) return;
            for (Node n : gardenGridPane.getChildren()) {
                Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
                if (r != null && c != null && r == row && c == col && n instanceof StackPane cell) {
                    removeOverlayByTag(cell, tag);
                    Label ic = new Label(emoji);
                    ic.setUserData(tag);
                    ic.setFont(Font.font("System", FontWeight.BOLD, fontSize));
                    ic.setStyle("-fx-text-fill: #1a1a1a; -fx-background-color: rgba(255,255,200,0.95); -fx-background-radius: 6; -fx-padding: 4 6;");
                    ic.setEffect(new DropShadow(6, Color.BLACK));
                    StackPane.setAlignment(ic, position);
                    StackPane.setMargin(ic, new Insets(4));
                    cell.getChildren().add(ic);
                    ic.toFront();
                    PauseTransition p = new PauseTransition(Duration.seconds(sec)); p.setOnFinished(e -> cell.getChildren().remove(ic)); p.play();
                    return;
                }
            }
            logger.warn("showCellIcon: no cell found at ({},{})", row, col);
        });
    }

    /** Shows a resource image on a grid cell for a given duration, then removes it. */
    private void showCellImage(int row, int col, String imagePath, int sec, int size) { showCellImage(row, col, imagePath, sec, size, Pos.TOP_LEFT, "overlay"); }
    private void showCellImage(int row, int col, String imagePath, int sec, int size, Pos position, String tag) {
        Platform.runLater(() -> {
            if (gardenGridPane == null) return;
            for (Node n : gardenGridPane.getChildren()) {
                Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
                if (r != null && c != null && r == row && c == col && n instanceof StackPane cell) {
                    removeOverlayByTag(cell, tag);
                    try {
                        ImageView iv = new ImageView(new Image(getClass().getResourceAsStream(imagePath)));
                        iv.setUserData(tag);
                        iv.setFitWidth(size); iv.setFitHeight(size); iv.setPreserveRatio(true);
                        iv.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.5)));
                        StackPane.setAlignment(iv, position);
                        StackPane.setMargin(iv, new Insets(4));
                        cell.getChildren().add(iv);
                        iv.toFront();
                        ScaleTransition pop = new ScaleTransition(Duration.millis(300), iv);
                        pop.setFromX(0.2); pop.setFromY(0.2); pop.setToX(1); pop.setToY(1); pop.play();
                        PauseTransition p = new PauseTransition(Duration.seconds(sec));
                        p.setOnFinished(e -> {
                            FadeTransition fade = new FadeTransition(Duration.millis(400), iv);
                            fade.setFromValue(1); fade.setToValue(0);
                            fade.setOnFinished(f -> cell.getChildren().remove(iv));
                            fade.play();
                        });
                        p.play();
                    } catch (Exception ex) {
                        showCellIcon(row, col, "🖼", sec, 16, position, tag);
                        logger.warn("showCellImage: image not found {}", imagePath);
                    }
                    return;
                }
            }
        });
    }

    private void hlCell(int row, int col, String bc, int sec) {
        Platform.runLater(() -> { for (Node n : gardenGridPane.getChildren()) { Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
            if (r != null && c != null && r == row && c == col && n instanceof StackPane cell) {
                String old = cell.getStyle(); cell.setStyle("-fx-background-color: rgba(200,230,200,0.95); -fx-border-color: " + bc + "; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;");
                PauseTransition p = new PauseTransition(Duration.seconds(sec)); p.setOnFinished(e -> cell.setStyle(old)); p.play(); break; } } });
    }

    private void showDmg(int row, int col, int dmg) {
        Platform.runLater(() -> {
            for (Node n : gardenGridPane.getChildren()) {
                Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
                if (r != null && c != null && r == row && c == col && n instanceof StackPane cell) {
                    removeOverlayByTag(cell, "damage");
                    Label d = new Label("-" + dmg);
                    d.setUserData("damage");
                    d.setFont(Font.font("Verdana", FontWeight.BOLD, 14)); d.setTextFill(Color.web(ACCENT_RED)); d.setEffect(new DropShadow(3, Color.rgb(0,0,0,0.3)));
                    StackPane.setAlignment(d, Pos.TOP_CENTER); cell.getChildren().add(d);
                    TranslateTransition mv = new TranslateTransition(Duration.millis(800), d); mv.setByY(-20);
                    FadeTransition fd = new FadeTransition(Duration.millis(800), d); fd.setFromValue(1); fd.setToValue(0); fd.setDelay(Duration.millis(500)); fd.setOnFinished(e -> cell.getChildren().remove(d));
                    mv.play(); fd.play();
                    break;
                }
            }
        });
    }

    private void showPesticideOnAllPlants() {
        Platform.runLater(() -> {
            if (gardenGridPane == null) return;
            for (Node n : gardenGridPane.getChildren()) {
                Integer r = GridPane.getRowIndex(n); Integer c = GridPane.getColumnIndex(n);
                if (r != null && c != null && n instanceof StackPane cell) {
                    if (gardenGrid.getPlant(r, c) != null) {
                        showCellIcon(r, c, "🧪", 6, 16, Pos.TOP_RIGHT, "pesticide_schedule");
                    }
                }
            }
        });
    }

    // ════════════════════════════════════════════
    //  RAIN ANIMATION (falling drops - RainEvent only)
    // ════════════════════════════════════════════

    private void startRain() {
        if (rainAnimation != null) return; rainDrops.clear();
        for (int i = 0; i < 80; i++) rainDrops.add(new double[]{ random.nextDouble()*1200, random.nextDouble()*800, 2+random.nextDouble()*3 });
        GraphicsContext gc = rainCanvas.getGraphicsContext2D();
        rainAnimation = new AnimationTimer() { @Override public void handle(long now) {
            double w = rainCanvas.getWidth(), h = rainCanvas.getHeight(); gc.clearRect(0,0,w,h); gc.setFill(Color.rgb(100,180,255,0.6));
            for (double[] d : rainDrops) { d[1] += d[2]; if (d[1] > h) { d[1] = 0; d[0] = random.nextDouble()*w; } gc.fillOval(d[0], d[1], 2, 10); }
        }}; rainAnimation.start();
    }

    private void stopRain() {
        if (rainAnimation != null) { rainAnimation.stop(); rainAnimation = null; rainCanvas.getGraphicsContext2D().clearRect(0, 0, rainCanvas.getWidth(), rainCanvas.getHeight()); }
        rainDrops.clear();
    }

}
