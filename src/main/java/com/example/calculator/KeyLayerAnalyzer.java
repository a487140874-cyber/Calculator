package com.example.calculator;

import geModel.GeDataModel;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import static java.lang.Math.abs;


/**
 * 是否破断核心计算
 */
public class KeyLayerAnalyzer {

    /** 破断步距搜索：起始值。 */
    private static final double FRACTURE_SEARCH_START = 1.0;

    /** 破断步距搜索：步长。 */
    private static final double FRACTURE_SEARCH_STEP = 0.1;

    /**
     * 破断步距搜索：上界（安全兜底）。
     *
     * <p>取值远大于任何有物理意义的破断步距（单位：米），因此不会截断正常的搜索——
     * 实测数据中收敛点都在 150 以内。它只用来防止搜索在「|Mi| 永远达不到 M」的岩层上
     * 无限循环下去。搜到上界仍未命中，即判定该层不破断。
     */
    private static final double FRACTURE_SEARCH_MAX = 10000;

    /**
     * 破断步距搜索：命中判据。当 |Mi| 超出极限破断弯矩 M 的量落在 (0, 5] 区间内时，
     * 认为找到了破断步距。
     */
    private static final double MOMENT_MATCH_TOLERANCE = 5;

    /**
     * 初始化变量
     * @param geDataModels
     * @return
     */
    public List<GeDataModel> initModel(List<GeDataModel> geDataModels){

//        for(GeDataModel geDataModel : geDataModels){
//            geDataModel.setL(BigDecimal.valueOf(0.025));
//        }

        for(GeDataModel geDataModel : geDataModels){
            //计算i
            geDataModel.setI(computeI(geDataModel.getB(), geDataModel.getH()));
            //计算q
            if(geDataModel.isKeyLayer()){
                geDataModel.setQ(computeQ(geDataModels, geDataModel.getNum()));
            }
        }
        //计算极限破断弯矩
        for(GeDataModel geDataModel : geDataModels){
//            BigDecimal sumQ = BigDecimal.ZERO;
//            //当前层不是关键层
//            if(!model.isKeyLayer()){
//                sumQ = sumQ.add(model.getQ());
//            }else{
//                sumQ = sumQ.add(model.getQ());
//                model.setQs(sumQ);
//                sumQ = BigDecimal.ZERO;
//            }
//            model.setQs(sumQ);

            //计算极限破断弯矩
            geDataModel.setM(computeMoment(geDataModel.getH(), geDataModel.getB(), geDataModel.getR()));
            //计算百搭
            geDataModel.setBd(computeBd(geDataModel.getK(), geDataModel.getE(), geDataModel.getI()));
            //计算ai
            geDataModel.setAi(computeAi(geDataModels, geDataModel.getNum()));
            //计算bi
            geDataModel.setBi(computeBi(geDataModels, geDataModel.getNum()));
        }

        //计算qx和qy
        for(GeDataModel geDataModel : geDataModels){
            if(geDataModel.isKeyLayer()){
                geDataModel.setQx(computeQx(geDataModel));
                geDataModel.setQy(computeQy(geDataModel));
            }
            geDataModel = computeM12(geDataModels, geDataModel);
        }
        return geDataModels;
    }

    /**
     * 「本层不破断」分支的开关，恒为 false —— 即该分支目前从不执行。
     *
     * <p>原代码此处的条件是 {@code "false".equals(getIsNotCrack())}。而 isNotCrack
     * 只会被设为 {@code "true"} 或保持 {@code null}，从未被设成 {@code "false"}，
     * 所以那个条件恒为假。isNotCrack 改用 boolean 后无法再原样写出这个「恒假」条件
     * （{@code !isNotCrack()} 的语义是反的），故以本常量显式表达，行为与原来完全一致。
     *
     * <p>这是当初有意的设计，不是笔误：分支代码予以保留，把开关拨到 true 即可启用。
     */
    private static final boolean USE_INTACT_LAYER_BRANCH = false;

