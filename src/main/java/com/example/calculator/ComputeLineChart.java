package com.example.calculator;

import geModel.GeDataModel;
import org.matheclipse.core.eval.EvalEngine;
import org.matheclipse.core.interfaces.IExpr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
//完成一些计算变量的初始化工作

public class ComputeLineChart {
    public GeDataModel initLineChart(GeDataModel geDataModel)
    {
        geDataModel.setRt(computeRt(geDataModel));
        geDataModel.setRR(computeRR(geDataModel));
        geDataModel.setMxz(computeMxz(geDataModel));
        geDataModel.setMx0(computeMx0(geDataModel));
        geDataModel.setQx0(computeQx0(geDataModel));
        geDataModel.setMyz(computeMy0(geDataModel));
        geDataModel.setMy0(computeMy0(geDataModel));
        geDataModel.setQy0(computeQy0(geDataModel));
        geDataModel.setDMax1(computeDMax(geDataModel,"1"));
        geDataModel.setDMax2(computeDMax(geDataModel,"2"));

        return geDataModel;
    }


    public GeDataModel shiInitLineChart(GeDataModel geDataModel)
    {
        geDataModel.setRt(computeRR(geDataModel));
        geDataModel.setRR(computeRR(geDataModel));
        geDataModel.setMxz(shiComputeMxz(geDataModel));
        geDataModel.setMx0(shiComputeMx0(geDataModel));
        geDataModel.setQx0(shiComputeQx0(geDataModel));
        geDataModel.setMyz(shiComputeMyz(geDataModel));
        geDataModel.setMy0(shiComputeMy0(geDataModel));
        geDataModel.setQy0(shiComputeQy0(geDataModel));
        return geDataModel;
    }



    /**
     * 计算Qy0
     */
    public BigDecimal computeQy0(GeDataModel geDataModel)
    {
        double b = geDataModel.getBi().doubleValue();
        double r = geDataModel.getRt().doubleValue();
        double qy = geDataModel.getQy().doubleValue();

        return BigDecimal.valueOf((qy * b - r)/2 );

    }


    /**
     * 计算Qy0
     */
    public BigDecimal shiComputeQy0(GeDataModel geDataModel)
    {
        double b = geDataModel.getBi().doubleValue();
        double r = geDataModel.getRR().doubleValue();
        double qy = geDataModel.getQy().doubleValue();

        return BigDecimal.valueOf((qy * b - r)/2 );

    }

    /**
     * 计算Myz
     */
    public BigDecimal computeMyz(GeDataModel geDataModel)
    {
        double r = geDataModel.getRt().doubleValue();
        double b = geDataModel.getBi().doubleValue();
        double bd = geDataModel.getBd().doubleValue();
        double qy = geDataModel.getQy().doubleValue();

        double temp1 = (qy*b -r)/2 * bd * (b * bd + 2);
        double fz = 6 * qy * b * b - 12*r*b + qy*b*b*b*bd - 3*r*b*b*bd;
        double fm = 24 * bd * (bd*b + 2);
        return BigDecimal.valueOf(temp1 - fz/fm);
    }



    /**
     * 计算Myz
     */
    public BigDecimal shiComputeMyz(GeDataModel geDataModel)
    {
        double r = geDataModel.getRR().doubleValue();
        double b = geDataModel.getBi().doubleValue();
        double bd = geDataModel.getBd().doubleValue();
        double qy = geDataModel.getQy().doubleValue();

        double temp1 = -(qy*b -r)/(2 * bd * (b * bd + 2));
        double fz = 6 * qy * b * b - 12*r*b + qy*b*b*b*bd - 3*r*b*b*bd;
        double fm = 24  * (bd*b + 2);
        return BigDecimal.valueOf(temp1 - fz/fm);


//        double fz = -(3 * r * ( bd*b + 2) * ( bd*b + 2) + b * (12 + 6*b*bd + b*b*bd*bd) * qy);
//        double fm = 24 * bd * (bd*b + 2);
//
//        double ff = fz/fm;
//
//        return null;


    }

