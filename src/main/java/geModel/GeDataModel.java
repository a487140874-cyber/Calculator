package geModel;




import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

//岩层
@Getter
@Setter
public class GeDataModel
{

    /**
     * 是否为关键层
     */
    private boolean isKeyLayer;

    /**
     * 当前层数
     */
    private int num;

    /**
     * 岩层名称
     */
    private String name;

    /**
     * 层厚
     * 输入变量
     */
    private BigDecimal h;

    /**
     * 体积力
     * 输入变量
     */
    private BigDecimal l;

    /**
     * 弹性模量
     * 输入变量
     */
    private BigDecimal e;

    /**
     * 抗拉强度
     * 输入变量
     */
    private BigDecimal r;

    /**
     * 现场实际推进距离
     * 输入变量
     */
    private BigDecimal ax;

    /**
     * 现场实际推进距离
     * 输入变量
     */
    private BigDecimal by;

    /**
     *岩层顶板破断角
     * 输入变量
     */
    private BigDecimal af;

    /**
     *基础刚度
     * 输入变量
     */
    private BigDecimal k;

    /**
     * 厚硬岩层任意截面惯性矩
     * 输入变量
     */
    private BigDecimal i;

    /**
     *由af和ax计算得出
     * 计算变量
     */
    private BigDecimal ai;

    /**
     *由af和by计算得出
     * 计算变量
     */
    private BigDecimal bi;

    /**
     *极限破断弯矩
     * 计算变量
     */
    private BigDecimal m;

    /**
     * 关键层上承受载荷
     */
    private BigDecimal q;

    /**
     * 关键层上承受载荷
     */
    private BigDecimal qs;

    /**
     *计算变量
     */
    private BigDecimal bd;

    /**
     * 计算变量
     */
    private BigDecimal qx;

    /**
     * 计算变量
     */
    private BigDecimal qy;

    /**
     * 岩梁宽度
     */
    private BigDecimal b;

    /**
     * 碎涨系数
     */
    private BigDecimal w;

    /**
     * 采煤高度
     */
    private BigDecimal mh;

    /**
     * 是否不垮落
     */
    private boolean isNotCrack;



    private BigDecimal Mx0;

    private BigDecimal Mxz;

    private BigDecimal Qx0;

    private BigDecimal Rt;

    private BigDecimal My0;
    private BigDecimal Myz;
    private BigDecimal Qy0;

    /**
     * 破断步距
     */
    private BigDecimal lastAi;

    /**
     * M1。<b>为已被砍掉的图形功能而算，目前不参与任何计算</b>，仅导出到 Excel。
     * 由 {@code KeyLayerAnalyzer.computeM12()} 赋值，详见其注释。保留以备图形功能日后重做。
     */
    private BigDecimal M1;

    /**
     * M2。同 {@link #M1}，为已被砍掉的图形功能而算，目前不参与任何计算，仅导出到 Excel。
     */
    private BigDecimal M2;

    /**
     * dMax1（下沉量）。<b>属于已被砍掉的图形功能</b>，由 {@code ComputeLineChart.computeDMax()}
     * 计算，而该方法当前无人调用，故此字段始终为 null。保留以备日后重做。
     */
    private BigDecimal dMax1;

    /**
     * dMax2（下沉量）。同 {@link #dMax1}，属已砍掉的图形功能，当前始终为 null。
     */
    private BigDecimal dMax2;

    /**
     * q11
     */
    private BigDecimal q11;

    /**
     * q21
     */
    private BigDecimal q21;

    /**
     * q12
     */
    private BigDecimal q12;

    /**
     * q22
     */
    private BigDecimal q22;

    /**
     * delta，计算图形用的角度
     */
    private BigDecimal dta;

    /**
     * sigma1 计算图形变量
     */
    private BigDecimal sigma1;

    /**
     * sigma2 计算图形变量
     */
    private BigDecimal sigma2;

    /**
     * ht 煤层高度
     */
    private BigDecimal ht;


    /**
     * 能量
     */
    private BigDecimal power;

    /**
     * 卸荷回弹量
     */
    private BigDecimal shi;


    /**
     * R;使用Axi计算的R
     */
    private BigDecimal RR;


    /**
     * 计算的U
     */
    private BigDecimal uix;


}
