package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.OutputSettings;
import cn.lineai.ui.theme.LineTheme;

public final class SecuritySettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onAllowAnyHttpChanged(boolean enabled);

        void onBrowserJavaScriptChanged(boolean enabled);

        void onBypassPathProtectionChanged(boolean enabled);

        void onProxyApply(String host, int port);

        void onProxyClear();
    }

    private final Listener listener;
    private final FormTextFieldView proxyHostField;
    private final FormTextFieldView proxyPortField;
    private Switch bypassPathProtectionSwitch;
    private boolean bypassDialogInProgress;

    public SecuritySettingsScreenView(Context context, OutputSettings settings, String proxyHost, int proxyPort, Listener listener) {
        super(context, context.getString(R.string.screen_security_title), listener::onBack, null);
        this.listener = listener;
        OutputSettings safeSettings = settings == null
                ? new OutputSettings(false, OutputSettings.BROWSER_BUILTIN)
                : settings;
        boolean allowAnyHttp = safeSettings.isAllowAnyHttp();
        boolean browserJavaScriptEnabled = safeSettings.isBrowserJavaScriptEnabled();
        boolean bypassPathProtection = safeSettings.isBypassPathProtection();
        LinearLayout content = getContent();

        SettingsSectionView http = new SettingsSectionView(context, context.getString(R.string.screen_security_section_http));
        http.addRow(new SwitchRowView(context, IconButtonView.SHIELD_CHECK,
                context.getString(R.string.settings_row_security_allow_any_http_title),
                context.getString(R.string.settings_row_security_allow_any_http_desc),
                allowAnyHttp,
                (buttonView, isChecked) -> listener.onAllowAnyHttpChanged(isChecked)), false);
        content.addView(http, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView proxy = new SettingsSectionView(context, context.getString(R.string.screen_security_section_proxy));
        proxyHostField = new FormTextFieldView(context,
                context.getString(R.string.screen_security_proxy_host),
                proxyHost == null ? "" : proxyHost,
                "127.0.0.1", "", false, false);
        proxy.addView(proxyHostField, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        proxyPortField = new FormTextFieldView(context,
                context.getString(R.string.screen_security_proxy_port),
                proxyPort > 0 ? String.valueOf(proxyPort) : "",
                "7890", context.getString(R.string.screen_security_proxy_hint), false, false);
        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        portParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        proxy.addView(proxyPortField, portParams);

        LinearLayout proxyActions = new LinearLayout(context);
        proxyActions.setOrientation(LinearLayout.HORIZONTAL);
        proxyActions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, LineTheme.dp(context, 42), 1f);
        actionParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        LinearLayout applyButton = new LinearLayout(context);
        applyButton.setGravity(Gravity.CENTER);
        applyButton.setClickable(true);
        applyButton.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 8));
        applyButton.setOnClickListener(v -> onProxyApply(context));
        TextView applyText = LineTheme.text(context, context.getString(R.string.screen_security_proxy_apply),
                LineTheme.FONT_SM, LineTheme.TEXT_ON_COLOR, android.graphics.Typeface.BOLD);
        applyButton.addView(applyText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        proxyActions.addView(applyButton, actionParams);
        LinearLayout clearButton = new LinearLayout(context);
        clearButton.setGravity(Gravity.CENTER);
        clearButton.setClickable(true);
        clearButton.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 8, LineTheme.BORDER_LIGHT));
        clearButton.setOnClickListener(v -> {
            proxyHostField.setText("");
            proxyPortField.setText("");
            listener.onProxyClear();
            Toast.makeText(context, context.getString(R.string.screen_security_proxy_applied), Toast.LENGTH_SHORT).show();
        });
        TextView clearText = LineTheme.text(context, context.getString(R.string.screen_security_proxy_clear),
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, android.graphics.Typeface.BOLD);
        clearButton.addView(clearText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        proxyActions.addView(clearButton, new LinearLayout.LayoutParams(0, LineTheme.dp(context, 42), 1f));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        proxy.addView(proxyActions, actionsParams);
        proxy.addRow(new ActionRowView(context, IconButtonView.GLOBE,
                context.getString(R.string.screen_security_section_proxy),
                context.getString(R.string.screen_security_proxy_applied),
                false, false, () -> { }), false);
        content.addView(proxy, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView browser = new SettingsSectionView(context, context.getString(R.string.screen_security_section_browser));
        browser.addRow(new SwitchRowView(context, IconButtonView.CODE,
                context.getString(R.string.screen_output_browser_js_label),
                context.getString(R.string.screen_output_browser_js_desc),
                browserJavaScriptEnabled,
                (buttonView, isChecked) -> listener.onBrowserJavaScriptChanged(isChecked)), false);
        content.addView(browser, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView path = new SettingsSectionView(context, context.getString(R.string.screen_security_section_path));
        SwitchRowView bypassRow = new SwitchRowView(context, IconButtonView.SHIELD,
                context.getString(R.string.settings_row_security_bypass_path_title),
                context.getString(R.string.settings_row_security_bypass_path_desc),
                bypassPathProtection,
                (buttonView, isChecked) -> onBypassPathProtectionToggled(context, isChecked));
        bypassPathProtectionSwitch = findSwitch(bypassRow);
        path.addRow(bypassRow, false);
        content.addView(path, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void onProxyApply(Context context) {
        String host = proxyHostField.getText().trim();
        String portText = proxyPortField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            port = 0;
        }
        if (host.isEmpty() || port <= 0 || port > 65535) {
            Toast.makeText(context, context.getString(R.string.screen_security_proxy_invalid), Toast.LENGTH_SHORT).show();
            return;
        }
        listener.onProxyApply(host, port);
        Toast.makeText(context, context.getString(R.string.screen_security_proxy_applied), Toast.LENGTH_SHORT).show();
    }

    private void onBypassPathProtectionToggled(Context context, boolean isChecked) {
        if (bypassDialogInProgress) {
            return;
        }
        if (!isChecked) {
            listener.onBypassPathProtectionChanged(false);
            return;
        }
        bypassDialogInProgress = true;
        if (bypassPathProtectionSwitch != null) {
            bypassPathProtectionSwitch.setChecked(false);
        }
        new LineAlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_row_security_bypass_path_warning_title))
                .setMessage(context.getString(R.string.settings_row_security_bypass_path_warning_message))
                .setNegativeButton(context.getString(R.string.common_cancel), (dialog, which) -> bypassDialogInProgress = false)
                .setPositiveButton(context.getString(R.string.common_confirm), (dialog, which) -> {
                    if (bypassPathProtectionSwitch != null) {
                        bypassPathProtectionSwitch.setChecked(true);
                    }
                    bypassDialogInProgress = false;
                    listener.onBypassPathProtectionChanged(true);
                })
                .setOnCancelListener(dialog -> bypassDialogInProgress = false)
                .show();
    }

    private static Switch findSwitch(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            android.view.View child = row.getChildAt(i);
            if (child instanceof Switch) {
                return (Switch) child;
            }
        }
        return null;
    }
}
