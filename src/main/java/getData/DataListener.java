package getData;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import geModel.GeDataModelExcel;

import java.util.ArrayList;
import java.util.List;

//监听器
public class DataListener implements ReadListener<GeDataModelExcel> {
    private List<GeDataModelExcel> dataList = new ArrayList<>();

    @Override
    public void invoke(GeDataModelExcel data, AnalysisContext analysisContext) {
        System.out.println("读取到一行数据: " + data);
        dataList.add(data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        System.out.println("所有数据解析完成!");
    }

    public List<GeDataModelExcel> getDataList() {
        return dataList;
    }
}