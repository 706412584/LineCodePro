package cn.lineai.mvp;

import cn.lineai.ipc.IpcProviderConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public final class IpcProviderControllerBindPolicyTest {

    private static IpcProviderConfig config(String id, boolean enabled, long updatedAt) {
        return IpcProviderConfig.builder()
                .id(id)
                .enabled(enabled)
                .providerType("terminal")
                .name(id)
                .packageName("pkg")
                .serviceClass("svc")
                .updatedAt(updatedAt)
                .build();
    }

    @Test
    public void emptyListBindsNothing() {
        Assert.assertNull(IpcProviderController.resolveProviderToBind(Collections.emptyList()));
    }

    @Test
    public void nullListBindsNothing() {
        Assert.assertNull(IpcProviderController.resolveProviderToBind(null));
    }

    @Test
    public void firstEnabledWinsFollowingUpdatedDescOrder() {
        IpcProviderConfig builtIn = config(IpcProviderConfig.BUILT_IN_ID, true, 1000);
        IpcProviderConfig external = config("ipc_external", true, 2000);
        List<IpcProviderConfig> providers = new ArrayList<>(Arrays.asList(external, builtIn));
        Assert.assertSame(external, IpcProviderController.resolveProviderToBind(providers));
    }

    @Test
    public void skipsDisabledAndBindsNextEnabled() {
        IpcProviderConfig disabled = config("ipc_external", false, 2000);
        IpcProviderConfig builtIn = config(IpcProviderConfig.BUILT_IN_ID, true, 1000);
        Assert.assertSame(builtIn,
                IpcProviderController.resolveProviderToBind(Arrays.asList(disabled, builtIn)));
    }

    @Test
    public void allDisabledBindsNothing() {
        List<IpcProviderConfig> providers = Arrays.asList(
                config(IpcProviderConfig.BUILT_IN_ID, false, 1000),
                config("ipc_external", false, 2000));
        Assert.assertNull(IpcProviderController.resolveProviderToBind(providers));
    }

    @Test
    public void nullEntriesAreIgnored() {
        List<IpcProviderConfig> providers = Arrays.asList(
                null,
                config(IpcProviderConfig.BUILT_IN_ID, true, 1000));
        Assert.assertNotNull(IpcProviderController.resolveProviderToBind(providers));
    }

    @Test
    public void builtInFlagMatchesFixedId() {
        Assert.assertTrue(config(IpcProviderConfig.BUILT_IN_ID, true, 1).isBuiltIn());
        Assert.assertFalse(config("ipc_external", true, 1).isBuiltIn());
    }
}
