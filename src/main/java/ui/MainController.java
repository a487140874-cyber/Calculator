package ui;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.example.calculator.EnergyCalculator;
import com.example.calculator.KeyLayerAnalyzer;
import com.example.calculator.LayerLoadCalculator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import geModel.GeDataModel;
import geModel.GeDataModelExcel;
import geModel.GeDataModelMapper;
import getData.GetDateFromExcle;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.geometry.Insets;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MainController {

    private enum WorkflowState {
        EMPTY, IMPORTED, KEY_LAYERS, COLLAPSE, ENERGY
    }

    private enum StepStatus { DONE, READY, RUNNING, LOCKED }

    /** The center viewer shows one of these at a time; the pill row above it switches between them. */
    private enum ViewMode {
        PROFILE("岩层剖面"), ENERGY("能量分析"), THREE_D("三维视图");

        private final String label;

        ViewMode(String label) { this.label = label; }
    }

    private record ThreeDResult(File outputFile, String processOutput) { }

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

    @FXML private BorderPane rootPane;
    @FXML private Button importButton;
    @FXML private Button themeToggleButton;
    @FXML private javafx.scene.control.MenuButton exportButton;
    @FXML private MenuItem exportImageMenuItem;
    @FXML private MenuItem importMenuItem;
    @FXML private MenuItem exportMenuItem;

    private boolean darkTheme = true;

    @FXML private Label fileNameLabel;
    @FXML private Label fileBadgeLabel;
    @FXML private VBox stepNav;
    @FXML private HBox viewPillsBox;
    @FXML private StackPane viewerStack;
    @FXML private VBox profileViewBox;
    @FXML private Label scaleInfoLabel;
    @FXML private Button exportExcelButton;
    @FXML private Button exportImageButton;
    @FXML private HBox statCardsBox;
    @FXML private VBox detailPanel;
    @FXML private VBox overviewSideBox;
    @FXML private HBox filterBox;
    @FXML private javafx.scene.control.TextField searchField;
    @FXML private VBox energyTabBox;
    @FXML private VBox d3TabBox;

    private final List<String[]> activityLog = new ArrayList<>();

    private enum ThreeDState { IDLE, GENERATING, READY, FAILED }
    private ThreeDState threeDState = ThreeDState.IDLE;
    private File lastThreeDImage;
    private String lastThreeDError;
    private long threeDStartMillis;
    private Timeline threeDElapsedTimeline;

    private static final String[] TABLE_FILTERS = {"全部", "关键层", "已垮落", "未垮落", "能量聚集"};
    private String activeTableFilter = TABLE_FILTERS[0];

    private static final Map<String, String> LITHOLOGY_COLORS = Map.ofEntries(
            Map.entry("泥岩", "#7D7468"),
            Map.entry("砂质泥岩", "#A38C6C"),
            Map.entry("细砂岩", "#E0CE96"),
            Map.entry("中砂岩", "#D2A03F"),
            Map.entry("粗砂岩", "#96562B"),
            Map.entry("石灰岩", "#74A6B8"),
            Map.entry("砂岩", "#D9A06B"),
            Map.entry("页岩", "#4A6A85"),
            Map.entry("煤层", "#2E2E33"),
            Map.entry("煤", "#2E2E33")
    );
    private static final String DEFAULT_LITHOLOGY_COLOR = "#C2CBD4";

    private List<GeDataModel> geDataModels;
    private final ObservableList<GeDataModel> tableData = FXCollections.observableArrayList();
    private final FilteredList<GeDataModel> filteredTableData = new FilteredList<>(tableData, m -> true);
    private final ObjectProperty<GeDataModel> selectedLayer = new SimpleObjectProperty<>();
    private final Map<GeDataModel, Region> profileBarsByLayer = new IdentityHashMap<>();
    private WorkflowState workflowState = WorkflowState.EMPTY;
    private ViewMode viewMode = ViewMode.PROFILE;
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
        isKeyLayerCol.setCellValueFactory(data -> new SimpleBooleanProperty(data.getValue().isKeyLayer()).asObject());
        isNotCrackCol.setCellValueFactory(data -> new SimpleBooleanProperty(data.getValue().isNotCrack()).asObject());
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
        });

        visualizationPane.widthProperty().addListener((observable, oldValue, newValue) -> {
            if (!tableData.isEmpty()) {
                drawRockLayersVisualization();
            }
        });
        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> applyTableFilter());
        }
        rootPane.getStyleClass().add("theme-dark");
        themeToggleButton.setText("浅色模式");

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
        exportImageMenuItem.setDisable(busy || lastThreeDImage == null);
        if (exportExcelButton != null) exportExcelButton.setDisable(busy || exportUnavailable);
        if (exportImageButton != null) exportImageButton.setDisable(busy || lastThreeDImage == null);

        // The selected view may have become unavailable (e.g. after resetData).
        if (!viewAvailable(viewMode)) viewMode = ViewMode.PROFILE;

        updateFileBadge();
        refreshStepNav();
        refreshStatCards();
        refreshTableFilters();
        refreshEnergyTab();
        refreshD3Tab();
        refreshViewPills();
        applyViewMode();
        refreshScaleInfo();
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
        s[4] = activeStepIndex == 4 ? StepStatus.RUNNING
                : (workflowState.ordinal() < WorkflowState.ENERGY.ordinal() ? StepStatus.LOCKED : StepStatus.READY);
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
        note.setMinHeight(Region.USE_PREF_SIZE);
        note.setMaxWidth(190);
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
            case 4 -> "可执行";
            case 5 -> "可导出 Excel";
            default -> "";
        };
    }

    private void onStepClicked(int index, StepStatus status) {
        switch (index) {
            case 0 -> handleImportExcel();
            case 1 -> { if (status == StepStatus.READY) handleCalculateKeyLayers(); else selectView(ViewMode.PROFILE); }
            case 2 -> { if (status == StepStatus.READY) handleCalculateCollapse(); else selectView(ViewMode.PROFILE); }
            case 3 -> { if (status == StepStatus.READY) handleCalculateEnergy(); else selectView(ViewMode.ENERGY); }
            case 4 -> { if (status == StepStatus.READY) handleGenerate3D(); else selectView(ViewMode.THREE_D); }
            case 5 -> handleExportExcel();
            default -> { }
        }
    }

    /* ==================== Center viewer switcher ==================== */

    private boolean viewAvailable(ViewMode mode) {
        return switch (mode) {
            case PROFILE -> true;
            case ENERGY -> workflowState.ordinal() >= WorkflowState.ENERGY.ordinal();
            case THREE_D -> workflowState == WorkflowState.ENERGY;
        };
    }

    private void selectView(ViewMode mode) {
        if (!viewAvailable(mode)) return;
        viewMode = mode;
        refreshViewPills();
        applyViewMode();
        refreshScaleInfo();
    }

    private void applyViewMode() {
        if (viewerStack == null) return;
        setViewVisible(profileViewBox, viewMode == ViewMode.PROFILE);
        setViewVisible(energyTabBox, viewMode == ViewMode.ENERGY);
        setViewVisible(d3TabBox, viewMode == ViewMode.THREE_D);
    }

    private void setViewVisible(Node node, boolean visible) {
        if (node == null) return;
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void refreshViewPills() {
        if (viewPillsBox == null) return;
        viewPillsBox.getChildren().clear();
        for (ViewMode mode : ViewMode.values()) {
            Button pill = new Button(mode.label);
            pill.getStyleClass().add("view-pill");
            if (mode == viewMode) {
                pill.getStyleClass().add("view-pill-active");
            }
            pill.setDisable(!viewAvailable(mode));
            pill.setOnAction(event -> selectView(mode));
            viewPillsBox.getChildren().add(pill);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label hint = new Label(switch (viewMode) {
            case PROFILE -> "点击色块选中岩层";
            case ENERGY -> "按弹性能大小排序";
            case THREE_D -> "由 Python 渲染的静态三维图";
        });
        hint.getStyleClass().add("text-caption");
        viewPillsBox.getChildren().addAll(spacer, hint);
    }

    /** Dimension / scale readout floating over the viewer, mirroring the prototype's scale box. */
    private void refreshScaleInfo() {
        if (scaleInfoLabel == null) return;
        if (geDataModels == null || geDataModels.isEmpty() || viewMode != ViewMode.PROFILE) {
            setViewVisible(scaleInfoLabel, false);
            return;
        }
        BigDecimal totalThickness = BigDecimal.ZERO;
        for (GeDataModel layer : geDataModels) {
            if (layer.getH() != null) totalThickness = totalThickness.add(layer.getH());
        }
        BigDecimal[] dimensions = findDimensions();
        StringBuilder text = new StringBuilder();
        text.append("岩层数：").append(geDataModels.size()).append(" 层\n");
        text.append("总厚度：").append(formatPlain(totalThickness)).append(" m\n");
        if (dimensions != null) {
            text.append("推进距离 ax：").append(formatPlain(dimensions[0]))
                    .append(" m　剖面宽度 by：").append(formatPlain(dimensions[1])).append(" m");
        } else {
            text.append("推进距离 ax / 剖面宽度 by：数据中未提供");
        }
        scaleInfoLabel.setText(text.toString());
        setViewVisible(scaleInfoLabel, true);
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
        VBox list = new VBox(2);
        for (String[] entry : activityLog) {
            Label msg = new Label(entry[1]);
            msg.getStyleClass().add("text-body");
            msg.setWrapText(true);
            msg.setMinHeight(Region.USE_PREF_SIZE);
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
        overviewSideBox.getChildren().setAll(scroll);
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
        stopThreeDElapsedTimer();
        threeDState = ThreeDState.IDLE;
        lastThreeDImage = null;
        lastThreeDError = null;
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
                    selectView(ViewMode.ENERGY);
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
        if (lastThreeDImage == null || !lastThreeDImage.isFile()) {
            showError("Image Not Ready", "Please generate the 3D layer image first.");
            return;
        }
        save3DImage(lastThreeDImage);
    }

    @FXML
    private void handleGenerate3D() {
        if (!requireState(WorkflowState.ENERGY, "Please complete energy calculation first.")) return;

        BigDecimal[] dimensions = findDimensions();
        if (dimensions == null) {
            showError("Missing Data", "Cannot find positive ax and by values in the data.");
            return;
        }
        activeStepIndex = 4;
        threeDState = ThreeDState.GENERATING;
        threeDStartMillis = System.currentTimeMillis();
        startThreeDElapsedTimer();
        refreshD3Tab();
        runTask(
                "Generating 3D image...",
                () -> generate3DImage(dimensions[0], dimensions[1]),
                this::show3DResult,
                "3D Generation Error",
                this::handleThreeDFailure);
    }

    private void handleThreeDFailure(Throwable error) {
        stopThreeDElapsedTimer();
        threeDState = ThreeDState.FAILED;
        lastThreeDError = readableMessage(error);
        appendLog("三维岩层图生成失败：" + lastThreeDError);
        refreshD3Tab();
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

    private ThreeDResult generate3DImage(BigDecimal ax, BigDecimal by) throws Exception {
        File jsonFile = Files.createTempFile("layer_data_", ".json").toFile();
        File scriptFile = Files.createTempFile("generate_3d_layers_", ".py").toFile();
        File outputFile = Files.createTempFile("3d_layers_", ".png").toFile();
        if (!outputFile.delete()) throw new IOException("Unable to prepare the 3D output file.");

        try {
            Files.writeString(jsonFile.toPath(), generateLayerDataJson(geDataModels, ax, by), StandardCharsets.UTF_8);
            try (InputStream script = getClass().getResourceAsStream("/python/generate_3d_layers.py")) {
                if (script == null) throw new IOException("Bundled 3D Python script was not found.");
                Files.copy(script, scriptFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            String python = findPythonExecutable();
            ProcessBuilder builder = new ProcessBuilder(
                    python, scriptFile.getAbsolutePath(), jsonFile.getAbsolutePath(), outputFile.getAbsolutePath());
            builder.redirectErrorStream(true);
            builder.environment().put("MPLCONFIGDIR", System.getProperty("java.io.tmpdir"));
            builder.environment().put("XDG_CACHE_HOME", System.getProperty("java.io.tmpdir"));
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            int exitCode = process.waitFor();
            if (exitCode != 0 || !outputFile.isFile()) {
                throw new IOException("Python exited with code " + exitCode + ".\n" + output);
            }
            return new ThreeDResult(outputFile, output.toString());
        } finally {
            Files.deleteIfExists(jsonFile.toPath());
            Files.deleteIfExists(scriptFile.toPath());
        }
    }

    private String findPythonExecutable() throws IOException, InterruptedException {
        Set<String> candidates = new LinkedHashSet<>();
        String configured = System.getenv("PYTHON_EXECUTABLE");
        if (configured != null && !configured.isBlank()) candidates.add(configured);
        candidates.add("python3");
        // macOS 上从访达启动的 .app 不继承 shell 的 PATH（只有 /usr/bin:/bin:/usr/sbin:/sbin），
        // 因此 Homebrew、python.org 安装的解释器必须按绝对路径显式探测，否则只能找到
        // 系统自带的 /usr/bin/python3——而它默认不含 NumPy/Matplotlib。
        candidates.add("/opt/homebrew/bin/python3");   // Homebrew（Apple Silicon）
        candidates.add("/usr/local/bin/python3");      // Homebrew（Intel）/ python.org
        candidates.add("/Library/Frameworks/Python.framework/Versions/Current/bin/python3");
        candidates.add("/usr/bin/python3");            // 系统自带（需 pip install --user）
        candidates.add("python");

        for (String candidate : candidates) {
            try {
                ProcessBuilder probeBuilder = new ProcessBuilder(
                        candidate, "-c", "import numpy, matplotlib");
                probeBuilder.redirectErrorStream(true);
                probeBuilder.environment().put("MPLCONFIGDIR", System.getProperty("java.io.tmpdir"));
                probeBuilder.environment().put("XDG_CACHE_HOME", System.getProperty("java.io.tmpdir"));
                Process probe = probeBuilder.start();
                try (InputStream output = probe.getInputStream()) {
                    output.transferTo(java.io.OutputStream.nullOutputStream());
                }
                if (probe.waitFor() == 0) return candidate;
            } catch (IOException ignored) {
                // Continue probing the remaining standard Python locations.
            }
        }
        throw new IOException("No Python interpreter with NumPy and Matplotlib was found. "
                + "Install the packages or set PYTHON_EXECUTABLE to a compatible Python executable.");
    }

    private void show3DResult(ThreeDResult result) {
        stopThreeDElapsedTimer();
        threeDState = ThreeDState.READY;
        lastThreeDImage = result.outputFile();
        statusLabel.setText("3D image generated successfully.");
        appendLog("三维岩层图生成完成");
        selectView(ViewMode.THREE_D);
        refreshD3Tab();
    }

    /* ==================== 3D view tab ==================== */

    private void refreshD3Tab() {
        if (d3TabBox == null) return;
        d3TabBox.getChildren().clear();
        d3TabBox.setAlignment(Pos.CENTER);

        switch (threeDState) {
            case GENERATING -> d3TabBox.getChildren().add(buildD3GeneratingView());
            case FAILED -> d3TabBox.getChildren().add(buildD3FailedView());
            case READY -> d3TabBox.getChildren().add(buildD3ReadyView());
            default -> d3TabBox.getChildren().add(buildD3IdleView());
        }
    }

    private Node buildD3IdleView() {
        Label hint = new Label(workflowState == WorkflowState.ENERGY
                ? "点击下方按钮生成三维岩层图" : "完成能量分析后即可生成三维岩层图");
        hint.getStyleClass().add("text-soft");
        Button generate = new Button("生成三维岩层图");
        generate.getStyleClass().add("btn-primary");
        generate.setDisable(busy || workflowState != WorkflowState.ENERGY);
        generate.setOnAction(event -> handleGenerate3D());
        VBox box = new VBox(12, hint, generate);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Node buildD3GeneratingView() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(34, 34);
        Label label = new Label("正在生成三维岩层图…");
        label.getStyleClass().add("text-body");
        Label elapsed = new Label(elapsedText());
        elapsed.setId("d3ElapsedLabel");
        elapsed.getStyleClass().addAll("text-caption", "mono-num");
        VBox box = new VBox(12, spinner, label, elapsed);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Node buildD3FailedView() {
        VBox banner = new VBox(8);
        banner.getStyleClass().addAll("banner", "banner-danger");
        banner.setMaxWidth(520);
        Label title = new Label("三维岩层图生成失败");
        title.getStyleClass().add("text-panel-head");
        Label detail = new Label(lastThreeDError == null ? "" : lastThreeDError);
        detail.getStyleClass().addAll("text-body", "mono-num");
        detail.setWrapText(true);
        Button retry = new Button("重试生成");
        retry.getStyleClass().add("btn-primary");
        retry.setOnAction(event -> handleGenerate3D());
        banner.getChildren().addAll(title, detail, retry);
        return banner;
    }

    private Node buildD3ReadyView() {
        ImageView imageView = new ImageView(new Image(lastThreeDImage.toURI().toString()));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(760);

        Button openBtn = new Button("打开图片");
        openBtn.getStyleClass().add("btn-secondary");
        openBtn.setOnAction(event -> openFile(lastThreeDImage));
        Button saveBtn = new Button("另存为");
        saveBtn.getStyleClass().add("btn-secondary");
        saveBtn.setOnAction(event -> save3DImage(lastThreeDImage));
        Button regenBtn = new Button("重新生成");
        regenBtn.getStyleClass().add("btn-primary");
        regenBtn.setDisable(busy);
        regenBtn.setOnAction(event -> handleGenerate3D());
        HBox actions = new HBox(8, openBtn, saveBtn, regenBtn);
        actions.setAlignment(Pos.CENTER);

        VBox box = new VBox(12, imageView, actions);
        box.setAlignment(Pos.CENTER);
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    private void startThreeDElapsedTimer() {
        stopThreeDElapsedTimer();
        threeDElapsedTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateThreeDElapsedLabel()));
        threeDElapsedTimeline.setCycleCount(Timeline.INDEFINITE);
        threeDElapsedTimeline.play();
    }

    private void stopThreeDElapsedTimer() {
        if (threeDElapsedTimeline != null) {
            threeDElapsedTimeline.stop();
            threeDElapsedTimeline = null;
        }
    }

    private void updateThreeDElapsedLabel() {
        if (d3TabBox == null) return;
        Node node = d3TabBox.lookup("#d3ElapsedLabel");
        if (node instanceof Label label) {
            label.setText(elapsedText());
        }
    }

    private String elapsedText() {
        double seconds = (System.currentTimeMillis() - threeDStartMillis) / 1000.0;
        return String.format("已用 %.1f s", seconds);
    }

    private void openFile(File file) {
        try {
            if (!Desktop.isDesktopSupported()) throw new IOException("Desktop file opening is not supported.");
            Desktop.getDesktop().open(file);
        } catch (Exception e) {
            showError("Open Error", "Could not open the image file: " + readableMessage(e));
        }
    }

    private void save3DImage(File source) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save 3D Image");
        chooser.setInitialFileName("3d-layers.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File destination = chooser.showSaveDialog(dataTable.getScene().getWindow());
        if (destination == null) return;
        try {
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            statusLabel.setText("3D image saved to " + destination.getName() + ".");
        } catch (IOException e) {
            showError("Save Error", "Could not save the image: " + readableMessage(e));
        }
    }

    private String generateLayerDataJson(List<GeDataModel> layers, BigDecimal ax, BigDecimal by) {
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"ax\": ").append(ax.toPlainString()).append(",\n");
        json.append("  \"by\": ").append(by.toPlainString()).append(",\n  \"layers\": [\n");
        for (int i = 0; i < layers.size(); i++) {
            GeDataModel layer = layers.get(i);
            if (layer.getH() == null || layer.getH().signum() <= 0) {
                throw new IllegalArgumentException("Layer " + layer.getNum() + " has an invalid thickness.");
            }
            String name = layer.getName() == null ? "Layer " + layer.getNum() : layer.getName();
            json.append("    {\"name\": \"").append(escapeJson(name)).append("\", ")
                    .append("\"h\": ").append(layer.getH().toPlainString()).append(", ")
                    .append("\"num\": ").append(layer.getNum());
            if (hasPositiveEnergy(layer)) {
                json.append(", \"power\": ").append(layer.getPower().toPlainString());
            }
            json.append("}");
            if (i < layers.size() - 1) json.append(',');
            json.append('\n');
        }
        return json.append("  ]\n}\n").toString();
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.toString();
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
                fracture.setStyle("-fx-stroke: -accent-red;");
                fracture.getStrokeDashArray().addAll(4d, 3d);
                fracture.setMouseTransparent(true);
                visualizationPane.getChildren().add(fracture);
            }

            if (layerHeight >= 20) {
                Label label = new Label(layer.getNum() + "  " + safe(layer.getName())
                        + "   " + formatPlain(layer.getH()) + " m");
                label.getStyleClass().add(needsLightLabel(layer) ? "profile-label-dark" : "profile-label");
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

    /**
     * Picks whichever profile-label colour contrasts better with the bar's fill. The old
     * "name contains 煤" rule only held while every other lithology was a light beige.
     */
    private boolean needsLightLabel(GeDataModel layer) {
        double bar = relativeLuminance(colorForLayer(layer));
        return contrastRatio(bar, relativeLuminance("#EEF1F3"))
                > contrastRatio(bar, relativeLuminance("#1E2833"));
    }

    private static double contrastRatio(double first, double second) {
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double relativeLuminance(String hexColor) {
        return 0.2126 * channelLuminance(Integer.parseInt(hexColor.substring(1, 3), 16))
                + 0.7152 * channelLuminance(Integer.parseInt(hexColor.substring(3, 5), 16))
                + 0.0722 * channelLuminance(Integer.parseInt(hexColor.substring(5, 7), 16));
    }

    private static double channelLuminance(int value) {
        double channel = value / 255.0;
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
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
            hint.setMinHeight(Region.USE_PREF_SIZE);
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
        alert.setTitle("关于");
        alert.setHeaderText("超长工作面采动覆岩承载结构能量积聚预测系统");
        alert.setContentText("导入岩层 Excel 数据，计算关键层、垮落与破断、能量积聚，并生成三维可视化。");
        alert.showAndWait();
    }

    @FXML
    private void handleExit() {
        ((Stage) dataTable.getScene().getWindow()).close();
    }

    @FXML
    private void handleToggleTheme() {
        darkTheme = !darkTheme;
        if (darkTheme) {
            rootPane.getStyleClass().add("theme-dark");
        } else {
            rootPane.getStyleClass().remove("theme-dark");
        }
        themeToggleButton.setText(darkTheme ? "浅色模式" : "深色模式");
    }
}
