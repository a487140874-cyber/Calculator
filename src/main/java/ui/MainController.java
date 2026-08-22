package ui;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.example.calculator.EnergyCalculator;
import com.example.calculator.KeyLayerAnalyzer;
import com.example.calculator.LayerLoadCalculator;
import geModel.GeDataModel;
import geModel.GeDataModelExcel;
import geModel.GeDataModelMapper;
import getData.GetDateFromExcle;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.SubScene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Line;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MainController {

    private enum WorkflowState {
        EMPTY, IMPORTED, KEY_LAYERS, COLLAPSE, ENERGY
    }

    private enum StepStatus { DONE, READY, RUNNING, LOCKED }

    private static final String[] STEP_LABELS = {
            "数据导入", "关键层计算", "垮落分析", "能量分析", "三维可视化", "结果导出"
    };

    @FXML private TableView<GeDataModel> dataTable;
    @FXML private TableColumn<GeDataModel, Integer> numCol;
    @FXML private TableColumn<GeDataModel, String> nameCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> hCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> lCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> eCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> rCol;
    @FXML private TableColumn<GeDataModel, Boolean> isKeyLayerCol;
    @FXML private TableColumn<GeDataModel, Boolean> isNotCrackCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> lastAiCol;

    @FXML private Pane visualizationPane;
    @FXML private Label statusLabel;

    @FXML private Button importButton;
    @FXML private javafx.scene.control.MenuButton exportButton;
    @FXML private MenuItem exportImageMenuItem;
    @FXML private MenuItem importMenuItem;
    @FXML private MenuItem exportMenuItem;

    @FXML private Label fileNameLabel;
    @FXML private Label fileBadgeLabel;
    @FXML private VBox stepNav;
    @FXML private TabPane workTabs;
    @FXML private Tab overviewTab;
    @FXML private Tab tableTab;
    @FXML private Tab energyTab;
    @FXML private Tab d3Tab;
    @FXML private HBox statCardsBox;
    @FXML private VBox detailPanel;
    @FXML private VBox overviewSideBox;
    @FXML private HBox filterBox;
    @FXML private javafx.scene.control.TextField searchField;
    @FXML private VBox energyTabBox;
    @FXML private VBox d3TabBox;

    private final List<String[]> activityLog = new ArrayList<>();

    private static final double THREE_D_HEIGHT_SCALE = 6.0;

    private final Group threeDRoot = new Group();
    private final Group threeDContentGroup = new Group();
    private final PerspectiveCamera threeDCamera = new PerspectiveCamera(true);
    private final Rotate threeDRotateX = new Rotate(-20, Rotate.X_AXIS);
    private final Rotate threeDRotateY = new Rotate(-35, Rotate.Y_AXIS);
    private final Translate threeDTranslate = new Translate(0, 0, -800);
    private final Map<GeDataModel, Box> threeDBoxesByLayer = new IdentityHashMap<>();
    private SubScene threeDSubScene;
    private Label threeDEmptyLabel;
    private double threeDMouseOldX;
    private double threeDMouseOldY;
    private double threeDStackHeight = 800;

    private static final String[] TABLE_FILTERS = {"全部", "关键层", "已垮落", "未垮落", "能量聚集"};
    private String activeTableFilter = TABLE_FILTERS[0];

    private static final Map<String, String> LITHOLOGY_COLORS = Map.ofEntries(
            Map.entry("泥岩", "#A79F95"),
            Map.entry("砂质泥岩", "#B9B2A4"),
            Map.entry("细砂岩", "#CDC5AE"),
            Map.entry("中砂岩", "#C0B18F"),
            Map.entry("粗砂岩", "#B7A97C"),
            Map.entry("石灰岩", "#9DA8A8"),
            Map.entry("砂岩", "#C49A5A"),
            Map.entry("页岩", "#556B7A"),
            Map.entry("煤层", "#3A3A3D"),
            Map.entry("煤", "#3A3A3D")
    );
    private static final String DEFAULT_LITHOLOGY_COLOR = "#C8D1DA";

    private List<GeDataModel> geDataModels;
    private final ObservableList<GeDataModel> tableData = FXCollections.observableArrayList();
    private final FilteredList<GeDataModel> filteredTableData = new FilteredList<>(tableData, m -> true);
    private final ObjectProperty<GeDataModel> selectedLayer = new SimpleObjectProperty<>();
    private final Map<GeDataModel, Region> profileBarsByLayer = new IdentityHashMap<>();
    private WorkflowState workflowState = WorkflowState.EMPTY;
    private boolean busy;
    private int activeStepIndex = -1;
    private File importedFile;

    private final GetDateFromExcle dataImporter = new GetDateFromExcle();
    private final LayerLoadCalculator layerLoadCalculator = new LayerLoadCalculator();
    private final KeyLayerAnalyzer keyLayerAnalyzer = new KeyLayerAnalyzer();
    private final EnergyCalculator energyCalculator = new EnergyCalculator();

    @FXML
    public void initialize() {
        numCol.setCellValueFactory(new PropertyValueFactory<>("num"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        hCol.setCellValueFactory(new PropertyValueFactory<>("h"));
        lCol.setCellValueFactory(new PropertyValueFactory<>("l"));
        eCol.setCellValueFactory(new PropertyValueFactory<>("e"));
        rCol.setCellValueFactory(new PropertyValueFactory<>("r"));
        isKeyLayerCol.setCellValueFactory(new PropertyValueFactory<>("isKeyLayer"));
        isNotCrackCol.setCellValueFactory(new PropertyValueFactory<>("isNotCrack"));
        lastAiCol.setCellValueFactory(new PropertyValueFactory<>("lastAi"));

        hCol.setCellFactory(col -> numericCell(1));
        lCol.setCellFactory(col -> numericCell(4));
        eCol.setCellFactory(col -> numericCell(0));
        rCol.setCellFactory(col -> numericCell(1));
        lastAiCol.setCellFactory(col -> numericCell(1));
        isKeyLayerCol.setCellFactory(col -> booleanTagCell(true));
        isNotCrackCol.setCellFactory(col -> booleanTagCell(false));

        dataTable.setItems(filteredTableData);
        dataTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> selectedLayer.set(newValue));
        selectedLayer.addListener((observable, oldValue, newValue) -> {
            refreshDetailPanel();
            refreshProfileSelection();
            syncTableSelection();
            refreshEnergyTab();
            refreshThreeDSelection();
        });

        visualizationPane.widthProperty().addListener((observable, oldValue, newValue) -> {
            if (!tableData.isEmpty()) {
                drawRockLayersVisualization();
            }
        });
        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> applyTableFilter());
        }
        initThreeDScene();
        refreshDetailPanel();
        refreshLogFeed();
        updateControls();
    }

    private void updateControls() {
        importButton.setDisable(busy);
        importMenuItem.setDisable(busy);
        boolean exportUnavailable = workflowState.ordinal() < WorkflowState.COLLAPSE.ordinal();
        exportButton.setDisable(busy || exportUnavailable);
        exportMenuItem.setDisable(busy || exportUnavailable);
        exportImageMenuItem.setDisable(busy || workflowState != WorkflowState.ENERGY);

        boolean dataLoaded = workflowState != WorkflowState.EMPTY;
        tableTab.setDisable(!dataLoaded);
        energyTab.setDisable(workflowState.ordinal() < WorkflowState.ENERGY.ordinal());
        d3Tab.setDisable(workflowState != WorkflowState.ENERGY);

        updateFileBadge();
        refreshStepNav();
        refreshStatCards();
        refreshTableFilters();
        refreshEnergyTab();
        refreshThreeDScene();
    }

    /* ==================== Energy analysis tab ==================== */

    private void refreshEnergyTab() {
        if (energyTabBox == null) return;
        energyTabBox.getChildren().clear();

        if (!hasEnergyResults()) {
            energyTabBox.setAlignment(Pos.CENTER);
            Label empty = new Label("尚无能量计算结果");
            empty.getStyleClass().add("text-soft");
            energyTabBox.getChildren().add(empty);
            return;
        }

        energyTabBox.setAlignment(Pos.TOP_LEFT);
        BigDecimal maxEnergy = geDataModels.stream()
                .filter(this::hasPositiveEnergy)
                .map(GeDataModel::getPower)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);

        VBox rows = new VBox(2);
        rows.setPadding(new Insets(4));
        for (GeDataModel layer : geDataModels) {
            rows.getChildren().add(buildEnergyRow(layer, maxEnergy));
        }

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        energyTabBox.getChildren().add(scroll);
    }

    private Node buildEnergyRow(GeDataModel layer, BigDecimal maxEnergy) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 10, 6, 10));
        row.setStyle("-fx-cursor: hand; -fx-border-color: transparent transparent -divider transparent; -fx-border-width: 1;");
        if (layer == selectedLayer.get()) {
            row.setStyle(row.getStyle() + "-fx-background-color: -primary-soft;");
        }
        row.setOnMouseClicked(event -> selectedLayer.set(layer));

        Label numLabel = new Label(String.valueOf(layer.getNum()));
        numLabel.getStyleClass().add("mono-num");
        numLabel.setMinWidth(24);

        Label nameLabel = new Label(safe(layer.getName()));
        nameLabel.setMinWidth(110);

        boolean hasEnergy = hasPositiveEnergy(layer);
        double ratio = hasEnergy ? layer.getPower().doubleValue() / maxEnergy.doubleValue() : 0;

        StackPane track = new StackPane();
        track.setAlignment(Pos.CENTER_LEFT);
        track.setPrefWidth(260);
        track.setMinWidth(260);

        Region bar = new Region();
        bar.setPrefHeight(16);
        bar.setMinHeight(16);
        bar.setPrefWidth(Math.max(4, ratio * 260));
        bar.setStyle("-fx-background-color: " + (hasEnergy ? energyColor(ratio) : "-divider") + "; -fx-background-radius: 2;");
        track.getChildren().add(bar);
        StackPane.setAlignment(bar, Pos.CENTER_LEFT);

        Label valueLabel = new Label(hasEnergy ? formatPlain2(layer.getPower()) + " MJ" : "无能量结果");
        valueLabel.getStyleClass().add(hasEnergy ? "mono-num" : "text-muted");
        valueLabel.setMinWidth(90);

        row.getChildren().addAll(numLabel, nameLabel, track, valueLabel);

        if (layer.isKeyLayer() && !hasEnergy) {
            row.getChildren().add(makeTag("关键层 · 无能量结果", "tag-nokey"));
        }
        return row;
    }

    private String energyColor(double ratio) {
        double t = Math.pow(Math.max(0, Math.min(1, ratio)), 0.75);
        int[] from = {0xF2, 0xE3, 0xC4};
        int[] to = {0xA8, 0x32, 0x18};
        int r = (int) Math.round(from[0] + (to[0] - from[0]) * t);
        int g = (int) Math.round(from[1] + (to[1] - from[1]) * t);
        int b = (int) Math.round(from[2] + (to[2] - from[2]) * t);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    /* ==================== Data table: filters + formatted cells ==================== */

    private void refreshTableFilters() {
        if (filterBox == null) return;
        filterBox.getChildren().clear();
        for (String filter : TABLE_FILTERS) {
            Button button = new Button(filter + " (" + countForFilter(filter) + ")");
            button.getStyleClass().addAll("btn-small",
                    filter.equals(activeTableFilter) ? "btn-secondary" : "btn-ghost");
            button.setOnAction(event -> {
                activeTableFilter = filter;
                applyTableFilter();
                refreshTableFilters();
            });
            filterBox.getChildren().add(button);
        }
    }

    private long countForFilter(String filter) {
        if (geDataModels == null) return 0;
        return geDataModels.stream().filter(m -> matchesTableFilter(m, filter)).count();
    }

    private boolean matchesTableFilter(GeDataModel model, String filter) {
        return switch (filter) {
            case "关键层" -> model.isKeyLayer();
            case "已垮落" -> model.isKeyLayer() && !model.isNotCrack();
            case "未垮落" -> !model.isKeyLayer() || model.isNotCrack();
            case "能量聚集" -> hasPositiveEnergy(model);
            default -> true;
        };
    }

    private void applyTableFilter() {
        String query = searchField == null || searchField.getText() == null ? "" : searchField.getText().trim();
        filteredTableData.setPredicate(model -> matchesTableFilter(model, activeTableFilter)
                && (query.isEmpty() || (model.getName() != null && model.getName().contains(query))));
    }

    private TableCell<GeDataModel, BigDecimal> numericCell(int decimals) {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().add("mono-num");
                setStyle("-fx-alignment: CENTER-RIGHT;");
                setText(empty || value == null ? null : value.setScale(decimals, RoundingMode.HALF_UP).toPlainString());
            }
        };
    }

    private TableCell<GeDataModel, Boolean> booleanTagCell(boolean keyColumn) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                String text;
                String styleClass;
                if (keyColumn) {
                    text = value ? "关键层" : "非关键层";
                    styleClass = value ? "tag-key" : "tag-nokey";
                } else {
                    text = value ? "未垮落" : "已垮落";
                    styleClass = value ? "tag-nocrack" : "tag-crack";
                }
                Label tag = new Label(text);
                tag.getStyleClass().addAll("tag", styleClass);
                setGraphic(tag);
            }
        };
    }

    private void updateFileBadge() {
        fileNameLabel.setText(importedFile == null ? "未打开文件" : importedFile.getName());
        fileBadgeLabel.getStyleClass().removeAll("tag-nokey", "tag-key", "tag-energy", "tag-done");
        if (workflowState == WorkflowState.EMPTY) {
            fileBadgeLabel.setText("等待导入");
            fileBadgeLabel.getStyleClass().add("tag-nokey");
        } else if (busy) {
            fileBadgeLabel.setText("计算中");
            fileBadgeLabel.getStyleClass().add("tag-key");
        } else if (workflowState.ordinal() >= WorkflowState.COLLAPSE.ordinal()) {
            fileBadgeLabel.setText("存在未导出结果");
            fileBadgeLabel.getStyleClass().add("tag-energy");
        } else {
            fileBadgeLabel.setText("导入成功");
            fileBadgeLabel.getStyleClass().add("tag-done");
        }
    }

    /* ==================== Flow step navigator ==================== */

    private StepStatus stepStatusFor(WorkflowState readyAt) {
        if (workflowState.ordinal() < readyAt.ordinal()) return StepStatus.LOCKED;
        if (workflowState == readyAt) return StepStatus.READY;
        return StepStatus.DONE;
    }

    private StepStatus[] computeStepStatuses() {
        StepStatus[] s = new StepStatus[STEP_LABELS.length];
        s[0] = activeStepIndex == 0 ? StepStatus.RUNNING
                : (workflowState == WorkflowState.EMPTY ? StepStatus.READY : StepStatus.DONE);
        s[1] = activeStepIndex == 1 ? StepStatus.RUNNING : stepStatusFor(WorkflowState.IMPORTED);
        s[2] = activeStepIndex == 2 ? StepStatus.RUNNING : stepStatusFor(WorkflowState.KEY_LAYERS);
        s[3] = activeStepIndex == 3 ? StepStatus.RUNNING : stepStatusFor(WorkflowState.COLLAPSE);
        s[4] = workflowState.ordinal() < WorkflowState.ENERGY.ordinal() ? StepStatus.LOCKED : StepStatus.DONE;
        s[5] = activeStepIndex == 5 ? StepStatus.RUNNING
                : (workflowState.ordinal() < WorkflowState.COLLAPSE.ordinal() ? StepStatus.LOCKED : StepStatus.READY);
        return s;
    }

    private void refreshStepNav() {
        if (stepNav == null) return;
        StepStatus[] statuses = computeStepStatuses();
        stepNav.getChildren().clear();
        for (int i = 0; i < STEP_LABELS.length; i++) {
            stepNav.getChildren().add(buildStepCell(i, statuses[i]));
        }
    }

    private Node buildStepCell(int index, StepStatus status) {
        HBox cell = new HBox(10);
        cell.getStyleClass().add("step-cell");
        cell.setAlignment(Pos.TOP_LEFT);
        if (status == StepStatus.LOCKED) {
            cell.getStyleClass().add("step-cell-locked");
        }

        Label mark = new Label(markFor(status));
        mark.getStyleClass().addAll("step-mark", "step-mark-" + status.name().toLowerCase());
        mark.setAlignment(Pos.CENTER);

        Label label = new Label(STEP_LABELS[index]);
        label.getStyleClass().add("text-body");
        Label note = new Label(noteFor(index, status));
        note.getStyleClass().add("text-caption");
        note.setWrapText(true);
        VBox textBox = new VBox(2, label, note);

        cell.getChildren().addAll(mark, textBox);

        if (status != StepStatus.LOCKED) {
            cell.setOnMouseClicked(event -> onStepClicked(index, status));
        }
        return cell;
    }

    private String markFor(StepStatus status) {
        return switch (status) {
            case DONE -> "✓";
            case READY -> "▸";
            case RUNNING -> "◔";
            case LOCKED -> "·";
        };
    }

    private String noteFor(int index, StepStatus status) {
        if (status == StepStatus.LOCKED) return "需先完成前置步骤";
        if (status == StepStatus.RUNNING) return "进行中…";
        return switch (index) {
            case 0 -> geDataModels == null ? "选择 Excel 文件导入" : geDataModels.size() + " 层数据";
            case 1 -> status == StepStatus.DONE ? "已识别关键层" : "可执行";
            case 2 -> status == StepStatus.DONE ? "已完成垮落分析" : "可执行";
            case 3 -> status == StepStatus.DONE ? "已完成能量计算" : "可执行";
            case 4 -> "可交互查看三维分布";
            case 5 -> "可导出 Excel";
            default -> "";
        };
    }

    private void onStepClicked(int index, StepStatus status) {
        switch (index) {
            case 0 -> handleImportExcel();
            case 1 -> { if (status == StepStatus.READY) handleCalculateKeyLayers(); else selectTab(overviewTab); }
            case 2 -> { if (status == StepStatus.READY) handleCalculateCollapse(); else selectTab(overviewTab); }
            case 3 -> { if (status == StepStatus.READY) handleCalculateEnergy(); else selectTab(energyTab); }
            case 4 -> selectTab(d3Tab);
            case 5 -> handleExportExcel();
            default -> { }
        }
    }

    private void selectTab(Tab tab) {
        if (tab != null && !tab.isDisable()) {
            workTabs.getSelectionModel().select(tab);
        }
    }

    /* ==================== Operations / data-quality log ==================== */

    private void appendLog(String message) {
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        activityLog.add(0, new String[]{time, message});
        while (activityLog.size() > 50) {
            activityLog.remove(activityLog.size() - 1);
        }
        refreshLogFeed();
    }

    private void refreshLogFeed() {
        if (overviewSideBox == null) return;
        Label heading = new Label("操作与数据质量记录");
        heading.getStyleClass().add("text-panel-head");

        VBox list = new VBox(2);
        for (String[] entry : activityLog) {
            Label msg = new Label(entry[1]);
            msg.getStyleClass().add("text-body");
            msg.setWrapText(true);
            Label time = new Label(entry[0]);
            time.getStyleClass().addAll("text-caption", "mono-num");
            VBox row = new VBox(2, msg, time);
            row.setStyle("-fx-border-color: transparent transparent -divider transparent; -fx-border-width: 1;");
            row.setPadding(new Insets(6, 4, 6, 4));
            list.getChildren().add(row);
        }
        if (activityLog.isEmpty()) {
            Label empty = new Label("暂无操作记录");
            empty.getStyleClass().add("text-caption");
            list.getChildren().add(empty);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox wrapper = new VBox(8, heading, scroll);
        wrapper.getStyleClass().add("app-panel");
        wrapper.setPadding(new Insets(10));
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        overviewSideBox.getChildren().setAll(wrapper);
    }

    /* ==================== Overview stat cards ==================== */

    private void refreshStatCards() {
        if (statCardsBox == null) return;
        statCardsBox.getChildren().setAll(
                buildStatCard("岩层总数", geDataModels == null ? "-" : String.valueOf(geDataModels.size()), "层"),
                buildStatCard("关键层数量", geDataModels == null ? "-" : String.valueOf(countKeyLayers()), "层"),
                buildStatCard("发生垮落岩层", geDataModels == null ? "-" : String.valueOf(countCollapsed()), "层"),
                buildStatCard("能量聚集层", !hasEnergyResults() ? "-" : String.valueOf(energyCalculator.getEnergyLayerCount(geDataModels)), "层"),
                buildStatCard("最大能量值", !hasEnergyResults() ? "-" : formatMaxEnergy(), "")
        );
    }

    private Node buildStatCard(String label, String value, String unit) {
        VBox card = new VBox(7);
        card.getStyleClass().add("stat-card");
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("text-caption");
        HBox valueRow = new HBox(4);
        valueRow.setAlignment(Pos.BASELINE_LEFT);
        Label valueNode = new Label(value);
        valueNode.getStyleClass().addAll("text-value-lg", "mono-num");
        valueRow.getChildren().add(valueNode);
        if (!unit.isBlank()) {
            Label unitNode = new Label(unit);
            unitNode.getStyleClass().add("text-caption");
            valueRow.getChildren().add(unitNode);
        }
        card.getChildren().addAll(labelNode, valueRow);
        return card;
    }

    private long countKeyLayers() {
        return geDataModels.stream().filter(GeDataModel::isKeyLayer).count();
    }

    private long countCollapsed() {
        return geDataModels.stream().filter(m -> m.isKeyLayer() && !m.isNotCrack()).count();
    }

    private boolean hasEnergyResults() {
        return geDataModels != null && energyCalculator.hasEnergyResults(geDataModels);
    }

    private String formatMaxEnergy() {
        return geDataModels.stream()
                .filter(this::hasPositiveEnergy)
                .map(GeDataModel::getPower)
                .max(BigDecimal::compareTo)
                .map(v -> v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                .orElse("-");
    }

    private void resetData() {
        workflowState = WorkflowState.EMPTY;
        geDataModels = null;
        importedFile = null;
        tableData.clear();
        selectedLayer.set(null);
        visualizationPane.getChildren().clear();
        updateControls();
    }

    @FXML
    private void handleImportExcel() {
        if (busy) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Excel Data File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"));
        File selectedFile = fileChooser.showOpenDialog(dataTable.getScene().getWindow());
        if (selectedFile == null) return;

        resetData();
        activeStepIndex = 0;
        runTask(
                "Importing data from " + selectedFile.getName() + "...",
                () -> dataImporter.getData(selectedFile.getAbsolutePath()),
                imported -> {
                    if (imported == null || imported.isEmpty()) {
                        throw new IllegalStateException("No data was found in the selected file.");
                    }
                    List<ValidationIssue> issues = validateImportedData(imported);
                    if (!issues.isEmpty()) {
                        showValidationDialog(selectedFile, imported.size(), issues);
                        statusLabel.setText("Import validation failed: " + issues.size() + " issue(s) found in "
                                + selectedFile.getName() + ".");
                        appendLog("数据质量提示：" + selectedFile.getName() + " 校验未通过，" + issues.size() + " 处错误");
                        return;
                    }
                    geDataModels = imported;
                    importedFile = selectedFile;
                    tableData.setAll(imported);
                    workflowState = WorkflowState.IMPORTED;
                    drawRockLayersVisualization();
                    statusLabel.setText("Successfully imported " + imported.size() + " records. Ready to calculate key layers.");
                    appendLog("导入 " + selectedFile.getName() + " · " + imported.size() + " 行 · 校验通过");
                },
                "Import Error");
    }

    @FXML
    private void handleCalculateKeyLayers() {
        if (!requireState(WorkflowState.IMPORTED, "Please import data before calculating key layers.")) return;
        activeStepIndex = 1;
        runTask(
                "Calculating key layers...",
                () -> layerLoadCalculator.findKeyLayers(geDataModels),
                result -> {
                    geDataModels = requireResult(result, "Key layer calculation returned no data.");
                    workflowState = WorkflowState.KEY_LAYERS;
                    refreshTableAndVisualization();
                    statusLabel.setText("Key layers calculated successfully. Ready for collapse analysis.");
                    appendLog("关键层识别完成 · " + countKeyLayers() + " 层");
                },
                "Key Layer Calculation Error");
    }

    @FXML
    private void handleCalculateCollapse() {
        if (!requireState(WorkflowState.KEY_LAYERS, "Please calculate key layers first.")) return;
        activeStepIndex = 2;
        runTask(
                "Calculating layer collapse...",
                () -> keyLayerAnalyzer.compute(geDataModels),
                result -> {
                    geDataModels = requireResult(result, "Collapse calculation returned no data.");
                    workflowState = WorkflowState.COLLAPSE;
                    refreshTableAndVisualization();
                    statusLabel.setText("Collapse calculation complete. Ready for energy calculation or export.");
                    appendLog("垮落与破断分析完成 · 已垮落 " + countCollapsed() + " 层");
                },
                "Collapse Calculation Error");
    }

    @FXML
    private void handleCalculateEnergy() {
        if (!requireState(WorkflowState.COLLAPSE, "Please complete collapse analysis first.")) return;
        activeStepIndex = 3;
        runTask(
                "Calculating energy...",
                () -> energyCalculator.calculateEnergy(geDataModels),
                result -> {
                    geDataModels = requireResult(result, "Energy calculation returned no data.");
                    if (!energyCalculator.hasEnergyResults(geDataModels)) {
                        throw new IllegalStateException("No positive energy values were generated. Check the collapse-analysis results.");
                    }
                    workflowState = WorkflowState.ENERGY;
                    refreshTableAndVisualization();
                    long count = energyCalculator.getEnergyLayerCount(geDataModels);
                    statusLabel.setText("Energy calculation completed. " + count + " layers have positive energy values.");
                    appendLog("能量计算完成 · " + count + " 层有能量值");
                    selectTab(energyTab);
                },
                "Energy Calculation Error");
    }

    private boolean requireState(WorkflowState required, String message) {
        if (busy || workflowState != required || geDataModels == null || geDataModels.isEmpty()) {
            showError("Step Not Available", message);
            return false;
        }
        return true;
    }

    private <T> T requireResult(T result, String message) {
        if (result == null) throw new IllegalStateException(message);
        return result;
    }

    private <T> void runTask(String progressMessage, Callable<T> operation,
                             Consumer<T> onSuccess, String errorTitle) {
        runTask(progressMessage, operation, onSuccess, errorTitle, null);
    }

    private <T> void runTask(String progressMessage, Callable<T> operation,
                             Consumer<T> onSuccess, String errorTitle, Consumer<Throwable> onFailure) {
        busy = true;
        updateControls();
        statusLabel.setText(progressMessage);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            try {
                onSuccess.accept(task.getValue());
            } catch (Exception e) {
                if (onFailure != null) onFailure.accept(e);
                else showError(errorTitle, readableMessage(e));
                statusLabel.setText("Status: Operation failed.");
            } finally {
                busy = false;
                activeStepIndex = -1;
                updateControls();
            }
        });
        task.setOnFailed(event -> {
            busy = false;
            activeStepIndex = -1;
            updateControls();
            Throwable error = task.getException();
            if (onFailure != null) onFailure.accept(error);
            else showError(errorTitle, readableMessage(error));
            statusLabel.setText("Status: Operation failed.");
        });

        Thread worker = new Thread(task, "calculator-background-task");
        worker.setDaemon(true);
        worker.start();
    }

    private String readableMessage(Throwable error) {
        if (error == null) return "Unknown error.";
        Throwable cause = error;
        while (cause.getCause() != null && cause.getMessage() == null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    @FXML
    private void handleExportExcel() {
        if (workflowState.ordinal() < WorkflowState.COLLAPSE.ordinal()
                || geDataModels == null || geDataModels.isEmpty()) {
            showError("Results Not Ready", "Please complete collapse analysis before exporting.");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Processed Data");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showSaveDialog(dataTable.getScene().getWindow());
        if (file == null) return;

        activeStepIndex = 5;
        runTask(
                "Exporting data to " + file.getName() + "...",
                () -> {
                    List<GeDataModelExcel> exportList = GeDataModelMapper.toExcelList(geDataModels);
                    try (ExcelWriter writer = EasyExcel.write(file, GeDataModelExcel.class).build()) {
                        WriteSheet sheet = EasyExcel.writerSheet("Processed Data").build();
                        writer.write(exportList, sheet);
                    }
                    return file;
                },
                exported -> {
                    statusLabel.setText("Data successfully exported to " + exported.getName() + ".");
                    appendLog("导出 " + exported.getName() + " 成功");
                },
                "Export Error");
    }

    @FXML
    private void handleExportThreeDImage() {
        exportThreeDImage();
    }

    private BigDecimal[] findDimensions() {
        for (GeDataModel model : geDataModels) {
            if (model.getAx() != null && model.getBy() != null
                    && model.getAx().signum() > 0 && model.getBy().signum() > 0) {
                return new BigDecimal[]{model.getAx(), model.getBy()};
            }
        }
        return null;
    }

    /* ==================== 3D view tab: native JavaFX scene ==================== */

    private void initThreeDScene() {
        threeDCamera.setNearClip(0.1);
        threeDCamera.setFarClip(10000);
        threeDCamera.getTransforms().addAll(threeDRotateY, threeDRotateX, threeDTranslate);

        AmbientLight ambient = new AmbientLight(Color.web("#707070"));
        PointLight keyLight = new PointLight(Color.WHITE);
        keyLight.setTranslateX(-400);
        keyLight.setTranslateY(-600);
        keyLight.setTranslateZ(-600);

        threeDRoot.getChildren().addAll(threeDCamera, ambient, keyLight, threeDContentGroup);

        threeDSubScene = new SubScene(threeDRoot, 760, 520, true, SceneAntialiasing.BALANCED);
        threeDSubScene.setFill(Color.web("#0B1420"));
        threeDSubScene.setCamera(threeDCamera);

        threeDSubScene.setOnMousePressed(event -> {
            threeDMouseOldX = event.getSceneX();
            threeDMouseOldY = event.getSceneY();
        });
        threeDSubScene.setOnMouseDragged(event -> {
            double dx = event.getSceneX() - threeDMouseOldX;
            double dy = event.getSceneY() - threeDMouseOldY;
            threeDRotateY.setAngle(threeDRotateY.getAngle() + dx * 0.4);
            threeDRotateX.setAngle(Math.max(-85, Math.min(85, threeDRotateX.getAngle() - dy * 0.4)));
            threeDMouseOldX = event.getSceneX();
            threeDMouseOldY = event.getSceneY();
        });
        threeDSubScene.setOnScroll(event -> {
            double newZ = threeDTranslate.getZ() + event.getDeltaY() * 1.5;
            threeDTranslate.setZ(Math.max(-6000, Math.min(-150, newZ)));
        });

        buildD3TabContent();
    }

    private void buildD3TabContent() {
        if (d3TabBox == null) return;
        d3TabBox.getChildren().clear();
        d3TabBox.setAlignment(Pos.TOP_LEFT);

        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 10, 8, 10));
        Button resetBtn = new Button("重置视角");
        resetBtn.getStyleClass().add("btn-secondary");
        resetBtn.setOnAction(event -> resetThreeDCamera());
        Button exportBtn = new Button("导出图片");
        exportBtn.getStyleClass().add("btn-secondary");
        exportBtn.setOnAction(event -> exportThreeDImage());
        Label hint = new Label("左键拖拽旋转 · 滚轮缩放 · 点击岩层查看详情");
        hint.getStyleClass().add("text-caption");
        toolbar.getChildren().addAll(resetBtn, exportBtn, hint);

        threeDEmptyLabel = new Label("完成能量分析后即可查看三维视图");
        threeDEmptyLabel.getStyleClass().add("text-soft");

        StackPane sceneHost = new StackPane(threeDSubScene, threeDEmptyLabel);
        sceneHost.getStyleClass().add("app-panel");
        VBox.setVgrow(sceneHost, Priority.ALWAYS);
        threeDSubScene.widthProperty().bind(sceneHost.widthProperty());
        threeDSubScene.heightProperty().bind(sceneHost.heightProperty());

        d3TabBox.getChildren().addAll(toolbar, sceneHost);
    }

    private void refreshThreeDScene() {
        if (threeDContentGroup == null) return;
        threeDContentGroup.getChildren().clear();
        threeDBoxesByLayer.clear();

        boolean hasData = geDataModels != null && !geDataModels.isEmpty();
        if (threeDEmptyLabel != null) {
            threeDEmptyLabel.setVisible(!hasData);
        }
        if (!hasData) return;

        BigDecimal[] dims = findDimensions();
        double totalThickness = geDataModels.stream()
                .map(GeDataModel::getH)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
        double footprintX = dims != null ? dims[0].doubleValue() * THREE_D_HEIGHT_SCALE
                : Math.max(200, totalThickness * THREE_D_HEIGHT_SCALE * 0.6);
        double footprintZ = dims != null ? dims[1].doubleValue() * THREE_D_HEIGHT_SCALE : footprintX * 0.8;

        double currentY = 0;
        for (GeDataModel layer : geDataModels) {
            double thickness = layer.getH() == null ? 1.0 : Math.max(0.3, layer.getH().doubleValue());
            double boxHeight = thickness * THREE_D_HEIGHT_SCALE;

            Box box = new Box(footprintX, boxHeight, footprintZ);
            box.setTranslateY(currentY + boxHeight / 2);
            PhongMaterial material = new PhongMaterial(Color.web(colorForLayer(layer)));
            material.setSpecularColor(Color.web("#333333"));
            box.setMaterial(material);
            box.setOnMouseClicked(event -> selectedLayer.set(layer));

            Tooltip tooltip = new Tooltip(layer.getNum() + "  " + safe(layer.getName())
                    + "\n厚度 " + formatPlain(layer.getH()) + " m"
                    + (hasPositiveEnergy(layer) ? "\n能量 " + formatPlain2(layer.getPower()) + " MJ" : ""));
            Tooltip.install(box, tooltip);

            threeDContentGroup.getChildren().add(box);
            threeDBoxesByLayer.put(layer, box);
            currentY += boxHeight;
        }

        threeDContentGroup.setTranslateY(-currentY / 2.0);
        threeDStackHeight = currentY;
        applyThreeDDefaultCamera();
        refreshThreeDSelection();
    }

    private void refreshThreeDSelection() {
        GeDataModel selected = selectedLayer.get();
        threeDBoxesByLayer.forEach((layer, box) -> {
            PhongMaterial material = (PhongMaterial) box.getMaterial();
            if (layer == selected) {
                material.setDiffuseColor(Color.web("#FFD166"));
                material.setSpecularColor(Color.WHITE);
            } else {
                material.setDiffuseColor(Color.web(colorForLayer(layer)));
                material.setSpecularColor(Color.web("#333333"));
            }
        });
    }

    private void applyThreeDDefaultCamera() {
        threeDRotateY.setAngle(-35);
        threeDRotateX.setAngle(-20);
        threeDTranslate.setZ(-Math.max(400, threeDStackHeight * 2.4));
    }

    private void resetThreeDCamera() {
        applyThreeDDefaultCamera();
    }

    private void exportThreeDImage() {
        if (threeDBoxesByLayer.isEmpty()) {
            showError("Image Not Ready", "Please complete energy calculation first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export 3D Image");
        chooser.setInitialFileName("3d-layers.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File file = chooser.showSaveDialog(dataTable.getScene().getWindow());
        if (file == null) return;
        try {
            WritableImage image = threeDSubScene.snapshot(new SnapshotParameters(), null);
            BufferedImage bufferedImage = new BufferedImage(
                    (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < bufferedImage.getHeight(); y++) {
                for (int x = 0; x < bufferedImage.getWidth(); x++) {
                    bufferedImage.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            if (!ImageIO.write(bufferedImage, "png", file)) {
                throw new IOException("No PNG writer is available.");
            }
            statusLabel.setText("3D image saved to " + file.getName() + ".");
            appendLog("导出三维视图截图 " + file.getName());
        } catch (IOException e) {
            showError("Export Error", "Could not export the image: " + readableMessage(e));
        }
    }

    private boolean hasPositiveEnergy(GeDataModel layer) {
        return layer.getPower() != null && layer.getPower().signum() > 0;
    }

    private void refreshTableAndVisualization() {
        tableData.setAll(geDataModels);
        dataTable.refresh();
        drawRockLayersVisualization();
    }

    private void drawRockLayersVisualization() {
        visualizationPane.getChildren().clear();
        profileBarsByLayer.clear();
        if (tableData.isEmpty()) return;
        double currentY = 0;
        double paneWidth = visualizationPane.getWidth() > 0 ? visualizationPane.getWidth() : 250;

        for (GeDataModel layer : tableData) {
            double thickness = layer.getH() == null ? 1.0 : Math.max(1.0, layer.getH().doubleValue());
            double layerHeight = Math.max(18, thickness * 5);

            Region bar = new Region();
            bar.getStyleClass().add("profile-bar");
            bar.setLayoutX(0);
            bar.setLayoutY(currentY);
            bar.setPrefSize(paneWidth, layerHeight);
            bar.setMinSize(paneWidth, layerHeight);
            bar.setStyle("-fx-background-color: " + colorForLayer(layer) + ";");
            bar.setOnMouseClicked(event -> selectedLayer.set(layer));
            visualizationPane.getChildren().add(bar);
            profileBarsByLayer.put(layer, bar);

            if (layer.isKeyLayer()) {
                Region accent = new Region();
                accent.setLayoutX(0);
                accent.setLayoutY(currentY);
                accent.setPrefSize(3, layerHeight);
                accent.setStyle("-fx-background-color: -primary;");
                accent.setMouseTransparent(true);
                visualizationPane.getChildren().add(accent);
            }

            if (layer.isKeyLayer() && !layer.isNotCrack()) {
                Line fracture = new Line(0, currentY + layerHeight / 2.0, paneWidth, currentY + layerHeight / 2.0);
                fracture.setStroke(Color.web("#C0492E"));
                fracture.getStrokeDashArray().addAll(4d, 3d);
                fracture.setMouseTransparent(true);
                visualizationPane.getChildren().add(fracture);
            }

            if (layerHeight >= 20) {
                Label label = new Label(layer.getNum() + "  " + safe(layer.getName())
                        + "   " + formatPlain(layer.getH()) + " m");
                label.getStyleClass().add(isDarkLithology(layer) ? "profile-label-dark" : "profile-label");
                label.setLayoutX(10);
                label.setLayoutY(currentY + Math.max(0, layerHeight / 2 - 8));
                label.setMouseTransparent(true);
                visualizationPane.getChildren().add(label);
            }

            currentY += layerHeight;
        }
        visualizationPane.setPrefHeight(currentY);
        refreshProfileSelection();
    }

    private void refreshProfileSelection() {
        GeDataModel selected = selectedLayer.get();
        profileBarsByLayer.forEach((layer, bar) -> {
            if (layer == selected) {
                if (!bar.getStyleClass().contains("profile-bar-selected")) {
                    bar.getStyleClass().add("profile-bar-selected");
                }
            } else {
                bar.getStyleClass().remove("profile-bar-selected");
            }
        });
    }

    private void syncTableSelection() {
        GeDataModel selected = selectedLayer.get();
        if (dataTable.getSelectionModel().getSelectedItem() != selected) {
            if (selected == null) {
                dataTable.getSelectionModel().clearSelection();
            } else {
                dataTable.getSelectionModel().select(selected);
            }
        }
    }

    private String colorForLayer(GeDataModel layer) {
        if (layer.getName() == null) return DEFAULT_LITHOLOGY_COLOR;
        return LITHOLOGY_COLORS.getOrDefault(layer.getName().trim(), DEFAULT_LITHOLOGY_COLOR);
    }

    private boolean isDarkLithology(GeDataModel layer) {
        return layer.getName() != null && layer.getName().contains("煤");
    }

    /* ==================== Selected-layer detail panel ==================== */

    private void refreshDetailPanel() {
        detailPanel.getChildren().clear();
        GeDataModel layer = selectedLayer.get();

        if (layer == null) {
            Label empty = new Label("尚未选中岩层");
            empty.getStyleClass().add("text-soft");
            Label hint = new Label("在剖面图或数据表中点击任一岩层，此处显示其输入参数与计算结果。");
            hint.getStyleClass().add("text-caption");
            hint.setWrapText(true);
            VBox box = new VBox(10, empty, hint);
            box.setAlignment(Pos.TOP_CENTER);
            detailPanel.getChildren().add(box);
            return;
        }

        HBox header = new HBox(9);
        header.setAlignment(Pos.CENTER_LEFT);
        Label numBadge = new Label(String.valueOf(layer.getNum()));
        numBadge.getStyleClass().addAll("layer-num-badge", "mono-num");
        Label nameLabel = new Label(safe(layer.getName()));
        nameLabel.getStyleClass().add("text-app-name");
        header.getChildren().addAll(numBadge, nameLabel);

        HBox tags = new HBox(6);
        tags.getChildren().add(makeTag(layer.isKeyLayer() ? "关键层" : "非关键层",
                layer.isKeyLayer() ? "tag-key" : "tag-nokey"));
        if (layer.isKeyLayer()) {
            tags.getChildren().add(makeTag(layer.isNotCrack() ? "未垮落" : "已垮落",
                    layer.isNotCrack() ? "tag-nocrack" : "tag-crack"));
        }
        if (hasPositiveEnergy(layer)) {
            tags.getChildren().add(makeTag("能量 " + formatPlain2(layer.getPower()) + " MJ", "tag-energy"));
        }

        VBox inputSection = buildFieldSection("输入参数", List.of(
                new String[]{"层厚 H", formatPlain(layer.getH()), "m"},
                new String[]{"体积力 L", formatPlain4(layer.getL()), "MN/m³"},
                new String[]{"弹性模量 E", formatPlain(layer.getE()), "MPa"},
                new String[]{"抗拉强度 R", formatPlain(layer.getR()), "MPa"},
                new String[]{"推进距离 ax / by", formatPlain(layer.getAx()) + " / " + formatPlain(layer.getBy()), "m"},
                new String[]{"破断角 af", formatPlain(layer.getAf()), "°"},
                new String[]{"岩梁宽度 b · 碎胀系数 w", formatPlain(layer.getB()) + " · " + formatPlain(layer.getW()), ""}
        ));

        VBox resultSection = buildFieldSection("计算结果", List.of(
                new String[]{"是否关键层", layer.isKeyLayer() ? "是" : "否", ""},
                new String[]{"垮落状态", layer.isKeyLayer() ? (layer.isNotCrack() ? "未垮落" : "已垮落") : "不适用", ""},
                new String[]{"破断步距 lastAi", formatPlain(layer.getLastAi()), "m"},
                new String[]{"卸荷回弹量 shi", formatPlain4(layer.getShi()), "m"},
                new String[]{"最大位移量 uix", formatPlain4(layer.getUix()), "m"},
                new String[]{"弹性能 Power", formatPlain2(layer.getPower()), "MJ"}
        ));

        VBox content = new VBox(10, header, tags, new Separator(), inputSection, resultSection);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        detailPanel.getChildren().add(scroll);
    }

    private Node makeTag(String text, String styleClass) {
        Label tag = new Label(text);
        tag.getStyleClass().addAll("tag", styleClass);
        return tag;
    }

    private VBox buildFieldSection(String title, List<String[]> rows) {
        Label heading = new Label(title);
        heading.getStyleClass().add("text-caption");
        VBox section = new VBox(4, heading);
        for (String[] row : rows) {
            HBox line = new HBox();
            line.setAlignment(Pos.CENTER_LEFT);
            Label k = new Label(row[0]);
            k.getStyleClass().add("text-caption");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label v = new Label(row[2].isBlank() ? row[1] : row[1] + " " + row[2]);
            v.getStyleClass().addAll("text-body", "mono-num");
            line.getChildren().addAll(k, spacer, v);
            section.getChildren().add(line);
        }
        return section;
    }

    private String formatPlain(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private String formatPlain2(BigDecimal v) {
        return v == null ? "-" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPlain4(BigDecimal v) {
        return v == null ? "-" : v.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /* ==================== Import validation ==================== */

    private record ValidationIssue(int rowNumber, String field, String reason, String fix) { }

    private List<ValidationIssue> validateImportedData(List<GeDataModel> models) {
        List<ValidationIssue> issues = new ArrayList<>();
        Set<Integer> seenNums = new HashSet<>();
        for (int i = 0; i < models.size(); i++) {
            GeDataModel model = models.get(i);
            int rowNumber = i + 2; // header occupies row 1; data starts at row 2

            if (model.getName() == null || model.getName().isBlank()) {
                issues.add(new ValidationIssue(rowNumber, "岩层名称 name", "单元格为空，缺少必填字段", "填写该层的岩层名称"));
            }
            if (model.getH() == null) {
                issues.add(new ValidationIssue(rowNumber, "层厚 h", "单元格为空，缺少必填字段", "填写该层厚度（m）"));
            } else if (model.getH().signum() <= 0) {
                issues.add(new ValidationIssue(rowNumber, "层厚 h", "层厚必须大于 0", "检查数值是否录入正确"));
            }
            if (model.getL() == null) {
                issues.add(new ValidationIssue(rowNumber, "体积力 l", "单元格为空，缺少必填字段", "填写该层体积力（MN/m³）"));
            }
            if (model.getE() == null) {
                issues.add(new ValidationIssue(rowNumber, "弹性模量 E", "单元格为空，缺少必填字段", "填写该层弹性模量（MPa）"));
            }
            if (model.getR() == null) {
                issues.add(new ValidationIssue(rowNumber, "抗拉强度 R", "单元格为空，缺少必填字段", "填写该层抗拉强度（MPa）"));
            }
            if (!seenNums.add(model.getNum())) {
                issues.add(new ValidationIssue(rowNumber, "当前层数 num",
                        "层号 " + model.getNum() + " 与之前某行重复，层序不连续", "按自下向上重新编号，确保层号唯一"));
            }
        }
        return issues;
    }

    private void showValidationDialog(File file, int rowCount, List<ValidationIssue> issues) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("数据校验未通过");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label header = new Label("发现 " + issues.size() + " 处错误。必须修复后才能导入该文件。");
        header.getStyleClass().add("text-body");
        Label meta = new Label("文件名：" + file.getName() + "    识别行数：" + rowCount);
        meta.getStyleClass().add("text-caption");

        TableView<ValidationIssue> table = new TableView<>(FXCollections.observableArrayList(issues));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefSize(680, 280);

        TableColumn<ValidationIssue, Number> rowCol = new TableColumn<>("行号");
        rowCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().rowNumber()));
        TableColumn<ValidationIssue, String> fieldCol = new TableColumn<>("字段");
        fieldCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().field()));
        TableColumn<ValidationIssue, String> reasonCol = new TableColumn<>("问题原因");
        reasonCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().reason()));
        TableColumn<ValidationIssue, String> fixCol = new TableColumn<>("建议修复方式");
        fixCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fix()));
        table.getColumns().addAll(rowCol, fieldCol, reasonCol, fixCol);

        VBox content = new VBox(10, header, meta, table);
        content.setPrefWidth(700);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Geological Key Layer Calculator");
        alert.setContentText("This program calculates key geological layers, collapse potential, and energy distribution from imported Excel data.");
        alert.showAndWait();
    }

    @FXML
    private void handleExit() {
        ((Stage) dataTable.getScene().getWindow()).close();
    }
}
