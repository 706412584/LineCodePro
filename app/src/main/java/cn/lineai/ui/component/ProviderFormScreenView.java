package cn.lineai.ui.component;

import cn.lineai.ui.theme.FlowLayoutView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelGrouping;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelProviderPreset;
import cn.lineai.ui.util.ModelProviderPresetStrings;
import cn.lineai.model.ModelProviderPresets;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务商添加/编辑表单（cc-haha ProviderFormModal 复刻）。
 *
 * <p>一条服务商配置 = 4 个模型槽位（main/haiku/sonnet/opus，main 必填，其余留空取 main 值）。
 * 流程：预设 chips（预填连接+槽位默认值）→ 区域端点（多区域预设）→ 名称/baseUrl/协议/apiKey
 * → 4 槽位输入（可拉模型目录单选填入）→ 测试连通 → 保存整组。</p>
 *
 * <p>编辑模式：预填既有组数据；apiKey 留空沿用旧值。</p>
 */
public final class ProviderFormScreenView extends ScreenScaffoldView {

    public interface Listener {
        void onBack();

        /** 保存整组（1–4 行，同 groupId；新增时 groupId 为空由仓库生成）。 */
        void onSaveGroup(List<ModelConfig> group);

        void onTest(ModelConfig mainSlotConfig);

        List<String> onFetchModelCatalog(ModelProtocolType type, String baseUrl, String apiKey) throws Exception;
    }

    private static final String[] SLOT_ROLES = {
            ModelConfig.SLOT_MAIN, ModelConfig.SLOT_HAIKU, ModelConfig.SLOT_SONNET, ModelConfig.SLOT_OPUS
    };

    private final Listener listener;
    private final List<ModelConfig> editingGroup; // 空 = 新建
    private final ModelProviderPreset initialPreset;

    private ModelProviderPreset selectedPreset;
    private ModelProtocolType protocolType = ModelProtocolType.OPENAI_COMPATIBLE;
    private final EditText nameInput;
    private final EditText baseUrlInput;
    private final EditText apiKeyInput;
    private final LinearLayout regionHost;
    private final EditText[] slotInputs = new EditText[4];
    private final TextView saveAction;
    private final TextView testAction;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<String> fetchedModelIds = new ArrayList<>();
    private boolean fetchingModels;
    private boolean isTesting;