    /**
     * 计算My0
     */
    public BigDecimal computeMy0(GeDataModel geDataModel)
    {
        double r = geDataModel.getRt().doubleValue();
        double b = geDataModel.getBi().doubleValue();
        double qy  = geDataModel.getQy().doubleValue();
        double Myz = computeMyz(geDataModel).doubleValue();

        return BigDecimal.valueOf(Myz + qy*b*b/8 - r*b/4);
    }


    /**
     * 计算My0
     */
    public BigDecimal shiComputeMy0(GeDataModel geDataModel)
    {
        double r = geDataModel.getRR().doubleValue();
        double b = geDataModel.getBi().doubleValue();
        double qy  = geDataModel.getQy().doubleValue();
        double Myz = shiComputeMyz(geDataModel).doubleValue();

        return BigDecimal.valueOf(Myz + qy*b*b/8 - r*b/4);
    }


    /**
     * 计算qx0
     */
    public BigDecimal computeQx0(GeDataModel geDataModel)
    {
        double a = geDataModel.getLastAi().doubleValue();
        double r = geDataModel.getRt().doubleValue();
        double qx = geDataModel.getQx().doubleValue();

        return BigDecimal.valueOf((qx * a + r)/2 );


    }

    /**
     * 计算qx0,使用axi而非ai，使用RR
     */
    public BigDecimal shiComputeQx0(GeDataModel geDataModel)
    {
        double a = geDataModel.getAi().doubleValue();
        double r = geDataModel.getRR().doubleValue();
        double qx = geDataModel.getQx().doubleValue();

        return BigDecimal.valueOf((qx * a + r)/2 );


    }

    /**
     * 计算图形公式中的R
     * @param geDataModel
     * @return
     */
    public BigDecimal computeRt(GeDataModel geDataModel)
    {
        double a = geDataModel.getLastAi().doubleValue();
        double b = geDataModel.getBi().doubleValue();
        double bd = geDataModel.getBd().doubleValue();
        double qx = geDataModel.getQx().doubleValue();
        double qy = geDataModel.getQy().doubleValue();

        double fz = -a * (48 + 24*a*bd + 8*a*a*bd*bd + a*a*a*bd*bd*bd) * qx
                + b * (48 + 24*bd*b + 8*b*b*bd*bd + b*b*b*bd*bd*bd)* qy;

        double fm = 2 * (48 + 12*bd*a + 12*bd*b + 6*a*a*bd*bd + 6*b*b*bd*bd + a*a*a*bd*bd*bd + b*b*b*bd*bd*bd);

        return BigDecimal.valueOf(fz/fm);

    }

    /**
     * 计算R
     * @param geDataModel
     * @return
     */
    public BigDecimal computeRR(GeDataModel geDataModel)
    {
        double a = geDataModel.getAi().doubleValue();
        double b = geDataModel.getBi().doubleValue();
        double bd = geDataModel.getBd().doubleValue();

        //这里的qx和qy需要重新计算
        KeyLayerAnalyzer analyzer = new KeyLayerAnalyzer();


        double qx = analyzer.computeQx(geDataModel).doubleValue();
        double qy = analyzer.computeQy(geDataModel).doubleValue();


        double fz = -a * (48 + 24*a*bd + 8*a*a*bd*bd + a*a*a*bd*bd*bd) * qx
                + b * (48 + 24*bd*b + 8*b*b*bd*bd + b*b*b*bd*bd*bd)* qy;

        double fm = 2 * (48 + 12*bd*a + 12*bd*b + 6*a*a*bd*bd + 6*b*b*bd*bd + a*a*a*bd*bd*bd + b*b*b*bd*bd*bd);


        return BigDecimal.valueOf(fz/fm);

    }

    public BigDecimal computeMxz(GeDataModel geDataModel)
    {
        double a = geDataModel.getLastAi().doubleValue();
        double bd = geDataModel.getBd().doubleValue();
        double r = geDataModel.getRt().doubleValue();
        double qx = geDataModel.getQx().doubleValue();

        double fz = -(3 * r * ( bd*a + 2) * ( bd*a + 2) + a * (12 + 6*a*bd + a*a*bd*bd) * qx);
        double fm = 24 * bd * (bd*a + 2);

        return BigDecimal.valueOf(fz/fm);
    }


