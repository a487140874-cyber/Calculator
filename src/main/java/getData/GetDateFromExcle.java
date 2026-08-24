package getData;

import com.alibaba.excel.EasyExcel;
import geModel.GeDataModel;
import geModel.GeDataModelExcel;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.List;

//从excle中获取数据
public class GetDateFromExcle {
    public List<GeDataModel> getData(String path)
    {
        System.out.println(System.getProperty("java.class.path"));

        String fileName = path;
        // 检查文件是否存在
        if (fileName == null || fileName.trim().isEmpty()) {
            System.out.println("文件路径不能为空！");
            throw new IllegalArgumentException("文件路径不能为空");
        }
        // 创建监听器实例
        DataListener dataListener = new DataListener();
        List<GeDataModel> geDataModelList = new ArrayList<>();
        try {
            // 读取 Excel 文件
            EasyExcel.read(fileName, GeDataModelExcel.class, dataListener).sheet().doRead();
            // 将 ModelExcel 对象列表转换为 Model 对象列表
            List<GeDataModelExcel> geDataModelExcelList = dataListener.getDataList();
            ModelMapper modelMapper = new ModelMapper();

            for (GeDataModelExcel geDataModelExcel : geDataModelExcelList) {
                GeDataModel geDataModel = modelMapper.map(geDataModelExcel, GeDataModel.class);
                geDataModelList.add(geDataModel);
            }

            // 打印读取的结果
            System.out.println("读取的所有数据: ");
            dataListener.getDataList().forEach(System.out::println);
        } catch (Exception e) {
            throw new IllegalStateException("读取文件失败，请检查路径和文件格式是否正确", e);
        }
        return geDataModelList;

    }
}
