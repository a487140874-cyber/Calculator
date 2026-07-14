package com.example.calculator;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import geModel.GeDataModel;
import geModel.GeDataModelExcel;
import getData.GetDateFromExcle;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class test {
    public static List<GeDataModelExcel> copyToDto(List<GeDataModel> sourceList) {
        List<GeDataModelExcel> targetList = new ArrayList<>();
        Field[] sourceFields = GeDataModel.class.getDeclaredFields();
        Field[] targetFields = GeDataModelExcel.class.getDeclaredFields();

        for (GeDataModel source : sourceList) {
            GeDataModelExcel target = new GeDataModelExcel();
            for (Field sourceField : sourceFields) {
                sourceField.setAccessible(true);
                for (Field targetField : targetFields) {
                    targetField.setAccessible(true);
                    if (sourceField.getName().equals(targetField.getName()) && sourceField.getType().equals(targetField.getType())) {
                        try {
                            targetField.set(target, sourceField.get(source));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            targetList.add(target);
        }
        return targetList;
    }
    public static void main(String[] args) {
        List<GeDataModel> geDataModels = new ArrayList<>();
        //读取数据
        GetDateFromExcle getDateFromExcle = new GetDateFromExcle();
        geDataModels = getDateFromExcle.getData("/Users/gcf/Desktop/测试用数据.xlsx");

        KeyLayerAnalyzer keyLayerAnalyzer = new KeyLayerAnalyzer();
        LayerLoadCalculator layerLoadCalculator = new LayerLoadCalculator();
        ComputeLineChart computeLineChart = new ComputeLineChart();

        //初始化关键测
        geDataModels =  layerLoadCalculator.findKeyLayers(geDataModels);

        //核心计算（是否垮落）

        geDataModels = keyLayerAnalyzer.compute(geDataModels);


        List<GeDataModel> list = new ArrayList<>();

        // 导出数据到 Excel
        for(GeDataModel geDataModel : geDataModels){
            if("true".equals(geDataModel.getIsKeyLayer())){
                if("true".equals(geDataModel.getIsNotCrack())){
                    geDataModel.setM1(BigDecimal.valueOf(7.5));
                    geDataModel.setM2(BigDecimal.valueOf(39.1));
                    geDataModel = computeLineChart.computeInit(geDataModel);
                    list.add(geDataModel);
                }else{
                    break;
                }
            }
//            if("true".equals(geDataModel.getIsNotCrack())){
//                computeLineChart.computeMix(geDataModel);
//                BigDecimal[][] data =  computeLineChart.computeLineChart(geDataModel);
//            }
        }


        try (ExcelWriter excelWriter = EasyExcel.write("/Users/gcf/Desktop/计算数据.xlsx", GeDataModelExcel.class).build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet("模板").build();
            excelWriter.write(list, writeSheet);
        }

        try (ExcelWriter excelWriter = EasyExcel.write("/Users/gcf/Desktop/计算数据1.xlsx", GeDataModelExcel.class).build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet("模板").build();
            excelWriter.write(geDataModels, writeSheet);
        }

    }
}
