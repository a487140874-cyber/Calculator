package ui;

import geModel.GeDataModel;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.Group;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.transform.Scale;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 能量可视化界面控制器
 */
public class EnergyController {

    @FXML private ScrollPane scrollPane;
    @FXML private Pane energyVisualizationPane;
    @FXML private Label statusLabel;

    private List<GeDataModel> geDataModels;
    private double zoomFactor = 1.0;
    private final Scale zoomTransform = new Scale(1, 1, 0, 0);
    private StackPane zoomContainer;

    /**
     * 设置要显示的数据
     */
    public void setData(List<GeDataModel> geDataModels) {
        this.geDataModels = geDataModels;
        drawEnergyVisualization();
    }

    @FXML
    private void initialize() {
        statusLabel.setText("Status: Ready");
        
        // 配置ScrollPane以支持滚轮滚动
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);

        scrollPane.setContent(null);
        Group zoomGroup = new Group(energyVisualizationPane);
        zoomContainer = new StackPane(zoomGroup);
        zoomContainer.setAlignment(Pos.TOP_LEFT);
        scrollPane.setContent(zoomContainer);
        energyVisualizationPane.getTransforms().add(zoomTransform);
        
        // 添加滚轮事件处理
        scrollPane.setOnScroll(event -> {
            if (event.isControlDown()) {
                // Ctrl + 滚轮进行缩放
                if (event.getDeltaY() > 0) {
                    handleZoomIn();
                } else {
                    handleZoomOut();
                }
                event.consume();
            }
        });
    }

    /**
     * 绘制能量可视化图像
     */
    private void drawEnergyVisualization() {
        if (geDataModels == null || geDataModels.isEmpty()) {
            statusLabel.setText("Status: No data to display");
            return;
        }

        energyVisualizationPane.getChildren().clear();
        statusLabel.setText("Status: Drawing energy visualization...");

        double startY = 20;
        double layerHeight = 50;
        double layerWidth = 700;
        double spacing = 8;

        int energyLayerCount = 0;
        double maxEnergy = 0.0;

        // 找到最大能量值用于标注比例
        for (GeDataModel model : geDataModels) {
            if (hasPositiveEnergy(model)) {
                double energy = model.getPower().doubleValue();
                if (energy > maxEnergy) {
                    maxEnergy = energy;
                }
            }
        }

        for (int i = 0; i < geDataModels.size(); i++) {
            GeDataModel model = geDataModels.get(i);
            double currentY = startY + i * (layerHeight + spacing);

            // 绘制岩层矩形
            Rectangle layerRect = new Rectangle(50, currentY, layerWidth, layerHeight);
            
            // 如果有能量值，将整个岩层背景设为绿色
            if (hasPositiveEnergy(model)) {
                // 根据能量值大小调整绿色深度
                double energyRatio = model.getPower().doubleValue() / (maxEnergy > 0 ? maxEnergy : 1.0);
                Color energyColor = Color.color(0.7 - energyRatio * 0.2, 0.8 + energyRatio * 0.2, 0.7 - energyRatio * 0.2); // 正确的绿色系背景
                layerRect.setFill(energyColor);
                layerRect.setStroke(Color.GREEN);
                layerRect.setStrokeWidth(3.0);
                energyLayerCount++;
            } else if (model.isKeyLayer()) {
                // 如果是关键层但没有能量值，使用蓝色边框
                layerRect.setFill(Color.LIGHTBLUE);
                layerRect.setStroke(Color.BLUE);
                layerRect.setStrokeWidth(2.0);
            } else {
                layerRect.setFill(Color.LIGHTGRAY);
                layerRect.setStroke(Color.GRAY);
                layerRect.setStrokeWidth(1.0);
            }
            
            energyVisualizationPane.getChildren().add(layerRect);

            // 添加岩层编号
            Text numText = new Text(20, currentY + layerHeight / 2 + 5, String.valueOf(i + 1));
            numText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            energyVisualizationPane.getChildren().add(numText);

            // 添加岩层名称
            Text nameText = new Text(60, currentY + 20, 
                model.getName() != null ? model.getName() : "Layer " + (i + 1));
            nameText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            energyVisualizationPane.getChildren().add(nameText);

            // 添加基本信息
            String info = String.format("H:%.2f  L:%.2f  E:%.2f", 
                model.getH() != null ? model.getH().doubleValue() : 0.0,
                model.getL() != null ? model.getL().doubleValue() : 0.0,
                model.getE() != null ? model.getE().doubleValue() : 0.0);
            Text infoText = new Text(60, currentY + 35, info);
            infoText.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
            energyVisualizationPane.getChildren().add(infoText);

            // 如果是关键层，显示关键层信息
            if (model.isKeyLayer()) {
                String keyLayerInfo = String.format("关键层 - 卸荷回弹量:%.6f  最大位移量:%.6f", 
                    model.getShi() != null ? model.getShi().doubleValue() : 0.0,
                    model.getUix() != null ? model.getUix().doubleValue() : 0.0);
                Text keyText = new Text(300, currentY + 20, keyLayerInfo);
                keyText.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                keyText.setFill(Color.BLUE);
                energyVisualizationPane.getChildren().add(keyText);
            }

            // 如果有能量值，显示能量信息（重点标注）
            if (hasPositiveEnergy(model)) {
                String energyInfo = String.format("能量: %.6f MJ", model.getPower().doubleValue());
                Text energyText = new Text(300, currentY + 50, energyInfo); // 调整Y位置避免遮挡
                energyText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                energyText.setFill(Color.BLUE); // 改为蓝色字体
                
                energyVisualizationPane.getChildren().add(energyText);
                
                Text energyMark = new Text(600, currentY + 25, "能量积聚岩层");
                energyMark.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                energyMark.setFill(Color.ORANGE);

                // 添加背景框
                Rectangle energyBox = new Rectangle(595, currentY + 12, 100, 20);
                energyBox.setFill(Color.YELLOW);
                energyBox.setStroke(Color.ORANGE);
                energyBox.setStrokeWidth(2.0);
                energyBox.setOpacity(0.6);
                energyVisualizationPane.getChildren().add(energyBox);
                energyVisualizationPane.getChildren().add(energyMark);
            }
        }

        // 添加标题
        Text title = new Text(300, 15, "岩层能量分布可视化");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setFill(Color.DARKBLUE);
        energyVisualizationPane.getChildren().add(title);

        // 调整面板大小
        double totalHeight = startY + geDataModels.size() * (layerHeight + spacing) + 100;
        energyVisualizationPane.setPrefHeight(totalHeight);
        energyVisualizationPane.setPrefWidth(layerWidth + 150);
        updateZoomContainerSize();

        statusLabel.setText(String.format("Status: 可视化完成。共%d层，其中%d层有能量值。最大能量值: %.6f", 
            geDataModels.size(), energyLayerCount, maxEnergy));
    }

    @FXML
    private void handleRefresh() {
        drawEnergyVisualization();
    }

    @FXML
    private void handleZoomIn() {
        zoomFactor *= 1.2;
        applyZoom();
    }

    @FXML
    private void handleZoomOut() {
        zoomFactor /= 1.2;
        applyZoom();
    }

    @FXML
    private void handleResetZoom() {
        zoomFactor = 1.0;
        applyZoom();
    }

    private void applyZoom() {
        zoomFactor = Math.max(0.25, Math.min(4.0, zoomFactor));
        zoomTransform.setX(zoomFactor);
        zoomTransform.setY(zoomFactor);
        updateZoomContainerSize();
        statusLabel.setText(String.format("Status: Zoom %.0f%%", zoomFactor * 100));
    }

    private void updateZoomContainerSize() {
        if (zoomContainer == null) return;
        zoomContainer.setPrefSize(
                energyVisualizationPane.getPrefWidth() * zoomFactor,
                energyVisualizationPane.getPrefHeight() * zoomFactor);
    }

    @FXML
    private void handleShowDetails() {
        if (geDataModels == null || geDataModels.isEmpty()) {
            return;
        }

        StringBuilder details = new StringBuilder();
        details.append("Energy Calculation Details:\n\n");
        
        int energyCount = 0;
        for (GeDataModel model : geDataModels) {
            if (model.isKeyLayer() && hasPositiveEnergy(model)) {
                energyCount++;
                details.append(String.format("Layer %s:\n", model.getName() != null ? model.getName() : "Unknown"));
                details.append(String.format("  Energy: %.6f\n", model.getPower().doubleValue()));
                details.append(String.format("  Shi: %.6f\n", model.getShi() != null ? model.getShi().doubleValue() : 0.0));
                details.append(String.format("  U: %.6f\n", model.getUix() != null ? model.getUix().doubleValue() : 0.0));
                details.append("\n");
            }
        }
        
        if (energyCount == 0) {
            details.append("No energy values calculated yet.");
        }

        TextArea textArea = new TextArea(details.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefSize(560, 420);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Energy Calculation Details");
        alert.setHeaderText(energyCount + " layers have positive energy values");
        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
        statusLabel.setText(String.format("Status: Displayed details for %d energy layers.", energyCount));
    }

    @FXML
    private void handleExportImage() {
        if (geDataModels == null || geDataModels.isEmpty()) {
            statusLabel.setText("Status: No energy visualization to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Energy Visualization");
        chooser.setInitialFileName("energy-visualization.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File file = chooser.showSaveDialog(energyVisualizationPane.getScene().getWindow());
        if (file == null) return;

        try {
            WritableImage image = energyVisualizationPane.snapshot(new SnapshotParameters(), null);
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
            statusLabel.setText("Status: Image exported to " + file.getName() + ".");
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Could not export the image: " + e.getMessage(), ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
            statusLabel.setText("Status: Image export failed.");
        }
    }

    private boolean hasPositiveEnergy(GeDataModel model) {
        return model.getPower() != null && model.getPower().signum() > 0;
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) energyVisualizationPane.getScene().getWindow();
        stage.close();
    }
}
