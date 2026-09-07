package cn.lineai.model;

import java.util.List;

/**
 * 模型仓储接口，定义 ModelRepository 的公开契约。
 */
public interface ModelStore {
    List<ModelConfig> getModels();

    ModelConfig save(ModelConfig model);

    ModelConfig getModel(String id);

    void deleteModels(List<String> ids);

    void setSelectedModelId(String id);

    String getSelectedModelId();

    ModelConfig getSelectedModel();

    void clearAll();

    /** 整组保存服务商槽位（实现无组能力时退化为逐行 save）。 */
    default List<ModelConfig> saveGroup(List<ModelConfig> group) {
        List<ModelConfig> saved = new java.util.ArrayList<>();
        if (group != null) {
            for (ModelConfig model : group) {
                saved.add(save(model));
            }
        }
        return saved;
    }

    /** 读取指定组的槽位行；实现无组概念时返回空。 */
    default List<ModelConfig> getModelsInGroup(String groupId) {
        return new java.util.ArrayList<>();
    }
}
