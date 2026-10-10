// 依赖与插件来源：**只用官方源**（google / mavenCentral / gradlePluginPortal）。
//
// ⚠️ 2026-10-10 更正（第一次真 CI 抓到的）：这里原本把阿里云镜像排在最前面"给本机加速"，
// 结果 GitHub runner 上两个阿里云镜像都返回 **502**，插件解析（KSP）因此在所有仓库里"找不到"，
// CI 直接红。而且对本机其实也没用——本机另有 init 脚本（`D:\Lib\gradle-repository\init.d\init.gradle`）
// 同样挂了阿里云，真正生效的是那一份。
//
// 结论：**镜像属于"本机加速"，不属于仓库配置**。想让本机快，就配在 init 脚本里（本机已经配了）；
// 仓库里这份必须可移植——换台机器、进 CI 都得能跑。
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 用 PREFER_SETTINGS 而不是官方的 FAIL_ON_PROJECT_REPOS：本机有组织级 init 脚本
    // （D:\Lib\gradle-repository\init.d\init.gradle）会往 project 级注册阿里云镜像，
    // FAIL_ON_PROJECT_REPOS 会直接构建失败。PREFER_SETTINGS 会忽略那些注册并给出警告
    // （本机的加速就靠那份 init 脚本；仓库里这份保持可移植）。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "wheremy"
include(":app")
