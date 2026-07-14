package com.example.calculator;

import geModel.GeDataModel;
import geModel.GeDataModelExcel;
import geModel.GeDataModelMapper;
import getData.GetDateFromExcle;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 导出链路的黄金基线测试。
 *
 * <p>为什么单独测导出：{@link GeDataModelMapper} 靠「字段名相同 <b>且类型相同</b>」
 * 反射拷贝。一旦 {@link GeDataModel} 里某个字段的类型变了（比如 isKeyLayer 从
 * String 改成 boolean），而 {@link GeDataModelExcel} 那侧没跟着改，拷贝就会被
 * <b>静默跳过</b>——导出的 Excel 里那一列直接空掉，编译器不会报任何错，
 * {@link PipelineGoldenTest} 也发现不了（它只看计算结果，不看导出）。
 *
 * <p>本测试就是专门堵这个洞：把导出模型的每个字段快照下来，任何字段悄悄丢值都会失败。
 */
class ExcelExportGoldenTest {

    private static final Path INPUT = Paths.get("src/test/resources/测试用数据.xlsx");
    private static final Path GOLDEN = Paths.get("src/test/resources/golden/export-snapshot.txt");
    private static final int SCALE = 6;

    @Test
    void 导出模型的字段内容应与黄金基线一致() throws IOException {
        Assumptions.assumeTrue(Files.exists(INPUT), "缺少输入数据 " + INPUT + "，跳过");

        List<GeDataModel> layers = new GetDateFromExcle().getData(INPUT.toString());
        assertNotNull(layers);

        layers = new LayerLoadCalculator().findKeyLayers(layers);
        layers = new KeyLayerAnalyzer().compute(layers);
        List<GeDataModel> withEnergy = new EnergyCalculator().calculateEnergy(layers);
        if (withEnergy != null) {
            layers = withEnergy;
        }

        // 走与「Export Results」按钮完全相同的转换路径
        List<GeDataModelExcel> exported = GeDataModelMapper.toExcelList(layers);

        String actual = snapshot(exported);

        if (!Files.exists(GOLDEN)) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual, StandardCharsets.UTF_8);
            throw new AssertionError("已生成导出基线快照：" + GOLDEN + "，请确认后提交。");
        }

        assertEquals(normalize(Files.readString(GOLDEN, StandardCharsets.UTF_8)), normalize(actual),
                "导出内容与黄金基线不一致——某个字段可能因类型不匹配被反射拷贝静默跳过了！");
    }

    /** 快照导出模型的每个 Excel 列，专门盯「字段是否丢值」。 */
    private static String snapshot(List<GeDataModelExcel> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 导出 Excel 内容快照（GeDataModel -> GeDataModelExcel）\n");
        sb.append("# 由 ExcelExportGoldenTest 自动生成\n");
        sb.append("rowCount=").append(rows.size()).append('\n').append('\n');

        for (GeDataModelExcel r : rows) {
            sb.append("[row num=").append(r.getNum()).append("] ").append(r.getName()).append('\n');
            sb.append("  是否为关键层=").append(r.getIsKeyLayer()).append('\n');
            sb.append("  是否垮落=").append(r.getIsNotCrack()).append('\n');
            put(sb, "层厚h", r.getH());
            put(sb, "体积力l", r.getL());
            put(sb, "弹性模量e", r.getE());
            put(sb, "抗拉强度r", r.getR());
            put(sb, "推进距离ax", r.getAx());
            put(sb, "推进距离by", r.getBy());
            put(sb, "破断角af", r.getAf());
            put(sb, "基础刚度k", r.getK());
            put(sb, "岩梁宽度b", r.getB());
            put(sb, "碎涨系数w", r.getW());
            put(sb, "采煤高度mh", r.getMh());
            put(sb, "delta", r.getDta());
            put(sb, "煤层高度ht", r.getHt());
            put(sb, "惯性矩i", r.getI());
            put(sb, "bd", r.getBd());
            put(sb, "DMax1", r.getDMax1());
            put(sb, "DMax2", r.getDMax2());
            put(sb, "M1", r.getM1());
            put(sb, "M2", r.getM2());
            put(sb, "破断步距lastAi", r.getLastAi());
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void put(StringBuilder sb, String name, BigDecimal value) {
        sb.append("  ").append(name).append('=')
          .append(value == null ? "null" : value.setScale(SCALE, RoundingMode.HALF_UP).toPlainString())
          .append('\n');
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").trim();
    }
}
