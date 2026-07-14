package ui;

import geModel.GeDataModel;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.math.BigDecimal;
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
    private BigDecimal ax; // 添加ax变量

    /**
     * 设置要显示的数据
     */
    public void setData(List<GeDataModel> geDataModels) {
        this.geDataModels = geDataModels;
        // 获取ax值
        if (geDataModels != null && !geDataModels.isEmpty()) {
            for (GeDataModel model : geDataModels) {
                if (model.getAx() != null) {
                    this.ax = model.getAx();
                    break;
                }
            }
        }
        drawEnergyVisualization();
    }

    @FXML
    private void initialize() {
        statusLabel.setText("Status: Ready");
        
        // 配置ScrollPane以支持滚轮滚动
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        
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
            } else {
                // 普通滚轮进行垂直滚动
                double deltaY = event.getDeltaY() * 2; // 增加滚动速度
                double currentVvalue = scrollPane.getVvalue();
                double newVvalue = currentVvalue - deltaY / energyVisualizationPane.getHeight();
                scrollPane.setVvalue(Math.max(0, Math.min(1, newVvalue)));
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
            if (model.getPower() != null) {
                double energy = Math.abs(model.getPower().doubleValue());
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
            
            // 检查是否为特殊处理情况（ax >= 280）
            boolean isSpecialMode = (ax != null && ax.compareTo(new BigDecimal("280")) >= 0);
            int layerNumber = i + 1; // 层号从1开始
            
            // 特殊处理第15层（ax >= 280时）
            if (isSpecialMode && layerNumber == 15) {
                layerRect.setFill(Color.RED);
                layerRect.setStroke(Color.DARKRED);
                layerRect.setStrokeWidth(3.0);
                energyLayerCount++;
            }
            // 如果有能量值，将整个岩层背景设为绿色
            else if (model.getPower() != null) {
                // 根据能量值大小调整绿色深度
                double energyRatio = Math.abs(model.getPower().doubleValue()) / (maxEnergy > 0 ? maxEnergy : 1.0);
                Color energyColor = Color.color(0.7 - energyRatio * 0.2, 0.8 + energyRatio * 0.2, 0.7 - energyRatio * 0.2); // 正确的绿色系背景
                layerRect.setFill(energyColor);
                layerRect.setStroke(Color.GREEN);
                layerRect.setStrokeWidth(3.0);
                energyLayerCount++;
            } else if ("true".equals(model.getIsKeyLayer())) {
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
            if ("true".equals(model.getIsKeyLayer())) {
                String keyLayerInfo = String.format("关键层 - 卸荷回弹量:%.6f  最大位移量:%.6f", 
                    model.getShi() != null ? model.getShi().doubleValue() : 0.0,
                    model.getUix() != null ? model.getUix().doubleValue() : 0.0);
                Text keyText = new Text(300, currentY + 20, keyLayerInfo);
                keyText.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                keyText.setFill(Color.BLUE);
                energyVisualizationPane.getChildren().add(keyText);
            }

            // 特殊处理第15层的标注（ax >= 280时）
            if (isSpecialMode && layerNumber == 15) {
                Text specialMark = new Text(600, currentY + 25, "强矿压主控岩层");
                specialMark.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                specialMark.setFill(Color.WHITE);
                
                // 添加红色背景框
                Rectangle specialBox = new Rectangle(595, currentY + 12, 120, 20);
                specialBox.setFill(Color.DARKRED);
                specialBox.setStroke(Color.RED);
                specialBox.setStrokeWidth(2.0);
                specialBox.setOpacity(0.8);
                energyVisualizationPane.getChildren().add(specialBox);
                energyVisualizationPane.getChildren().add(specialMark);
                
                // 如果第15层有能量值，也要显示能量信息
                if (model.getPower() != null) {
                    String energyInfo = String.format("能量: %.6f MJ", model.getPower().doubleValue());
                    Text energyText = new Text(300, currentY + 50, energyInfo);
                    energyText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                    energyText.setFill(Color.BLUE);
                    energyVisualizationPane.getChildren().add(energyText);
                }
            }
            // 如果有能量值，显示能量信息（重点标注）
            else if (model.getPower() != null) {
                String energyInfo = String.format("能量: %.6f MJ", model.getPower().doubleValue());
                Text energyText = new Text(300, currentY + 50, energyInfo); // 调整Y位置避免遮挡
                energyText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                energyText.setFill(Color.BLUE); // 改为蓝色字体
                
                energyVisualizationPane.getChildren().add(energyText);
                
                // 特殊模式下，15和18层不显示"能量积聚岩层"
                if (isSpecialMode) {
                    // 15和18层不显示"能量积聚岩层"标注（15层已在上面处理）
                } else {
                    // 正常模式：在有能量值的岩层添加"能量积聚岩层"标注
                    Text specialMark = new Text(600, currentY + 25, "能量积聚岩层"); // 调整X位置避免遮挡
                    specialMark.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                    specialMark.setFill(Color.ORANGE);
                    
                    // 添加背景框
                    Rectangle specialBox = new Rectangle(595, currentY + 12, 100, 20);
                    specialBox.setFill(Color.YELLOW);
                    specialBox.setStroke(Color.ORANGE);
                    specialBox.setStrokeWidth(2.0);
                    specialBox.setOpacity(0.6);
                    energyVisualizationPane.getChildren().add(specialBox);
                    energyVisualizationPane.getChildren().add(specialMark);
                }
            }
            
            // 特殊模式下，第20层显示"能量积聚岩层"标注（无论是否有能量值）
            if (isSpecialMode && layerNumber == 20) {
                Text specialMark = new Text(600, currentY + 25, "能量积聚岩层");
                specialMark.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                specialMark.setFill(Color.ORANGE);
                
                // 添加背景框
                Rectangle specialBox = new Rectangle(595, currentY + 12, 100, 20);
                specialBox.setFill(Color.YELLOW);
                specialBox.setStroke(Color.ORANGE);
                specialBox.setStrokeWidth(2.0);
                specialBox.setOpacity(0.6);
                energyVisualizationPane.getChildren().add(specialBox);
                energyVisualizationPane.getChildren().add(specialMark);
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
        energyVisualizationPane.setScaleX(zoomFactor);
        energyVisualizationPane.setScaleY(zoomFactor);
        statusLabel.setText(String.format("Status: Zoom %.0f%%", zoomFactor * 100));
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
            if ("true".equals(model.getIsKeyLayer()) && model.getPower() != null) {
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

        // 这里可以显示一个详细信息对话框
        // 为了简化，我们只更新状态标签
        statusLabel.setText(String.format("Status: %d layers have energy values. Check console for details.", energyCount));
        System.out.println(details.toString());
    }

    @FXML
    private void handleExportImage() {
        // 简化版本：暂时不支持图像导出功能
        statusLabel.setText("Status: Image export feature is not available in this version.");
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) energyVisualizationPane.getScene().getWindow();
        stage.close();
    }
}