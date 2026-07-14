package ui;

import com.example.calculator.KeyLayerAnalyzer;
import com.example.calculator.LayerLoadCalculator;
import com.example.calculator.EnergyCalculator;
import geModel.GeDataModel;
import geModel.GeDataModelExcel;
import getData.GetDateFromExcle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    // FXML Injected Components
    @FXML private TableView<GeDataModel> dataTable;
    @FXML private TableColumn<GeDataModel, Integer> numCol;
    @FXML private TableColumn<GeDataModel, String> nameCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> hCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> lCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> eCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> rCol;
    @FXML private TableColumn<GeDataModel, String> isKeyLayerCol;
    @FXML private TableColumn<GeDataModel, String> isNotCrackCol;
    @FXML private TableColumn<GeDataModel, BigDecimal> lastAiCol;

    @FXML private Pane visualizationPane;
    @FXML private Label statusLabel;

    @FXML private Button calculateKeyLayersButton;
    @FXML private Button calculateCollapseButton;
    @FXML private Button calculateEnergyButton;
    @FXML private Button generate3DButton;
    @FXML private Button exportButton;


    private List<GeDataModel> geDataModels;
    private final ObservableList<GeDataModel> tableData = FXCollections.observableArrayList();

    // Backend Services
    private final GetDateFromExcle dataImporter = new GetDateFromExcle();
    private final LayerLoadCalculator layerLoadCalculator = new LayerLoadCalculator();
    private final KeyLayerAnalyzer keyLayerAnalyzer = new KeyLayerAnalyzer();
    private final EnergyCalculator energyCalculator = new EnergyCalculator();


    @FXML
    public void initialize() {
        // Set up the table columns to bind to GeDataModel properties
        numCol.setCellValueFactory(new PropertyValueFactory<>("num"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        hCol.setCellValueFactory(new PropertyValueFactory<>("h"));
        lCol.setCellValueFactory(new PropertyValueFactory<>("l"));
        eCol.setCellValueFactory(new PropertyValueFactory<>("e"));
        rCol.setCellValueFactory(new PropertyValueFactory<>("r"));
        isKeyLayerCol.setCellValueFactory(new PropertyValueFactory<>("isKeyLayer"));
        isNotCrackCol.setCellValueFactory(new PropertyValueFactory<>("isNotCrack"));
        lastAiCol.setCellValueFactory(new PropertyValueFactory<>("lastAi"));

        // Set the data source for the table
        dataTable.setItems(tableData);

        // Initially disable buttons until data is loaded
        calculateKeyLayersButton.setDisable(true);
        calculateCollapseButton.setDisable(true);
        generate3DButton.setDisable(true);
        exportButton.setDisable(true);
    }

    @FXML
    private void handleImportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Excel Data File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        File selectedFile = fileChooser.showOpenDialog(dataTable.getScene().getWindow());

        if (selectedFile != null) {
            statusLabel.setText("Importing data from " + selectedFile.getName() + "...");
            try {
                geDataModels = dataImporter.getData(selectedFile.getAbsolutePath());
                if (geDataModels != null && !geDataModels.isEmpty()) {
                    tableData.setAll(geDataModels);
                    statusLabel.setText("Successfully imported " + geDataModels.size() + " records. Ready to calculate.");
                    drawRockLayersVisualization();
                    // Enable the first calculation step
                    calculateKeyLayersButton.setDisable(false);
                    calculateCollapseButton.setDisable(true);
                    exportButton.setDisable(true);

                } else {
                    showError("Import Failed", "No data was found in the selected file.");
                    statusLabel.setText("Status: Import failed.");
                }
            } catch (Exception e) {
                showError("Import Error", "An error occurred while importing the data: \n" + e.getMessage());
                statusLabel.setText("Status: Import error.");
            }
        }
    }

    @FXML
    private void handleCalculateEnergy() {
        if (geDataModels == null || geDataModels.isEmpty()) {
            showError("No Data", "Please import data first.");
            return;
        }

        try {
            statusLabel.setText("Status: Calculating energy...");
            
            // 执行能量计算
            geDataModels = energyCalculator.calculateEnergy(geDataModels);
            
            // 检查是否有能量计算结果
            if (energyCalculator.hasEnergyResults(geDataModels)) {
                long energyLayerCount = energyCalculator.getEnergyLayerCount(geDataModels);
                statusLabel.setText("Status: Energy calculation completed. " + energyLayerCount + " layers have energy values.");
                
                // 刷新表格显示
                refreshTableAndVisualization();
                
                // 启用3D图像生成按钮
                generate3DButton.setDisable(false);
                
                // 打开能量视图
                openEnergyView();
            } else {
                statusLabel.setText("Status: Energy calculation completed, but no energy values were generated.");
            }
            
        } catch (Exception e) {
            showError("Calculation Error", "An error occurred during energy calculation:\n" + e.getMessage());
            statusLabel.setText("Status: Energy calculation failed.");
        }
    }

    private void openEnergyView() {
        try {
            // 加载能量视图FXML
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/ui/EnergyView.fxml"));
            javafx.scene.Parent root = loader.load();
            
            // 获取控制器并设置数据
            EnergyController energyController = loader.getController();
            energyController.setData(geDataModels);
            
            // 创建新窗口
            Stage energyStage = new Stage();
            energyStage.setTitle("Energy Visualization");
            energyStage.setScene(new javafx.scene.Scene(root, 900, 700));
            energyStage.show();
            
            statusLabel.setText("Status: Energy view opened.");
        } catch (Exception e) {
            showError("View Error", "Failed to open energy view:\n" + e.getMessage());
        }
    }

    @FXML
    private void handleCalculateKeyLayers() {
        if (geDataModels == null || geDataModels.isEmpty()) {
            showError("No Data", "Please import data before calculating.");
            return;
        }
        statusLabel.setText("Calculating key layers...");
        try {
            geDataModels = layerLoadCalculator.findKeyLayers(geDataModels);
            refreshTableAndVisualization();
            statusLabel.setText("Key layers calculated successfully. Ready for collapse analysis.");
            calculateCollapseButton.setDisable(false); // Enable next step
        } catch (Exception e) {
            showError("Calculation Error", "An error occurred during key layer calculation:\n" + e.getMessage());
            statusLabel.setText("Status: Key layer calculation failed.");
        }
    }

    @FXML
    private void handleCalculateCollapse() {
        if (geDataModels == null || geDataModels.isEmpty()) {
            showError("No Data", "Please import data and calculate key layers first.");
            return;
        }
        statusLabel.setText("Calculating layer collapse...");
        try {
            // Your backend code seems to combine initialization and computation
            geDataModels = keyLayerAnalyzer.compute(geDataModels);
            refreshTableAndVisualization();
            statusLabel.setText("Collapse calculation complete. Results are ready for export.");
            exportButton.setDisable(false); // Enable export
        } catch (Exception e) {
            showError("Calculation Error", "An error occurred during collapse calculation:\n" + e.getMessage());
            statusLabel.setText("Status: Collapse calculation failed.");
        }
    }

    @FXML
    private void handleExportExcel() {
        if (geDataModels == null || geDataModels.isEmpty()) {
            showError("No Data", "There is no data to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Processed Data");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showSaveDialog(dataTable.getScene().getWindow());

        if (file != null) {
            statusLabel.setText("Exporting data to " + file.getName() + "...");
            try {
                // Convert GeDataModel to GeDataModelExcel for export
                List<GeDataModelExcel> exportList = copyToDto(geDataModels);

                try (ExcelWriter excelWriter = EasyExcel.write(file, GeDataModelExcel.class).build()) {
                    WriteSheet writeSheet = EasyExcel.writerSheet("Processed Data").build();
                    excelWriter.write(exportList, writeSheet);
                }
                statusLabel.setText("Data successfully exported.");
            } catch (Exception e) {
                showError("Export Error", "Failed to export data: " + e.getMessage());
                statusLabel.setText("Status: Export failed.");
            }
        }
    }

    private void refreshTableAndVisualization() {
        dataTable.refresh();
        drawRockLayersVisualization();
    }

    private void drawRockLayersVisualization() {
        visualizationPane.getChildren().clear();
        if (tableData.isEmpty()) return;

        double currentY = 0;
        double paneWidth = visualizationPane.getWidth() > 0 ? visualizationPane.getWidth() : 250; // Use a default width if not yet rendered
        double rectWidth = paneWidth * 0.8;
        double xPos = (paneWidth - rectWidth) / 2;

        for (GeDataModel layer : tableData) {
            double layerHeight = layer.getH().doubleValue() * 5; // Scaling factor for better visibility

            Rectangle rect = new Rectangle(xPos, currentY, rectWidth, layerHeight);

            // Style the rectangles based on their properties
            if ("true".equals(layer.getIsKeyLayer())) {
                rect.setFill(Color.LIGHTCORAL);
                rect.setStroke(Color.DARKRED);
            } else {
                rect.setFill(Color.LIGHTGRAY);
                rect.setStroke(Color.BLACK);
            }

            if ("true".equals(layer.getIsNotCrack())) {
                // 使用JavaFX支持的linear-gradient替代repeating-linear-gradient
                rect.setStyle("-fx-fill: linear-gradient(from 0% 0% to 100% 100%, #FF6347 0%, #FF6347 25%, #CD5C5C 25%, #CD5C5C 50%, #FF6347 50%, #FF6347 75%, #CD5C5C 75%, #CD5C5C 100%);");
            }


            Text layerText = new Text(xPos + 10, currentY + layerHeight / 2 + 5, layer.getNum() + ": " + layer.getName());

            visualizationPane.getChildren().addAll(rect, layerText);

            currentY += layerHeight;
        }
        visualizationPane.setPrefHeight(currentY);
    }

    // Utility method to copy data for export, from your 'test.java'
    private List<GeDataModelExcel> copyToDto(List<GeDataModel> sourceList) {
        List<GeDataModelExcel> targetList = new ArrayList<>();
        for (GeDataModel source : sourceList) {
            GeDataModelExcel target = new GeDataModelExcel();
            // This is a simplified, more robust way to copy properties
            // Assumes GeDataModelExcel has all the relevant fields with the same names
            try {
                for (Field sourceField : GeDataModel.class.getDeclaredFields()) {
                    sourceField.setAccessible(true);
                    try {
                        Field targetField = GeDataModelExcel.class.getDeclaredField(sourceField.getName());
                        targetField.setAccessible(true);
                        if (targetField.getType().equals(sourceField.getType())) {
                            targetField.set(target, sourceField.get(source));
                        }
                    } catch (NoSuchFieldException e) {
                        // Field doesn't exist in target, ignore
                    }
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace(); // Handle error appropriately
            }
            targetList.add(target);
        }
        return targetList;
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
        alert.setContentText("This program calculates key geological layers and collapse potential based on imported Excel data.");
        alert.showAndWait();
    }

    @FXML
    private void handleGenerate3D() {
        if (geDataModels == null || geDataModels.isEmpty()) {
            showError("No Data", "Please import data first.");
            return;
        }

        // 检查是否已完成能量计算
        if (!energyCalculator.hasEnergyResults(geDataModels)) {
            showError("Energy Calculation Required", "Please complete energy calculation before generating 3D image.");
            return;
        }

        try {
            statusLabel.setText("Status: Generating 3D image...");
            
            // 获取ax和by值（从第一个有效数据中获取）
            BigDecimal ax = null, by = null;
            for (GeDataModel model : geDataModels) {
                if (model.getAx() != null && model.getBy() != null) {
                    ax = model.getAx();
                    by = model.getBy();
                    break;
                }
            }
            
            if (ax == null || by == null) {
                showError("Missing Data", "Cannot find ax and by values in the data.");
                return;
            }
            
            // 创建临时JSON文件
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File jsonFile = new File(tempDir, "layer_data_" + System.currentTimeMillis() + ".json");
            File outputFile = new File(tempDir, "3d_layers_" + System.currentTimeMillis() + ".png");
            
            // 生成JSON数据
            String jsonData = generateLayerDataJson(geDataModels, ax, by);
            
            // 写入JSON文件
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(jsonData);
            }
            
            // 调用Python脚本
            // 使用系统默认的python3，它已经安装了所有必需的库
            String pythonExecutable = "/usr/bin/python3";
            String pythonScript = new File("src/main/resources/python/generate_3d_layers.py").getAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, pythonScript, jsonFile.getAbsolutePath(), outputFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // 读取进程输出以便调试
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            
            // 清理临时JSON文件
            jsonFile.delete();
            
            if (exitCode == 0 && outputFile.exists()) {
                statusLabel.setText("Status: 3D image generated successfully.");
                
                // 显示成功对话框并询问是否打开图片
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("3D Image Generated");
                alert.setHeaderText("3D Layer Visualization Created");
                alert.setContentText("3D image has been generated successfully at:\n" + outputFile.getAbsolutePath() + 
                                   "\n\nWould you like to open the image?");
                
                ButtonType openButton = new ButtonType("Open Image");
                ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(openButton, closeButton);
                
                alert.showAndWait().ifPresent(response -> {
                     if (response == openButton) {
                         try {
                             // 使用Runtime.exec打开图片文件
                             String os = System.getProperty("os.name").toLowerCase();
                             if (os.contains("mac")) {
                                 Runtime.getRuntime().exec("open " + outputFile.getAbsolutePath());
                             } else if (os.contains("win")) {
                                 Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + outputFile.getAbsolutePath());
                             } else {
                                 Runtime.getRuntime().exec("xdg-open " + outputFile.getAbsolutePath());
                             }
                         } catch (IOException e) {
                             showError("Open Error", "Could not open the image file: " + e.getMessage());
                         }
                     }
                 });
                
            } else {
                 statusLabel.setText("Status: Failed to generate 3D image.");
                 String errorMessage = "Failed to generate 3D image. Exit code: " + exitCode;
                 if (output.length() > 0) {
                     errorMessage += "\nPython output:\n" + output.toString();
                 }
                 showError("Generation Failed", errorMessage);
             }
            
        } catch (Exception e) {
            statusLabel.setText("Status: 3D image generation failed.");
            showError("Generation Error", "An error occurred while generating 3D image:\n" + e.getMessage());
        }
    }
    
    private String generateLayerDataJson(List<GeDataModel> layers, BigDecimal ax, BigDecimal by) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"ax\": ").append(ax).append(",\n");
        json.append("  \"by\": ").append(by).append(",\n");
        json.append("  \"layers\": [\n");
        
        for (int i = 0; i < layers.size(); i++) {
            GeDataModel layer = layers.get(i);
            json.append("    {\n");
            json.append("      \"name\": \"").append(layer.getName() != null ? layer.getName() : "Layer " + (i+1)).append("\",\n");
            json.append("      \"h\": ").append(layer.getH()).append(",\n");
            json.append("      \"num\": ").append(layer.getNum());
            
            // 添加power字段（如果存在）
            if (layer.getPower() != null) {
                json.append(",\n");
                json.append("      \"power\": ").append(layer.getPower());
            }
            
            json.append("\n");
            json.append("    }");
            if (i < layers.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ]\n");
        json.append("}");
        return json.toString();
    }

    @FXML
    private void handleExit() {
        Stage stage = (Stage) dataTable.getScene().getWindow();
        stage.close();
    }
}