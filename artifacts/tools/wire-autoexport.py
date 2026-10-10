# 给 DataModule 补 autoExporter 提供者、给 WheremyApp 补调度与 import（上一版 edit 因文件变动没落上）。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")

DM = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\di\DataModule.kt"
APP = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\WheremyApp.kt"

t = io.open(DM, encoding="utf-8").read()
provider = '''    /**
     * 定时自动导出（`FR-BAK-08`）。
     *
     * 落点是**每次现取**的：用户随时可能换目录或取消授权，缓存住一个 URI 会在下一次导出时炸。
     */
    @Provides
    @Singleton
    fun autoExporter(
        backup: BackupRepository,
        settings: SettingsRepository,
        @ApplicationContext context: Context,
    ): AutoExporter = AutoExporter(
        backup = backup,
        sinkProvider = {
            val uri = settings.current().autoExportUri
            if (uri.isBlank()) null else TreeDocumentSink(context.contentResolver, Uri.parse(uri))
        },
        settings = settings,
    )

'''
anchor = "    @Provides\n    @Singleton\n    fun analysisTrace"
if "fun autoExporter" in t:
    print("DataModule 已经有 autoExporter，跳过")
else:
    assert anchor in t, "DataModule 里没找到 analysisTrace 提供者"
    t = t.replace(anchor, provider + anchor, 1)
    for imp, after in [
        ("import com.wheremy.app.data.backup.AutoExporter", "import com.wheremy.app.data.backup.BackupRepository"),
        ("import com.wheremy.app.data.backup.TreeDocumentSink", "import com.wheremy.app.data.backup.BackupRepository"),
        ("import android.net.Uri", "import android.content.Context"),
    ]:
        if imp not in t:
            if after in t:
                t = t.replace(after, imp + "\n" + after, 1)
            else:
                t = t.replace("import com.wheremy.app.data.backup.BackupRepository", imp + "\nimport com.wheremy.app.data.backup.BackupRepository", 1)
    io.open(DM, "w", encoding="utf-8", newline="").write(t)
    print("DataModule 已补 autoExporter 与 import")

a = io.open(APP, encoding="utf-8").read()
if "AutoExportScheduler.schedule(this)" not in a:
    a = a.replace(
        "        ReminderScheduler.schedule(this)\n",
        "        ReminderScheduler.schedule(this)\n"
        "        // 定时自动导出（FR-BAK-08）：周期任务一直在，开关在 worker 里判——这样设置里开关一下立刻生效\n"
        "        AutoExportScheduler.schedule(this)\n",
        1,
    )
    if "import com.wheremy.app.data.backup.AutoExportScheduler" not in a:
        a = a.replace(
            "import com.wheremy.app.reminder.ReminderScheduler",
            "import com.wheremy.app.data.backup.AutoExportScheduler\nimport com.wheremy.app.reminder.ReminderScheduler",
            1,
        )
    io.open(APP, "w", encoding="utf-8", newline="").write(a)
    print("WheremyApp 已补调度与 import")
else:
    print("WheremyApp 已经调过 schedule，跳过")
