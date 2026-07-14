package geModel;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 领域模型 {@link GeDataModel} 与 Excel 导出模型 {@link GeDataModelExcel} 之间的转换。
 *
 * <p>原先这段逻辑内联在 ui.MainController 里，导致它无法被测试覆盖——而它恰恰是
 * 最需要覆盖的地方：它靠「字段名相同 + 类型相同」来反射拷贝，一旦某个字段的类型
 * 变了，拷贝会被静默跳过、导出列直接空掉，且编译器不会有任何提示。
 */
public class GeDataModelMapper {

    public static List<GeDataModelExcel> toExcelList(List<GeDataModel> sourceList) {
        List<GeDataModelExcel> targetList = new ArrayList<>();
        for (GeDataModel source : sourceList) {
            targetList.add(toExcel(source));
        }
        return targetList;
    }

    public static GeDataModelExcel toExcel(GeDataModel source) {
        GeDataModelExcel target = new GeDataModelExcel();
        for (Field sourceField : GeDataModel.class.getDeclaredFields()) {
            sourceField.setAccessible(true);
            try {
                Field targetField = GeDataModelExcel.class.getDeclaredField(sourceField.getName());
                targetField.setAccessible(true);
                if (targetField.getType().equals(sourceField.getType())) {
                    targetField.set(target, sourceField.get(source));
                }
            } catch (NoSuchFieldException e) {
                // 目标模型没有这个字段，忽略（GeDataModel 中有不少纯计算中间量不导出）
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("拷贝字段失败: " + sourceField.getName(), e);
            }
        }
        return target;
    }
}
