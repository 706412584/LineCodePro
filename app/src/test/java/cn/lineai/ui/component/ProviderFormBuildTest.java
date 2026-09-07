package cn.lineai.ui.component;

import android.content.Context;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * ProviderFormScreenView 的组构造逻辑：槽位留空回退 main、必填校验。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public final class ProviderFormBuildTest {

    private ProviderFormScreenView newForm() {
        Context context = RuntimeEnvironment.getApplication();
        return new ProviderFormScreenView(context, null, null, new ProviderFormScreenView.Listener() {
            @Override
            public void onBack() { }

            @Override
            public void onSaveGroup(List<ModelConfig> group) { }

            @Override
            public void onTest(ModelConfig mainSlotConfig) { }

            @Override
            public List<String> onFetchModelCatalog(ModelProtocolType type, String baseUrl, String apiKey) {
                return Arrays.asList("m1", "m2");
            }
        });
    }

    private void fill(ProviderFormScreenView view, String name, String baseUrl, String key,
                      String main, String haiku, String sonnet, String opus) {
        view.setFieldForTest("name", name);
        view.setFieldForTest("baseUrl", baseUrl);
        view.setFieldForTest("apiKey", key);
        view.setFieldForTest("main", main);
        view.setFieldForTest("haiku", haiku);
        view.setFieldForTest("sonnet", sonnet);
        view.setFieldForTest("opus", opus);
    }

    @Test
    public void emptySlotsFallBackToMainModel() {
        ProviderFormScreenView view = newForm();
        fill(view, "test", "https://api.example.com/v1", "sk-k", "glm-5", "", "", "");
        List<ModelConfig> group = view.buildGroupConfigs();
        Assert.assertNotNull(group);
        Assert.assertEquals(4, group.size());
        for (ModelConfig config : group) {
            Assert.assertEquals("glm-5", config.getModelId());
        }
        Assert.assertEquals(ModelConfig.SLOT_MAIN, group.get(0).getEffectiveSlotRole());
        Assert.assertEquals(ModelConfig.SLOT_HAIKU, group.get(1).getEffectiveSlotRole());
        Assert.assertEquals(ModelConfig.SLOT_OPUS, group.get(3).getEffectiveSlotRole());
    }

    @Test
    public void distinctSlotsAreKept() {
        ProviderFormScreenView view = newForm();
        fill(view, "test", "https://api.example.com/v1", "sk-k", "m-main", "m-haiku", "", "m-opus");
        List<ModelConfig> group = view.buildGroupConfigs();
        Assert.assertEquals(4, group.size());
        Assert.assertEquals("m-main", group.get(0).getModelId());
        Assert.assertEquals("m-haiku", group.get(1).getModelId());
        Assert.assertEquals("m-main", group.get(2).getModelId()); // sonnet 空 → 回退 main
        Assert.assertEquals("m-opus", group.get(3).getModelId());
    }

    @Test
    public void missingMainModelFailsValidation() {
        ProviderFormScreenView view = newForm();
        fill(view, "test", "https://api.example.com/v1", "sk-k", "", "", "", "");
        Assert.assertNull(view.buildGroupConfigs());
    }

    @Test
    public void missingApiKeyFailsValidation() {
        ProviderFormScreenView view = newForm();
        fill(view, "test", "https://api.example.com/v1", "", "m", "", "", "");
        Assert.assertNull(view.buildGroupConfigs());
    }
}
