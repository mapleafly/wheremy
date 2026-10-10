# 修两条无障碍测试的 flake：把 Main 调度器设成 Unconfined（与其它 VM 测试一致），
# 这样 VM 的协程在调用线程上立即跑完，不再依赖 Android 主 Looper 的推进节奏。
#
# 为什么会 flake：CI runner 更慢/更忙时，`compose.waitUntil { vm.state.value.current != null }`
# 20 秒都等不到——本地跑同一条命令是通过的（4m46s），所以这是 flake 不是确定性失败。
import io
import sys

sys.stdout.reconfigure(errors="replace")
SRC = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\ui"

files = {
    SRC + r"\review\ReviewAccessibilityTest.kt": "ReviewViewModel",
    SRC + r"\gallery\GalleryAccessibilityTest.kt": "GalleryViewModel",
}

for path, vm in files.items():
    t = io.open(path, encoding="utf-8").read()
    if "Dispatchers.setMain" in t:
        print("  已有 setMain：%s" % path.split("\\")[-1])
        continue

    # 1) import
    for imp in [
        "import kotlinx.coroutines.Dispatchers",
        "import kotlinx.coroutines.ExperimentalCoroutinesApi",
        "import kotlinx.coroutines.test.resetMain",
        "import kotlinx.coroutines.test.setMain",
    ]:
        if imp not in t:
            # 插到第一个 kotlinx import 之前，或 runBlocking 之前
            anchor = "import kotlinx.coroutines.runBlocking"
            if anchor in t:
                t = t.replace(anchor, imp + "\n" + anchor, 1)
            else:
                anchor2 = "import org.junit.Rule"
                t = t.replace(anchor2, imp + "\n" + anchor2, 1)

    # 2) 类上加 @OptIn（Unconfined 是实验 API 吗？不是，但 setMain 需要 ExperimentalCoroutinesApi）
    if "@OptIn(ExperimentalCoroutinesApi::class)" not in t:
        t = t.replace("@RunWith(RobolectricTestRunner::class)", "@OptIn(ExperimentalCoroutinesApi::class)\n@RunWith(RobolectricTestRunner::class)", 1)

    # 3) @Before / @After
    setup = '''
    @Before
    fun setUpDispatcher() {
        // VM 的协程跑在 viewModelScope（Dispatchers.Main）上；设成 Unconfined 后立即执行，
        // 不再看主 Looper 的脸色——CI runner 慢的时候后者会让 waitUntil 超时（这条 flake 就是真 CI 抓到的）
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }
'''
    anchor3 = "    @get:Rule\n    val compose = createComposeRule()"
    if anchor3 in t:
        t = t.replace(anchor3, anchor3 + "\n" + setup, 1)
    else:
        # 退而求其次：挂在第一个 @Test 之前
        t = t.replace("    @Test", setup + "\n    @Test", 1)

    # 4) @After import（如果还没有）
    if "import org.junit.After" not in t:
        t = t.replace("import org.junit.Rule", "import org.junit.After\nimport org.junit.Rule", 1)
    if "import org.junit.Before" not in t:
        t = t.replace("import org.junit.Rule", "import org.junit.Before\nimport org.junit.Rule", 1)

    io.open(path, "w", encoding="utf-8", newline="").write(t)
    print("  已修：%s" % path.split("\\")[-1])

print("完成")
