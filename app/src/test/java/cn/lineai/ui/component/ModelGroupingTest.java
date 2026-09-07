package cn.lineai.ui.component;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelGrouping;
import cn.lineai.model.ModelProtocolType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * 服务商分组聚合（cc-haha 语义）：groupId 聚合、solo 独立组、选中组置顶、
 * 槽位去重合并角色标签。
 */
public final class ModelGroupingTest {

    private static ModelConfig model(String id, String modelId, String groupId, String slotRole) {
        return new ModelConfig(id, modelId, ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenAI", "https://example.com/v1", "k", modelId,
                200, false, true, "", 0, groupId, slotRole);
    }

    @Test
    public void groupAggregatesByGroupId() {
        List<ModelConfig> models = Arrays.asList(
                model("a", "m1", "g1", ModelConfig.SLOT_MAIN),
                model("b", "m2", "g1", ModelConfig.SLOT_HAIKU),
                model("c", "m3", "g2", ModelConfig.SLOT_MAIN));
        List<ModelGrouping.ProviderGroup> groups = ModelGrouping.groupForUi(models, "c");
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals("g2", groups.get(0).groupId);   // 选中组置顶
        Assert.assertTrue(groups.get(0).containsSelected);
        Assert.assertEquals(2, groups.get(1).models.size());
        Assert.assertEquals("a", groups.get(1).models.get(0).getId()); // 组内 main 置顶
    }

    @Test
    public void legacyRowsBecomeSoloGroups() {
        List<ModelConfig> models = Arrays.asList(
                model("a", "m1", "", ""),
                model("b", "m2", "", ""));
        List<ModelGrouping.ProviderGroup> groups = ModelGrouping.groupForUi(models, "");
        Assert.assertEquals(2, groups.size()); // 各自独立，不按 providerLabel 合并
        Assert.assertTrue(groups.get(0).groupId.startsWith("solo:"));
    }

    @Test
    public void dedupeMergesSlotRolesForSameModelId() {
        List<ModelConfig> group = Arrays.asList(
                model("a", "same-model", "g1", ModelConfig.SLOT_MAIN),
                model("b", "same-model", "g1", ModelConfig.SLOT_SONNET),
                model("c", "other-model", "g1", ModelConfig.SLOT_HAIKU));
        List<ModelGrouping.GroupedModel> deduped = ModelGrouping.dedupeByModelId(group);
        Assert.assertEquals(2, deduped.size());
        Assert.assertEquals(2, deduped.get(0).slots.size());
        Assert.assertEquals(Arrays.asList(ModelConfig.SLOT_MAIN, ModelConfig.SLOT_SONNET),
                deduped.get(0).getSlotRoles());
        Assert.assertEquals("same-model", deduped.get(0).getModelId());
    }

    @Test
    public void selectedLookupFindsSlotRow() {
        List<ModelConfig> group = Arrays.asList(
                model("a", "m1", "g1", ModelConfig.SLOT_MAIN),
                model("b", "m2", "g1", ModelConfig.SLOT_HAIKU));
        List<ModelGrouping.GroupedModel> deduped = ModelGrouping.dedupeByModelId(group);
        Assert.assertEquals("b", deduped.get(1).findSelected("b").getId());
        Assert.assertNull(deduped.get(0).findSelected("zz"));
    }

    @Test
    public void emptyInputYieldsEmptyGroups() {
        Assert.assertTrue(ModelGrouping.groupForUi(new ArrayList<>(), "").isEmpty());
        Assert.assertTrue(ModelGrouping.dedupeByModelId(new ArrayList<>()).isEmpty());
    }
}
