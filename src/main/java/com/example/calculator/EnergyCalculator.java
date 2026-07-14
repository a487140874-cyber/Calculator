package com.example.calculator;

import geModel.GeDataModel;
import java.util.List;

/**
 * 能量计算器
 * 封装能量计算相关的逻辑
 */
public class EnergyCalculator {
    
    /**
     * 计算所有关键层的能量
     * 
     * @param geDataModels 岩层数据模型列表
     * @return 计算完成后的岩层数据模型列表，包含能量值
     */
    public List<GeDataModel> calculateEnergy(List<GeDataModel> geDataModels) {
        if (geDataModels == null || geDataModels.isEmpty()) {
            throw new IllegalArgumentException("岩层数据不能为空");
        }
        
        // 调用ComputeKeyLayerShi中的主要计算方法
        return ComputeKeyLayerShi.computeMain(geDataModels);
    }
    
    /**
     * 检查是否有能量计算结果
     * 
     * @param geDataModels 岩层数据模型列表
     * @return 如果有任何关键层包含能量值则返回true
     */
    public boolean hasEnergyResults(List<GeDataModel> geDataModels) {
        if (geDataModels == null || geDataModels.isEmpty()) {
            return false;
        }
        
        return geDataModels.stream()
                .filter(model -> "true".equals(model.getIsKeyLayer()))
                .anyMatch(model -> model.getPower() != null);
    }
    
    /**
     * 获取有能量值的关键层数量
     * 
     * @param geDataModels 岩层数据模型列表
     * @return 有能量值的关键层数量
     */
    public long getEnergyLayerCount(List<GeDataModel> geDataModels) {
        if (geDataModels == null || geDataModels.isEmpty()) {
            return 0;
        }
        
        return geDataModels.stream()
                .filter(model -> "true".equals(model.getIsKeyLayer()))
                .filter(model -> model.getPower() != null)
                .count();
    }
}