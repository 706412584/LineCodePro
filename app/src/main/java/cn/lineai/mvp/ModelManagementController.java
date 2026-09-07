package cn.lineai.mvp;

import cn.lineai.model.ModelConfig;
import java.util.ArrayList;
import java.util.List;

public final class ModelManagementController {
    interface Host {
        void refreshModelsScreen();

        void returnToModelsScreen();

        void render();
    }

    interface ModelStore {
        List<ModelConfig> getModels();

        ModelConfig getModel(String id);

        String getSelectedModelId();

        void setSelectedModelId(String id);

        ModelConfig save(ModelConfig model);

        void deleteModels(List<String> ids);

        List<ModelConfig> saveGroup(List<ModelConfig> group);

        List<ModelConfig> getModelsInGroup(String groupId);
    }

    private static final class RepositoryModelStore implements ModelStore {
        private final cn.lineai.model.ModelStore modelStore;

        RepositoryModelStore(cn.lineai.model.ModelStore modelStore) {
            this.modelStore = modelStore;
        }

        @Override
        public List<ModelConfig> getModels() {
            return modelStore.getModels();
        }

        @Override
        public ModelConfig getModel(String id) {
            return modelStore.getModel(id);
        }

        @Override
        public String getSelectedModelId() {
            return modelStore.getSelectedModelId();
        }

        @Override
        public void setSelectedModelId(String id) {
            modelStore.setSelectedModelId(id);
        }

        @Override
        public ModelConfig save(ModelConfig model) {
            return modelStore.save(model);
        }

        @Override
        public void deleteModels(List<String> ids) {
            modelStore.deleteModels(ids);
        }

        @Override
        public List<ModelConfig> saveGroup(List<ModelConfig> group) {
            return modelStore.saveGroup(group);
        }

        @Override
        public List<ModelConfig> getModelsInGroup(String groupId) {
            return modelStore.getModelsInGroup(groupId);
        }
    }

    private final ModelStore modelStore;
    private final Host host;

    public ModelManagementController(cn.lineai.model.ModelStore modelStore, Host host) {
        this(new RepositoryModelStore(modelStore), host);
    }

    ModelManagementController(ModelStore modelStore, Host host) {
        this.modelStore = modelStore;
        this.host = host;
    }

    public List<ModelConfig> getModels() {
        return modelStore.getModels();
    }

    public ModelConfig getModel(String id) {
        return modelStore.getModel(id);
    }

    public String getSelectedModelId() {
        return modelStore.getSelectedModelId();
    }

    public void selectModel(String id) {
        modelStore.setSelectedModelId(id);
        host.refreshModelsScreen();
        host.render();
    }

    public void saveModel(ModelConfig model) {
        ModelConfig saved = modelStore.save(model);
        modelStore.setSelectedModelId(saved.getId());
        host.returnToModelsScreen();
        host.render();
    }

    /** 服务商整组保存：选中重指到该组 main 行（cc-haha 激活语义）。 */
    public void saveGroup(List<ModelConfig> group) {
        List<ModelConfig> saved = modelStore.saveGroup(group);
        if (!saved.isEmpty()) {
            modelStore.setSelectedModelId(saved.get(0).getId());
        }
        host.returnToModelsScreen();
        host.render();
    }

    public List<ModelConfig> getModelsInGroup(String groupId) {
        if (groupId != null && groupId.startsWith("solo:")) {
            // 老数据聚合键 solo:<providerLabel>@<baseUrl>：按服务商名+地址过滤，
            // 编辑后保存为正式组（升级语义）
            String[] parts = groupId.substring("solo:".length()).split("@", 2);
            String label = parts.length > 0 ? parts[0] : "";
            String baseUrl = parts.length > 1 ? parts[1] : "";
            List<ModelConfig> result = new ArrayList<>();
            for (ModelConfig model : modelStore.getModels()) {
                if (model.getGroupId().length() == 0
                        && model.getProviderLabel().equals(label)
                        && model.getBaseUrl().equals(baseUrl)) {
                    result.add(model);
                }
            }
            return result;
        }
        return modelStore.getModelsInGroup(groupId);
    }

    public void deleteModels(List<String> ids) {
        modelStore.deleteModels(ids);
        host.refreshModelsScreen();
        host.render();
    }
}
