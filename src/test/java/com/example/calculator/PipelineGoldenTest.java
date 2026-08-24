package com.example.calculator;

import geModel.GeDataModel;
import getData.GetDateFromExcle;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 黄金基线测试（Golden Master / 特征化测试）。
 *
 * <p>目的不是验证公式“算得对”，而是锁住公式“算出来的值没变”。重构期间，
 * 任何一处计算结果的改动都会让本测试失败——这正是我们需要的安全网。
 *
 * <p>工作方式：
 * <ol>
 *   <li>读取 {@code src/test/resources/测试用数据.xlsx}</li>
 *   <li>依次跑完整条链路：关键层 → 破断 → 能量</li>
 *   <li>把每层的所有关键中间量序列化成文本快照</li>
 *   <li>与 {@code src/test/resources/golden/pipeline-snapshot.txt} 比对</li>
 * </ol>
 *
 * <p>首次运行时若快照文件不存在，会自动生成它并让测试失败一次，提示你人工确认
 * 内容合理后再提交——之后它就成为不可变的对照基准。
 *
 * <p>若输入数据文件不存在，测试会被跳过（而非失败），以免阻塞构建。
 */
class PipelineGoldenTest {

    /** 快照中数值的保留位数：足以捕捉真实的计算变化，又能容忍浮点末位噪音。 */
    private static final int SCALE = 6;

    /**
     * 三份数据覆盖同一套通用算法在不同推进距离下的表现：
     * <ul>
     *   <li>{@code 测试用数据.xlsx}（ax=220）：正常路径</li>
     *   <li>{@code 测试用数据-大推进距离.xlsx}（ax=300）：验证大推进距离仍使用通用逻辑，
     *       不改写 by，也不绑定固定层号。由真实数据改 ax 一列而来。</li>
     *   <li>{@code 测试用数据-死循环回归.xlsx}（ax=278）：<b>加上界之前，这份数据会让程序永久挂死</b>——
     *       第 20 层的 |Mi| 永远达不到极限破断弯矩 M，而搜索循环当时没有上界。
     *       此用例确保它不会再挂，并锁住「搜到上界仍未命中 → 判为不破断」的处理结果。
     *       由真实数据改 ax 一列而来。</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "测试用数据.xlsx,            pipeline-snapshot.txt",
            "测试用数据-大推进距离.xlsx,   pipeline-snapshot-large-ax.txt",
            "测试用数据-死循环回归.xlsx,   pipeline-snapshot-infinite-loop-regression.txt"
    })
    @Timeout(value = 5, unit = TimeUnit.MINUTES) // 防止未来修改意外重新引入无界搜索
    void 完整计算链路的输出应与黄金基线一致(String inputName, String goldenName) throws IOException {
        Path INPUT = Paths.get("src/test/resources").resolve(inputName.trim());
        Path GOLDEN = Paths.get("src/test/resources/golden").resolve(goldenName.trim());

        Assumptions.assumeTrue(Files.exists(INPUT),
                "缺少输入数据 " + INPUT + "，跳过基线测试。请放入岩层数据后重新运行。");

        List<GeDataModel> layers = new GetDateFromExcle().getData(INPUT.toString());
        assertNotNull(layers, "读取 Excel 失败");
        Assumptions.assumeFalse(layers.isEmpty(), "输入数据为空");

        // 步骤 1：关键层
        layers = new LayerLoadCalculator().findKeyLayers(layers);
        // 步骤 2：是否破断/垮落
        layers = new KeyLayerAnalyzer().compute(layers);
        // 步骤 3：能量
        List<GeDataModel> withEnergy = new EnergyCalculator().calculateEnergy(layers);
        // computeMain 在找不到 isNotCrack 层时会返回 null，此处如实记录该行为
        if (withEnergy != null) {
            layers = withEnergy;
        }

        String actual = snapshot(layers, withEnergy == null);

        if (!Files.exists(GOLDEN)) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual, StandardCharsets.UTF_8);
            throw new AssertionError(
                    "已生成黄金基线快照：" + GOLDEN + "\n"
                    + "请人工确认其中的数值合理，然后 git commit。之后本测试即以它为准。");
        }

        String expected = Files.readString(GOLDEN, StandardCharsets.UTF_8);
        assertEquals(normalize(expected), normalize(actual),
                "计算结果与黄金基线不一致——重构改变了数值输出！\n"
                + "若这是预期内的修正，请核对无误后删除 " + GOLDEN + " 并重新运行以重建基线。");
    }

    /** 把整条链路的产物序列化成稳定、可 diff 的文本。 */
    private static String snapshot(List<GeDataModel> layers, boolean energyReturnedNull) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 岩层计算链路快照（关键层 → 破断 → 能量）\n");
        sb.append("# 数值保留 ").append(SCALE).append(" 位小数；此文件由 PipelineGoldenTest 自动生成\n");
        sb.append("layerCount=").append(layers.size()).append('\n');
        sb.append("energyComputeMainReturnedNull=").append(energyReturnedNull).append('\n');
        sb.append('\n');

        for (GeDataModel m : layers) {
            sb.append("[layer ").append(m.getNum()).append("] ").append(m.getName()).append('\n');
            // 输入量（确认输入解析本身没变）
            put(sb, "h", m.getH());
            put(sb, "l", m.getL());
            put(sb, "e", m.getE());
            put(sb, "r", m.getR());
            put(sb, "ax", m.getAx());
            put(sb, "by", m.getBy());
            put(sb, "af", m.getAf());
            put(sb, "k", m.getK());
            put(sb, "b", m.getB());
            put(sb, "w", m.getW());
            put(sb, "mh", m.getMh());
            // 判定结果。
            // 这两个字段已从 String 改为 boolean，但此处刻意沿用改造前的表示法
            // （true -> "true"，false -> "null"），好让快照与改造前逐字节一致——
            // 这样基线文件本身就成了「boolean 转换未改变任何行为」的证明。
            sb.append("  isKeyLayer=").append(m.isKeyLayer() ? "true" : "null").append('\n');
            sb.append("  isNotCrack=").append(m.isNotCrack() ? "true" : "null").append('\n');
            // 计算中间量
            put(sb, "i", m.getI());
            put(sb, "ai", m.getAi());
            put(sb, "bi", m.getBi());
            put(sb, "m", m.getM());
            put(sb, "q", m.getQ());
            put(sb, "bd", m.getBd());
            put(sb, "qx", m.getQx());
            put(sb, "qy", m.getQy());
            put(sb, "M1", m.getM1());
            put(sb, "M2", m.getM2());
            put(sb, "RR", m.getRR());
            put(sb, "Rt", m.getRt());
            put(sb, "Mx0", m.getMx0());
            put(sb, "Mxz", m.getMxz());
            put(sb, "Qx0", m.getQx0());
            put(sb, "My0", m.getMy0());
            put(sb, "Myz", m.getMyz());
            put(sb, "Qy0", m.getQy0());
            // 最终结果
            put(sb, "lastAi", m.getLastAi());
            put(sb, "shi", m.getShi());
            put(sb, "uix", m.getUix());
            put(sb, "power", m.getPower());
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void put(StringBuilder sb, String name, BigDecimal value) {
        sb.append("  ").append(name).append('=');
        sb.append(value == null ? "null" : value.setScale(SCALE, RoundingMode.HALF_UP).toPlainString());
        sb.append('\n');
    }

    /** 抹平换行差异，避免 Windows/Unix 行尾导致假失败。 */
    private static String normalize(String s) {
        return s.replace("\r\n", "\n").trim();
    }
}
