package com.example.calculator;

import geModel.GeDataModel;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.analysis.solvers.BrentSolver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class ComputeKeyLayerShi {

    /**
     * 推进距离 ax 达到该值时，启用「大推进距离」特殊计算逻辑
     * （只算第 {@link #SPECIAL_LAYER_A}、{@link #SPECIAL_LAYER_B} 层的能量）。
     *
     * <p><b>⚠ 与 {@link KeyLayerAnalyzer} 中的 279 不一致</b>，两处判定的是同一个工况。
     * 同时，Python 脚本 {@code generate_3d_layers.py} 里也硬编码了 {@code ax >= 280}
     * 及第 15/18 层的特殊显示逻辑，三处必须保持同步。此处保持原值 280 不动。
     */
    static final double SPECIAL_MODE_AX_THRESHOLD = 280;

    /** 大推进距离工况下参与能量计算的两个特殊层号。 */
    private static final int SPECIAL_LAYER_A = 15;
    private static final int SPECIAL_LAYER_B = 18;

    /**
     * <b>⚠ 写死的能量值，并非计算结果。</b>
     *
     * <p>大推进距离工况下，第 15/18 层的能量被直接赋成这两个常量，其上方按正常公式
     * 算出的 layerPower 被算完即弃。这看起来是为某次演示临时改的，会让该工况下的
     * 能量输出与输入数据完全无关。此处如实保留原行为，待你确认后再决定是否恢复为真实计算。
     */
    private static final BigDecimal HARDCODED_POWER_LAYER_15 = BigDecimal.valueOf(64.629741);
    private static final BigDecimal HARDCODED_POWER_LAYER_18 = BigDecimal.valueOf(42.178526);

    /** 能量公式中，用于把积分结果扩展到岩层影响范围的跨度余量。 */
    private static final double ENERGY_SPAN_MARGIN = 120;

    /** 卸荷回弹量 shi 的公式系数：shi = Σ(h * 6 / e)。 */
    private static final double SHI_COEFFICIENT = 6;

    /** 弹性地基梁那一段积分（自由端向外）的积分上限。 */
    private static final double FOUNDATION_INTEGRAL_UPPER_BOUND = 60.0;

    /** 数值积分允许的最大求值次数。 */
    private static final int MAX_INTEGRATION_EVALUATIONS = 10000;

    /** BrentSolver 求根的搜索区间与最大迭代次数。 */
    private static final double SOLVER_SEARCH_BOUND = 10000;
    private static final int SOLVER_MAX_ITERATIONS = 100;

    /**
     *计算Shi
     */

    public static BigDecimal computeShi(List<GeDataModel> geDataModels, int index) {

        double shi =  geDataModels.get(index).getH().doubleValue() * SHI_COEFFICIENT / geDataModels.get(index).getE().doubleValue();
        for(int i = index + 1; i < geDataModels.size(); i++){
            if(geDataModels.get(i).isKeyLayer()){
                break;
            }
            shi = shi + geDataModels.get(i).getH().doubleValue() * SHI_COEFFICIENT / geDataModels.get(i).getE().doubleValue();
        }

        return new BigDecimal(shi);
    }

    /**
     *计算u（x）计算当前层的uix
     * @param
     * @param
     */
    public static BigDecimal computeUix(GeDataModel geDataModel) {


        //原始方程
        double E = geDataModel.getE().doubleValue();
        double I =geDataModel.getI().doubleValue();
        double a =  geDataModel.getAi().doubleValue();
        double R = geDataModel.getRR().doubleValue();
        double beta = geDataModel.getBd().doubleValue();
        double qx = geDataModel.getQx().doubleValue();
        double x = -a/2;

                double Uix = 5 / (E * I) * (

                qx / 24 * Math.pow(a / 2 + x, 4) +
                        R / 12 * Math.pow(a / 2 + x, 3) -
                        (3 * R * Math.pow(beta * a + 2, 2) + a * (12 + 6 * a * beta + Math.pow(a, 2) * Math.pow(beta, 2)) * qx) * Math.pow(x, 2)
                                / (48 * beta * (beta * a + 2))
                        -
                        a * x * (3 * R * Math.pow(beta * a + 2, 2) + a * (12 + 6 * a * beta + Math.pow(a * beta, 2)) * qx) / (48 * beta * (beta * a + 2))
                        +
                        (4 * R * (24 + 24 * a * beta + 6 * Math.pow(a, 2) * Math.pow(beta, 2) - 2 * Math.pow(a * beta, 3)
                                - Math.pow(a, 4) * Math.pow(beta, 4)) + a * (96 + 96 * a * beta + 16 * Math.pow(a * beta, 2)
                                - 2 * Math.pow(a * beta, 3) - Math.pow(a * beta, 4)) * qx)

                                / (384 * Math.pow(beta, 3) * (beta * a + 2))

        );

        return new BigDecimal(Uix);

    }




    /**
     * 求解y的范围
     */
    public static BigDecimal computeY(List<GeDataModel> geDataModels, int index, double Shi,double bxi) {

        //初始化关键变量
        GeDataModel geDataModel = geDataModels.get(index);

        double qx = geDataModel.getQx().doubleValue();
        double qy = geDataModel.getQy().doubleValue();

        // ===============================================
        // 步骤 1: 定义所有的常量
        // 请根据你的实际问题修改这些值
        // ===============================================
        final double E = geDataModel.getE().doubleValue();
        final double I =  geDataModel.getI().doubleValue();
        //这里的qx和qy需要重新计算
        final double R =  geDataModel.getRR().doubleValue();
        final double b =  geDataModel.getBi().doubleValue();
        final double beta = geDataModel.getBd().doubleValue();

        // 我们要求解 f(x) = C 时 x 的值
        // 其中 f(x) 是整个复杂表达式，C 是 mm 的值
        // 目标函数: g(x) = f(x) - mm = 0
        // 这部分是 mm * E * I
        final double targetValue = Shi * E * I / 5;

        // ===============================================
        // 步骤 2: 定义待求解的函数 g(x)
        // 使用一个 Lambda 表达式来实现 UnivariateFunction 接口
        // ===============================================
        UnivariateFunction function = y -> {
            // 这是 f(x) 的表达式，与你提供的公式完全对应
            double fy =
                    (qy / 24) * Math.pow(b / 2 + y, 4) -
                            (R / 12) * Math.pow(b / 2 + y, 3) -
                            (-3 * R * Math.pow(beta * b + 2, 2) + b * (12 + 6 * b * beta + Math.pow(b, 2) * Math.pow(beta, 2)) * qy) /
                                    (48 * beta * (beta * b + 2)) * Math.pow(y, 2) -
                            b * y * (-3 * R * Math.pow(beta * b + 2, 2) +
                                    b * (12 + 6 * b * beta + Math.pow(b * beta, 2)) * qy) / (48 * beta * (beta * b + 2)) +
                            (4 * R * (-24 - 24 * b * beta - 6 * Math.pow(b, 2) * Math.pow(beta, 2) + 2 * Math.pow(b * beta, 3) + Math.pow(b, 4) * Math.pow(beta, 4)) + b * (96 + 96 * b * beta + 16 * Math.pow(b * beta, 2) - 2 * Math.pow(b * beta, 3) - Math.pow(b * beta, 4)) * qy) / (384 * Math.pow(beta, 3) * (beta * b + 2));

            // 返回 g(x) = f(x) - targetValue
            return fy - targetValue;
        };

        // ===============================================
        // 步骤 3: 使用 BrentSolver 求解
        // ===============================================
        BrentSolver solver = new BrentSolver();

        // 求解需要提供一个搜索区间 [min, max]
        double minY = -SOLVER_SEARCH_BOUND;
        double maxY = SOLVER_SEARCH_BOUND;

        // 求解，并返回找到的根
        try {
            double solutionY = solver.solve(SOLVER_MAX_ITERATIONS, function, minY, maxY);
            System.out.println("在区间 [" + minY + ", " + maxY + "] 内找到的 y 值为: " + solutionY);

            // 验证结果，将求解出的 x 代入原方程，看是否等于 mm
            double calculatedMm1 = (5.0 / (E * I)) * (
                    (qy / 24) * Math.pow(b / 2 + solutionY, 4) +
                            (R / 12) * Math.pow(b / 2 + solutionY, 3) -
                            (-3 * R * Math.pow(beta * b + 2, 2) + b * (12 + 6 * b * beta + Math.pow(b, 2) * Math.pow(beta, 2)) * qy) / (48 * beta * (beta * b + 2)) * Math.pow(solutionY, 2) -
                            b * solutionY * (-3 * R * Math.pow(beta * b + 2, 2) + b * (12 + 6 * b * beta + Math.pow(b * beta, 2)) * qy) / (48 * beta * (beta * b + 2)) +
                            (4 * R * (-24 - 24 * b * beta - 6 * Math.pow(b, 2) * Math.pow(beta, 2) - 2 * Math.pow(b * beta, 3) - Math.pow(b, 4) * Math.pow(beta, 4)) + b * (96 + 96 * b * beta + 16 * Math.pow(b * beta, 2) - 2 * Math.pow(b * beta, 3) - Math.pow(b * beta, 4)) * qy) / (384 * Math.pow(beta, 3) * (beta * b + 2))
            );
            System.out.println("将该 y 值代入方程得到的 shi 值: " + calculatedMm1);
            System.out.println("目标 shi 值: " + Shi);
            return BigDecimal.valueOf(solutionY);

        } catch (org.apache.commons.math3.exception.NoBracketingException e) {
            System.err.println("在给定区间内未找到根。请尝试扩大搜索区间或更改初始值。");
            System.err.println(e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("求解时发生错误: " + e.getMessage());
            return null;
        }

    }


    /**
     * 求解x的范围
     */
    public static BigDecimal computeX(List<GeDataModel> geDataModels, int index, double Shi,double axi) {
        //原始方程
//        double E = 1,I=1,qx=1,a=1,R=1,beta=1,x=1;
//        double fx =  1 / (E * I) * (
//
//                        qx / 24 * Math.pow(a / 2 + x,4) +
//                        R / 12 * Math.pow(a / 2 + x,3) -
//                        (3 * R * Math.pow(beta * a + 2,2) + a * (12 + 6 * a * beta + Math.pow(a,2) * Math.pow(beta,2)) * qx) / (48 * beta * (beta * a + 2)) * Math.pow(x,2) -
//                        a * x * (3 * R * Math.pow(beta * a + 2,2) + a * (12 + 6 * a * beta + Math.pow(a * beta,2)) * qx) / (48 * beta * (beta * a + 2)) +
//                        (4 * R * (24 + 24 * a * beta + 6 * Math.pow(a,2) * Math.pow(beta,2) - 2 * Math.pow(a * beta,3) - Math.pow(a,4) * Math.pow(beta,4)) + a * (96 + 96 * a * beta + 16 * Math.pow(a*beta,2) - 2 * Math.pow(a*beta,3) - Math.pow(a*beta,4)) * qx) / (384 * Math.pow(beta,3) * (beta * a + 2))
//
//        );

        //初始化关键变量

        GeDataModel geDataModel = geDataModels.get(index);
        double qx = geDataModel.getQx().doubleValue();
        // ===============================================
        // 步骤 1: 定义所有的常量
        //
        // ===============================================
        final double E = geDataModel.getE().doubleValue() ;
        final double I = geDataModel.getI().doubleValue();
        //这里的qx和qy需要重新计算

        final double R =  geDataModel.getRR().doubleValue();
        final double a =  geDataModel.getAi().doubleValue();
        final double beta = geDataModel.getBd().doubleValue();

        // 我们要求解 f(x) = C 时 x 的值
        // 其中 f(x) 是整个复杂表达式，C 是 shi 的值
        // 目标函数: g(x) = f(x) - mm = 0
        // 这部分是 mm * E * I
        final double targetValue = Shi * E * I / 5;

        // ===============================================
        // 步骤 2: 定义待求解的函数 g(x)
        // 使用一个 Lambda 表达式来实现 UnivariateFunction 接口
        // ===============================================
        UnivariateFunction function = x -> {
            // 这是 f(x) 的表达式，与你提供的公式完全对应
            double fx =
                    (qx / 24) * Math.pow(a / 2 + x, 4) +
                            (R / 12) * Math.pow(a / 2 + x, 3) -
                            (3 * R * Math.pow(beta * a + 2, 2) + a * (12 + 6 * a * beta + Math.pow(a, 2) * Math.pow(beta, 2)) * qx) / (48 * beta * (beta * a + 2)) * Math.pow(x, 2) -
                            a * x * (3 * R * Math.pow(beta * a + 2, 2) + a * (12 + 6 * a * beta + Math.pow(a * beta, 2)) * qx) / (48 * beta * (beta * a + 2)) +
                            (4 * R * (24 + 24 * a * beta + 6 * Math.pow(a, 2) * Math.pow(beta, 2) - 2 * Math.pow(a * beta, 3) - Math.pow(a, 4) * Math.pow(beta, 4)) + a * (96 + 96 * a * beta + 16 * Math.pow(a * beta, 2) - 2 * Math.pow(a * beta, 3) - Math.pow(a * beta, 4)) * qx) / (384 * Math.pow(beta, 3) * (beta * a + 2));

            // 返回 g(x) = f(x) - targetValue
            return fx - targetValue;
        };

        // ===============================================
        // 步骤 3: 使用 BrentSolver 求解
        // ===============================================
        BrentSolver solver = new BrentSolver();

        // 求解需要提供一个搜索区间 [min, max]
        double minX = -SOLVER_SEARCH_BOUND;
        double maxX = SOLVER_SEARCH_BOUND;

        // 求解，并返回找到的根
        try {
            double solutionX = solver.solve(SOLVER_MAX_ITERATIONS, function, minX, maxX);
            System.out.println("在区间 [" + minX + ", " + maxX + "] 内找到的 x 值为: " + solutionX);

            // 验证结果，将求解出的 x 代入原方程，看是否等于 mm
            double calculatedMm = (5.0 / (E * I)) * (
                    (qx / 24) * Math.pow(a / 2 + solutionX, 4) +
                            (R / 12) * Math.pow(a / 2 + solutionX, 3) -
                            (3 * R * Math.pow(beta * a + 2, 2) + a * (12 + 6 * a * beta + Math.pow(a, 2) * Math.pow(beta, 2)) * qx) / (48 * beta * (beta * a + 2)) * Math.pow(solutionX, 2) -
                            a * solutionX * (3 * R * Math.pow(beta * a + 2, 2) + a * (12 + 6 * a * beta + Math.pow(a * beta, 2)) * qx) / (48 * beta * (beta * a + 2)) +
                            (4 * R * (24 + 24 * a * beta + 6 * Math.pow(a, 2) * Math.pow(beta, 2) - 2 * Math.pow(a * beta, 3) - Math.pow(a, 4) * Math.pow(beta, 4)) + a * (96 + 96 * a * beta + 16 * Math.pow(a * beta, 2) - 2 * Math.pow(a * beta, 3) - Math.pow(a * beta, 4)) * qx) / (384 * Math.pow(beta, 3) * (beta * a + 2))
            );
            System.out.println("将该 x 值代入方程得到的 shi 值: " + calculatedMm);
            System.out.println("目标 shi 值: " + Shi);
            return BigDecimal.valueOf(solutionX);

        } catch (org.apache.commons.math3.exception.NoBracketingException e) {
            System.err.println("在给定区间内未找到根。请尝试扩大搜索区间或更改初始值。");
            System.err.println(e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("求解时发生错误: " + e.getMessage());
            return null;
        }

    }


    /**
     * 计算Mix
     */
    public static BigDecimal computeMix(GeDataModel geDataModel, double lowerBound2) {
        //变量初始化部分
        KeyLayerAnalyzer analyzer = new KeyLayerAnalyzer();
        double qx = analyzer.computeQx(geDataModel).doubleValue();
        double qy = analyzer.computeQy(geDataModel).doubleValue();
        geDataModel.setQx(BigDecimal.valueOf(qx));
        geDataModel.setQy(BigDecimal.valueOf(qy));
        //初始化关键变量
        ComputeLineChart computeLineChart = new ComputeLineChart();
        geDataModel =  computeLineChart.shiInitLineChart(geDataModel);
        lowerBound2 = -lowerBound2/2;

// ==================================================================
        // 步骤 1: 定义所有常量 (请务必用你的实际值替换这些占位符)
        // ==================================================================
        double bd = geDataModel.getBd().doubleValue();
        double r = geDataModel.getRR().doubleValue();
        double ai = geDataModel.getAi().doubleValue(); // Note: Using ai for the second formula
        double qx0 = geDataModel.getQx0().doubleValue();
        double mx0 = geDataModel.getMx0().doubleValue();
        double mxz = geDataModel.getMxz().doubleValue();


        double result1 = -1;
        double result2 = -1;

        // 创建一个积分器实例。
        UnivariateIntegrator integrator = new SimpsonIntegrator();
        final int maxEvaluations = MAX_INTEGRATION_EVALUATIONS;

        // ==================================================================
        // 步骤 2: 计算第一个表达式的定积分
        // exprStr = "(1 / bd) * exp(-bd*x) * (qx0 * sin(bd * x) + bd * mx0 * ( cos(bd * x) + sin(bd * x) ) )"
        // 积分区间: [0, 60]
        // ==================================================================
        System.out.println("--- 正在计算第一个积分 ---");

        // 将字符串表达式转换为 Java 函数
        UnivariateFunction function1 = x -> {
            double term_sin = qx0 * Math.sin(bd * x);
            double term_cos_sin = bd * mx0 * (Math.cos(bd * x) + Math.sin(bd * x));
            double innerExpression = (1.0 / bd) * Math.exp(-bd * x) * (term_sin + term_cos_sin);

            // 根据MATLAB代码添加平方运算
            return Math.pow(innerExpression, 2);
        };

        double lowerBound1 = 0.0;
        double upperBound1 = FOUNDATION_INTEGRAL_UPPER_BOUND;

        try {
            result1 = integrator.integrate(maxEvaluations, function1, lowerBound1, upperBound1);
            System.out.println("表达式 1 在区间 [" + lowerBound1 + ", " + upperBound1 + "] 上的定积分结果是:");
            System.out.printf("%.10f%n", result1);
        } catch (Exception e) {
            System.err.println("计算第一个积分时出错: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n" + "--- 正在计算第二个积分 ---");
        // ==================================================================
        // 步骤 3: 计算第二个表达式的定积分
        // exprStr2 = "mxz + (qx/2)*(ai/2 + x)^2 + (r/2)*(ai/2 + x)"
        // 积分区间: [ppt, 0]
        // ==================================================================

        // 将字符串表达式转换为 Java 函数
        UnivariateFunction function2 = x -> {
            double commonTerm = (ai / 2.0) + x;
            double term_qx = (qx / 2.0) * Math.pow(commonTerm, 2);
            double term_r = (r / 2.0) * commonTerm;
            double innerExpression = mxz + term_qx + term_r;

            // 根据MATLAB代码添加平方运算
            return Math.pow(innerExpression, 2);
        };

        // **重要**: 积分区间根据您的要求设置为 [lowerBound2 , 0]
        double upperBound2 = 0.0;

        try {
            result2 = integrator.integrate(maxEvaluations, function2, lowerBound2, upperBound2);
            System.out.println("表达式 2 在区间 [" + lowerBound2 + ", " + upperBound2 + "] 上的定积分结果是:");
            System.out.printf("%.10f%n", result2);
        } catch (Exception e) {
            System.err.println("计算第二个积分时出错: " + e.getMessage());
            e.printStackTrace();
        }

        return BigDecimal.valueOf((result1 + result2) / (geDataModel.getI().doubleValue() * geDataModel.getE().doubleValue()));

    }

    /**
     * 计算miy
     */
    public static BigDecimal computeMiy(GeDataModel geDataModel, double lowerBound2) {

        KeyLayerAnalyzer analyzer = new KeyLayerAnalyzer();
        double qx = analyzer.computeQx(geDataModel).doubleValue();
        double qy = analyzer.computeQy(geDataModel).doubleValue();
        geDataModel.setQx(BigDecimal.valueOf(qx));
        geDataModel.setQy(BigDecimal.valueOf(qy));
        //初始化关键变量
        ComputeLineChart computeLineChart = new ComputeLineChart();
        geDataModel =  computeLineChart.shiInitLineChart(geDataModel);

        lowerBound2 = -lowerBound2/2;

        // ==================================================================
        // 步骤 1: 定义所有常量 (请务必用你的实际值替换这些占位符)
        // ==================================================================
        final double bd = geDataModel.getBd().doubleValue();
        final double qy0 = geDataModel.getQy0().doubleValue();
        final double my0 = geDataModel.getMy0().doubleValue();

        final double myz = geDataModel.getMyz().doubleValue();
        final double bi = geDataModel.getBi().doubleValue();
        final double r = geDataModel.getRt().doubleValue();

        double result1 = -1;
        double result2 = -1;

        // 创建一个积分器实例。SimpsonIntegrator 对于大多数平滑函数来说效果很好。
        UnivariateIntegrator integrator = new SimpsonIntegrator();
        final int maxEvaluations = MAX_INTEGRATION_EVALUATIONS;

        // ==================================================================
        // 步骤 2: 计算第一个表达式的定积分
        // exprStr = "(1 / bd) * exp(-bd*x) * (qx0 * sin(bd * x) + bd * mx0 * ( cos(bd * x) + sin(bd * x) ) )"
        // 积分区间: [0, 60]
        // ==================================================================
        System.out.println("--- 正在计算第一个积分 ---");

        // 将字符串表达式转换为 Java 函数
        UnivariateFunction function1 = y -> {
            double term_sin = qy0 * Math.sin(bd * y);
            double term_cos_sin = bd * my0 * (Math.cos(bd * y) + Math.sin(bd * y));
            double innerExpression = (1.0 / bd) * Math.exp(-bd * y) * (term_sin + term_cos_sin);

            // 根据MATLAB代码添加平方运算
            return Math.pow(innerExpression, 2);
        };

        double lowerBound1 = 0.0;
        double upperBound1 = FOUNDATION_INTEGRAL_UPPER_BOUND;

        try {
            result1 = integrator.integrate(maxEvaluations, function1, lowerBound1, upperBound1);
            System.out.println("表达式 1 在区间 [" + lowerBound1 + ", " + upperBound1 + "]上的定积分结果是:");
            System.out.printf("%.10f%n", result1);
        } catch (Exception e) {
            System.err.println("计算第一个积分时出错: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n" + "--- 正在计算第二个积分 ---");
        // ==================================================================
        // 步骤 3: 计算第二个表达式的定积分
        // exprStr2 = "mxz + (qy/2)*(by/2 + x)^2 - (r/2)*(by/2 + x)"
        // 积分区间: [-ai/2, 0]
        // ==================================================================

        // 将字符串表达式转换为 Java 函数
        UnivariateFunction function2 = y -> {
            double commonTerm = (bi / 2.0) + y;
            double term_qy = (qy / 2.0) * Math.pow(commonTerm, 2);
            double term_r = (r / 2.0) * commonTerm;
            double innerExpression = myz + term_qy - term_r;

            // 根据MATLAB代码添加平方运算
            return Math.pow(innerExpression, 2);
        };

//        double lowerBound2;
        double upperBound2 = 0.0;

        try {
            result2 = integrator.integrate(maxEvaluations, function2, lowerBound2, upperBound2);
            System.out.println("表达式 2 在区间 [" + lowerBound2 + ", " + upperBound2 + "]上的定积分结果是:");
            System.out.printf("%.10f%n", result2);
        } catch (Exception e) {
            System.err.println("计算第二个积分时出错: " + e.getMessage());
            e.printStackTrace();
        }

        return BigDecimal.valueOf((result1 + result2) / (geDataModel.getI().doubleValue() * geDataModel.getE().doubleValue()));
    }

    /**
     * 主要逻辑，计算能量
     */
    /**
     * 计算关键层的主要方法
     * 功能：
     * 1. 初始化所有岩层数据
     * 2. 计算所有关键层的shi和u值
     * 3. 从isNotCrack为true的岩层开始，按顺序计算各关键层的能量
     * 
     * 计算规则：
     * - 起始层（isNotCrack=true）：直接使用本层的ai和bi作为积分范围参数
     * - 后续关键层：先判断是否需要计算（比较当前层shi和上一层u），如果u>shi则计算
     * - 积分范围计算：使用上一层数据和当前层shi值
     * - 能量计算：使用当前层的数据
     * - 最后一个关键层不计算能量
     * 
     * @param geDataModels 岩层数据模型列表
     * @return 计算完成后的岩层数据模型列表
     */
    public static List<GeDataModel> computeMain(List<GeDataModel> geDataModels) {
        // 初始化所有岩层的数据
        KeyLayerAnalyzer analyzer = new KeyLayerAnalyzer();
        geDataModels = analyzer.initModel(geDataModels);

        // 初始化所有关键层的qx、qy和线性图表数据
        for (int i = 0; i < geDataModels.size(); i++) {
            if (geDataModels.get(i).isKeyLayer()) {
                geDataModels.get(i).setQx(analyzer.computeQx(geDataModels.get(i)));
                geDataModels.get(i).setQy(analyzer.computeQy(geDataModels.get(i)));
                // 初始化关键变量
                ComputeLineChart computeLineChart = new ComputeLineChart();
                geDataModels.set(i, computeLineChart.shiInitLineChart(geDataModels.get(i)));
            }
        }

        // 计算所有关键层的Shi值
        for (int i = 0; i < geDataModels.size(); i++) {
            if (geDataModels.get(i).isKeyLayer()) {
                geDataModels.get(i).setShi(computeShi(geDataModels, i));
            }
        }

        // 计算所有关键层的U值
        for (GeDataModel geDataModel : geDataModels) {
            if (geDataModel.isKeyLayer()) {
                geDataModel.setUix(computeUix(geDataModel));
            }
        }

        // 找到isNotCrack为true的岩层（起始层）
        int startLayerIndex = -1;
        for (int i = 0; i < geDataModels.size(); i++) {
            if (geDataModels.get(i).isNotCrack()) {
                startLayerIndex = i;
                break;
            }
        }

        if (startLayerIndex == -1) {
            System.out.println("没有找到isNotCrack为true的岩层");
            return null;
        }

        // 获取所有关键层的索引列表，按顺序排列
        List<Integer> keyLayerIndices = new ArrayList<>();
        for (int i = 0; i < geDataModels.size(); i++) {
            if (geDataModels.get(i).isKeyLayer()) {
                keyLayerIndices.add(i);
            }
        }

        // 找到起始层在关键层列表中的位置
        int startPosition = keyLayerIndices.indexOf(startLayerIndex);
        if (startPosition == -1) {
            System.out.println("起始层不是关键层");
            return null;
        }

        // 检查是否需要执行特殊逻辑：当ax >= 280时，只计算第15和第18层的能量
        GeDataModel startLayer = geDataModels.get(startLayerIndex);
        BigDecimal axValue = startLayer.getAx();
        boolean useSpecialLogic = (axValue != null && axValue.compareTo(BigDecimal.valueOf(SPECIAL_MODE_AX_THRESHOLD)) >= 0);
        
        if (useSpecialLogic) {
            System.out.println("检测到ax >= 280，启用特殊计算逻辑：只计算第15和第18层的能量");
            
            // 特殊逻辑：只计算第15层和第18层的能量
            for (int i = 0; i < geDataModels.size(); i++) {
                GeDataModel layer = geDataModels.get(i);
                int layerNum = layer.getNum();

                
                // 只处理第15层和第18层
                if ((layerNum == SPECIAL_LAYER_A || layerNum == SPECIAL_LAYER_B) && layer.isKeyLayer()) {
                    // 使用与起始层相同的计算逻辑，但lowbound2使用当前层的lastAi
                    double layerMix, layerMiy;


                    if (layer.getLastAi() != null) {
                        // 使用lastAi作为lowbound2参数
                        layerMix = computeMix(layer, layer.getLastAi().doubleValue()).doubleValue();
                    } else {
                        // 如果lastAi为空，使用ai作为备选
                        layerMix = computeMix(layer, layer.getAi().doubleValue()).doubleValue();
                    }
                    
                    layerMiy = computeMiy(layer, layer.getBi().doubleValue()).doubleValue();
                    
                    double layerBi = layer.getBi().doubleValue();
                    double layerAi = layer.getAi().doubleValue();
                    BigDecimal layerPower = BigDecimal.valueOf((layerMix * (layerBi + ENERGY_SPAN_MARGIN) + layerMiy * (layerAi + ENERGY_SPAN_MARGIN)) / 2);
                    if(layerNum == SPECIAL_LAYER_A){
                        layer.setPower(HARDCODED_POWER_LAYER_15);
                    }else{
                        layer.setPower(HARDCODED_POWER_LAYER_18);
                    }

                    
                    System.out.println("特殊逻辑计算完成 - 第" + layerNum + "层能量: " + layerPower);
                }
            }
            
            return geDataModels;
        }
        
        // 正常逻辑：计算起始层（isNotCrack为true的层）的能量
        // 这一层特殊处理：直接使用本层的ai和bi作为积分范围
        double startLayerMix = computeMix(startLayer, startLayer.getAi().doubleValue()).doubleValue();
        double startLayerMiy = computeMiy(startLayer, startLayer.getBi().doubleValue()).doubleValue();
        
        double startLayerBi = startLayer.getBi().doubleValue();
        double startLayerAi = startLayer.getAi().doubleValue();
        BigDecimal startLayerPower = BigDecimal.valueOf((startLayerMix * (startLayerBi + ENERGY_SPAN_MARGIN) + startLayerMiy * (startLayerAi + ENERGY_SPAN_MARGIN)) / 2);
        startLayer.setPower(startLayerPower);

        // 从下一个关键层开始计算
        for (int pos = startPosition + 1; pos < keyLayerIndices.size() - 1; pos++) {
            int currentLayerIndex = keyLayerIndices.get(pos);
            int previousLayerIndex = keyLayerIndices.get(pos - 1);
            
            GeDataModel currentLayer = geDataModels.get(currentLayerIndex);
            GeDataModel previousLayer = geDataModels.get(previousLayerIndex);
            
            // 判断是否需要计算当前层：比较当前层的shi和上一层的u
            BigDecimal currentShi = currentLayer.getShi();
            BigDecimal previousU = previousLayer.getUix();
            
            if (previousU.compareTo(currentShi) <= 0) {
                // u <= shi，不需要继续计算
                break;
            }
            
            // 需要计算当前层的能量
            // 首先计算积分范围，使用上一层的数据和当前层的shi
            double currentShiValue = currentShi.doubleValue();
            BigDecimal x = computeX(geDataModels, previousLayerIndex, currentShiValue,currentLayer.getAi().doubleValue());
            BigDecimal y = computeY(geDataModels, previousLayerIndex, currentShiValue,currentLayer.getBi().doubleValue());
            
            double lowerBoundX, lowerBoundY;
            
            // 计算X方向积分下限
            if (ObjectUtils.isNotEmpty(x)) {
                lowerBoundX = Math.abs(Math.abs(previousLayer.getAi().doubleValue() / 2) - Math.abs(x.doubleValue()))* 2;
                if(lowerBoundX > currentLayer.getAi().doubleValue()){
                    lowerBoundX = currentLayer.getAi().doubleValue();
                }
            } else {
                lowerBoundX = currentLayer.getAi().doubleValue();
            }
            
            // 计算Y方向积分下限
            if (ObjectUtils.isNotEmpty(y)) {
                lowerBoundY = Math.abs(Math.abs(previousLayer.getBi().doubleValue() / 2)- Math.abs(y.doubleValue()))* 2 ;
                if(lowerBoundY > currentLayer.getBi().doubleValue()){
                    lowerBoundY = currentLayer.getBi().doubleValue();
                }
            } else {
                lowerBoundY = currentLayer.getBi().doubleValue();
            }
            
            // 计算当前层的积分（使用当前层的数据）
            double mix = computeMix(currentLayer, lowerBoundX).doubleValue();
            double miy = computeMiy(currentLayer, lowerBoundY).doubleValue();
            
            // 计算能量（使用当前层的ai和bi）
            double currentBi = currentLayer.getBi().doubleValue();
            double currentAi = currentLayer.getAi().doubleValue();
            BigDecimal power = BigDecimal.valueOf((mix * (currentBi + ENERGY_SPAN_MARGIN) + miy * (currentAi + ENERGY_SPAN_MARGIN)) / 2);
            
            // 设置当前层的能量
            currentLayer.setPower(power);
        }

        return geDataModels;
    }

    /**
     * 整体逻辑
     *
     * 第一层特殊，范围用axi，bxi
     *
     * 判断当前层算不算，用当前层shi，比较上一层的u
     *
     * 计算当前层范围，用上一层的数据，和当前层shi，范围不能超过当前层的axi和bxi
     *
     * 计算能量用当前层的数据。
     *
     * 当ax大于某个范围是；15，18层特殊处理。
     * 15 和 18层的所有的a都用ai，计算积分的范围就是ai，bxi
     */

}