    /**
     * 计算 M1 和 M2。
     *
     * <p><b>M1/M2 是为「岩层受力折线图 / 下沉量 dMax / 应力 sigma」等图形功能算的，
     * 而该图形功能后来被砍掉了。</b>因此它们目前<b>不参与任何计算</b>——关键层判定、
     * 破断、能量三步都不读取，唯一的活跃去向是导出到 Excel 的 M1、M2 两列。
     * 其余消费者（{@link ComputeLineChart#computeDMax}、{@link ComputeLineChart#computeSgm}）
     * 都属于那族尚未启用的图形代码。此逻辑按要求保留，以备图形功能日后重做。
     *
     * <p>当前实际走的是下面「本层破断」的分支（见 {@link #USE_INTACT_LAYER_BRANCH}），
     * 其语义为：M1 = 上方最近关键层的层厚；M2 = 再上一个关键层的层厚 − 上一个关键层的层厚。
     * 注意 M2 会出现负值（如第 1、8、10 层）——若日后重做图形功能，需先确认这是否是想要的量。
     */
    public GeDataModel computeM12(List<GeDataModel> geDataModels, GeDataModel geDataModel)
    {
        //当前层不破断
        if(USE_INTACT_LAYER_BRANCH){
            geDataModel.setM1(geDataModel.getH());
            for(int i = geDataModel.getNum(); i < geDataModels.size(); i++){
                if(geDataModels.get(i).isKeyLayer()){
                    geDataModel.setM2(geDataModels.get(i).getH().subtract(geDataModel.getH()));
                    break;
                }
            }
        }else{
            //本层破断
            for(int i = geDataModel.getNum(); i < geDataModels.size(); i++){
                if(geDataModels.get(i).isKeyLayer()){
                    geDataModel.setM1(geDataModels.get(i).getH());
                    for(int j = i + 1; j < geDataModels.size(); j++){
                        if(geDataModels.get(j).isKeyLayer()){
                            geDataModel.setM2(geDataModels.get(j).getH().subtract(geDataModels.get(i).getH()));
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return geDataModel;
    }

    /**
     * 判定 {@code current} 层不垮落，并连同其上覆的所有岩层一起标记为不垮落，然后结束整条计算。
     *
     * <p>物理依据：关键层一旦不破断，就托住了它上面的全部岩层，上覆各层自然也不会垮落。
     * 因此首个不垮落的关键层构成分界面——其下为垮落带（已垮落），其上（含本层）均为未垮落。
     *
     * <p>此前的写法是只给当前层置位就直接 return，上覆各层从未被评估、`isNotCrack` 停留在
     * 默认的 false，界面于是把它们显示成「已垮落」，与实际情况相反。
     *
     * <p>注意：能量计算 {@link ComputeKeyLayerShi#computeMain} 取<b>第一个</b>
     * isNotCrack 层作为起始层（正向遍历后 break），该层不受本方法影响，故能量结果不变。
     */
    private List<GeDataModel> markNotCrackFromHere(List<GeDataModel> geDataModels, GeDataModel current) {
        boolean reached = false;
        for (GeDataModel model : geDataModels) {
            if (model == current) {
                reached = true;
            }
            if (reached) {
                model.setNotCrack(true);
            }
        }
        return geDataModels;
    }

    /**
     * 计算q
     */
    public BigDecimal computeQ(List<GeDataModel> geDataModels, int index){
        int next = geDataModels.size() - 1;
        double q = 0.0;//体积力乘高度，rh和
        //获得本关键层的下一个关键层是第几层
        for(int i = index; i< geDataModels.size(); i++){
            if(geDataModels.get(i).isKeyLayer()){
                next = i;
                break;
            }

        }
        for(int i = index - 1;i<next;i++){
            double l = geDataModels.get(i).getL().doubleValue();
            double h = geDataModels.get(i).getH().doubleValue();
            q = q + l*h;
        }
        return  BigDecimal.valueOf(q);
    }

    /**
     * 计算qx
     */
    public BigDecimal computeQx(GeDataModel geDataModel){
        double q = geDataModel.getQ().doubleValue();
        double ai = geDataModel.getAi().doubleValue();
        double bi = geDataModel.getBi().doubleValue();
        double qx = q*bi*bi*bi*bi/(ai*ai*ai*ai + bi*bi*bi*bi);
        return BigDecimal.valueOf(q*bi*bi*bi*bi/(ai*ai*ai*ai + bi*bi*bi*bi));
    }

    /**
     * 计算qy
     */
    public BigDecimal computeQy(GeDataModel geDataModel){
        double q = geDataModel.getQ().doubleValue();
        double ai = geDataModel.getAi().doubleValue();
        double bi = geDataModel.getBi().doubleValue();
        double qy = q*ai*ai*ai*ai/(ai*ai*ai*ai + bi*bi*bi*bi);
        return BigDecimal.valueOf(q*ai*ai*ai*ai/(ai*ai*ai*ai + bi*bi*bi*bi));
    }


    public BigDecimal computeQx(GeDataModel geDataModel, BigDecimal aiValue){
        double q = geDataModel.getQ().doubleValue();
        double ai = aiValue.doubleValue();
        double bi = geDataModel.getBi().doubleValue();
        return BigDecimal.valueOf(q*bi*bi*bi*bi/(ai*ai*ai*ai + bi*bi*bi*bi));

    }

    public BigDecimal computeQy(GeDataModel geDataModel, BigDecimal aiValue){
        double q = geDataModel.getQ().doubleValue();
        double ai = aiValue.doubleValue();
        double bi = geDataModel.getBi().doubleValue();
        return BigDecimal.valueOf(q*ai*ai*ai*ai/(ai*ai*ai*ai + bi*bi*bi*bi));
    }


    /**
     * 计算i
     */
    public BigDecimal computeI(BigDecimal B,BigDecimal H){
        double b = B.doubleValue();
        double h = H.doubleValue();
        double i = b*h*h*h/12.0;
        return BigDecimal.valueOf(b*h*h*h/12.0);
    }



    /**
     * 计算极限破断弯矩
     */
    public BigDecimal computeMoment(BigDecimal h,BigDecimal b,BigDecimal r){
        return b.multiply(h).multiply(h).multiply(r).divide(new BigDecimal(6), 10, RoundingMode.HALF_UP);
    }

    /**
     * 计算百搭
     */
    public BigDecimal computeBd(BigDecimal k,BigDecimal e,BigDecimal i){
        BigDecimal four = BigDecimal.valueOf(4);
        BigDecimal bd = k.divide(e.multiply(i).multiply(four), 10, RoundingMode.HALF_UP);
        double fourValue = bd.doubleValue();
        fourValue = Math.sqrt(fourValue);
        fourValue = Math.sqrt(fourValue);
        return BigDecimal.valueOf(fourValue);
    }

    /**
     * 计算ai
     */
    public BigDecimal computeAi(List<GeDataModel> geDataModels, int index){
        BigDecimal length = BigDecimal.ZERO;
        if(index == 1){
            return geDataModels.get(0).getAx();
        }
        for(int i = 0; i< index - 1; i++){
            double af = geDataModels.get(i).getAf().doubleValue();
            af = Math.toRadians(af);
            BigDecimal cotValue = BigDecimal.valueOf(Math.tan(af));
            cotValue = BigDecimal.ONE.divide(cotValue, 10, RoundingMode.HALF_UP);
            length = length.add(geDataModels.get(i).getH().multiply(cotValue).multiply(BigDecimal.TWO));
        }
        BigDecimal cs =  geDataModels.get(index-1).getAx().subtract(length);
        return geDataModels.get(index-1).getAx().subtract(length);
    }

    /**
     * 计算bi
     */
    public BigDecimal computeBi(List<GeDataModel> geDataModels, int index){
        BigDecimal length = BigDecimal.ZERO;
        if(index == 1){
            return geDataModels.get(0).getBy();
        }
        for(int i = 0; i< index - 1; i++){
            double af = geDataModels.get(i).getAf().doubleValue();
            af = Math.toRadians(af);
            BigDecimal cotValue = BigDecimal.valueOf(Math.tan(af));
            cotValue = BigDecimal.ONE.divide(cotValue, 10, RoundingMode.HALF_UP);
            length = length.add(geDataModels.get(i).getH().multiply(cotValue).multiply(BigDecimal.TWO));
        }
        return geDataModels.get(index-1).getBy().subtract(length);
    }

    /**
     *a>b 核心公式
     */
    public BigDecimal computeMain1(BigDecimal ai, BigDecimal bi, BigDecimal bdValue, BigDecimal qxValue, BigDecimal qyValue) {
        BigDecimal a = ai;
        BigDecimal b = bi;
        BigDecimal bd = bdValue;
        BigDecimal qx = qxValue;
        BigDecimal qy = qyValue;

        // Calculate the numerator (fz)
        BigDecimal term1 = a.multiply(qx)
                .multiply(BigDecimal.valueOf(-576)
                        .add(BigDecimal.valueOf(144).multiply(a.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(60).multiply(a.pow(3)).multiply(bd.pow(3)))
                        .add(BigDecimal.valueOf(12).multiply(a.pow(4)).multiply(bd.pow(4)))
                        .add(a.pow(5).multiply(bd.pow(5)))
                        .subtract(BigDecimal.valueOf(24).multiply(b).multiply(bd)
                                .multiply(BigDecimal.valueOf(12)
                                        .add(BigDecimal.valueOf(6).multiply(a).multiply(bd))
                                        .add(a.pow(2).multiply(bd.pow(2)))))
                        .subtract(BigDecimal.valueOf(12).multiply(b.pow(2)).multiply(bd.pow(2))
                                .multiply(BigDecimal.valueOf(12)
                                        .add(BigDecimal.valueOf(6).multiply(a).multiply(bd))
                                        .add(a.pow(2).multiply(bd.pow(2)))))
                        .subtract(BigDecimal.valueOf(2).multiply(b.pow(3)).multiply(bd.pow(3))
                                .multiply(BigDecimal.valueOf(12)
                                        .add(BigDecimal.valueOf(6).multiply(a).multiply(bd))
                                        .add(a.pow(2).multiply(bd.pow(2))))));

        BigDecimal term2 = BigDecimal.valueOf(3).multiply(b)
                .multiply(BigDecimal.valueOf(2).add(a.multiply(bd)).pow(2))
                .multiply(BigDecimal.valueOf(48)
                        .add(BigDecimal.valueOf(24).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(8).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(b.pow(3).multiply(bd.pow(3))))
                .multiply(qy);

        BigDecimal fz = term1.subtract(term2);

        // Calculate the denominator (fm)
        BigDecimal fm = BigDecimal.valueOf(48).multiply(bd)
                .multiply(BigDecimal.valueOf(2).add(a.multiply(bd)))
                .multiply(BigDecimal.valueOf(48)
                        .add(BigDecimal.valueOf(12).multiply(a).multiply(bd))
                        .add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(6).multiply(a.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(6).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(a.pow(3).multiply(bd.pow(3)))
                        .add(b.pow(3).multiply(bd.pow(3))));

        // Divide fz by fm and round to 10 decimal places
        return fz.divide(fm, 10, RoundingMode.HALF_UP);
    }
//    public double computeMain1(BigDecimal ai,BigDecimal bi,BigDecimal bdValue,BigDecimal qxValue,BigDecimal qyValue){
//        double a = ai.doubleValue();
//        double b = bi.doubleValue();
//        double bd = bdValue.doubleValue();
//        double qx = qxValue.doubleValue();
//        double qy = qyValue.doubleValue();
//
//        ///
//        double fz = (a*qx*((-576 + 144*a*a*bd*bd + 60*a*a*a*bd*bd*bd + 12*a*a*a*a*bd*bd*bd*bd + a*a*a*a*a*bd*bd*bd*bd*bd
//                -24*b*bd*(12+6*a*bd+a*a*bd*bd) - 12*b*b*bd*bd*(12+6*a*bd+a*a*bd*bd) -
//                2*b*b*b*bd*bd*bd*(12+6*a*bd+a*a*bd*bd)) ) - (3*b*(2+a*bd)*(2+a*bd)*(48+24*b*bd+8*b*b*bd*bd
//                + b*b*b*bd*bd*bd))*qy);
//        double fm = (48*bd*(2+a*bd)*(48+12*a*bd+12*b*bd+6*a*a*bd*bd+6*b*b*bd*bd+a*a*a*bd*bd*bd + b*b*b*bd*bd*bd));
//
//        BigDecimal z =  BigDecimal.valueOf(fz);
//        BigDecimal m =  BigDecimal.valueOf(fm);
//        return  z.divide(m, 10, RoundingMode.HALF_UP).doubleValue();
//    }


    /**
     *a<b 核心公式
     */
    public BigDecimal computeMain2(BigDecimal ai, BigDecimal bi, BigDecimal bdValue, BigDecimal qxValue, BigDecimal qyValue) {
        BigDecimal a = ai;
        BigDecimal b = bi;
        BigDecimal bd = bdValue;
        BigDecimal qx = qxValue;
        BigDecimal qy = qyValue;

        // Calculate the numerator (fz)
        BigDecimal term1 = a.negate().multiply(BigDecimal.valueOf(3))
                .multiply(BigDecimal.valueOf(2).add(b.multiply(bd)).pow(2)
                        .multiply(BigDecimal.valueOf(48)
                                .add(BigDecimal.valueOf(24).multiply(a).multiply(bd))
                                .add(BigDecimal.valueOf(8).multiply(a.pow(2)).multiply(bd.pow(2)))
                                .add(a.pow(3).multiply(bd.pow(3))))
                        .multiply(qx));

        BigDecimal term2 = b.multiply(qy)
                .multiply(BigDecimal.valueOf(-576)
                        .add(BigDecimal.valueOf(144).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(60).multiply(b.pow(3)).multiply(bd.pow(3)))
                        .add(BigDecimal.valueOf(12).multiply(b.pow(4)).multiply(bd.pow(4)))
                        .add(b.pow(5).multiply(bd.pow(5)))
                        .subtract(BigDecimal.valueOf(24).multiply(a).multiply(bd)
                                .multiply(BigDecimal.valueOf(12)
                                        .add(BigDecimal.valueOf(6).multiply(b).multiply(bd))
                                        .add(b.pow(2).multiply(bd.pow(2)))))
                        .subtract(BigDecimal.valueOf(12).multiply(a.pow(2)).multiply(bd.pow(2))
                                .multiply(BigDecimal.valueOf(12)
                                        .add(BigDecimal.valueOf(6).multiply(b).multiply(bd))
                                        .add(b.pow(2).multiply(bd.pow(2)))))
                        .subtract(BigDecimal.valueOf(2).multiply(a.pow(3)).multiply(bd.pow(3))
                                .multiply(BigDecimal.valueOf(12)
                                        .add(BigDecimal.valueOf(6).multiply(b).multiply(bd))
                                        .add(b.pow(2).multiply(bd.pow(2))))));

        BigDecimal fz = term1.add(term2);

        // Calculate the denominator (fm)
        BigDecimal fm = BigDecimal.valueOf(48).multiply(bd)
                .multiply(BigDecimal.valueOf(2).add(b.multiply(bd)))
                .multiply(BigDecimal.valueOf(48)
                        .add(BigDecimal.valueOf(12).multiply(a).multiply(bd))
                        .add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(6).multiply(a.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(6).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(a.pow(3).multiply(bd.pow(3)))
                        .add(b.pow(3).multiply(bd.pow(3))));

        // Divide fz by fm and round to 10 decimal places
        return fz.divide(fm, 10, RoundingMode.HALF_UP);
    }
//    public double computeMain2(BigDecimal ai,BigDecimal bi,BigDecimal bdValue,BigDecimal qxValue,BigDecimal qyValue){
//        double a = ai.doubleValue();
//        double b = bi.doubleValue();
//        double bd = bdValue.doubleValue();
//        double qx = qxValue.doubleValue();
//        double qy = qyValue.doubleValue();
//
//        double fz = (-3*a*(((2+b*bd)*(2+b*bd)*(48+24*a*bd+8*a*a*bd*bd
//                + a*a*a*bd*bd*bd))*qx) + b*qy*((-576 + 144*b*b*bd*bd + 60*b*b*b*bd*bd*bd + 12*b*b*b*b*bd*bd*bd*bd + b*b*b*b*b*bd*bd*bd*bd*bd
//                -24*a*bd*(12+6*b*bd+b*b*bd*bd) - 12*a*a*bd*bd*(12+6*b*bd+b*b*bd*bd) -
//                2*a*a*a*bd*bd*bd*(12+6*b*bd+b*b*bd*bd))));
//
//        double fm =  (48*bd*(2+b*bd)*(48+12*a*bd+12*b*bd+6*a*a*bd*bd+6*b*b*bd*bd+a*a*a*bd*bd*bd + b*b*b*bd*bd*bd));
//        BigDecimal z =  BigDecimal.valueOf(fz);
//        BigDecimal m =  BigDecimal.valueOf(fm);
//        return  z.divide(m, 10, RoundingMode.HALF_UP).doubleValue();
//
////        return (-3*a*(((2+a*bd)*(2+a*bd)*(48+24*a*bd+8*a*a*bd*bd
////                + a*a*a*bd*bd*bd))*qx) + b*qy*((-576 + 144*b*b*bd*bd + 60*b*b*b*bd*bd*bd + 12*b*b*b*b*bd*bd*bd*bd + b*b*b*b*b*bd*bd*bd*bd*bd
////                -24*a*bd*(12+6*b*bd+b*b*bd*bd) - 12*a*a*bd*bd*(12+6*b*bd+b*b*bd*bd) -
////                2*a*a*a*bd*bd*bd*(12+6*b*bd+b*b*bd*bd)))) / (48*bd*(2+b*bd)*(48+12*a*bd+12*b*bd+6*a*a*bd*bd+6*b*b*bd*bd+a*a*a*bd*bd*bd + b*b*b*bd*bd*bd));
//    }
//
//    //计算u
////    public double computeU(Model model,BigDecimal aValue){
////        double a = aValue.doubleValue();
////        double h = model.getH().doubleValue();
////        double b = model.getBi().doubleValue();
////        double e = model.getE().doubleValue();
////        double i = model.getI().doubleValue();
////        double r = model.getR().doubleValue();
////        double bd = model.getBd().doubleValue();
////        double qx = model.getQx().doubleValue();
////        double qy = model.getQy().doubleValue();
////
//////        double fz = (-(h*h*a*a*r/48)+a*qx*(60*a*a*a*a*a*bd*bd*bd*bd*bd+12*a*a*a*a*a*a*bd*bd*bd*bd*bd*bd
//////                +a*a*a*a*a*a*a*bd*bd*bd*bd*bd*bd*bd-a*a*a*a*bd*bd*bd*bd*(-168+12*b*bd+6*b*b*bd*bd+b*b*b*bd*bd*bd)
//////                -2*a*a*a*bd*bd*bd*(-120+12*b*bd+6*b*b*bd*bd+b*b*b*bd*bd*bd)+96*(24+12*b*bd+6*b*b*bd*bd+b*b*b*bd*bd*bd)
//////                +96*a*bd*(24+12*b*bd+6*b*b*bd*bd+b*b*b*bd*bd*bd)+16*a*a*bd*bd*(24+12*b*bd+6*b*b*bd*bd+b*b*b*bd*bd*bd))
//////                - 2*b*(48+24*b*bd+8*b*b*bd*bd+b*b*b*bd*bd*bd)*(-24-24*a*bd-6*a*a*bd*bd+2*a*a*a*bd*bd*bd+a*a*a*a*bd*bd*bd*bd)*qy);
//////        double fm =  (384*bd*bd*bd*(2+a*bd)*(48+12*a*bd+12*b*bd+6*a*a*bd*bd+6*b*b*bd*bd+a*a*a*bd*bd*bd+b*b*b*bd*bd*bd)*e*i);
////        double fz = (a*qx*(-60*a*a*a*a*a*bd*bd*bd*bd*bd-12*a*a*a*a*a*a*bd*bd*bd*bd*bd*bd
////                -a*a*a*a*a*a*a*bd*bd*bd*bd*bd*bd*bd+3*a*a*a*a*bd*bd*bd*bd*(-40+12*b*bd+6*b*b*bd*bd+b*b*b*bd*bd*bd)
////                +2*a*a*a*bd*bd*bd*(120+132*b*bd+66*b*b*bd*bd+11*b*b*b*bd*bd*bd)+96*(24+12*b*bd+6*b*b*bd*bd+b*b*b*bd*bd*bd)
////                +96*a*bd*(24+12*b*bd+6*b*b*bd*bd+b*b*b*bd*bd*bd)+64*a*a*bd*bd*(24+12*b*bd+6*b*b*bd*bd+b*b*b*bd*bd*bd))
////                + 4*b*(48+24*b*bd+8*b*b*bd*bd+b*b*b*bd*bd*bd)*(12+12*b*bd+9*a*a*bd*bd+5*a*a*a*bd*bd*bd+a*a*a*a*bd*bd*bd*bd)*qy);
////        double fm =  (384*bd*bd*bd*(2+a*bd)*(48+12*a*bd+12*b*bd+6*a*a*bd*bd+6*b*b*bd*bd+a*a*a*bd*bd*bd+b*b*b*bd*bd*bd));
////
////        return  (-(h*h*a*a*r/48)+(fz/fm))/e/i*5;
////     }
    public BigDecimal computeU(GeDataModel geDataModel, BigDecimal aValue) {
        // Set the precision for BigDecimal calculations
        MathContext mc = new MathContext(30);

        // Extract values from the model
        BigDecimal a = aValue;
        BigDecimal h = geDataModel.getH();
        BigDecimal b = geDataModel.getBi();
        BigDecimal e = geDataModel.getE();
        BigDecimal i = geDataModel.getI();
        BigDecimal r = geDataModel.getR();
        BigDecimal bd = geDataModel.getBd();
        BigDecimal qx = geDataModel.getQx();
        BigDecimal qy = geDataModel.getQy();

        // Calculate the numerator (fz)
        BigDecimal term1 = a.multiply(qx).multiply(
                BigDecimal.valueOf(-60).multiply(a.pow(5)).multiply(bd.pow(5))
                        .subtract(BigDecimal.valueOf(12).multiply(a.pow(6)).multiply(bd.pow(6)))
                        .subtract(a.pow(7).multiply(bd.pow(7)))
                        .add(BigDecimal.valueOf(3).multiply(a.pow(4)).multiply(bd.pow(4))
                                .multiply(BigDecimal.valueOf(-40).add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                                        .add(BigDecimal.valueOf(6).multiply(b.pow(2)).multiply(bd.pow(2)))
                                        .add(b.pow(3).multiply(bd.pow(3)))))
                        .add(BigDecimal.valueOf(2).multiply(a.pow(3)).multiply(bd.pow(3))
                                .multiply(BigDecimal.valueOf(120).add(BigDecimal.valueOf(132).multiply(b).multiply(bd))
                                        .add(BigDecimal.valueOf(66).multiply(b.pow(2)).multiply(bd.pow(2)))
                                        .add(BigDecimal.valueOf(11).multiply(b.pow(3)).multiply(bd.pow(3)))))
                        .add(BigDecimal.valueOf(96).multiply(
                                BigDecimal.valueOf(24).add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                                        .add(BigDecimal.valueOf(6).multiply(b.pow(2)).multiply(bd.pow(2)))
                                        .add(b.pow(3).multiply(bd.pow(3)))))
                        .add(BigDecimal.valueOf(96).multiply(a).multiply(bd)
                                .multiply(BigDecimal.valueOf(24).add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                                        .add(BigDecimal.valueOf(6).multiply(b.pow(2)).multiply(bd.pow(2)))
                                        .add(b.pow(3).multiply(bd.pow(3)))))
                        .add(BigDecimal.valueOf(64).multiply(a.pow(2)).multiply(bd.pow(2))
                                .multiply(BigDecimal.valueOf(24).add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                                        .add(BigDecimal.valueOf(6).multiply(b.pow(2)).multiply(bd.pow(2)))
                                        .add(b.pow(3).multiply(bd.pow(3))))), mc);

        BigDecimal term2 = BigDecimal.valueOf(4).multiply(b)
                .multiply(BigDecimal.valueOf(48).add(BigDecimal.valueOf(24).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(8).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(b.pow(3).multiply(bd.pow(3))))
                .multiply(BigDecimal.valueOf(12).add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(9).multiply(a.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(5).multiply(a.pow(3)).multiply(bd.pow(3)))
                        .add(a.pow(4).multiply(bd.pow(4))))
                .multiply(qy);

        BigDecimal fz = term1.add(term2, mc);

        // Calculate the denominator (fm)
        BigDecimal fm = BigDecimal.valueOf(384).multiply(bd.pow(3))
                .multiply(BigDecimal.valueOf(2).add(a.multiply(bd)), mc)
                .multiply(BigDecimal.valueOf(48).add(BigDecimal.valueOf(12).multiply(a).multiply(bd))
                        .add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(6).multiply(a.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(6).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(a.pow(3).multiply(bd.pow(3)))
                        .add(b.pow(3).multiply(bd.pow(3))), mc);

        // Compute the result
        BigDecimal part1 = h.pow(2).multiply(a.pow(2)).multiply(r).divide(BigDecimal.valueOf(48), mc).negate();
        BigDecimal result = (part1.add(fz.divide(fm, mc))).divide(e.multiply(i), mc).multiply(BigDecimal.valueOf(5));

        return result;
    }

    public static BigDecimal computeU2(GeDataModel geDataModel, BigDecimal aValue) {

        BigDecimal a = aValue;
        BigDecimal h = geDataModel.getH();
        BigDecimal b = geDataModel.getBi();
        BigDecimal e = geDataModel.getE();
        BigDecimal i = geDataModel.getI();
        BigDecimal r = geDataModel.getR();
        BigDecimal bd = geDataModel.getBd();
        BigDecimal qx = geDataModel.getQx();
        BigDecimal qy = geDataModel.getQy();

        // Set the precision for BigDecimal calculations
        MathContext mc = new MathContext(30);

        // Calculate the numerator (fz)
        BigDecimal term1 = BigDecimal.valueOf(4).multiply(a).multiply(qx).multiply(
                BigDecimal.valueOf(48).add(BigDecimal.valueOf(24).multiply(a).multiply(bd))
                        .add(BigDecimal.valueOf(8).multiply(a.pow(2)).multiply(bd.pow(2)))
                        .add(a.pow(3).multiply(bd.pow(3))), mc);

        BigDecimal term2 = BigDecimal.valueOf(12).add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                .add(BigDecimal.valueOf(9).multiply(b.pow(2)).multiply(bd.pow(2)))
                .add(BigDecimal.valueOf(5).multiply(b.pow(3)).multiply(bd.pow(3)))
                .add(b.pow(4).multiply(bd.pow(4)));

        BigDecimal term3 = BigDecimal.valueOf(2304).add(BigDecimal.valueOf(2304).multiply(b).multiply(bd))
                .add(BigDecimal.valueOf(1536).multiply(b.pow(2)).multiply(bd.pow(2)))
                .add(BigDecimal.valueOf(240).multiply(b.pow(3)).multiply(bd.pow(3)))
                .subtract(BigDecimal.valueOf(120).multiply(b.pow(4)).multiply(bd.pow(4)))
                .subtract(BigDecimal.valueOf(60).multiply(b.pow(5)).multiply(bd.pow(5)))
                .subtract(BigDecimal.valueOf(12).multiply(b.pow(6)).multiply(bd.pow(6)))
                .subtract(b.pow(7).multiply(bd.pow(7)));

        BigDecimal term4 = BigDecimal.valueOf(12).multiply(a).multiply(bd)
                .multiply(BigDecimal.valueOf(96).add(BigDecimal.valueOf(96).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(64).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(22).multiply(b.pow(3)).multiply(bd.pow(3)))
                        .add(BigDecimal.valueOf(3).multiply(b.pow(4)).multiply(bd.pow(4))), mc);

        BigDecimal term5 = BigDecimal.valueOf(6).multiply(a.pow(2)).multiply(bd.pow(2))
                .multiply(BigDecimal.valueOf(96).add(BigDecimal.valueOf(96).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(64).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(22).multiply(b.pow(3)).multiply(bd.pow(3)))
                        .add(BigDecimal.valueOf(3).multiply(b.pow(4)).multiply(bd.pow(4))), mc);

        BigDecimal term6 = a.pow(3).multiply(bd.pow(3))
                .multiply(BigDecimal.valueOf(96).add(BigDecimal.valueOf(96).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(64).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(22).multiply(b.pow(3)).multiply(bd.pow(3)))
                        .add(BigDecimal.valueOf(3).multiply(b.pow(4)).multiply(bd.pow(4))), mc);

        BigDecimal fz = term1.multiply(term2, mc).add(b.multiply(qy).multiply(term3.add(term4).add(term5).add(term6), mc), mc);

        // Calculate the denominator (fm)
        BigDecimal fm = BigDecimal.valueOf(384).multiply(bd.pow(3))
                .multiply(BigDecimal.valueOf(2).add(b.multiply(bd)), mc)
                .multiply(BigDecimal.valueOf(48).add(BigDecimal.valueOf(12).multiply(a).multiply(bd))
                        .add(BigDecimal.valueOf(12).multiply(b).multiply(bd))
                        .add(BigDecimal.valueOf(6).multiply(a.pow(2)).multiply(bd.pow(2)))
                        .add(BigDecimal.valueOf(6).multiply(b.pow(2)).multiply(bd.pow(2)))
                        .add(a.pow(3).multiply(bd.pow(3)))
                        .add(b.pow(3).multiply(bd.pow(3))), mc);

        // Compute the result
        BigDecimal part1 = h.pow(2).multiply(b.pow(2)).multiply(r).divide(BigDecimal.valueOf(48), mc).negate();
        BigDecimal part2 = fz.divide(fm, mc);

        BigDecimal result = (part1.add(part2)).divide(e.multiply(i), mc).multiply(BigDecimal.valueOf(5));

        return result;
    }


    //s公式
//    public boolean computeS(List<Model> models,int index,BigDecimal u){
//        double temp = 0.0;
//        double M = models.get(index).getMh().doubleValue();
//        double U = u.doubleValue();
//        for(int i = 0; i< index -1; i++){
//            temp  = temp + models.get(i).getH().doubleValue()*(models.get(i).getW().doubleValue() - 1);
//        }
//        double a = M - temp - U;
//        return M - temp - U >=0;
//    }

    public boolean computeS(List<GeDataModel> geDataModels, int index, BigDecimal u) {
        BigDecimal temp = BigDecimal.ZERO;
        BigDecimal M = geDataModels.get(index).getMh();
        BigDecimal U = u;

        for (int i = 0; i < index - 1; i++) {
            BigDecimal h = geDataModels.get(i).getH();
            BigDecimal w = geDataModels.get(i).getW();
            temp = temp.add(h.multiply(w.subtract(BigDecimal.ONE)));
        }

        BigDecimal a = M.subtract(temp).subtract(U);
        return a.compareTo(BigDecimal.ZERO) >= 0;
    }


    //计算逻辑
    public List<GeDataModel> compute(List<GeDataModel> geDataModels){
        //初始化数据
        geDataModels = this.initModel(geDataModels);

        //计算逻辑
        for(GeDataModel geDataModel : geDataModels){
            double u = 0.0;
            if(geDataModel.isKeyLayer()){
                double Mi;
                double j = -1;
                boolean ddd = true;
                for(double i = FRACTURE_SEARCH_START;;){
                    if(i< geDataModel.getBi().intValue()){
                        geDataModel.setQx(this.computeQx(geDataModel, BigDecimal.valueOf(i)));
                        geDataModel.setQy(this.computeQy(geDataModel, BigDecimal.valueOf(i)));
                        Mi  = computeMain1(BigDecimal.valueOf(i), geDataModel.getBi(), geDataModel.getBd(), geDataModel.getQx(), geDataModel.getQy()).doubleValue();
                    }else{
                        ddd = false;
                        break;
                    }

                    if(abs(Mi)- geDataModel.getM().doubleValue() <= MOMENT_MATCH_TOLERANCE && abs(Mi)- geDataModel.getM().doubleValue() > 0 ){
                        geDataModel.setQx(this.computeQx(geDataModel, BigDecimal.valueOf(i)));
                        geDataModel.setQy(this.computeQy(geDataModel, BigDecimal.valueOf(i)));
                        j = i;
                        break;
                    }
                    i = i+ FRACTURE_SEARCH_STEP;
                }

                if(!ddd){
                    // 搜索破断步距：i 从 1 起步，步长 0.1，直到 |Mi| 刚好超过极限破断弯矩 M。
                    // 原代码此处没有上界，只有命中才退出。但命中判据要求 |Mi| - M 为正，
                    // 而对某些岩层 |Mi| 永远够不到 M（实测：一份 ax=278 的数据，第 20 层的
                    // |Mi| - M 从 i=1 到 i=100000 始终为负，从 -371 只爬到 -249）——
                    // 退出条件根本不存在，程序就此挂死。故加上界兜底。
                    for(double i = FRACTURE_SEARCH_START; i < FRACTURE_SEARCH_MAX; i = i + FRACTURE_SEARCH_STEP){
                        geDataModel.setQx(this.computeQx(geDataModel, BigDecimal.valueOf(i)));
                        geDataModel.setQy(this.computeQy(geDataModel, BigDecimal.valueOf(i)));
                        Mi = computeMain2(BigDecimal.valueOf(i), geDataModel.getBi(), geDataModel.getBd(), geDataModel.getQx(), geDataModel.getQy()).doubleValue();

                        if(abs(Mi)- geDataModel.getM().doubleValue() <= MOMENT_MATCH_TOLERANCE && abs(Mi)- geDataModel.getM().doubleValue() > 0 ){
                            geDataModel.setQx(this.computeQx(geDataModel, BigDecimal.valueOf(i)));
                            geDataModel.setQy(this.computeQy(geDataModel, BigDecimal.valueOf(i)));
                            j = i;
                            break;
                        }
                    }

                    // 搜到上界仍未命中：该层的弯矩始终达不到极限破断弯矩，即本层不会破断。
                    // 这与下面「j > ai」的处理是同一个结论，故同样判为不破断并结束。
                    if(j < 0){
                        System.out.println("第" + geDataModel.getNum() + "层：搜索至上界 "
                                + FRACTURE_SEARCH_MAX + " 仍未达到极限破断弯矩，判定为不破断。");
                        return markNotCrackFromHere(geDataModels, geDataModel);
                    }

                    u = computeU2(geDataModel,BigDecimal.valueOf(j)).doubleValue();
                    geDataModel.setLastAi(BigDecimal.valueOf(j));
                    if(j > geDataModel.getAi().doubleValue()){
                        return markNotCrackFromHere(geDataModels, geDataModel);
                    }
                    if(!computeS(geDataModels, geDataModel.getNum(),BigDecimal.valueOf(u))){
                        return markNotCrackFromHere(geDataModels, geDataModel);
                    }
                }else{
                    u = computeU(geDataModel,BigDecimal.valueOf(j)).doubleValue();
                    geDataModel.setLastAi(BigDecimal.valueOf(j));
                    if(j > geDataModel.getAi().doubleValue()){
                        return markNotCrackFromHere(geDataModels, geDataModel);
                    }
                    if(!computeS(geDataModels, geDataModel.getNum(),BigDecimal.valueOf(u))){
                        return markNotCrackFromHere(geDataModels, geDataModel);
                    }
                }

            }
        }
        return geDataModels;
    }


    }
