package cn.lineai.mvp;

import cn.lineai.model.ModelConfig;
import java.util.List;

public interface ModelController {
    List<ModelConfig> getModels();

    ModelConfig getModel(String id);

    String getSelectedModelId();

    void onModelSelected(String id);

    void onModelSaved(ModelConfig model);

    /** 服务商整组保存（4 槽位表单）。 */
    void onProviderGroupSaved(List<ModelConfig> group);

    /** 读取指定服务商组的槽位行。 */
    List<ModelConfig> getModelsInGroup(String groupId);

    void onModelTest(ModelConfig model);

    void onModelsDeleted(List<String> ids);

    void onModelQuickSwitch(String modelId);

    void showModelManagement();
}
