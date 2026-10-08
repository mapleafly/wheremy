// 依赖与插件来源：阿里云镜像在前（本机在中国大陆，直连 Google/Maven Central 很慢），
// google()/mavenCentral()/gradlePluginPortal() 在后作为兜底 —— 镜像没同步到的新版本会自动回落到官方源。
// 说明：本机有组织级 init 脚本会往 project 级塞仓库，我们用官方推荐的 FAIL_ON_PROJECT_REPOS
// 保证只有这里一份来源（那份脚本的项目级注册会被忽略并给出警告）。
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin") { name = "Aliyun-Plugin" }
        maven("https://maven.aliyun.com/repository/google") { name = "Aliyun-Google" }
        maven("https://maven.aliyun.com/repository/public") { name = "Aliyun-Public" }
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
    // FAIL_ON_PROJECT_REPOS 会直接构建失败。PREFER_SETTINGS 会忽略那些注册并给出警告。
    // 依赖来源仍然只有下面这一份（换台机器、进 CI 都按这份走）。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google") { name = "Aliyun-Google" }
        maven("https://maven.aliyun.com/repository/public") { name = "Aliyun-Public" }
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
