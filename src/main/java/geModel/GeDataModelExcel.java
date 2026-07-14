package geModel;


import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@EqualsAndHashCode
public class GeDataModelExcel {


    /**
     * 是否为关键层
     */
    @ExcelProperty(value = "是否为关键层", index = 21)
    private String isKeyLayer;

    /**
     * 是否不垮落
     */
    @ExcelProperty(value = "是否垮落", index = 22)
    private String isNotCrack;

    /**
     * 当前层数
     */
    @ExcelProperty(value = "当前层数", index = 0)
    private int num;

    /**
     * 岩层名称
     */
    @ExcelProperty(value= "岩层名称" ,index = 1)
    private String name;

    /**
     * 层厚
     * 输入变量
     */
    @ExcelProperty(value= "层厚" ,index = 2)
    private BigDecimal h;

    /**
     * 体积力
     * 输入变量
     */
    @ExcelProperty(value= "体积力",index = 3)
    private BigDecimal l;

    /**
     * 弹性模量
     * 输入变量
     */
    @ExcelProperty(value= "弹性模量",index = 4)
    private BigDecimal e;

    /**
     * 抗拉强度
     * 输入变量
     */
    @ExcelProperty(value = "抗拉强度", index = 5)
    private BigDecimal r;

    /**
     * 现场实际推进距离
     * 输入变量
     */
    @ExcelProperty(value = "现场实际推进距离", index = 6)
    private BigDecimal ax;

    /**
     * 现场实际推进距离
     * 输入变量
     */
    @ExcelProperty(value = "现场实际推进距离", index = 7)
    private BigDecimal by;

    /**
     *岩层顶板破断角
     * 输入变量
     */
    @ExcelProperty(value = "岩层顶板破断角", index = 8)
    private BigDecimal af;

    /**
     *基础刚度
     * 输入变量
     */
    @ExcelProperty(value = "基础刚度", index = 9)
    private BigDecimal k;

    /**
     * 厚硬岩层任意截面惯性矩
     * 输入变量
     */
    @ExcelProperty(value = "厚硬岩层任意截面惯性矩", index = 18)
    private BigDecimal i;

    /**
     *由af和ax计算得出
     * 计算变量
     */
    @ExcelIgnore
    private BigDecimal ai;

    /**
     *由af和by计算得出
     * 计算变量
     */
    @ExcelIgnore
    private BigDecimal bi;

    /**
     *极限破断弯矩
     * 计算变量
     */
    @ExcelIgnore
    private BigDecimal m;

    /**
     *计算变量
     */
    @ExcelProperty(value = "", index = 17)
    private BigDecimal bd;

    /**
     * 计算变量
     */
    @ExcelIgnore
    private BigDecimal qx;

    /**
     * 计算变量
     */
    @ExcelIgnore
    private BigDecimal qy;

    /**
     * 岩梁宽度
     */
    @ExcelProperty(value = "岩梁宽度", index = 10)
    private BigDecimal b;

    /**
     * 碎涨系数
     */
    @ExcelProperty(value = "碎涨系数", index = 11)
    private BigDecimal w;

    /**
     * 采煤高度
     */
    @ExcelProperty( value = "采煤高度", index = 12)
    private BigDecimal mh;

    /**
     * delta，计算图形用的角度
     */
    @ExcelProperty(value = "delta", index = 13)
    private BigDecimal dta;

    /**
     * ht 煤层高度
     */
    @ExcelProperty(value = "煤层高度", index = 14)
    private BigDecimal ht;



    /**
     * dMax1
     */
    @ExcelProperty(value = "DMax1", index = 15)
    private BigDecimal dMax1;

    /**
     * dMax2
     */
    @ExcelProperty(value = "DMax2", index = 16)
    private BigDecimal dMax2;


    /**
     * M1
     */
    @ExcelProperty(value = "M1", index = 19)
    private BigDecimal M1;

    /**
     * M2
     */
    @ExcelProperty(value = "M2", index = 20)
    private BigDecimal M2;

    /**
     * 破断步距
     */
    @ExcelProperty(value = "破断步距", index = 23)
    private BigDecimal lastAi;


}
