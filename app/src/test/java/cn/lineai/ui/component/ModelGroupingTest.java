package cn.lineai.ui.component;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/** 模型列表服务商分组逻辑：主模型组置顶、组内保序、无主模型时按原序。 */
public final class ModelGroupingTest {

    private static ModelConfig model(String id, String provider) {
        return new ModelConfig(id, id, ModelProtocolType.OPENAI_COMPATIBLE,
                provider, "https://example.com/v1", "k", "m-" + id,
                200, false, true, "", 0);
    }

    @Test
    public void selectedGroupIsFirst() {
        List<ModelConfig> models = Arrays.asList(
                model("a", "OpenAI"), model("b", "Anthropic"), model("c", "OpenAI"));
        Map<String, List<ModelConfig>> groups = ModelListScreenView.groupModelsByProvider(
                models, "b", ModelConfig::getProviderLabel);
        Assert.assertEquals(Arrays.asList("Anthropic", "OpenAI"),
                new ArrayList<>(groups.keySet()));
        Assert.assertEquals(2, groups.get("OpenAI").size());
        Assert.assertEquals(1, groups.get("Anthropic").size());
    }

    @Test
    public void noSelectionKeepsOriginalOrder() {
        List<ModelConfig> models = Arrays.asList(
                model("a", "OpenAI"), model("b", "Anthropic"), model("c", "OpenAI"));
        Map<String, List<ModelConfig>> groups = ModelListScreenView.groupModelsByProvider(
                models, "", ModelConfig::getProviderLabel);
        Assert.assertEquals(Arrays.asList("OpenAI", "Anthropic"),
                new ArrayList<>(groups.keySet()));
    }

    @Test
    public void groupMembersPreserveRelativeOrder() {
        List<ModelConfig> models = Arrays.asList(
                model("x", "P"), model("a", "P"), model("z", "Q"), model("b", "P"));
        Map<String, List<ModelConfig>> groups = ModelListScreenView.groupModelsByProvider(
                models, "b", ModelConfig::getProviderLabel);
        List<String> pIds = new ArrayList<>();
        for (ModelConfig m : groups.get("P")) {
            pIds.add(m.getId());
        }
        Assert.assertEquals(Arrays.asList("x", "a", "b"), pIds);
        Assert.assertEquals("Q", new ArrayList<>(groups.keySet()).get(1));
    }

    @Test
    public void emptyInputYieldsEmptyGroups() {
        Map<String, List<ModelConfig>> groups = ModelListScreenView.groupModelsByProvider(
                new ArrayList<>(), "", ModelConfig::getProviderLabel);
        Assert.assertTrue(groups.isEmpty());
    }
}
