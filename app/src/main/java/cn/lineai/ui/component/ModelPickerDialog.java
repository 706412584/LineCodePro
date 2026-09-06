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
