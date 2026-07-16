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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MainController {

    private enum WorkflowState {
        EMPTY, IMPORTED, KEY_LAYERS, COLLAPSE, ENERGY
    }

    private record ThreeDResult(File outputFile, String processOutput) { }

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
    @FXML private Button calculateKeyLayersButton;
    @FXML private Button calculateCollapseButton;
    @FXML private Button calculateEnergyButton;
    @FXML private Button generate3DButton;
    @FXML private Button exportButton;
    @FXML private MenuItem importMenuItem;
    @FXML private MenuItem exportMenuItem;

    private List<GeDataModel> geDataModels;
    private final ObservableList<GeDataModel> tableData = FXCollections.observableArrayList();
    private WorkflowState workflowState = WorkflowState.EMPTY;
    private boolean busy;
    private Stage energyStage;
    private EnergyController energyController;

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
        dataTable.setItems(tableData);

        visualizationPane.widthProperty().addListener((observable, oldValue, newValue) -> {
            if (!tableData.isEmpty()) {
                drawRockLayersVisualization();
            }
        });
        updateControls();
    }

    private void updateControls() {
        importButton.setDisable(busy);
        importMenuItem.setDisable(busy);
        calculateKeyLayersButton.setDisable(busy || workflowState != WorkflowState.IMPORTED);
        calculateCollapseButton.setDisable(busy || workflowState != WorkflowState.KEY_LAYERS);
        calculateEnergyButton.setDisable(busy || workflowState != WorkflowState.COLLAPSE);
        generate3DButton.setDisable(busy || workflowState != WorkflowState.ENERGY);
        boolean exportUnavailable = workflowState.ordinal() < WorkflowState.COLLAPSE.ordinal();
        exportButton.setDisable(busy || exportUnavailable);
        exportMenuItem.setDisable(busy || exportUnavailable);
    }

    private void resetData() {
        workflowState = WorkflowState.EMPTY;
        geDataModels = null;
        tableData.clear();
        visualizationPane.getChildren().clear();
        if (energyStage != null) {
            energyStage.close();
            energyStage = null;
            energyController = null;
        }
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
        runTask(
                "Importing data from " + selectedFile.getName() + "...",
                () -> dataImporter.getData(selectedFile.getAbsolutePath()),
                imported -> {
                    if (imported == null || imported.isEmpty()) {
                        throw new IllegalStateException("No data was found in the selected file.");
                    }
                    geDataModels = imported;
                    tableData.setAll(imported);
                    workflowState = WorkflowState.IMPORTED;
                    drawRockLayersVisualization();
                    statusLabel.setText("Successfully imported " + imported.size() + " records. Ready to calculate key layers.");
                },
                "Import Error");
    }

    @FXML
    private void handleCalculateKeyLayers() {
        if (!requireState(WorkflowState.IMPORTED, "Please import data before calculating key layers.")) return;
        runTask(
                "Calculating key layers...",
                () -> layerLoadCalculator.findKeyLayers(geDataModels),
                result -> {
                    geDataModels = requireResult(result, "Key layer calculation returned no data.");
                    workflowState = WorkflowState.KEY_LAYERS;
                    refreshTableAndVisualization();
                    statusLabel.setText("Key layers calculated successfully. Ready for collapse analysis.");
                },
                "Key Layer Calculation Error");
    }

    @FXML
    private void handleCalculateCollapse() {
        if (!requireState(WorkflowState.KEY_LAYERS, "Please calculate key layers first.")) return;
        runTask(
                "Calculating layer collapse...",
                () -> keyLayerAnalyzer.compute(geDataModels),
                result -> {
                    geDataModels = requireResult(result, "Collapse calculation returned no data.");
                    workflowState = WorkflowState.COLLAPSE;
                    refreshTableAndVisualization();
                    statusLabel.setText("Collapse calculation complete. Ready for energy calculation or export.");
                },
                "Collapse Calculation Error");
    }

    @FXML
    private void handleCalculateEnergy() {
        if (!requireState(WorkflowState.COLLAPSE, "Please complete collapse analysis first.")) return;
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
                    openEnergyView();
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
                showError(errorTitle, readableMessage(e));
                statusLabel.setText("Status: Operation failed.");
            } finally {
                busy = false;
                updateControls();
            }
        });
        task.setOnFailed(event -> {
            busy = false;
            updateControls();
            Throwable error = task.getException();
            showError(errorTitle, readableMessage(error));
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

    private void openEnergyView() {
        try {
            if (energyStage == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/EnergyView.fxml"));
                Parent root = loader.load();
                energyController = loader.getController();
                energyStage = new Stage();
                energyStage.setTitle("Energy Visualization");
                energyStage.initOwner(dataTable.getScene().getWindow());
                energyStage.setScene(new Scene(root, 900, 700));
                energyStage.setOnHidden(event -> {
                    energyStage = null;
                    energyController = null;
                });
            }
            energyController.setData(geDataModels);
            energyStage.show();
            energyStage.toFront();
        } catch (Exception e) {
            showError("View Error", "Failed to open energy view:\n" + readableMessage(e));
        }
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
                exported -> statusLabel.setText("Data successfully exported to " + exported.getName() + "."),
                "Export Error");
    }

    @FXML
    private void handleGenerate3D() {
        if (!requireState(WorkflowState.ENERGY, "Please complete energy calculation first.")) return;

        BigDecimal[] dimensions = findDimensions();
        if (dimensions == null) {
            showError("Missing Data", "Cannot find positive ax and by values in the data.");
            return;
        }
        runTask(
                "Generating 3D image...",
                () -> generate3DImage(dimensions[0], dimensions[1]),
                this::show3DResult,
                "3D Generation Error");
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
        candidates.add("/usr/bin/python3");
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
        statusLabel.setText("3D image generated successfully.");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("3D Image Generated");
        alert.setHeaderText("3D layer visualization created");
        alert.setContentText("Temporary image:\n" + result.outputFile().getAbsolutePath());
        ButtonType open = new ButtonType("Open Image");
        ButtonType save = new ButtonType("Save As...");
        ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(open, save, close);
        alert.showAndWait().ifPresent(response -> {
            if (response == open) openFile(result.outputFile());
            if (response == save) save3DImage(result.outputFile());
        });
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
        if (energyController != null) energyController.setData(geDataModels);
    }

    private void drawRockLayersVisualization() {
        visualizationPane.getChildren().clear();
        if (tableData.isEmpty()) return;
        double currentY = 0;
        double paneWidth = visualizationPane.getWidth() > 0 ? visualizationPane.getWidth() : 250;
        double rectWidth = paneWidth * 0.8;
        double xPos = (paneWidth - rectWidth) / 2;

        for (GeDataModel layer : tableData) {
            double thickness = layer.getH() == null ? 1.0 : Math.max(1.0, layer.getH().doubleValue());
            double layerHeight = thickness * 5;
            Rectangle rect = new Rectangle(xPos, currentY, rectWidth, layerHeight);
            if (layer.isKeyLayer()) {
                rect.setFill(Color.LIGHTCORAL);
                rect.setStroke(Color.DARKRED);
            } else {
                rect.setFill(Color.LIGHTGRAY);
                rect.setStroke(Color.BLACK);
            }
            if (layer.isNotCrack()) {
                rect.setStyle("-fx-fill: linear-gradient(from 0% 0% to 100% 100%, #FF6347 0%, #FF6347 25%, #CD5C5C 25%, #CD5C5C 50%, #FF6347 50%, #FF6347 75%, #CD5C5C 75%, #CD5C5C 100%);");
            }
            Text text = new Text(xPos + 10, currentY + layerHeight / 2 + 5,
                    layer.getNum() + ": " + (layer.getName() == null ? "" : layer.getName()));
            visualizationPane.getChildren().addAll(rect, text);
            currentY += layerHeight;
        }
        visualizationPane.setPrefHeight(currentY);
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
