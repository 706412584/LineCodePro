package cn.lineai.mvp;

import android.content.Context;
import android.util.Log;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import cn.lineai.ipc.terminal.TerminalShellCallback;
import cn.lineai.ipc.terminal.TerminalShellResult;

/**
 * 编辑框 git 分支芯片的后台探测（终端提供者模式）。
 *
 * <p>工作区切换时在后台跑 {@code git rev-parse --abbrev-ref HEAD}（cwd=工作区），
 * 结果经 {@link Sink#onGitBranch} 回主线程；非 git 仓库 / 失败 / 超时 → 空（芯片隐藏）。</p>
 */
public final class GitBranchController {

    public interface Sink {
        void onGitBranch(String branch);
    }

    private static final String TAG = "GitBranchCtrl";
    private static final long TIMEOUT_MS = 15000L;

    private final Context context;
    private final IpcProviderManager ipcProviderManager;
    private final BackgroundTaskRunner backgroundTasks;
    private final MainThreadDispatcher mainThread;
    private final Sink sink;
    private volatile String lastBranch = "";
    private volatile boolean probing;

    public GitBranchController(Context context, IpcProviderManager ipcProviderManager,
                               BackgroundTaskRunner backgroundTasks, MainThreadDispatcher mainThread, Sink sink) {
        this.context = context.getApplicationContext();
        this.ipcProviderManager = ipcProviderManager;
        this.backgroundTasks = backgroundTasks;
        this.mainThread = mainThread;
        this.sink = sink;
    }

    public String lastBranch() {
        return lastBranch;
    }

    /** 工作区/模式变化时触发；path 为空或 provider 未绑定时清空芯片。 */
    public void probe(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            publish("");
            return;
        }
        if (probing) {
            return;
        }
        probing = true;
        backgroundTasks.execute("linecode-git-branch", () -> {
            String branch = "";
            try {
                Object found = ipcProviderManager == null
                        ? null : ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL);
                if (found instanceof TerminalIpcProvider) {
                    TerminalIpcProvider provider = (TerminalIpcProvider) found;
                    if (provider.isBound()) {
                        StringBuilder output = new StringBuilder();
                        TerminalShellResult result = provider.executeShell(
                                "git rev-parse --abbrev-ref HEAD 2>/dev/null",
                                projectPath.trim(), TIMEOUT_MS, new TerminalShellCallback() {
                                    @Override
                                    public void onOutput(String content) {
                                        synchronized (output) {
                                            output.append(content == null ? "" : content);
                                        }
                                    }

                                    @Override
                                    public void onError(String error) {
                                    }

                                    @Override
                                    public void onComplete(int exitCode) {
                                    }
                                });
                        String text = output.toString().trim();
                        if (result.isSuccess() && text.length() > 0 && !text.contains(" ")) {
                            branch = text.split("\n")[0].trim();
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "git branch probe failed: " + e.getMessage());
            } finally {
                probing = false;
            }
            publish(branch);
        });
    }

    private void publish(final String branch) {
        lastBranch = branch;
        if (sink != null) {
            mainThread.post(() -> sink.onGitBranch(branch));
        }
    }
}
