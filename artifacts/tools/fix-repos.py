# 把仓库配置改成可移植：settings.gradle.kts 只留官方源；镜像交给本机 init 脚本。
#
# 为什么必须改：第一次真 CI 跑到「静态检查」就红，报 KSP 插件在所有仓库里找不到。
# runner 上实测：Maven Central 200、plugins.gradle.org 303、而**两个阿里云镜像都是 502**。
# 镜像排在 pluginManagement 第一位 → 插件解析整体失败。
# 本机之所以一直好用，是因为本机有 D:\Lib\gradle-repository\init.d\init.gradle 也挂了阿里云
# （即使 settings 里那份不通，init 那份也补上了）。也就是说：仓库里那份镜像配置对本机没用、对 CI 有害。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\settings.gradle.kts"
t = io.open(PATH, encoding="utf-8").read()

old_head = """// 依赖与插件来源：阿里云镜像在前（本机在中国大陆，直连 Google/Maven Central 很慢），
// google()/mavenCentral()/gradlePluginPortal() 在后作为兜底 —— 镜像没同步到的新版本会自动回落到官方源。
// 说明：本机有组织级 init 脚本会往 project 级塞仓库，我们用官方推荐的 FAIL_ON_PROJECT_REPOS
// 保证只有这里一份来源（那份脚本的项目级注册会被忽略并给出警告）。
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin") { name = "Aliyun-Plugin" }
        maven("https://maven.aliyun.com/repository/google") { name = "Aliyun-Google" }
        maven("https://maven.aliyun.com/repository/public") { name = "Aliyun-Public" }
        google {"""
new_head = """// 依赖与插件来源：**只用官方源**（google / mavenCentral / gradlePluginPortal）。
//
// ⚠️ 2026-10-10 更正（第一次真 CI 抓到的）：这里原本把阿里云镜像排在最前面"给本机加速"，
// 结果 GitHub runner 上两个阿里云镜像都返回 **502**，插件解析（KSP）因此在所有仓库里"找不到"，
// CI 直接红。而且对本机其实也没用——本机另有 init 脚本（`D:\\Lib\\gradle-repository\\init.d\\init.gradle`）
// 同样挂了阿里云，真正生效的是那一份。
//
// 结论：**镜像属于"本机加速"，不属于仓库配置**。想让本机快，就配在 init 脚本里（本机已经配了）；
// 仓库里这份必须可移植——换台机器、进 CI 都得能跑。
pluginManagement {
    repositories {
        google {"""
if old_head in t:
    t = t.replace(old_head, new_head, 1)
    print("pluginManagement 已改为只用官方源")
else:
    print("!! pluginManagement 段没匹配")

old_dep = """// 用 PREFER_SETTINGS 而不是官方的 FAIL_ON_PROJECT_REPOS：本机有组织级 init 脚本
    // （D:\\Lib\\gradle-repository\\init.d\\init.gradle）会往 project 级注册阿里云镜像，
    // FAIL_ON_PROJECT_REPOS 会直接构建失败。PREFER_SETTINGS 会忽略那些注册并给出警告。
    // 依赖来源仍然只有下面这一份（换台机器、进 CI 都按这份走）。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google") { name = "Aliyun-Google" }
        maven("https://maven.aliyun.com/repository/public") { name = "Aliyun-Public" }
        google {"""
new_dep = """// 用 PREFER_SETTINGS 而不是官方的 FAIL_ON_PROJECT_REPOS：本机有组织级 init 脚本
    // （D:\\Lib\\gradle-repository\\init.d\\init.gradle）会往 project 级注册阿里云镜像，
    // FAIL_ON_PROJECT_REPOS 会直接构建失败。PREFER_SETTINGS 会忽略那些注册并给出警告
    // （本机的加速就靠那份 init 脚本；仓库里这份保持可移植）。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {"""
if old_dep in t:
    t = t.replace(old_dep, new_dep, 1)
    print("dependencyResolutionManagement 已去掉阿里云镜像")
else:
    print("!! dependencyResolutionManagement 段没匹配")

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("settings.gradle.kts 已更新")