    public BigDecimal shiComputeMxz(GeDataModel geDataModel)
    {
        double a = geDataModel.getAi().doubleValue();
        double bd = geDataModel.getBd().doubleValue();
        double r = geDataModel.getRR().doubleValue();
        double qx = geDataModel.getQx().doubleValue();

        double fz = -(3 * r * ( bd*a + 2) * ( bd*a + 2) + a * (12 + 6*a*bd + a*a*bd*bd) * qx);
        double fm = 24 * bd * (bd*a + 2);

        return BigDecimal.valueOf(fz/fm);
    }


    public BigDecimal computeMx0(GeDataModel geDataModel)
    {
        double mxz = geDataModel.getMxz().doubleValue();
        double qx = geDataModel.getQx().doubleValue();
        double a = geDataModel.getLastAi().doubleValue();
        double r = geDataModel.getRt().doubleValue();

        return BigDecimal.valueOf(mxz + qx*a*a/8 - r*a/4);
    }


    public BigDecimal shiComputeMx0(GeDataModel geDataModel)
    {
        double mxz = geDataModel.getMxz().doubleValue();
        double qx = geDataModel.getQx().doubleValue();
        double a = geDataModel.getAi().doubleValue();
        double r = geDataModel.getRR().doubleValue();

        return BigDecimal.valueOf(mxz + qx*a*a/8 + r*a/4);
    }


    /**
     * 核心计算公式1
     * @param geDataModel
     * @param xValue
     * @return
     */
    public BigDecimal computeMx1(GeDataModel geDataModel, BigDecimal xValue){
        double bd = geDataModel.getBd().doubleValue();
        double x = xValue.doubleValue();
        double q = geDataModel.getQx0().doubleValue();
        double m = geDataModel.getMx0().doubleValue();
        double mi = geDataModel.getM().doubleValue();

        double temp1 = Math.exp(-bd * x);
        double temp2 = Math.sin(bd * x);
        double temp3 = Math.cos(bd * x);

        double result = temp1 * (q*temp2 + bd*m * (temp3 + temp2));

        return BigDecimal.valueOf(result * result / mi / mi);

    }

    /**
     * 核心计算公式2
     * @param geDataModel
     * @param xValue
     * @return
     */
    public BigDecimal computeMx2(GeDataModel geDataModel, BigDecimal xValue){
        double r = geDataModel.getRt().doubleValue();
        double bd = geDataModel.getBd().doubleValue();
        double a = geDataModel.getLastAi().doubleValue();
        double qx = geDataModel.getQx().doubleValue();
        double qy = geDataModel.getQy().doubleValue();
        double b = geDataModel.getBi().doubleValue();
        double x = xValue.doubleValue();
        double mi = geDataModel.getM().doubleValue();

        double temp1 = -(3 * r * (bd*a + 2)* (bd*a + 2) + a* (12 + 6*a*bd + a*a*bd*bd) * qx)/(24 * bd * (bd*a + 2));
        double temp2 = qx*(a/2 + x) * (a/2 + x) / 2;
        double temp3 = -a * (48 + 24*bd*a + 8*a*a*bd*bd + a*a*a*bd*bd*bd) * qx
                + b * (48 + 24*bd*b + 8*b*b*bd*bd + b*b*b*b*bd*bd*bd) * qy;
        double temp4 = 4 * (48 + 12*bd*a + 12*bd*b + 6*a*a*bd*bd + 6*b*b*bd*bd + a*a*a*bd*bd*bd + b*b*b*b*bd*bd*bd);

        double temp5 = temp3 / temp4 + (a / 2 + x);

        double result = temp1 - temp2 + temp5;

        return BigDecimal.valueOf(result/mi/mi);
    }


    /**
     * 核心计算
     */
    public BigDecimal[][] computeLineChart(GeDataModel geDataModel){
        geDataModel = initLineChart(geDataModel);
        double num1 = (geDataModel.getLastAi().doubleValue() / 2);
        int num = (int)num1;
        BigDecimal[][] data = new BigDecimal[60 + num][2];
        for(int i = -num;i < 60;i++){
            data[i + num][0] = BigDecimal.valueOf(i);
            if(i <= 0 ){
                data[i + num][1] = computeMx1(geDataModel,BigDecimal.valueOf(i));
            }else{
                data[i + num][1] = computeMx2(geDataModel,BigDecimal.valueOf(i));
            }
        }
        return data;
    }