    public ProviderFormScreenView(Context context, ModelProviderPreset preset, List<ModelConfig> editingGroup,
                                  Listener listener) {
        super(context, editingGroup == null || editingGroup.isEmpty()
                        ? context.getString(R.string.screen_model_provider_form_add)
                        : context.getString(R.string.screen_model_provider_form_edit),
                listener::onBack, null);
        this.listener = listener;
        this.editingGroup = editingGroup == null ? new ArrayList<>() : editingGroup;
        boolean editing = !this.editingGroup.isEmpty();
        this.initialPreset = preset == null ? ModelProviderPresets.CUSTOM : preset;

        LinearLayout content = getContent();
        ScrollView scroll = new ScrollView(context);
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(VERTICAL);
        LineTheme.padding(form, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);
        scroll.addView(form, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ===== 预设 chips（仅新建）=====
        if (!editing) {
            form.addView(label(context, context.getString(R.string.screen_model_add_options_section_presets)));
            selectedPreset = initialPreset;
            applyPreset(initialPreset, false);
            form.addView(buildPresetChips(context));
        } else {
            ModelConfig main = mainOf(this.editingGroup);
            selectedPreset = null;
            protocolType = main.getProtocolType();
        }

        // ===== 区域端点（多区域预设）=====
        regionHost = new LinearLayout(context);
        regionHost.setOrientation(VERTICAL);
        form.addView(regionHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        renderRegionEndpoints();

        // ===== 名称 / baseUrl / 协议 =====
        ModelConfig editingMain = editing ? mainOf(this.editingGroup) : null;
        form.addView(label(context, context.getString(R.string.screen_model_add_field_name)));
        nameInput = ModelFormHelper.input(context,
                editing ? editingMain.getName() : initialPreset.getId().equals("custom") ? "" : initialPreset.getId(),
                context.getString(R.string.screen_model_add_hint_remote_name), false, false);
        form.addView(nameInput, fieldParams(context));

        form.addView(label(context, context.getString(R.string.screen_model_add_field_base_url)));
        baseUrlInput = ModelFormHelper.input(context,
                editing ? editingMain.getBaseUrl() : initialPreset.getBaseUrl(),
                initialPreset.getPlaceholder(), false, false);
        form.addView(baseUrlInput, fieldParams(context));

        form.addView(label(context, context.getString(R.string.screen_model_add_field_protocol)));
        LinearLayout protocolRow = buildProtocolSelector(context);
        form.addView(protocolRow, fieldParams(context));

        // ===== apiKey =====
        form.addView(label(context, context.getString(R.string.screen_model_add_field_api_key)));
        String keyHint = editing ? context.getString(R.string.screen_model_provider_key_keep) : "";
        apiKeyInput = ModelFormHelper.input(context, "",
                keyHint.length() == 0 ? context.getString(R.string.screen_model_add_hint_api_key) : keyHint,
                false, false);
        form.addView(apiKeyInput, fieldParams(context));

        // ===== 模型槽位 =====
        LinearLayout slotsHeader = new LinearLayout(context);
        slotsHeader.setOrientation(HORIZONTAL);
        slotsHeader.setGravity(Gravity.CENTER_VERTICAL);
        slotsHeader.addView(label(context, context.getString(R.string.screen_model_provider_slots_title)),
                new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView fetchButton = actionText(context, context.getString(R.string.screen_model_add_query_catalog),
                LineTheme.ACCENT);
        fetchButton.setOnClickListener(v -> fetchModels());
        slotsHeader.addView(fetchButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        form.addView(slotsHeader, fieldParams(context));

        String[] editingSlots = editingSlotValues();
        int[] slotLabels = {
                R.string.model_slot_main, R.string.model_slot_haiku,
                R.string.model_slot_sonnet, R.string.model_slot_opus
        };
        for (int i = 0; i < 4; i++) {
            form.addView(label(context, context.getString(slotLabels[i]) + (i == 0 ? " *" : "")));
            LinearLayout slotRow = new LinearLayout(context);
            slotRow.setOrientation(HORIZONTAL);
            slotRow.setGravity(Gravity.CENTER_VERTICAL);
            slotInputs[i] = ModelFormHelper.input(context, editingSlots[i],
                    i == 0 ? context.getString(R.string.screen_model_add_hint_model_id)
                            : context.getString(R.string.screen_model_provider_slot_hint_same),
                    false, false);
            slotRow.addView(slotInputs[i], new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            if (i > 0) {
                TextView pick = actionText(context, context.getString(R.string.screen_model_provider_pick), LineTheme.TEXT_SECONDARY);
                final int slotIndex = i;
                pick.setOnClickListener(v -> pickModelForSlot(slotIndex));
                LinearLayout.LayoutParams pickParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                pickParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
                slotRow.addView(pick, pickParams);
            }
            form.addView(slotRow, fieldParams(context));
        }

        // ===== 底部操作 =====
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        testAction = actionText(context, context.getString(R.string.screen_model_add_test_button), LineTheme.TEXT_SECONDARY);
        testAction.setOnClickListener(v -> runTest());
        footer.addView(testAction, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        saveAction = actionText(context,
                editing ? context.getString(R.string.common_save) : context.getString(R.string.common_add),
                LineTheme.ACCENT);
        LinearLayout.LayoutParams saveParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        saveParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        saveAction.setOnClickListener(v -> save());
        footer.addView(saveAction, saveParams);
        LinearLayout.LayoutParams footerParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = LineTheme.dp(context, LineTheme.LG);
        form.addView(footer, footerParams);

        if (editing) {
            prefillFromGroup();
        }
        updateSaveState();
    }

    // ===== 预设 =====

    private View buildPresetChips(Context context) {
        FlowLayoutView chips = new FlowLayoutView(context);
        for (ModelProviderPreset preset : ModelProviderPresets.all()) {
            TextView chip = LineTheme.text(context,
                    ModelProviderPresetStrings.getLabel(context, preset.getId()),
                    LineTheme.FONT_XS,
                    preset.getId().equals(selectedPreset == null ? "" : selectedPreset.getId())
                            ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY,
                    android.graphics.Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setClickable(true);
            boolean active = preset.getId().equals(selectedPreset == null ? "" : selectedPreset.getId());
            chip.setBackground(LineTheme.rounded(context,
                    active ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 12));
            LineTheme.padding(chip, LineTheme.MD, 6, LineTheme.MD, 6);
            chip.setOnClickListener(v -> {
                selectedPreset = preset;
                applyPreset(preset, true);
                // 重建 chips 高亮
                LinearLayout parent = (LinearLayout) chips.getParent();
                int index = parent.indexOfChild(chips);
                parent.removeView(chips);
                parent.addView(buildPresetChips(context), index);
            });
            chips.addView(chip, new android.view.ViewGroup.MarginLayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        return chips;
    }

    /** 预设切换：预填 baseUrl/协议/槽位默认值（cc-haha handlePresetChange 语义）。 */
    private void applyPreset(ModelProviderPreset preset, boolean overwriteInputs) {
        protocolType = preset.getProtocolType();
        if (overwriteInputs) {
            baseUrlInput.setText(preset.getBaseUrl());
            String[] defaults = preset.getDefaultModels();
            for (int i = 0; i < 4; i++) {
                slotInputs[i].setText(strip1mMarker(defaults[i]));
            }
            fetchedModelIds.clear();
        } else if (!preset.getBaseUrl().isEmpty()) {
            // 构造函数早期调用：inputs 尚未创建，仅记录协议
        }
    }

    private void renderRegionEndpoints() {
        regionHost.removeAllViews();
        ModelProviderPreset preset = selectedPreset != null ? selectedPreset : initialPreset;
        String[][] regions = preset.getRegionalEndpoints();
        if (regions == null || regions.length < 2 || !editingGroup.isEmpty()) {
            return;
        }
        Context context = regionHost.getContext();
        regionHost.addView(label(context, context.getString(R.string.screen_model_provider_region)));
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 8));
        LineTheme.padding(row, 3, 3, 3, 3);
        for (final String[] region : regions) {
            TextView button = LineTheme.text(context, region[0], LineTheme.FONT_SM,
                    baseUrlInput != null && region[1].equals(baseUrlInput.getText().toString())
                            ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY,
                    android.graphics.Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setClickable(true);
            boolean active = baseUrlInput != null && region[1].equals(baseUrlInput.getText().toString());
            button.setBackground(LineTheme.rounded(context, active ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 8));
            button.setOnClickListener(v -> {
                baseUrlInput.setText(region[1]);
                renderRegionEndpoints();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LineTheme.dp(context, 36), 1f);
            row.addView(button, params);
        }
        LinearLayout.LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        regionHost.addView(row, rowParams);
        regionHost.requestLayout();
    }

    private LinearLayout buildProtocolSelector(Context context) {
        LinearLayout segment = new LinearLayout(context);
        segment.setOrientation(HORIZONTAL);
        segment.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 8));
        LineTheme.padding(segment, 3, 3, 3, 3);
        ModelProtocolType[] types = {
                ModelProtocolType.OPENAI_COMPATIBLE,
                ModelProtocolType.ANTHROPIC_MESSAGES,
                ModelProtocolType.CODEX_RESPONSES
        };
        for (final ModelProtocolType type : types) {
            TextView button = LineTheme.text(context, type.getLabel(), LineTheme.FONT_SM,
                    type == protocolType ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY,
                    android.graphics.Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setClickable(true);
            button.setBackground(LineTheme.rounded(context,
                    type == protocolType ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 8));
            button.setOnClickListener(v -> {
                protocolType = type;
                Context ctx = segment.getContext();
                int count = segment.getChildCount();
                for (int i = 0; i < count; i++) {
                    View child = segment.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView tv = (TextView) child;
                        boolean active = tv.getText().toString().equals(type.getLabel());
                        tv.setTextColor(active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
                        tv.setBackground(LineTheme.rounded(ctx, active ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 8));
                    }
                }
            });
            segment.addView(button, new LinearLayout.LayoutParams(0, LineTheme.dp(context, 36), 1f));
        }
        return segment;
    }

    // ===== 模型目录 =====

    private void fetchModels() {
        final String baseUrl = valueOf(baseUrlInput);
        final String apiKey = resolveApiKey();
        if (baseUrl.trim().length() == 0) {
            toast(R.string.screen_model_add_require_base_url);
            return;
        }
        if (fetchingModels) {
            return;
        }
        fetchingModels = true;
        new Thread(() -> {
            try {
                final List<String> ids = listener.onFetchModelCatalog(protocolType, baseUrl, apiKey);
                mainHandler.post(() -> {
                    fetchingModels = false;
                    fetchedModelIds.clear();
                    if (ids != null) {
                        fetchedModelIds.addAll(ids);
                    }
                    if (fetchedModelIds.isEmpty()) {
                        toast(R.string.screen_model_add_fetch_failed);
                    } else {
                        toast(R.string.screen_model_add_fetch_ok);
                    }
                });
            } catch (final Exception e) {
                mainHandler.post(() -> {
                    fetchingModels = false;
                    Toast.makeText(getContext(), e.getMessage() == null ? "query failed" : e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "linecode-provider-catalog").start();
    }

    /** 目录单选填入指定槽位。 */
    private void pickModelForSlot(int slotIndex) {
        if (fetchedModelIds.isEmpty()) {
            fetchModels();
            return;
        }
        ModelPickerDialog.show(getContext(), fetchedModelIds, valueOf(slotInputs[slotIndex]),
                (modelId, custom) -> slotInputs[slotIndex].setText(custom ? "" : modelId));
    }

    // ===== 测试 / 保存 =====

    private void runTest() {
        if (isTesting) {
            return;
        }
        ModelConfig mainConfig = buildMainForTest();
        if (mainConfig == null) {
            return;
        }
        isTesting = true;
        toast(R.string.screen_model_add_test_started);
        listener.onTest(mainConfig);
        isTesting = false;
    }

    private void save() {
        List<ModelConfig> group = buildGroupConfigs();
        if (group == null) {
            return;
        }
        listener.onSaveGroup(group);
    }

    /**
     * 构造整组 1–4 行。main 必填；haiku/sonnet/opus 留空回退 main 值；
     * 相同 modelId 的槽位仍各占一行（组内去重由 UI 层展示时处理）。
     * 编辑模式：groupId/各行 id 沿用旧组；apiKey 空则沿用旧值。
     */
    List<ModelConfig> buildGroupConfigs() {
        Context context = getContext();
        String name = valueOf(nameInput).trim();
        String baseUrl = valueOf(baseUrlInput).trim();
        String apiKey = resolveApiKey();
        String mainModel = valueOf(slotInputs[0]).trim();
        if (name.length() == 0) {
            toast(R.string.screen_model_add_require_name_id);
            return null;
        }
        if (baseUrl.length() == 0) {
            toast(R.string.screen_model_add_require_base_url);
            return null;
        }
        if (mainModel.length() == 0) {
            toast(R.string.screen_model_add_require_model_id);
            return null;
        }
        if (apiKey.length() == 0) {
            toast(R.string.screen_model_add_require_api_key);
            return null;
        }
        boolean editing = !editingGroup.isEmpty();
        String groupId = editing ? editingGroup.get(0).getGroupId() : "";
        ModelConfig oldMain = editing ? mainOf(editingGroup) : null;
        int toolCallLimit = editing && oldMain != null ? oldMain.getToolCallLimit() : ModelConfig.DEFAULT_TOOL_CALL_LIMIT;
        boolean compressionEnabled = editing && oldMain != null && oldMain.isCompressionModelEnabled();
        boolean compressionAuto = editing ? (oldMain != null && oldMain.isCompressionModelAuto()) : ModelConfig.DEFAULT_COMPRESSION_MODEL_AUTO;
        String compressionModelId = editing && oldMain != null ? oldMain.getCompressionModelId() : "";

        List<ModelConfig> group = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String modelId = valueOf(slotInputs[i]).trim();
            if (modelId.length() == 0 && i == 0) {
                continue; // 已校验
            }
            if (modelId.length() == 0) {
                modelId = mainModel; // 留空回退 main
            }
            String slotRole = SLOT_ROLES[i];
            String rowId = editing ? findSlotRowId(slotRole) : "";
            group.add(new ModelConfig(
                    rowId, modelId, protocolType, name, baseUrl, apiKey, modelId,
                    toolCallLimit, compressionEnabled, compressionAuto, compressionModelId,
                    contextSizeForSlot(i),
                    groupId, slotRole
            ));
        }
        if (group.isEmpty()) {
            return null;
        }
        return group;
    }

    private ModelConfig buildMainForTest() {
        List<ModelConfig> group = buildGroupConfigs();
        return group == null || group.isEmpty() ? null : group.get(0);
    }

    // ===== 辅助 =====

    private void prefillFromGroup() {
        ModelConfig main = mainOf(editingGroup);
        for (int i = 0; i < 4; i++) {
            ModelConfig slot = findSlot(editingGroup, SLOT_ROLES[i]);
            slotInputs[i].setText(slot == null ? "" : slot.getModelId());
        }
        nameInput.setText(main.getProviderLabel());
        baseUrlInput.setText(main.getBaseUrl());
        renderRegionEndpoints();
    }

    private String[] editingSlotValues() {
        if (editingGroup.isEmpty()) {
            String[] defaults = initialPreset.getDefaultModels();
            return new String[] {
                    strip1mMarker(defaults[0]), strip1mMarker(defaults[1]),
                    strip1mMarker(defaults[2]), strip1mMarker(defaults[3])
            };
        }
        String[] values = new String[4];
        for (int i = 0; i < 4; i++) {
            ModelConfig slot = findSlot(editingGroup, SLOT_ROLES[i]);
            values[i] = slot == null ? "" : slot.getModelId();
        }
        return values;
    }

    private int contextSizeForSlot(int slotIndex) {
        ModelProviderPreset preset = selectedPreset != null ? selectedPreset : initialPreset;
        if (!editingGroup.isEmpty()) {
            ModelConfig slot = findSlot(editingGroup, SLOT_ROLES[slotIndex]);
            if (slot != null) {
                return slot.getContextSize();
            }
        }
        String raw = preset.getDefaultModels()[slotIndex];
        return raw != null && raw.endsWith("[1m]") ? 1000000 : ModelConfig.CONTEXT_SIZE_UNSET;
    }

    /** 编辑时解析 [1m]：槽位输入只显示纯 model id。 */
    private static String strip1mMarker(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("[1m]") ? value.substring(0, value.length() - 4) : value;
    }

    private String findSlotRowId(String slotRole) {
        ModelConfig slot = findSlot(editingGroup, slotRole);
        return slot == null ? "" : slot.getId();
    }

    private static ModelConfig findSlot(List<ModelConfig> group, String slotRole) {
        for (ModelConfig model : group) {
            if (slotRole.equals(model.getEffectiveSlotRole())) {
                return model;
            }
        }
        return null;
    }

    private static ModelConfig mainOf(List<ModelConfig> group) {
        ModelConfig main = findSlot(group, ModelConfig.SLOT_MAIN);
        return main != null ? main : group.get(0);
    }

    /** apiKey 解析：编辑模式留空 = 沿用旧组 main 行的 key。 */
    private String resolveApiKey() {
        String value = valueOf(apiKeyInput).trim();
        if (value.length() > 0 || editingGroup.isEmpty()) {
            return value;
        }
        return mainOf(editingGroup).getApiKey();
    }

    private void updateSaveState() {
        saveAction.setTextColor(canSubmit() ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY);
    }

    private boolean canSubmit() {
        return valueOf(nameInput).trim().length() > 0
                && valueOf(baseUrlInput).trim().length() > 0
                && valueOf(slotInputs[0]).trim().length() > 0
                && (valueOf(apiKeyInput).trim().length() > 0 || !editingGroup.isEmpty());
    }

    private static String valueOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    // ===== 测试钩子（包级可见） =====

    void setFieldForTest(String field, String value) {
        String safe = value == null ? "" : value;
        switch (field) {
            case "name": nameInput.setText(safe); break;
            case "baseUrl": baseUrlInput.setText(safe); break;
            case "apiKey": apiKeyInput.setText(safe); break;
            case "main": slotInputs[0].setText(safe); break;
            case "haiku": slotInputs[1].setText(safe); break;
            case "sonnet": slotInputs[2].setText(safe); break;
            case "opus": slotInputs[3].setText(safe); break;
            default: break;
        }
    }

    private void toast(int resId) {
        Toast.makeText(getContext(), resId, Toast.LENGTH_SHORT).show();
    }

    private TextView actionText(Context context, String label, int color) {
        TextView view = LineTheme.textMedium(context, label, LineTheme.FONT_MD, color);
        LineTheme.padding(view, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        view.setClickable(true);
        return view;
    }

    private TextView label(Context context, String text) {
        TextView view = LineTheme.textMedium(context, text, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
        LinearLayout.LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayout.LayoutParams fieldParams(Context context) {
        LinearLayout.LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.SM);
        params.bottomMargin = LineTheme.dp(context, LineTheme.XS);
        return params;
    }
}
