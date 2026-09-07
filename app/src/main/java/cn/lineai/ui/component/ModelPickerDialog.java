package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.R;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class ModelPickerDialog {

    public interface OnModelSelectedListener {
        void onModelSelected(String modelId, boolean custom);
    }

    /** 多选回调：返回选中的模型 id 列表（可能为空）。 */
    public interface OnModelsMultiSelectedListener {
        void onModelsSelected(java.util.List<String> modelIds);
    }

    private ModelPickerDialog() {
    }

    /**
     * 多选模型目录：每行带勾选框，底部「确定」返回全部选中项。
     * 用于同一 baseURL/key 下批量创建多个模型配置。
     */
    public static void showMulti(Context context, List<String> modelIds, String selectedId,
                                 OnModelsMultiSelectedListener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.roundedTop(context, LineTheme.SURFACE_ELEVATED, 16));

        TextView title = LineTheme.text(context, context.getString(R.string.screen_model_add_picker_title),
                LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        LineTheme.padding(title, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        panel.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new cn.lineai.ui.theme.BoundedScrollView(context, 420);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        java.util.Set<String> chosen = new java.util.LinkedHashSet<>();
        for (String id : modelIds) {
            addMultiRow(list, id, id.equals(selectedId), chosen);
        }

        TextView confirm = LineTheme.text(context, context.getString(R.string.common_confirm),
                LineTheme.FONT_MD, LineTheme.TEXT_ON_COLOR, Typeface.BOLD);
        confirm.setGravity(Gravity.CENTER);
        confirm.setClickable(true);
        confirm.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 8));
        LineTheme.padding(confirm, LineTheme.LG, 12, LineTheme.LG, 12);
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onModelsSelected(new java.util.ArrayList<>(chosen));
            }
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        confirmParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        confirmParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        confirmParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        panel.addView(confirm, confirmParams);
        panel.setPadding(0, 0, 0, LineTheme.dp(context, 8));

        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(DialogDimensions.insetDialogWidth(context), LinearLayout.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    /** 多选行：整行点击切换勾选。 */
    private static void addMultiRow(LinearLayout list, String label, boolean initiallySelected, java.util.Set<String> chosen) {
        Context context = list.getContext();
        if (initiallySelected) {
            chosen.add(label);
        }
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        LineTheme.padding(row, LineTheme.LG, 14, LineTheme.LG, 14);
        TextView text = LineTheme.text(context, label, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.CheckBox check = new android.widget.CheckBox(context);
        check.setChecked(initiallySelected);
        check.setClickable(false);
        row.addView(check, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> {
            boolean nowChecked = !check.isChecked();
            check.setChecked(nowChecked);
            if (nowChecked) {
                chosen.add(label);
            } else {
                chosen.remove(label);
            }
        });
        list.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** 分组单选回调：configId 为 ModelConfig.getId()。 */
    public interface OnGroupedModelSelectedListener {
        void onModelSelected(String configId);

        void onManageModels();
    }

    /**
     * 对话头部快速切换用的选择器：按服务商分组置顶当前选中、行显示模型名（副行 model id）、
     * 选中标记、底部"管理模型"入口。单选语义。
     */
    public static void showGrouped(Context context, List<cn.lineai.model.ModelConfig> models,
                                   String selectedConfigId, OnGroupedModelSelectedListener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.roundedTop(context, LineTheme.SURFACE_ELEVATED, 16));

        TextView title = LineTheme.text(context, context.getString(R.string.header_model_switch_desc),
                LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        LineTheme.padding(title, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        panel.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new cn.lineai.ui.theme.BoundedScrollView(context, 460);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 服务商分组（groupId 聚合，选中组置顶；组内按 modelId 去重合并角色标签）
        List<cn.lineai.model.ModelGrouping.ProviderGroup> groups =
                cn.lineai.model.ModelGrouping.groupForUi(models, selectedConfigId);
        for (cn.lineai.model.ModelGrouping.ProviderGroup group : groups) {
            addGroupedHeader(list, context, group.name, group.containsSelected);
            List<cn.lineai.model.ModelGrouping.GroupedModel> groupedModels =
                    cn.lineai.model.ModelGrouping.dedupeByModelId(group.models);
            for (cn.lineai.model.ModelGrouping.GroupedModel grouped : groupedModels) {
                cn.lineai.model.ModelConfig selectedSlot = grouped.findSelected(selectedConfigId);
                // 展示行绑定组内第一个槽位（切换语义：点击 = 选中该模型行的 main 槽位优先）
                cn.lineai.model.ModelConfig rowModel = selectedSlot != null ? selectedSlot : grouped.slots.get(0);
                addGroupedRow(list, dialog, rowModel, selectedSlot != null,
                        slotRolesLabel(context, grouped.getSlotRoles()), listener);
            }
        }

        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));

        TextView manage = LineTheme.text(context, context.getString(R.string.screen_models_manage),
                LineTheme.FONT_MD, LineTheme.ACCENT, Typeface.BOLD);
        LineTheme.padding(manage, LineTheme.LG, 14, LineTheme.LG, 14);
        manage.setClickable(true);
        manage.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onManageModels();
            }
        });
        panel.addView(manage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.setPadding(0, 0, 0, LineTheme.dp(context, 8));

        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(DialogDimensions.insetDialogWidth(context), LinearLayout.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    /** 组头：服务商名 + 「默认」标记（选中组）。 */
    private static void addGroupedHeader(LinearLayout list, Context context, String providerName, boolean isDefault) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(header, LineTheme.LG, 8, LineTheme.LG, 2);
        TextView name = LineTheme.text(context, providerName, LineTheme.FONT_XS,
                isDefault ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        header.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (isDefault) {
            TextView mark = LineTheme.text(context,
                    context.getString(R.string.screen_models_group_default),
                    LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            header.addView(mark, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        list.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** 角色标签合并文案（「主模型 · Sonnet」）。 */
    private static String slotRolesLabel(Context context, List<String> roles) {
        StringBuilder builder = new StringBuilder();
        for (String role : roles) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(slotRoleLabel(context, role));
        }
        return builder.toString();
    }

    private static String slotRoleLabel(Context context, String role) {
        if (cn.lineai.model.ModelConfig.SLOT_HAIKU.equals(role)) {
            return context.getString(R.string.model_slot_haiku);
        }
        if (cn.lineai.model.ModelConfig.SLOT_SONNET.equals(role)) {
            return context.getString(R.string.model_slot_sonnet);
        }
        if (cn.lineai.model.ModelConfig.SLOT_OPUS.equals(role)) {
            return context.getString(R.string.model_slot_opus);
        }
        return context.getString(R.string.model_slot_main);
    }

    /** 分组选择器行：主行模型名，副行 model id · 角色标签，选中打勾。 */
    private static void addGroupedRow(LinearLayout list, Dialog dialog, cn.lineai.model.ModelConfig model,
                                      boolean selected, String rolesLabel, OnGroupedModelSelectedListener listener) {
        Context context = list.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        LineTheme.padding(row, LineTheme.LG, 10, LineTheme.LG, 10);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onModelSelected(model.getId());
            }
        });

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String name = model.getName() == null || model.getName().length() == 0
                ? model.getModelId() : model.getName();
        TextView rowTitle = LineTheme.text(context, name, LineTheme.FONT_MD,
                selected ? LineTheme.ACCENT : LineTheme.TEXT, selected ? Typeface.BOLD : Typeface.NORMAL);
        rowTitle.setSingleLine(true);
        rowTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(rowTitle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        String subText = rolesLabel.length() > 0
                ? model.getModelId() + " · " + rolesLabel
                : model.getModelId();
        TextView sub = LineTheme.text(context, subText, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = LineTheme.dp(context, 2);
        info.addView(sub, subParams);

        if (selected) {
            IconButtonView check = new IconButtonView(context, IconButtonView.CHECK);
            check.setIconColor(LineTheme.ACCENT);
            check.setIconSizeDp(18, 16);
            check.setClickable(false);
            row.addView(check, new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));
        }
        list.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    public static void show(Context context, List<String> modelIds, String selectedId, OnModelSelectedListener listener) {
        show(context, modelIds, selectedId, listener, false);
    }

    public static void show(Context context, List<String> modelIds, String selectedId, OnModelSelectedListener listener, boolean dismissOnly) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.roundedTop(context, LineTheme.SURFACE_ELEVATED, 16));

        View handle = new View(context);
        handle.setBackground(LineTheme.rounded(context, LineTheme.TEXT_TERTIARY, 2));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        handleParams.bottomMargin = LineTheme.dp(context, LineTheme.XS);
        panel.addView(handle, handleParams);

        TextView title = LineTheme.text(context, context.getString(R.string.screen_model_add_picker_title), LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        LineTheme.padding(title, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));

        ScrollView scroll = new cn.lineai.ui.theme.BoundedScrollView(context, 420);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (String id : modelIds) {
            addRow(list, dialog, id, id.equals(selectedId), false, listener);
        }
        addRow(list, dialog, context.getString(R.string.screen_model_add_custom_id_picker), false, true, listener);
        panel.setPadding(0, 0, 0, LineTheme.dp(context, 12));

        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(DialogDimensions.insetDialogWidth(context), LinearLayout.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private static void addRow(LinearLayout list, Dialog dialog, String label, boolean selected, boolean custom, OnModelSelectedListener listener) {
        Context context = list.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        LineTheme.padding(row, LineTheme.LG, 14, LineTheme.LG, 14);
        TextView text = LineTheme.text(context, label, LineTheme.FONT_MD, custom ? LineTheme.ACCENT : LineTheme.TEXT, Typeface.NORMAL);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (!custom && selected) {
            IconButtonView check = new IconButtonView(context, IconButtonView.CHECK);
            check.setIconColor(LineTheme.ACCENT);
            check.setIconSizeDp(18, 16);
            check.setClickable(false);
            row.addView(check, new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));
        }

        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onModelSelected(custom ? "" : label, custom);
            }
        });
        list.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }
}
