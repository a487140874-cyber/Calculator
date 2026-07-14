package com.example.calculator;

import geModel.GeDataModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 层载荷计算器，求得关键层
 */
public class LayerLoadCalculator {



    public BigDecimal[][] getData(List<GeDataModel> geDataModels){
        BigDecimal[][] data = new BigDecimal[geDataModels.size()][4];
        for(GeDataModel geDataModel : geDataModels){
            data[geDataModel.getNum() - 1][0] = geDataModel.getE();
            data[geDataModel.getNum() - 1][1] = geDataModel.getH();
            data[geDataModel.getNum() - 1][2] = geDataModel.getL();
        }
        return data;
    }


    ArrayList<Integer> keyLayers = new ArrayList<>();

    // 动态计算任意层数的载荷
    public static BigDecimal calculateLayerLoad(BigDecimal[] E, BigDecimal[] h, BigDecimal[] l, int layerIndex,int currentLayer) {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;

        if (layerIndex == currentLayer) {
            return l[layerIndex -1 ].multiply(h[layerIndex - 1]);
        }

        // 计算分子和分母

        //上次关键层非零
        if(currentLayer > 0){
            for (int i = currentLayer -1; i < layerIndex; i++) {
                numerator = numerator.add(l[i].multiply(h[i]));
            }

            for (int i = currentLayer -1; i < layerIndex; i++) {
                denominator = denominator.add(E[i].multiply(h[i].pow(3)));
            }
            numerator = E[currentLayer -1].multiply(h[currentLayer -1].pow(3)).multiply(numerator);
        }else {
            //上次关键层为零
            for (int i = currentLayer; i < layerIndex; i++) {
                numerator = numerator.add(l[i].multiply(h[i]));
            }

            for (int i = currentLayer; i < layerIndex; i++) {
                denominator = denominator.add(E[i].multiply(h[i].pow(3)));
            }
            numerator = E[currentLayer].multiply(h[currentLayer].pow(3)).multiply(numerator);

        }

        return numerator.divide(denominator, BigDecimal.ROUND_HALF_UP);
    }

    // 求得所有关键层
    public List<GeDataModel> findKeyLayers(List<GeDataModel> geDataModels) {
        int currentLayer = 0; // 初始层
        BigDecimal[][] data = getData(geDataModels);
        int layerCount = data.length;
        //弹性模量
        BigDecimal[] E = new BigDecimal[layerCount];
        //层厚
        BigDecimal[] h = new BigDecimal[layerCount];
        //体积力
        BigDecimal[] l = new BigDecimal[layerCount];

        // 提取数据
        for (int i = 0; i < layerCount; i++) {
            E[i] = data[i][0];
            h[i] = data[i][1];
            l[i] = data[i][2];
        }

        BigDecimal[] loads = new BigDecimal[layerCount];
        for (int i = 0; i < layerCount; i++) {
            loads[i] = calculateLayerLoad(E, h, l, i + 1 ,currentLayer);
            if(i > 0){
                //如果本层载荷小于上层载荷，则本层为关键层
                if(loads[i].compareTo(loads[i - 1]) <= 0){
                    //更新最新关键层
                    currentLayer = i + 1;
                    //重新计算本层数据
                    loads[i] = calculateLayerLoad(E, h, l, i + 1 , currentLayer);
                    //记录关键层
                    keyLayers.add(currentLayer);
                    geDataModels.get(i).setKeyLayer(true);
                }
            }
            System.out.println("Layer " + (i + 1) + " Load: " + loads[i]);
        }
        return geDataModels;
    }
}