    /**
     * 计算Mix
     */
    public BigDecimal computeMix(GeDataModel geDataModel){
        geDataModel = initLineChart(geDataModel);
//        EvalUtilities util = new EvalUtilities();
        EvalEngine engine = EvalEngine.get();
        String exprStr = "((1 / bd) * exp(-bd*x) * (qx0 * sin(bd * x) + bd * mx0 * ( cos(bd * x) + sin(bd * x) ) ))^2";

        String exprStr2 = "(mxz + (qx/2)*(ai/2 + x)^2 + (r/2)*(ai/2 + x))^2";

        // 解析字符串为符号表达式
        // 替换 Java 变量到符号表达式
        exprStr = exprStr.replace("bd", geDataModel.getBd().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr = exprStr.replace("qx0", geDataModel.getQx0().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr = exprStr.replace("mx0", geDataModel.getMx0().setScale(5, RoundingMode.HALF_UP).toString());

        exprStr2 = exprStr2.replace("mxz", geDataModel.getMxz().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("qx", geDataModel.getQx().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("r", geDataModel.getRt().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("ai", geDataModel.getAx().setScale(5, RoundingMode.HALF_UP).toString());

//        IExpr result = util.evaluate(exprStr);
//        IExpr result2 = util.evaluate(exprStr2);
//        IExpr result3 = util.evaluate(exprStr3);

        String definiteIntegral = "Integrate[" + exprStr + ",{x, 0, 60}]";
        String definiteIntegral2 = "Integrate[" + exprStr2 + ",{x, -"+ geDataModel.getLastAi().doubleValue()/2 +  ", 0}]";

        IExpr definiteResult = engine.evaluate(definiteIntegral);
        IExpr definiteResult2 = engine.evaluate(definiteIntegral2);

        double i = geDataModel.getI().doubleValue();
        double e = geDataModel.getE().doubleValue();

        double result = definiteResult.toDoubleDefault()/i/e + definiteResult2.toDoubleDefault()/i/e;

        return  BigDecimal.valueOf(result);


    }

    //还需要修改
    public BigDecimal computeMiy(GeDataModel geDataModel){
        geDataModel = initLineChart(geDataModel);
//        EvalUtilities util = new EvalUtilities();
        EvalEngine engine = EvalEngine.get();
        String exprStr = "((1 / bd) * exp(-bd*x) * (qx0 * sin(bd * x) + bd * mx0 * ( cos(bd * x) + sin(bd * x) ) ))^2";

        String exprStr2 = "(mxz + (qy/2)*(by/2 + x)^2 - (r/2)*(by/2 + x))^2";

        // 解析字符串为符号表达式
        // 替换 Java 变量到符号表达式
        exprStr = exprStr.replace("bd", geDataModel.getBd().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr = exprStr.replace("qy0", geDataModel.getQy0().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr = exprStr.replace("my0", geDataModel.getMy0().setScale(5, RoundingMode.HALF_UP).toString());

        exprStr2 = exprStr2.replace("myz", geDataModel.getMyz().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("qy", geDataModel.getQy().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("r", geDataModel.getRt().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("by", geDataModel.getBy().setScale(5, RoundingMode.HALF_UP).toString());

//        IExpr result = util.evaluate(exprStr);
//        IExpr result2 = util.evaluate(exprStr2);
//        IExpr result3 = util.evaluate(exprStr3);

        String definiteIntegral = "Integrate[" + exprStr + ",{x, 0, 60}]";
        String definiteIntegral2 = "Integrate[" + exprStr2 + ",{x, -"+ geDataModel.getBi().doubleValue()/2 +  ", 0}]";

        IExpr definiteResult = engine.evaluate(definiteIntegral);
        IExpr definiteResult2 = engine.evaluate(definiteIntegral2);

        double i = geDataModel.getI().doubleValue();
        double e = geDataModel.getE().doubleValue();

        double result = definiteResult.toDoubleDefault()/i/e + definiteResult2.toDoubleDefault()/i/e;

        return  BigDecimal.valueOf(result);
    }

    /**
     * 计算dMax
     */
    public BigDecimal computeDMax(GeDataModel geDataModel, String type){
        EvalEngine engine = EvalEngine.get();
        String exprStr = "(L/2 + (2*M1 + M2)/2*tan(dta))*M2*l / (2*M1 + M2)*cot(dta) ";

        exprStr = exprStr.replace("l", geDataModel.getL().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr = exprStr.replace("M1", geDataModel.getM1().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr = exprStr.replace("M2", geDataModel.getM2().setScale(5, RoundingMode.HALF_UP).toString());
        if("1".equals(type)){
            exprStr = exprStr.replace("L", geDataModel.getAx().setScale(5, RoundingMode.HALF_UP).toString());

        }
        else{
            exprStr = exprStr.replace("L", geDataModel.getBy().setScale(5, RoundingMode.HALF_UP).toString());
        }
        //这里可能需要角度转弧度
        exprStr = exprStr.replace("dta", geDataModel.getDta().setScale(5, RoundingMode.HALF_UP).toString());

        IExpr definiteResult = engine.evaluate(exprStr);

        return  BigDecimal.valueOf(definiteResult.toDoubleDefault());

    }

    public BigDecimal computeSgm(GeDataModel geDataModel, String type){
        EvalEngine engine = EvalEngine.get();

        String exprStr1 = "(2*D + l*H)*(x + M1*cot(dta)) * tan(dta)/2 * M1 + M2";
        String exprStr2 = "( 2*D*(M1+M2) + M1*l*H + (2*D + l*H) )*x*tan(dta) / 2*M1 + M2";

        if("1".equals(type)){
            exprStr1 = exprStr1.replace("D", geDataModel.getDMax1().setScale(5, RoundingMode.HALF_UP).toString());
            exprStr2 = exprStr2.replace("D", geDataModel.getDMax1().setScale(5, RoundingMode.HALF_UP).toString());
        }else{
            exprStr1 = exprStr1.replace("D", geDataModel.getDMax2().setScale(5, RoundingMode.HALF_UP).toString());
            exprStr2 = exprStr2.replace("D", geDataModel.getDMax2().setScale(5, RoundingMode.HALF_UP).toString());
        }
        exprStr1 = exprStr1.replace("l", geDataModel.getL().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr1 = exprStr1.replace("H", geDataModel.getH().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr1 = exprStr1.replace("M1", geDataModel.getM1().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr1 = exprStr1.replace("M2", geDataModel.getM2().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr1 = exprStr1.replace("dta", geDataModel.getDta().setScale(5, RoundingMode.HALF_UP).toString());


        exprStr2 = exprStr2.replace("l", geDataModel.getL().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("H", geDataModel.getH().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("M1", geDataModel.getM1().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("M2", geDataModel.getM2().setScale(5, RoundingMode.HALF_UP).toString());
        exprStr2 = exprStr2.replace("dta", geDataModel.getDta().setScale(5, RoundingMode.HALF_UP).toString());


        String temp1 = "M2/2/tan(dta)";
        String temp2 = "(M1+M2)/tan(dta)";
        temp1 = temp1.replace("M2", geDataModel.getM2().setScale(5, RoundingMode.HALF_UP).toString());
        temp1 = temp1.replace("dta", geDataModel.getDta().setScale(5, RoundingMode.HALF_UP).toString());

        temp2 = temp2.replace("M1", geDataModel.getM1().setScale(5, RoundingMode.HALF_UP).toString());
        temp2 = temp2.replace("M2", geDataModel.getM2().setScale(5, RoundingMode.HALF_UP).toString());
        temp2 = temp2.replace("dta", geDataModel.getDta().setScale(5, RoundingMode.HALF_UP).toString());

        String min = engine.evaluate(temp1).toString();
        String max = engine.evaluate(temp2).toString();


        String definiteIntegral1 = "Integrate[" + exprStr1 + ",{x, 0,"+min+"}]";
        String definiteIntegral2 = "Integrate[" + exprStr2 + ",{x,"+min+", "+ max +"}]";

        IExpr definiteResult = engine.evaluate(definiteIntegral1);
        IExpr definiteResult2 = engine.evaluate(definiteIntegral2);

        return null;

    }

    public GeDataModel computeInit(GeDataModel model){


         return initLineChart(model);


    }

}
