package cn.lineai.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务商分组的 UI 聚合（cc-haha 语义转译）。
 *
 * <p>一个服务商 = 一组同 {@code groupId} 的槽位行；旧数据（groupId 空）每行自成独立组。
 * 组内 4 槽位（main/haiku/sonnet/opus）去重合并模型 id 并聚合角色标签——
 * 切换器里每个模型一行，描述显示其全部角色（如「主模型 · Sonnet」）。</p>
 */
public final class ModelGrouping {

    /** 一个服务商（或独立单模型）在 UI 上的聚合单元。 */
    public static final class ProviderGroup {
        public final String groupId;
        public final String name;
        public final List<ModelConfig> models;
        /** 组内是否包含当前选中模型（组头「默认」标记）。 */
        public final boolean containsSelected;

        ProviderGroup(String groupId, String name, List<ModelConfig> models, boolean containsSelected) {
            this.groupId = groupId;
            this.name = name;
            this.models = models;
            this.containsSelected = containsSelected;
        }
    }

    /** 切换器行：模型 id 去重后的聚合（角色标签合并）。 */
    public static final class GroupedModel {
        public final ModelConfig primary;
        /** 去重后的全部槽位行（供按 configId 切换）。 */
        public final List<ModelConfig> slots;

        GroupedModel(ModelConfig primary, List<ModelConfig> slots) {
            this.primary = primary;
            this.slots = slots;
        }

        public String getModelId() {
            return primary.getModelId();
        }

        public String getName() {
            return primary.getName().length() == 0 ? primary.getModelId() : primary.getName();
        }

        /** 角色标签序（main/haiku/sonnet/opus）。 */
        public List<String> getSlotRoles() {
            List<String> roles = new ArrayList<>();
            for (ModelConfig slot : slots) {
                String role = slot.getEffectiveSlotRole();
                if (!roles.contains(role)) {
                    roles.add(role);
                }
            }
            return roles;
        }

        /** 该聚合模型内与选中 id 匹配的槽位行（无则 null）。 */
        public ModelConfig findSelected(String selectedId) {
            for (ModelConfig slot : slots) {
                if (slot.getId().equals(selectedId)) {
                    return slot;
                }
            }
            return null;
        }
    }

    private ModelGrouping() {
    }

    /**
     * 聚合为服务商组列表：含选中模型的组置顶，其余按原序。
     * 组名取组内第一行的 providerLabel。
     */
    public static List<ProviderGroup> groupForUi(List<ModelConfig> models, String selectedId) {
        LinkedHashMap<String, List<ModelConfig>> byGroup = new LinkedHashMap<>();
        for (ModelConfig model : models) {
            if (model == null) {
                continue;
            }
            String key = model.getGroupId().length() > 0
                    ? model.getGroupId()
                    // 旧数据独立成组：用行 id 保证唯一
                    : "solo:" + model.getId();
            List<ModelConfig> bucket = byGroup.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                byGroup.put(key, bucket);
            }
            bucket.add(model);
        }
        // 组内 main 置顶
        for (List<ModelConfig> bucket : byGroup.values()) {
            bucket.sort((a, b) -> {
                boolean aMain = ModelConfig.SLOT_MAIN.equals(a.getEffectiveSlotRole());
                boolean bMain = ModelConfig.SLOT_MAIN.equals(b.getEffectiveSlotRole());
                if (aMain != bMain) {
                    return aMain ? -1 : 1;
                }
                return 0;
            });
        }
        List<ProviderGroup> groups = new ArrayList<>();
        String safeSelected = selectedId == null ? "" : selectedId;
        for (Map.Entry<String, List<ModelConfig>> entry : byGroup.entrySet()) {
            List<ModelConfig> bucket = entry.getValue();
            String name = bucket.get(0).getProviderLabel();
            boolean containsSelected = false;
            for (ModelConfig model : bucket) {
                if (model.getId().equals(safeSelected)) {
                    containsSelected = true;
                    break;
                }
            }
            groups.add(new ProviderGroup(entry.getKey(), name, bucket, containsSelected));
        }
        // 选中组置顶，其余保序
        List<ProviderGroup> ordered = new ArrayList<>();
        for (ProviderGroup group : groups) {
            if (group.containsSelected) {
                ordered.add(group);
            }
        }
        for (ProviderGroup group : groups) {
            if (!group.containsSelected) {
                ordered.add(group);
            }
        }
        return ordered;
    }

    /**
     * 组内 4 槽位按 modelId 去重聚合（cc-haha ModelSelector.buildProviderModels 语义）：
     * 同 id 的槽位合并为一个 GroupedModel，角色标签取并集，primary 取第一个槽位。
     */
    public static List<GroupedModel> dedupeByModelId(List<ModelConfig> groupModels) {
        LinkedHashMap<String, List<ModelConfig>> byModelId = new LinkedHashMap<>();
        for (ModelConfig model : groupModels) {
            if (model == null) {
                continue;
            }
            List<ModelConfig> bucket = byModelId.get(model.getModelId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                byModelId.put(model.getModelId(), bucket);
            }
            bucket.add(model);
        }
        List<GroupedModel> result = new ArrayList<>();
        for (List<ModelConfig> bucket : byModelId.values()) {
            result.add(new GroupedModel(bucket.get(0), bucket));
        }
        return result;
    }
}
