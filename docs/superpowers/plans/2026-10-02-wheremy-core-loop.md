# Wheremy 核心闭环实施计划（Plan 1：构建 + 数据层 + AI 层 + 最小可用录入查找）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一个可侧载到安卓机实际使用的 Wheremy APK：选图/拍照 → 多模态 AI 预填 → 一键确认入库到层级容器树，并能用中文关键词搜索与位置树下钻找到物品。

**Architecture:** 单 Gradle module，原生 Kotlin + Jetpack Compose。Room/SQLite 是唯一真相源，全部数据落在 APP 私有目录。AI 通过 `PhotoAnalyzer` 接口接入，真实实现与离线降级实现可互换，AI 失败永远不阻塞录入。

**Tech Stack:** AGP 8.13.2、Gradle 8.14.3（用本机 PATH 上的 `gradle`，本计划不生成 wrapper）、Kotlin 2.2.21、KSP 2.2.21-2.0.5、Compose BOM 2026.06.01、Room 2.8.5、OkHttp 4.12.0、kotlinx-serialization-json 1.10.0、Coil3 3.3.0、DataStore 1.2.1、core-ktx 1.18.0、lifecycle 2.10.0、Robolectric 4.16.1、JUnit 4.13.2、MockWebServer 4.12.0。后四个版本是 Task 1 实施时定下来的：更新的发布要求 AGP 9.1 + compileSdk 37，本机只有 android-36，见"实施偏差记录"第 7 条。

**Spec:** `docs/superpowers/specs/2026-10-02-wheremy-design.md`。本计划实现 spec 的 M0–M4；M5 到期提醒、备份导出导入、设置页与 CameraX 实时取景另写 Plan 2（见文末"Plan 2 范围"）。

## Global Constraints

每个任务都隐含遵守以下条目，数值逐字取自 spec：

- 单机使用，**不做**账号、云同步、多人共享、iOS/Web/小程序。
- `minSdk 26`、`targetSdk 36`、`compileSdk 36`，包名 `com.wheremy.app`。
- 位置树在 Kotlin 内存中构建，**不用** SQL 递归 CTE。
- 搜索用单派生列 `search_text` + `LIKE`，**不用** FTS5。
- 待归类收件箱 = `locationId IS NULL OR status = 'DRAFT'`，**不建**独立表。
- AI 走 OpenAI 兼容 `POST {base_url}/chat/completions`，默认 `qwen-vl-max`；`temperature 0.1`、`max_tokens 600`、callTimeout 15s、失败重试 1 次后降级。
- 尺寸：AI 用图长边 1024（q80），原图长边 1600（q85），缩略图最长边 256（q80）。
- API key 只存 DataStore；**绝不写入备份文件，绝不提交进 git**。
- 图标只用 `material-icons-core` 内置项，**不引入** `material-icons-extended`。
- 每个任务结束前 `gradle :app:testDebugUnitTest` 必须全绿才允许提交。

## 与 spec 的有意偏离

见文末"实施偏差记录"共 9 条，其中前两条影响 spec 的既有表述，需要用户确认：Plan 1 用系统 Photo Picker 代替 CameraX 取景；Plan 1 期间 Room 用 `fallbackToDestructiveMigration`。其余 7 条（不引入导航库、离线分析器只在测试里用、图标只用 core 集、搜索不做拼音容错、四个依赖退到 compileSdk 36 可用的最新版、callTimeout 归类为 TIMEOUT、ViewModel 层 JVM 测试改用内存库 + inline 执行器）不改 spec 的目标形态，只记录取舍。

---

### Task 1: 项目骨架与构建链路

**Files:**
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/wheremy/app/MainActivity.kt`
- Test: `app/src/test/java/com/wheremy/app/BuildSmokeTest.kt`

**Interfaces:**
- Consumes: 无
- Produces: `gradle :app:assembleDebug` 产出 `app/build/outputs/apk/debug/app-debug.apk`；`gradle/libs.versions.toml` 中的全部 alias 供后续任务直接使用。

- [x] **Step 1: 写 `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.13.2"
kotlin = "2.2.21"
ksp = "2.2.21-2.0.5"
composeBom = "2026.06.01"
room = "2.8.5"
okhttp = "4.12.0"
serialization = "1.10.0"
coroutines = "1.10.2"
coil = "3.3.0"
datastore = "1.2.1"
lifecycle = "2.10.0"
coreKtx = "1.18.0"
activityCompose = "1.11.0"
junit = "4.13.2"
robolectric = "4.16.1"
androidxTestCore = "1.7.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [x] **Step 2: 写 `settings.gradle.kts` 与 `gradle.properties`**

`settings.gradle.kts`（镜像地址显式写死，不依赖 `~/.gradle/init.d/init.gradle`，换机也能构建）：

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}
rootProject.name = "wheremy"
include(":app")
```

`gradle.properties`：

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [x] **Step 3: 写 `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wheremy.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wheremy.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

- [x] **Step 4: 写 Manifest、资源与空壳 Activity**

`app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:label="@string/app_name"
        android:allowBackup="false"
        android:theme="@style/Theme.Wheremy">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`：

```xml
<resources>
    <string name="app_name">Wheremy</string>
</resources>
```

`app/src/main/res/values/themes.xml`：

```xml
<resources>
    <style name="Theme.Wheremy" parent="android:Theme.Material.NoActionBar" />
</resources>
```

`app/src/main/java/com/wheremy/app/MainActivity.kt`：

```kotlin
package com.wheremy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { Text("Wheremy") } } }
    }
}
```

- [x] **Step 5: 写冒烟测试 `app/src/test/java/com/wheremy/app/BuildSmokeTest.kt`**

```kotlin
package com.wheremy.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildSmokeTest {
    @Test fun arithmeticStillWorks() = assertEquals(2, 1 + 1)
}
```

- [x] **Step 6: 生成 local.properties（已被 .gitignore 排除）**

Run: `printf 'sdk.dir=D:/Lib/Android/Sdk\n' > local.properties`
Expected: 文件存在；`git status --short` 里看不到它。

路径必须写正斜杠。写成 `D:\\Lib\\Android\\Sdk` 时，Java properties 解码会把没被转义的反斜杠吃掉，AGP 拿到 `D:LibAndroidSdk`，配置 `:app:testDebugUnitTest` 阶段直接抛 `java.io.IOException: 文件名、目录名或卷标语法不正确`。

- [x] **Step 7: 跑单元测试**

Run: `gradle :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`，`BuildSmokeTest` 通过。首次运行会下载依赖（走阿里云镜像）。若某个版本在本机跑不过（镜像上没有、或它要求的 AGP/compileSdk 比本机高），就近取可用版本，并把改动写进本文件末尾"实施偏差记录"。Task 1 实施时就是这样定下 composeBom/coreKtx/lifecycle/coil 四个版本的，见偏差记录第 7 条。

- [x] **Step 8: 打 APK**

Run: `gradle :app:assembleDebug --console=plain`
Expected: `app/build/outputs/apk/debug/app-debug.apk` 存在。

- [x] **Step 9: 提交**

```bash
git add settings.gradle.kts gradle.properties gradle app/build.gradle.kts app/src app/schemas
git commit -m "build: 安卓原生骨架，Gradle + Compose + Room 工具链打通"
```

---

### Task 2: Room 实体、DAO 与数据库

**Files:**
- Create: `app/src/main/java/com/wheremy/app/data/db/Entities.kt`
- Create: `app/src/main/java/com/wheremy/app/data/db/Daos.kt`
- Create: `app/src/main/java/com/wheremy/app/data/db/WheremyDatabase.kt`
- Test: `app/src/test/java/com/wheremy/app/data/db/DatabaseSmokeTest.kt`

**Interfaces:**
- Consumes: Task 1 的 Room + KSP 配置。
- Produces:
  - `data class LocationNodeEntity(id: String, parentId: String?, name: String, kind: String?, sortOrder: Int, createdAt: Long, updatedAt: Long)`
  - `data class ItemEntity(id: String, name: String, category: String?, seasons: String?, color: String?, brand: String?, material: String?, aliases: String?, description: String?, notes: String?, quantity: Int, locationId: String?, status: ItemStatus, expiresAt: Long?, expireKind: String?, expireNotifiedAt: Long?, searchText: String, createdAt: Long, updatedAt: Long, lastMovedAt: Long?)`
  - `data class ItemPhotoEntity(id: String, ownerType: String, ownerId: String, filePath: String, thumbPath: String, sortOrder: Int, createdAt: Long)`
  - `data class ItemTagEntity(itemId: String, tag: String)`
  - `enum class ItemStatus { DRAFT, CONFIRMED }`、`object PhotoOwner { const val ITEM; const val LOCATION }`
  - `abstract class WheremyDatabase : RoomDatabase`，`locationDao(): LocationDao`、`itemDao(): ItemDao`、`photoDao(): PhotoDao`、`companion object { const val NAME = "wheremy.db"; fun create(Context): WheremyDatabase }`

- [x] **Step 1: 写失败测试**

`app/src/test/java/com/wheremy/app/data/db/DatabaseSmokeTest.kt`：

```kotlin
package com.wheremy.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseSmokeTest {
    private lateinit var db: WheremyDatabase

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WheremyDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    @Test fun insertsLocationItemTagAndPhoto() = runBlocking {
        db.locationDao().upsert(LocationNodeEntity("root", null, "家", null, 0, 1L, 1L))
        db.itemDao().upsert(
            ItemEntity(
                id = "i1", name = "深灰色羊毛外套", category = "衣物",
                seasons = "秋,冬", color = "深灰", brand = null, material = "羊毛",
                aliases = "厚外套", description = "中长款翻领外套", notes = null,
                quantity = 1, locationId = "root", status = ItemStatus.CONFIRMED,
                expiresAt = null, expireKind = null, expireNotifiedAt = null,
                searchText = "深灰色羊毛外套 厚外套", createdAt = 1L, updatedAt = 1L,
                lastMovedAt = null,
            )
        )
        db.itemDao().insertTags(listOf(ItemTagEntity("i1", "秋冬"), ItemTagEntity("i1", "外出")))
        db.photoDao().upsert(
            ItemPhotoEntity("p1", PhotoOwner.ITEM, "i1", "originals/a.jpg", "thumbs/a.jpg", 0, 1L)
        )

        assertEquals(1, db.locationDao().all().size)
        assertEquals(listOf("外出", "秋冬"), db.itemDao().tagsFor("i1"))
        assertEquals(1, db.photoDao().forOwner(PhotoOwner.ITEM, "i1").size)
        assertEquals(ItemStatus.CONFIRMED, db.itemDao().byId("i1")!!.status)
        assertNull(db.itemDao().byId("missing"))
    }

    @Test fun upsertReplacesExistingRow() = runBlocking {
        db.itemDao().upsert(ItemEntity("i2", "旧名", null, null, null, null, null, null, null, null,
            1, null, ItemStatus.DRAFT, null, null, null, "旧名", 1L, 1L, null))
        db.itemDao().upsert(ItemEntity("i2", "新名", null, null, null, null, null, null, null, null,
            1, null, ItemStatus.DRAFT, null, null, null, "新名", 1L, 2L, null))
        assertEquals(1, db.itemDao().all().size)
        assertEquals("新名", db.itemDao().byId("i2")!!.name)
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*DatabaseSmokeTest" --console=plain`
Expected: 编译失败，`unresolved reference: ItemEntity` / `WheremyDatabase`。

- [x] **Step 3: 写 `Entities.kt`**

```kotlin
package com.wheremy.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ItemStatus { DRAFT, CONFIRMED }

object PhotoOwner {
    const val ITEM = "item"
    const val LOCATION = "location"
}

@Entity(
    tableName = "location_node",
    foreignKeys = [ForeignKey(
        entity = LocationNodeEntity::class, parentColumns = ["id"],
        childColumns = ["parentId"], onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("parentId")],
)
data class LocationNodeEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val kind: String?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "item",
    foreignKeys = [ForeignKey(
        entity = LocationNodeEntity::class, parentColumns = ["id"],
        childColumns = ["locationId"], onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("locationId"), Index("status"), Index("expiresAt")],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String?,
    val seasons: String?,
    val color: String?,
    val brand: String?,
    val material: String?,
    val aliases: String?,
    val description: String?,
    val notes: String?,
    val quantity: Int,
    val locationId: String?,
    val status: ItemStatus,
    val expiresAt: Long?,
    val expireKind: String?,
    val expireNotifiedAt: Long?,
    val searchText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMovedAt: Long?,
)

@Entity(tableName = "item_photo", indices = [Index("ownerType", "ownerId")])
data class ItemPhotoEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val filePath: String,
    val thumbPath: String,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(tableName = "item_tag", primaryKeys = ["itemId", "tag"], indices = [Index("tag")])
data class ItemTagEntity(val itemId: String, val tag: String)
```

- [x] **Step 4: 写 `Daos.kt`**

```kotlin
package com.wheremy.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LocationDao {
    @Upsert suspend fun upsert(node: LocationNodeEntity)
    @Query("SELECT * FROM location_node") suspend fun all(): List<LocationNodeEntity>
    @Query("SELECT * FROM location_node WHERE id = :id") suspend fun byId(id: String): LocationNodeEntity?
    @Delete suspend fun delete(node: LocationNodeEntity)
}

@Dao
interface ItemDao {
    @Upsert suspend fun upsert(item: ItemEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTags(tags: List<ItemTagEntity>)
    @Query("DELETE FROM item_tag WHERE itemId = :itemId") suspend fun clearTags(itemId: String)
    @Query("SELECT tag FROM item_tag WHERE itemId = :itemId ORDER BY tag") suspend fun tagsFor(itemId: String): List<String>
    @Query("SELECT * FROM item WHERE id = :id") suspend fun byId(id: String): ItemEntity?
    @Query("SELECT * FROM item") suspend fun all(): List<ItemEntity>
    @Query("SELECT * FROM item WHERE locationId = :locationId ORDER BY updatedAt DESC")
    suspend fun atLocation(locationId: String): List<ItemEntity>
    @Query("SELECT * FROM item WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun byStatus(status: ItemStatus): List<ItemEntity>
    @Delete suspend fun delete(item: ItemEntity)
}

@Dao
interface PhotoDao {
    @Upsert suspend fun upsert(photo: ItemPhotoEntity)
    @Query("SELECT * FROM item_photo WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY sortOrder")
    suspend fun forOwner(ownerType: String, ownerId: String): List<ItemPhotoEntity>
    @Query("DELETE FROM item_photo WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: String)
}
```

- [x] **Step 5: 写 `WheremyDatabase.kt`**

```kotlin
package com.wheremy.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class ItemStatusConverter {
    @TypeConverter fun fromStatus(v: String): ItemStatus = ItemStatus.valueOf(v)
    @TypeConverter fun toStatus(v: ItemStatus): String = v.name
}

@Database(
    entities = [LocationNodeEntity::class, ItemEntity::class, ItemPhotoEntity::class, ItemTagEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(ItemStatusConverter::class)
abstract class WheremyDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun itemDao(): ItemDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val NAME = "wheremy.db"

        /** Plan 1 期间允许破坏性迁移（尚无真实数据），发布前换成真实 Migration */
        fun create(appContext: Context): WheremyDatabase =
            Room.databaseBuilder(appContext, WheremyDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
```

- [x] **Step 6: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*DatabaseSmokeTest" --console=plain`
Expected: 2 个测试 PASS。`app/schemas/com.wheremy.app.data.db.WheremyDatabase/1.json` 出现，证明 `exportSchema` 生效。

- [x] **Step 7: 提交**

```bash
git add app/src app/schemas
git commit -m "feat: Room 数据模型（物品、容器树、照片、标签）"
```

---

### Task 3: search_text 派生列、中文 LIKE 搜索与结果排序

**Files:**
- Create: `app/src/main/java/com/wheremy/app/data/db/SearchTextBuilder.kt`
- Create: `app/src/main/java/com/wheremy/app/domain/SearchRanker.kt`
- Modify: `app/src/main/java/com/wheremy/app/data/db/Daos.kt`（`ItemDao` 增加查询方法）
- Test: `app/src/test/java/com/wheremy/app/data/db/SearchTextBuilderTest.kt`
- Test: `app/src/test/java/com/wheremy/app/data/db/ItemSearchDaoTest.kt`
- Test: `app/src/test/java/com/wheremy/app/domain/SearchRankerTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `ItemEntity`、`ItemDao`。
- Produces:
  - `object SearchTextBuilder { fun build(name: String, brand: String?, aliases: String?, description: String?, notes: String?, tags: List<String>): String }`
  - `ItemDao.searchLike(pattern: String): List<ItemEntity>`、`ItemDao.inbox(): List<ItemEntity>`、`ItemDao.moveTo(itemId: String, locationId: String?, movedAt: Long)`
  - `object SearchRanker { fun rank(query: String, items: List<ItemEntity>): List<ItemEntity> }`

- [x] **Step 1: 写 SearchTextBuilder 的失败测试**

`app/src/test/java/com/wheremy/app/data/db/SearchTextBuilderTest.kt`：

```kotlin
package com.wheremy.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextBuilderTest {
    @Test fun joinsFieldsInSpecOrder() {
        val s = SearchTextBuilder.build(
            name = "深灰色羊毛外套", brand = "Uniqlo", aliases = "厚外套,大衣",
            description = "中长款翻领", notes = null, tags = listOf("秋冬", "外出"),
        )
        assertEquals("深灰色羊毛外套 uniqlo 厚外套,大衣 秋冬 外出 中长款翻领", s)
    }

    @Test fun nullAndBlankFieldsAreSkipped() {
        assertEquals("护照", SearchTextBuilder.build("护照", null, null, null, null, emptyList()))
    }

    @Test fun collapsesWhitespaceAndLowercases() {
        assertEquals("nike 跑鞋", SearchTextBuilder.build("  NIKE   跑鞋  ", null, null, null, null, emptyList()))
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*SearchTextBuilderTest" --console=plain`
Expected: 编译失败 `unresolved reference: SearchTextBuilder`。

- [x] **Step 3: 实现 SearchTextBuilder**

`app/src/main/java/com/wheremy/app/data/db/SearchTextBuilder.kt`：

```kotlin
package com.wheremy.app.data.db

object SearchTextBuilder {
    fun build(
        name: String,
        brand: String?,
        aliases: String?,
        description: String?,
        notes: String?,
        tags: List<String>,
    ): String = listOfNotNull(
        name,
        brand,
        aliases,
        tags.joinToString(" ").ifBlank { null },
        description,
        notes,
    ).joinToString(" ")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
}
```

字段顺序即 spec 第 5 节的"名称 + 品牌 + 别名 + 标签 + 描述 + 备注"。

- [x] **Step 4: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*SearchTextBuilderTest" --console=plain`
Expected: 3 个测试 PASS。

- [x] **Step 5: 写 DAO 搜索的失败测试**

`app/src/test/java/com/wheremy/app/data/db/ItemSearchDaoTest.kt`：

```kotlin
package com.wheremy.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ItemSearchDaoTest {
    private lateinit var db: WheremyDatabase

    private fun item(
        id: String,
        name: String,
        aliases: String? = null,
        tags: List<String> = emptyList(),
        locationId: String? = null,
        status: ItemStatus = ItemStatus.CONFIRMED,
    ) = ItemEntity(
        id = id, name = name, category = "衣物", seasons = "秋", color = null,
        brand = null, material = null, aliases = aliases, description = null,
        notes = null, quantity = 1, locationId = locationId, status = status,
        expiresAt = null, expireKind = null, expireNotifiedAt = null,
        searchText = SearchTextBuilder.build(name, null, aliases, null, null, tags),
        createdAt = 1L, updatedAt = 1L, lastMovedAt = null,
    )

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WheremyDatabase::class.java).build()
        runBlocking {
            db.locationDao().upsert(LocationNodeEntity("root", null, "家", null, 0, 1L, 1L))
            db.itemDao().upsert(item("a", "深灰色羊毛外套", aliases = "厚外套"))
            db.itemDao().upsert(item("b", "自行车打气筒", tags = listOf("车库", "维修")))
            db.itemDao().upsert(item("c", "护照", tags = listOf("证件")))
        }
    }

    @After fun tearDown() = db.close()

    @Test fun matchesChineseSubstringInName() = runBlocking {
        assertEquals(listOf("a"), db.itemDao().searchLike("%羊毛%").map { it.id })
    }

    @Test fun matchesAliasOnly() = runBlocking {
        assertEquals(listOf("a"), db.itemDao().searchLike("%厚外套%").map { it.id })
    }

    @Test fun matchesTagOnly() = runBlocking {
        assertEquals(listOf("b"), db.itemDao().searchLike("%维修%").map { it.id })
    }

    @Test fun unmatchedQueryIsEmpty() = runBlocking {
        assertTrue(db.itemDao().searchLike("%不存在%").isEmpty())
    }

    @Test fun inboxPicksNullLocationAndDrafts() = runBlocking {
        db.itemDao().upsert(item("d", "待确认物品", locationId = "root", status = ItemStatus.DRAFT))
        db.itemDao().upsert(item("e", "已归类物品", locationId = "root"))
        assertEquals(setOf("a", "b", "c", "d"), db.itemDao().inbox().map { it.id }.toSet())
    }

    @Test fun moveToSetsLocationAndLastMovedAt() = runBlocking {
        db.itemDao().moveTo("a", "root", 999L)
        val moved = db.itemDao().byId("a")!!
        assertEquals("root", moved.locationId)
        assertEquals(999L, moved.lastMovedAt)
        assertEquals(999L, moved.updatedAt)
    }
}
```

- [x] **Step 6: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*ItemSearchDaoTest" --console=plain`
Expected: 编译失败 `unresolved reference: searchLike`。

- [x] **Step 7: 给 ItemDao 补查询**

`app/src/main/java/com/wheremy/app/data/db/Daos.kt` 中 `ItemDao` 整块替换为：

```kotlin
@Dao
interface ItemDao {
    @Upsert suspend fun upsert(item: ItemEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTags(tags: List<ItemTagEntity>)
    @Query("DELETE FROM item_tag WHERE itemId = :itemId") suspend fun clearTags(itemId: String)
    @Query("SELECT tag FROM item_tag WHERE itemId = :itemId ORDER BY tag") suspend fun tagsFor(itemId: String): List<String>
    @Query("SELECT * FROM item WHERE id = :id") suspend fun byId(id: String): ItemEntity?
    @Query("SELECT * FROM item") suspend fun all(): List<ItemEntity>
    @Query("SELECT * FROM item WHERE searchText LIKE :pattern ORDER BY updatedAt DESC LIMIT 300")
    suspend fun searchLike(pattern: String): List<ItemEntity>
    @Query("SELECT * FROM item WHERE locationId IS NULL OR status = 'DRAFT' ORDER BY updatedAt DESC")
    suspend fun inbox(): List<ItemEntity>
    @Query("SELECT * FROM item WHERE locationId = :locationId ORDER BY updatedAt DESC")
    suspend fun atLocation(locationId: String): List<ItemEntity>
    @Query("SELECT * FROM item WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun byStatus(status: ItemStatus): List<ItemEntity>
    @Query("UPDATE item SET locationId = :locationId, lastMovedAt = :movedAt, updatedAt = :movedAt WHERE id = :itemId")
    suspend fun moveTo(itemId: String, locationId: String?, movedAt: Long)
    @Delete suspend fun delete(item: ItemEntity)
}
```

`inbox()` 里 `'DRAFT'` 字面量与 `ItemStatusConverter` 持久化的 `name` 一致（Global Constraints 定义的收件箱语义）。

- [x] **Step 8: 写排序器的失败测试**

`app/src/test/java/com/wheremy/app/domain/SearchRankerTest.kt`：

```kotlin
package com.wheremy.app.domain

import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.SearchTextBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRankerTest {
    private fun item(name: String, aliases: String? = null, tags: List<String> = emptyList()) =
        ItemEntity(
            id = name, name = name, category = null, seasons = null, color = null,
            brand = null, material = null, aliases = aliases, description = null,
            notes = null, quantity = 1, locationId = null, status = ItemStatus.CONFIRMED,
            expiresAt = null, expireKind = null, expireNotifiedAt = null,
            searchText = SearchTextBuilder.build(name, null, aliases, null, null, tags),
            createdAt = 1L, updatedAt = 1L, lastMovedAt = null,
        )

    @Test fun exactNameBeatsPrefixBeatsContains() {
        val ranked = SearchRanker.rank("外套", listOf(
            item("灰色厚外套"), item("外套收纳袋"), item("外套"),
        ))
        assertEquals(listOf("外套", "外套收纳袋", "灰色厚外套"), ranked.map { it.name })
    }

    @Test fun nameHitOutranksAliasHit() {
        val ranked = SearchRanker.rank("外套", listOf(item("大衣", aliases = "外套"), item("外套袋")))
        assertEquals(listOf("外套袋", "大衣"), ranked.map { it.name })
    }

    @Test fun aliasOutranksTag() {
        val ranked = SearchRanker.rank("维修", listOf(
            item("扳手", tags = listOf("维修")), item("螺丝刀", aliases = "维修工具"),
        ))
        assertEquals(listOf("螺丝刀", "扳手"), ranked.map { it.name })
    }

    @Test fun latinQueryIsCaseInsensitive() {
        assertEquals(1, SearchRanker.rank("NIKE", listOf(item("nike 跑鞋"))).size)
    }

    @Test fun blankQueryPreservesInputOrder() {
        val input = listOf(item("b"), item("a"))
        assertEquals(listOf("b", "a"), SearchRanker.rank("   ", input).map { it.name })
    }

    @Test fun sameScoreKeepsDaoOrder() {
        val input = listOf(item("外套A"), item("外套B"))
        assertEquals(listOf("外套A", "外套B"), SearchRanker.rank("外套", input).map { it.name })
    }
}
```

- [x] **Step 9: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*SearchRankerTest" --console=plain`
Expected: 编译失败 `unresolved reference: SearchRanker`。

- [x] **Step 10: 实现 SearchRanker**

`app/src/main/java/com/wheremy/app/domain/SearchRanker.kt`：

```kotlin
package com.wheremy.app.domain

import com.wheremy.app.data.db.ItemEntity

object SearchRanker {
    fun rank(query: String, items: List<ItemEntity>): List<ItemEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.sortedByDescending { score(it, q) }
    }

    private fun score(item: ItemEntity, q: String): Int {
        val name = item.name.lowercase()
        when {
            name == q -> return 100
            name.startsWith(q) -> return 80
            name.contains(q) -> return 60
        }
        val aliases = item.aliases?.lowercase().orEmpty()
        if (aliases.contains(q)) return 40
        return if (item.searchText.lowercase().contains(q)) 20 else 0
    }
}
```

分数只表达相对次序，绝对值无语义。`sortedByDescending` 稳定，同分保留 DAO 的 `updatedAt DESC` 顺序。返回 0 的行仍保留（DAO 已用 LIKE 过滤，0 分只可能来自 `all()` + 筛选路径）。

- [x] **Step 11: 跑全部单测**

Run: `gradle :app:testDebugUnitTest --console=plain`
Expected: `SearchTextBuilderTest`、`ItemSearchDaoTest`、`SearchRankerTest`、`DatabaseSmokeTest` 全绿。

- [x] **Step 12: 提交**

```bash
git add app/src
git commit -m "feat: search_text 派生列、中文 LIKE 搜索与结果排序"
```

---

### Task 4: 位置树内存构建、面包屑与删除约束

**Files:**
- Create: `app/src/main/java/com/wheremy/app/domain/LocationTree.kt`
- Create: `app/src/main/java/com/wheremy/app/domain/LocationTreePolicy.kt`
- Test: `app/src/test/java/com/wheremy/app/domain/LocationTreeTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `LocationNodeEntity`。
- Produces:
  - `data class LocationNode(id: String, parentId: String?, name: String, kind: String?, sortOrder: Int)`
  - `class LocationTree` — `roots()`、`childrenOf(id)`、`pathOf(id): List<LocationNode>`、`breadcrumb(id, sep = " / ")`、`descendantIds(id): Set<String>`、`node(id): LocationNode?`、`all(): List<LocationNode>`
  - `fun buildLocationTree(nodes: List<LocationNodeEntity>): LocationTree`
  - `object LocationTreePolicy { fun canDelete(tree: LocationTree, id: String, itemCount: Int): Boolean }`

- [x] **Step 1: 写失败测试**

`app/src/test/java/com/wheremy/app/domain/LocationTreeTest.kt`：

```kotlin
package com.wheremy.app.domain

import com.wheremy.app.data.db.LocationNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTreeTest {
    private fun node(id: String, parent: String?, name: String, order: Int = 0, kind: String? = null) =
        LocationNodeEntity(id, parent, name, kind, order, 1L, 1L)

    private val tree = buildLocationTree(
        listOf(
            node("home", null, "家"),
            node("bedroom", "home", "主卧", 0),
            node("closet", "bedroom", "衣柜", 0, "柜子"),
            node("drawer", "closet", "第二层抽屉", 0, "抽屉"),
            node("kitchen", "home", "厨房", 1),
        )
    )

    @Test fun rootsAreNodesWithoutParent() {
        assertEquals(listOf("home"), tree.roots().map { it.id })
    }

    @Test fun childrenSortedByOrderThenName() {
        assertEquals(listOf("bedroom", "kitchen"), tree.childrenOf("home").map { it.id })
    }

    @Test fun breadcrumbWalksUpToRoot() {
        assertEquals("家 / 主卧 / 衣柜 / 第二层抽屉", tree.breadcrumb("drawer"))
    }

    @Test fun rootBreadcrumbIsItsOwnName() {
        assertEquals("家", tree.breadcrumb("home"))
    }

    @Test fun missingNodeHasEmptyPath() {
        assertEquals("", tree.breadcrumb("nope"))
        assertEquals(emptyList<LocationNode>(), tree.pathOf("nope"))
    }

    @Test fun descendantIdsIncludeSelfAndWholeSubtree() {
        assertEquals(setOf("home", "bedroom", "closet", "drawer", "kitchen"), tree.descendantIds("home"))
        assertEquals(setOf("bedroom", "closet", "drawer"), tree.descendantIds("bedroom"))
    }

    @Test fun orphanNodesSurfaceAsRoots() {
        val t = buildLocationTree(listOf(node("home", null, "家"), node("lost", "gone", "孤儿")))
        assertTrue(t.roots().map { it.id }.contains("lost"))
    }

    @Test fun parentCycleDoesNotHang() {
        val t = buildLocationTree(listOf(node("a", "b", "A"), node("b", "a", "B")))
        assertEquals("B / A", t.breadcrumb("a"))
        assertEquals(setOf("a", "b"), t.descendantIds("a"))
    }

    @Test fun canDeleteOnlyEmptyLeaf() {
        assertTrue(LocationTreePolicy.canDelete(tree, "kitchen", itemCount = 0))
        assertFalse(LocationTreePolicy.canDelete(tree, "home", itemCount = 0))
        assertFalse(LocationTreePolicy.canDelete(tree, "kitchen", itemCount = 2))
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*LocationTreeTest" --console=plain`
Expected: 编译失败 `unresolved reference: buildLocationTree`。

- [x] **Step 3: 实现 LocationTree**

`app/src/main/java/com/wheremy/app/domain/LocationTree.kt`：

```kotlin
package com.wheremy.app.domain

import com.wheremy.app.data.db.LocationNodeEntity

data class LocationNode(
    val id: String,
    val parentId: String?,
    val name: String,
    val kind: String?,
    val sortOrder: Int,
)

class LocationTree(private val byId: Map<String, LocationNode>) {

    fun all(): List<LocationNode> = byId.values.toList()

    fun node(id: String): LocationNode? = byId[id]

    /** parentId 为 null，或父节点不存在（脏数据）的节点都当根处理 */
    fun roots(): List<LocationNode> = ordering(
        byId.values.filter { it.parentId == null || it.parentId !in byId }
    )

    fun childrenOf(id: String): List<LocationNode> = ordering(byId.values.filter { it.parentId == id })

    /** seen 集合断环：父子被写坏时返回到环入口为止，不无限上溯 */
    fun pathOf(id: String): List<LocationNode> {
        val chain = ArrayList<LocationNode>(8)
        val seen = HashSet<String>(8)
        var current = byId[id]
        while (current != null && seen.add(current.id)) {
            chain.add(current)
            current = current.parentId?.let { byId[it] }
        }
        return chain.asReversed()
    }

    fun breadcrumb(id: String, sep: String = " / "): String = pathOf(id).joinToString(sep) { it.name }

    fun descendantIds(id: String): Set<String> {
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque<String>()
        stack.addLast(id)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (!out.add(cur)) continue
            childrenOf(cur).forEach { stack.addLast(it.id) }
        }
        return out
    }

    private fun ordering(nodes: Collection<LocationNode>) =
        nodes.sortedWith(compareBy({ it.sortOrder }, { it.name }))
}

/** spec 第 5 节：全量读入内存建树，不使用递归 SQL */
fun buildLocationTree(nodes: List<LocationNodeEntity>): LocationTree =
    LocationTree(
        nodes.associateBy { it.id }.mapValues { (_, e) ->
            LocationNode(e.id, e.parentId, e.name, e.kind, e.sortOrder)
        }
    )
```

- [x] **Step 4: 实现 LocationTreePolicy**

`app/src/main/java/com/wheremy/app/domain/LocationTreePolicy.kt`：

```kotlin
package com.wheremy.app.domain

object LocationTreePolicy {
    /** 有子节点或有物品就拒绝删除，不做级联（spec 第 5、8 节） */
    fun canDelete(tree: LocationTree, id: String, itemCount: Int): Boolean =
        tree.childrenOf(id).isEmpty() && itemCount == 0
}
```

- [x] **Step 5: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*LocationTreeTest" --console=plain`
Expected: 9 个测试 PASS。

- [x] **Step 6: 提交**

```bash
git add app/src
git commit -m "feat: 位置树内存构建、面包屑与删除约束"
```

---

### Task 5: PhotoStore 落盘与三档尺寸压缩

**Files:**
- Create: `app/src/main/java/com/wheremy/app/data/photos/PhotoStore.kt`
- Test: `app/src/test/java/com/wheremy/app/data/photos/PhotoStoreTest.kt`

**Interfaces:**
- Consumes: Android `Bitmap`/`BitmapFactory`，Task 1 依赖。
- Produces:
  - `data class StoredPhoto(originalPath: String, thumbPath: String, analysisJpeg: ByteArray)`
  - `class PhotoStore(val rootDir: File)` — `fun write(jpeg: ByteArray): StoredPhoto`、`fun file(relPath: String): File`、`fun delete(relPath: String): Boolean`
  - 坏数据 `write` 抛 `IllegalStateException("照片解码失败")`（spec 第 8 节"超大照片/解码失败"）

- [x] **Step 1: 写失败测试**

`app/src/test/java/com/wheremy/app/data/photos/PhotoStoreTest.kt`：

```kotlin
package com.wheremy.app.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoStoreTest {
    private lateinit var store: PhotoStore

    private fun jpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        return out.toByteArray()
    }

    private fun edgesOf(bytes: ByteArray): Pair<Int, Int> {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
        return bmp.width to bmp.height
    }

    private fun edgesOf(path: String) = edgesOf(File(store.rootDir, path).readBytes())

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        store = PhotoStore(File(ctx.filesDir, "photos-test").also { it.deleteRecursively() })
    }

    @Test fun writesOriginalAndThumbUnderUuidPaths() {
        val stored = store.write(jpeg(4000, 3000))
        assertTrue(File(store.rootDir, stored.originalPath).exists())
        assertTrue(File(store.rootDir, stored.thumbPath).exists())
        assertTrue(stored.originalPath.matches(Regex("originals/[0-9a-fA-F-]{36}\\.jpg")))
        assertTrue(stored.thumbPath.matches(Regex("thumbs/[0-9a-fA-F-]{36}\\.jpg")))
    }

    @Test fun originalLongEdgeIs1600() {
        val (w, h) = edgesOf(store.write(jpeg(4000, 3000)).originalPath)
        assertEquals(1600, maxOf(w, h))
        assertEquals(1200, minOf(w, h))
    }

    @Test fun thumbLongEdgeIs256() {
        val (w, h) = edgesOf(store.write(jpeg(4000, 3000)).thumbPath)
        assertEquals(256, maxOf(w, h))
    }

    @Test fun analysisBytesLongEdgeIs1024() {
        val (w, h) = edgesOf(store.write(jpeg(4000, 3000)).analysisJpeg)
        assertEquals(1024, maxOf(w, h))
    }

    @Test fun smallImageIsNotUpscaled() {
        val (w, h) = edgesOf(store.write(jpeg(600, 400)).originalPath)
        assertEquals(600 to 400, w to h)
    }

    @Test fun deleteRemovesFile() {
        val stored = store.write(jpeg(2000, 1500))
        assertTrue(store.delete(stored.originalPath))
        assertFalse(File(store.rootDir, stored.originalPath).exists())
    }

    @Test fun garbageBytesAreRejected() {
        // Robolectric 的阴影 BitmapFactory 默认把任意字节都"解码"成一张占位图，
        // 关掉宽容模式才能复现真机上 BitmapFactory 返回 null 的那条分支。
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        val result = runCatching { store.write(byteArrayOf(1, 2, 3)) }
        assertTrue("坏数据不应写出文件", result.isFailure)
        assertEquals(0, File(store.rootDir, "originals").listFiles()?.size ?: 0)
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*PhotoStoreTest" --console=plain`
Expected: 编译失败 `unresolved reference: PhotoStore`。

- [x] **Step 3: 实现 PhotoStore**

`app/src/main/java/com/wheremy/app/data/photos/PhotoStore.kt`：

```kotlin
package com.wheremy.app.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class StoredPhoto(
    val originalPath: String,
    val thumbPath: String,
    val analysisJpeg: ByteArray,
)

class PhotoStore(val rootDir: File) {
    private val originals = File(rootDir, DIR_ORIGINALS).apply { mkdirs() }
    private val thumbs = File(rootDir, DIR_THUMBS).apply { mkdirs() }

    fun file(relPath: String): File = File(rootDir, relPath)

    fun delete(relPath: String): Boolean = File(rootDir, relPath).delete()

    fun write(jpeg: ByteArray): StoredPhoto {
        val src = decode(jpeg)
        try {
            val id = UUID.randomUUID().toString()
            val originalFile = File(originals, "$id.jpg")
            val thumbFile = File(thumbs, "$id.jpg")
            compress(src, ORIGINAL_EDGE, QUALITY_ORIGINAL, originalFile)
            compress(src, THUMB_EDGE, QUALITY_THUMB, thumbFile)
            return StoredPhoto(
                originalPath = "$DIR_ORIGINALS/$id.jpg",
                thumbPath = "$DIR_THUMBS/$id.jpg",
                analysisJpeg = encode(src, ANALYSIS_EDGE, QUALITY_ANALYSIS),
            )
        } finally {
            src.recycle()
        }
    }

    /** 先读尺寸定 inSampleSize，避免手机原图整张进内存导致 OOM */
    private fun decode(jpeg: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { ERROR_DECODE }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longEdge / (sample * 2) >= ORIGINAL_EDGE) sample *= 2
        return BitmapFactory.decodeByteArray(
            jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error(ERROR_DECODE)
    }

    private fun compress(src: Bitmap, longEdge: Int, quality: Int, into: File) {
        val scaled = scaleTo(src, longEdge)
        into.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (scaled !== src) scaled.recycle()
    }

    private fun encode(src: Bitmap, longEdge: Int, quality: Int): ByteArray {
        val scaled = scaleTo(src, longEdge)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== src) scaled.recycle()
        return out.toByteArray()
    }

    private fun scaleTo(src: Bitmap, longEdge: Int): Bitmap {
        val edge = maxOf(src.width, src.height)
        if (edge <= longEdge) return src
        val ratio = longEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val DIR_ORIGINALS = "originals"
        const val DIR_THUMBS = "thumbs"
        const val ORIGINAL_EDGE = 1600
        const val THUMB_EDGE = 256
        const val ANALYSIS_EDGE = 1024
        const val QUALITY_ORIGINAL = 85
        const val QUALITY_THUMB = 80
        const val QUALITY_ANALYSIS = 80
        const val ERROR_DECODE = "照片解码失败"
    }
}
```

`decode` 的 `inSampleSize` 只保证不低于 `ORIGINAL_EDGE/2` 的粗降采样，随后 `scaleTo` 精确到 1600，两步都不产生超尺寸位图。

- [x] **Step 4: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*PhotoStoreTest" --console=plain`
Expected: 7 个测试 PASS。

实施时注意一处 Robolectric 阴影行为：`ShadowBitmapFactory` 默认宽容，任何字节都能"解码"成占位图，所以 `garbageBytesAreRejected` 里要调 `ShadowBitmapFactory.setAllowInvalidImageData(false)` 才能复现真机上 `BitmapFactory` 解码失败那条分支；生产代码里的 `check(bounds.outWidth > 0 …)` 保持不变，不要为了让测试通过而去改 `PhotoStore` 的校验逻辑。

- [x] **Step 5: 提交**

```bash
git add app/src
git commit -m "feat: 照片落盘、缩略图与 AI 分析尺寸压缩"
```

---

### Task 6: AnalysisResult、Prompt 与容错 JSON 解析

**Files:**
- Create: `app/src/main/java/com/wheremy/app/data/vision/AnalysisResult.kt`
- Create: `app/src/main/java/com/wheremy/app/data/vision/Prompt.kt`
- Create: `app/src/main/java/com/wheremy/app/data/vision/AnalysisParser.kt`
- Test: `app/src/test/java/com/wheremy/app/data/vision/AnalysisParserTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces:
  - `data class AnalysisResult(name: String?, category: String?, seasons: List<String>, color: String?, brand: String?, material: String?, aliases: List<String>, description: String?, tags: List<String>, containerGuess: String?)`，`val isEmpty: Boolean`，`AnalysisResult.EMPTY`
  - `object Prompt { val SYSTEM: String }`
  - `object AnalysisParser { fun parse(rawContent: String): AnalysisResult }`

- [x] **Step 1: 写失败测试**

`app/src/test/java/com/wheremy/app/data/vision/AnalysisParserTest.kt`（覆盖 spec 第 7 节的降级阶梯）：

```kotlin
package com.wheremy.app.data.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisParserTest {
    @Test fun parsesCleanJson() {
        val r = AnalysisParser.parse(
            """{"name":"深灰色羊毛外套","category":"衣物","seasons":["秋","冬"],"color":"深灰",""" +
                """"brand":null,"material":"羊毛","aliases":["厚外套"],"description":"中长款翻领",""" +
                """"tags":["秋冬","外出"],"container_guess":"衣柜"}"""
        )
        assertEquals("深灰色羊毛外套", r.name)
        assertEquals("衣物", r.category)
        assertEquals(listOf("秋", "冬"), r.seasons)
        assertEquals(listOf("厚外套"), r.aliases)
        assertEquals(listOf("秋冬", "外出"), r.tags)
        assertEquals("衣柜", r.containerGuess)
        assertEquals("羊毛", r.material)
        assertNull(r.brand)
    }

    @Test fun toleratesMarkdownFence() {
        val r = AnalysisParser.parse("```json\n{\"name\":\"护照\",\"tags\":[\"证件\"]}\n```")
        assertEquals("护照", r.name)
        assertEquals(listOf("证件"), r.tags)
    }

    @Test fun extractsBracesFromProse() {
        val r = AnalysisParser.parse("好的，识别结果如下：{\"name\":\"打气筒\"} 希望对你有帮助")
        assertEquals("打气筒", r.name)
    }

    @Test fun wrongTypeFieldIsDroppedButOthersSurvive() {
        val r = AnalysisParser.parse("""{"name":"雨伞","seasons":"秋","tags":["雨天"]}""")
        assertEquals("雨伞", r.name)
        assertEquals(emptyList<String>(), r.seasons)
        assertEquals(listOf("雨天"), r.tags)
    }

    @Test fun blankStringBecomesNull() {
        assertNull(AnalysisParser.parse("""{"name":"   "}""").name)
    }

    @Test fun literalNullStringBecomesNull() {
        assertNull(AnalysisParser.parse("""{"name":"钥匙","material":"null"}""").material)
    }

    @Test fun nonJsonYieldsEmpty() {
        assertTrue(AnalysisParser.parse("这不是 JSON").isEmpty)
        assertTrue(AnalysisParser.parse("").isEmpty)
    }

    @Test fun unknownKeysOnlyIsFlaggedEmpty() {
        assertTrue(AnalysisParser.parse("""{"whatever":1}""").isEmpty)
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*AnalysisParserTest" --console=plain`
Expected: 编译失败 `unresolved reference: AnalysisParser`。

- [x] **Step 3: 写 AnalysisResult**

`app/src/main/java/com/wheremy/app/data/vision/AnalysisResult.kt`：

```kotlin
package com.wheremy.app.data.vision

data class AnalysisResult(
    val name: String? = null,
    val category: String? = null,
    val seasons: List<String> = emptyList(),
    val color: String? = null,
    val brand: String? = null,
    val material: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val containerGuess: String? = null,
) {
    val isEmpty: Boolean
        get() = name == null && category == null && description == null && tags.isEmpty()

    companion object {
        val EMPTY = AnalysisResult()
    }
}
```

- [x] **Step 4: 写 Prompt**

`app/src/main/java/com/wheremy/app/data/vision/Prompt.kt`：

```kotlin
package com.wheremy.app.data.vision

object Prompt {
    val SYSTEM = """
        你是家庭物品收纳助手。看照片，识别其中最主要的单个物品。只输出一个 JSON 对象，不要解释，不要 Markdown 围栏。
        字段：
        name: 日常口语叫法，例如"羊毛外套"，不要商品全称。必填。
        category: 从 衣物/数码/电器/药品/食品/工具/文档/饰品/厨具/玩具/运动/其他 中选一个。
        seasons: 适用季节数组，取值只能是 春/夏/秋/冬；不适用填 []。
        color: 主色，单个词。
        brand: 仅当照片上文字清晰可读时才填，否则填 null。绝对不要猜测品牌。
        material: 能看出来的材质，不确定填 null。
        aliases: 0 到 3 个日后可能被用来搜它的口语别名。
        description: 不超过 60 字的客观外观描述。
        tags: 3 到 8 个检索标签，覆盖用途、场合、所处场景。
        container_guess: 这类物品通常收纳在什么容器里，单个词，例如 抽屉/收纳盒/衣柜/箱子/挂钩/货架/工具包。
        所有不确定的字段一律填 null，不要编造。
    """.trimIndent()
}
```

`brand` 明确禁止猜测：编造的品牌会让日后搜索命中一堆错项，比字段缺失更难发现。

- [x] **Step 5: 写 AnalysisParser**

`app/src/main/java/com/wheremy/app/data/vision/AnalysisParser.kt`：

```kotlin
package com.wheremy.app.data.vision

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

object AnalysisParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val FENCE = Regex("```(?:json|JSON)?\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
    private val OUTER_BRACES = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)

    fun parse(rawContent: String): AnalysisResult {
        val obj = extractObject(rawContent) ?: return AnalysisResult.EMPTY
        return AnalysisResult(
            name = obj.string("name"),
            category = obj.string("category"),
            seasons = obj.strings("seasons"),
            color = obj.string("color"),
            brand = obj.string("brand"),
            material = obj.string("material"),
            aliases = obj.strings("aliases"),
            description = obj.string("description"),
            tags = obj.strings("tags"),
            containerGuess = obj.string("container_guess"),
        )
    }

    /** 依次尝试围栏内 JSON、最外层 {...}、整段文本；任一解析成功即返回 */
    private fun extractObject(raw: String): JsonObject? {
        val candidates = listOfNotNull(
            FENCE.find(raw)?.groupValues?.getOrNull(1),
            OUTER_BRACES.find(raw)?.value,
            raw.trim().takeIf { it.isNotEmpty() },
        )
        for (candidate in candidates) {
            runCatching { json.parseToJsonElement(candidate).jsonObject }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
            ?: emptyList()
}
```

`contentOrNull` 对 `JsonNull` 返回 null，但对裸字面量 `"null"`（模型常这么写）返回字符串 `null`，所以那句 `!it.equals("null")` 是必要的。任一字段类型不对只丢该字段，不影响其它字段。

- [x] **Step 6: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*AnalysisParserTest" --console=plain`
Expected: 8 个测试 PASS。

- [x] **Step 7: 提交**

```bash
git add app/src
git commit -m "feat: AI 分析结果模型、中文 Prompt 与容错 JSON 解析"
```

---

### Task 7: PhotoAnalyzer 接口、API 实现与离线实现

**Files:**
- Create: `app/src/main/java/com/wheremy/app/data/vision/PhotoAnalyzer.kt`
- Create: `app/src/main/java/com/wheremy/app/data/vision/ApiPhotoAnalyzer.kt`
- Create: `app/src/main/java/com/wheremy/app/data/vision/OfflinePhotoAnalyzer.kt`
- Test: `app/src/test/java/com/wheremy/app/data/vision/ApiPhotoAnalyzerTest.kt`

**Interfaces:**
- Consumes: Task 6 的 `AnalysisParser`/`AnalysisResult`/`Prompt`、OkHttp、MockWebServer。
- Produces:
  - `data class VisionConfig(baseUrl: String, model: String, apiKey: String)`
  - `enum class FailureReason { NO_CONFIG, OFFLINE, TIMEOUT, UNAUTHORIZED, RATE_LIMITED, SERVER, BAD_RESPONSE }`
  - `sealed interface AnalysisOutcome { data class Success(result: AnalysisResult); data class Failed(reason: FailureReason) }`
  - `interface PhotoAnalyzer { suspend fun analyze(jpeg: ByteArray): AnalysisOutcome }`
  - `class ApiPhotoAnalyzer(client: OkHttpClient, configProvider: suspend () -> VisionConfig?) : PhotoAnalyzer`
  - `class OfflinePhotoAnalyzer : PhotoAnalyzer`

失败以 `AnalysisOutcome.Failed` 返回而不是抛异常：UI 层不需要 try/catch 网络，且能按 `FailureReason` 出可读文案。

- [x] **Step 1: 写接口与离线实现**

`app/src/main/java/com/wheremy/app/data/vision/PhotoAnalyzer.kt`：

```kotlin
package com.wheremy.app.data.vision

data class VisionConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

enum class FailureReason { NO_CONFIG, OFFLINE, TIMEOUT, UNAUTHORIZED, RATE_LIMITED, SERVER, BAD_RESPONSE }

sealed interface AnalysisOutcome {
    data class Success(val result: AnalysisResult) : AnalysisOutcome
    data class Failed(val reason: FailureReason) : AnalysisOutcome
}

interface PhotoAnalyzer {
    suspend fun analyze(jpeg: ByteArray): AnalysisOutcome
}
```

`app/src/main/java/com/wheremy/app/data/vision/OfflinePhotoAnalyzer.kt`：

```kotlin
package com.wheremy.app.data.vision

class OfflinePhotoAnalyzer : PhotoAnalyzer {
    override suspend fun analyze(jpeg: ByteArray): AnalysisOutcome =
        AnalysisOutcome.Failed(FailureReason.NO_CONFIG)
}
```

- [x] **Step 2: 写失败测试**

`app/src/test/java/com/wheremy/app/data/vision/ApiPhotoAnalyzerTest.kt`：

```kotlin
package com.wheremy.app.data.vision

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ApiPhotoAnalyzerTest {
    private lateinit var server: MockWebServer
    private val base by lazy { server.url("/compatible-mode/v1").toString() }

    private fun analyzer(
        timeoutSeconds: Long = 5,
        config: suspend () -> VisionConfig? = { VisionConfig(base, "qwen-vl-max", "sk-test") },
    ) = ApiPhotoAnalyzer(
        OkHttpClient.Builder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build(),
        config,
    )

    /** 把 content 包进 OpenAI 兼容响应体 */
    private fun completion(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"choices":[{"message":{"role":"assistant","content":"$escaped"}}]}"""
    }

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun postsOpenAiCompatibleBodyWithBase64Image() = runBlocking {
        server.enqueue(MockResponse().setBody(completion("""{"name":"护照"}""")))
        analyzer().analyze(byteArrayOf(1, 2, 3))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/chat/completions"))
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
        // toRequestBody 会补上 charset，服务端按媒体类型主值解析，所以只断言主值
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"model\":\"qwen-vl-max\""))
        assertTrue(body.contains("\"temperature\":0.1"))
        assertTrue(body.contains("\"max_tokens\":600"))
        assertTrue(body.contains("data:image/jpeg;base64,"))
        assertTrue("system prompt 应随请求发出", body.contains("家庭物品收纳助手"))
    }

    @Test fun successParsesContentIntoResult() = runBlocking {
        server.enqueue(MockResponse().setBody(completion("""{"name":"护照","tags":["证件"]}""")))
        val outcome = analyzer().analyze(byteArrayOf(1))
        assertTrue(outcome is AnalysisOutcome.Success)
        assertEquals("护照", (outcome as AnalysisOutcome.Success).result.name)
    }

    @Test fun maps401ToUnauthorizedWithoutRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(AnalysisOutcome.Failed(FailureReason.UNAUTHORIZED), analyzer().analyze(byteArrayOf(1)))
        assertEquals("鉴权失败不该重试", 1, server.requestCount)
    }

    @Test fun maps429ToRateLimitedWithExactlyOneRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(AnalysisOutcome.Failed(FailureReason.RATE_LIMITED), analyzer().analyze(byteArrayOf(1)))
        assertEquals("失败只重试一次", 2, server.requestCount)
    }

    @Test fun serverErrorThenSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(completion("""{"name":"钥匙"}""")))
        assertTrue(analyzer().analyze(byteArrayOf(1)) is AnalysisOutcome.Success)
        assertEquals(2, server.requestCount)
    }

    @Test fun emptyChoicesIsBadResponse() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setBody("""{"choices":[]}""")) }
        assertEquals(AnalysisOutcome.Failed(FailureReason.BAD_RESPONSE), analyzer().analyze(byteArrayOf(1)))
    }

    @Test fun unparseableContentIsBadResponse() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setBody(completion("我看不出这是什么"))) }
        assertEquals(AnalysisOutcome.Failed(FailureReason.BAD_RESPONSE), analyzer().analyze(byteArrayOf(1)))
    }

    @Test fun missingConfigShortCircuitsWithoutRequest() = runBlocking {
        val outcome = analyzer(config = { null }).analyze(byteArrayOf(1))
        assertEquals(AnalysisOutcome.Failed(FailureReason.NO_CONFIG), outcome)
        assertEquals(0, server.requestCount)
    }

    @Test fun callTimeoutBecomesFailedNotException() = runBlocking {
        // 服务端晚 300ms 才回头，客户端 100ms 就放弃；比"猜 1ms 一定超时"稳定
        repeat(2) {
            server.enqueue(
                MockResponse().setBody(completion("""{"name":"x"}"""))
                    .setHeadersDelay(300, TimeUnit.MILLISECONDS)
            )
        }
        val slow = ApiPhotoAnalyzer(
            OkHttpClient.Builder().callTimeout(100, TimeUnit.MILLISECONDS).build(),
            { VisionConfig(base, "qwen-vl-max", "sk-test") },
        )
        assertEquals(AnalysisOutcome.Failed(FailureReason.TIMEOUT), slow.analyze(byteArrayOf(1)))
    }

    @Test fun connectionFailureBecomesOfflineNotException() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        val outcome = analyzer().analyze(byteArrayOf(1))
        assertTrue(
            "网络异常必须以 Failed 返回，不能抛出",
            outcome is AnalysisOutcome.Failed && outcome.reason == FailureReason.OFFLINE,
        )
    }
}
```

原本这条用例写作 `callTimeout(1, TimeUnit.MILLISECONDS)` + 断言"OFFLINE 或 TIMEOUT 都算过"，
在本机全量跑（JVM 已热）时会在 `1ms 内跑完 localhost 往返` 那一侧翻车，变成 `Success` 而失败，
是个靠时序赌博的测试。实施时拆成上面两条确定性的：服务端 `setHeadersDelay(300ms)` 配客户端
`callTimeout(100ms)` 必然超时，`SocketPolicy.DISCONNECT_AT_START` 必然连接失败。
同时 `ApiPhotoAnalyzer` 的 catch 从 `java.net.SocketTimeoutException` 改成它的父类
`java.io.InterruptedIOException`：OkHttp 的 **callTimeout** 抛的是 `InterruptedIOException("timeout")`，
不是 `SocketTimeoutException`，原来的写法会让生产路径（`AppContainer` 只设 callTimeout 15s）把超时
误报成 OFFLINE「网络不可用」。见文末偏差记录第 8 条。

- [x] **Step 3: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*ApiPhotoAnalyzerTest" --console=plain`
Expected: 编译失败 `unresolved reference: ApiPhotoAnalyzer`。

- [x] **Step 4: 实现 ApiPhotoAnalyzer**

`app/src/main/java/com/wheremy/app/data/vision/ApiPhotoAnalyzer.kt`：

```kotlin
package com.wheremy.app.data.vision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Base64

class ApiPhotoAnalyzer(
    private val client: OkHttpClient,
    private val configProvider: suspend () -> VisionConfig?,
) : PhotoAnalyzer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(jpeg: ByteArray): AnalysisOutcome {
        val cfg = configProvider() ?: return AnalysisOutcome.Failed(FailureReason.NO_CONFIG)
        val url = cfg.baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildBody(cfg.model, jpeg)
        var last = AnalysisOutcome.Failed(FailureReason.SERVER) as AnalysisOutcome
        repeat(MAX_ATTEMPTS) {
            last = attempt(url, cfg.apiKey, body)
            if (last is AnalysisOutcome.Success || !isRetryable(last)) return last
        }
        return last
    }

    /** 鉴权与配置类错误重试没有意义，只有服务端/网络抖动值得再试一次 */
    private fun isRetryable(outcome: AnalysisOutcome): Boolean = when (outcome) {
        is AnalysisOutcome.Failed -> outcome.reason in RETRYABLE
        is AnalysisOutcome.Success -> false
    }

    private suspend fun attempt(url: String, apiKey: String, body: String): AnalysisOutcome =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when {
                        resp.code == 401 || resp.code == 403 -> AnalysisOutcome.Failed(FailureReason.UNAUTHORIZED)
                        resp.code == 429 -> AnalysisOutcome.Failed(FailureReason.RATE_LIMITED)
                        !resp.isSuccessful -> AnalysisOutcome.Failed(FailureReason.SERVER)
                        else -> parseCompletion(text)
                    }
                }
            } catch (_: InterruptedIOException) {
                AnalysisOutcome.Failed(FailureReason.TIMEOUT)
            } catch (_: IOException) {
                AnalysisOutcome.Failed(FailureReason.OFFLINE)
            }
        }

    private fun parseCompletion(payload: String): AnalysisOutcome {
        val content = runCatching {
            json.parseToJsonElement(payload)
                .jsonObject["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!
                .jsonPrimitive.content
        }.getOrNull() ?: return AnalysisOutcome.Failed(FailureReason.BAD_RESPONSE)

        val result = AnalysisParser.parse(content)
        return if (result.isEmpty) AnalysisOutcome.Failed(FailureReason.BAD_RESPONSE)
        else AnalysisOutcome.Success(result)
    }

    private fun buildBody(model: String, jpeg: ByteArray): String {
        val dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)
        return buildJsonObject {
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", 600)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", Prompt.SYSTEM) })
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject { put("type", "text"); put("text", "识别这张照片里的主要物品。") })
                        add(buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", dataUrl) }
                        })
                    }
                })
            }
        }.toString()
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        val JSON_MEDIA = "application/json".toMediaType()
        val RETRYABLE = setOf(
            FailureReason.SERVER,
            FailureReason.OFFLINE,
            FailureReason.TIMEOUT,
            FailureReason.RATE_LIMITED,
        )
    }
}
```

行为说明：`repeat` 里 `return last` 提前退出非重试路径；`kotlinx.serialization.json.put(String, Number)` 接受 `Double`，序列化输出 `0.1`，测试的 `"temperature":0.1` 断言依赖这一点。

- [x] **Step 5: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*ApiPhotoAnalyzerTest" --console=plain`
Expected: 9 个测试 PASS。

- [x] **Step 6: 提交**

```bash
git add app/build.gradle.kts app/src
git commit -m "feat: OpenAI 兼容多模态分析客户端与离线降级实现"
```

---

### Task 8: 分析用例、位置建议对齐与设置存储

**Files:**
- Create: `app/src/main/java/com/wheremy/app/domain/AnalyzePhotoUseCase.kt`
- Create: `app/src/main/java/com/wheremy/app/domain/SuggestLocationUseCase.kt`
- Create: `app/src/main/java/com/wheremy/app/data/settings/SettingsRepository.kt`
- Test: `app/src/test/java/com/wheremy/app/domain/AnalyzePhotoUseCaseTest.kt`
- Test: `app/src/test/java/com/wheremy/app/domain/SuggestLocationUseCaseTest.kt`

**Interfaces:**
- Consumes: Task 7 `PhotoAnalyzer`/`AnalysisOutcome`/`VisionConfig`，Task 4 `LocationTree`。
- Produces:
  - `class AnalyzePhotoUseCase(analyzer: PhotoAnalyzer) { suspend fun analyze(jpeg: ByteArray): Pair<AnalysisResult, FailureReason?> }`
  - `class SuggestLocationUseCase { fun suggest(tree: LocationTree, guess: String?): String? }`
  - `data class Settings(visionEnabled: Boolean, baseUrl: String, model: String, apiKey: String, reminderLeadDays: Int)`，`val hasUsableKey: Boolean`
  - `class SettingsRepository(ctx: Context)`：`suspend fun current(): Settings`、`suspend fun currentConfig(): VisionConfig?`、`suspend fun setApiKey(String)`、`setBaseUrl`、`setModel`、`setVisionEnabled`、`setReminderLeadDays`、`val changes: Flow<Settings>`

- [x] **Step 1: 写位置建议的失败测试**

`app/src/test/java/com/wheremy/app/domain/SuggestLocationUseCaseTest.kt`：

```kotlin
package com.wheremy.app.domain

import com.wheremy.app.data.db.LocationNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestLocationUseCaseTest {
    private fun node(id: String, parent: String?, name: String, kind: String? = null) =
        LocationNodeEntity(id, parent, name, kind, 0, 1L, 1L)

    private val tree = buildLocationTree(
        listOf(
            node("home", null, "家", "房屋"),
            node("desk", "home", "书桌抽屉", "抽屉"),
            node("box", "home", "蓝色收纳盒", "收纳盒"),
            node("closet", "home", "衣柜", "柜子"),
        )
    )
    private val useCase = SuggestLocationUseCase()

    @Test fun matchesKindExactly() = assertEquals("desk", useCase.suggest(tree, "抽屉"))

    @Test fun matchesNodeNameExactly() = assertEquals("closet", useCase.suggest(tree, "衣柜"))

    @Test fun partialWordFindsBest() = assertEquals("box", useCase.suggest(tree, "收纳"))

    @Test fun guessContainingKindMatches() = assertEquals("desk", useCase.suggest(tree, "抽屉柜"))

    @Test fun noMatchReturnsNullRatherThanInventing() = assertNull(useCase.suggest(tree, "保险柜"))

    @Test fun nullOrBlankGuessReturnsNull() {
        assertNull(useCase.suggest(tree, null))
        assertNull(useCase.suggest(tree, "   "))
    }

    @Test fun rootNodesAreNeverSuggested() {
        val single = buildLocationTree(listOf(node("home", null, "家", "房屋")))
        assertNull(useCase.suggest(single, "家"))
    }

    @Test fun sameScorePrefersShallowerNode() {
        val t = buildLocationTree(
            listOf(node("home", null, "家"), node("c1", "home", "柜子", "柜子"), node("c2", "c1", "内层柜子", "柜子"))
        )
        assertEquals("c1", useCase.suggest(t, "柜子"))
    }
}
```

同分取浅节点，是因为东西放进一级容器比塞进它的子容器更常见、也更显眼。

- [x] **Step 2: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*SuggestLocationUseCaseTest" --console=plain`
Expected: 编译失败 `unresolved reference: SuggestLocationUseCase`。

- [x] **Step 3: 实现 SuggestLocationUseCase**

`app/src/main/java/com/wheremy/app/domain/SuggestLocationUseCase.kt`：

```kotlin
package com.wheremy.app.domain

/**
 * 模型不知道用户家的结构，只能输出通用容器词（"抽屉"）。
 * 这里把它对齐到用户真实的树；对不上就返回 null，绝不虚构一个不存在的位置。
 */
class SuggestLocationUseCase {

    fun suggest(tree: LocationTree, guess: String?): String? {
        val g = guess?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        var bestId: String? = null
        var bestScore = 0
        var bestDepth = Int.MAX_VALUE

        walk(tree.roots().map { it.id }, tree) { node, depth ->
            if (node.parentId == null) return@walk
            val s = score(node, g)
            if (s > bestScore || (s == bestScore && s > 0 && depth < bestDepth)) {
                bestScore = s
                bestDepth = depth
                bestId = node.id
            }
        }
        return bestId
    }

    private fun score(node: LocationNode, guess: String): Int {
        val name = node.name.lowercase()
        val kind = node.kind?.lowercase()
        return when {
            kind == guess || name == guess -> 3
            kind != null && (kind.contains(guess) || guess.contains(kind)) -> 2
            name.contains(guess) || guess.contains(name) -> 1
            else -> 0
        }
    }

    /** 先序遍历并带 depth；seen 挡住脏数据造成的环 */
    private fun walk(
        startIds: List<String>,
        tree: LocationTree,
        seen: MutableSet<String> = HashSet(),
        block: (LocationNode, Int) -> Unit,
    ) {
        fun visit(id: String, depth: Int) {
            if (!seen.add(id)) return
            val node = tree.node(id) ?: return
            block(node, depth)
            tree.childrenOf(id).forEach { visit(it.id, depth + 1) }
        }
        startIds.forEach { visit(it, 0) }
    }
}
```

- [x] **Step 4: 写分析用例的失败测试**

`app/src/test/java/com/wheremy/app/domain/AnalyzePhotoUseCaseTest.kt`：

```kotlin
package com.wheremy.app.domain

import com.wheremy.app.data.vision.AnalysisOutcome
import com.wheremy.app.data.vision.AnalysisResult
import com.wheremy.app.data.vision.FailureReason
import com.wheremy.app.data.vision.PhotoAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyzePhotoUseCaseTest {
    private fun fake(outcome: AnalysisOutcome) = object : PhotoAnalyzer {
        override suspend fun analyze(jpeg: ByteArray) = outcome
    }

    @Test fun successPassesResultThrough() = runBlocking {
        val r = AnalysisResult(name = "护照", tags = listOf("证件"))
        val (result, failure) = AnalyzePhotoUseCase(fake(AnalysisOutcome.Success(r))).analyze(byteArrayOf(1))
        assertEquals(r, result)
        assertNull(failure)
    }

    @Test fun failureDowngradesToEmptyAndKeepsReason() = runBlocking {
        val (result, failure) = AnalyzePhotoUseCase(
            fake(AnalysisOutcome.Failed(FailureReason.OFFLINE))
        ).analyze(byteArrayOf(1))
        assertEquals(AnalysisResult.EMPTY, result)
        assertEquals(FailureReason.OFFLINE, failure)
    }

    @Test fun thrownExceptionIsDowngradedNotPropagated() = runBlocking {
        val crashing = object : PhotoAnalyzer {
            override suspend fun analyze(jpeg: ByteArray): AnalysisOutcome = throw IllegalStateException("boom")
        }
        val (result, failure) = AnalyzePhotoUseCase(crashing).analyze(byteArrayOf(1))
        assertEquals(AnalysisResult.EMPTY, result)
        assertEquals(FailureReason.BAD_RESPONSE, failure)
    }
}
```

- [x] **Step 5: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*AnalyzePhotoUseCaseTest" --console=plain`
Expected: 编译失败 `unresolved reference: AnalyzePhotoUseCase`。

- [x] **Step 6: 实现 AnalyzePhotoUseCase**

`app/src/main/java/com/wheremy/app/domain/AnalyzePhotoUseCase.kt`：

```kotlin
package com.wheremy.app.domain

import com.wheremy.app.data.vision.AnalysisOutcome
import com.wheremy.app.data.vision.AnalysisResult
import com.wheremy.app.data.vision.FailureReason
import com.wheremy.app.data.vision.PhotoAnalyzer

/** 返回 (结果, 失败原因)。失败原因非空时调用方照常让用户手填，不阻塞录入 */
class AnalyzePhotoUseCase(private val analyzer: PhotoAnalyzer) {

    suspend fun analyze(jpeg: ByteArray): Pair<AnalysisResult, FailureReason?> =
        try {
            when (val outcome = analyzer.analyze(jpeg)) {
                is AnalysisOutcome.Success -> outcome.result to null
                is AnalysisOutcome.Failed -> AnalysisResult.EMPTY to outcome.reason
            }
        } catch (_: Exception) {
            AnalysisResult.EMPTY to FailureReason.BAD_RESPONSE
        }
}
```

- [x] **Step 7: 实现 SettingsRepository**

`app/src/main/java/com/wheremy/app/data/settings/SettingsRepository.kt`：

```kotlin
package com.wheremy.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wheremy.app.data.vision.VisionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "wheremy_settings")

data class Settings(
    val visionEnabled: Boolean,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val reminderLeadDays: Int,
) {
    val hasUsableKey: Boolean
        get() = visionEnabled && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

class SettingsRepository(private val ctx: Context) {

    val changes: Flow<Settings> = ctx.settingsDataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = ctx.settingsDataStore.data.first().toSettings()

    /** 没配 key 或用户关掉 AI 时返回 null，容器据此选 OfflinePhotoAnalyzer */
    suspend fun currentConfig(): VisionConfig? {
        val s = current()
        return if (s.hasUsableKey) VisionConfig(s.baseUrl, s.model, s.apiKey) else null
    }

    suspend fun setVisionEnabled(value: Boolean) = edit { it[Keys.enabled] = value }
    suspend fun setBaseUrl(value: String) = edit { it[Keys.baseUrl] = value.trim() }
    suspend fun setModel(value: String) = edit { it[Keys.model] = value.trim() }
    suspend fun setApiKey(value: String) = edit { it[Keys.apiKey] = value.trim() }
    suspend fun setReminderLeadDays(value: Int) = edit { it[Keys.leadDays] = value.coerceIn(0, 90) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        ctx.settingsDataStore.edit(block)
    }

    private fun androidx.datastore.preferences.core.Preferences.toSettings() = Settings(
        visionEnabled = this[Keys.enabled] ?: true,
        baseUrl = this[Keys.baseUrl] ?: DEFAULT_BASE_URL,
        model = this[Keys.model] ?: DEFAULT_MODEL,
        apiKey = this[Keys.apiKey] ?: "",
        reminderLeadDays = this[Keys.leadDays] ?: 3,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        const val DEFAULT_MODEL = "qwen-vl-max"
    }

    private object Keys {
        val enabled = booleanPreferencesKey("vision_enabled")
        val baseUrl = stringPreferencesKey("base_url")
        val model = stringPreferencesKey("model")
        val apiKey = stringPreferencesKey("api_key")
        val leadDays = intPreferencesKey("reminder_lead_days")
    }
}
```

`DEFAULT_BASE_URL` 是阿里云百炼的 OpenAI 兼容端点；换豆包或智谱只需改 `baseUrl` 与 `model` 两个字段（spec 第 7 节）。API key 明文存 DataStore 的风险与控制见 spec 第 5 节，设置页必须把它展示给用户。

- [x] **Step 8: 跑全部单测**

Run: `gradle :app:testDebugUnitTest --console=plain`
Expected: 全绿。

- [x] **Step 9: 提交**

```bash
git add app/src
git commit -m "feat: AI 分析用例、位置建议本地对齐与设置存储"
```

---

### Task 9: ItemRepository（DRAFT → CONFIRMED 与查询）

**Files:**
- Create: `app/src/main/java/com/wheremy/app/data/repo/Models.kt`
- Create: `app/src/main/java/com/wheremy/app/data/repo/ItemRepository.kt`
- Test: `app/src/test/java/com/wheremy/app/data/repo/ItemRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2 DAO、Task 3 `SearchTextBuilder`/`SearchRanker`、Task 5 `PhotoStore`/`StoredPhoto`、Task 6 `AnalysisResult`、Task 4 `LocationTree`。
- Produces:
  - `data class ItemDraft(id, name, category, seasons: List<String>, color, brand, material, aliases: List<String>, description, notes, quantity, locationId, expiresAt, expireKind, tags: List<String>)`（除 `id` 外全部有默认值）
  - `data class Filters(category: String? = null, season: String? = null, tag: String? = null, onlyInbox: Boolean = false)`
  - `data class ItemSummary(item: ItemEntity, tags: List<String>, thumbPath: String?, locationPath: String?)`
  - `val CATEGORIES: List<String>`、`val SEASONS: List<String>`
  - `class ItemRepository(db: WheremyDatabase, val photoStore: PhotoStore)`：`suspend fun newDraft(jpeg: ByteArray): String`、`fun takePendingAnalysis(): ByteArray?`、`suspend fun applyAnalysis(itemId, AnalysisResult)`、`suspend fun draftOf(itemId): ItemDraft`、`suspend fun confirm(draft): String`、`suspend fun keepAsDraft(draft): String`、`suspend fun get(id): ItemEntity?`、`suspend fun rankedSearch(query, filters): List<ItemEntity>`、`suspend fun inbox()`、`suspend fun atLocation(id)`、`suspend fun summaries(items, tree): List<ItemSummary>`、`suspend fun move(itemId, locationId?)`、`suspend fun delete(itemId)`、`suspend fun tagsOf(id)`、`suspend fun thumbPathOf(id)`、`suspend fun photoBytesOf(id): ByteArray?`

- [x] **Step 1: 写共享模型**

`app/src/main/java/com/wheremy/app/data/repo/Models.kt`：

```kotlin
package com.wheremy.app.data.repo

import com.wheremy.app.data.db.ItemEntity

data class ItemDraft(
    val id: String,
    val name: String = "",
    val category: String? = null,
    val seasons: List<String> = emptyList(),
    val color: String? = null,
    val brand: String? = null,
    val material: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val notes: String? = null,
    val quantity: Int = 1,
    val locationId: String? = null,
    val expiresAt: Long? = null,
    val expireKind: String? = null,
    val tags: List<String> = emptyList(),
)

data class Filters(
    val category: String? = null,
    val season: String? = null,
    val tag: String? = null,
    val onlyInbox: Boolean = false,
)

data class ItemSummary(
    val item: ItemEntity,
    val tags: List<String>,
    val thumbPath: String?,
    val locationPath: String?,
)

val CATEGORIES = listOf(
    "衣物", "数码", "电器", "药品", "食品", "工具", "文档", "饰品", "厨具", "玩具", "运动", "其他",
)

val SEASONS = listOf("春", "夏", "秋", "冬")
```

- [x] **Step 2: 写失败测试**

`app/src/test/java/com/wheremy/app/data/repo/ItemRepositoryTest.kt`：

```kotlin
package com.wheremy.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.LocationNodeEntity
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.vision.AnalysisResult
import com.wheremy.app.domain.buildLocationTree
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ItemRepositoryTest {
    private lateinit var db: WheremyDatabase
    private lateinit var repo: ItemRepository

    private fun jpeg(w: Int = 900, h: Int = 600): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        return out.toByteArray()
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WheremyDatabase::class.java).build()
        repo = ItemRepository(db, PhotoStore(File(ctx.filesDir, "p").also { it.deleteRecursively() }))
        runBlocking {
            db.locationDao().upsert(LocationNodeEntity("home", null, "家", null, 0, 1L, 1L))
            db.locationDao().upsert(LocationNodeEntity("drawer", "home", "抽屉", "抽屉", 0, 1L, 1L))
        }
    }

    @Test fun newDraftPersistsRowAndPhotoFiles() = runBlocking {
        val id = repo.newDraft(jpeg())
        val item = repo.get(id)!!
        assertEquals(ItemStatus.DRAFT, item.status)
        assertNull(item.locationId)
        assertNotNull(repo.thumbPathOf(id))
        assertNotNull("分析用图应随 DRAFT 缓存下来", repo.takePendingAnalysis())
        assertNull("取过一次就应清空", repo.takePendingAnalysis())
    }

    @Test fun applyAnalysisFillsFieldsButStaysDraft() = runBlocking {
        val id = repo.newDraft(jpeg())
        repo.applyAnalysis(
            id, AnalysisResult(
                name = "深灰色羊毛外套", category = "衣物", seasons = listOf("秋", "冬"),
                aliases = listOf("厚外套"), tags = listOf("秋冬", "外出"), description = "翻领中长款",
            )
        )
        val e = repo.get(id)!!
        assertEquals(ItemStatus.DRAFT, e.status)
        assertEquals("深灰色羊毛外套", e.name)
        assertEquals("秋,冬", e.seasons)
        assertTrue(e.searchText.contains("厚外套"))
        assertEquals(listOf("外出", "秋冬"), repo.tagsOf(id))
    }

    @Test fun emptyAnalysisKeepsExistingFields() = runBlocking {
        val id = repo.newDraft(jpeg())
        repo.applyAnalysis(id, AnalysisResult(name = "护照"))
        repo.applyAnalysis(id, AnalysisResult.EMPTY)
        assertEquals("护照", repo.get(id)!!.name)
    }

    @Test fun confirmSetsStatusLocationTagsAndSearchText() = runBlocking {
        val id = repo.newDraft(jpeg())
        repo.confirm(
            repo.draftOf(id).copy(
                name = "打气筒", locationId = "drawer", notes = "气针在盖子里",
                tags = listOf("维修"), seasons = listOf("春"),
            )
        )
        val e = repo.get(id)!!
        assertEquals(ItemStatus.CONFIRMED, e.status)
        assertEquals("drawer", e.locationId)
        assertTrue(e.searchText.contains("气针在盖子里"))
        assertTrue(e.searchText.contains("维修"))
        assertEquals(listOf("维修"), repo.tagsOf(id))
    }

    @Test fun confirmRejectsBlankName() = runBlocking {
        val id = repo.newDraft(jpeg())
        val error = runCatching { repo.confirm(repo.draftOf(id).copy(name = "   ")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test fun confirmOnUnknownIdInsertsFreshRow() = runBlocking {
        repo.confirm(ItemDraft(id = "brand-new", name = "手工填的物品", locationId = "home"))
        val e = repo.get("brand-new")!!
        assertEquals(ItemStatus.CONFIRMED, e.status)
        assertTrue(e.createdAt > 0)
        assertNotNull(e.lastMovedAt)
    }

    @Test fun keepAsDraftClearsLocationSoItSurfacesInInbox() = runBlocking {
        val id = repo.newDraft(jpeg())
        repo.keepAsDraft(repo.draftOf(id).copy(name = "半成品", locationId = "drawer"))
        val e = repo.get(id)!!
        assertEquals(ItemStatus.DRAFT, e.status)
        assertNull(e.locationId)
        assertTrue(repo.inbox().map { it.id }.contains(id))
    }

    @Test fun rankedSearchPutsNameHitBeforeSubstringHit() = runBlocking {
        val a = repo.newDraft(jpeg())
        repo.confirm(repo.draftOf(a).copy(name = "外套收纳袋", locationId = "home"))
        val b = repo.newDraft(jpeg())
        repo.confirm(repo.draftOf(b).copy(name = "外套", locationId = "home"))
        assertEquals(listOf(b, a), repo.rankedSearch("外套", Filters()).map { it.id })
    }

    @Test fun filtersCombineCategoryAndSeason() = runBlocking {
        val a = repo.newDraft(jpeg())
        repo.confirm(repo.draftOf(a).copy(name = "跑鞋", category = "衣物", seasons = listOf("夏"), locationId = "home"))
        val b = repo.newDraft(jpeg())
        repo.confirm(repo.draftOf(b).copy(name = "棉服", category = "衣物", seasons = listOf("冬"), locationId = "home"))
        assertEquals(listOf(b), repo.rankedSearch("", Filters(category = "衣物", season = "冬")).map { it.id })
        assertEquals(2, repo.rankedSearch("", Filters(category = "衣物")).size)
        assertEquals(0, repo.rankedSearch("", Filters(category = "数码")).size)
    }

    @Test fun summariesCarryThumbAndBreadcrumb() = runBlocking {
        val id = repo.newDraft(jpeg())
        repo.confirm(repo.draftOf(id).copy(name = "护照", locationId = "drawer"))
        val tree = buildLocationTree(db.locationDao().all())
        val s = repo.summaries(listOf(repo.get(id)!!), tree).single()
        assertEquals("家 / 抽屉", s.locationPath)
        assertNotNull(s.thumbPath)
    }

    @Test fun moveUpdatesLocationAndStampsLastMovedAt() = runBlocking {
        val id = repo.newDraft(jpeg())
        assertNull(repo.get(id)!!.lastMovedAt)
        repo.move(id, "drawer")
        assertNotNull(repo.get(id)!!.lastMovedAt)
        assertEquals("drawer", repo.get(id)!!.locationId)
    }

    @Test fun deleteRemovesRowTagsAndFiles() = runBlocking {
        val id = repo.newDraft(jpeg())
        repo.confirm(repo.draftOf(id).copy(name = "废弃物", locationId = "home"))
        val thumb = repo.thumbPathOf(id)!!
        assertNotNull(repo.photoBytesOf(id))
        repo.delete(id)
        assertNull(repo.get(id))
        assertTrue(repo.tagsOf(id).isEmpty())
        assertFalse(repo.photoStore.file(thumb).exists())
    }

    @Test fun brokenJpegDoesNotCreateRow() = runBlocking {
        // 关阴影 BitmapFactory 的宽容模式，才代表真机解码失败那条分支。
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        val before = repo.rankedSearch("", Filters()).size
        val failed = runCatching { repo.newDraft(byteArrayOf(1, 2, 3)) }
        assertTrue(failed.isFailure)
        assertEquals(before, repo.rankedSearch("", Filters()).size)
    }
}
```

`brokenJpegDoesNotCreateRow` 锁住 spec 第 8 节"解码失败不入库"：`PhotoStore.write` 抛异常发生在插入 DB 行之前。
同 Task 5 的 `garbageBytesAreRejected`，必须先用 `ShadowBitmapFactory.setAllowInvalidImageData(false)`
关掉阴影的宽容模式——默认阴影会把 `[1,2,3]` 也"解码"成占位图，测试会假通过。别为了让这条测试过去而放宽
`PhotoStore` 的校验。

- [x] **Step 3: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*ItemRepositoryTest" --console=plain`
Expected: 编译失败 `unresolved reference: ItemRepository`。

- [x] **Step 4: 实现 ItemRepository**

`app/src/main/java/com/wheremy/app/data/repo/ItemRepository.kt`：

```kotlin
package com.wheremy.app.data.repo

import com.wheremy.app.data.db.ItemDao
import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.ItemTagEntity
import com.wheremy.app.data.db.PhotoDao
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.SearchTextBuilder
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.vision.AnalysisResult
import com.wheremy.app.domain.LocationTree
import com.wheremy.app.domain.SearchRanker
import java.io.File
import java.util.UUID

class ItemRepository(private val db: WheremyDatabase, val photoStore: PhotoStore) {

    private val itemDao: ItemDao = db.itemDao()
    private val photoDao: PhotoDao = db.photoDao()

    /** 本次 newDraft 生成的 1024 边长分析用图，省掉二次编码 */
    private var pendingAnalysis: ByteArray? = null

    fun takePendingAnalysis(): ByteArray? = pendingAnalysis.also { pendingAnalysis = null }

    /** 选定图片的瞬间就落库，之后任何中断都不会丢这件物品 */
    suspend fun newDraft(jpeg: ByteArray): String {
        val stored = photoStore.write(jpeg)
        pendingAnalysis = stored.analysisJpeg
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        itemDao.upsert(blankItem(id, now))
        photoDao.upsert(
            ItemPhotoEntity(
                id = UUID.randomUUID().toString(), ownerType = PhotoOwner.ITEM, ownerId = id,
                filePath = stored.originalPath, thumbPath = stored.thumbPath, sortOrder = 0,
                createdAt = now,
            )
        )
        return id
    }

    /** 模型明确给出的字段才覆盖；重复分析不会清空用户已手填的内容 */
    suspend fun applyAnalysis(itemId: String, a: AnalysisResult) {
        val cur = itemDao.byId(itemId) ?: return
        val merged = cur.copy(
            name = a.name ?: cur.name,
            category = a.category ?: cur.category,
            seasons = a.seasons.csv() ?: cur.seasons,
            color = a.color ?: cur.color,
            brand = a.brand ?: cur.brand,
            material = a.material ?: cur.material,
            aliases = a.aliases.csv() ?: cur.aliases,
            description = a.description ?: cur.description,
            updatedAt = System.currentTimeMillis(),
        )
        val tags = if (a.tags.isNotEmpty()) a.tags else itemDao.tagsFor(itemId)
        itemDao.upsert(merged.copy(searchText = searchTextOf(merged, tags)))
        if (a.tags.isNotEmpty()) {
            itemDao.clearTags(itemId)
            itemDao.insertTags(a.tags.distinct().map { ItemTagEntity(itemId, it) })
        }
    }

    suspend fun draftOf(itemId: String): ItemDraft {
        val e = itemDao.byId(itemId) ?: error("物品不存在：$itemId")
        return ItemDraft(
            id = e.id, name = e.name, category = e.category,
            seasons = e.seasons.splitList(), color = e.color, brand = e.brand,
            material = e.material, aliases = e.aliases.splitList(),
            description = e.description, notes = e.notes, quantity = e.quantity,
            locationId = e.locationId, expiresAt = e.expiresAt, expireKind = e.expireKind,
            tags = itemDao.tagsFor(e.id),
        )
    }

    suspend fun confirm(draft: ItemDraft): String {
        val name = draft.name.trim()
        require(name.isNotEmpty()) { "名称不能为空" }
        val now = System.currentTimeMillis()
        val existing = itemDao.byId(draft.id)
        val fields = fieldsOf(draft)

        val movedAt = when {
            existing == null -> if (draft.locationId != null) now else null
            existing.locationId != draft.locationId -> now
            else -> existing.lastMovedAt
        }

        val base = existing ?: blankItem(draft.id, now)
        val filled = fields.applyTo(base)
        itemDao.upsert(
            filled.copy(
                status = ItemStatus.CONFIRMED,
                lastMovedAt = movedAt,
                updatedAt = now,
                searchText = searchTextOf(filled, draft.tags),
            )
        )
        writeTags(draft.id, draft.tags)
        return draft.id
    }

    /** "稍后处理"：字段照样存下来，退回 DRAFT 出现在收件箱，位置不动（第二轮评审后改写） */
    suspend fun keepAsDraft(draft: ItemDraft): String {
        val cur = itemDao.byId(draft.id) ?: return draft.id
        val filled = reArm(cur, fieldsOf(draft).applyTo(cur)).copy(status = ItemStatus.DRAFT)
        itemDao.upsert(
            filled.copy(
                updatedAt = System.currentTimeMillis(),
                searchText = searchTextOf(filled, draft.tags),
            )
        )
        writeTags(draft.id, draft.tags)
        return draft.id
    }

    suspend fun get(id: String): ItemEntity? = itemDao.byId(id)

    suspend fun tagsOf(id: String): List<String> = itemDao.tagsFor(id)

    suspend fun thumbPathOf(id: String): String? =
        photoDao.forOwner(PhotoOwner.ITEM, id).firstOrNull()?.thumbPath

    /** 冷启动后的重新分析只能用 1600 原图，多花一点 token，行为仍然正确 */
    suspend fun photoBytesOf(id: String): ByteArray? =
        photoDao.forOwner(PhotoOwner.ITEM, id).firstOrNull()
            ?.let { runCatching { File(photoStore.rootDir, it.filePath).readBytes() }.getOrNull() }

    /** SQL 用 LIKE 取候选，内存按命中字段强弱排序（spec 第 3 节） */
    suspend fun rankedSearch(query: String, filters: Filters): List<ItemEntity> {
        val candidates = if (query.isBlank()) itemDao.all()
        else itemDao.searchLike("%${query.trim().lowercase()}%")
        return SearchRanker.rank(query, candidates.filter { matches(it, filters) })
    }

    suspend fun inbox(): List<ItemEntity> = itemDao.inbox()

    suspend fun atLocation(locationId: String): List<ItemEntity> = itemDao.atLocation(locationId)

    suspend fun summaries(items: List<ItemEntity>, tree: LocationTree): List<ItemSummary> =
        items.map { e ->
            ItemSummary(
                item = e,
                tags = itemDao.tagsFor(e.id),
                thumbPath = thumbPathOf(e.id),
                locationPath = e.locationId?.let { tree.breadcrumb(it).ifBlank { null } },
            )
        }

    suspend fun move(itemId: String, locationId: String?) =
        itemDao.moveTo(itemId, locationId, System.currentTimeMillis())

    /** 先删行再删文件：行没了文件成了孤儿也不会被当成有效物品（spec 第 8 节） */
    suspend fun delete(itemId: String) {
        val entity = itemDao.byId(itemId) ?: return
        val photos = photoDao.forOwner(PhotoOwner.ITEM, itemId)
        itemDao.delete(entity)
        itemDao.clearTags(itemId)
        photoDao.deleteForOwner(PhotoOwner.ITEM, itemId)
        photos.forEach {
            photoStore.delete(it.filePath)
            photoStore.delete(it.thumbPath)
        }
    }

    private fun blankItem(id: String, now: Long) = ItemEntity(
        id = id, name = "", category = null, seasons = null, color = null,
        brand = null, material = null, aliases = null, description = null, notes = null,
        quantity = 1, locationId = null, status = ItemStatus.DRAFT, expiresAt = null,
        expireKind = null, expireNotifiedAt = null, searchText = "",
        createdAt = now, updatedAt = now, lastMovedAt = null,
    )

    private suspend fun writeTags(itemId: String, tags: List<String>) {
        itemDao.clearTags(itemId)
        if (tags.isNotEmpty()) itemDao.insertTags(tags.distinct().map { ItemTagEntity(itemId, it) })
    }

    private fun fieldsOf(draft: ItemDraft) = Fields(
        name = draft.name.trim(),
        category = draft.category?.trim()?.ifBlank { null },
        seasons = draft.seasons.csv(),
        color = draft.color?.trim()?.ifBlank { null },
        brand = draft.brand?.trim()?.ifBlank { null },
        material = draft.material?.trim()?.ifBlank { null },
        aliases = draft.aliases.csv(),
        description = draft.description?.trim()?.ifBlank { null },
        notes = draft.notes?.trim()?.ifBlank { null },
        quantity = draft.quantity.coerceAtLeast(1),
        locationId = draft.locationId,
        expiresAt = draft.expiresAt,
        expireKind = draft.expireKind,
    )

    private fun searchTextOf(e: ItemEntity, tags: List<String>) = SearchTextBuilder.build(
        name = e.name, brand = e.brand, aliases = e.aliases,
        description = e.description, notes = e.notes, tags = tags,
    )

    private fun matches(e: ItemEntity, f: Filters): Boolean =
        (f.category == null || e.category == f.category) &&
            (f.season == null || e.seasons.splitList().contains(f.season)) &&
            (f.tag == null || e.searchText.contains(f.tag.lowercase())) &&
            (!f.onlyInbox || (e.locationId == null || e.status == ItemStatus.DRAFT))

    private fun String?.splitList(): List<String> =
        this?.split(",")?.map(String::trim)?.filter(String::isNotEmpty) ?: emptyList()

    private fun List<String>.csv(): String? = if (isEmpty()) null else joinToString(",")
}

/** 一次写入要覆盖的字段集合：confirm 与 keepAsDraft 共用，避免两处各写一遍 copy 链 */
private data class Fields(
    val name: String,
    val category: String?,
    val seasons: String?,
    val color: String?,
    val brand: String?,
    val material: String?,
    val aliases: String?,
    val description: String?,
    val notes: String?,
    val quantity: Int,
    val locationId: String?,
    val expiresAt: Long?,
    val expireKind: String?,
) {
    fun applyTo(e: ItemEntity): ItemEntity = e.copy(
        name = name, category = category, seasons = seasons, color = color,
        brand = brand, material = material, aliases = aliases,
        description = description, notes = notes, quantity = quantity,
        locationId = locationId, expiresAt = expiresAt, expireKind = expireKind,
    )
}
```

- [x] **Step 5: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*ItemRepositoryTest" --console=plain`
Expected: 13 个测试 PASS。

- [x] **Step 6: 提交**

```bash
git add app/src
git commit -m "feat: 物品仓储，DRAFT 到 CONFIRMED 全流程与筛选查询"
```

---

### Task 10: LocationRepository（节点增删改与删除约束）

**Files:**
- Modify: `app/src/main/java/com/wheremy/app/data/db/Daos.kt`（`ItemDao` 增加一条计数查询）
- Create: `app/src/main/java/com/wheremy/app/data/repo/LocationRepository.kt`
- Test: `app/src/test/java/com/wheremy/app/data/repo/LocationRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `LocationDao`/`ItemDao`/`LocationNodeEntity`/`WheremyDatabase`、Task 4 的 `buildLocationTree`/`LocationTree.descendantIds`。
- Produces:
  - `class LocationRepository(db: WheremyDatabase)`：
    - `suspend fun tree(): LocationTree`
    - `suspend fun nodes(): List<LocationNodeEntity>`
    - `suspend fun byId(id: String): LocationNodeEntity?`
    - `suspend fun add(parentId: String?, name: String, kind: String? = null): String`
    - `suspend fun rename(id: String, name: String)`
    - `suspend fun setKind(id: String, kind: String?)`
    - `suspend fun changeParent(id: String, newParentId: String?)`
    - `suspend fun itemCount(id: String): Int`
    - `suspend fun subtreeItemCount(id: String): Int`
    - `suspend fun delete(id: String)`
  - `class LocationNotEmptyException(childCount: Int, itemCount: Int) : Exception`
  - `val LOCATION_KINDS: List<String>`（spec 第 5 节 `kind` 的建议值，仍允许自由输入）

同级重名与"不许把节点挂进自己的子树"是位置树唯一的完整性约束；`parent_id` 被写坏时 Task 4 的 `roots()` 已把孤儿当根处理，这里不再重复防御。

- [x] **Step 1: 给 `ItemDao` 加计数查询**

`Daos.kt` 中 `ItemDao` 的 `@Delete suspend fun delete(item: ItemEntity)` 之前插入：

```kotlin
    @Query("SELECT COUNT(*) FROM item WHERE locationId = :locationId")
    suspend fun countAt(locationId: String): Int
```

- [x] **Step 2: 写失败测试**

`app/src/test/java/com/wheremy/app/data/repo/LocationRepositoryTest.kt`：

```kotlin
package com.wheremy.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.WheremyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocationRepositoryTest {
    private lateinit var db: WheremyDatabase
    private lateinit var repo: LocationRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WheremyDatabase::class.java).build()
        repo = LocationRepository(db)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun item(name: String, locationId: String?) = ItemEntity(
        id = name + "-" + System.nanoTime(), name = name, category = null, seasons = null,
        color = null, brand = null, material = null, aliases = null, description = null,
        notes = null, quantity = 1, locationId = locationId, status = ItemStatus.CONFIRMED,
        expiresAt = null, expireKind = null, expireNotifiedAt = null, searchText = name,
        createdAt = 1L, updatedAt = 1L, lastMovedAt = null,
    )

    @Test fun addRootAndChild() = runBlocking {
        val home = repo.add(null, "家")
        val wardrobe = repo.add(home, "衣柜", "衣柜")
        val tree = repo.tree()
        assertEquals(listOf(home), tree.roots().map { it.id })
        assertEquals(listOf(wardrobe), tree.childrenOf(home).map { it.id })
        assertEquals("家 / 衣柜", tree.breadcrumb(wardrobe))
        assertEquals("衣柜", repo.byId(wardrobe)?.kind)
    }

    @Test fun addRejectsBlankName() = runBlocking {
        assertTrue(runCatching { repo.add(null, "   ") }.isFailure)
    }

    @Test fun addRejectsDuplicateSiblingName() = runBlocking {
        val home = repo.add(null, "家")
        repo.add(home, "衣柜")
        assertTrue(runCatching { repo.add(home, " 衣柜 ") }.isFailure)
        // 不同父节点下可以同名
        assertNotNull(repo.add(null, "衣柜"))
    }

    @Test fun addAppendsSortOrderAfterSiblings() = runBlocking {
        val home = repo.add(null, "家")
        repo.add(home, "衣柜")
        val third = repo.add(home, "书架")
        assertEquals(2, repo.byId(third)?.sortOrder)
    }

    @Test fun renameKeepsChildrenAndRejectsClash() = runBlocking {
        val home = repo.add(null, "家")
        val a = repo.add(home, "主卧")
        repo.add(home, "次卧")
        repo.rename(a, "大卧室")
        assertEquals("大卧室", repo.byId(a)?.name)
        // 改名只换 name，两个子节点都还在
        assertEquals(2, repo.tree().childrenOf(home).size)
        assertTrue(runCatching { repo.rename(a, "次卧") }.isFailure)
    }

    @Test fun setKindOverwrites() = runBlocking {
        val id = repo.add(null, "储物间")
        repo.setKind(id, "房间")
        assertEquals("房间", repo.byId(id)?.kind)
        repo.setKind(id, null)
        assertNull(repo.byId(id)?.kind)
    }

    @Test fun changeParentRejectsSelfAndDescendant() = runBlocking {
        val a = repo.add(null, "家")
        val b = repo.add(a, "主卧")
        val c = repo.add(b, "抽屉")
        assertTrue(runCatching { repo.changeParent(a, a) }.isFailure)
        assertTrue(runCatching { repo.changeParent(a, c) }.isFailure)
        assertTrue(runCatching { repo.changeParent(a, "missing") }.isFailure)
        // 拒绝之后树结构没被破坏
        assertEquals(listOf(a), repo.tree().roots().map { it.id })
        assertEquals(listOf(b), repo.tree().childrenOf(a).map { it.id })
    }

    @Test fun changeParentRehangsAndDetaches() = runBlocking {
        val home = repo.add(null, "家")
        val work = repo.add(null, "公司")
        val shelf = repo.add(home, "货架")
        repo.changeParent(shelf, work)
        assertEquals(listOf(shelf), repo.tree().childrenOf(work).map { it.id })
        assertTrue(repo.tree().childrenOf(home).isEmpty())
        repo.changeParent(shelf, null)
        assertTrue(repo.tree().roots().any { it.id == shelf })
    }

    @Test fun changeParentRejectsDuplicateNameInTarget() = runBlocking {
        val home = repo.add(null, "家")
        val work = repo.add(null, "公司")
        repo.add(home, "柜子")
        val drawer = repo.add(work, "柜子")
        assertTrue(runCatching { repo.changeParent(drawer, home) }.isFailure)
    }

    @Test fun deleteRejectsWhenNotEmpty() = runBlocking {
        val home = repo.add(null, "家")
        val room = repo.add(home, "主卧")
        val drawer = repo.add(room, "抽屉")
        db.itemDao().upsert(item("外套", room))
        assertTrue("有子位置就不能删", runCatching { repo.delete(home) }.isFailure)
        assertTrue("有物品就不能删", runCatching { repo.delete(room) }.isFailure)
        assertTrue("空叶子可以删", runCatching { repo.delete(drawer) }.isSuccess)
        assertNull(repo.byId(drawer))
    }

    @Test fun deleteRejectsWhenOnlyItemCounted() = runBlocking {
        val home = repo.add(null, "家")
        db.itemDao().upsert(item("电钻", home))
        val err = runCatching { repo.delete(home) }.exceptionOrNull()
        assertTrue(err is LocationNotEmptyException)
        assertEquals(1, (err as LocationNotEmptyException).itemCount)
        assertEquals(0, err.childCount)
    }

    @Test fun deleteEmptyNodeAndCountsSubtree() = runBlocking {
        val home = repo.add(null, "家")
        val room = repo.add(home, "主卧")
        val drawer = repo.add(room, "抽屉")
        val keys = item("钥匙", drawer)
        db.itemDao().upsert(keys)
        assertEquals(1, repo.itemCount(drawer))
        assertEquals(0, repo.itemCount(room))
        assertEquals(1, repo.subtreeItemCount(room))
        assertEquals(1, repo.subtreeItemCount(home))
        // 先把物品搬走，空节点才允许删除
        db.itemDao().delete(keys)
        repo.delete(drawer)
        assertNull(repo.byId(drawer))
    }
}
```

原计划的这三处断言与实现不自洽，实施时按上面的版本纠正：`renameKeepsChildrenAndRejectsClash`
期望改名后 `childrenOf(home)` 还剩 2 个（原写 1，改名不会删子节点）；`deleteRejectsWhenNotEmpty`
原样例把物品放在 `room` 里却断言 `delete(room)` 成功（非空必拒）；`deleteEmptyNodeAndCountsSubtree`
原样例在 `drawer` 里还有物品的情况下直接 `delete(drawer)`，同样会被非空保护拒绝。三条都是测试写错、
实现没错，没有放宽仓储的约束。

- [x] **Step 3: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*LocationRepositoryTest" --console=plain`
Expected: 编译失败，`LocationRepository` 与 `LocationNotEmptyException` 未定义。

- [x] **Step 4: 实现 LocationRepository**

`app/src/main/java/com/wheremy/app/data/repo/LocationRepository.kt`：

```kotlin
package com.wheremy.app.data.repo

import com.wheremy.app.data.db.ItemDao
import com.wheremy.app.data.db.LocationDao
import com.wheremy.app.data.db.LocationNodeEntity
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.domain.LocationTree
import com.wheremy.app.domain.buildLocationTree
import java.util.UUID

/** spec 第 5 节 kind 的自由标签建议值；输入框允许填这些之外的任意词 */
val LOCATION_KINDS = listOf("房间", "衣柜", "抽屉", "收纳盒", "箱子", "挂钩", "货架", "其他")

class LocationNotEmptyException(val childCount: Int, val itemCount: Int) : Exception(
    "该位置下还有 $childCount 个子位置和 $itemCount 件物品，请先搬空再删除"
)

class LocationRepository(db: WheremyDatabase) {

    private val locationDao: LocationDao = db.locationDao()
    private val itemDao: ItemDao = db.itemDao()

    suspend fun tree(): LocationTree = buildLocationTree(nodes())

    suspend fun nodes(): List<LocationNodeEntity> = locationDao.all()

    suspend fun byId(id: String): LocationNodeEntity? = locationDao.byId(id)

    suspend fun add(parentId: String?, name: String, kind: String? = null): String {
        val trimmed = requireName(name)
        requireNoSiblingNamed(trimmed, parentId, excludeId = null)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        locationDao.upsert(
            LocationNodeEntity(
                id = id, parentId = parentId, name = trimmed,
                kind = kind?.trim()?.ifBlank { null },
                sortOrder = nextSortOrder(parentId), createdAt = now, updatedAt = now,
            )
        )
        return id
    }

    suspend fun rename(id: String, name: String) {
        val node = locationDao.byId(id) ?: return
        val trimmed = requireName(name)
        requireNoSiblingNamed(trimmed, node.parentId, excludeId = id)
        locationDao.upsert(node.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setKind(id: String, kind: String?) {
        val node = locationDao.byId(id) ?: return
        locationDao.upsert(
            node.copy(kind = kind?.trim()?.ifBlank { null }, updatedAt = System.currentTimeMillis())
        )
    }

    /** 换父节点时把它挪到目标层级的末尾，Plan 1 不提供同级手动排序 */
    suspend fun changeParent(id: String, newParentId: String?) {
        val node = locationDao.byId(id) ?: return
        if (node.parentId == newParentId) return
        if (newParentId != null) {
            require(newParentId != id) { "不能把位置移动到它自己下面" }
            require(locationDao.byId(newParentId) != null) { "目标位置不存在" }
            require(newParentId !in tree().descendantIds(id)) { "不能把位置移动到它的子位置下面" }
        }
        requireNoSiblingNamed(node.name, newParentId, excludeId = id)
        locationDao.upsert(
            node.copy(
                parentId = newParentId, sortOrder = nextSortOrder(newParentId, excludeId = id),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun itemCount(id: String): Int = itemDao.countAt(id)

    /** 照片墙"含子位置"计数的唯一来源，树已在内存里，不必递归 SQL */
    suspend fun subtreeItemCount(id: String): Int {
        val ids = tree().descendantIds(id)
        return itemDao.all().count { it.locationId in ids }
    }

    suspend fun delete(id: String) {
        val node = locationDao.byId(id) ?: return
        val children = locationDao.all().count { it.parentId == id }
        val items = itemDao.countAt(id)
        if (children > 0 || items > 0) throw LocationNotEmptyException(children, items)
        locationDao.delete(node)
    }

    private fun requireName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "位置名称不能为空" }
        return trimmed
    }

    private suspend fun requireNoSiblingNamed(name: String, parentId: String?, excludeId: String?) {
        val clash = locationDao.all().any {
            it.id != excludeId && it.parentId == parentId && it.name == name
        }
        require(!clash) { "同一层级下已有「$name」" }
    }

    private suspend fun nextSortOrder(parentId: String?, excludeId: String? = null): Int {
        val siblings = locationDao.all().filter { it.parentId == parentId && it.id != excludeId }
        return (siblings.maxOfOrNull { it.sortOrder } ?: 0) + 1
    }
}
```

- [x] **Step 5: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*LocationRepositoryTest" --console=plain`
Expected: 12 个测试 PASS。

- [x] **Step 6: 提交**

```bash
git add app/src
git commit -m "feat: 位置仓储，重名与环检测约束及非空删除保护"
```

---

### Task 11: AppContainer 与 ViewModel 工厂接线

**Files:**
- Create: `app/src/main/java/com/wheremy/app/WheremyApp.kt`
- Create: `app/src/main/java/com/wheremy/app/di/AppContainer.kt`
- Create: `app/src/main/java/com/wheremy/app/di/AppViewModel.kt`
- Modify: `app/src/main/AndroidManifest.xml`（`<application android:name=".WheremyApp">`）
- Test: `app/src/test/java/com/wheremy/app/di/AppContainerTest.kt`

**Interfaces:**
- Consumes: Task 2 `WheremyDatabase.create`、Task 5 `PhotoStore`、Task 7 `ApiPhotoAnalyzer`、Task 8 `SettingsRepository`/`AnalyzePhotoUseCase`/`SuggestLocationUseCase`、Task 9 `ItemRepository`、Task 10 `LocationRepository`。
- Produces:
  - `class WheremyApp : Application` — `lateinit var container: AppContainer private set`
  - `class AppContainer(appContext: Context)` — `appContext` / `db` / `photoStore` / `settings` / `items: ItemRepository` / `locations: LocationRepository` / `httpClient` / `photoAnalyzer` / `analyzePhoto` / `suggestLocation`，全部 `by lazy`
  - `@Composable inline fun <reified VM : ViewModel> appViewModel(container: AppContainer, key: String? = null, noinline make: (AppContainer) -> VM): VM`
  - `val LocalAppContainer: ProvidableCompositionLocal<AppContainer>`

依赖用手写的 `AppContainer` 而不是 Hilt（spec 第 3 节）：全部字段 `by lazy`，单测里构造 `AppContainer(ctx)` 就能拿到真数据库 + 真 PhotoStore，不需要任何 DI 框架。

- [x] **Step 1: 写 `AppContainer`**

`app/src/main/java/com/wheremy/app/di/AppContainer.kt`：

```kotlin
package com.wheremy.app.di

import android.content.Context
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.data.vision.ApiPhotoAnalyzer
import com.wheremy.app.domain.AnalyzePhotoUseCase
import com.wheremy.app.domain.SuggestLocationUseCase
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AppContainer(appContext: Context) {

    val appContext: Context = appContext.applicationContext

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val db: WheremyDatabase by lazy { WheremyDatabase.create(appContext) }

    val photoStore: PhotoStore by lazy { PhotoStore(File(appContext.filesDir, "photos")) }

    val items: ItemRepository by lazy { ItemRepository(db, photoStore) }

    val locations: LocationRepository by lazy { LocationRepository(db) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 未配 key 或用户关掉 AI 时 configProvider 返回 null，ApiPhotoAnalyzer 直接给 NO_CONFIG，
     * 行为与 OfflinePhotoAnalyzer 一致，所以运行时不需要在两个实现之间切换（spec 第 5 节）。
     * OfflinePhotoAnalyzer 留给无网络的调试构建与单测注入。
     */
    val photoAnalyzer: ApiPhotoAnalyzer by lazy {
        ApiPhotoAnalyzer(httpClient) { settings.currentConfig() }
    }

    val analyzePhoto: AnalyzePhotoUseCase by lazy { AnalyzePhotoUseCase(photoAnalyzer) }

    val suggestLocation: SuggestLocationUseCase by lazy { SuggestLocationUseCase() }

    companion object {
        const val CALL_TIMEOUT_SECONDS = 15L
    }
}
```

- [x] **Step 2: 写 `WheremyApp`**

`app/src/main/java/com/wheremy/app/WheremyApp.kt`：

```kotlin
package com.wheremy.app

import android.app.Application
import com.wheremy.app.di.AppContainer

class WheremyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

`AndroidManifest.xml` 的 `<application>` 起始标签补上 `android:name=".WheremyApp"`，其余属性不动。

- [x] **Step 3: 写 ViewModel 接线助手**

`app/src/main/java/com/wheremy/app/di/AppViewModel.kt`：

```kotlin
package com.wheremy.app.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

val LocalAppContainer: ProvidableCompositionLocal<AppContainer> =
    staticCompositionLocalOf { error("AppContainer 尚未提供") }

/** 每个屏幕写 appViewModel(container) { XxxViewModel(it) }，不引入 Hilt 也不手写枚举工厂 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    container: AppContainer,
    key: String? = null,
    noinline make: (AppContainer) -> VM,
): VM {
    val owner = LocalViewModelStoreOwner.current ?: error("缺少 ViewModelStoreOwner")
    return viewModel(
        viewModelStoreOwner = owner,
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = make(container) as T
        },
    )
}
```

`key` 是给 Task 16 用的：录入页每次进入都要一个干净的 ViewModel（`ReviewViewModel` 内部有 `started`/`touched` 一次性状态），外壳每次进入时自增一个序号当 key。

- [x] **Step 4: 写容器测试**

`app/src/test/java/com/wheremy/app/di/AppContainerTest.kt`：

```kotlin
package com.wheremy.app.di

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.vision.AnalysisOutcome
import com.wheremy.app.data.vision.FailureReason
import com.wheremy.app.data.repo.ItemDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppContainerTest {

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getDatabasePath(WheremyDatabase.NAME).delete()
        File(ctx.filesDir, "photos").deleteRecursively()
        container = AppContainer(ctx)
    }

    @Test fun repositoriesShareOneDatabase() = runBlocking {
        val home = container.locations.add(null, "家")
        val jpeg = jpeg(600, 400)
        val id = container.items.newDraft(jpeg)
        container.items.confirm(ItemDraft(id = id, name = "电钻", locationId = home))
        assertEquals(1, container.locations.itemCount(home))
        assertEquals(listOf("电钻"), container.items.atLocation(home).map { it.name })
        assertTrue(container.items.inbox().isEmpty())
    }

    @Test fun lazyMembersAreSingletons() {
        assertTrue(container.items === container.items)
        assertTrue(container.db === container.db)
        assertTrue(container.photoAnalyzer === container.photoAnalyzer)
    }

    @Test fun analyzerReportsNoConfigWithoutKey() = runBlocking {
        assertNull(container.settings.currentConfig())
        val outcome = container.photoAnalyzer.analyze(jpeg(64, 64))
        assertTrue(outcome is AnalysisOutcome.Failed)
        assertEquals(FailureReason.NO_CONFIG, (outcome as AnalysisOutcome.Failed).reason)
    }

    @Test fun settingsDefaultsAreBailianQwen() = runBlocking {
        val s = container.settings.current()
        assertTrue(s.visionEnabled)
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", s.baseUrl)
        assertEquals("qwen-vl-max", s.model)
        assertEquals("", s.apiKey)
        assertEquals(3, s.reminderLeadDays)
        assertEquals(false, s.hasUsableKey)
    }

    private fun jpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            return bos.toByteArray()
        }
    }
}
```

- [x] **Step 5: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*AppContainerTest" --console=plain`
Expected: 4 个测试 PASS。

- [x] **Step 6: 跑全量单测并构建**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: 全绿且产出 `app/build/outputs/apk/debug/app-debug.apk`。

本步第一次跑的时候把 Task 7 那条 `callTimeout(1ms)` 的赌博式用例照出来了（单跑过、全量热身后翻车），
已按 Task 7 末尾的说明换成确定性的超时/断连两条。全量 92 条 PASS，APK 13.7 MB。

- [x] **Step 7: 提交**

```bash
git add app/src app/src/main/AndroidManifest.xml
git commit -m "feat: AppContainer 手写依赖图与 ViewModel 接线"
```

---

### Task 12: CaptureFlow（选图 → 落库 → AI → 位置建议）

**Files:**
- Create: `app/src/main/java/com/wheremy/app/domain/CaptureFlow.kt`
- Modify: `app/src/main/java/com/wheremy/app/di/AppContainer.kt`（加 `captureFlow`）
- Test: `app/src/test/java/com/wheremy/app/domain/CaptureFlowTest.kt`

**Interfaces:**
- Consumes: Task 9 `ItemRepository.newDraft`/`takePendingAnalysis`/`applyAnalysis`/`draftOf`/`photoBytesOf`、Task 10 `LocationRepository.tree`、Task 8 `AnalyzePhotoUseCase`/`SuggestLocationUseCase`、Task 7 `FailureReason`。
- Produces:
  - `data class CaptureOutcome(itemId: String, draft: ItemDraft, containerGuess: String?, suggestedLocationId: String?, failure: FailureReason?)`
  - `class CaptureFlow(items: ItemRepository, locations: LocationRepository, analyze: AnalyzePhotoUseCase, suggest: SuggestLocationUseCase)` — `suspend fun capture(jpeg: ByteArray): CaptureOutcome`、`suspend fun reanalyze(itemId: String): CaptureOutcome?`

这一段是 spec 第 6.1 节的完整闭环，全部逻辑放在可测的普通类里；Compose 只做状态展示（Task 13、14）。

- [x] **Step 1: 写失败测试**

`app/src/test/java/com/wheremy/app/domain/CaptureFlowTest.kt`：

```kotlin
package com.wheremy.app.domain

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.vision.AnalysisOutcome
import com.wheremy.app.data.vision.AnalysisResult
import com.wheremy.app.data.vision.FailureReason
import com.wheremy.app.data.vision.PhotoAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CaptureFlowTest {

    private class FakeAnalyzer(var outcome: AnalysisOutcome) : PhotoAnalyzer {
        var received: ByteArray? = null
        var calls = 0
        override suspend fun analyze(jpeg: ByteArray): AnalysisOutcome {
            calls++
            received = jpeg
            return outcome
        }
    }

    private lateinit var db: WheremyDatabase
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository
    private lateinit var analyzer: FakeAnalyzer
    private lateinit var flow: CaptureFlow

    private fun result(
        name: String? = "深灰色羊毛外套",
        containerGuess: String? = null,
    ) = AnalysisResult(
        name = name, category = "衣物", seasons = listOf("秋", "冬"), color = "深灰",
        brand = null, material = "羊毛", aliases = listOf("厚外套"),
        description = "中长款翻领", tags = listOf("秋冬", "通勤"), containerGuess = containerGuess,
    )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WheremyDatabase::class.java).build()
        val dir = File(ctx.filesDir, "cap-" + System.nanoTime()).also { it.mkdirs() }
        items = ItemRepository(db, PhotoStore(dir))
        locations = LocationRepository(db)
        analyzer = FakeAnalyzer(AnalysisOutcome.Failed(FailureReason.NO_CONFIG))
        flow = CaptureFlow(items, locations, AnalyzePhotoUseCase(analyzer), SuggestLocationUseCase())
    }

    @Test fun capturePersistsDraftEvenWhenAnalysisFails() = runBlocking {
        val out = flow.capture(jpeg(1600, 1200))
        assertEquals(FailureReason.NO_CONFIG, out.failure)
        val stored = items.get(out.itemId)
        assertEquals(ItemStatus.DRAFT, stored?.status)
        assertTrue(out.draft.name.isEmpty())
        assertNotNull(items.thumbPathOf(out.itemId))
    }

    @Test fun capturePrefillsFieldsFromAnalysis() = runBlocking {
        analyzer.outcome = AnalysisOutcome.Success(result())
        val out = flow.capture(jpeg(1600, 1200))
        assertNull(out.failure)
        assertEquals("深灰色羊毛外套", out.draft.name)
        assertEquals("衣物", out.draft.category)
        assertEquals(listOf("秋", "冬"), out.draft.seasons)
        assertEquals(listOf("秋冬", "通勤"), out.draft.tags)
        assertEquals("深灰色羊毛外套", items.get(out.itemId)?.name)
    }

    @Test fun captureUsesDownscaledImageNotOriginal() = runBlocking {
        analyzer.outcome = AnalysisOutcome.Success(result())
        val input = jpeg(1600, 1200)
        flow.capture(input)
        val sent = analyzer.received!!
        assertTrue(sent.isNotEmpty())
        assertTrue("应发送 1024 边长版本，实际 ${sent.size} vs 原图 ${input.size}", sent.size < input.size)
    }

    @Test fun capturePrefersSuggestedLocationIntoDraft() = runBlocking {
        val home = locations.add(null, "家")
        val wardrobe = locations.add(home, "衣柜顶层", "衣柜")
        analyzer.outcome = AnalysisOutcome.Success(result(containerGuess = "衣柜"))
        val out = flow.capture(jpeg(900, 600))
        assertEquals("衣柜", out.containerGuess)
        assertEquals(wardrobe, out.suggestedLocationId)
        assertEquals(wardrobe, out.draft.locationId)
    }

    @Test fun captureLeavesLocationEmptyWhenGuessMatchesNothing() = runBlocking {
        locations.add(null, "家")
        analyzer.outcome = AnalysisOutcome.Success(result(containerGuess = "工具箱"))
        val out = flow.capture(jpeg(900, 600))
        assertEquals("工具箱", out.containerGuess)
        assertNull(out.suggestedLocationId)
        assertNull(out.draft.locationId)
        assertTrue(items.inbox().any { it.id == out.itemId })
    }

    @Test fun reanalyzeKeepsItemIdAndUsesStoredPhoto() = runBlocking {
        analyzer.outcome = AnalysisOutcome.Failed(FailureReason.OFFLINE)
        val first = flow.capture(jpeg(1200, 900))
        assertTrue(items.get(first.itemId)!!.name.isEmpty())
        analyzer.outcome = AnalysisOutcome.Success(result())
        val again = flow.reanalyze(first.itemId)
        assertNotNull(again)
        assertEquals(first.itemId, again!!.itemId)
        assertEquals("深灰色羊毛外套", again.draft.name)
        // 重新分析不改 status，仍然由用户点确认才转 CONFIRMED
        assertEquals(ItemStatus.DRAFT, items.get(first.itemId)?.status)
    }

    @Test fun reanalyzeReturnsNullWhenNoPhoto() = runBlocking {
        assertNull(flow.reanalyze("missing"))
    }

    private fun jpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos)
            return bos.toByteArray()
        }
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "*CaptureFlowTest" --console=plain`
Expected: 编译失败，`CaptureFlow` 未定义。

- [x] **Step 3: 实现 CaptureFlow**

`app/src/main/java/com/wheremy/app/domain/CaptureFlow.kt`：

```kotlin
package com.wheremy.app.domain

import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.vision.FailureReason

data class CaptureOutcome(
    val itemId: String,
    val draft: ItemDraft,
    val containerGuess: String?,
    val suggestedLocationId: String?,
    val failure: FailureReason?,
)

/** spec 第 6.1 节：先落库再分析，任何一步失败都只是少一些预填，不会丢物品 */
class CaptureFlow(
    private val items: ItemRepository,
    private val locations: LocationRepository,
    private val analyze: AnalyzePhotoUseCase,
    private val suggest: SuggestLocationUseCase,
) {

    suspend fun capture(jpeg: ByteArray): CaptureOutcome {
        val itemId = items.newDraft(jpeg)
        return run(itemId, items.takePendingAnalysis() ?: jpeg)
    }

    /** 详情页"重新分析"：冷启动后 1024 版本已丢，只能用原图，代价是多一点 token */
    suspend fun reanalyze(itemId: String): CaptureOutcome? {
        val bytes = items.photoBytesOf(itemId) ?: return null
        return run(itemId, bytes)
    }

    private suspend fun run(itemId: String, bytes: ByteArray): CaptureOutcome {
        val (result, failure) = analyze.analyze(bytes)
        if (!result.isEmpty) items.applyAnalysis(itemId, result)
        val draft = items.draftOf(itemId)
        val suggested = suggest.suggest(locations.tree(), result.containerGuess)
            ?.takeIf { draft.locationId == null }
        return CaptureOutcome(
            itemId = itemId,
            draft = draft.copy(locationId = draft.locationId ?: suggested),
            containerGuess = result.containerGuess,
            suggestedLocationId = suggested,
            failure = failure,
        )
    }
}
```

原计划的这段 import 少了 `com.wheremy.app.data.repo.ItemDraft`，而 `CaptureOutcome.draft` 与
`draft.copy(...)` 都在 `data.repo` 包里，直接抄会 `unresolved reference: ItemDraft`；已补在上面。

- [x] **Step 4: 容器接上 CaptureFlow**

`AppContainer.kt` 末尾 `companion object` 之前插入，并补 `import com.wheremy.app.domain.CaptureFlow`：

```kotlin
    val captureFlow: CaptureFlow by lazy {
        CaptureFlow(items, locations, analyzePhoto, suggestLocation)
    }
```

- [x] **Step 5: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*CaptureFlowTest" --console=plain`
Expected: 7 个测试 PASS。

- [x] **Step 6: 提交**

```bash
git add app/src
git commit -m "feat: 录入闭环用例，AI 预填与位置建议对齐"
```

---

### Task 13: 主题与确认页（M3 录入闭环的页面层）

**Files:**
- Create: `app/src/main/java/com/wheremy/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/common/LocationFlatten.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/common/LocationPicker.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/common/UiFormat.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/review/ReviewViewModel.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/review/ReviewScreen.kt`
- Modify: `app/src/main/java/com/wheremy/app/di/AppContainer.kt`（加 `pendingCapture`）
- Modify: `app/src/main/java/com/wheremy/app/data/repo/Models.kt`（加 `EXPIRE_KINDS`，和 `CATEGORIES`/`SEASONS` 同文件；原计划写的 `domain/Models.kt` 并不存在）
- Test: `app/src/test/java/com/wheremy/app/ui/review/ReviewViewModelTest.kt`

**Interfaces:**
- Consumes: Task 12 `CaptureFlow`、Task 9 `ItemRepository`/`ItemDraft`/`CATEGORIES`/`SEASONS`、Task 10 `LocationRepository`/`LOCATION_KINDS`、Task 4 `LocationTree`、Task 11 `AppContainer`/`appViewModel`/`LocalAppContainer`。
- Produces:
  - `@Composable fun WheremyTheme(content: @Composable () -> Unit)`
  - `data class LocationOption(val id: String, val label: String, val depth: Int)` + `fun flattenLocations(tree: LocationTree): List<LocationOption>`
  - `fun formatDate(Long): String`、`fun daysUntil(Long, today: LocalDate = now()): Int`、`fun localDateOf(Long): LocalDate`、`fun utcPickerToLocalMillis(Long): Long`、`fun localMillisToUtcPicker(Long?): Long?`、`fun failureText(FailureReason?): String?`
  - `class ReviewViewModel(capture, items, locations) : ViewModel` — `state: StateFlow<ReviewUiState>`（含 `itemId`/`draft`/`analyzing`/`failure`/`containerGuess`/`suggestedLocationId`/`locations`/`thumbPath`/`saved`/`error`）、`start(jpeg)`、`load(itemId)`、`update((ItemDraft)->ItemDraft)`、`markTouched(String)`、`setLocation(String?)`、`createLocation(parentId, name)`、`toggleSeason(String)`、`toggleTag(String)`、`setQuantity(Int)`、`setExpires(Long?, String?)`、`retryAnalysis()`、`save()`、`keepForLater()`、`consumeSaved()`
  - `@Composable fun ReviewScreen(vm: ReviewViewModel, photosDir: File, onDone: () -> Unit, onPickImage: () -> Unit)`
  - `@Composable fun LocationPickerDialog(options: List<LocationOption>, title: String, selectedId: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit, rootLabel: String = "暂不归类，先进收件箱", onCreate: ((parentId: String?, name: String) -> Unit)? = null)`
  - `val EXPIRE_KINDS: List<String>`

外壳与 `MainActivity` 接线在 Task 16：本任务结束时页面可编译、单测可跑，但还没有入口能点进去。

- [x] **Step 1: 写主题**

`app/src/main/java/com/wheremy/app/ui/theme/Theme.kt`：

```kotlin
package com.wheremy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal40 = Color(0xFF00695C)
private val Teal80 = Color(0xFF7FD6C5)
private val Amber40 = Color(0xFF9A6A00)
private val Amber80 = Color(0xFFFFD37A)

private val Light = lightColorScheme(
    primary = Teal40, onPrimary = Color.White, secondary = Amber40, onSecondary = Color.White,
    tertiary = Teal40,
)

private val Dark = darkColorScheme(
    primary = Teal80, onPrimary = Color(0xFF00201B), secondary = Amber80,
    onSecondary = Color(0xFF332500), tertiary = Teal80,
)

@Composable
fun WheremyTheme(content: @Composable () -> Unit) {
    // 固定配色：单用户工具类 APP，动态取色会让缩略图色彩判断失真
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
```

- [x] **Step 2: 容器暂存选图字节**

`AppContainer.kt` 中 `suggestLocation` 之后插入（`import kotlinx.coroutines.flow.MutableStateFlow`）：

```kotlin
    /** 选图与确认页分处两个 composable，进程内暂存字节数 MB，确认页消费后立即清空 */
    val pendingCapture = MutableStateFlow<ByteArray?>(null)
```

- [x] **Step 3: 写位置扁平化助手**

`app/src/main/java/com/wheremy/app/ui/common/LocationFlatten.kt`：

```kotlin
package com.wheremy.app.ui.common

import com.wheremy.app.domain.LocationTree

data class LocationOption(val id: String, val label: String, val depth: Int)

/** 选择框与树页共用同一份缩进列表；树已在内存，直接前序展开 */
fun flattenLocations(tree: LocationTree): List<LocationOption> {
    val out = ArrayList<LocationOption>()

    fun walk(parentId: String?, depth: Int) {
        val children = if (parentId == null) tree.roots() else tree.childrenOf(parentId)
        children.forEach {
            out.add(LocationOption(it.id, it.name, depth))
            walk(it.id, depth + 1)
        }
    }

    walk(null, 0)
    return out
}
```

- [x] **Step 4: 写 ReviewViewModel**

`app/src/main/java/com/wheremy/app/ui/review/ReviewViewModel.kt`：

```kotlin
package com.wheremy.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.vision.FailureReason
import com.wheremy.app.domain.CaptureFlow
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.ui.common.LocationOption
import com.wheremy.app.ui.common.flattenLocations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val itemId: String? = null,
    val draft: ItemDraft = ItemDraft(id = ""),
    val analyzing: Boolean = false,
    val failure: FailureReason? = null,
    val containerGuess: String? = null,
    val suggestedLocationId: String? = null,
    val locations: List<LocationOption> = emptyList(),
    val thumbPath: String? = null,
    val saved: Boolean = false,
    val error: String? = null,
)

class ReviewViewModel(
    private val capture: CaptureFlow,
    private val items: ItemRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /** 用户已经改过的字段，AI 结果回来时不再覆盖（spec 第 6.1 节） */
    private val touched = HashSet<String>()
    private var started = false

    fun markTouched(field: String) {
        touched.add(field)
    }

    fun start(jpeg: ByteArray) {
        if (started) return
        started = true
        _state.update { it.copy(analyzing = true) }
        viewModelScope.launch { apply(capture.capture(jpeg)) }
    }

    fun retryAnalysis() {
        val id = _state.value.itemId ?: return
        _state.update { it.copy(analyzing = true) }
        viewModelScope.launch {
            val out = capture.reanalyze(id)
            _state.update { it.copy(analyzing = false) }
            out?.let { apply(it) }
        }
    }

    private suspend fun apply(out: com.wheremy.app.domain.CaptureOutcome) {
        refreshLocations()
        val thumb = items.thumbPathOf(out.itemId)
        _state.update { cur ->
            cur.copy(
                itemId = out.itemId,
                analyzing = false,
                draft = merge(cur.draft, out.draft),
                failure = out.failure,
                containerGuess = out.containerGuess,
                suggestedLocationId = out.suggestedLocationId,
                thumbPath = thumb,
            )
        }
    }

    /** AI 结果落库后读回的草稿，逐项让位于用户手改过的字段 */
    private fun merge(user: ItemDraft, fromAi: ItemDraft): ItemDraft {
        fun <T> pick(field: String, ai: T, mine: T): T = if (field in touched) mine else ai
        return fromAi.copy(
            name = pick("name", fromAi.name, user.name),
            category = pick("category", fromAi.category, user.category),
            seasons = pick("seasons", fromAi.seasons, user.seasons),
            color = pick("color", fromAi.color, user.color),
            brand = pick("brand", fromAi.brand, user.brand),
            material = pick("material", fromAi.material, user.material),
            aliases = pick("aliases", fromAi.aliases, user.aliases),
            description = pick("description", fromAi.description, user.description),
            notes = pick("notes", fromAi.notes, user.notes),
            quantity = pick("quantity", fromAi.quantity, user.quantity),
            tags = pick("tags", fromAi.tags, user.tags),
            locationId = pick("locationId", fromAi.locationId, user.locationId),
        )
    }

    /** 进程重建后重进确认页，或从详情页点"编辑" */
    fun load(itemId: String) {
        if (_state.value.itemId == itemId) return
        started = true
        viewModelScope.launch {
            val draft = runCatching { items.draftOf(itemId) }.getOrNull() ?: return@launch
            _state.update {
                it.copy(
                    itemId = itemId, draft = draft, analyzing = false,
                    thumbPath = items.thumbPathOf(itemId),
                )
            }
            refreshLocations()
        }
    }

    /** 确认页里现场建位置：AI 猜出"衣柜"但树里没有时的唯一出路 */
    fun createLocation(parentId: String?, name: String) {
        viewModelScope.launch {
            val id = runCatching { locations.add(parentId, name) }.getOrElse { err ->
                _state.update { s -> s.copy(error = err.message ?: "新建位置失败") }
                return@launch
            }
            refreshLocations()
            setLocation(id)
        }
    }

    private suspend fun refreshLocations() {
        val options = flattenLocations(locations.tree())
        _state.update { it.copy(locations = options) }
    }

    fun update(transform: (ItemDraft) -> ItemDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    fun setLocation(locationId: String?) {
        markTouched("locationId")
        update { it.copy(locationId = locationId) }
    }

    // 注意：上面 update 的签名是 (ItemDraft) -> ItemDraft，这里必须直接返回草稿，
    // 写成 it.copy(draft = …) 会报 Unresolved reference: draft（16 处编译错误）。
    fun toggleSeason(season: String) {
        markTouched("seasons")
        update { d ->
            d.copy(seasons = if (season in d.seasons) d.seasons - season else d.seasons + season)
        }
    }

    fun toggleTag(tag: String) {
        markTouched("tags")
        update { d -> d.copy(tags = if (tag in d.tags) d.tags - tag else d.tags + tag) }
    }

    fun setQuantity(delta: Int) {
        markTouched("quantity")
        update { d -> d.copy(quantity = (d.quantity + delta).coerceAtLeast(1)) }
    }

    fun setExpires(epochMillis: Long?, kind: String?) {
        markTouched("expiresAt")
        update { d -> d.copy(expiresAt = epochMillis, expireKind = kind) }
    }

    fun save() {
        val cur = _state.value
        val id = cur.itemId ?: return
        viewModelScope.launch {
            runCatching { items.confirm(cur.draft) }
                .onSuccess { _state.update { s -> s.copy(saved = true, error = null) } }
                .onFailure { _state.update { s -> s.copy(error = it.message ?: "保存失败") } }
        }
    }

    /** "稍后处理"：字段照样存下来，退回 DRAFT 出现在收件箱，位置不动（第二轮评审后改写） */
    fun keepForLater() {
        val cur = _state.value
        val id = cur.itemId ?: return
        analysis?.cancel()
        viewModelScope.launch {
            runCatching { items.keepAsDraft(cur.draft) }
                .onSuccess { _state.update { s -> s.copy(saved = true, error = null) } }
                .onFailure { _state.update { s -> s.copy(error = it.message ?: "保存失败") } }
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(saved = false) }
    }
}
```

`save()` 里 `require(name.isNotEmpty())` 抛出的 `IllegalArgumentException("名称不能为空")` 直接成为 `error`，表单不再自己校验一遍。

- [x] **Step 5: 写 ViewModel 失败测试**

`app/src/test/java/com/wheremy/app/ui/review/ReviewViewModelTest.kt`：

```kotlin
package com.wheremy.app.ui.review

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.vision.AnalysisOutcome
import com.wheremy.app.data.vision.AnalysisResult
import com.wheremy.app.data.vision.FailureReason
import com.wheremy.app.data.vision.PhotoAnalyzer
import com.wheremy.app.domain.AnalyzePhotoUseCase
import com.wheremy.app.domain.CaptureFlow
import com.wheremy.app.domain.SuggestLocationUseCase
import com.wheremy.app.ui.common.LocationOption
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReviewViewModelTest {

    private class FakeAnalyzer(var outcome: AnalysisOutcome) : PhotoAnalyzer {
        override suspend fun analyze(jpeg: ByteArray) = outcome
    }

    private lateinit var db: WheremyDatabase
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository
    private lateinit var analyzer: FakeAnalyzer
    private lateinit var vm: ReviewViewModel
    private val mainDispatcher = UnconfinedTestDispatcher()

    private val success = AnalysisOutcome.Success(
        AnalysisResult(
            name = "深灰色羊毛外套", category = "衣物", seasons = listOf("秋", "冬"),
            color = "深灰", brand = null, material = "羊毛", aliases = listOf("厚外套"),
            description = "中长款翻领", tags = listOf("秋冬", "通勤"), containerGuess = "衣柜",
        )
    )

    @Before fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WheremyDatabase::class.java)
            // Room 默认把 DAO 调用派发到 IO 线程，而 UnconfinedTestDispatcher 让 launch 立即返回，
            // 断言会跑在状态更新之前。让执行器就地跑，整条链路在 start() 里同步走完。
            .setQueryExecutor(InlineExecutor)
            .setTransactionExecutor(InlineExecutor)
            .build()
        val dir = File(ctx.filesDir, "rv-" + System.nanoTime()).also { it.mkdirs() }
        items = ItemRepository(db, PhotoStore(dir))
        locations = LocationRepository(db)
        analyzer = FakeAnalyzer(AnalysisOutcome.Failed(FailureReason.OFFLINE))
        val capture = CaptureFlow(items, locations, AnalyzePhotoUseCase(analyzer), SuggestLocationUseCase())
        vm = ReviewViewModel(capture, items, locations)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test fun startMarksAnalyzingThenFillsDraft() = runTest {
        analyzer.outcome = success
        vm.start(jpeg(800, 600))
        assertEquals("深灰色羊毛外套", vm.state.value.draft.name)
        assertFalse(vm.state.value.analyzing)
        assertNull(vm.state.value.failure)
        assertEquals(listOf("秋冬", "通勤"), vm.state.value.draft.tags)
        assertTrue(vm.state.value.thumbPath?.endsWith(".jpg") == true)
    }

    @Test fun failedAnalysisStillGivesEditableForm() = runTest {
        vm.start(jpeg(800, 600))
        assertEquals(FailureReason.OFFLINE, vm.state.value.failure)
        assertTrue(vm.state.value.itemId != null)
        assertEquals("", vm.state.value.draft.name)
    }

    @Test fun userEditSurvivesLateAnalysis() = runTest {
        vm.start(jpeg(800, 600))
        vm.markTouched("name")
        vm.update { it.copy(name = "我的外套") }
        analyzer.outcome = success
        vm.retryAnalysis()
        assertEquals("我的外套", vm.state.value.draft.name)
        // 没碰过的字段接受 AI 结果
        assertEquals("衣物", vm.state.value.draft.category)
    }

    @Test fun suggestedLocationPrefillsAndUserChoiceWins() = runTest {
        val home = locations.add(null, "家")
        val wardrobe = locations.add(home, "衣柜", "衣柜")
        analyzer.outcome = success
        vm.start(jpeg(800, 600))
        assertEquals(wardrobe, vm.state.value.draft.locationId)
        vm.setLocation(home)
        assertEquals(home, vm.state.value.draft.locationId)
    }

    @Test fun createLocationInsidePickerSelectsIt() = runTest {
        val home = locations.add(null, "家")
        analyzer.outcome = success
        vm.start(jpeg(400, 300))
        vm.createLocation(home, "阳台收纳箱")
        val created = vm.state.value.locations.last()
        assertEquals("阳台收纳箱", created.label)
        assertEquals(created.id, vm.state.value.draft.locationId)
        assertNull(vm.state.value.error)
    }

    @Test fun createLocationSurfacesDuplicateName() = runTest {
        val home = locations.add(null, "家")
        locations.add(home, "衣柜")
        analyzer.outcome = success
        vm.start(jpeg(400, 300))
        vm.createLocation(home, "衣柜")
        assertTrue(vm.state.value.error?.contains("衣柜") == true)
    }

    @Test fun locationListIsIndented() = runTest {
        val home = locations.add(null, "家")
        val room = locations.add(home, "主卧")
        locations.add(room, "抽屉")
        vm.start(jpeg(400, 300))
        assertEquals(
            listOf(LocationOption(home, "家", 0), LocationOption(room, "主卧", 1)),
            vm.state.value.locations.take(2),
        )
        assertEquals(3, vm.state.value.locations.size)
    }

    @Test fun saveWithoutNameReportsErrorAndKeepsDraft() = runTest {
        vm.start(jpeg(400, 300))
        vm.save()
        assertEquals("名称不能为空", vm.state.value.error)
        assertFalse(vm.state.value.saved)
        assertEquals(ItemStatus.DRAFT, items.get(vm.state.value.itemId!!)?.status)
    }

    @Test fun saveConfirmsItem() = runTest {
        val home = locations.add(null, "家")
        analyzer.outcome = success
        vm.start(jpeg(400, 300))
        vm.setLocation(home)
        vm.save()
        assertTrue(vm.state.value.saved)
        val saved = items.get(vm.state.value.itemId!!)
        assertEquals(ItemStatus.CONFIRMED, saved?.status)
        assertEquals("家", saved?.locationId?.let { locations.byId(it)?.name })
    }

    @Test fun keepForLaterPutsItemInInbox() = runTest {
        val home = locations.add(null, "家")
        analyzer.outcome = success
        vm.start(jpeg(400, 300))
        vm.setLocation(home)
        vm.keepForLater()
        assertTrue(vm.state.value.saved)
        val kept = items.get(vm.state.value.itemId!!)
        assertEquals(ItemStatus.DRAFT, kept?.status)
        assertNull(kept?.locationId)
        assertEquals("深灰色羊毛外套", kept?.name)
    }

    @Test fun togglesAndQuantityRoundTrip() = runTest {
        analyzer.outcome = success
        vm.start(jpeg(400, 300))
        vm.toggleSeason("秋")
        assertEquals(listOf("冬"), vm.state.value.draft.seasons)
        vm.toggleTag("换季")
        assertTrue("换季" in vm.state.value.draft.tags)
        // AI 没给数量，草稿从 1 开始，＋1 到 2
        vm.setQuantity(1)
        assertEquals(2, vm.state.value.draft.quantity)
        vm.setQuantity(-10)
        assertEquals(1, vm.state.value.draft.quantity)
    }

    @Test fun loadExistingDraftForEditing() = runTest {
        val home = locations.add(null, "家")
        val id = items.newDraft(jpeg(400, 300))
        items.confirm(ItemDraft(id = id, name = "电钻", locationId = home, quantity = 2))
        vm.load(id)
        assertEquals("电钻", vm.state.value.draft.name)
        assertEquals(2, vm.state.value.draft.quantity)
        assertEquals(home, vm.state.value.draft.locationId)
        assertFalse(vm.state.value.analyzing)
    }

    private fun jpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            return bos.toByteArray()
        }
    }
}

/** 在调用线程就地执行，配合 eager 的 Main 测试调度器消除线程切换带来的竞态 */
private object InlineExecutor : Executor {
    override fun execute(command: Runnable) = command.run()
}
```

- [x] **Step 6: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*ReviewViewModelTest" --console=plain`
Expected: 12 个测试 PASS。

- [x] **Step 7: 提交（VM 层）**

```bash
git add app/src
git commit -m "feat: 确认页 ViewModel，AI 预填与用户手改字段合并"
```

- [x] **Step 8: 共享的展示层格式化**

`app/src/main/java/com/wheremy/app/ui/common/UiFormat.kt`：

```kotlin
package com.wheremy.app.ui.common

import com.wheremy.app.data.vision.FailureReason
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE)

fun daysUntil(epochMillis: Long, today: LocalDate = LocalDate.now()): Int =
    ChronoUnit.DAYS.between(today, localDateOf(epochMillis)).toInt()

fun localDateOf(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/** M3 DatePicker 给的是 UTC 零点毫秒，存成本地零点才能和"今天还剩几天"对得上 */
fun utcPickerToLocalMillis(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC)
        .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun localMillisToUtcPicker(epochMillis: Long?): Long? =
    if (epochMillis == null) null
    else localDateOf(epochMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** spec 第 8 节：每种失败都要给可读中文和下一步 */
fun failureText(reason: FailureReason?): String? = when (reason) {
    null -> null
    FailureReason.NO_CONFIG -> "未启用 AI（没配 API Key），字段留空，可在设置页补全"
    FailureReason.OFFLINE -> "网络不可用，已跳过 AI 分析"
    FailureReason.TIMEOUT -> "AI 分析超时（15 秒），可点重新分析"
    FailureReason.UNAUTHORIZED -> "API Key 无效或被拒，请检查设置页"
    FailureReason.RATE_LIMITED -> "AI 服务限流，稍后再试一次"
    FailureReason.SERVER -> "AI 服务暂不可用，可点重新分析"
    FailureReason.BAD_RESPONSE -> "AI 返回内容无法解析，可点重新分析"
}
```

`app/src/main/java/com/wheremy/app/data/repo/Models.kt` 末尾追加（Task 9 建立该文件，`CATEGORIES`/`SEASONS` 就在里面；原计划误写成 `domain/Models.kt`）：

```kotlin
val EXPIRE_KINDS = listOf("保质期", "保修", "证件有效期", "其他")
```

- [x] **Step 9: 写位置选择对话框（确认页、照片墙、详情页共用）**

`app/src/main/java/com/wheremy/app/ui/common/LocationPicker.kt`：

```kotlin
package com.wheremy.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * onPick(null) 表示"不放在任何位置"，文案由 rootLabel 决定：物品=进收件箱，位置节点=回到根层级。
 * onCreate 非空时底部给出"在选中项下新建"的输入框；确认页要顺手建位置，照片墙和详情页只要挑选。
 */
@Composable
fun LocationPickerDialog(
    options: List<LocationOption>,
    title: String,
    selectedId: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    rootLabel: String = "暂不归类，先进收件箱",
    onCreate: ((parentId: String?, name: String) -> Unit)? = null,
) {
    var newName by remember { mutableStateOf("") }
    val parentLabel = options.firstOrNull { it.id == selectedId }?.label
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(rootLabel, modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(options, key = { it.id }) { opt ->
                        Row(modifier = Modifier.padding(start = (opt.depth * 16).dp)) {
                            TextButton(
                                onClick = { onPick(opt.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    opt.label,
                                    fontWeight = if (opt.id == selectedId) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
                if (onCreate != null) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("在「${parentLabel ?: "根层级"}」下新建") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreate(selectedId, newName.trim())
                                newName = ""
                            }
                        },
                    ) { Text("新建并选中") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
```

- [x] **Step 10: 写确认页**

`app/src/main/java/com/wheremy/app/ui/review/ReviewScreen.kt`：

```kotlin
package com.wheremy.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wheremy.app.data.repo.CATEGORIES
import com.wheremy.app.data.repo.EXPIRE_KINDS
import com.wheremy.app.data.repo.SEASONS
import com.wheremy.app.data.vision.FailureReason
import com.wheremy.app.ui.common.LocationOption
import com.wheremy.app.ui.common.LocationPickerDialog
import com.wheremy.app.ui.common.daysUntil
import com.wheremy.app.ui.common.failureText
import com.wheremy.app.ui.common.formatDate
import com.wheremy.app.ui.common.localMillisToUtcPicker
import com.wheremy.app.ui.common.utcPickerToLocalMillis
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    vm: ReviewViewModel,
    photosDir: File,
    onDone: () -> Unit,
    onPickImage: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showLocations by remember { mutableStateOf(false) }
    var showExpire by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            vm.consumeSaved()
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("确认物品信息") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    TextButton(onClick = vm::keepForLater, enabled = state.itemId != null) { Text("稍后处理") }
                    Button(onClick = vm::save, enabled = state.itemId != null) { Text("保存") }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.thumbPath?.let { rel ->
                AsyncImage(
                    model = File(photosDir, rel),
                    contentDescription = "物品照片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            if (state.itemId == null && !state.analyzing) {
                OutlinedButton(onClick = onPickImage) { Text("选择照片") }
            }

            AiStatusLine(state.analyzing, state.failure, state.containerGuess) { vm.retryAnalysis() }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = state.draft.name,
                onValueChange = { vm.markTouched("name"); vm.update { d -> d.copy(name = it) } },
                label = { Text("名称 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LabeledSection("分类") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { c ->
                        FilterChip(
                            selected = state.draft.category == c,
                            onClick = {
                                vm.markTouched("category")
                                vm.update { d -> d.copy(category = if (d.category == c) null else c) }
                            },
                            label = { Text(c) },
                        )
                    }
                }
            }

            LabeledSection("季节") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SEASONS.forEach { s ->
                        FilterChip(
                            selected = s in state.draft.seasons,
                            onClick = { vm.toggleSeason(s) },
                            label = { Text(s) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.draft.color.orEmpty(),
                    onValueChange = { vm.markTouched("color"); vm.update { d -> d.copy(color = it) } },
                    label = { Text("颜色") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.draft.material.orEmpty(),
                    onValueChange = { vm.markTouched("material"); vm.update { d -> d.copy(material = it) } },
                    label = { Text("材质") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.draft.brand.orEmpty(),
                onValueChange = { vm.markTouched("brand"); vm.update { d -> d.copy(brand = it) } },
                label = { Text("品牌") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.draft.aliases.joinToString("、"),
                onValueChange = { raw ->
                    vm.markTouched("aliases")
                    vm.update { d ->
                        d.copy(aliases = raw.split('、', '，', ',').map { it.trim() }.filter { it.isNotEmpty() })
                    }
                },
                label = { Text("别名（顿号分隔，方便日后搜到）") },
                modifier = Modifier.fillMaxWidth(),
            )

            LabeledSection("标签（点一下删除）") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.draft.tags.forEach { t ->
                        AssistChip(onClick = { vm.toggleTag(t) }, label = { Text(t) })
                    }
                    AddTagField(onAdd = { vm.toggleTag(it) })
                }
            }

            OutlinedTextField(
                value = state.draft.description.orEmpty(),
                onValueChange = { vm.markTouched("description"); vm.update { d -> d.copy(description = it) } },
                label = { Text("描述") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.draft.notes.orEmpty(),
                onValueChange = { vm.markTouched("notes"); vm.update { d -> d.copy(notes = it) } },
                label = { Text("备注（只有你自己看得到的）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.setQuantity(-1) }) { Text("－") }
                Text("${state.draft.quantity}", modifier = Modifier.padding(horizontal = 12.dp))
                OutlinedButton(onClick = { vm.setQuantity(1) }) { Text("＋") }
                Text("数量", style = MaterialTheme.typography.labelLarge)
            }

            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("放在哪里", style = MaterialTheme.typography.labelLarge)
                        Text(locationLabel(state.draft.locationId, state.locations))
                    }
                    TextButton(onClick = { showLocations = true }) { Text("选择") }
                }
            }

            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("到期提醒", style = MaterialTheme.typography.labelLarge)
                        Text(expireLabel(state.draft.expiresAt, state.draft.expireKind))
                    }
                    TextButton(onClick = { showExpire = true }) { Text("设置") }
                }
            }
        }
    }

    if (showLocations) {
        LocationPickerDialog(
            options = state.locations,
            title = "选择位置",
            selectedId = state.draft.locationId,
            onDismiss = { showLocations = false },
            onPick = { id -> vm.setLocation(id); showLocations = false },
            onCreate = { parentId, name -> vm.createLocation(parentId, name) },
        )
    }

    if (showExpire) {
        ExpireDialog(
            initialMillis = state.draft.expiresAt,
            initialKind = state.draft.expireKind,
            onDismiss = { showExpire = false },
            onClear = { vm.setExpires(null, null); showExpire = false },
            onConfirm = { millis, kind -> vm.setExpires(millis, kind); showExpire = false },
        )
    }
}

private fun locationLabel(id: String?, options: List<LocationOption>): String =
    id?.let { picked -> options.firstOrNull { it.id == picked }?.label } ?: "未归类（进收件箱）"

private fun expireLabel(millis: Long?, kind: String?): String = when {
    millis == null -> "未设置"
    else -> "${kind ?: "到期"} · ${formatDate(millis)}（还剩 ${daysUntil(millis)} 天）"
}

@Composable
private fun AiStatusLine(
    analyzing: Boolean,
    failure: FailureReason?,
    guess: String?,
    onRetry: () -> Unit,
) {
    if (analyzing) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("AI 正在看照片，可以先自己填", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    } else {
        failureText(failure)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (guess != null) Text("AI 猜的位置：$guess", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("重新分析") }
        }
    }
}

@Composable
private fun LabeledSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun AddTagField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("加标签") },
        singleLine = true,
        modifier = Modifier.width(160.dp),
        trailingIcon = {
            TextButton(onClick = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            }) { Text("＋") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExpireDialog(
    initialMillis: Long?,
    initialKind: String?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (Long, String?) -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcPicker(initialMillis))
    var kind by remember { mutableStateOf(initialKind) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onConfirm(utcPickerToLocalMillis(it), kind) }
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onClear) { Text("清除") } },
    ) {
        Column {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                EXPIRE_KINDS.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = if (kind == k) null else k }, label = { Text(k) })
                }
            }
            DatePicker(state = pickerState)
        }
    }
}
```

- [x] **Step 11: 跑全量单测 + 编译**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: 全绿。确认页的交互在本任务无法点按验证（外壳还没接线），留到末尾"真机手工验收清单"逐条走。若某个图标报 unresolved reference，说明它不在 `material-icons-core` 里，换成 `Icons.Filled.Clear` / `Icons.Filled.Check` 并在"实施偏差记录"里注明。

- [x] **Step 12: 提交**

```bash
git add app/src
git commit -m "feat: 确认页表单、位置选择与到期设置"
```
---

### Task 14: 位置树页（M4 下钻与节点维护）

**Files:**
- Create: `app/src/main/java/com/wheremy/app/ui/tree/TreeViewModel.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/tree/TreeScreen.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/common/ItemRow.kt`
- Test: `app/src/test/java/com/wheremy/app/ui/tree/TreeViewModelTest.kt`

**Interfaces:**
- Consumes: Task 4 `LocationTree`/`LocationNode`、Task 10 `LocationRepository`/`LocationNotEmptyException`/`LOCATION_KINDS`、Task 9 `ItemRepository.summaries`/`ItemSummary`、Task 13 `LocationOption`/`flattenLocations`/`LocationPickerDialog`/`formatDate`。
- Produces:
  - `data class TreeNodeUi(id: String, name: String, kind: String?, directCount: Int, subtreeCount: Int, hasChildren: Boolean)`
  - `data class TreeUiState(currentParentId: String?, breadcrumb: List<LocationNode>, children: List<TreeNodeUi>, currentItems: List<ItemSummary>, loading: Boolean, moveId: String?, moveTargets: List<LocationOption>, error: String?, message: String?)`
  - `class TreeViewModel(items: ItemRepository, locations: LocationRepository) : ViewModel` — `state`、`refresh()`、`open(id)`、`up(): Boolean`、`create(name, kind?)`、`rename(id, name)`、`setKind(id, kind?)`、`prepareMove(id)`、`closeMove()`、`move(id, newParentId?)`、`remove(id)`、`consumeError()`、`consumeMessage()`
  - `@Composable fun TreeScreen(vm: TreeViewModel, photosDir: File, onOpenItem: (String) -> Unit)`
  - `@Composable fun ItemThumbRow(summary: ItemSummary, photosDir: File, onClick: () -> Unit)`

spec 第 6.2 节的"长按可新增子节点、改名、排序、拍照记录该位置"里，**排序与位置拍照不在本计划内**（见末尾"Plan 2 范围"第 5 条），本任务实现新增/改名/类型/移动到/删除五项。

- [x] **Step 1: 写 TreeViewModel**

`app/src/main/java/com/wheremy/app/ui/tree/TreeViewModel.kt`：

```kotlin
package com.wheremy.app.ui.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.domain.LocationNode
import com.wheremy.app.ui.common.LocationOption
import com.wheremy.app.ui.common.flattenLocations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TreeNodeUi(
    val id: String,
    val name: String,
    val kind: String?,
    val directCount: Int,
    val subtreeCount: Int,
    val hasChildren: Boolean,
)

data class TreeUiState(
    val currentParentId: String? = null,
    val breadcrumb: List<LocationNode> = emptyList(),
    val children: List<TreeNodeUi> = emptyList(),
    val currentItems: List<ItemSummary> = emptyList(),
    val loading: Boolean = true,
    val moveId: String? = null,
    val moveTargets: List<LocationOption> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

class TreeViewModel(
    private val items: ItemRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TreeUiState())
    val state: StateFlow<TreeUiState> = _state.asStateFlow()

    private var parentId: String? = null

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun open(id: String) {
        parentId = id
        viewModelScope.launch { load() }
    }

    /** true 表示返回键被"上一层"消费；已在根层级返回 false 交给外壳 */
    fun up(): Boolean {
        val path = _state.value.breadcrumb
        if (path.isEmpty()) return false
        parentId = path.dropLast(1).lastOrNull()?.id
        viewModelScope.launch { load() }
        return true
    }

    private suspend fun load() {
        val tree = locations.tree()
        val parent = parentId
        val nodes = if (parent == null) tree.roots() else tree.childrenOf(parent)
        _state.update {
            it.copy(
                currentParentId = parent,
                breadcrumb = if (parent == null) emptyList() else tree.pathOf(parent),
                children = nodes.map { node ->
                    TreeNodeUi(
                        id = node.id,
                        name = node.name,
                        kind = node.kind,
                        directCount = locations.itemCount(node.id),
                        subtreeCount = locations.subtreeItemCount(node.id),
                        hasChildren = tree.childrenOf(node.id).isNotEmpty(),
                    )
                },
                currentItems = if (parent == null) emptyList()
                else items.summaries(items.atLocation(parent), tree),
                loading = false,
            )
        }
    }

    fun create(name: String, kind: String?) {
        val trimmed = name.trim()
        viewModelScope.launch {
            runCatching { locations.add(parentId, trimmed, kind) }
                .onSuccess { _state.update { s -> s.copy(message = "已新建「$trimmed」") } }
                .onFailure { setError(it) }
            load()
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            runCatching { locations.rename(id, name) }.onFailure { setError(it) }
            load()
        }
    }

    fun setKind(id: String, kind: String?) {
        viewModelScope.launch {
            locations.setKind(id, kind)
            load()
        }
    }

    /** 移动到对话框的候选：排除自己与整棵子树，从源头避免造环 */
    fun prepareMove(id: String) {
        viewModelScope.launch {
            val tree = locations.tree()
            val banned = tree.descendantIds(id)
            _state.update {
                it.copy(
                    moveId = id,
                    moveTargets = flattenLocations(tree).filter { opt -> opt.id !in banned },
                )
            }
        }
    }

    fun closeMove() {
        _state.update { it.copy(moveId = null, moveTargets = emptyList()) }
    }

    fun move(id: String, newParentId: String?) {
        viewModelScope.launch {
            runCatching { locations.changeParent(id, newParentId) }
                .onSuccess { _state.update { s -> s.copy(message = "已移动到新层级", moveId = null) } }
                .onFailure { setError(it) }
            load()
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            runCatching { locations.delete(id) }
                .onSuccess {
                    if (parentId == id) parentId = null
                    _state.update { s -> s.copy(message = "已删除该位置") }
                }
                .onFailure { setError(it) }
            load()
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun setError(err: Throwable) {
        _state.update { it.copy(error = err.message ?: "操作失败") }
    }
}
```

`locations.add/rename/changeParent/delete` 抛的都是带中文信息的 `IllegalArgumentException` 与 `LocationNotEmptyException`，界面直接显示 `message`，不在 VM 里二次翻译。

- [x] **Step 2: 写 ViewModel 测试**

**注意（实施时改过两处，都是测试自己写错，不是实现错）：** ① `setUp` 里已经 `vm.refresh()` 过一次，`locations.add(...)` 不会自动反映到 `state`，所以 `removeDeletesEmptyLeaf` 必须先再 `vm.refresh()` 才能拿到 `children.single()`；② `removeRejectsNonEmptyWithReadableError` 原本删的是 `children.single()`，即下钻后那个**空的**子位置「主卧」，它会删成功而断言 `error` 非空必挂——要验证 `LocationNotEmptyException` 就得删那个**非空的父节点**「家」。另外本文件也套用偏差记录第 9 条的内存库 + `InlineExecutor`。

`app/src/test/java/com/wheremy/app/ui/tree/TreeViewModelTest.kt`：

```kotlin
package com.wheremy.app.ui.tree

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TreeViewModelTest {

    private lateinit var db: WheremyDatabase
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository
    private lateinit var vm: TreeViewModel
    private lateinit var photosDir: File
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WheremyDatabase::class.java)
            // 见偏差记录第 9 条：Room 默认把查询派发到 IO 线程，eager 的 Main 调度器会让断言抢跑
            .setQueryExecutor(InlineExecutor)
            .setTransactionExecutor(InlineExecutor)
            .build()
        photosDir = File(ctx.filesDir, "tree-" + System.nanoTime()).also { it.mkdirs() }
        items = ItemRepository(db, PhotoStore(photosDir))
        locations = LocationRepository(db)
        vm = TreeViewModel(items, locations)
        vm.refresh()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        photosDir.deleteRecursively()
    }

    @Test fun emptyTreeLoadsRootLevel() = runTest {
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.children.isEmpty())
        assertTrue(vm.state.value.breadcrumb.isEmpty())
    }

    @Test fun rootLevelListsRootsWithCounts() = runTest {
        val home = locations.add(null, "家")
        val room = locations.add(home, "主卧")
        putItem("电钻", home)
        putItem("毛衣", room)
        vm.refresh()
        val row = vm.state.value.children.single()
        assertEquals("家", row.name)
        assertEquals(1, row.directCount)
        assertEquals(2, row.subtreeCount)
        assertTrue(row.hasChildren)
    }

    @Test fun openDescendsAndBuildsBreadcrumb() = runTest {
        val home = locations.add(null, "家")
        val room = locations.add(home, "主卧")
        vm.open(home)
        vm.open(room)
        assertEquals(listOf("家", "主卧"), vm.state.value.breadcrumb.map { it.name })
        assertEquals(room, vm.state.value.currentParentId)
    }

    @Test fun upGoesOneLevelAndStopsAtRoot() = runTest {
        val home = locations.add(null, "家")
        vm.open(home)
        assertTrue(vm.up())
        assertTrue(vm.state.value.breadcrumb.isEmpty())
        assertFalse(vm.up())
    }

    @Test fun createAddsUnderCurrentParent() = runTest {
        val home = locations.add(null, "家")
        vm.open(home)
        vm.create(" 阳台柜 ", "收纳盒")
        assertEquals(listOf("阳台柜"), vm.state.value.children.map { it.name })
        assertEquals("收纳盒", vm.state.value.children.first().kind)
        assertTrue(vm.state.value.message?.contains("阳台柜") == true)
    }

    @Test fun createSurfacesDuplicateName() = runTest {
        val home = locations.add(null, "家")
        locations.add(home, "衣柜")
        vm.open(home)
        vm.create("衣柜", null)
        assertTrue(vm.state.value.error?.contains("衣柜") == true)
        assertEquals(1, vm.state.value.children.size)
    }

    @Test fun renameAndSetKindUpdateRows() = runTest {
        val id = locations.add(null, "旧名字")
        vm.rename(id, "新名字")
        assertEquals("新名字", vm.state.value.children.single().name)
        vm.setKind(id, "箱子")
        assertEquals("箱子", vm.state.value.children.single().kind)
    }

    @Test fun prepareMoveExcludesSelfAndDescendants() = runTest {
        val a = locations.add(null, "家")
        val b = locations.add(a, "主卧")
        locations.add(b, "抽屉")
        locations.add(null, "公司")
        vm.prepareMove(a)
        assertEquals(listOf("公司"), vm.state.value.moveTargets.map { it.label })
        assertEquals(a, vm.state.value.moveId)
        vm.closeMove()
        assertNull(vm.state.value.moveId)
        assertTrue(vm.state.value.moveTargets.isEmpty())
    }

    @Test fun moveRehangsAndClosesDialog() = runTest {
        val home = locations.add(null, "家")
        val work = locations.add(null, "公司")
        val shelf = locations.add(home, "货架")
        vm.move(shelf, work)
        vm.open(work)
        assertEquals(listOf("货架"), vm.state.value.children.map { it.name })
        assertNull(vm.state.value.moveId)
    }

    @Test fun moveSurfacesCycleError() = runTest {
        val a = locations.add(null, "家")
        val b = locations.add(a, "主卧")
        vm.move(a, b)
        assertTrue(vm.state.value.error?.contains("子位置") == true)
        assertEquals(listOf("家"), vm.state.value.children.map { it.name })
    }

    @Test fun removeRejectsNonEmptyWithReadableError() = runTest {
        val home = locations.add(null, "家")
        locations.add(home, "主卧")
        vm.open(home)
        // 删的是「家」：它带着一个子位置，必须被 LocationNotEmptyException 拦住
        vm.remove(home)
        assertTrue(vm.state.value.error?.contains("搬空") == true)
        assertEquals(listOf("主卧"), vm.state.value.children.map { it.name })
    }

    @Test fun removeDeletesEmptyLeaf() = runTest {
        locations.add(null, "临时")
        // add 不会自己刷新状态，先让 children 反映新节点
        vm.refresh()
        vm.remove(vm.state.value.children.single().id)
        assertTrue(vm.state.value.children.isEmpty())
        assertTrue(vm.state.value.message?.contains("删除") == true)
    }

    @Test fun currentItemsShownOnlyInsideALocation() = runTest {
        val home = locations.add(null, "家")
        putItem("电钻", home)
        assertTrue(vm.state.value.currentItems.isEmpty())
        vm.open(home)
        val summary = vm.state.value.currentItems.single()
        assertEquals("电钻", summary.item.name)
        assertEquals("家", summary.locationPath)
        assertTrue(summary.thumbPath?.endsWith(".jpg") == true)
    }

    private suspend fun putItem(name: String, locationId: String) {
        val id = items.newDraft(jpeg(400, 300))
        items.confirm(ItemDraft(id = id, name = name, locationId = locationId))
    }

    private fun jpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            return bos.toByteArray()
        }
    }

    /** 在调用线程就地执行，配合 eager 的 Main 测试调度器消除线程切换带来的竞态 */
    private object InlineExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }
}
```

- [x] **Step 3: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*TreeViewModelTest" --console=plain`
Expected: 13 个测试 PASS。

- [x] **Step 4: 提交（VM 层）**

```bash
git add app/src
git commit -m "feat: 位置树 ViewModel，下钻与节点维护约束"
```

---

- [x] **Step 5: 写共用的物品行**

`app/src/main/java/com/wheremy/app/ui/common/ItemRow.kt`（位置树、收件箱、详情页都用同一种"缩略图 + 名称 + 补充信息"的行）：

```kotlin
package com.wheremy.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wheremy.app.data.repo.ItemSummary
import java.io.File

@Composable
fun ItemThumbRow(
    summary: ItemSummary,
    photosDir: File,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val rel = summary.thumbPath
        if (rel != null) {
            AsyncImage(
                model = File(photosDir, rel),
                contentDescription = summary.item.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(summary.item.name.ifBlank { "未命名物品" }, style = MaterialTheme.typography.bodyMedium)
            val extra = listOfNotNull(
                if (summary.item.quantity > 1) "x${summary.item.quantity}" else null,
                summary.item.expiresAt?.let { "到期 ${formatDate(it)}" },
                summary.locationPath?.let { "在 $it" },
            )
            if (extra.isNotEmpty()) {
                Text(
                    text = extra.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [x] **Step 6: 写位置树页**

`app/src/main/java/com/wheremy/app/ui/tree/TreeScreen.kt`：

```kotlin
package com.wheremy.app.ui.tree

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheremy.app.data.repo.LOCATION_KINDS
import com.wheremy.app.ui.common.ItemThumbRow
import com.wheremy.app.ui.common.LocationPickerDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeScreen(
    vm: TreeViewModel,
    photosDir: File,
    onOpenItem: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TreeNodeUi?>(null) }
    var menuFor by remember { mutableStateOf<TreeNodeUi?>(null) }
    var confirmDelete by remember { mutableStateOf<TreeNodeUi?>(null) }

    // 每次进入本页（含从详情页返回）都重读：详情页可能刚搬走或删掉物品
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.message, state.error) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.breadcrumb.isEmpty()) "全部位置"
                        else state.breadcrumb.joinToString(" / ") { it.name },
                        maxLines = 2,
                    )
                },
                actions = {
                    if (state.breadcrumb.isNotEmpty()) {
                        TextButton(onClick = { vm.up() }) { Text("上一层") }
                    }
                    TextButton(onClick = { showCreate = true }) { Text("新建") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (state.children.isEmpty() && state.currentItems.isEmpty()) {
                item {
                    Text(
                        text = if (state.currentParentId == null)
                            "还没有位置。点右上角「新建」建第一个，例如「家」「主卧」「衣柜」。"
                        else "这个位置还是空的，点「新建」加个子位置，或去照片墙录入物品。",
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(state.children, key = { "loc-" + it.id }) { node ->
                LocationRow(
                    node = node,
                    onClick = { vm.open(node.id) },
                    onMenu = { menuFor = node },
                )
            }
            if (state.currentParentId != null && state.currentItems.isNotEmpty()) {
                item { Text("这里的物品", style = MaterialTheme.typography.labelLarge) }
                items(state.currentItems, key = { "item-" + it.item.id }) { summary ->
                    ItemThumbRow(summary, photosDir = photosDir, onClick = { onOpenItem(summary.item.id) })
                }
            }
            item { Text(" ", modifier = Modifier.height(80.dp)) }
        }
    }

    if (showCreate || editing != null) {
        LocationEditDialog(
            initial = editing,
            onDismiss = { showCreate = false; editing = null },
            onConfirm = { name, kind ->
                val target = editing
                if (target == null) vm.create(name, kind) else {
                    vm.rename(target.id, name)
                    vm.setKind(target.id, kind)
                }
                showCreate = false
                editing = null
            },
        )
    }

    menuFor?.let { node ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(node.name) },
            text = {
                Column {
                    TextButton(
                        onClick = { editing = node; menuFor = null }
                    ) { Text("改名 / 设置类型", modifier = Modifier.fillMaxWidth()) }
                    TextButton(
                        onClick = { vm.prepareMove(node.id); menuFor = null }
                    ) { Text("移动到…", modifier = Modifier.fillMaxWidth()) }
                    TextButton(
                        onClick = { confirmDelete = node; menuFor = null }
                    ) { Text("删除", modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("关闭") } },
        )
    }

    state.moveId?.let { id ->
        LocationPickerDialog(
            options = state.moveTargets,
            title = "移动到",
            selectedId = id,
            onDismiss = { vm.closeMove() },
            onPick = { vm.move(id, it) },
            rootLabel = "移到根层级",
        )
    }

    confirmDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除「${node.name}」？") },
            text = { Text("只有空位置可以删除：子位置和物品需要先搬走。") },
            confirmButton = {
                TextButton(onClick = { vm.remove(node.id); confirmDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun LocationRow(node: TreeNodeUi, onClick: () -> Unit, onMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(node.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val counts = buildString {
                append("${node.directCount} 件")
                if (node.subtreeCount != node.directCount) append("（含子位置 ${node.subtreeCount} 件）")
            }
            Text(
                text = listOfNotNull(node.kind, counts).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (node.hasChildren) Text("▸", color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.MoreVert, contentDescription = "位置操作")
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LocationEditDialog(
    initial: TreeNodeUi?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, kind: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建位置" else "编辑位置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("类型（可选，也可自己填）", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LOCATION_KINDS.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = if (kind == k) null else k },
                            label = { Text(k) },
                        )
                    }
                }
                OutlinedTextField(
                    value = kind.orEmpty(),
                    onValueChange = { kind = it },
                    label = { Text("类型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), kind?.trim()?.ifBlank { null }) }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
```

图标只用 `Icons.Filled.MoreVert`，它确定在 `material-icons-core` 里；`FlowRow` 需要 `ExperimentalLayoutApi` -opt-in，已在函数注解里给出。

- [x] **Step 7: 编译并跑全量单测**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: 全绿。

- [x] **Step 8: 提交**

```bash
git add app/src
git commit -m "feat: 位置树页面，下钻、面包屑与节点维护对话框"
```

---

### Task 15: 照片墙、筛选与详情页（M4 查找闭环）

**Files:**
- Modify: `app/src/main/java/com/wheremy/app/data/db/Daos.kt`（`ItemDao` 加 `allTags`）
- Modify: `app/src/main/java/com/wheremy/app/data/repo/ItemRepository.kt`（加 `allTags()` 与 `photosOf`）
- Create: `app/src/main/java/com/wheremy/app/ui/gallery/GalleryViewModel.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/gallery/GalleryScreen.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/detail/DetailViewModel.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/detail/DetailScreen.kt`
- Test: `app/src/test/java/com/wheremy/app/ui/gallery/GalleryViewModelTest.kt`
- Test: `app/src/test/java/com/wheremy/app/ui/detail/DetailViewModelTest.kt`

**Interfaces:**
- Consumes: Task 9 `ItemRepository.rankedSearch`/`inbox`/`summaries`/`move`/`delete`/`Filters`/`ItemSummary`/`CATEGORIES`/`SEASONS`、Task 10 `LocationRepository`、Task 13 `LocationOption`/`flattenLocations`/`formatDate`/`daysUntil`/`LocationPickerDialog`、Task 14 `ItemThumbRow`。
- Produces:
  - `suspend fun ItemDao.allTags(): List<String>` / `suspend fun ItemRepository.allTags(): List<String>` / `suspend fun ItemRepository.photosOf(itemId: String): List<String>`
  - `data class GalleryUiState(query: String, filters: Filters, items: List<ItemSummary>, allTags: List<String>, moveId: String?, moveTargets: List<LocationOption>, loading: Boolean, error: String?)`
  - `class GalleryViewModel(items: ItemRepository, locations: LocationRepository, val inboxMode: Boolean) : ViewModel` — `state`、`refresh()`、`setQuery(String)`、`setCategory(String?)`、`toggleSeason(String)`、`setTag(String?)`、`setOnlyInbox(Boolean)`、`move(itemId, locationId?)`、`remove(itemId)`、`prepareMove(itemId)`、`closeMove()`、`consumeError()`
  - `@Composable fun GalleryScreen(vm: GalleryViewModel, photosDir: File, onOpenItem: (String) -> Unit)`
  - `data class DetailUiState(itemId: String?, summary: ItemSummary?, photos: List<String>, breadcrumb: String?, moveTargets: List<LocationOption>, moveOpen: Boolean, loading: Boolean, error: String?, deleted: Boolean)`
  - `class DetailViewModel(items: ItemRepository, locations: LocationRepository) : ViewModel` — `state`、`load(itemId)`、`prepareMove()`、`closeMove()`、`moveTo(locationId?)`、`remove()`、`consumeError()`、`consumeDeleted()`
  - `@Composable fun DetailScreen(vm: DetailViewModel, itemId: String, photosDir: File, onBack: () -> Unit, onEdit: (String) -> Unit)`

照片墙与收件箱是同一个 `GalleryViewModel`，只差 `inboxMode` 与顶栏文案（spec 第 6.3/6.4 节共用一套筛选条件）。

- [x] **Step 1: 仓储补两条查询**

`Daos.kt` 的 `ItemDao` 内 `countAt` 之前插入：

```kotlin
    @Query("SELECT DISTINCT tag FROM item_tag ORDER BY tag")
    suspend fun allTags(): List<String>
```

`ItemRepository.kt` 中 `atLocation` 之后插入（`PhotoOwner` 已在 Task 2 引入）：

```kotlin
    suspend fun allTags(): List<String> = itemDao.allTags()

    suspend fun photosOf(itemId: String): List<String> =
        photoDao.forOwner(PhotoOwner.ITEM, itemId).map { it.filePath }
```

- [x] **Step 2: 写 GalleryViewModel**

`app/src/main/java/com/wheremy/app/ui/gallery/GalleryViewModel.kt`：

```kotlin
package com.wheremy.app.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.repo.Filters
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.ui.common.LocationOption
import com.wheremy.app.ui.common.flattenLocations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val query: String = "",
    val filters: Filters = Filters(),
    val items: List<ItemSummary> = emptyList(),
    val allTags: List<String> = emptyList(),
    val moveId: String? = null,
    val moveTargets: List<LocationOption> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class GalleryViewModel(
    private val items: ItemRepository,
    private val locations: LocationRepository,
    val inboxMode: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val effective = _state.value.filters.copy(onlyInbox = inboxMode || _state.value.filters.onlyInbox)
        val found = items.rankedSearch(_state.value.query, effective)
        val tree = locations.tree()
        _state.update {
            it.copy(
                filters = effective,
                items = items.summaries(found, tree),
                allTags = items.allTags(),
                loading = false,
            )
        }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        viewModelScope.launch { load() }
    }

    fun setCategory(value: String?) {
        mutate { it.copy(category = if (it.category == value) null else value) }
    }

    fun toggleSeason(value: String) {
        mutate { it.copy(season = if (it.season == value) null else value) }
    }

    fun setTag(value: String?) {
        mutate { it.copy(tag = if (it.tag == value) null else value) }
    }

    fun setOnlyInbox(value: Boolean) {
        mutate { it.copy(onlyInbox = value) }
    }

    private fun mutate(block: (Filters) -> Filters) {
        _state.update { it.copy(filters = block(it.filters)) }
        viewModelScope.launch { load() }
    }

    fun prepareMove(itemId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(moveId = itemId, moveTargets = flattenLocations(locations.tree()))
            }
        }
    }

    fun closeMove() {
        _state.update { it.copy(moveId = null, moveTargets = emptyList()) }
    }

    fun move(itemId: String, locationId: String?) {
        viewModelScope.launch {
            runCatching { items.move(itemId, locationId) }
                .onFailure { _state.update { s -> s.copy(error = it.message ?: "搬动失败") } }
            closeMove()
            load()
        }
    }

    fun remove(itemId: String) {
        viewModelScope.launch {
            runCatching { items.delete(itemId) }
                .onFailure { _state.update { s -> s.copy(error = it.message ?: "删除失败") } }
            load()
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}
```

收件箱模式下 `onlyInbox` 被强制为 `true`，用户仍能叠加分类/季节/标签筛选。

- [x] **Step 3: 写 GalleryViewModel 测试**

**注意（实施时改过三处，都是测试自己写错，不是实现错）：** ① 正文漏了 `jpeg()` 助手，`put()` 直接引用它会编不过，已补进文件；② `inboxModeForcesInboxFilter` 原断言 `listOf("没地方放", "未确认")`，但 `sorted()` 按 Unicode 码位排，未 U+672A 在 没 U+6CA1 之前，实际是 `["未确认", "没地方放"]`；③ 同 Task 14，`setUp` 用内存库 + `InlineExecutor`（偏差记录第 9 条），并在 `tearDown` 删掉临时照片目录。

`app/src/test/java/com/wheremy/app/ui/gallery/GalleryViewModelTest.kt`：

```kotlin
package com.wheremy.app.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GalleryViewModelTest {

    private lateinit var db: WheremyDatabase
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository
    private lateinit var photosDir: File
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WheremyDatabase::class.java)
            // 见偏差记录第 9 条：Room 默认把查询派发到 IO 线程，eager 的 Main 调度器会让断言抢跑
            .setQueryExecutor(InlineExecutor)
            .setTransactionExecutor(InlineExecutor)
            .build()
        photosDir = File(ctx.filesDir, "gal-" + System.nanoTime()).also { it.mkdirs() }
        items = ItemRepository(db, PhotoStore(photosDir))
        locations = LocationRepository(db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        photosDir.deleteRecursively()
    }

    private fun vm(inbox: Boolean = false) = GalleryViewModel(items, locations, inbox)

    private suspend fun put(
        name: String,
        locationId: String? = null,
        category: String? = null,
        seasons: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        draft: Boolean = false,
    ): String {
        val id = items.newDraft(jpeg(400, 300))
        val draftModel = ItemDraft(
            id = id, name = name, locationId = locationId, category = category,
            seasons = seasons, tags = tags,
        )
        if (draft) items.keepAsDraft(draftModel) else items.confirm(draftModel)
        return id
    }

    @Test fun listsConfirmedItemsWithThumbs() = runTest {
        val home = locations.add(null, "家")
        put("电钻", home)
        val machine = vm()
        machine.refresh()
        assertEquals(listOf("电钻"), machine.state.value.items.map { it.item.name })
        assertTrue(machine.state.value.items.single().thumbPath!!.endsWith(".jpg"))
        assertEquals("家", machine.state.value.items.single().locationPath)
        assertFalse(machine.state.value.loading)
    }

    @Test fun queryRanksExactNameFirst() = runTest {
        val home = locations.add(null, "家")
        put("外套", home)
        put("深灰色羊毛外套", home)
        val machine = vm()
        machine.setQuery("外套")
        assertEquals("外套", machine.state.value.items.first().item.name)
        assertEquals(2, machine.state.value.items.size)
    }

    @Test fun categoryAndSeasonFiltersCompose() = runTest {
        val home = locations.add(null, "家")
        put("毛衣", home, category = "衣物", seasons = listOf("冬"))
        put("衬衫", home, category = "衣物", seasons = listOf("夏"))
        put("扳手", home, category = "工具")
        val machine = vm()
        machine.setCategory("衣物")
        assertEquals(listOf("毛衣", "衬衫"), machine.state.value.items.map { it.item.name }.sorted())
        machine.toggleSeason("冬")
        assertEquals(listOf("毛衣"), machine.state.value.items.map { it.item.name })
        machine.toggleSeason("冬")
        assertEquals(2, machine.state.value.items.size)
    }

    @Test fun tagFilterUsesAiTags() = runTest {
        val home = locations.add(null, "家")
        put("护照夹", home, tags = listOf("证件"))
        put("身份证", home, tags = listOf("证件"))
        put("袜子", home, tags = listOf("换季"))
        val machine = vm()
        machine.refresh()
        assertEquals(listOf("换季", "证件"), machine.state.value.allTags)
        machine.setTag("证件")
        assertEquals(2, machine.state.value.items.size)
    }

    @Test fun inboxModeForcesInboxFilter() = runTest {
        val home = locations.add(null, "家")
        put("已归类", home)
        put("没地方放", null)
        val draftId = put("未确认", home, draft = true)
        val machine = vm(inbox = true)
        machine.refresh()
        val names = machine.state.value.items.map { it.item.name }
        assertTrue(machine.state.value.filters.onlyInbox)
        // sorted() 按码位排：未 U+672A 在 没 U+6CA1 之前
        assertEquals(listOf("未确认", "没地方放"), names.sorted())
        assertEquals(ItemStatus.DRAFT, items.get(draftId)?.status)
    }

    @Test fun moveClearsLocationAndLeavesInbox() = runTest {
        val home = locations.add(null, "家")
        val id = put("电钻", home)
        val machine = vm(inbox = true)
        machine.refresh()
        assertTrue(machine.state.value.items.isEmpty())
        machine.move(id, null)
        assertNull(items.get(id)?.locationId)
        assertEquals(listOf("电钻"), machine.state.value.items.map { it.item.name })
    }

    @Test fun removeDeletesRowAndFiles() = runTest {
        val home = locations.add(null, "家")
        val id = put("旧物", home)
        val thumb = items.thumbPathOf(id)
        val machine = vm()
        machine.refresh()
        machine.remove(id)
        assertNull(items.get(id))
        assertTrue(machine.state.value.items.isEmpty())
        assertFalse(File(items.photoStore.rootDir, thumb!!).exists())
    }

    @Test fun moveTargetsCoverWholeTree() = runTest {
        val home = locations.add(null, "家")
        locations.add(home, "主卧")
        val id = put("电钻", home)
        val machine = vm()
        machine.refresh()
        machine.prepareMove(id)
        assertEquals(listOf("家", "主卧"), machine.state.value.moveTargets.map { it.label })
        assertEquals(id, machine.state.value.moveId)
        machine.closeMove()
        assertNull(machine.state.value.moveId)
    }

    private fun jpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            return bos.toByteArray()
        }
    }

    /** 在调用线程就地执行，配合 eager 的 Main 测试调度器消除线程切换带来的竞态 */
    private object InlineExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }
}
```

`GalleryViewModelTest` 里 `items.photoStore` 是 Task 9 暴露的 `val photoStore`，删除物品后缩略图文件应当消失。

- [x] **Step 4: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*GalleryViewModelTest" --console=plain`
Expected: 8 个测试 PASS。

- [x] **Step 5: 写照片墙页**

`app/src/main/java/com/wheremy/app/ui/gallery/GalleryScreen.kt`：

```kotlin
package com.wheremy.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wheremy.app.data.repo.CATEGORIES
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.data.repo.SEASONS
import com.wheremy.app.ui.common.LocationPickerDialog
import com.wheremy.app.ui.common.daysUntil
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    vm: GalleryViewModel,
    photosDir: File,
    onOpenItem: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    // 每次进入本页都重读：确认页保存、详情页搬动之后回退到这里要看到最新数据
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.inboxMode) "待归类" else "照片墙") },
                actions = {
                    if (!vm.inboxMode) {
                        FilterChip(
                            selected = state.filters.onlyInbox,
                            onClick = { vm.setOnlyInbox(!state.filters.onlyInbox) },
                            label = { Text("只看待归类") },
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("搜名称、别名、颜色、品牌、描述") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FilterBar(state, vm)
            if (!state.loading && state.items.isEmpty()) {
                Text(
                    text = when {
                        state.query.isNotBlank() -> "没找到匹配的物品，换个说法试试（类别、颜色、使用场景都能搜）。"
                        vm.inboxMode -> "收件箱是空的，都已归类。"
                        else -> "还没有物品。点右下角的按钮选第一张照片。"
                    },
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(state.items, key = { "g-" + it.item.id }) { summary ->
                    GalleryCell(
                        summary = summary,
                        photosDir = photosDir,
                        onClick = { onOpenItem(summary.item.id) },
                        onMenu = { menuFor = summary.item.id },
                    )
                }
            }
        }
    }

    menuFor?.let { id ->
        val summary = state.items.firstOrNull { it.item.id == id }
        if (summary == null) {
            // 列表在菜单打开期间被刷新（例如删除后重读），旧 id 直接丢掉菜单状态
            menuFor = null
        } else {
            AlertDialog(
                onDismissRequest = { menuFor = null },
                title = { Text(summary.item.name.ifBlank { "未命名物品" }) },
                text = {
                    Column {
                        TextButton(
                            onClick = { vm.prepareMove(id); menuFor = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("搬到…", modifier = Modifier.fillMaxWidth()) }
                        TextButton(
                            onClick = { confirmDelete = id; menuFor = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("删除", modifier = Modifier.fillMaxWidth()) }
                    }
                },
                confirmButton = { TextButton(onClick = { menuFor = null }) { Text("关闭") } },
            )
        }
    }

    state.moveId?.let { id ->
        LocationPickerDialog(
            options = state.moveTargets,
            title = "搬到",
            selectedId = state.items.firstOrNull { it.item.id == id }?.item?.locationId,
            onDismiss = { vm.closeMove() },
            onPick = { vm.move(id, it) },
        )
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这件物品？") },
            text = { Text("照片和记录会一起删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.remove(id); confirmDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(state: GalleryUiState, vm: GalleryViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CATEGORIES.forEach { c ->
            FilterChip(
                selected = state.filters.category == c,
                onClick = { vm.setCategory(c) },
                label = { Text(c) },
            )
        }
        SEASONS.forEach { s ->
            FilterChip(
                selected = state.filters.season == s,
                onClick = { vm.toggleSeason(s) },
                label = { Text(s) },
            )
        }
        state.allTags.forEach { t ->
            FilterChip(
                selected = state.filters.tag == t,
                onClick = { vm.setTag(t) },
                label = { Text("#" + t) },
            )
        }
    }
}

@Composable
private fun GalleryCell(
    summary: ItemSummary,
    photosDir: File,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val item = summary.item
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        val rel = summary.thumbPath
        if (rel != null) {
            AsyncImage(
                model = File(photosDir, rel),
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                // 没照片时拿名称占位，照片墙不会是一片空白格子
                Text(
                    text = item.name.take(8).ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.name.ifBlank { "未命名物品" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMenu) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = summary.locationPath ?: "待归类",
            style = MaterialTheme.typography.bodySmall,
            color = if (summary.locationPath == null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.expiresAt?.let { expires ->
            val days = daysUntil(expires)
            Text(
                text = if (days < 0) "已过期 ${-days} 天" else "还有 $days 天到期",
                style = MaterialTheme.typography.labelSmall,
                color = if (days <= 30) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [x] **Step 6: 编译确认照片墙可构建**

Run: `gradle :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL。若 `Icons.Filled.MoreVert` 报 unresolved reference，说明它不在 `material-icons-core` 里，换成文字按钮「操作」并在"实施偏差记录"注明。

- [x] **Step 7: 写 DetailViewModel**

`app/src/main/java/com/wheremy/app/ui/detail/DetailViewModel.kt`：

```kotlin
package com.wheremy.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.ui.common.LocationOption
import com.wheremy.app.ui.common.flattenLocations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val itemId: String? = null,
    val summary: ItemSummary? = null,
    val photos: List<String> = emptyList(),
    val breadcrumb: String? = null,
    val moveTargets: List<LocationOption> = emptyList(),
    val moveOpen: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
)

class DetailViewModel(
    private val items: ItemRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** 每次进入详情页都重读：确认页可能刚改过字段；换物品时先丢掉旧状态，避免旧名称闪一下 */
    fun load(itemId: String) {
        val sameItem = _state.value.itemId == itemId
        _state.update {
            if (sameItem) it.copy(loading = true, error = null)
            else DetailUiState(itemId = itemId)
        }
        viewModelScope.launch { read(itemId) }
    }

    private suspend fun read(id: String) {
        val entity = items.get(id)
        if (entity == null) {
            _state.update { it.copy(loading = false, error = "这条记录已经不在了，可能刚被删除") }
            return
        }
        val tree = locations.tree()
        _state.update {
            it.copy(
                summary = items.summaries(listOf(entity), tree).single(),
                photos = items.photosOf(id),
                breadcrumb = entity.locationId?.let { loc -> tree.breadcrumb(loc).ifBlank { null } },
                loading = false,
            )
        }
    }

    fun prepareMove() {
        viewModelScope.launch {
            _state.update { it.copy(moveOpen = true, moveTargets = flattenLocations(locations.tree())) }
        }
    }

    fun closeMove() {
        _state.update { it.copy(moveOpen = false, moveTargets = emptyList()) }
    }

    fun moveTo(locationId: String?) {
        val id = _state.value.itemId ?: return
        viewModelScope.launch {
            runCatching { items.move(id, locationId) }
                .onFailure { _state.update { s -> s.copy(error = it.message ?: "搬动失败") } }
            closeMove()
            read(id)
        }
    }

    fun remove() {
        val id = _state.value.itemId ?: return
        viewModelScope.launch {
            runCatching { items.delete(id) }
                .onSuccess { _state.update { s -> s.copy(deleted = true) } }
                .onFailure { _state.update { s -> s.copy(error = it.message ?: "删除失败") } }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    fun consumeDeleted() {
        _state.update { it.copy(deleted = false) }
    }
}
```

`moveTo` 之后直接调 `read(id)`：此时 `itemId` 已确定，少一层封装也少一次状态抖动。

- [x] **Step 8: 写 DetailViewModel 测试**

本文件同样套用偏差记录第 9 条（内存库 + `InlineExecutor`），并补了 `photosDir.deleteRecursively()`。

`app/src/test/java/com/wheremy/app/ui/detail/DetailViewModelTest.kt`：

```kotlin
package com.wheremy.app.ui.detail

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DetailViewModelTest {

    private lateinit var db: WheremyDatabase
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository
    private lateinit var photosDir: File
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WheremyDatabase::class.java)
            // 见偏差记录第 9 条：Room 默认把查询派发到 IO 线程，eager 的 Main 调度器会让断言抢跑
            .setQueryExecutor(InlineExecutor)
            .setTransactionExecutor(InlineExecutor)
            .build()
        photosDir = File(ctx.filesDir, "detail-" + System.nanoTime()).also { it.mkdirs() }
        items = ItemRepository(db, PhotoStore(photosDir))
        locations = LocationRepository(db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        photosDir.deleteRecursively()
    }

    private fun jpeg(): ByteArray {
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        return out.toByteArray()
    }

    private suspend fun put(name: String, locationId: String?): String {
        val id = items.newDraft(jpeg())
        items.confirm(ItemDraft(id = id, name = name, locationId = locationId, category = "工具"))
        return id
    }

    @Test fun loadsSummaryPhotosAndBreadcrumb() = runTest {
        val home = locations.add(null, "家")
        val wardrobe = locations.add(home, "衣柜")
        val id = put("羽绒服", wardrobe)
        val vm = DetailViewModel(items, locations)
        vm.load(id)
        val s = vm.state.value
        assertEquals("羽绒服", s.summary?.item?.name)
        assertEquals("家 / 衣柜", s.breadcrumb)
        assertEquals(1, s.photos.size)
        assertTrue(File(photosDir, s.photos.single()).exists())
        assertFalse(s.loading)
    }

    @Test fun moveToRewritesBreadcrumbAndClosesDialog() = runTest {
        val home = locations.add(null, "家")
        val box = locations.add(null, "储物箱")
        val id = put("电钻", home)
        val vm = DetailViewModel(items, locations)
        vm.load(id)
        vm.prepareMove()
        assertTrue(vm.state.value.moveOpen)
        assertEquals(listOf("家", "储物箱"), vm.state.value.moveTargets.map { it.label })
        vm.moveTo(box)
        assertFalse(vm.state.value.moveOpen)
        assertEquals("储物箱", vm.state.value.breadcrumb)
        assertEquals(box, items.get(id)?.locationId)
    }

    @Test fun moveToInboxClearsLocationAndShowsPending() = runTest {
        val home = locations.add(null, "家")
        val id = put("旧手机", home)
        val vm = DetailViewModel(items, locations)
        vm.load(id)
        vm.moveTo(null)
        assertNull(vm.state.value.breadcrumb)
        assertNull(vm.state.value.summary?.locationPath)
        assertNull(items.get(id)?.locationId)
    }

    @Test fun removeMarksDeletedAndDeletesPhotoFile() = runTest {
        val id = put("过期药", null)
        val thumbBefore = items.thumbPathOf(id)!!
        val vm = DetailViewModel(items, locations)
        vm.load(id)
        vm.remove()
        assertTrue(vm.state.value.deleted)
        assertNull(items.get(id))
        assertFalse(File(photosDir, thumbBefore).exists())
    }

    @Test fun loadMissingItemReportsErrorInsteadOfBlankPage() = runTest {
        val vm = DetailViewModel(items, locations)
        vm.load("not-there")
        assertFalse(vm.state.value.loading)
        assertEquals("这条记录已经不在了，可能刚被删除", vm.state.value.error)
        assertNull(vm.state.value.summary)
        vm.consumeError()
        assertNull(vm.state.value.error)
    }

    /** 在调用线程就地执行，配合 eager 的 Main 测试调度器消除线程切换带来的竞态 */
    private object InlineExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    @Test fun moveTargetsCoverWholeTree() = runTest {
        val home = locations.add(null, "家")
        val room = locations.add(home, "主卧")
        locations.add(room, "抽屉")
        val id = put("充电线", room)
        val vm = DetailViewModel(items, locations)
        vm.load(id)
        vm.prepareMove()
        assertEquals(listOf("家", "主卧", "抽屉"), vm.state.value.moveTargets.map { it.label })
        assertEquals(listOf(0, 1, 2), vm.state.value.moveTargets.map { it.depth })
        vm.closeMove()
        assertFalse(vm.state.value.moveOpen)
        assertTrue(vm.state.value.moveTargets.isEmpty())
    }
}
```

- [x] **Step 9: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "*DetailViewModelTest" --console=plain`
Expected: 6 个测试 PASS。`loadsSummaryPhotosAndBreadcrumb` 若得到 2 条照片，说明 `photosOf` 误用了 `PhotoDao.forOwner` 之外的查询——它只应返回每个位置的原图 `filePath`，一个物品一张原图即 1 条。

- [x] **Step 10: 写详情页**

`app/src/main/java/com/wheremy/app/ui/detail/DetailScreen.kt`：

```kotlin
package com.wheremy.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.ui.common.LocationPickerDialog
import com.wheremy.app.ui.common.daysUntil
import com.wheremy.app.ui.common.formatDate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: DetailViewModel,
    itemId: String,
    photosDir: File,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    // 从录入页返回时本页面已整体离开组合，effect 会重新执行，天然拿到最新数据
    LaunchedEffect(itemId) { vm.load(itemId) }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.consumeError()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            vm.consumeDeleted()
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.summary?.item?.name?.ifBlank { "未命名物品" } ?: "物品详情",
                        maxLines = 1,
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    if (state.summary != null) {
                        TextButton(onClick = { onEdit(itemId) }) { Text("编辑") }
                        TextButton(onClick = { vm.prepareMove() }) { Text("搬到") }
                        TextButton(onClick = { confirmDelete = true }) { Text("删除") }
                    }
                },
            )
        },
    ) { padding ->
        val summary = state.summary
        if (summary == null) {
            Text(
                text = if (state.loading) "读取中…" else "没有这条记录。",
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.photos.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(state.photos, key = { it }) { rel ->
                            AsyncImage(
                                model = File(photosDir, rel),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(200.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                LocationCard(state.breadcrumb, summary)
                FieldCard(summary)
                Text(
                    text = "更新于 ${formatDate(summary.item.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(" ", modifier = Modifier.height(24.dp))
            }
        }
    }

    if (state.moveOpen) {
        LocationPickerDialog(
            options = state.moveTargets,
            title = "搬到",
            selectedId = state.summary?.item?.locationId,
            onDismiss = { vm.closeMove() },
            onPick = { vm.moveTo(it) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这件物品？") },
            text = { Text("照片和记录会一起删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.remove() }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LocationCard(breadcrumb: String?, summary: ItemSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("位置", style = MaterialTheme.typography.labelLarge)
            Text(
                text = breadcrumb ?: "待归类（还没放好）",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (breadcrumb == null) FontWeight.Bold else FontWeight.Normal,
                color = if (breadcrumb == null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        summary.tags.forEach { tag ->
            Text(
                text = "#" + tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun FieldCard(summary: ItemSummary) {
    val item = summary.item
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
    ) {
        DetailRow("分类", item.category)
        DetailRow("季节", item.seasons?.replace(",", "、"))
        DetailRow("颜色", item.color)
        DetailRow("材质", item.material)
        DetailRow("品牌", item.brand)
        DetailRow("别名", item.aliases)
        DetailRow("数量", if (item.quantity == 1) null else "x${item.quantity}")
        DetailRow(
            "到期",
            item.expiresAt?.let {
                val days = daysUntil(it)
                val hint = if (days < 0) "已过期 ${-days} 天" else "还有 $days 天"
                "${formatDate(it)}（$hint）" + (item.expireKind?.let { k -> " · $k" } ?: "")
            },
        )
        DetailRow("描述", item.description)
        DetailRow("备注", item.notes)
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
```

返回按钮用 `TextButton` 而不是箭头图标，和位置树页的「上一层」一致，也避免再依赖 `material-icons-extended`。

- [x] **Step 11: 跑全量单测 + 编译**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: 全绿。

- [x] **Step 12: 提交**

```bash
git add app/src
git commit -m "feat: 照片墙、筛选与物品详情页"
```

---

### Task 16: 外壳与导航（三 Tab + 拍照入口 + 路由状态）

**Files:**
- Create: `app/src/main/java/com/wheremy/app/ui/WheremyRoot.kt`
- Modify: `app/src/main/java/com/wheremy/app/MainActivity.kt`（替换 Task 1 的占位 `setContent`）

**Interfaces:**
- Consumes: Task 11 `AppContainer`/`WheremyApp`/`appViewModel(container, key, make)`/`LocalAppContainer`、Task 13 `WheremyTheme`/`pendingCapture`/`ReviewScreen`/`ReviewViewModel`、Task 14 `TreeScreen`/`TreeViewModel`、Task 15 `GalleryScreen`/`GalleryViewModel`/`DetailScreen`/`DetailViewModel`。
- Produces: `@Composable fun WheremyRoot()`

不引入 `navigation-compose`（版本没在本地 Gradle 8.14.3 + Compose BOM 2026.09.00 上验证过，且三个页面 + 两个全屏路由用不到路由栈）：路由状态就是四个 `rememberSaveable` 字段，屏幕之间互斥（全屏页直接 `return`），旋转/进程重建靠 SaveableState 恢复。

`appViewModel` 的 `key` 在本任务用上：录入页每次进入都要一个干净的 `ReviewViewModel`（`started`/`touched` 是一次性状态），所以进入时把 `reviewSeq` 自增，key 变成 `review-1`、`review-2`……老实例留在 store 里直到 Activity 销毁，每个只占几 KB，不构成泄漏。

- [x] **Step 1: 写 `WheremyRoot`**

`app/src/main/java/com/wheremy/app/ui/WheremyRoot.kt`：

```kotlin
package com.wheremy.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheremy.app.di.LocalAppContainer
import com.wheremy.app.di.appViewModel
import com.wheremy.app.ui.detail.DetailScreen
import com.wheremy.app.ui.detail.DetailViewModel
import com.wheremy.app.ui.gallery.GalleryScreen
import com.wheremy.app.ui.gallery.GalleryViewModel
import com.wheremy.app.ui.review.ReviewScreen
import com.wheremy.app.ui.review.ReviewViewModel
import com.wheremy.app.ui.tree.TreeScreen
import com.wheremy.app.ui.tree.TreeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RootTab { GALLERY, TREE, INBOX }

@Composable
fun WheremyRoot() {
    val container = LocalAppContainer.current
    val photosDir = container.photoStore.rootDir
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(RootTab.GALLERY.name) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewOpen by rememberSaveable { mutableStateOf(false) }
    /** null = 拍照新增；非空 = 编辑已有物品 */
    var reviewItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewSeq by rememberSaveable { mutableStateOf(0) }
    val currentTab = RootTab.entries.firstOrNull { it.name == tab } ?: RootTab.GALLERY

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // 选到的可能是几 MB 的原图，别在主线程读
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes == null || bytes.isEmpty()) {
                    snackbar.showSnackbar("读不到这张照片，换一张试试")
                } else {
                    container.pendingCapture.value = bytes
                    reviewItemId = null
                    reviewSeq += 1
                    reviewOpen = true
                }
            }
        }
    }
    val pickPhoto = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    if (reviewOpen) {
        val vm = appViewModel(container, key = "review-$reviewSeq") {
            ReviewViewModel(it.captureFlow, it.items, it.locations)
        }
        LaunchedEffect(Unit) {
            val pending = container.pendingCapture.value
            val editId = reviewItemId
            when {
                pending != null -> vm.start(pending)
                editId != null -> vm.load(editId)
                // 进程被系统杀掉后恢复：字节只活在内存里，续不上就退回原页面。
                // 这里不能 showSnackbar——录入页在组合树里时外壳的 SnackbarHost 不存在，会一直挂起
                else -> reviewOpen = false
            }
        }
        val leaveReview = {
            container.pendingCapture.value = null
            reviewOpen = false
        }
        BackHandler(onBack = leaveReview)
        ReviewScreen(
            vm = vm,
            photosDir = photosDir,
            onDone = leaveReview,
            onPickImage = pickPhoto,
        )
        return
    }

    detailId?.let { id ->
        val vm = appViewModel(container, key = "detail") { DetailViewModel(it.items, it.locations) }
        BackHandler(onBack = { detailId = null })
        DetailScreen(
            vm = vm,
            itemId = id,
            photosDir = photosDir,
            onBack = { detailId = null },
            onEdit = { itemId ->
                container.pendingCapture.value = null
                reviewItemId = itemId
                reviewSeq += 1
                reviewOpen = true
            },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == RootTab.GALLERY,
                    onClick = { tab = RootTab.GALLERY.name },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("照片墙") },
                )
                NavigationBarItem(
                    selected = currentTab == RootTab.TREE,
                    onClick = { tab = RootTab.TREE.name },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("位置") },
                )
                NavigationBarItem(
                    selected = currentTab == RootTab.INBOX,
                    onClick = { tab = RootTab.INBOX.name },
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                    label = { Text("待归类") },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = pickPhoto) {
                Icon(Icons.Filled.Add, contentDescription = "选照片录入")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (currentTab) {
                RootTab.GALLERY -> {
                    val vm = appViewModel(container, key = "gallery") {
                        GalleryViewModel(it.items, it.locations, inboxMode = false)
                    }
                    GalleryScreen(vm = vm, photosDir = photosDir, onOpenItem = { detailId = it })
                }

                RootTab.TREE -> {
                    val vm = appViewModel(container, key = "tree") {
                        TreeViewModel(it.items, it.locations)
                    }
                    // 下钻时返回键先退一层；已在根层级不拦截，交回系统（退出应用）
                    val atRoot = vm.state.collectAsStateWithLifecycle().value.breadcrumb.isEmpty()
                    BackHandler(enabled = !atRoot) { vm.up() }
                    TreeScreen(vm = vm, photosDir = photosDir, onOpenItem = { detailId = it })
                }

                RootTab.INBOX -> {
                    val vm = appViewModel(container, key = "inbox") {
                        GalleryViewModel(it.items, it.locations, inboxMode = true)
                    }
                    GalleryScreen(vm = vm, photosDir = photosDir, onOpenItem = { detailId = it })
                }
            }
        }
    }
}
```

几个刻意的选择：
- 三个 Tab 各自一个 `GalleryViewModel`/`TreeViewModel` 实例（key 不同），照片墙的搜索词和收件箱的筛选互不干扰；实例创建一次，切走再切回不丢状态。
- 全屏页（录入、详情）在 Tab Scaffold 之外提前 `return`，所以 Tab 的选中态在返回后仍然停原来的位置。
- 返回键在三处各拦一次：录入页和详情页直接退出该层，位置树在已下钻时退一层、在根层级不拦截（`BackHandler(enabled = …)`），交回系统行为。

- [x] **Step 2: 接线 `MainActivity`**

整体替换 Task 1 写的占位实现：

```kotlin
package com.wheremy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.wheremy.app.di.LocalAppContainer
import com.wheremy.app.ui.WheremyRoot
import com.wheremy.app.ui.theme.WheremyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WheremyApp).container
        setContent {
            WheremyTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    WheremyRoot()
                }
            }
        }
    }
}
```

`LocalAppContainer` 由这里提供、由 `WheremyRoot` 读取：容器只在一处被消费，不做第二份参数透传。

- [x] **Step 3: 编译 + 全量单测**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: 全绿。外壳没有新的 JVM 单测：它只做路由与协程派发，Compose 点按测试要新增 `ui-test-junit4`（版本未在本机验证过），收益低于它在 Task 17 手工清单里被逐条走一遍。若图标 `Icons.Filled.Warning`/`List`/`Home`/`Add` 有 unresolved，全部换成 `Text("照片墙")` 形式的 label-only `NavigationBarItem`（`icon = { Text("墙") }`），并在"实施偏差记录"注明。

- [x] **Step 4: 提交**

```bash
git add app/src
git commit -m "feat: 外壳导航、拍照入口与返回键处理"
```

---

### Task 17: 全量回归与真机手工验收（M0–M4 收口）

**Files:**
- Modify: 任意（清单里发现的缺陷就地修复，修一条提一次）

**Interfaces:**
- Consumes: Task 1–16 的全部产物。
- Produces: 一个能侧载使用的 `app-debug.apk`，以及逐条勾选过的验收清单。

- [x] **Step 1: 全量单测 + 构建**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: 全绿，产物 `app/build/outputs/apk/debug/app-debug.apk`。
先记下测试总数（Task 1–15 累计约 137 个 `@Test`），后面若某个任务改坏了能对比出来。

实测（2026-10-02 复核修正后）：`BUILD SUCCESSFUL`，**149 个 `@Test` 全绿（0 失败 / 0 跳过）**，`app-debug.apk` 13.95 MB。Task 16 的四个图标 `Icons.Filled.Home`/`List`/`Warning`/`Add` 与 Task 14 的 `MoreVert` 都在 `material-icons-core` 里解析成功，不必降级成文字 label（偏差记录第 5 条的那条后备说明未触发）。`Icons.Filled.List` 已换成 `Icons.AutoMirrored.Filled.List`，只剩这一处 deprecation 警告要处理。

Task 13–16 交付之后做了一轮独立代码评审（只看新增 UI 层），11 条发现里 7 条确认属实并已修复，每条先红后绿，共 6 个 `fix:` 提交：

| 发现 | 症状 | 修法 |
|---|---|---|
| 迟到的 AI 改写已保存物品 | 保存之后分析才回来，物品名被模型猜的值换掉、还白加一个标签 | `ItemRepository.applyAnalysis` 开头 `if (cur.status != ItemStatus.DRAFT) return` |
| 到期日被迟到分析抹掉 | `ReviewViewModel.merge` 的字段表里没有 `expiresAt`/`expireKind`，而这两项只活在表单里，库里读回必定 null | merge 补两项，共用 `"expiresAt"` 这个 touched 键（`setExpires` 一次写日期+类型） |
| 采集链路跑在主线程 | 解码原图 + 三次缩放压缩 + 写两个文件全在 `viewModelScope`（Main）上，几百毫秒卡顿 | `CaptureFlow` 加 `io: CoroutineDispatcher = Dispatchers.IO` 参数，`capture`/`reanalyze` 整体 `withContext(io)`；单测传 Unconfined 保持就地跑完 |
| 抛出路径没人接 | 写盘失败/库损坏直接掀掉协程，转圈或空白表单永远留着 | Review 的 `guard{}`、Tree/Gallery 的 `safeLoad()`、Detail 的 `safeRead()`；`ReviewViewModel.load` 读不到物品时写 `state.error` 而不是静默 return |
| 录入页"换一张"后表单是死的 | `LaunchedEffect(Unit)` 不跟着 `reviewSeq` 重跑，新 VM 永远不 `start`，保存/稍后处理都点不动，照片丢了 | 改成 `LaunchedEffect(reviewSeq)`；选图读不到字节时，录入页开着就不弹 snackbar（外壳的 host 不在组合树里会一直挂起） |
| 过期显示"还剩 -31 天" | 三个界面各自拼倒计时字符串，只有录入页漏了负数分支 | 收拢成 `UiFormat.expireHint(days)`，补"今天到期"；日期没选时确定按钮 `enabled = false` |
| 降级文案指向不存在的页面 | 没配 key 时提示"可在设置页补全"，而设置页是 Plan 2 的事 | 文案改成"字段留空，直接手填就行"，并补测试钉住 spec 第 8 节"每种失败都要有中文提示" |

评审里另外 2 条性能项确认存在但没动，因为它们不影响这次验收（首批数据几十条），留给你按真实用量决定：`TreeViewModel.load` 对每个子位置各发一次 `subtreeItemCount`（内部是 `tree()` + 全表 `itemDao.all()`），物品多了以后每次下钻是 O(子节点数) 次全表扫描；`GalleryViewModel.setQuery` 每敲一个字都全表 LIKE 一遍，没有 debounce。改法分别是加一条 `GROUP BY location_id` 的聚合查询、以及把查询收进 `Flow.debounce` 管道。第二轮评审把后一条的**正确性**部分先修掉了（旧结果不许盖新结果），debounce 本身仍然没加。

### 第二轮评审（2026-10-02，覆盖 Task 9–16 的核心闭环）

又一轮独立评审，这次读的是录入→确认→搜索→照片墙→详情这一段。10 条发现，对着代码逐条复核之后改了 5 条（三个 `fix:` 提交，每个都以全量绿收尾）、驳回 3 条、只记录 2 条。测试数 186 → **191**（`25` 个类，0 失败 0 跳过）。

| 发现 | 判定 | 去处 |
|---|---|---|
| 「稍后处理」会把物品的位置抹掉 | 属实，改 | `ab3f430`：`keepAsDraft` 只退回 DRAFT。收件箱谓词本来就是"没位置 **或** DRAFT"，清空位置对"进收件箱"是多余的，而从详情页点编辑再按稍后处理就会把物品从原位扔出去 |
| 标签 chip 的筛选是 `searchText.contains(tag)` | 属实，改 | `ab3f430`：新增 `ItemDao.itemIdsWithTag`，判 `item_tag` 里的行。描述里提过一次"冬装"的物品不该被当成打了冬装标签 |
| 照片墙文案承诺"颜色、类别都能搜" | 属实，改文案 | `ab3f430`：spec 第 4 节的 `search_text` 只有 名称/品牌/别名/标签/描述/备注。第二行改成指向筛选条。**颜色要不要进 `search_text` 是产品判断，没替你改**（改了老物品还得重存一次才带上） |
| `retryAnalysis` 拿到 null 就什么都不做 | 属实，改 | `fdf2c85`：照片文件已不存在时 `reanalyze` 只能返回 null，那条分支没人接，转圈永远停不下来。现在停转圈并说清下一步 |
| 连续输入时旧结果能盖掉新结果 | 属实，改 | `fdf2c85`：每次读库领一个号，落地时对不上号就丢弃。**这条没有单测**：`ItemRepository`/`LocationRepository` 是具体类，测试里造不出"旧查询后到"的时序（Room 就地跑完，根本不会交错） |
| 名称为空时该禁用「保存」 | 驳回 | 现在按下保存会得到"名称不能为空"，比一个不给理由的灰按钮更有教益；`saveWithoutNameReportsErrorAndKeepsDraft` 钉着这个行为 |
| 进程重建会重复入库一条物品 | 驳回（说法不成立），但顺手记另一件 | `pendingCapture` 只活在内存里，重建后 `LaunchedEffect` 走的是 `else -> reviewOpen = false`，不会再 `capture` 一次。真正的问题是**录入页里"换一张"会新建一条 draft，上一条空壳 DRAFT 被留在收件箱**。修它要的是"替换照片"（删旧行旧文件 + 写新的），那是功能不是缺陷，等你定优先级 |
| `flattenLocations` 的递归缺环保护 | 驳回（可证明走不到） | 位置是"每个节点一个 parent"的指针图，环必然自成连通分量，因此从 `roots()` 前序展开永远进不到环里。真要出现环，先坏的是 `buildLocationTree` 而不是这里 |
| `summaries()` 与 `subtreeItemCount` 的 N+1 | 记录，不改 | 每件物品 2 条查询（tags + thumb），300 件就是 600 条。批量写法是 `WHERE itemId IN (...)` + 一条 `GROUP BY location_id`。验收期间不想动读路径 |
| `fallbackToDestructiveMigration(true)` 会清库 | 记录，现在做不了 | 写一条真 Migration 需要上一版本的 schema JSON：`exportSchema = true` 是开着的，但这台机器上从没产出过 v1 的 `1.json`，而验收期间 schema 冻结。放开冻结后的第一件事就是它 |

顺带两条同批次的记录：`confirm`/`keepAsDraft` 的"物品行 + 标签行"两次写也没包事务，但后果轻得多（顶多标签少写，重存一次就好），没动；`newDraft` 包进事务之后（`55f0a95`），Room 的协程事务沿用**调用方线程**，Robolectric 下那就是主线程，于是四个 ViewModel 测试类的 28 条用例集体报 `Cannot access database on the main thread`——按 `BackupRepositoryTest` 已有的处理给这四个类加了 `allowMainThreadQueries()`，理由写进各自 setUp 的注释。生产路径不受影响：`newDraft` 只在 `CaptureFlow` 的 `withContext(io)` 里被调。

### 第三轮评审（2026-10-02，两份独立只读报告）与「换一张」

两份报告都是只读评审，逐条对着代码复核之后，确认属实的一起改掉了：**`1a023c6`**（测试 191 → **199**）修提醒语义、标签大小写、设置页与 snackbar；**`5b30ee1`**（199 → **207**）把录入页那颗点不到的「换一张」做成真能换。Room schema 仍然一个字没动，验收期间的冻结有效。

- **收件箱里的东西永远收不到保质期提醒。** 提醒 SQL 带着 `status = 'CONFIRMED'`，而"稍后处理"和"选了照片还没确认"的都是 DRAFT——恰恰是最容易放坏的那一批。条件去掉了，`draftItemIsRemindedToo` / `draftWithADeadlineIsRemindedToo` 钉住。
- **拒了通知权限，那条物品就永久沉默。** `doWork()` 之前不管有没有真的发出去都回写 `expireNotifiedAt`，等于用户一次没注意点了"拒绝"，那件东西的提醒就被吃掉了。现在只在 `canNotify` 为真、通知确实发出去之后才回写（`deniedPermissionPostsNothingAndLeavesTheItemAnnouncable`：先拒 → 断言没有标记也没有通知；再授 → 同一条物品冒出通知）。
- **提醒的总则重写成"每件物品每个截止日期最多说两次"。** 旧谓词要么到期当天不说、要么过期后天天说。现在是 `expireNotifiedAt IS NULL`（进窗口那一次）`OR (expireNotifiedAt < 今天零点 AND expiresAt >= 今天零点 AND expiresAt < 明天零点)`（到期当天那一次），`ReminderEngine` 多传一个 `todayEnd`。三条边界各有单测，包括"昨天提醒过、明天到期"必须保持安静。
- **改了到期日不会重新排队。** 新增私有 `reArm(from, to)`：日期变了就把 `expireNotifiedAt` 抹掉，`confirm` 与 `keepAsDraft` 两条写路径都过它。这是 C 段第 9 条那一句的实现，也是没有加新列的前提下保住"改日期可再次触发"的做法（schema 冻结期不动表结构）。
- **标签大小写。** AI 给 `MacBook`、用户手打 `macbook` 是常事：筛选改成 `LOWER(tag) = LOWER(:tag)`，chip 列表 `allTags()` 按 lowercase 去重，否则同一个标签出现两行。
- **设置页连点吞键。** 原来 `setLeadDays` 是"写 DataStore 之后再 `current()` 读回来更新状态"，而 `runTest` 里连点五下每发都基于旧 state 算——实测 `expected:<8> but was:<7>`、`expected:<90> but was:<3>`。现在 VM 自己用 `SettingsRepository.LEAD_DAYS_MIN/MAX` 先夹好显示、不再读回，`init` 那次异步读加了 `leadDaysTouched` 门，免得它把乐观值踩掉；写盘失败退回原值并说一句中文。`rapidTapsAccumulateInsteadOfSwallowingEachOther` 从 3 连点五下必须落在 8。
- **设置页读不到已存值时不该崩。** `runCatching { settings.current() }`，失败就按默认值显示并说「读不到已存的设置，先按默认值显示」。
- **snackbar 会重放、会互相顶掉。** `message` 与 `error` 合成一个 key 的 `LaunchedEffect` 里，消费其中一个会改变组合 key、重启另一个，于是 error 永远被跳过——设置页与位置树拆成两个 effect；照片墙与详情页原来是"先 `showSnackbar` 再消费"，而 `showSnackbar` 挂起到你把界面划走为止，离开页面几分钟后消息自己冒出来，改成先消费再显示。**（这条第三轮的结论本身是错的：先消费会让 effect 的 key 变化、正在挂起的 `showSnackbar` 被当场取消，条幅压根没显示。第四轮 `8c62ab0` 用 `ui/common/SnackbarMessage.kt` 的两段 effect 同时解决重放与自我取消。）**
- **`MutableStateFlow.update { }` 是 inline 的**，所以写在它 lambda 里的挂起调用会被拖进 CAS 重试循环——`GalleryViewModel` 里两次读库被挪到 `update` 之前，读完再对号落地。
- **半途失败不留脏文件。** `PhotoStore.write` 自己扫（磁盘满时它写出的那两个路径还没有任何库行认得，仓库层的清理拿不到就白搭）；`ItemRepository.swapPhoto` 落库失败时删掉新写的那一对，旧图原样留在行上。
- **一处我自己的手误**：改提醒 SQL 时 `old_string` 越界，把 `allTags`/`itemIdsWithTag`/`countAt` 三个方法一起删了。编译当场就红，不是评审发现的——记在这里是因为它说明"大段替换 DAO 文件"这件事本身有风险，改 `Daos.kt` 要用最小的锚点。

「换一张」这一条是评审之外的收获：报告里说"UI 承诺了一个不存在的入口"，复核发现那颗按钮的条件是 `itemId == null && !analyzing`，而物品行在选图那一刻就落库了，所以它**几乎永远点不到**。后果不是不便而是丢数据——选错照片只能"关闭 → 去收件箱删掉那具没名字的壳 → 重录一遍"，手工填的字段全没。所以按功能修（细节见 A 段那条新增项与提交说明），顺带把"物品一落库就放开保存"（`capture` 的 `onDraftSaved` 回调）和"保存那一刻取消在飞分析"做了。

### 另外两处记录（没改，但不是本轮评审的发现）

- `TreeViewModel.load` 的 N+1（每个子位置一次全表 `itemDao.all()`）与 `GalleryViewModel.setQuery` 没有 debounce，理由同第二轮：几十条物品测不出差别，改动会碰到读路径。
- 位置树里**没有**长按，也没有对物品的操作：树上那颗 ⋮ 管的是位置节点（改名 / 移动到… / 删除）。搬一件物品走详情页「搬到」或照片墙卡片 ⋮。spec 第 6.2 节提过的"位置节点排序"仍然没做。

### 第四轮自审（2026-10-02，我自己读界面代码找"说了没做"的话）`8c62ab0`

前三轮都在追数据与状态机，这一轮换个角度：**每一条中文文案、每一颗按钮，代码真的做得到吗。** 测试 207 → **214**，Room schema 仍然没动。

1. **上一轮的"先消费再显示"是我自己造的新 bug。** `LaunchedEffect(state.error) { consume(); showSnackbar() }` 里，`consume()` 改的是这个 effect 自己的 key → effect 重启 → 正在挂起的 `showSnackbar` 被取消，**条幅根本没显示出来**，错误就此静默。四个页面（照片墙/位置树/详情/设置）本来各写一份，现在统一成 `ui/common/SnackbarMessage.kt`：第一个 effect 把文案抄进本地状态并立刻消费 VM 状态，第二个 effect 才 `showSnackbar` 并等它自己消失。两件事分开，既不会自我取消，也不会在切回页面时重放。上一轮那句"改成先消费再显示"因此作废。
2. **位置的改名会被悄悄抹掉。** 编辑框一次提交名称+类型，`TreeScreen` 却调了 `rename` 与 `setKind` 两笔**独立的读改写**，第二笔拿到的是第一笔落地前的旧行——用户改了名，最后落库的还是旧名。合成 `LocationRepository.update(id, name, kind)` 一笔写完，`TreeViewModel.updateNode` 一个入口，顺带回一句「已更新「新名字」」（以前改名成功是**什么都不说**的）。
3. **`canNotify` 只看运行时授权。** 系统里把应用通知总开关关掉（权限仍然给着）时 `notify()` 静默失败，Worker 却照样回写 `expireNotifiedAt`——和第 2 条一样是"欠了账还说说过"。加上 `NotificationManagerCompat.areNotificationsEnabled()`。同时设置页分成两句话：没给权限才给「开启通知」按钮，权限给了、系统里关着就只告诉你去哪儿开，**不再摆一颗按了没反应的死按钮**。
4. **空态与失败态会说谎。** 有搜索词或筛选条件时空态仍说"还没有物品。点右下角的按钮选第一张照片"（库里可能满满当当）；详情页读库抛异常时正文写"没有这条记录。"，而记录其实在；物品被别处删掉后重新读，旧卡片还挂着，「编辑/搬到/删除」对着空气。三处分别改成各自的真话（`DetailUiState.missing` 就是为区分"没有"与"读不出来"加的）。
5. **两颗点不动的按钮。** 照片压根没落库时「重新分析」按下去不会有任何反应（`retryAnalysis` 第一行就 `?: return`），现在它只在 `itemId != null` 时出现。超时文案里的"15 秒"是从 `AppContainer.CALL_TIMEOUT_SECONDS` 手抄进文案的，改常量就谎，去掉数字。
6. **原地移动不许说"已移动到新层级"。** `changeParent` 现在返回 Boolean，`TreeViewModel.move` 据此说「已经在这一层级，没有改动」。
7. **两处小毛病。** 位置树首帧 `loading` 从没被读，进页面会闪一下"还没有位置。点右上角「新建」…"；提前天数 0 显示成「提前 0 天提醒」（预设按钮那里它明明叫「当天」），现在说「当天到期提醒」，且两个方向键在 0/90 边界上禁用。

**这轮之后仍然没测的部分**：`SnackbarMessage`、空态文案、按钮禁用这几条是 Compose 层的，本项目的测试只到 ViewModel 与仓库，**没有 Compose UI 测试**，所以它们只有 A/C 段那几条手工验收能覆盖。这一点写在这里，别让"214 条全绿"看起来像覆盖了它们。（**这句在 2026-10-03 过期了**：批次 M 把其中能钉的几条搬进了单测，见下面「批次 M」。）

**下面这些 Task 内的代码清单已被上面推翻，按源码为准**（历史清单不重写，只在这里集中指认）：Task 10 的 `LocationRepository` 接口与实现里的 `rename`/`setKind`（现为 `update`，`changeParent` 返回 Boolean）、Task 10 的 `renameKeepsChildrenAndRejectsClash`/`setKindOverwrites` 两条用例、Task 14 的 `TreeViewModel` 方法表与 `rename`/`setKind` 两个实现、Task 14 的 `renameAndSetKindUpdateRows` 用例、Task 14 里 `LocationEditDialog` 的 `onConfirm`、Task 15 的 `AiStatusLine`（多一个 `canRetry` 参数）、Task 13 的 `failureText` 超时那条、计划 2 Task 5 的 `canNotify`。

- [x] **Step 2: 装到手机上**（2026-10-03 装了：`adb devices` 有 `9aa4b43e`（Xiaomi 13 / HyperOS），`adb install -r` 直接跑会被 MIUI 挡成 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。可行的一串是先 `input keyevent KEYCODE_HOME` 把应用退到后台（那个确认框属于 `com.miui.securitycenter`，屏幕上得有它）、安装命令挂后台、三五秒后点掉「继续安装」（`bounds=[99,2119][523,2257]`，旁边那颗是**带倒计时的「拒绝（4）」**，别按错），日志给 `Performing Streamed Install` → `Success`。装的是批次 V 那个 `app-debug.apk`，14,345,283 字节）

有 adb 时：

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

没有 adb 设备也能验收：把 `app/build/outputs/apk/debug/app-debug.apk` 拷到手机（微信文件传输、数据线拷贝、网盘都行）后点开安装，系统会提示允许"安装未知来源应用"。产物是 debug 签名，minSdk 26，包名 `com.wheremy.app`，`allowBackup="false"`。仍然不要用模拟器凑数（本机没确认过 SDK 里有可用镜像）。

2026-10-02 实跑：`D:/Lib/Android/Sdk/platform-tools/adb.exe devices` 输出 `List of devices attached` 后为空，本机也没有 Android Studio 可拉起模拟器。**Step 2/3/4 必须由你这边执行。**

- [ ] **Step 3: 逐条走核心闭环（spec 第 6 节）**

按顺序点，每条都要看到预期结果；括号里是失败时先看哪个模块。

**A 段：完全不依赖 API key，现在就能验收（21 条）**

- [x] 首次启动落在照片墙，网格为空并给出"还没有物品。点右下角的按钮选第一张照片。"；右下角那颗 ＋ 开的是**系统图片选择器**（Photo Picker），本版本没有取景页，spec 第 6.1 节标题里的"拍照"要到计划 2 表里那条 CameraX 才补上（外壳默认 Tab + 有意偏离第 1 条）
- [x] 位置 Tab → 新建「家」→ 新建子位置「主卧」→ 再下钻建「衣柜」，面包屑标题逐级变深（Task 10/14）。进「改名 / 设置类型」把名称和类型**一起**改掉再保存：两个字段必须都落住，条幅说「已更新「新名字」」——以前它们是两笔独立的读改写，改名会被后一笔悄悄盖回去（`8c62ab0`）。位置树第一次进来不该闪一下"还没有位置"
- [x] 移动到…里选中它**当前就在**的那个层级 → 条幅说「已经在这一层级，没有改动」，不是「已移动到新层级」
- [x] 右下角按钮选一张实物照片 → 进入录入页，缩略图出现；AI 状态行给中文降级提示（**未启用 AI（还没配 API Key），字段留空，直接手填就行**），「重新分析」按钮在（Task 7/8/13 的降级路径）。只有照片压根没落库（选到一张解不开的图）时这颗按钮不出现——那时候它按下去不会有半点反应（`8c62ab0`）
- [x] 录入页点「换一张」再选一张：还是**同一件物品**（itemId 不变），已填的字段一个都不丢，收件箱里**不多出一具空壳**；旧照片的两个文件从 `filesDir/photos` 下消失，换出来的那对是新的（`5b30ee1`）。换完 AI 状态行会重新转一次（没配 key 时照样直接给降级提示，表单不空）
- [x] 手工填名称/分类/季节/颜色/品牌/材质/别名/描述/标签/数量/备注，全部能存下来（Task 13）
- [x] 位置那一栏：树是空的时只有"暂不归类，先进收件箱"；用弹窗里的新建入口现场建一个位置并自动选中（Task 11/13）
- [x] 保存 → 回到照片墙，网格里出现该物品缩略图（Task 9/15）
- [x] 位置 Tab 下钻到「衣柜」，"这里的物品"列表里能看到它。**搬动一件物品的入口不在位置树里**：树上那颗 ⋮（改名 / 移动到… / 删除，点一下弹出、不是长按）管的是**位置节点本身**要把这件物品搬走，走详情页「搬到」或照片墙卡片的 ⋮ →「搬到…」（Task 10/14/15）
- [x] 照片墙搜索「外套」能按名称/别名/品牌/标签/描述/备注命中，命中名称的排在前面（Task 3）。**颜色与材质现在也在 `search_text` 里**（批次 N 拍板③，`SearchTextBuilder` 新加了 `FIELD_VERSION = 2` 来标记参与拼列的字段集合，启动时比一次号就给老数据补算），搜"gray""wool"这类词能命中；真机上 `jacket` 那一件的实际列值是 `jacket uniqlo gray wool overcoat winter keepsmewarm gift 2024`。搜索框的提示语以前只列了六个字段、漏掉颜色与材质，属于文案不实，已改成「搜名称、颜色、材质、品牌、备注等文字」——它本来就不可能把八个字段都写在一行里。**批次 S 起"打字能不能命中颜色"由 `GalleryScreenTest.typingInTheSearchBoxNarrowsTheWallByColor` 钉住，不必再靠手机**
- [x] 分类 chip + 季节 chip + 标签 chip 叠加筛选，结果集是交集（Task 9/15）。标签 chip 只认**真打了这个标签**的物品，描述里恰好出现过那个词的不算（第二轮评审 `ab3f430`）。筛到一件不剩时空态那句是**「按当前的搜索和筛选没找到物品，去掉几个条件再看看。」**，不会说"还没有物品"——库里可能满满当当（`8c62ab0`：以前只要没输入关键词，这条空屏就在撒谎）
- [x] 录入时不选位置直接保存 → 待归类 Tab 出现该物品，照片墙"只看待归类"也能筛到；「稍后处理」同理（Task 9 的收件箱定义）
- [x] 详情页「编辑」里什么都不改、直接按「稍后处理」→ 物品**还在原来的位置**（下钻那个位置能看见它），同时又出现在待归类里（第二轮评审 `ab3f430`：以前这一步会把位置抹掉）
- [x] 待归类里点「搬到」选一个位置后，该物品从待归类消失、出现在对应位置（Task 15）。**这条与 spec 第 6.4 节的矛盾已按拍板①解决**：收件箱的谓词是"没位置 **或** 还是 DRAFT"，而搬动本身不改状态，所以「搬到」现在顺手确认——**但只确认有名字的**。有名字的那件（真机上叫 `umbrella` 的走完整流程、有名字后搬走）会离开收件箱；**没名字的壳子搬到 `shelf` 之后仍然留在收件箱**，这是刻意的：无名物品一确认就既没名字、`search_text` 也空，变成搜不出来的哑物品，不如继续待在收件箱里催它有个名字（`16d9918`）。**这两半句现在各有一条界面测试钉着（`movingADraftByCardConfirmsItSoTheInboxEmpties` / `movingAnUnnamedDraftStillLeavesItInTheInbox`，批次 S），走的就是卡片 ⋮ →「搬到…」→ 选位置那条真实路径**
- [x] 详情页「编辑」回到录入页，字段带得全，改完保存回到详情页数据已更新（Task 13/15/16）
- [x] 详情页设一个 3 天后的到期日期 → 照片墙该卡片显示"还剩 3 天"，详情页显示「日期（还剩 3 天） · 保质期」；设成过去的日期两处都显示"已过期 N 天"，正好今天则是"今天到期"（Task 13 `UiFormat.expireHint`/Task 15）
- [x] 删除物品后，`filesDir/photos` 下对应文件消失（Task 5/9 的 `delete`）
- [x] 底栏条幅**真的会弹出来**：删一个还带着子位置的位置 → 底部闪过「该位置下还有 1 个子位置和 0 件物品，请先搬空再删除」；成功那一侧同样要有「已删除该位置」。四个页面（照片墙/位置树/详情/设置）都走同一个 `SnackbarMessage`（`8c62ab0`）——之前那种"先消费 VM 状态再 `showSnackbar`"的写法会把刚弹出的条幅当场取消，等于错误一声不响，只剩界面上一堆没解释的变化
- [x] 旋转屏幕：录入页**表单内容**不丢（它活在 ViewModel 里，`pendingCapture` 也只在内存）、标签输入框里敲了还没点 ＋ 的那几个字也不丢（`rememberSaveable`）；**开着的弹窗会被关掉**——位置选择弹窗、到期日期弹窗以及弹窗里选中的「保质期/保修」那一项用的是普通 `remember`，旋转后回到初始值。这不算 bug，别当新缺陷报（Task 11/13）；杀进程再进：回到照片墙而不是崩溃（Task 16 的 `else -> reviewOpen = false`）
- [x] 物理返回键：详情页/录入页退一层，位置树先退层级、根层级才退出应用（Task 16 Step 1）
- [x] 深色模式下所有文字仍可读（Task 13 的固定配色，不跟随系统动态取色）

A 段里最值得盯的两件事，都是这轮评审刚修的：**录入页的「换一张」按钮现在常驻**（以前它的条件是"物品还没落库且不正在分析"，而物品行是选图那一刻就写的，所以几乎点不到——修之前选错照片只能"关闭→去收件箱删掉那具没名字的壳→重录一遍"）；**没配 key 时表单必须照常能保存**（降级路径以前没有测试钉住）。

下面两条**已知没修**，验收时别当新 bug：

- **竖着拍的照片可能横躺**在缩略图与原图上。`PhotoStore` 是解码后再压一次，而 `Bitmap.compress` 不写 EXIF，方向信息就在这一步丢了；真机上多数相册会按 EXIF 转正，而我们这一份已经没有 EXIF 可看了。修法是解码后按 `ExifInterface` 的 orientation 主动转一次画布，属于功能不是补丁，等你定优先级。
- **通知点下去只打开应用、落在退出前上次选中的那一格**（真机实测：从设置页杀进程再点通知，回来还是设置页），既不是"默认 Tab（照片墙）"，也不会带着"看今天到期"的筛选进去。spec 第 6.5 节想要的是筛选后的照片墙，那需要 `launchMode`/`onNewIntent` 一类的接线，记在计划 2 的偏离里等你对（计划 2 Task 5 第 4 点）。

**B 段：需要真实 key，你回来再说（3 条）**

- [ ] 选照片后 AI 状态行显示"正在分析"（Task 12/13）
- [ ] 填好 key 后再选一张照片：名称/分类/颜色/标签被预填，AI 猜的容器名若与已有位置对得上，位置那一栏显示建议项（Task 6/7/13 的 `SuggestLocationUseCase`）
- [ ] 分析未完成时先手填名称，AI 结果回来后**不覆盖**已填字段（`touched`，Task 13），且已点保存的物品不会被迟到的结果改名（本轮补的 `applyAnalysis` 状态门）

B 段现在一条都测不了，原因不是缺陷而是没有入口：设置页（计划 2 Task 7）已经存在，但按你"先不做 AI 分支"的决定，它里面只有提醒提前天数和备份进出，没有任何写 `SettingsRepository.setApiKey/setBaseUrl` 的界面，`currentConfig()` 恒为 null，所有照片都会走 `NO_CONFIG`。要跑 B 段，得先往设置页加 AI 配置那三行，或者临时用 adb/DataStore 手改配置——这一步等你决定。

**这一段上面那句已经过期**：批次 L（`eef7a62`）把三行输入框加进了设置页，key/baseUrl/model 都能自己填，所以 B 段现在缺的只有你那把 key。**批次 T 又把失败分类补齐了**（见文末批次 T），key 填错、模型名填错、账户没钱这三种情况在确认页上各说一句人话，不必再靠"稍后再试"猜是哪一种。

**C 段：备份与到期提醒（计划 2 新增的面，10 条，同样不依赖 key）**

底栏第 4 个 Tab「设置」是这一段的入口（计划 2 Task 3 交付时它叫「数据」页，Task 7 把它并成设置页）。页面上明写着"备份文件里不含 API key，也不含任何设置"，头两条就是在验这句话的两面：能带走的东西，和带不走的东西。

- [x] 先建 2 件物品、各带 1 张照片、分到两个位置 → 设置页「导出到文件」选一个你找得回来的位置 → 提示「导出完成 · 位置 N 个 · 物品 2 件 · 照片 2 张 · x MB」。用文件管理器把 zip 解开看一眼：应当只有 `manifest.json`、`data.json`、`photos/` 下的原图与缩略图，`data.json` 里搜不到任何 key 字样（计划 2 Task 1/3）
- [x] 卸载重装（这会清空全部本机数据）→ 设置页「合并导入」选中刚才那个包 → 提示「合并导入完成 · …物品 2 件…」（导入这一侧不报包的大小，只报条数，见计划 2 评审修正第 2 条），照片墙缩略图回来了、位置树两层都在。**这条失败时先看 `BackupRepository.parentsFirst`**，包里位置若是"子在前、父在后"就靠它排序（计划 2 Task 2 偏离第 3 条）
- [x] 手工把 `data.json` 里的一个 `}` 删掉 → 「合并导入」→ 应当是**「备份的 data.json 读不出来，文件可能被截断了」**；再把 `locationId` 改成包里不存在的一个 → 应当是**「「某件物品」所在的位置不在包里，备份不完整」**。两次都要求**原来的 2 件物品一件不少**（计划 2 Task 2 的整体校验 + 单事务落库）。**「这不是 Wheremy 的备份包：少了 …」这一句只在包里根本没有 `manifest.json`/`data.json` 这两条条目时出现**——最典型的是用压缩软件把整个文件夹压进去、条目名多了一层前缀，那时报的也是这句。三种坏法对应三句话，别把它们混成一条验收（`BackupRepository.kt:130-150`）
- [x] 先新建 1 件只有本机有的物品，再「覆盖导入」同一个旧包 → 先弹确认框「覆盖导入会清空本机数据」→ 确认后那件新物品消失、包里的 2 件回来，照片墙不再出现孤儿缩略图（计划 2 Task 2 的 `removeLocalPhotosNotIn`）
- [x] 设置页「到期提醒」把提前天数从 3 改成 30（点「＋」或者直接点预设「30 天」）→ 那行字立刻变成「提前 30 天提醒」，退出去再回来还是它（真的写进了 DataStore，不是记在内存里）→ 再触发一次 Worker，一个月后才到期的物品也该被算进窗口。**连点 5 下「＋」必须落在 8**，不能被吞成"只涨了一两下"（`1a023c6`：VM 不再写回读 DataStore，改成先夹到 0..90 再落盘，所以快按不会互相覆盖；写盘真失败时会退回原值并说「这个提前天数没存进去…」）。**点到 0 那行字变成「当天到期提醒」**，不写"提前 0 天"，同时「−」变灰点不动（`8c62ab0`）。**改成 0 不等于"只剩当天到期的"**：窗口上界确实收到今天末，但"从来没提醒过"的过期物品仍然会进来说一次。**上限已经放开到 365**（拍板②，`16d9918`）：预设行现在有「90 天 / 半年 / 一年」，点「一年」那行字变成「提前 365 天提醒」，此时再连点「＋」停在三六五不动。真机验证过的四条：从 3 连点 5 下「＋」正好落在 **8**（没被吞成"只涨了一两下"）；点「当天」那行字是**「当天到期提醒」**而不是"提前 0 天"，且此时「−」点不动；点「一年」那行字变成「提前 365 天提醒」，再连点「＋」停在 365 不越界；把提前天数改成 30 后 `am force-stop` 再冷启动，那行字还是 30（真写进了 DataStore）。
- [x] 给某件物品设一个 3 天内的到期日期 → 在设置页点「开启通知」并授权 → 等 `ReminderWorker` 下一次跑（真机最短约 1 小时）→ 收到一条聚合通知，文案形如「1 件物品临近到期，最近的还剩 2 天」，点它打开应用落在照片墙；系统设置里能看到名为「到期提醒」的渠道（计划 2 Task 4/5）。**这条原来写的"用 adb 立刻触发"在周期性 WorkManager 任务上不成立**，见下面 C 段结论第 4 点——但批次 V 之后不用等了：设置页那颗「调试：立刻检查一次到期提醒」排的是一次性任务，抬手到 `Worker result SUCCESS` 实测 30 毫秒（07:11:29.012 抬手 / .019 `Scheduling work ID c4afe522…Job ID 4` / .030 `Starting work` / .042 SUCCESS）。真机实测（Xiaomi 13 / HyperOS，提前天数 3，库里有今天 10-03、明天 10-04、后天 10-05、年底 12-31 四件）：`dumpsys notification --noredact` 里是 `NotificationRecord(pkg=com.wheremy.app id=1001 channel=wheremy_expire)`，标题「Wheremy 到期提醒」，正文**「3 件物品临近到期，其中 1 件今天到期」**；渠道确实在系统里——`mId='wheremy_expire', mName=到期提醒, mImportance=3(DEFAULT)`，描述也在（`hasDescription`）。两处要如实标出来：**授权那一步走的是 `pm grant`，「开启通知」那颗按钮本身没点**（三种状态各说哪句、后者不给死按钮，是 `SettingsScreenTest` 钉的；`pm revoke`/`pm grant` 来回切时条幅与按钮跟着变，这轮之前实测过）；**"点它打开应用落在照片墙"这半句没通过**——`HOME` + `am kill`（`force-stop` 不行，它会把通知一起清掉）让进程真死，状态栏里那行「Wheremy 到期提醒」点下去 `mCurrentFocus` 确实回到 `MainActivity`，落的是**退出前上次选中的那一格（这次是设置页）**，见上面"已知没修"第 2 条
- [x] 关掉通知权限再触发一次 Worker → **不崩溃**，通知不出现，设置页此时显示「通知未开启，到期只在应用内标记」和「开启通知」按钮；**而且这件物品的"已提醒"标记不能被悄悄写上**——重新授权之后它还要能再被说一句（第三轮评审 `1a023c6`：以前拒权限时也回写 `expireNotifiedAt`，等于用户一没注意拒了，那条到期就永久沉默了）。这条同时验一遍旧毛病：在系统设置里手动开关通知后回到设置页，那行提示要跟着变（计划 2 评审修正第 9 条）。真机实测：`pm revoke` 到 `granted=false` 后按 debug，Worker 照样 `SUCCESS`（计划算出来了），但 `dumpsys notification` 一条 Wheremy 记录都没有，而**唯一的读库手段——应用自己的「导出到文件」——里四件物品的 `expireNotifiedAt` 全是缺键（`explicitNulls=false` 序列化的 NULL）**；设置页那时那两样都在：「通知未开启，到期只在应用内标记」＋「开启通知」。`pm grant` 之后那行字**没有立刻消失**（不是缺陷：它是 `Lifecycle.Event.ON_RESUME` 刷的，`SettingsScreen.kt:82`），HOME 再拉回前台就没了，然后按同一次按下才发出通知并回写标记（三件各 = `1790982800191`）
- [ ] **权限给了、但系统里把 Wheremy 的通知整体关掉**（设置 → 应用 → Wheremy → 通知，关掉「显示通知」）再触发 Worker → 同样不崩溃、不出现通知、**也不许回写"已提醒"**。这时设置页那句话换成「通知在系统里被关掉了：到 设置 → 应用 → Wheremy → 通知 里打开…」，而且**「开启通知」按钮不出现**——权限已经给了，再申请一次是按了没反应的死按钮（`8c62ab0`：`canNotify` 以前只看运行时授权，`notify()` 静默失败却照样 mark）。**这条仍然未测，别当成通过**：MIUI 上那个应用级总开关直接绑在运行时权限上，关掉它 `POST_NOTIFICATIONS` 就变 `granted=false`，界面落到第 7 条那一句，两种状态合成了一种（见下面真机走查结论第 2 点）。批次 V 有了 debug 入口也没解开这一层——要构造的是系统那边的状态，不是触发手段
- [x] 一件**早就过期**的物品：第一次触发 Worker 会说一次，再触发就永久沉默，直到你**改它的到期日**或删掉它（计划 2 Task 4 评审修正第 3 条）。反面用例是"过期物每天重新提醒"，那会把一次聚合通知变成闹钟，还会把 50 条的名额占满、让新到期的东西一条也进不来。真机实测：合并导入一件 2026-09-20 到期（已过期 13 天）的 `wm-expired` → 导出里它 `notified=null` → 按一次 debug，通知正文换成**「1 件物品到期或已过期，最早的已过期 13 天」**（跟"临近到期"那句分开说了）→ **再按一次**，导出里那枚标记仍是第一次的 `1790982984409`，一个字没动 → 走界面改它的到期日（见下一条①）之后才重新被说出来
- [x] 提醒的总则是**每件物品每个截止日期最多说两次**：进窗口那一次 + 到期当天那一次（`expiringBy` 的三个边界，第三轮评审重写过）。配套的三条都要看得见：① **改了日期就重新排队**——把保修日从 3 月改到 9 月，新日子还得被提一句，所以只动名字不该再说、动了日期就该再说（`ItemRepository.reArm`）；② **收件箱里还没归位的（DRAFT）同样提醒**，以前 SQL 带着 `status='CONFIRMED'`，"稍后处理"的东西等于被排除在保质期提醒之外，而那恰恰是最容易放坏的一批；③ 到期当天不重复播报两次。三条真机都见了：① 在详情页点「编辑」→「到期提醒」那行的「设置」→ 日历里点「2026年10月4日星期日」→「确定」，那行立刻变成「到期 · 2026-10-04（还剩 1 天）」，保存后导出里这件物品是 `expiresAt=1791043200000 notified=null`（**标记被清掉，重新排队**），再按一次 debug 就又说了一遍「1 件物品临近到期，最近的还剩 1 天」；② 上面那条「3 件物品临近到期」里的 3 件就是 `wm-b-today(CONFIRMED)` + `wm-e-draft(DRAFT，locationId=null，站在收件箱那一格)` + `wm-a-near(CONFIRMED)`，DRAFT 和它一起被数进去、一起被回写同一枚标记；③ `wm-b-today` 当天到期、当天进窗口，通知只说了一次「其中 1 件今天到期」，第二次按下时它的标记（`1790982800191`）已经不小于 `windowStart`（`1790956800000`），所以被 `expiringBy` 排除在外——导出 (4) 里三枚标记与 (3) 一字不差。**没有构造出来的是"隔一天到期的物品在到期当天再说第二次"**：那要跨一个自然日，一次 debug 触发办不到，只有 `expiringBy` 那三个边界的仓库测在钉它

C 段只有头四条（备份）跟 WorkManager 无关。第 5 条的后半段和第 6、7、8、9、10 条都要 Worker 真的排过班：`MainActivity.onCreate` 每次启动都会 `enqueueUniquePeriodicWork(KEEP)`，所以装好并打开过一次应用之后才算排上。这六条没有 adb 就只能等，测不到就在结论里写"未测"，别当成通过。**批次 V 把"只能等"改掉了**：设置页 debug 包那颗「立刻检查一次到期提醒」排的是一次性任务，同一天就能把这六条里的五条（第 6、7、9、10 条与第 5 条后半段）跑完；第 8 条卡的不是触发手段而是 MIUI 构造不出那个状态，仍然未测。下面那段是批次 V 之前写的，当时"没测"是真的。

**2026-10-03 真机走查结论（Xiaomi 13 / HyperOS，非 AI 部分全部跑完）**

A 段 21 条与 C 段头五条全部通过，条幅文案与截图/`uiautomator dump` 逐条对过。备份那四条的实测证据：导出条幅原文是「导出完成 · 位置 4 个 · 物品 2 件 · 照片 2 张 · 91 KB」，包里正好 `manifest.json` + `data.json` + `photos/{originals,thumbs}` 六个条目，`data.json` 里 `api|key|secret|token|base_url|deepseek|sk-` 一个都搜不到；合并导入「合并导入完成 · 位置 4 个 · 物品 2 件 · 照片 2 张」（按设计不报大小），三层树和两个照片文件都回到 `filesDir/photos`；三种坏包给三句不同的话——「合并导入失败：备份的 data.json 读不出来，文件可能被截断了」/「合并导入失败：「jacket」所在的位置不在包里，备份不完整」/「合并导入失败：这不是 Wheremy 的备份包：少了 manifest.json」，三次失败之后本机的 2 件物品一件不少、`photos` 下也没多出野文件；覆盖导入先弹「覆盖导入会清空本机数据」，确认后那件只有本机有的未命名壳子连同它的两个照片文件一起消失。顺带纠正一处以前的误判：**照片文件名不是按 itemId 命名的**，`item_photo.id` 自己是一个 uuid，所以「换一张」之后看到的文件名变化本身不说明 itemId 变没变——itemId 那条是靠"库里没有多出一件物品"和仓库测钉住的。

后面五条 Worker 相关的**当时没测，别当成通过**（批次 V 之后四条已在真机跑完，只有第 8 条仍然未测），原因有三条，都是新知识：

1. **`cmd jobscheduler run -f` 点不动周期性 WorkManager 任务。** 先记两点语法：WorkManager 的 job 有命名空间，不带 `-n` 会报 "Could not find job"，正确写法是 `cmd jobscheduler run -f -n androidx.work.systemjobscheduler com.wheremy.app <id>`（id 从 `dumpsys jobscheduler | grep "JOB .*wheremy"` 的 `u0a102/<id>` 里取，跑一次会换号）。但即使命令回 `Running job [FORCED]`，日志里跟着来的是 `WM-WorkerWrapper: Delaying execution for com.wheremy.app.data.work.ReminderWorker because it is being executed before schedule.`——WorkManager 自己按 `intervalDuration` 把这次执行推后了，`dumpsys notification` 里一条 Wheremy 的记录都没有，`item.expireNotifiedAt` 也确实是 NULL。所以"立刻触发"这条验收手段只对一次性任务成立；要当天测提醒，只能等 24 小时窗口，或者加一个只进 debug 变体的一次性触发入口——**这条批次 V 按拍板②加了**（`ReminderScheduler.runNow()` + 设置页那颗调试按钮）。**括号里那句"用 sqlite 读到的"要更正：那条读路本身不可信。** `run-as` 拷出来的 `wheremy.db` 只有 4096 字节、`sqlite_master` 里只有 `android_metadata`（真正的行都在 185 KB 的 `-wal` 里），而那份 WAL 拷到 Windows 侧回放不了——45 帧里有 40 帧 salt 跟头对不上，本地 `sqlite3.connect()` 还会顺手把拷来的 `-wal` 删掉。所以**"标记是 NULL"那个结论当时并没有被证据支撑**（它后来被批次 V 的实测证实是对的，但靠的是另一条路）：真机上读 `expireNotifiedAt` 唯一可靠的手段是**应用自己的「导出到文件」→ 解开 `data.json`**，它走的是应用的读路径，WAL 已经被应用合过了。
2. **MIUI 上"权限给了、系统里整体关掉通知"这个状态构造不出来。** 应用信息 → 通知管理 → 「允许通知」那个总开关直接绑在运行时权限上：关掉它 `POST_NOTIFICATIONS` 立刻变 `granted=false`，设置页于是显示的是第 7 条那句「通知未开启，到期只在应用内标记」（这句实测是对的，`pm revoke` / `pm grant` 来回切，条幅和「开启通知」按钮都跟着变）。第 8 条那种两句话的分工在 MIUI 上合成了第 7 条那一种。
3. **但能构造出下一层：把「到期提醒」这一个渠道单独静音。** 权限仍然 `granted=true`、渠道 `mImportance` 掉到 0，此时回设置页，**应用一句话都不说，也不出现「开启通知」按钮**——`canNotify()` 用的是 `areNotificationsEnabled()`，它只看应用级开关，看不见渠道被静音。也就是说 Worker 真跑起来时会往一个死渠道里发、然后照样回写 `expireNotifiedAt`，那件物品的到期就永久沉默了。这跟 `8c62ab0` 修的是同一个毛病的下一层（那一次是"权限给了但应用级关掉"）。**这一条已经有了拍板（2026-10-03 拍板③）：被静音的「到期提醒」也算通知到了**——人静音的是声音，不是那条会留在通知栏里的提醒，所以 `canNotify()` 的行为**按现状保留、一行代码没改**，改的是给它加钉子（`ReminderWorkerTest.mutedChannelStillCountsAsDelivered`，详见文末批次 V）。上面那句"修法是把 `importance == IMPORTANCE_NONE` 也算进 `canNotify` 并给第三句话"是被**否决**的方案，下一个人别再顺着手边把它"修平"。

遗留的手机状态要交代清楚：通知权限是开着的（我给的授权）；渠道「到期提醒」被我静音过一次，恢复后 `mImportance` 从 3(DEFAULT) 变成 2(LOW)、声音仍是系统通知音，AOSP 侧 `createNotificationChannel` 不会改已存在渠道的重要性，所以只有卸载重装才会把它按代码重建为 DEFAULT；深色模式还是我开的；`/sdcard/Download` 里三个坏包已删掉，留了 `wheremy-backup-2026-10-03.zip` 和 `(1)` 两份正常备份；机内数据现在是 `jacket`（在 `shelf`，到期 2026-10-06，标签 winter，数量 3）与 `umbrella`（在 `home / bedroom / wardrobe`），收件箱空。另外 MIUI 用 adb 安装会被系统弹窗挡下来（`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`），解法是安装命令挂后台、等三秒点掉手机上的「继续安装」。

**上面这段"遗留状态"到批次 V 之后全部作废**（保留原文是因为它是那一带路径与文案的证据）：应用已卸载重装，渠道确实被按代码重建为 `mImportance=3(DEFAULT)`；机内数据换成了 5 件 `wm-*` 测试物品和位置 `wm-room`，`jacket`/`umbrella` 被覆盖导入冲掉了；`/sdcard/Download` 里现在除了那两份正常备份还有两个 seed 包和 `(2)…(7)` 一串导出。当前的残留与清理清单在文末批次 V 那条里。

**2026-10-03 批次 M：把"只能真机看"的断言搬进 CI**

做法是在现有 `app/src/test` 里用 Robolectric 跑 Compose 界面测试——不新增模块、不要模拟器、不生成 wrapper，`gradle :app:testDebugUnitTest` 一条命令就把界面文案一起验了。新增 `ui/gallery/GalleryScreenTest`（6 例）、`ui/settings/SettingsScreenTest`（10 例）与共享的 `TestSettings`，测试从 26 类 230 例变成 **28 类 246 例**。

现在被钉住的（原来只有 A/C 段那几条手工验收覆盖）：搜索框的标语必须提到颜色与材质、空库/筛空/收件箱空三种说法各说各的、卡片上要有位置全路径与剩余天数（含"今天到期""已过期 2 天"，且不许出现「还剩 -2 天」）、提前天数连点五下不被吞、到 0 与 365 边界那颗方向键真的禁用、通知缺权限/应用级关掉/正常三种状态分别说哪句（后者不给死按钮）、「已就绪」那行跟着存下来的配置、隐私声明三段还在页面上、覆盖导入先问一次。`SnackbarMessage` 已经被 `8c62ab0` 那轮的 `SnackbarMessageTest` 钉住了，这里没重复。

**顺带抓到一处真说错话的地方**（已修，见 `fix:` 提交）：界面上「还缺 API key…」这一行原来是从三个文本框的当前值现算的，所以手打进半截 key、还没按保存，它就改口「已就绪」。`aiUsable` 现在跟的是**已经落盘的那份配置**，按保存/按开关时同步发布，不靠一次异步回读——回读拿到的是协程排到哪一步的快照，紧接着再按一次开关就能把旧值盖回去。

**下一个人会踩的三个坑**：① `LazyVerticalGrid` 只组合露出来的格子，Robolectric 默认 320x470dp 一屏只放得下一张卡片，第二件物品的文字压根没进语义树，断言会假失败——所以要有 `@GraphicsMode(NATIVE)` 和 `@Config(qualifiers = "w411dp-h891dp")`；② Compose 1.11.4 没有 `hasToggleAction`（`javap` 过 `ui-test.aar` 里的 `ActionsKt` 确认），开关只能 `onNode(isOn()).performClick()`，折叠线以下的按钮 `performClick()` 前要 `performScrollTo()`，一个测试只能 `setContent` 一次；③ DataStore 是进程级单例，界面用例的 VM 又"按了就写、不等回话"，那笔写会落在下一个 `setUp` 之后——表现是"库里没 key"这个前提不成立、`awaitStored` 三秒超时，而且**只在整包跑的时候复现**，单独跑那个类是绿的。修法两条一起：`tearDown` 先 `viewModelScope.cancel()` 掐掉还没提交的写，再用 `TestSettings.resetQuietly` 清完等 150ms、读回来仍是默认值才算清干净；整包 `--rerun-tasks` 连跑五次全绿。

仍然只能真机看的：C 段第 6–10 条（要 WorkManager 真的排过班）、照片的实际画质与 EXIF 方向、MIUI 那两层通知行为、点通知落在哪一屏。

**2026-10-03 批次 O：位置树与详情页也搬进来**

接着批次 M 那套 harness 加 `ui/tree/TreeScreenTest`（7 例）与 `ui/detail/DetailScreenTest`（7 例），测试从 28 类 246 例变成 **30 类 260 例**，整包 `--rerun-tasks` 全绿。树页钉的是：根层级与子层级的两句空态必须分开、标题「家 / 主卧」与「上一层」一次退一层、行上只有直接件数和含子位置件数不一致时才补那句括号、节点菜单三项全是文字按钮、删非空位置必须说清"还有几个子位置和几件物品"。详情页钉的是：「没有这条记录」和「读不出来」不许说成同一句、没填的字段整行不出现、到期那行把日期／剩余天数／类型拼在一行、还没归位的物品在位置卡片上就是「待归类（还没放好）」、删除先问一次且真删完要把人推回上一页——那条断的是 `backCount` 恰好 1，退两次同样是 bug。

顺带核对了「位置树首帧闪空」：`TreeScreen.kt:93` 已经在读 `state.loading`，第四轮自审记的那条说的是修之前的样子，不用再来一遍。

**这一轮新踩的两个坑**：① 位置编辑对话框在 Robolectric 下永远等不到 idle，`waitForIdle()` 空转到 60 秒抛 `AppNotIdleException`（真机没这问题，A 段那些位置就是从手机上的这个对话框建出来的）。当时我把锅甩给了那排 `FlowRow` 类型胶囊，**批次 P 推翻了这条归因**：真正的触发条件是对话框窗口里出现了文本框。所以 `creatingAddsTheRowAndAnnouncesItself` 不点「新建」那颗按钮，改成直接 `vm.create(" 家 ", null)`，验的是"建完这行真出来了、且说得出那句回执"，名称 trim 掉空格也在这条里；空名字建不出东西那一侧由 `LocationRepository.requireName` 守着，仓库测试早就钉住了。② 确认对话框开着时，顶栏的「删除」和对话框的「删除」是**两个**节点，`onNodeWithText("删除")` 会以"期望 1 个、实际 2 个"失败；确认那颗要 `onNode(hasAnyAncestor(isDialog()).and(hasText("删除")))`——`isDialog` 和 `hasAnyAncestor` 都在 `ui-test.aar` 的 `FiltersKt` 里（`javap` 过），这个版本没有 `hasDialogRole`。

三条反向验证（改坏产品代码，看测试是不是真会红）：把「没有这条记录。」换成「读不出来」那句 → 只红 `unknownRecordSaysItIsGoneNotUnreadable`；给没填的材质填上「未填写」→ 只红 `fieldsLeftBlankDoNotTakeUpARow`；删掉删除成功后的 `onBack()` → 只红 `deleteAsksOnceThenLeavesThePage`。每次只红对应那一条，验完还原。

**2026-10-03 批次 P：确认页与位置挑选对话框**

再加 `ui/review/ReviewScreenTest`（9 例）与 `ui/common/LocationPickerDialogTest`（3 例），测试变成 **32 类 272 例**，整包 `--rerun-tasks` 全绿。确认页这边钉的是：照片还没落库时那颗按钮只能写「选择照片」、「保存」与「稍后处理」按不住、「重新分析」根本不该出现；落了库同一颗改口「换一张」且一点就把选图回调打出去；AI 的预填必须看得见（名称框里是那一串、分类与季节胶囊是选中态、还带一句「AI 猜的位置：衣柜」）；降级那句话在（`TIMEOUT` 与 `NO_CONFIG` 两条），而且**界面上不许出现"秒"这个字**——超时时长是 `AppContainer.CALL_TIMEOUT_SECONDS` 说了算的，抄进文案就是等着说错；数量减号在 1 件时是死的、加到 2 才活；位置那行没选就是「未归类（进收件箱）」、选了写的是那一层的名字；到期那行从「未设置」变成「保质期 · 2026-10-06（还剩 3 天）」；标签框敲回车必须算"加上"（断的是"恰好一个节点"，所以加完没清空也算失败）。挑选对话框钉的是：同一颗按钮给物品说「暂不归类，先进收件箱」、给位置节点说「回到根层级」，以及每一行交出的是自己那层的 id。

**上一轮那条归因是错的，现在有了证据**：确认页的到期对话框里那排类型胶囊同样是 `FlowRow` 排的，它照样 idle；换成只有文本框的对话框就永不 idle。所以规矩是 **Robolectric 下对话框窗口里一出现文本框，`waitForIdle()` 和 `assertExists` 都过不去**（60 秒 `AppNotIdleException`；`mainClock.autoAdvance = false` 手动跳帧没用，sdk 28 和 34 一样）。位置编辑对话框、挑选对话框底部那行「在「家」下新建」因此进不了 CI：前者改成直接调 `vm.create(...)`，后者那句话由 `createLocationInsidePickerSelectsIt` 与真机 A 段盖。这一屏还有两处只有测试里写得出来的规矩：`＋` 这颗文字按钮在同屏出现两次（数量的和加标签的），要 `hasAnySibling(hasText("数量"))` 才取得到对的那颗；标签那颗是 `AssistChip`，它没有选中态，`assertIsSelected` 只能用在 `FilterChip` 上。

反向验证这回一次做满：七处改坏产品代码（按钮恒写「选择照片」、「重新分析」无条件出现、减号恒可按、确定恒可按、根行交出的是第一项而不是 null、每行都交 null、把"15 秒"抄回降级文案），各自只红对应的那一条，验完还原。

仍然只能真机看的多了一条：两个对话框里的文本输入本身（建位置、改名、设类型的完整来回）。
任何一条不过：用 superpowers:systematic-debugging 定位根因，就地修复，重跑 Step 1，再从这里继续；修好了单独提交一次 `fix: …`。

- [x] **Step 4: 提交验收状态**

清单勾选状态留在本文件里（勾选即提交），方便下次接着看：

```bash
git add docs/superpowers/plans/2026-10-02-wheremy-core-loop.md
git commit -m "docs: 核心闭环手工验收通过"
```

---

## Plan 2 范围（本计划刻意不做）

| # | 事项 | spec 出处 | 为什么留到 Plan 2 |
|---|---|---|---|
| 1 | 到期提醒：`WorkManager` 每日巡检 + 通知权限 + 提醒时间设置 | 第 6.5 节、M5 | 要先有真实数据才测得出提醒；`expireNotifiedAt` 字段与索引已在本计划建好 |
| 2 | 备份导出/导入（JSON + 照片 zip） | 第 6.6 节、M6 | 数据模型稳定之后才值得固定备份格式；导出必须排除 DataStore 里的 API key |
| 3 | 设置页：baseUrl / 模型名 / key 输入与"key 只在本地"说明 | 第 7 节 | 本计划先靠 DataStore 默认值（百炼 + `qwen-vl-max`）跑通，设置页只是给它一个入口 |
| 4 | CameraX 实时取景（含快门、预览、闪光灯） | 第 3 节、第 6.1 节 | 见"实施偏差记录"第 1 条 |
| 5 | ~~位置节点排序（长按拖动/上下移动）与"拍照记录该位置"~~ **已完成**：排序见批次 AF，位置照片见批次 AG。两条都不欠迁移——`sortOrder` 与 `PhotoOwner.LOCATION` 本来就在库里 | 第 6.2 节 | 留在表里是为了记住一件事：我两次都把这两条记成"要先解冻 schema"，而实际上缺的只是入口 |
| 6 | 真实 `Room Migration` 替换 `fallbackToDestructiveMigration` | 第 8 节 | 见"实施偏差记录"第 2 条；首次对外分享 APK 前必须完成 |
| 7 | 图片方向修正/EXIF、超大图内存优化 | 第 8 节性能注意 | 先用真实照片跑一遍，确认遇到问题再优化 |

## 实施偏差记录（需用户确认）

1. **取图用系统 Photo Picker，不实现 CameraX**（spec 第 3 节把 CameraX 列为最终形态）。Picker 免相机权限、代码量小；`PhotoStore`/`CaptureFlow` 的入参是 `ByteArray`，将来换成 CameraX 只替换取图那一步。
2. **Plan 1 期间 Room 用 `fallbackToDestructiveMigration`**（spec 第 8 节要求真实 Migration）。0.1 无真实数据；列为 Plan 2 第 6 条。
3. **不引入 `androidx.navigation.compose`**（spec 第 4 节只说"单 Activity + Compose"，未指定导航库）。三个 Tab + 两个全屏路由用 4 个 `rememberSaveable` 字段表达更直白，也避开一个未在本机验证过的版本依赖；若以后路由变复杂再换。
4. **`OfflinePhotoAnalyzer` 不在生产路径上被调用**。`AppContainer` 只装配 `ApiPhotoAnalyzer`，而它在 `configProvider()` 返回 `null`（没填 key）时直接给出 `NO_CONFIG` 失败，效果与离线实现相同。离线实现保留：Task 7 的契约测试用它验证"AI 完全不可用时录入仍可用"，调试构建也可手动换上。
5. **图标只用 `material-icons-core` 集合**，因此页面里的返回/上一层/搬动/删除都是文字按钮而不是箭头图标；Task 13 Step 10、Task 16 Step 3 各留了一条"若某图标 unresolved 就换文字并回来记一笔"的说明。
6. **搜索无拼音/模糊容错**（spec 第 6.3 节只要求关键词命中）。`search_text` 只有名称/品牌/别名/标签/描述/备注这几项进 `LIKE`（颜色与材质不在里面，见第二轮评审），拼音首字母与"外套装→外套"这类分词容错留给 Plan 2 之后看数据再决定。
7. **四个依赖退到 compileSdk 36 时代的最新版**：Compose BOM 2026.06.01（原写 2026.09.00）、core-ktx 1.18.0（原 1.19.1）、lifecycle 2.10.0（原 2.11.0）、Coil3 3.3.0（原 3.6.3）。Task 1 实施时逐个读到了每个 aar 里的 `aar-metadata.properties`：compose-ui 1.12.x、core-ktx 1.19.x、lifecycle-viewmodel-compose 2.11.0、coil 3.6.x 都写着 `minCompileSdk=37` + `minAndroidGradlePluginVersion=9.1.0`，而本机 `D:\Lib\Android\Sdk\platforms` 只有 android-29/34/35/36，AGP 8.13.2 自己也声明最高支持 compileSdk 36。Room 2.8.5、DataStore 1.2.1、OkHttp 4.12.0、activity-compose 1.11.0、Robolectric 4.16.1、androidx-test 1.7.0 都在 compileSdk 36 之下可用，保持不变。
   另一个方向是装 android-37 平台、把 AGP 提到 9.1、Gradle 提到 9.x，但那要动本机 SDK 和 PATH 上的全局工具链（本机 Gradle 由 version-fox 管理，且没有 Android Studio 可以点安装），超出一个 APP 开发计划的授权范围。**要升到最新 AndroidX 就得先做这件事，需要你决定。**
   顺带一条相关约束：Coil 3.5.0 及以上是用 Kotlin 2.4 编译的，其字节码 metadata 版本 2.4.0 超出了 Kotlin 2.2.21 编译器能读的范围（`Incompatible classes were found in dependencies`），所以 coil 只能取 3.3.0，除非把 Kotlin/KSP 一起升版。
8. **`ApiPhotoAnalyzer` 的超时归类从 OFFLINE 改成 TIMEOUT**：原计划代码 catch 的是 `java.net.SocketTimeoutException`，但 OkHttp 的 **callTimeout** 抛的是它的父类 `InterruptedIOException("timeout")`，会掉进 `IOException` 分支被报成"网络不可用"。生产路径只设了 callTimeout（spec 第 7 节的 15 秒），所以 API 响应慢会被误判成断网；改成 catch `InterruptedIOException` → `TIMEOUT`，`SocketTimeoutException`（connect/read 超时）作为子类同样命中。用户在 AI 失败时看到的提示词因此更准（"AI 响应超时"而不是"请检查网络"）。不改变降级行为：两者都是可重试 + 失败后手填。
9. **Plan 1 后半段的 ViewModel 层 JVM 测试一律用 `inMemoryDatabaseBuilder` + inline 执行器**（Task 13 起，Task 14/15/16 沿用）。`UnconfinedTestDispatcher` 让 `viewModelScope.launch` 立即返回，但 Room 默认把查询派发到自己的 IO 线程池，测试体会抢在状态更新前断言，`ReviewViewModelTest` 12 个测试曾因此全挂在 `expected:<…> but was:<null>`。`.setQueryExecutor(InlineExecutor)`/`.setTransactionExecutor(InlineExecutor)` 让整条链路在调用线程同步走完，才压得住竞态。同时 Task 13 的计划正文有 6 处编译级错漏，已就地改成本机跑过的代码：`toggleSeason/toggleTag/setQuantity/setExpires` 里的 `update { it.copy(draft = …) }`（`update` 的入参是 `ItemDraft` 不是 state，16 处 `Unresolved reference: draft`）、缺 `ItemDraft`/`LocationOption` import、`EXPIRE_KINDS` 的落点从 `domain/Models.kt` 改到实际存在的 `data/repo/Models.kt`、`LinearProgressIndicator(Modifier…)` 改具名参数 `LinearProgressIndicator(modifier = …)`（material3 1.11 的 indeterminate 重载与 `progress` 首位重载并存，具名更稳）。另有一条是**期望值写错而非代码错**：`plusMinusUpdatesQuantity` 原本断言 `quantity == 3`，但 `ItemDraft` 默认是 1，＋一次只能到 2——计划正文的注释自己也是这么说的，改断言不改实现。
10. **spec 第 6.2 节的"节点长按可新增子节点、改名、排序、拍照记录该位置"，同级排序没做成长按，做进了行上已有的 ⋮ 菜单**（批次 AF）。理由：改名/移动到…/删除这三项本来就在 ⋮ 里，同一批节点操作分成两个入口（长按一套、⋮ 一套）比少一个手势更值得守；而且长按在 Robolectric 里要额外造手势，界面上也就少一条能跑的断言。**这条是有意偏离 spec 原文**，否决它只需要把两颗按钮搬去 `combinedClickable`。同节还欠的"拍照记录该位置"已在批次 AG 做完，同样落在行内/顶部而不是长按——见下一条。
11. **spec 第 6.2 节的"拍照记录该位置"没做成相机取景，做成从相册选一张**（批次 AG）。理由与偏差第 1 条同源：取图入口整机只有一个 Photo Picker，`CameraX` 仍在 Plan 2 第 4 条；`LocationRepository.setPhoto` 收的是 `ByteArray`，将来接取景只替换取图那一步。位置照片**不进录入页、不碰 AI**——它就是"这层长什么样"的一张参照图，走 AI 反而会把它的文件名当成物品名。

## spec 覆盖对照

| spec 章节 | 要求 | 落在哪个任务 |
|---|---|---|
| 第 5 节 | 4 张表 + `search_text` 派生列 | Task 2、Task 3 |
| 第 5 节 | 位置树内存构建、面包屑、删除约束 | Task 4、Task 10 |
| 第 6.1 节 | 选图 → 落盘三档尺寸 → AI → 预填 → 确认 | Task 5、6、7、8、12、13 |
| 第 6.1 节 | AI 失败不阻塞录入、字段不被覆盖 | Task 7、8、12、13（`touched`） |
| 第 6.2 节 | 位置下钻、新建、改名、类型、搬动、删除 | Task 10、14 |
| 第 6.2 节 | 同级排序、拍照记录该位置 | 批次 AF（⋮ 菜单上移/下移）、批次 AG（位置照片：顶部横幅 + 上一层行内缩略图）；交互形态两处偏离原文，见偏差记录第 10、11 条 |
| 第 6.3 节 | 中文关键词搜索 + 结果排序 + 筛选 | Task 3、9、15 |
| 第 6.4 节 | 待归类收件箱 | Task 9（`inbox()`）、15（`inboxMode`）、16（Tab） |
| 第 6.5 节 | 到期字段与展示 | Task 2、13（选择器）、15（剩余天数）；提醒本体在 Plan 2 第 1 条 |
| 第 6.6 节 | 备份导出导入 | Plan 2 第 2 条（本计划仅保证 key 不落盘、`allowBackup=false`） |
| 第 7 节 | OpenAI 兼容调用、默认百炼 `qwen-vl-max`、15s 超时 | Task 6、7、8、11 |
| 第 8 节 | 照片私有目录、删除同步、DB 唯一真相源 | Task 5、9、15 |
| 第 9 节 | 测试策略（JVM 单测为主，UI 手工验收） | 每个任务的 Step「跑测试」+ Task 17 |
| 第 10 节 | M0–M4 | Task 1–16，Task 17 收口 |
| 第 1 节 | 目标（侧载可用、录入 ≤3 步、查找 ≤2 步）与非目标（无账号/云同步/小程序） | Task 1（单机 APK）、12–13（录入步数）、15–16（查找步数）；非目标体现为不引入任何网络账号依赖 |
| 第 2 节 | 为什么不用微信小程序 | 无需实现，只约束技术选型 |
| 第 3 节 | 技术选型：原生 Kotlin + Compose + Room，LIKE 搜索 | Task 1（构建链）、2、3、13 |
| 第 4 节 | 架构：单 Activity + `AppContainer` 手写 DI + ViewModel per screen | Task 11、16 |
| 第 11 节 | 9 条 `[待确认]` 的取舍 | 本计划的 Global Constraints 与"实施偏差记录"逐条沿用，实现期不再改动；见文末"需要你确认" |
| 第 12 节 | 环境事实：本机 `gradle 8.14.3` / `java 21` / SDK 在 `D:\Lib\Android\Sdk`，无 Android Studio | Task 1 Step 1（不生成 wrapper）、Task 17 Step 2（`adb` 用绝对路径） |

## 需要你确认（下午回来一次性过）

spec 第 11 节的 9 条 `[待确认]` 本计划全部按原样实现，没有再改动；只有下面 9 条是本计划新增的取舍，否决任何一条都只需要改一处：

1. Photo Picker 代替 CameraX（Plan 2 第 4 条补上取景）。
2. Plan 1 期间 destructive migration（Plan 2 第 6 条换真实 Migration）。
3. 不引入 `navigation-compose`，外壳自己管 4 个路由字段。
4. `OfflinePhotoAnalyzer` 只在契约测试里被使用，生产路径靠 `NO_CONFIG` 降级。
5. 图标只用 `material-icons-core`，所以返回/上一层/搬动/删除是文字按钮。
6. 到期只做到"录入 + 展示剩余天数"，提醒 Worker 与通知留到 Plan 2 第 1 条。
7. 四个依赖版本退到 compileSdk 36 时代（composeBom 2026.06.01 / coreKtx 1.18.0 / lifecycle 2.10.0 / coil 3.3.0）。要么这样，要么装 android-37 平台并把 AGP 提到 9.1 + Gradle 提到 9.x；后者要动本机 SDK 和全局工具链，不是这个计划该替你做决定。
8. AI 调用超时（callTimeout 15s）归类为 `TIMEOUT` 而不是 `OFFLINE`，提示词相应从"请检查网络"变成"AI 响应超时"。降级行为不变。
9. ViewModel 层的 JVM 测试改用 `inMemoryDatabaseBuilder` + inline 查询/事务执行器（只为消掉 Room IO 线程与 eager 测试调度器的竞态，产品代码路径不变）。

另外两件事需要你提供条件：真实多模态 API 的 `base_url` 与 key（Task 17 走 AI 分支必须有），以及一台能用 `adb` 连上的安卓机（M0 的验收标准就是装上能跑）。

**2026-10-02 补记（第二、三轮评审之后新增的待确认四条）**：上面 9 条是计划初稿的取舍，评审之后又攒了四条同等性质的决定，都是"两种做法都对但不是一种"，否决只需改一处：

10. ~~**收件箱的谓词与「搬到」打架**~~ **（已定：拍板①「搬动即确认，只确认有名字的」，`16d9918`）**（A 段那条已写明）。`ItemDao.inbox` 按 spec 是"没位置 **或** 还是 DRAFT"，而搬动不改状态，所以一件从没确认过的东西搬到有位置之后**仍然留在待归类**。要么让「搬到」顺手确认，要么改 spec 第 6.4 节那句话。
11. ~~**提前天数上限 90 天**~~ **（已定：拍板②放开到 365，`16d9918`）**，而 spec 第 9 节的验收步骤写着"调到 365"。`SettingsRepository` 会把它夹成 90，界面上到顶之后点「＋」不再涨（有提示，不是静默失败）。放开还是改验收话术，等你定。
12. ~~**颜色与材质不在 `search_text` 里**~~ **（已定：拍板③加进去，`16d9918`，配 `FIELD_VERSION` 启动补算）**（spec 第 4 节的字段表就是 名称/品牌/别名/标签/描述/备注，没有它们），所以搜"深灰外套"命不中。要不要加进去是产品判断：加了老物品还得重存一次才带上（派生列在写入时算）。
13. **提醒的确切语义按第三轮评审重写过**，与 spec 第 6.5 节那句"`expire_notified_at` 为空或早于本次窗口"不再逐字一致：现在是**每个截止日期最多两次**（进窗口一次 + 到期当天一次）、**DRAFT 同样提醒**、**改日期重新排队**、**权限被拒时不回写标记**。四条都有单测。spec 那句原文在"过期又没人处理"的东西上会每天重复播报，还会把一次 50 条的名额占满，所以我按行为合理性改了文案层面的谓词，没动表结构。要恢复原话只需把 `Daos.kt` 那个 `@Query` 改回去，代价写在那条验收项的反面用例里。

**2026-10-03 这条清单上当场生效的更新**：上面 10、11、12 都已拍板并实现，13 也已实现并有四条单测钉住。**现在真正还压在你那边的只有两件**，都不是"改一处就行"的取舍，而是要么加入口要么改判定：

- **要不要给 `ReminderWorker` 加一个只进 debug 变体的一次性触发入口。** 不加，C 段第 6–10 条就只能等 24 小时窗口——`cmd jobscheduler run -f` 对周期性任务无效（WorkManager 自己按 `intervalDuration` 把这次执行推后了，实测日志与库里 `expireNotifiedAt` 仍为 NULL 都印证）。加，就是一个新的调试入口，我不替你决定要不要往产品里塞。
- **渠道被单独静音时，设置页该不该说话。** 现在 `canNotify()` 只看应用级开关，看不见「到期提醒」这一个渠道 `mImportance=0`；那种状态下 Worker 会往死渠道里发、还照样回写 `expireNotifiedAt`，那件物品的到期就永久沉默。修法是把 `getNotificationChannel(CHANNEL_ID)?.importance == IMPORTANCE_NONE` 也算进 `canNotify`，并给第三句话（告诉人去系统里把这条渠道打开）。改的是判定逻辑加一句文案，没动。

同一段里另外三条 spec 写过但**没做**的，属于功能不是取舍，等你排优先级：通知点击应落到"筛选后的照片墙"（spec 第 6.5 节原话；现在只打开应用落到默认 Tab，要做需要 `launchMode`/`onNewIntent` 一类的接线）、位置节点的长按与排序（spec 第 6.2 节）、以及照片的 EXIF 方向（`PhotoStore` 重编码会丢方向信息，竖拍的照片可能横躺）。

**2026-10-02 补记（评审修正之后）**：Task 13–16 交付后那轮独立评审带来的 7 条修复没有引入新的取舍，全部是按 spec 补齐（第 6.1 节的"不覆盖已填字段"、第 8 节的"每种失败给可读中文"、以及卡顿/崩溃这类工程底线），细节在 Task 17 Step 1 的表格里；`gradle :app:testDebugUnitTest` 从 138 涨到 **149 个测试全绿**。

Step 3 已经拆成 A 段（19 条，完全不碰 key，现在装好 APK 就能验收）和 B 段（3 条，必须有 key）。B 段现在还缺的不是代码质量而是一个入口：设置页（计划 2 Task 7）现在只有「到期提醒」和「备份」两段，`app/src/main` 里依旧没有任何界面会调 `SettingsRepository.setApiKey/setBaseUrl`，`currentConfig()` 恒为 null，所以真机上所有照片都会走 `NO_CONFIG`。这是你决定"先不做 AI 分支"的直接后果，要跑 B 段还得往设置页里加三行输入框。

Plan 2 做完备份与到期提醒之后，Step 3 又补了 C 段（评审修正 + Task 7 之后 9 条，同样不依赖 key）：导出/导入/覆盖/坏包四条，提前天数一条，通知与提醒语义四条。所以现在的顺序是 A 段 → C 段（这两段共 28 条是本轮验收的全部范围），B 段等你带 key 回来再开。

非 AI 且还没做的，当时是 Plan 2 表里的 1/2/4/5/6/7 六条：到期提醒 Worker 与通知（spec 第 6.5 节已写死行为，不依赖你的决定）、备份导出导入（第 6.6 节，同样已定；注意导出必须排除 DataStore 里的 key）、CameraX 取景、位置排序与位置拍照、真实 Room Migration、EXIF/大图。其中只有提醒的**通知权限申请时机**和备份的**入口放哪儿**算界面决定，其余按 spec 直接做即可。

**2026-10-02 再补记（计划 2 之后）**：前四条（备份导出/导入/数据页 + 到期提醒引擎/Worker）已经做完并进了 C 段清单，通知权限在用户主动点「开启通知」时才申请。这五六个提交随后过了一轮独立评审，改掉的八条（导出大小、导入大小、过期只提醒一次、聚合文案、目录条目、异常中文收场、导出走事务快照、通知状态随前台刷新）逐条记在计划 2 文末「评审修正」，其中"过期只说一次"这条改了提醒行为，所以 C 段加了对应那条（第三轮重写语义之后它排在第 8）。

紧接着的 C 段第 5 条来自一个更 basic 的缺口：那条清单写着"把提前天数调一调再看窗口"，而这个应用当时没有任何地方能改它——计划 2 Task 7 补上了。原 Task 3 的「数据」页随之并成「设置」页（`ui/data/` → `ui/settings/`），底栏第 4 个 Tab 的 label 改成「设置」，提醒提前天数与通知引导放在上半段、备份放在下半段，代码是 `git mv` 挪过去的，一行没重写。剩下的 CameraX 取景、位置排序与位置拍照、真实 Migration、EXIF/大图仍然没做，全部需要你决定优先级或者依赖真机条件，不是这轮验收的阻塞项。

**2026-10-02 三次补记（第三轮评审 + 「换一张」）**：A 段从 18 条变 **19 条**（新增"换一张还是同一件物品、不丢字段、不留空壳"），C 段从 8 条变 **9 条**（提前天数那条补上"连点 5 下要落在 8"与"改成 0 仍会说一次从未提醒过的过期物"，通知那条补上"拒权限不许写标记"，再加一条提醒总则）。A 段里那句"已知没修：换一张会留空壳"已经划掉，换成两条真的还没修：**EXIF 方向**与**通知点击不带筛选**。清单里另有两处话术纠正：位置树的 ⋮ 管的是位置节点、搬一件物品走详情页或照片墙；旋转屏幕时表单内容和标签输入框都活着，但开着的弹窗会被关掉。测试 **25 个类 / 207 例全绿，0 失败 0 跳过**，`app-debug.apk` 约 14.4 MB，schema 未动。

**2026-10-03 批次 Q：第五轮自审（设置页 / 提醒链路 / AI 文案）**

这一轮逐眼读的是前四轮没覆盖的四处：`ui/settings/SettingsScreen.kt` 与 `SettingsViewModel.kt`、`ui/common/ItemRow.kt`、`data/work/ReminderWorker.kt` + `ReminderScheduler.kt` + `domain/ReminderEngine.kt`、`MainActivity.kt` + `WheremyApp.kt` + `di/AppContainer.kt` + `AndroidManifest.xml`，外加 AI 那条链路的文案（`data/vision/Prompt.kt`、`AnalysisParser.kt`、`ApiPhotoAnalyzer.kt`、`ui/common/UiFormat.kt`）。改出来两条"文案说的事代码没在做"：

1. **设置页那句「已就绪：走 X」说的是一个当下不会被用到的模型。** 它读的是文本框里的 `model`，而真正发请求时读的是 DataStore 里存着的那一份（`SettingsRepository.currentConfig()`）。把模型名改成别的、还没按保存，这一行立刻改口念新名字。上一轮 `readinessLineFollowsStoredConfigNotTheDraft` 把**开关**那一侧修诚实了（AI 用不用得起来自落盘配置），漏了**名字**这一侧。修法是 `SettingsUiState` 多一个 `savedModel`，只在 `init` 回读、`saveAiConfig` 落盘、以及三笔写半途失败后回读真值这三处跟着"存着的那一份"动，正文改用它——`aiUsable` 的口径没变，还是那个开关，所以 `readinessLineFollowsStoredConfigNotTheDraft` 原样通过。
2. **`failureText(OFFLINE)` 写着「已跳过 AI 分析」，可它没跳过。** 这条链路真发了请求，而 `OFFLINE` 在 `RETRYABLE` 里，所以最多两次，只是以 `IOException` 收场。换成「网络不可用，AI 没连上，先手填，联网后可以重试」，跟同排那几条一个句式：说清发生了什么 + 下一步是什么（超时的「先手填，稍后可以重试」、无 key 的「直接手填就行」）。

测试 **32 类 / 274 例**，`--rerun-tasks` 整包全绿。两条新用例各自反向验证过：把产品代码分别改回 `state.model` 和那句「已跳过」，只红 `readyLineNamesTheStoredModelNotTheOneBeingTyped` 与 `offlineSaysTheCallWasTriedAndFailedNotThatAnalysisWasSkipped` 各一条，验完还原。历史清单不重写，只在这里指认：Task 13 那份 `failureText` 代码清单里的 OFFLINE 一行、以及计划 2 Task 7 附近「已就绪：走 X」的取值，均以源码为准。

核过、确认代码真做得了的（所以一个字没改）：`android:allowBackup="false"` 对得上设置页最后那句；「照片和记录会一起删除，无法恢复」——`ItemRepository.delete` 连 `photoStore` 里的原图和缩略图一起删，`deleteRemovesRowTagsAndFiles` 钉着；覆盖导入那句「中途断开不会留下半个库」——清表与重建排在同一个 `db.withTransaction` 里，删本地照片和搬新图在事务之后，而包里的字节先落到 `incoming/` 临时目录；`Prompt` 要模型给的十个字段一路都有落点（`brand` 从解析到预填到详情页那一行到 `search_text`，没有"要了不接"的）；`MAX_ATTEMPTS = 2` 对得上"失败重试 1 次后降级"；`callTimeout` 15 秒与 `InterruptedIOException` → `TIMEOUT` 的归类；`ReminderWorker` 只在通知真发出去之后才回写 `expireNotifiedAt`；`ensureChannel` 在 `Application.onCreate`，早于第一次排班；底栏四个 Tab 的 label 与 FAB 的 `contentDescription` 没有谎话；`SuggestLocationUseCase` 对不上树就返回 null，绝不虚构位置——所以「AI 猜的位置：衣柜」那句在树里没有衣柜时说的仍然只是"猜的"。

看了决定不改的三条：① `AnalysisResult.isEmpty` 只看 name/category/description/tags 四项，模型若只回了颜色和材质会被判成 `BAD_RESPONSE`（「AI 返回内容无法解析」）并整个丢掉。准确的说法是"没返回可用内容"，但这种回法实测没出现过，为它单开一条文案不划算。② `formatBytes` 用 1000 进制的除数配 KB/MB 标签（31457280 显示成 "31 MB"，真值 30 MiB），注释里写明只求量级、并且避免依赖小数点。③ 导入完成报的「照片 z 张」数的是入库行数，`moveStagedPhotos` 里单张搬不进去会被 `runCatching` 吞掉——`BackupRepository.kt:93` 那段注释已经把这个取舍说在明处（DB 是唯一真相源，那几个字节还完好躺在用户选的包里，重导一次就补回来）。另外位置页里 `ItemRow` 那行「在 家 / 鞋柜」在它本来就在这一层时被说了两遍，属于冗余不是假话，没动。

**这是最近几个提交里第一次动 `app/src/main`**（前面八个只碰测试和文档），所以手机上装的那个包现在落后这两条文案。要看效果重装一次即可；不重装也不影响 A/C 段其余验收项，改的都是显示层的一行字。新包已经打好：`app/build/outputs/apk/debug/app-debug.apk`。

**2026-10-03 批次 R：外壳 `WheremyRoot` 的界面测试**

五条用例，都在 `app/src/test/java/com/wheremy/app/ui/WheremyRootTest.kt`：四个 Tab 各通向哪一屏（并且收件箱那句「收件箱是空的，都已归类。」不许和照片墙那句混用）、FAB 的 `contentDescription` 说的是"选照片"而不是"拍照"、待归类只列没有位置的那件但照片墙两样都列、从详情页「编辑」进确认页改名保存之后库里还是同一件物品且位置没丢、位置树下钻时返回键先退一层而不是把整个应用推出去。测试数从 32 类 / 274 例涨到 **33 类 / 279 例**，`--rerun-tasks` 全绿；这一批产品代码一个字没动，所以手机上那个包还是批次 Q 打出来的。

三段"这次才闹清楚"的，写给下一个写界面测试的人：

1. 这一屏用的是**真 `AppContainer`**（文件库 + 真 PhotoStore），不是别的界面测试那套 `Room.inMemoryDatabaseBuilder(...).setQueryExecutor(InlineExecutor)`——要验的就是容器到屏幕那根线，自己拼仓库等于没验。代价是读库落在 Room 自己的线程池上，`waitForIdle()` 等不到它，跨屏之后的断言一律得用 `waitUntil*`。顺带钉住两条 API 事实（`javap` 查过 1.11.4 的 jar）：`waitUntilExactlyOneExists` / `waitUntilAtLeastOneExists` 挂在 **junit4 的 `ComposeTestRule`** 上且标了 `@ExperimentalTestApi`，类上得 `@OptIn`；`SemanticsNodeInteraction` **没有** `waitUntilExists`。清库要连 `-wal` / `-shm` / `-journal` 一起删，只删主文件时留下的写-ahead 日志会把上一例的数据带回新库。
2. 底栏那颗 Tab 得用 `isSelectable().and(hasText(...))` 才取唯一：`hasText("待归类")` 单用会撞上照片墙卡片给没位置的物品印的那一行（`GalleryScreen.kt:280`），`hasText("照片墙")` 和 `hasText("待归类")` 还各自撞上那一屏自己的顶栏标题（`GalleryScreen.kt:79`）。
3. 两处自己踩到的写法错误。**等的文字必须只在目标屏上出现**——"保存之后等 `新外套`"其实被还没退场的确认页输入框满足了，于是紧接着那下「返回」点在录入页上，报的是"找不到节点"而不是断言失败；改成等 `编辑`（`DetailScreen.kt:86` 独有）才算认回详情页。**按值取到的输入框不能清完接着用**——`performTextClearance` 之后 `hasText("旧外套")` 已经找不到那个节点，得按标签 `hasText("名称 *")` 重新定位、用 `performTextReplacement` 一次写完。（`SettingsScreenTest` 里同一个 handle 连清带写没事，是因为它从头到尾都按标签匹配。）

反向验证动了三次产品代码，各自只红该红的：删掉 `TreeBackHandler(vm)` → 只红返回键那条；`onEdit` 里不写 `reviewItemId` → 只红编辑保存那条（确认页进不去，`LaunchedEffect` 走 `else -> reviewOpen = false`）；把待归类那个 Tab 的 `inboxMode` 改成 `false` → 只红收件箱那两条。第三条最初连带红过编辑那条，正是那次"不该红的红了"暴露了上面第 3 点的时序漏洞；换成等 `编辑` 之后三次改动杀到的用例数变成 1 / 1 / 2，同一批用例连跑三遍稳定。

仍然只能在真机看的：`PickVisualMedia` 的结果在 Robolectric 里投递不进去，所以「录入页开着时再选一张要走同一个物品」那条（`appViewModel(key = "review-$reviewSeq")` 与 `LaunchedEffect(reviewSeq)` 的配合）没有测试钉住，A 段那条还得手点。

**2026-10-03 批次 S：第六轮自审（照片链路与备份说法）+ 两条拍板搬进界面层**

先说结论：**这一轮没有该改的产品代码**。读的是五段以前只对着"行为"、没对着"说法"核过的代码：

- `PhotoStore`：1600/q85、256/q80、1024/q80 三个尺寸与 spec 第 5 节一致；`decode` 先按 `inSampleSize` 降采样再缩放，所以"手机原图整张进内存导致 OOM"那句注释不是摆设；写一半失败时那两个文件由写它的人扫干净（`ItemRepository` 的清理只认已经落库的行，拿到 `StoredPhoto` 之前它无能为力）。`CaptureFlow.reanalyze` 与 `ItemRepository.photoBytesOf` 也对得上「冷启动后的重新分析只能用 1600 原图」——它读的是 `originals/` 那一份，不是缩略图。
- `export`：四张表在同一个事务里读，包只有 `manifest.json` + `data.json` + `photos/`，而构造函数里根本没有 `SettingsRepository`——所以设置页那句「备份文件里不含 API key，也不含任何设置」是物理上成立，不是靠约定。
- 合并导入：`writeRows` 在 merge 时按各表主键把本机已有的行过滤掉，`upsertAll` 只收到"没有的那些"，所以「合并=已有的条目原样保留，只补没有的」确实意味着不会拿包里的版本盖掉本机改过的字段。
- 覆盖导入的确认框「先把现在的物品、位置和照片全部删掉，再按备份包重建」：四张表 `deleteAll`，加上 `removeLocalPhotosNotIn(keep)` 删掉包里没带的照片文件，最终磁盘上剩下的正好是包里那批——这句话成立。
- 解包侧 `isPhotoPath` 只认 `originals/`、`thumbs/` 两个目录名、必须恰好一个 `/`、不含 `..`，所以一个来路不明的 zip 写不到应用外面去。

看了不改的一条：`LocationNotEmptyException` 在只卡一项时会说「该位置下还有 0 个子位置和 2 件物品」，那个"0 个"是噪音不是假话；这一轮想保住的是"不动 `app/src/main`"，手机上那个包还能接着跑 A/C 段剩下的验收。

这批真正的产出是两条拍板以前只有仓库层测试钉着、界面那一段还得真机手点，现在进了 `GalleryScreenTest`（6 例 → 9 例）：

- `typingInTheSearchBoxNarrowsTheWallByColor`（拍板③）：搜索框打「红」必须让「羽绒服」留着、「手套」消失；打成搜不到的词时出场的是「按当前的搜索和筛选没找到物品」，不是「还没有物品」。以前 `searchFieldAdvertisesColorAndMaterial` 只验了提示语里写了颜色与材质——那颗框的 `onValueChange` 接到别处去，测试照样绿。
- `movingADraftByCardConfirmsItSoTheInboxEmpties` 与 `movingAnUnnamedDraftStillLeavesItInTheInbox`（拍板①的两半句）：卡片 ⋮ →「搬到…」→ 选「家」，有名字的 DRAFT 立刻离开收件箱并且库里 `status = CONFIRMED`；没名字的那件**位置写进去了、人还留在收件箱**（卡片上写着「家」，列表却没空）。后半句那个刻意的不对称最容易被后来人"顺手修平"，所以它也得有界面层的钉子。顺带确认：照片墙与详情页那两个「搬到」对话框都不带新建输入框，而 Robolectric 下对话框里唯一等不到 idle 的就是文本框，所以这条链路点得动、测得了。

三次反向验证各杀一条：`ItemRepository.move` 去掉确认那三行 → 只红第一条；条件里去掉 `e.name.isNotBlank()` → 只红第二条；`onValueChange = vm::setQuery` 换成空实现 → 只红第三条。测试数从 33 类 / 279 例涨到 **33 类 / 282 例**，整包 `--rerun-tasks` 全绿，产品代码没动。

**2026-10-03 批次 T：AI 失败分类补齐 400 / 402，B 段不用猜**

动这条是因为 B 段最可能撞上的两种失败，以前都归成同一句话。`ApiPhotoAnalyzer.attempt` 原来的分支是 `401/403 → UNAUTHORIZED`、`429 → RATE_LIMITED`、其余非 2xx 一律 `!resp.isSuccessful → SERVER`，而 `SERVER` 在 `RETRYABLE` 里——所以模型名填错、账户没钱这两种"重试第二次还是同一句拒绝"的情况，以前会**连着发两封**，然后在确认页上报「AI 服务暂不可用，可点重新分析」。那句话把方向指错了：真正要办的是改设置里的模型名，或者充值。

改了四处：

- `PhotoAnalyzer.kt`：`FailureReason` 加 `BAD_REQUEST` 与 `INSUFFICIENT_BALANCE`。
- `ApiPhotoAnalyzer.kt`：`402 → INSUFFICIENT_BALANCE`；`400` 先看响应体，`isBalanceComplaint(text)` 为真也判 `INSUFFICIENT_BALANCE`，否则 `BAD_REQUEST`。**为什么 400 要读正文**：DeepSeek 把"余额不足"报成 400 而不是 402（实测措辞是 `Balance insufficient or expired` / `insufficient_user_quota`），只看状态码就会把用户派去核对模型名。词根取 `balance`/`quota`/`余额`/`额度` 四个，只作用在 400 这一支，401/429 仍旧先看状态码。
- 两个新 reason **都不进 `RETRYABLE`**（`MAX_ATTEMPTS` 还是 2，只有服务端/网络抖动值得再试）。
- `UiFormat.kt`：两条新文案——`BAD_REQUEST`「请求被服务端拒了，多半是设置里的模型名或接口地址填错，改对后再点重新分析」；`INSUFFICIENT_BALANCE`「AI 账户没有可用余额或额度，充值或换账户后再点重新分析」。句式跟同排那几条一致：说清发生了什么 + 下一步是什么。

诚实说一句边界：若某家把余额不足报成 400 而措辞里四个词根一个都没有，它会落到 `BAD_REQUEST`，说的是"核对模型名"——那是文案偏了，不是行为坏了。

测试 **33 类 / 285 例**（`ApiPhotoAnalyzerTest` 10 例 → 13 例；额度那条一轮里跑英文和中文两种措辞，末尾断言"两次分析各发一封"），整包 `--rerun-tasks` 全绿。`UiFormatTest.everyFailureReasonHasReadableChineseText` 遍历 `FailureReason.entries`，所以两条新文案不必新写用例也被盯着不许留空、不许只给英文。

五次反向验证，各杀该杀的：把 400 那支改成恒 `BAD_REQUEST`（不看正文）→ 只红额度那条；注释掉 402 分支 → 只红 `paymentRequiredReadsAsNoQuota`；`BAD_REQUEST` 进 `RETRYABLE` → 只红 withoutRetry 那条；`INSUFFICIENT_BALANCE` 进 `RETRYABLE` → 红两条（400 与 402 各自断言"只发一封"）；去掉中文词根 → 只红额度那条。每次验完还原，并与备份 `diff` 一致。

这批又动了 `app/src/main`（批次 Q 之后第二次），手机上那个包还落后这三条 AI 文案。跑 B 段前先重装 `app/build/outputs/apk/debug/app-debug.apk`（14,345,283 字节），MIUI 会弹「继续安装」要点确认。历史快照不重写，只在这里指认：Task 8 那份 `enum class FailureReason {...}` 与 Task 13 那份 `failureText` 清单均以源码为准；B 段开头"没有入口"那一段也已在上文标注被批次 L 取代。

**2026-10-03 批次 U：`finish_reason=length` 不再冒充"无法解析"（外加一处测试脚手架的竞态）**

`parseCompletion` 以前只抓 `choices[0].message.content`，服务端怎么收口的（`finish_reason`）压根没看。这对推理模型是个真问题：批次 L 实测 `deepseek-flash` 一张纯色小图就在 reasoning 上花掉 75 token，认真看图时答案可能在 `max_tokens` 处被切成半句 JSON——界面报的是「AI 返回内容无法解析，可点重新分析」，把一次**正常收口**说成了"模型不会写 JSON"，而真正有用的一句话是"答案太长被切断了"。

- `FailureReason` 加 `TRUNCATED`；`ApiPhotoAnalyzer.parseCompletion` 先读 `finish_reason`，等于 `length` 就返回 `TRUNCATED`（`content` 整个缺席也走这一支，不再顺着"缺字段"落到 `BAD_RESPONSE`）。不进 `RETRYABLE`：同一份 `max_tokens` 再要一次还是同一句切断。
- 文案：「AI 的回应太长被切断了，可点重新分析，或换一张只拍一件物品的照片」。
- 测试按红的先来：`ApiPhotoAnalyzerTest` 先加两条（半句 JSON + content 缺席），跑出来 **15 例里恰好这 2 例红**，再写实现转绿。响应体助手里补了 `finish_reason`，默认 `"stop"`，跟真实回应一样——于是"stop 不许被误判成切断"这件事由既有的 `successParsesContentIntoResult` / `serverErrorThenSuccess` / `unparseableContentIsBadResponse` 三条盯着，不用另写。
- 两次反向验证：去掉 `finish_reason` 判定 → 只红新加的那两条；把条件放宽成"有 `finish_reason` 就算切断" → 红的是那三条既有用例。前者证明新用例不空转，后者证明它们也不是只会朝一个方向红。

顺带撞出来一处**测试脚手架的竞态**（与产品代码无关，但整包会红给它看）：`TestSettings.resetQuietly` 只等 3 秒。用例点「保存 AI 配置」的那三笔写已经交给 DataStore 自己的线程，`viewModelScope.cancel()` 收不回在路上的一笔；`testDebugUnitTest` 与 `assembleDebug` 同时跑时机器是满的，迟到笔能拖过 3 秒——于是 `readinessLineFollowsStoredConfigNotTheDraft` 在 tearDown、`readyLineNamesTheStoredModelNotTheOneBeingTyped` 在 setUp 各抛一次同一句"上一个用例的写还在盖这份默认值"（同一条边界，一次事件两处报）。改成预算 10 秒 + **连读两次都干净**才算清干净：清完先看一眼，隔 150ms 再看一眼，迟到笔正好落在两次之间也会被抓住再清一遍。证据：改之前四次带 `assembleDebug` 的整包里红两次，改之后连续五次全绿；单独把六个碰 DataStore 的类捞出来跑四次都不红——它要的是整包那种满载状态。说清边界：这不是根除，迟到笔若真拖过十秒还是会报，但那时候"再等更久"也帮不上，报出来才是对的。

测试 **33 类 / 287 例**（285 → 287），整包 `--rerun-tasks` 全绿。这批又动了 `app/src/main`，手机上那个包落后三条 AI 文案（批次 T 的两条 + 这条），跑 B 段前重装 `app/build/outputs/apk/debug/app-debug.apk`（14,345,283 字节）。历史快照照旧不重写：Task 8 那份 `enum class FailureReason {...}`、Task 13 那份 `failureText` 清单都以源码为准。

**2026-10-03 批次 V：debug 包留一颗「立刻检查一次到期提醒」，静音频道按拍板钉住**

这一批落的是用户 2026-10-03 的三条拍板：① 手机上的 Wheremy 已经卸载，允许我自己重装；② 按建议加那颗 debug 触发入口，好让我自己把 C 段第 6–10 条验完，不必等 24 小时窗口；③ **把「到期提醒」这一个渠道单独静音仍然算通知到了**——人静音的是声音，不是那条会留在通知栏里的提醒。

**第③条最后是"确认现状 + 加钉子"，产品代码一行没动。** `canNotify()` 看的只有运行时权限与 `areNotificationsEnabled()`（应用级总开关），从来没查渠道 importance，所以"静音也算送到、照样回写 `expireNotifiedAt`"本来就是它的行为。要保住的是下一个人不"顺手修平"它，于是 `ReminderWorkerTest.mutedChannelStillCountsAsDelivered`：授权 → `ensureChannel` → 把那条渠道的 importance 按到 `IMPORTANCE_NONE` → 断言 `canNotify` 仍为 true 且那件物品被回写。反向验证照着"下一个人会怎么改"写：真把 `getNotificationChannel(CHANNEL_ID)?.importance == IMPORTANCE_NONE` 算进 `canNotify` → 只红这一条。踩到的 Robolectric 事实记一句：`ShadowNotificationManager.getNotificationChannel` 是 **protected**，测试里要用真的 `NotificationManager` 去拿渠道。

**第②条四处改动：**

- `ReminderScheduler.runNow()`：一颗 `OneTimeWorkRequestBuilder<ReminderWorker>`，用 `enqueueUniqueWork(RUN_NOW_NAME, REPLACE, …)` 排成一次性任务。`REPLACE` 而不是 `KEEP`：连按两下时用户要看的是第二下那一次的窗口。提前天数仍由 Worker 每次现读，所以这条一次性任务与每日那一条用的是同一套取数。
- `SettingsViewModel` 多一个 **不给默认值** 的 `reminderRunNow: () -> Unit`：漏接一处编译就不过，比界面上摆一颗按了没反应的按钮好查。`runReminderCheckNow()` 只做两件事——把工作交出去、说一句「已把一次到期检查交给系统排队：按现在的提前天数算窗口，没有临近到期的物品就不会有通知」。这句是刻意不说"已发出"的：这一屏并不知道 Worker 跑成什么样，按下去没弹通知是常态而不是故障。
- `SettingsScreen(vm, showDebugTools: Boolean = BuildConfig.DEBUG)` 在「到期提醒每天检查一次…」那一行下面摆 `Text("调试：立刻检查一次到期提醒")`。门是**参数**而不是散在函数体里的 `if (BuildConfig.DEBUG)`：正式包不许出现这个入口，但测试必须能把两侧都摆出来。
- `WheremyRoot.kt:230` 接 `it.reminders::runNow`。

**测试 33 类 / 287 例 → 33 类 / 291 例**（`SettingsViewModelTest` +1、`SettingsScreenTest` +2、`ReminderWorkerTest` +1）。红的先来：四条新用例编译期就报 `Too many arguments for constructor(backup, io, settings)`×3、`Unresolved reference 'runReminderCheckNow'`、`Too many arguments for fun SettingsScreen(vm)`——先把我自己写错的两处（拿 protected 那个 `getNotificationChannel`、`SettingsScreenTest` 少 import `assertEquals`）修掉，剩下的失败才纯粹是"功能还不存在"。中途改过一次断言：VM 那条原本要消息里带「提前 N 天」，但仓库自己的规矩是 `LEAD_DAYS_MIN` 那档不许写"提前 0 天"，把数字塞进消息就得在这儿再复制一遍那条特例；改成要求消息说清「没有临近到期就不会有通知」，那才是按下去真正需要知道的事。

**四次反向验证**：删掉 `reminderRunNow()` 那一行 → 红 `debugBuildOffersAnImmediateReminderCheckWiredToTheHook` 与 VM 那条（2 条）；删掉消息那一行、把 `if (showDebugTools)` 写成 `if (true)` → 合起来只红 VM 消息那条与 `releaseLikeBuildHidesTheImmediateReminderCheck`（2 条）；`canNotify` 查渠道 importance → 只红静音钉子那条。每次验完从 `/tmp/tmut/*.bak` 还原，并与 `git diff` 核过只剩这批的改动。

**顺带把测试脚手架那处竞态按根因改了（与产品代码无关）。** 批次 U 留下的 `resetQuietly` 是"写五笔 → 等 150ms → 读，脏了就再写五笔"，满载时这笔账算错：五笔写排在同一条线程上，一笔要几百毫秒，150ms 后读到的还是"排在后面那笔没落地"时的旧值，于是判脏、再写五笔——**闸门自己一直在往队列里灌写**，读到十秒都不干净就抛"上一个用例的写还在盖这份默认值"（这次是 `readinessLineFollowsStoredConfigNotTheDraft` 在 tearDown、`readyLineNamesTheStoredModelNotTheOneBeingTyped` 在 setUp 各撞一次；每轮看着脏的那个字段正是五笔里排最后落地的 `api_key`）。改成**先只读不写，读到连着 6 次（每 50ms）值都不再变，才认为路上没写了**，然后才发一轮默认值，再等一次静下来确认干净；脏判据抽成 `Settings.pristine`。

同一批还冒出另一处同源的竞态（批次 L 起就存在，这次第一次撞到）：`aiEditsStayDraftsUntilSaved` 在 tearDown 抛 `Dispatchers.Main is used concurrently with setting it`。机制是 DataStore 那笔写落在它自己的线程上，写完要把 `viewModelScope` 里剩下的半段（`_state.update { message = AI_SAVED }`）交回测试的 Main 调度器执行，`Dispatchers.resetMain()` 正好撞进那一瞬间。修法是把"还原默认值"那一句提到 `resetMain()` **之前**（两个设置类都改）：它现在兼作上面那道静下来的闸门，过了它就没有在飞的写续体了，`resetMain` 落在干净的地方。说清边界：这是把窗口压小、不是根除——`cancel()` 收不回已经交给 DataStore 线程的那一笔，这一点没变。另外我一度给两条 `保存 AI 配置` 的用例加了"等自己那一笔落盘"的 `changes.first {}`，满载时反而 5 秒超时（`SettingsViewModelTest` 里同样的等法在 `runTest` 下是好使的，差别没查下去），所以那三处**回退了**，统一交给闸门兜。

验证：`--rerun-tasks` 整包 33 类 / 291 例全绿；`assembleDebug` 与四个碰 DataStore 的类同批连跑（批次 U 那种满载状态）**3 轮全绿**。

**真机走查（同一批，Xiaomi 13 / HyperOS，2026-10-03 07:00–07:30 本地时间）**

拍板①之后重装了 `app-debug.apk`（14,345,283 字节，MIUI 仍要把安装命令挂后台再点手机上的「继续安装」）。重装顺带把上一轮那个悬着的问题关掉了：渠道「到期提醒」现在确实是代码里那个 `IMPORTANCE_DEFAULT`——`dumpsys notification` 里 `mId='wheremy_expire', mName=到期提醒, mImportance=3`，而通知记录本身 `importance=3`。**这是"卸载重装才会按代码重建渠道"那句话的实测确认。**

数据不是手输的，这轮的第一个大发现是 **adb 往这台机子注键盘事件根本不可用**：默认输入法是 `com.tencent.wetype/.plugin.hld.WxHldService`（小鹤双拼），`input text abc` 和 `input keyevent KEYCODE_M/I/L/K` 都被它当成拼音吃掉、变成一溜中文候选，屏幕上方出现 `mi'lk'ca'r'to…` 和「密令擦入托那保密卡」，输入框里一个字母都没落。机上没有 AOSP Latin 输入法可切（第三方只有 wetype / sogou.xiaomi / iflytek / baidu，`ime show` 这个命令在这台 MIUI 上还不支持）。所以测试数据是**用应用自己的导入通道造的**：本地拼两个备份包推进 `/sdcard/Download`，一个走「覆盖导入」（先弹「覆盖导入会清空本机数据」），一个走「合并导入」——`wm-seed-window.zip` 给位置 `wm-room` 和四件物品（下面那张表按 `data.json` 里的 **id** 称呼它们，界面上显示的是名字，两套不一样：`wm-b-today`/`wm-today` 今天 10-03 CONFIRMED、`wm-e-draft` **DRAFT 且 `locationId=null`**（所以它站在「待归类」那一格而不是位置页里）、10-04，`wm-a-near`/`wm-near` 10-05、`wm-d-far`/`wm-far` 12-31），`wm-seed-expired.zip` 单独并进来一件 2026-09-20 的 `wm-c-expired`/`wm-expired`。改到期日也不碰键盘：走详情页 →「编辑」→「到期提醒」那行的「设置」→ 日历点一天 →「确定」→「保存」。

读库的那条手段要单独记一句，因为它是这轮能自证的唯一依靠，而上一轮写在文档里的做法是错的：**`run-as` 拷出来的库在 Windows 侧读不出 `item` 表**（主文件 4096 字节、`sqlite_master` 里只有 `android_metadata`，行全在 185 KB 的 `-wal` 里，而那份 WAL 的 45 帧有 40 帧 salt 与头对不上，本地 `sqlite3.connect()` 还会顺手把拷来的 `-wal` 删掉）。可信的读法是**在手机上按「导出到文件」，把 `wheremy-backup-*.zip` 拉回来解 `data.json`**——走的是应用自己的读路径。注意 `BackupFormat` 的 `explicitNulls = false`：**列为 NULL 时那个键整个不出现**，脚本里 `i['expireNotifiedAt']` 会 `KeyError`，得用 `.get()`。

实测到的五条（时间戳都是设备本地）：

| 按下去之前 | 按下之后 |
|---|---|
| `pm revoke` 到 `granted=false`，设置页那两行都在 | 07:11:29 抬手 → .019 `Scheduling work ID c4afe522…Job ID 4` → .042 `Worker result SUCCESS`；通知 0 条；导出里四件 `expireNotifiedAt` **全缺键**（=NULL），一行没被悄悄写上 |
| `pm grant` 回 `granted=true` | 07:13:20 按一次 → `SUCCESS id=30b1b6f3` → `NotificationRecord(id=1001, channel=wheremy_expire)`，正文**「3 件物品临近到期，其中 1 件今天到期」**；导出 (3) 里 `wm-b-today` / `wm-e-draft` / `wm-a-near` 各 = `1790982800191`，`wm-d-far` 仍 NULL |
| 同上，三件已带标记 | 07:15:02 **再按一次**（`SUCCESS id=0f3f1d4f`）→ 导出 (4) 里三枚标记仍是 `1790982800191`、`wm-d-far` 仍是缺键，跟 (3) 一模一样（两个包不可能字节相同，`manifest.exportedAt` 每次都变，比的是 `data.json` 里那几枚标记）：说过的不再说 |
| 合并导入 `wm-expired`（09-20，已过期 13 天） | 导出 (5) 里它 `notified=null` → 按一次，正文换**「1 件物品到期或已过期，最早的已过期 13 天」**并标上 `1790982984409` → **再按一次，导出 (6) 里那枚标记一个字没动** |
| 走界面把它的日子改成 10-04 | 详情页那行立刻变成「到期 · 2026-10-04（还剩 1 天）」→ 导出 (7)：`expiresAt=1791043200000`、`notified` **键消失**（`reArm` 真把标记清了）→ 按一次，正文又说了一遍**「1 件物品临近到期，最近的还剩 1 天」** |

第 8 条（权限给了但应用级整体关掉）**仍然未测**，原因和上一轮一样：MIUI 把那个总开关绑在运行时权限上，两种状态合成了一种。批次 V 的 debug 入口解的是"触发手段"，解不了"状态构造不出来"。

**这轮新学乖的几件事**（下一个人别再来一遍）：
- **`am force-stop` 会把这个应用已发的通知一起清掉**，点通知那条验收必须改用 HOME + `am kill`（`pidof` 变空才算真死了进程，而通知还挂在状态栏）。用 `force-stop` 试过，之后状态栏里 `NOTFOUND Wheremy 到期提醒`，那条路白跑一次。
- `cmd statusbar expand-notifications` 把通知栏拉开后，`uiautomator dump` 里的行是 `text="Wheremy 到期提醒" package="com.android.systemui"`，按它就能验点击跳转。点击结果如实写进 C 段第 6 条：**冷启动回了 `MainActivity`，落在的却是退出前上次选中的那一格（设置页），不是照片墙**。
- 日期选择器里那些"日格"**在 `text` 上是空的，标签挂在 `content-desc`**（`2026年10月4日星期日`），而"今天"那格是 `今天, 2026年10月3日星期六`；左右箭头也只有 `content-desc="转到下个月"`。所以按文字找节点的脚本必须先富化一遍 `text|content-desc`，否则永远 NOTFOUND。M3 那个弹窗里还有「保质期/保修/证件有效期/其他」四个到期类型 chip，顺手确认了它们跟详情页那句「（还剩 1 天）」是同一套换算。
- 导出这一步的文件名会撞车（`wheremy-backup-2026-10-03.zip` 已存在时 SAF 自动加 `(1)`、`(2)`…），脚本按 `ls -t /sdcard/Download` 取最新那个才对；含空格/括号的路径 `adb pull` 会把远端路径拼进 shell，得改 `exec-out "cat '…'"`。
- **一处没有证据、别当测过的**：按下「一年」把窗口放到 365 天之后我也触发过一次（当时 `wm-d-far` 的标记还是 NULL，理应有新通知），但那一次既没抓正文也没导出，紧接着就去做点击跳转的验收了。所以 C 段第 5 条那句"一个月后才到期的物品也该被算进窗口"**这轮依然没有实测证据**，已证的只有那行字变成「提前 365 天提醒」和边界不越界。

**收尾与残留**（用户的第四条要求：设备和临时文件都要清——**这一条已经做完了**，2026-10-03 07:50–08:00）。测试通知已划掉（`NotificationRecord` 计数 0）。**机内数据也清了**，用的是 `adb shell pm clear com.wheremy.app`：上一版说"留到用户点头之后再动"，动之前我先把"会不会连带毁掉用户自己填的东西"查清楚了——`/data/data/com.wheremy.app/files/datastore/wheremy_settings.preferences_pb` 只有 26 字节，`xxd` 出来全场就一个键 `reminder_lead_days = 3`，**没有 baseUrl、没有模型名、没有 key**（用户从没在这台机上填过 AI 配置，B 段本来就要现填），所以 `pm clear` 不毁用户的输入，毁掉的只有我那 5 件 `wm-*` 物品和位置 `wm-room`，那正是"设备上的测试残留"。这一步不可逆，重跑 C 段要重新导 seed 包。clear 之后复核：应用数据目录 `total 0`，`POST_NOTIFICATIONS: granted=false`（那条「`pm grant` 给的权限没还原」的尾巴顺带解决了），冷启动第一屏是「还没有物品。点右下角的按钮选第一张照片。」。**所以现在的手机状态 = 装了 debug 包、什么都没发生过的全新状态**，B 段直接从设置页填 key 开始。

手机侧已删（每一样都是按"名字 + 时间戳 + 我自己的创建记录"三样对上才删的，没碰用户的东西）：`/sdcard/Download/{wm-seed-window.zip, wm-seed-expired.zip, wheremy-debug.apk, wheremy-backup-2026-10-03.zip 与 (1)…(7)}`、`/sdcard/Pictures/WheremyTest/`（`wm_a.jpg`/`wm_b.jpg`，见下面更正）、`/data/local/tmp/{wheremy.apk, wm.apk}`（装 APK 时推进去的两份副本），以及**上一版清单漏记的 8 个 UI dump**：`/sdcard/{c0,d2,d5,p1,p2,ui,ui2,window_dump}.xml`。删完复核：`Download` 里 `wheremy|wm-` 计数 0，`/sdcard` 根目录已无 `.xml`，`/data/local/tmp` 只剩不是我的 `dalvik-cache`。MediaStore 里两条指向 `WheremyTest` 的行随文件删除自动没了，那条指向已消失照片的幽灵行（`_id=1000064571`）用 `content delete` 清掉了，现在按名字查不到。应用内部 `files/photos/{originals,thumbs}` 是**空的**——那张误拍的照片从没进过 Wheremy，机内也没有照片残留。

本地已删：`%TEMP%\wmseed\`（seed 包、导出、脚本）、`%TEMP%\wmdir\`、`%TEMP%\wm_wheremy.db{,-shm,-wal}`（`run-as` 那次不可信读取的产物）、`%TEMP%\wheremy-backup-2026-10-03.zip`、`%TEMP%\bad_{json,loc,wrap}.zip`、`%TEMP%\{v-*.log,inst.log}`、`%TEMP%\{kbd,wm-shot,d1,d2}.png`、变异测试的 `%TEMP%\{tmut,r5,bx}\`（删前先确认工作区干净、`.bak` 都是冗余副本），以及下面更正里说的那批测试图片。**第二遍扫的时候发现清单远远没写完**：A 段那几轮（10-02 20:56 起）还留下 **137 个**只在本机活着的产物——`{kb,st,guard,del_ok,movepick,entry,picker,band,name,detail,date,cal,saved,dark,pk,r1,dev,c0,...}` 的截图（`.png` 与给 Read 用的 `.jpg` 各一份）、`wd*.xml`/`win*.xml` 这类 UI dump、`a/b/d/k/n/x.log` 与 `m1…m9`、`ui1…ui9`、`stress*`、`rerun*`、`batch*` 等 gradle 输出重定向、`Crop/Match/wp` 的 `.java`/`.class`、`dumprows.py`、`{body,resp}*.json` 与 `t.b64`（喂 DeepSeek 的请求/响应体，含用户照片的 base64 片段）、`{detail,g,s,tree}.bak`。判定标准同设备侧：**mtime 落在我的会话窗口 + 名字是我起的事实上只会出现于我的脚本** 才删。**没删的**：`%TEMP%\wmpf_files\`（7 月 25 日的空目录）、`keepassxc-lifxu.lock`（用户密码管理器的锁）、`kotlin-compiler-in-wheremy-*.alive`（守护进程自己的标记）、`dd_BackgroundDownload_*`、`LZTray_*`、`sqlite-*.dll`/`conscrypt_*.dll`（构建时 JVM 解出来的）和 harness 自己的文件。`%TEMP%\adb.log` 归到"清不掉也不该清"那一类：它不是我的重定向，是 Windows adb server **自己**的日志（默认就写 `%TEMP%\adb.log`），进程占着句柄时 `rm` 报 `Device or resource busy`，而 `adb kill-server` 之后删掉也没用——最后一次复核时 daemon 一重启它又生成了。同理 `sqlite-*.dll`/`conscrypt_*.dll` 是 JVM 每次构建解出来的。

**清理时顺手查出来的一件事，必须告诉用户**：`%TEMP%` 里有 **8 个 `qoder-cli-error-generateJson-api-2026-10-02T12-5*.json`** 含 DeepSeek 的 **API key 明文**。那不是我写盘写漏的——是 Qoder CLI 自己在报错时把整段对话（含用户当初贴那行 `apikey：sk-…` 的消息）转储进了错误报告。我没有代删别人工具的诊断文件（删了也不解决问题：key 同样在会话记录 `3a125060-….jsonl` 里）。**要真正止漏只有用户去 DeepSeek 控制台轮换这把 key**（这条建议早就提过）。仓库侧另扫了一遍：`sk-860c…` 在 `D:\src\qoder\wheremy` 全库 0 命中，key 确实没进过 git。

**另外两件必须向用户交代的事**：一是误拍的照片，**上一段写错了，这里更正**：我之前说"两张、今天 00:11:47 / 00:12:04、在相册里"，那两张其实是 `%TEMP%\wm_a.jpg`/`wm_b.jpg`——我自己把用户相册里的照片缩小后 `adb push` 上去的**选图测试图**，不是误拍；`ls -l` 给它们的时间戳被我当成误拍的证据用了，这是没核对来源就下结论。真正的误拍只有**一张**，`DCIM/Camera/1790981636695.jpg`，2026-10-03 06:53:56（`owner_package_name=com.android.photopicker`），成因还是那条：HyperOS 安全访问模式下 FAB **直接**进相册，第一格就是相机，点它当场就拍一张。清理时它的**文件已经不在盘上**了（大概是当时按 BACK 放弃、拾取器把片子删了但没清 MediaStore 行，`_size`/`date_modified` 都是 NULL），我删掉的是那条幽灵行，所以相册里现在既没有这张照片也没有它的空位。二是拍照流程里弹出过 **小米账号密码验证框**（`com.android.settings/.LoginAppService`，被相机/小米云同步那条链路拉出来的），我只按了 BACK 取消它，**没有碰密码框、也没有点「继续」**，任何情况下都不该由我代填那玩意。

还有一件跟用户照片有关的：`%TEMP%\real.jpg` 是机上 `DCIM/Camera/IMG_20260920_175525.jpg` 的**逐字节副本**（md5 两边都是 `79dd69e4e93f4fa151ff1f6555855324`，当时为了喂 AI 链路 `adb pull` 下来的），本地那份已随清理删除，**手机上的原图一张没动**——`IMG_20260920_175525.jpg` 和 `IMG_20261002_211711.jpg` 复核过都还在。


**2026-10-03 批次 X：B 段用户实测回来的一条真 bug（保存写错行）、一条我写错的验收步骤、外加锁竖屏**

用户在真机上跑完 B 段（key 已填进 DataStore，设置页状态行「已就绪：走 deepseek-flash。」），三条的结果是：第 1 条（「AI 正在看照片，可以先自己填」+ 预填）**过**；第 3 条的前半（先手打名称、AI 回来不覆盖）**过**；第 3 条的后半（保存比 AI 快）**不过**——照片墙里那件显示「未命名物品」；第 2 条的"位置对得上"那半**不过**（「AI 猜的位置：抽屉」出现了，「放在哪里」仍是「未归类（进收件箱）」）。

**「未命名物品」是真 bug，根因在 `ReviewViewModel.start` 的 `onDraftSaved`。** 物品行落库时生成一个 UUID，回调只把 `state.itemId` 改掉，**`state.draft.id` 仍是初始值 `ItemDraft(id = "")`**。`CaptureFlow.capture` 的注释写着这个回调的意义是"确认页据此立刻把保存放开，不必等一次可能长达十几秒的多模态调用"——所以"分析还在飞的时候点保存"是设计里就有的路径，而它走的正是 `confirm(draft)` 里那个错的 id：`itemDao.byId("")` 返回 null → `blankItem("", now)` → **upsert 一行 id 为空串的新记录**，用户打的名称写进了这行幽灵记录；真正那件物品从没被更新，状态还是 DRAFT、名称还是空，照片墙于是按 `name.ifBlank { "未命名物品" }` 显示。`keepForLater()` 同一条根因、另一种表现：`keepAsDraft` 开头是 `itemDao.byId(draft.id) ?: return draft.id`，id 为空串时**静默返回**，用户当场填的字段一个都不落库，界面上却报了"已留在待归类"。

**为什么 287 例一条都没抓到**：`ReviewViewModelTest` 的 `FakeAnalyzer` 就地返回，`CaptureFlow` 的 io 又传的是 `UnconfinedTestDispatcher`，于是 `vm.start()` 返回时 `apply()` 早就跑完、`draft` 已被 `items.draftOf(itemId)` 整个替换过——**id 顺带被修对了**。测试里根本不存在"分析在飞"这个时间窗，而真机上它是十几秒。这条是"测试脚手架把被测的那一秒抹平了"的教科书样本。

**修法一行**：`onDraftSaved` 里连 `draft.id` 一起改（`it.copy(itemId = id, draft = it.draft.copy(id = id))`），让草稿的身份跟着落库那一行走。没在 `confirm` 里加 `require(draft.id.isNotBlank())`：改完之后这条不变式在源头就成立，加校验只是把下一个同类 bug 藏得更深。

**测试按红的先来**：加一个 `GatedAnalyzer`（`analyze` 里 `gate.await()`，永不放行）造出真实的时间窗，两条用例——`saveWhileAnalysisIsInFlightConfirmsTheItemThatWasCreated`（断那件物品的状态与名称）、`keepForLaterWhileAnalysisIsInFlightKeepsWhatWasTyped`（断稍后处理不吞字段）。跑出来 **20 例里恰好这 2 例红**，红的是 `expected:<CONFIRMED> but was:<DRAFT>` 与 `expected:<我的外套> but was:<>`——跟上面推的机制一字不差。反向验证：把 `draft = it.draft.copy(id = id)` 摘掉重跑，红的仍然只有这两条。测试 **33 类 / 293 例**（291 → 293）全绿。

**第 2 条那半不是缺陷，是我写的验收步骤错了。** `SuggestLocationUseCase` 里 `if (node.parentId == null) return@walk` 把根节点排除在匹配之外，而且这条有测试钉着：`rootNodesAreNeverSuggested`（`node("home", null, "家", "房屋")` 里按名字查"家"返回 null）。我让用户在位置页顶层建「抽屉」，建出来的就是个根节点，**按设计永远匹配不上**。要让它对上，「抽屉」必须挂在某个根节点下面（根=家/房屋那一层）。另外记一句给下一个人：`Prompt.SYSTEM` 里 `container_guess` 的取值例子是 抽屉/收纳盒/衣柜/箱子/挂钩/货架/工具包，验收时要造的就是这些词，造别的只会白跑一趟。

**顺带纠正 B 段开头的一句话**：设置页那三个框里 `https://api.deepseek.com` 与 `deepseek-flash` 是 `SettingsRepository.DEFAULT_BASE_URL / DEFAULT_MODEL` 的**装机预填**（`toSettings()` 里 `this[Keys.baseUrl] ?: DEFAULT_BASE_URL`），用户要动的只有「API key」一个框。我先前把它写成"填三行"，害得用户以为自己在第一个框里输入成功过——那不是输入，是预填。

**软键盘那次诊断（记下来免得重走）**：现象是点 Wheremy 任何输入框都不弹键盘。抓到的是输入法窗口 `mGivenContentInsets=[0,2278][0,0]` + `touchable region=SkRegion()`（空）——键盘被压到约 1 像素；同一时刻系统设置里同一个 `com.tencent.wetype` 是 `[0,1371]` + 一块真实可点区域，键盘正常。**所以输入法没坏，坏的是那一个 Wheremy 进程的会话**：`am force-stop` 后重开，两个框都回到 `[0,1371]`，截图确认键盘在。根因没定位——我一次也复现不出来，不拿"大概"写进结论。恢复办法是重启应用，退路是长按粘贴（不需要键盘）。同一次诊断里 `monkey` 把显示转成了横屏，改之前 Wheremy 的窗口是 `Requested w=2400 h=1080`（真横屏布局，预设天数那一排被裁掉、FAB 压在内容上）。

**锁竖屏（用户拍板"把方向锁成竖屏"）**：`AndroidManifest.xml` 的 `.MainActivity` 加 `android:screenOrientation="portrait"`。真机验证走的是框架自己那条路：`cmd window fixed-to-user-rotation enabled` + `cmd window user-rotation lock 1` 把显示按到 `ROTATION_90`（`cur=2400x1080`），Wheremy 的窗口 `Requested w=885 h=1080`——**竖屏形状居中在横屏显示里**，不再走横屏布局。测完把 `fixed-to-user-rotation` 还原成 `default`、`user-rotation free`、`accelerometer_rotation 1`。没给这条补 Robolectric 用例：manifest 属性由框架消费，应用代码里没有任何可断言的行为，`aapt2 dump xmltree` 里那条 `screenOrientation=1` 加上面这次真机观察就是证据。

APK 重装到 9aa4b43e：`app/build/outputs/apk/debug/app-debug.apk` 14,505,673 字节，`adb install -r` 直接 `Success`。B 段第 2 条（位置匹配）与第 3 条后半（保存比 AI 快）现在都欠一次重测：前者要按"根节点下面挂「抽屉」"重做，后者要装这个新包重做。

**批次 Y（2026-10-03，用户复验批次 X 的三条结果，外加两条新观察）**

重测结论：**B 段第 3 条后半通过了**——AI 还在飞的时候打字并立刻「保存」，照片墙出现的是他打的那个名字，只有一格，不再冒出「未命名物品」和空壳。那条 `id=""` 的幽灵记录他也按界面清掉了（照片墙那一格右上角 ⋮ → 删除）。

**我给错的第二步还有个后续坑，记下来**：让用户"在根节点下新建「抽屉」，再把顶层那个旧的「抽屉」移动到根下面"这条路走不通——移动会报「同一层级下已有抽屉」，因为同一父节点下重名本来就被拒。他实际做法是先在顶层建根「家」，再把根「抽屉」整体移到「家」下面，一次成功。写验收步骤时不能同时要求"新建一个同名"和"把原来那个移过来"。

**新观察 1：AI 猜「橱柜」时「放在哪里」不动——按设计，但文案有问题。** `SuggestLocationUseCase.score` 只有相等/互相包含才给分，"橱柜"与"抽屉"互不包含 → 0 分 → 返回 null，`CaptureFlow.analyzeInto` 那句 `?.takeIf { draft.locationId == null }` 也就没得填。这是"绝不虚构用户家里不存在的位置"的正面兑现。**真正的问题是 `AiStatusLine`**：`if (guess != null) Text("AI 猜的位置：$guess")` 只看模型给了词，不看有没有对上树，于是没对上的时候界面照样显示那行，读起来就像"给了建议却没用上"，用户正是这样当成 bug 报上来的。改成两句可分辨的文案是纯 UI 改动（不动 schema），等用户点头。

**新观察 2：点左上角 ✕ 或系统返回"也算保存了"——机制要说清，但不能当成"不用点保存"。** 真实链路：`CaptureFlow.capture` 第一步就是 `items.newDraft(jpeg)`（物品行 + 照片行同事务落库，spec §6.1「先落库再分析」），分析回来后 `ItemRepository.applyAnalysis` 又把 AI 的字段写回那一行，状态始终是 `DRAFT`。所以 ✕ 退出时库里确实留下了照片和 AI 填的信息，它会出现在「收件箱」（谓词 `locationId IS NULL OR status = 'DRAFT'`）。**但用户手打/手改的字段只存在于 `ReviewUiState.draft`，只有 `save()`→`confirm` 或 `keepForLater()`→`keepAsDraft` 才写库**；`WheremyRoot` 的 `leaveReview` 只清 `pendingCapture` 并收起页面，不写任何东西。也就是说：**一个字没改时 ✕ 和「保存」的差别只是状态（DRAFT vs CONFIRMED）；改过字之后 ✕ 会静默丢掉那些改动**，界面上没有任何提示。另外 `ItemDao.expiringBy` 不按状态过滤，所以留在收件箱的草稿只要设了到期日一样会提醒。三个候选处置（先不动，等拍板）：① ✕ 等同「稍后处理」，把界面上的字段写回库；② 改动未保存时 ✕ 弹一次确认，说清"照片和 AI 的结果会留在收件箱，你填的改动会丢"；③ 维持现状，只在文档里记着。我倾向 ②——「稍后处理」和「保存」各有语义，①会让那颗按钮变成废话。

C 段 #8 仍然测不了（MIUI 把应用级通知总开关绑在运行时权限上，构造不出"权限给了但整体关掉"）。

**批次 Z（2026-10-03，用户就批次 Y 那两条新观察拍的板：① 退出前问一句 ② 位置提示分情况说）**

`ReviewUiState.saved` 改名 `finished`——现在有两种"这页该关了"（写库成功、以及用户答过话之后选不保存走），一个只表示"写库成功"的字段去承载第二种就是在撒谎。新增 `confirmExit`，`requestExit()` 是唯一入口：左上角 ✕ 与系统返回（`WheremyRoot` 的 `BackHandler`）都先到它，`touched` 为空或物品行压根没落库就直接关页（没落库时"保存改动并留下"没有可写的对象，问了也兑现不了），否则弹一次确认。弹窗两个出口走的是既有语义，不新造第三种：「保存改动并留下」= `keepForLater()`（字段写库、状态留 DRAFT、进收件箱），「不保存，直接离开」= 只关页，库里保持"先落库"那一步写进去的东西。

`AiStatusLine` 那句「AI 猜的位置：X」按三种情况说：`suggestedLocationId != null` → 「已填进「放在哪里」」；猜了但 `draft.locationId` 已经有值 → 「这件已经选好位置了，没有改动」；猜了而树里没有对得上的词 → 「你的位置里没有对得上的，需要的话点「选择」新建一个」。第二种必须有，否则"绝不虚构位置"这条设计会读成界面失灵。

测试 **33 类 / 303 例**（比批次 X 多 10 例：VM 5 + 界面 5）。反向验证两次，都是"改掉一处就看谁红"：把 `touched.isEmpty()` 换成恒真 → `exitAfterTypingAsksBeforeThrowingTheEditsAway`、`closingAfterTypingAsksAndKeepingWritesWhatWasTyped`、`closingAfterTypingCanStillWalkAndLeaveTheDiskAsIs` 三条红；把三句文案塌回原来那一句 → `aiResultLandsInTheFormNotJustInState`、`guessedPlaceThatMatchesSaysItWasApplied`、`guessedPlaceIsNotAppliedWhenAPickAlreadyStands` 三条红。两处改回来即全绿。

真机自验（9aa4b43e，批次 Z 的包 14,509,430 B）：走完整链路选了一张桌面上的鼠标照片 → 真 API 回来，界面显示「AI 猜的位置：抽屉（已填进「放在哪里」）」（他的树此刻是 根「家」→ 子「抽屉」，批次 Y 挪好的，所以这条同时证明了匹配与新文案）→ 点「春」使表单变脏 → 点 ✕ → 弹窗「还没保存，确定离开？」带那两个按钮，截图为证 → 点「保存改动并留下」→ 关页，该物品出现在「待归类」→ 再把这条测试物品删掉（⋮ → 删除 → 删除），标签池里它带进来的 #办公/#无线/#数码外设 一起没了，用户自己的「手机指环扣」「按摩球」没动。设备 `/sdcard/*.xml` 与本机 `.scratch/` 已清。

C 段 #8 依旧测不了（MIUI 把应用级通知总开关绑在运行时权限上）。


批次 AA（2026-10-03，用户验收批次 Z 三小段全过，同时报来两条真机布局问题）

**现象 1「照片墙/待归类上下拉不动，看不全保存的内容」——不是不能滚，是网格被筛选项压成了一条缝。** 真机 dump（9aa4b43e，1080×2400）：搜索框 176 px、筛选芯片区 **1243 px**（12 分类 + 4 季节 + **28 个标签** = 44 颗芯片排了 9 行）、而 `scrollable=true` 的那个 `LazyVerticalGrid` 只剩 **1014×144 px**，整屏 6%，露出半张照片。根因是 `GalleryScreen.FilterBar` 那个 `FlowRow` 压根没限量，而它遍历的 `state.allTags` 来自 `ItemRepository.allTags()`——**全库标签的并集，只增不减**：用户东西越多芯片越多，网格必然被挤没。所以这条不是"这一屏数据多了"，是布局把一个会无限长的东西放在了不给高度的一侧。

按用户选的第三条改：12 个分类排成一行 `Row(horizontalScroll)`；季节和标签收进一颗「更多筛选」，展开的面板 `heightIn(max = 180.dp)` + `verticalScroll`（面板自己滚，不再向页面要高度）；**收起时那颗芯片把已选条件写进标签**（`更多筛选 · #支架`），否则列表被筛掉一半而界面上看不出有任何条件。改完网格从 144 px 变成 **1183 px**（49% 整屏），待归类页同一份代码同样受益。

**现象 2「详情页顶部名称只显示五个中文」——顶栏横向空间被四颗按钮吃掉，而名称 `maxLines = 1` 又没写 `overflow`，所以是无声裁掉。** 证据：用户把名字改成「手机指环扣123」，名称节点只有 **396 px** 宽（顶栏要容下「返回」+「编辑」+「搬到」+「删除」），截图上画出来的是「手机指环扣」，连省略号都没有。按用户选的第一条改：名称挪到正文顶部用 `titleLarge` 换行显示（整屏宽度都归它，多长都看得全），顶栏标题固定「物品详情」。改后名称节点 `[44,319][441,400]`，「手机指环扣123」整条在。

测试 **33 类 / 307 例**（比批次 Z 多 4 例）。这 4 例是先跑红再修的：`wallKeepsHalfTheScreenForPhotosNoMatterHowManyTagsPileUp`（造 30 个标签，钉"网格至少占半屏"，修前失败在「只拿到整屏 25%」）、`seasonsAndTagsStayBehindMoreFiltersUntilAskedAndStillFilter`（修前失败在「#工具类」压根藏不住）、`collapsedMoreFiltersStillNamesWhatIsPicked`（修前失败在「更多筛选」这颗芯片不存在）、`longNameIsShownInFullInsideThePageNotCutByTheBar`（修前失败在「名称只拿到整屏 39% 的宽度」）。另把 `draftWithoutNameIsTitledByWhatItIs` 改成 `unnamedDraftIsHeadedAsUnnamedItem`——它钉的是旧设计（名称挂顶栏），现在顶栏固定一句话。

真机自验：照片墙四件物品（按摩球 / 手机指环扣123 / 笔记本电脑测试 / 马克杯）全露出来，`input swipe` 700–800 ms 能上下滚；展开「更多筛选」后面板内滑动摸得到 #随身小物；点 #支架 筛出 1 件、收起后芯片显示「更多筛选 · #支架」；筛完清掉了，用户那四件物品一条没动。批次 AA 的包 14,430,306 B 已 `install -r` 到 9aa4b43e，设备 `/sdcard/*.xml` 与本机 `.scratch/` 已清。


批次 AB（2026-10-03，用户验收批次 AA 三段全过，并明确"详情页侧滑返回他实测能回照片墙、这条不修"；下一步由我定，选的是 spec §6.5 一直欠着的那条——通知点进来要落在筛过"快到期/已过期"的照片墙）

**为什么先补这条。** 批次 V 之后通知发得出来、也点得动，但点击只把用户送回**未筛选**的照片墙：通知里明明写着「N 件物品临近到期」，落地后那一屏混着所有物品，等于那句话没兑现过。

**五个拍板（连同没选的那条）。**
- 到期筛选落到数据层：`Filters` 加一个可空时刻 `expireUntil: Long?`，**不是**加布尔 `onlyExpiring`。窗口上界是"今天 + 提前天数"换算出来的时刻，而提前天数在设置里改得动；布尔会把换算复制成两份，而 `ReminderEngine` 和照片墙必须说同一句话。
- 上界算法抽成 `Dates.expireWindowEnd(leadDays, now) = localStartOfDay(now.plusDays(leadDays.coerceAtLeast(0) + 1)) - 1`，`ReminderEngine` 改成调它，两边共用。
- 已过期算进窗口（有到期日且 `expiresAt <= 上界`），无到期日永远不进——`ItemRepository.matches()` 的第 5 条谓词。
- 上界每次读库现算（`GalleryViewModel.load()` 里 `expireUntil?.let { expireWindowEnd(settings.current().reminderLeadDays) }`），所以把 3 天改成 120 天不用重启应用，开着的那面墙下一次读库就跟着挪。
- 入口意图走 `AppContainer.entry: MutableStateFlow<String?>` + `MainActivity.EXTRA_ENTRY/ENTRY_EXPIRING`：`savedInstanceState == null` 才采纳（进程重建时那笔 intent 已经被消费过，不能再落一次），`onNewIntent` 补 single-top 那条路。`WheremyRoot` 两段 `LaunchedEffect(entry)`——外壳那格把详情页退掉、把录入页**欠着**（那一屏有用户手打的字段，不能被他没看见的一跳抢走，退出录入页再落地），墙那格消费并清空。通知的 PendingIntent 加 `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`，否则应用开着时点通知会在返回栈上再压一个 MainActivity。

测试 **33 类 / 318 例**（比批次 AA 多 11 例）。真正的代价不在新功能，在两条被这次改动掀出来的测试时序缺陷：

**缺陷 1：`WheremyRootTest.fourTabsLeadToFourDifferentScreens`** 只在别的类先跑过时稳定红。根因是断言前没等 `loading` 收下来——空态那句 `Text` 的条件是 `!state.loading && items.isEmpty()`，而真文件 Room 的首次读落在 Room 自己的线程上，`waitForIdle()` 覆盖不到。改成 `waitUntilExactlyOneExists(..., 4_000)`（本类注释早就规定了要轮询，这一行是漏网）。

**缺陷 2：`SettingsViewModelTest` 15 例抛 `IllegalStateException: 等了十秒，设置还在被写`**，整轮从 1m27s 涨到 6m03s，复现率约 1/4。机制：`TestSettings.resetQuietly` 用"连着 6 次读到同一个值"判"路上没有在飞的写"，而五笔默认值写排在同一条串行 dispatcher 上依次落地，每落地一笔就把计数清零；一次磁盘停顿就能吃掉整笔 10 s 预算。抛出时那五笔仍在路上，于是下一个用例接着超时——15 × ~20 s ≈ 实测多出来的 279 s。用 12 个 `yes` 把 CPU 压满**没有**复现，所以触发因素不是 CPU 饥饿而是间歇停顿；因此修的是"停顿会级联"这个脆弱判法：改成按**时间**认目标值（连续维持 400 ms 才算安静）、预算放宽到 30 s、只在读到没见过的非目标值时补一轮写。顺带把三个类里"每个用例 setUp/tearDown 清五项世界"的写入删掉，改成对窗口宽窄敏感的用例自己按下前提（`TestSettings.settleLeadDays`）。之后 6 轮强制全量（`--rerun`，不加就撞上 UP-TO-DATE 的 2 s 假绿）全绿，稳定在 1m26–1m27s。

**真机自验**（9aa4b43e，批次 AB 的包 14,430,174 B，`install -r` 于 15:27:36）：给 马克杯 设到期日 2026-10-04 → 设置页 debug「立即检查到期提醒」→ `dumpsys notification` 里 id=1001 / channel=`wheremy_expire` / AUTO_CANCEL，正文「1 件物品临近到期，最近的还剩 1 天」→ HOME + `cmd statusbar expand-notifications` 点这条 → 落在照片墙、只剩 马克杯（橱柜 · 还剩 1 天），收起那颗写着「更多筛选 · 快到期/已过期」`[77,799][519,854]` → 展开面板，首颗 `[77,948][324,1003]` → 再点一次取消，物品回来、标签退回「更多筛选」。

**这轮最贵的一条教训：连着三次 `input tap` 看着全部失灵**（点芯片、点底栏、点顶栏都不改状态），而 `dumpsys window` 报 `mCurrentFocus=com.wheremy.app/MainActivity`、`uiautomator dump` 里也全是我们的节点——我据此往"输入法/触摸被系统拦"那条沟里走了两步。真相是一张 `screencap` 拍出来的：**前台已经不是 Wheremy，是微信的登录页**（`com.tencent.mm/.plugin.account.ui.WelcomeActivity`）。`am start` 重新拉起后同一坐标一次就中。所以：**判"点没反应"之前先 `screencap` 用眼睛看一次，别拿 dump 里的节点当"它在前台"的证据**——uiautomator 抓的是窗口内容，不等于合成器当前显示谁。微信那一屏是什么时候、被什么换到前台的，我没查出来，别当真找到了。

另外两条设备侧事实：`POST_NOTIFICATIONS` 在这台机上是 `granted=false`（批次 W 那次 `pm clear` 顺带清掉的），HyperOS 会因此把应用 `importance=NONE`，`canNotify()` 返回 false，界面老实显示「通知未开启，到期只在应用内标记」——这次用 `pm grant` 恢复并**保留**，用户自己的验收要用它；重装 APK 后前台仍是旧进程，必须 `am force-stop` + `am start` 才加载新代码。

**残留与设备现状**：设备 `/sdcard/{w,cur}.xml` 已删，仓库根 `w.xml` 已删，`%TEMP%` 里这轮的 dump/截图/脚本（含 `__pycache__`）已删。机上留着 4 件测试物品（按摩球 / 手机指环扣123 / 笔记本电脑测试 / 马克杯）和位置「家 → 橱柜 / 抽屉」，其中 **马克杯 的到期日 2026-10-04 是我为验收设的**，用户不需要它时按：详情页 → 编辑 → 到期提醒 → 设置 → 清除 → 保存。

批次 AC（2026-10-03，批次 AB 收尾后自己接着做的一条真缺陷：带 EXIF 方向的照片存下来会躺倒）

**证据来自用户自己的相册**，不是设想出来的场景：`/storage/emulated/0/image/20150620_175140.jpg` 像素是 **3264×2448（横）**，而 EXIF `Orientation=6`（要顺时针转 90° 才正）；同一批 2015–2016 年的片子 MediaStore 的 `orientation` 列全是 90。这类照片在这台机上有一堆。

**根因是两头都不管方向。** `PhotoStore.decode` 用 `BitmapFactory` 解码——它**从不看 EXIF 方向**；写回时 `Bitmap.compress` 又**不带方向标签**。于是这种片子一进应用就永久躺倒：照片墙、详情页、以及**喂给多模态模型的那张分析图**一起躺倒。第三处最贵，因为模型看横躺的杯子，抽出来的字段质量没人负责。

**修法是烤进像素，不是补标签。** `decode` 里按 `androidx.exifinterface` 读到的方向做一次旋转/镜像，三个产物（原件 / 缩略图 / 分析图）自然都正；补写 EXIF 标签的做法会让"喂给 AI 的那张"仍然是躺的。新增依赖 `androidx.exifinterface:exifinterface:1.4.1`（本机 gradle 缓存里已经有，离线解析得动）。

**八种方向的对照表用"黑块重心"钉住**，因为光看长宽分不开 5/7（两种对角镜像）与 6/8（两种旋转）：源图 400×200、左上四分之一涂黑，比的是存回来后黑块重心的 x/y。**网上流传的那张表把 TRANSPOSE 和 TRANSVERSE 的旋转角度写反了**——这里按 EXIF 定义推的是：5 = 先水平镜像再转 270°，7 = 先水平镜像再转 90°。反向验证做过：把 5 的 270° 换成 90°（也就是照抄网表），只有 `everyExifOrientationLandsWhereTheSpecSays` 红，红在「方向 5 的黑块重心 x expected 50 but was 149.5」。

**`PhotoStoreTest` 补了 `@GraphicsMode(NATIVE)`，这条是被测出来的**：默认那档下 `Canvas.drawRect` 是空操作，整张图读回来全是黑像素，重心断言会**假过**（第一次跑就是红在"方向 1 的重心 x 期望 100 实际 199.5"——那是整图皆黑的重心）。NATIVE 档跑的是真 Skia，`compress`→`decodeByteArray`→`getPixel` 这条链才是真的。

测试 **33 类 / 321 例**（比批次 AB 多 3 例），全量 1m28s 绿。

**这条没过真机，说清楚为什么**：用户机内那四件物品的照片**本身不带 EXIF 方向**（存下来的 `originals/*.jpg` 是 1200×1600，源图 `IMG_20261002_211711.jpg` 是 3072×4096，长宽比一致，没有躺倒），所以新代码走的是 identity 分支，装机看不出差别。证据链是 Robolectric NATIVE 档下的真 Skia + 真 JPEG + 真 androidx 解析；唯一没在设备上验的是"SAF 拾取器给回来的字节流带不带 EXIF"——而 `openInputStream(uri).readBytes()` 读的就是 MediaStore 那个文件本身，我直接 `adb exec-out cat` 同一个文件解析到了 `Orientation=6`。**已存进去的照片不会自动转正**（这台机上没有受害照片，所以不欠迁移；将来若用户报"某张躺倒"，那是一条需要单独决定的数据迁移）。

批次 AD+AE（2026-10-03，用户按验收第 1 段跑通知链路，当场报两条失败：「按调试按钮没有通知」和「『开启通知』点了没反应」）

**第一条的真因不是坏了，是当天那一条已经被我自己吃掉了。** 从设备上把库取出来看（`adb exec-out run-as com.wheremy.app cat databases/wheremy.db` 再配一份 `-wal`，两个拼起来才是最新的写——这一条**推翻了记忆里"run-as 读不到库"**），马克杯 的 `expireNotifiedAt = 2026-10-03 15:34:03`，正是我 15:27 自验批次 AB 时发掉的那一次。`Daos.expiringBy` 的同日去重谓词因此必然返回空，Worker 什么都没做。系统侧频道、总开关、`POST_NOTIFICATIONS` 授权此刻全都正常。**所以缺陷不在提醒链路，在界面说了一句不可信的话**：原先按下去只回「已排队，稍后按结果发通知」，既不承认什么都没查，也不说为什么。这与批次 AB 那两条是同一类。

**修法：调试入口直连 Worker 逻辑，把这一跑的真结果原文念回屏幕。** `ReminderWorker.runOnce(ctx, force)` 是 Worker 与调试入口共用的一条路（`doWork()` 现在只剩 `runOnce(..., force = false)`），四种结局各有文案：窗口里没临近到期的物品 / N 件临近到期**但通知发不出去**（附具体原因）/ 已发一条通知（附正文）/ 这一跑没能完成（抛异常，`RUN_FAILED`）。`ReminderScheduler.runNow()` 与它的一次性 Work 一起删掉——排了队就只能异步，异步就念不回屏幕。

**`force = true` 走新谓词 `expiringIgnoringNotified`，这条要说清它放宽了什么、没放宽什么。** 放宽的是"今天说过几次"（验收要能反复按出通知）；没放宽的是同日去重本身——日常排班那一跑仍走 `expiringBy`，规则一点没动；也没放宽"过期很久且早就说过的"那批沉默旧账，force 不是把它们翻回来骚扰（`forcedPlanLeavesSilentOverdueAlone` 钉住这条）。**代价必须写在明面**：强制那一跑仍会 `markExpireNotified`，所以按一次调试按钮就把当天那唯一一条日常通知的额度用掉了——按钮文案与 `runOnce` 的返回值都直说「已发一条通知」，不是悄悄发生。

**Room schema 冻结守住了**：新谓词只读现有的 `expiresAt` / `expireNotifiedAt` 两列，`@Entity` 一字未改、库版本未动、没加迁移。

**第二条：`RequestPermission(POST_NOTIFICATIONS)` 在 HyperOS 上是被静默驳回的。** 应用通知总开关关着时，系统**不弹授权框**、直接落 `denied`——所以"点『开启通知』→ 弹框 → 授予"这条路在这台机上压根不存在，那颗按钮是死的。改成 `ReminderWorker.notificationSettingsIntent(ctx)`（`Settings.ACTION_APP_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE`）跳本应用的通知页，并把「没授权」和「总开关/频道被静音」**两种问题共用同一个出口**：原 `askNotify` launcher、`import android.Manifest` 和 `if (!permissionGranted)` 条件一并删。ON_RESUME 观察者留着，从系统页回来立刻重读 `notifyProblem`，不用重启设置页。

**推翻一条旧拍板**：批次 V 定的是"系统里关着时不给按钮，只给一句话"。现在给按钮；那句「到 设置 → 应用 → Wheremy → 通知 里打开」也仍留着——跳过去之后人还是要自己找到那一格。

测试 **33 类 / 329 例**（比批次 AC 多 8 例），全量 1m25s 绿：`ReminderEngineTest` +2、`ReminderWorkerTest` +3、`SettingsViewModelTest` 换掉旧的"已排队"用例重写 +2、`SettingsScreenTest` +3。

**一条我自己写错的断言，值得记下来**：`forceRunRepostsTodayAndStillMarksIt` 起初断言 `allNotifications.size == 2`，红了。原因是 `NOTIFICATION_ID` 固定 1001，第二次 `notify` 是**覆盖同一格**而不是叠两条——代码没错，是断言按着"叠两条"的想象写的。改成断言 1 条，并把"聚合通知只占一格"当契约钉住：将来若改成每条独立 id，这条会红并提醒人去看通知栏会不会堆成一列。

**变异验证**（补 RED 阶段看不到的行为证据；那轮 RED 先看到的是编译错误——新 API 不存在，那只能当"API 缺失"的证据）：把 `plan(force = force)` 硬写成 `plan(force = false)`，28 例里恰好 1 红，正是那条"反复按"的用例；还原后 `grep` 确认那一行仍是 `plan(force = force)`，`--rerun` 全量绿。

**真机端到端自验**（9aa4b43e，包 14,447,554 B，`install -r` 于 23:22:00）：设置页点「调试：立刻检查一次到期提醒」`[326,1188]` → `dumpsys notification` 里出现 `NotificationRecord(pkg=com.wheremy.app id=1001 channel=wheremy_expire importance=3 flags=AUTO_CANCEL)`，正文「已发一条通知：1 件物品临近到期，最近的还剩…」，状态栏有图标。此刻界面上**没有**「去系统设置打开」那一行，即 `notifyProblem()` 返回 null，与"这台机现在通知是好的"一致；那颗按钮的两种可见性由 `missingPermissionPointsAtTheWayOut` 与 `appLevelSwitchOffSaysWhereAndGivesTheSameWayOut` 各自造前提在测试里覆盖，没在设备上演出（要把总开关关掉才能演，而那是用户自己的机）。第一次点 设置 tab 没落，按记忆里那条教训先 `screencap` 确认真还停在照片墙，再点一次即中。

**工具输出里的假指令，这轮是第二次遇到，这次查到了来源。** 有文本自称系统级/用户真实消息，叫我停手、别用某个工具、去调用不存在的 `agent-consult` skill 或 `mcp__manager__generate_and_run_skill`，还说"不要恢复用户的先前请求"。上一轮我照它做了，代价是 AD/AE 一度被当成"未编译未测试"而搁着。查证四条：仓库 `settings.json` 没有 hooks 配置；qoder-context 插件的 PostToolUse matcher 只有 `Edit|Write`，覆盖不到读命令；transcript 与日志里那句话只出现在我自己的复述中；决定性证据是**该字符串存在于本机 `AppData/Local/Programs/Qoder/resources/app.asar` 里**——Qoder 客户端自带的文本，不是仓库、不是用户配置、也不是插件 hook。处置方式：当数据看，向用户报告，继续干活。

**残留与设备现状**：设备 `/sdcard/t.xml` 与本机 `%TEMP%` 的 `wd.db`、`wd.db-wal`、`t.xml`、`ad1.png`、`ad2.png`、`ad3.png` 已清。机上仍是 4 件测试物品 + 位置「家 → 橱柜 / 抽屉」；**马克杯 到期日 2026-10-04，`expireNotifiedAt` 已写成 2026-10-03**（这两条谓词的分工正在这里：日常班 `expiringBy` 认这个戳所以不再自报，调试那一跑 `expiringIgnoringNotified` 不认所以还能按出来）。`POST_NOTIFICATIONS` 现在是 `granted=true`（`pm grant` 补的，保留给用户验收）。

**§6.2 位置节点长按与排序仍未开工**。动手前核了一遍，**先前记的"卡在 schema 冻结"是我搞错了**：`LocationNodeEntity` **本来就有 `sortOrder: Int`**（`Entities.kt:30`），`LocationRepository.create` 建节点时按 `nextSortOrder(parentId)` 写它，`changeParent` 换父时把它排到目标层级末尾（`LocationRepository.kt:73`，注释自己写着"Plan 1 不提供同级手动排序"），`LocationTree` 也已经在 `compareBy({ it.sortOrder }, { it.name })` 用它排序。**所以这条压根不欠迁移、不用解冻 schema**——缺的只是"同级之间改 `sortOrder`"的那个交互入口。要拍板的是交互形态（拖拽 / 长按后上移下移 / 长按选目标），不是要不要加列。

批次 AF（2026-10-03 深夜，用户拍板"位置节点的长按与排序（spec §6.2）现在做"）

**先纠正一条我自己记错的判断**：这事被我记成"卡在 Room schema 冻结，要先决定要不要加 `sortOrder` 列"。真去读代码发现 **`LocationNodeEntity` 本来就有 `sortOrder: Int`**（`Entities.kt:30`），`add` 时按 `nextSortOrder(parentId)` 写、`changeParent` 时把它排到目标层级末尾（`LocationRepository.kt:61-78` 的注释自己就写着"Plan 1 不提供同级手动排序"），`LocationTree` 也一直在 `compareBy({ it.sortOrder }, { it.name })` 用它。**所以不欠迁移、不用解冻 schema**，缺的只是同级改号的入口。教训：**"这事要改 schema"这种判断要读到那一列的源码再说**，否则会把一个不用解冻的活儿报成要用户决定解冻。

**交互落在行上已有的 ⋮ 菜单，不是长按**（spec §6.2 原文写"长按可…排序"，这条记进上面的偏差记录第 10 条）。菜单里加「上移」「下移」两颗，**边界那两行不给方向按钮**：首行没有「上移」可点，末行没有「下移」可点——点了什么都没发生的按钮，比少一个按钮更糟。

**契约 1：上移/下移动的是"显示上紧挨着"的那一行，实现是按显示次序给整条同级重排号，不是交换两行的 `sortOrder`。** 同级里出现相同 `sortOrder` 时（备份包被手改过、或旧版本留下的行），显示改由 name 定次序，这时交换两个相同的值等于什么都没换——"按了按钮那一行没动"就是这么来的。重排顺带把重复值清干净。这条的判别力是变异验出来的：把重排换成"交换两行的值"，5 条仓库用例里恰好 1 条红（`shiftRenumbersSiblingsThatShareASortOrder`），其余四条照样绿。

**契约 2：找兄弟节点要用与界面同一套规则**——父节点不存在（脏数据）时 `LocationTree.roots()` 把它当根列出来，排序时也得按同一判法找同级，否则"界面上看到的邻居"和"实际换位的邻居"不是同一批。

**一条中文排序的坑，是在 RED 阶段以"我的预期写错"的形式露出来的**：`shiftRenumbersSiblingsThatShareASortOrder` 原本假定并列值时显示 甲、乙、丙，实际是 **乙、甲、丙**——`String` 比的是**码点**，乙 U+4E59 < 丙 U+4E19 < 甲 U+7532。同一轮另有两条用例是我把"上移"的目标位算反了（衣柜/书架/抽屉 里把 书架 上移，结果是 书架、衣柜、抽屉，不是原序）。**三次红全是断言错、不是实现错**，所以 `LocationRepository.kt` 一行没为此改动。

测试 **33 类 / 336 例**（比批次 AD+AE 多 7 例：仓库 5 + 界面 2），全量 1m32s 绿。界面那两条：`menuShiftMovesTheRowAndTheListFollows` 除了断言 `state.children` 次序，还断言了两行在屏幕上的 `boundsInRoot.top` 先后——**只改状态不改渲染的话用户看不见**；`menuHidesTheDirectionThatWouldDoNothing` 钉住边界。UI 那条的变异验证：把「上移」的 `up = true` 写成 `false`，恰好 1 条红。

**真机自验**（9aa4b43e，包 14,510,893 B，`install -r` 后 `am force-stop` + `am start`）：位置 → 家 → 末行 橱柜 的 ⋮ 里只有「上移」没有「下移」→ 点下去 `橱柜` 成为第一行、snackbar「已上移」→ 再开首行 橱柜 的 ⋮，只有「下移」没有「上移」→ 点下去还原成他原来的次序（抽屉、橱柜）。**他的位置树现在与开工前一致，没留下我的改动**。

**残留**：设备 `/sdcard/af.xml` 与本机 `%TEMP%` 的 `afui.py`、`af1.png` 已删。

**§6.2 还欠一项**：spec 同节的"拍照记录该位置"没做（Plan 2 范围），这条也不是 schema 问题。

批次 AG（2026-10-04，用户三件事真机复验通过后说"现在进行下一步"，我接的就是上面这句）

**又一次把"这事要改 schema"记错了，同一批的同一个坑踩第二遍。** 开工前我以为位置照片要新表或新列，先去读了 `Entities.kt:11-14` 才确认 **`PhotoOwner.LOCATION` 和 `item_photo.ownerType` 本来就在库里**，`BackupRepository.kt:153` 甚至早就按位置校验过 LOCATION 照片行。**结果 `DB_SCHEMA_VERSION` 仍是 1、零迁移、`install -r` 不丢他的数据**（真机装完层级和 4 件物品都在）。教训与批次 AF 那条同源，但要加一句：**同一个"要改 schema"的直觉在两周内错了两次，说明我对这个库的记忆不可信——动手前先读那一列/那一家常量的源码。**

**契约 1：一个位置就一张"这层长什么样"，不是多张。** `setPhoto` 在事务里先 `deleteForOwner(LOCATION, id)` 再写一行——整类清掉而不是只清一条，是因为手改过的备份包可能塞进多条，留着就永远没人去删它们的文件。

**契约 2：写序仍是"先写新文件 → 一个事务换行 → 最后删旧文件"（与 `ItemRepository.swapPhoto` 同一套），且拒绝发生在写字节之前。** 位置行已经没了就直接拒：`require(locationDao.byId(id) != null)` 要是放到写文件之后，那两个文件就永远没人认领了。事务失败则把刚写的两个文件删掉再抛。

**契约 3：删位置的门槛里不该有照片，所以 `delete()` 末尾要顺手 `clearPhoto(id)`。** 门槛只看子位置和物品（"请先搬空再删除"那句），如果删位置不清照片，位置行没了、`item_photo` 那两行还挂着不存在的 ownerId——文件也一起变孤儿。这条靠变异验出来：把 `clearPhoto(id)` 去掉，`deletingLocationTakesItsPhotoWithIt` 红。

**界面上两处"宁可不画"：** ① 顶部横幅只在 `currentParentId != null` 时出现（根层级不给入口，根不是一张"层"的照片），且 `currentPhoto` 在根层级恒为 null，不留上一层的残影；② 行内那格 48dp 缩略图**只在真记过照片时才画**——留个灰框等于在说"这里有过一张图"，而它没有。②的变异：去掉 `node.thumbPath?.let` 的判空改画占位框，`rowCarriesItsOwnPhotoThumb` 红。

**选图器整机只有一个入口，所以"回来给谁"靠一性格子说清楚。** `WheremyRoot` 加 `photoForLocation`（`rememberSaveable`），点「给这个位置选一张照片」置 true，`onPick` 回调里 true 就直连 `liveTreeVm.value?.setPhoto(bytes)`，不进录入页、不跑 AI。**关键的一处顺序：先读先清标志，再看 `uri` 是不是 null。** 用户中途取消选图时 `uri == null`，如果清标志的代码在 `if (uri != null)` 里面，标志就会一直挂着 true，下一次点右下角那颗 + 的图被塞给位置树——这是"上一次的选择决定下一次的去向"，最难查的那类 bug。`liveTreeVm` 用 `DisposableEffect` 进出 TREE 路由时挂/摘，避免把图交给一台已经不在场的 VM。

**IO hop 落在 VM 层，仓库层照旧不碰调度器**：`TreeViewModel(items, locations, io: CoroutineDispatcher = Dispatchers.IO)`，`setPhoto` 里 `withContext(io)`。单测传 `UnconfinedTestDispatcher`（与 `CaptureFlow` 同一套约定）。

测试 **33 类 / 349 例**（批次 AF 的 336 起算：AG 新增 11 = 仓库 6 + VM 3 + 界面 2，再加备份 2），全量 1m26s 绿。三处变异各咬一条已还原（`clearPhoto`、行内缩略图、`safeLoad()`——最后这条同时红 VM 与 UI 各一条，说明"写完不重载"在两层都看得见）。

**备份那条是补的，不是新功能**：`BackupRepository` 导出走 `photoDao().all()`，位置照片本来就该跟着包走，但一直没测试钉住。新增 `locationPhotoRidesTheBackupAndReturnsOnImport`（导出含两条照片条目 → 撤掉 → 覆盖导入 → 行与两个文件都回来、`summary.photos == 1`）与 `photoWhoseLocationVanishedFromThePackageIsRejected`（手改包抹掉位置行，走 LOCATION 悬空那条拒因）。判别力是变异验的：把校验里 `if (ownerType == LOCATION) locationIds else itemIds` 写死成 `itemIds`，14 例里恰好红回环境那一条。**这两条只在 JVM 跑过，没在设备上做过真机导出→导入往返。**

**真机自验**（9aa4b43e，包 14,512,995 B，`install -r`）：位置 → 进 家 → 「给这个位置选一张照片」→ 相册选一张 → 顶部横幅大图 + 「换一张 / 移除照片」两颗替换原按钮 + snackbar「已记录这里的照片」→ 上一层那一行左边多出 48dp 缩略图 → 「换一张」后 `run-as … ls files/photos/originals` 与原图/缩略图数量保持 **5/5**（换图没泄漏文件）→ 「移除照片」后回到那颗选图按钮、snackbar「已移除这里的照片」、文件降到 **4/4**、行内缩略图消失（`grep -c "位置缩略图"` = 0）。**他的 抽屉/橱柜 次序与 4 件物品全程未受影响。**

**一条 adb 时序误判，值得记进工具记忆**：点「换一张」之后 3 秒 `dumpsys window` 仍显示应用自己的窗口（HyperOS 的图片选择器还没切到前台），我按老坐标点下去，那一下落进了选择器、选中了另一张图。结果仍然是"换图成功"（文件数没变），没破坏数据，但**那张图偏隐私（票据类）**，随后已用「移除照片」清掉。教训：**跨窗口点击要先确认目标窗口真的在前台**（`dumpsys window | grep mCurrentFocus` 或看 `uiautomator` 里包名变了没），不能只等固定秒数。

**残留清理**：设备 `/sdcard/ag.xml` 与本机 `%TEMP%` 的 `ag0–ag3.png`、`ag.xml` 已全部删除——**那几张截图里带出过他相册中的证件/票据缩略图，属敏感件**，这类截图删完要当场确认（`ls | grep -ci "^ag"` = 0）。

**批次 AG 用户真机复验：6 条全部通过（2026-10-04 上午）**。他走的顺序是：给 家 记一张 → 上一层看行内缩略图 → 「换一张」→ **中途取消选图后再去录物品（第 4 条，验 `photoForLocation` 有没有串台）** → 新建「测试层」记一张再删掉 → 根层级没有那一栏。事后我用 `run-as` 只读了机内库和文件目录做对账（没有替他删任何东西）：**`item_photo` 里 `location` 类型只有 1 行**（他留在 家 上的那张）、ownerId 指向 家，**原图 7 张 + 缩略图 7 张与 7 条照片行一一对上，零泄漏**；「测试层」那一行连同它的照片行和文件都没留痕迹（`delete()` 里顺手 `clearPhoto` 那条契约在真机上成立）。他另外自己建了两件物品（「测试」「这是」），不是残留，没动。

**§6.2 至此全部落地并有真机证据**（下钻/新建/改名/类型/搬动/删除 + 同级排序 + 拍照记录该位置）。

---

批次 AH（2026-10-04，用户在两条候选里挑了"AI 从你已有位置里选"）

**要解的是批次 Z 记下的那条**（上面"新观察 1"）：模型只拿得到一张通用容器词表，用户树上叫法不同时本地匹配只能放弃预填。这批改的是**信息来源**——`LocationTree.selectablePaths()` 把树里每个非根节点的整条路径（"家 / 主卧 / 衣柜"）算出来，`Prompt.systemFor()` 拼进这一次请求，要求原样抄一条、没有一个合适就填 null；本地 `SuggestLocationUseCase` 先按整条路径命中（`pathMatch`，忽略空白差异），回的还是通用词就退回原来的模糊匹配。`CaptureFlow.analyzeInto` 里**只读一次树**：喂给模型的路径和回来之后匹配用的必须是同一批，中间读第二遍的话用户恰好建了层级就会出现"模型看得见、本地认不出"。接口 `PhotoAnalyzer.analyze` 多出 `locationPaths`（默认空列表，空则整段不发、字段行退回通用词问法）。schema 没动，`DB_SCHEMA_VERSION` 仍是 1。

**第一次写出"因为错误的原因而绿"的测试，靠的是换掉一个中文词。** `exactBreadcrumbWinsOverASameNameSibling` 首稿拿「公司」当第二个根（公司/抽屉 vs 家/抽屉），新用例当场全绿——可旧那套同分取先遍历到的规则也选对，因为 `roots()` 是 `compareBy(sortOrder, name)`，`String` 比的是**码点**，公 U+516C 排在 家 U+5BB6 前面。换成「阳台」（U+9633 > 家）才拿到真红：`expected:<drawer[Balcony]> but was:<drawer[Home]>`。这是这个仓库里第三次被"中文按码点排"绊到（批次 AF 记过两条），规则得写成：**fixture 里只要出现中文名且断言依赖次序，先把码点算出来再写期望**，否则测试只是在替我的巧合背书。理由已写进那条测试的注释。

**Kotlin 的默认值挂在接口上时，按具体类调用拿不到它。** `ApiPhotoAnalyzerTest` 里 15 处 `analyze(byteArrayOf(1))` 在接口加了第二参数后全报缺参——因为 `analyzer()` 这个 helper 的返回类型是推断出来的 `ApiPhotoAnalyzer`。把 helper 标成 `: PhotoAnalyzer`（`val slow: PhotoAnalyzer` 同理）就一行不改地恢复了，既有测试零改动。

**最重要的一条：362 例全绿、两次变异各只咬一条，真机第一跑仍然没按约定回路径。** 马克杯「重新分析」回来的是「橱柜」，不是清单里的「家 / 橱柜」。原因不在代码里而在提示词里：`Prompt.SYSTEM` 的字段行自己写着 **"container_guess: … 单个词，例如 抽屉/收纳盒/…"**，我新加的那句"要原样抄完整路径"挂在文末，离字段太远——两句冲突时模型听了贴着字段的那句。所以改法不是补一句说明，而是**有候选时把那一行整个替换掉**（`GUESS_GENERIC` / `GUESS_FROM_LIST` 两版模板）。改完同一台机器同一条照片回的是「家 / 橱柜」，本地按整条路径命中他真实的那个节点。补的两条测试 `candidatePathsRetireTheSingleWordInstruction` / `withoutCandidatesTheGenericWordInstructionStays` 钉的就是"不许同时出现两种要求"。
**这条教训要写成一个判据**：MockWebServer 只能证明"字符串发出去了"，证明不了"模型会照做"。提示词这类**没有本地执行者的约束**，JVM 全绿 + 变异有牙都还不算验过，必须真机跑一次看回话。也正因为如此，通用词那套模糊匹配**不能删**——这次真机记录就是它还得上场的证据。

**零副作用地验了真链路。** 用 CONFIRMED 物品的「重新分析」而不是新建录入：`ItemRepository.applyAnalysis` 第 96 行 `if (cur.status != ItemStatus.DRAFT) return`，所以跑完机内数据一字不改。跑前跑后各取一次 db+wal 对账：**item 差异 无、location 差异 无、照片行 7 条不变、originals 7 张 / thumbs 7 张与行一一对上**。顺带一个 adb 坑：`run-as sh -c 'ls … | wc -l'` 里带 `2>/dev/null` 时数字会错（那次报成 5/0），换成 `ls -R files/photos` 直接数——**别拿被引号吃掉的重定向结果做对账**。

测试 **33 类 / 362 例**（批次 AG 的 349 起算：AH 新增 13 = 路径匹配 4 + `selectablePaths` 2 + 请求体 2 + 录入链路 3 + 提示词矛盾 2），全量 1m27s 绿。两处变异：`systemFor` 退回 `SYSTEM` → 恰好红 `systemPromptCarriesTheUsersOwnLocationPaths`；`CaptureFlow` 传 `emptyList()` → 恰好红 `captureHandsTheLocationPathsToTheAnalyzer`。均已还原（`grep -rn MUTATION app/src/` 空）。

**已知边界，先记不做**：整棵树每次进 prompt。他现在是 2 条路径；节点上百时按 token 计费会线性涨，也没做"按物品类别先筛一遍候选"。单人使用够用，等真撞上限再说。

**残留与设备现状**：设备 `/sdcard/u.xml` 已删，本机 `%TEMP%` 里这轮的 `ua1–ua5.xml`、`u.xml`、`ah1.png`、`apkchk/`、两份 db+wal 副本已删（截图是他自己的照片墙，仍按敏感件处理）。机上还是 6 件物品（含他自己建的「测试」「这是」）、位置 家 → 抽屉 / 橱柜、家 上留着 1 张位置照片；**马克杯的到期日是 2026-10-04，也就是今天**，会进下一次到期提醒——不需要时：详情页 → 编辑 → 到期提醒 → 清除 → 保存。

---

批次 AI（2026-10-04，用户复验批次 AH 三条时报的一条真机布局 bug）

**界面测试只断言"这句话在不在"，就永远看不见"这句话把谁挤了"。** 确认页那句 AI 位置说明和「重新分析」在同一个 `Row` 里，两个子节点都没权重，而 `Row` 对非权重子节点是**按顺序量**的：文本先按整行宽度被量走（长到折两行），按钮拿到剩下的 0 宽，"重新分析"四个字竖成一列。`ReviewScreenTest` 里那 5 条 AI 文案用例全部断言的是 `assertExists()`，一条都没错，界面上却是坏的。修法一行：给文本 `Modifier.weight(1f)`（同文件 250/263 行早就是这么写的）。

**同类坑两周内第二次（批次 AA 修过照片墙筛选行吃掉网格），却没沉淀成通则。** 这次补上的判据：**只要 `Row` 里"一段会变长的文字"挨着"一颗必须按得动的控件"，文字就得有权重**——写测试时不能只问文案对不对，还要问邻居还是不是横排。新用例 `longAiGuessLineLeavesTheRetryButtonHorizontal` 断言的是形状（按钮 `boundsInRoot` 宽 > 高），RED 时报的是"宽 0.0，高 0.0"，与真机那个竖排是同一个原因。

**顺手做了全项目同因排查**（括号配对扫 `Row` 里同时出现 `Text(` 与 `*Button(`/`*Chip(` 且无 `weight(`）：命中 4 处，除本处外是数量行（－/数字/＋，定宽）、设置页三颗导入导出按钮（定宽）、照片墙两条筛选行（`horizontalScroll`，无限宽，正是批次 AA 的修法）。**没有第二处挤压，所以没有连带改动**。

**给用户写验收步骤时我把一条分支写成了必然路径**（他复验的第 1 件）：我写"点左上角 ✕ → 弹窗里选「不保存，直接离开」"，可 `requestExit()` 只在 `touched` 非空时才弹确认，他没手改过字段所以直接退了。行为是对的（批次 Z 的设计：没动过东西就别拦人），**错在我的指引没写前置条件**。往后交给他的每一步，凡依赖界面状态，都要把"什么情况下才会出现"写在步骤里。

测试 **33 类 / 363 例**（批次 AH 的 362 起算 + 本条形状用例），全量 1m26s 绿。

**真机自验**（9aa4b43e，`install -r`）：照片墙 → 马克杯 → 编辑 → 「重新分析」→ AI 回「家 / 橱柜」，那句说明折成两行占 `[44,1056][816,1144]`，「重新分析」在右侧 `[849,1073][1003,1128]`（154×55，横排），截图肉眼复核一致。跑完取 db+wal 对账：**马克杯仍是 CONFIRMED、`updatedAt` 还是 2026-10-03 15:32:03、位置未变**——用的仍是"CONFIRMED 物品重跑不落库"那条零副作用验法。

**残留清理**：设备 `/sdcard/ui.xml` 已删（`ls /sdcard | grep -c ui.xml` = 0），本机 `%TEMP%` 的 `ui.xml`、`ai-01-fix.png`、两份 db+wal 副本已删。机上另有他今天下午自己录的三件（「黑框眼镜」DRAFT 未归类、「黑色卡包」DRAFT 已归 家/橱柜、「收纳包」CONFIRMED），不是我的残留，没动。

---

批次 AJ（2026-10-04，一张照片录多件物品；spec 另开一份：`specs/2026-10-04-wheremy-multi-item-capture-design.md`）

**要解的是他自己提的场景**：一张照片里有骨传导耳机和手机支架，录完一件另一件就没了。方案在两条里选了 2（确认页顶部一排芯片），并拍板**一次最多 4 件**、「不要这件」**不需要二次确认**。分四片走：① 提示词+解析器认多件 → ② 分析链路带出多件 → 真机探针 → ③ 共享一张照片的数据层 → ④a 每件各算一次位置建议 → ④b 确认页多件。

**最容易做漏且最容易假装成功的一处是解析器**（spec §6 就点名的那条）。`AnalysisParser.extractObject` 的写法是"取第一个带我们认识的字段的结果"，模型回 `{"items":[…]}` 时它只会留下第一件——**看起来像模型没认出来，实际是解析器把其余件静默扔掉**。所以解析器同时认两种形状（数组与单对象），并且数组里混进不认识的那件要跳过而不是整批失败。这条按批次 AH 的判据不算验完：真机第一跑（17:32）回了三件、`finish_reason=stop`，才证明契约真的通了。

**探针带出两句改法，都是"改了提示词就得再真机验一次"的那种。** 一是模型把**承托面**当成了一件（那张照片多报了一件"桌垫"），二是 user 段还写着"识别这张照片里的**主要物品**"——system 已经要数组了，user 还在用单数口吻往一件上拽。两处改完两跑都回两件、桌垫不再出现、真物品没被一起误伤（§7.2）。顺带看见的不稳：第一件的 `container_guess` 是 `null` 而第二件有值，而当时确认页只拿第一件的猜测做预填——**等于把预填成败押在模型先说哪件上**，这条在 ④a 用"每件各算一次建议"消掉了。

**"删文件要引用计数"这条差点写成只数一半。** `refsTo` 第一版是 `WHERE filePath = :path`，两条新用例当场红了"缩略图不许被删"：`item_photo` 的 `filePath` 与 `thumbPath` 是两列，而两个目录里的文件名互不重合，所以一个路径只会出现在其中一列——**分开问反而容易漏掉缩略图那条**。改成 `filePath = :path OR thumbPath = :path`。备份侧同理：`export` 逐行 `putFile` 会往 zip 里塞两个同名条目，加了 `written` 集合去重，`summary.photos` 的含义改成"写出的不同文件对数"。

**实现偏离了自己写的 spec §4，并且是改对的那一侧。** 原话"一件一个 `ReviewViewModel` 实例"落地时发现撑不住：每件各算一次建议、退出时按"还有 K 件没保存"点名、「换一张」= 整批重跑，这三处都需要**有一处同时握着整批**，一件一个 VM 就得把这三件事推到外壳里去，而外壳本来只管"开哪一页"。改成一台 VM 管 N 个 `ReviewSlot`（`draft`/`touched`/`saved` 逐件独立），批次 X/Z 的既有语义是一条条搬进槽位，没有重写。已把理由写回 spec §4，不留一句"计划赶不上变化"的空话。

**跨件隔离的界面测试，必须在"非默认那件"上动手。** 我给"切芯片不丢手打的字"写的那条 UI 测试，是把字敲在**第 1 件**的名称框里再切来切去——而第 1 件正是默认选中的那颗，于是 `updateCurrent` 写死槽位 0 这种变异**只红了 3 条 VM 测试，界面那条照样绿**。断言文字写着"要落到这一件的草稿上"，实际验的是"落到第 1 件的草稿上"。改成先点第 2 颗芯片再敲字，它才真的有牙（同一条变异现在咬 4 条）。这是"界面测试只断言文案在不在"（批次 AI）的**同族**坑：位置/顺序/归属没设对，测试就在替我的巧合背书。

**Robolectric 的 `performTextInput` 把注入的字落在文本开头**，不是追加。所以"敲进去的字"这类断言只能咬"这段字在不在"，不能咬拼接顺序——我按追加写期望，红了一次 `expected:<[骨传导耳机（我的）]> but was:<[（我的）骨传导耳机]>`。

**只有真机看得见的一处标签 bug**：切到**没落库的第 2 件**，那颗按钮写着「选择照片」，可图就在上面、点下去走的也是"换掉这一批的图"。原因是标签问 `state.itemId == null`（这一件有没有行），该问的是"这一批选过图没有"。402 例没抓到它，因为界面测试从没在"第 2 件 + 未落库"这个组合下断言过这颗按钮。改成 `ReviewUiState.hasStoredItem`（VM 的 `hasDraft` 复用它，一处定义），先红后绿补了 `anUnsavedItemInABatchStillOffersToSwapThePhoto`。

**探针第 4 问（两件各自缩略图、删一件另一件图不丢）拖到切片 4 才补齐**——这是"探针阶段不写界面"这条安排的代价：探针只能回答模型回什么，答不了共用文件在真机上的后果。现在按预期收口：两件各显示缩略图、库里两行 `item_photo` 指向同一个 `bbcd795e…`、磁盘只多出**一对**文件（12→14）、删掉一件后两个文件仍在且另一件图正常、删完最后一件两个文件都被回收（回到 12）。

测试 **33 类 / 403 例**（批次 AI 的 363 起算：切片 3 数据层 +6、切片 4a +2、切片 4b VM +10 与界面 +8 再减掉改写、标签修复 +1，以全量跑为准），全量 1m34s 绿。变异三次：`updateCurrent` 写死槽位 0 → 红 4 条（改测试前只红 3 条）；`save()` 无条件 `finish()` → 红 `savingTheFirstSlotInABatchKeepsThePageOpen`；芯片排条件从 `slots.size > 1` 改成"总是出现" → 红 `aSingleItemPhotoShowsNoChipStrip`。均已还原。

**真机自验**（9aa4b43e，他 13:16 那张）：芯片排、圆点、切芯片换整张表单、「这一件还没保存」+「不要这件」、「保存这一件」不关页、「已保存这一件」提示、✕ 弹「还有 1 件没保存」且**不给**那颗兑现不了的「保存改动并留下」，全部按 dump 逐条对过；批次 AI 那条形状仍在（「重新分析」`[849,1238][1003,1293]` 横排）。中途一次 **AI 超时**（`isDataStall: tcp fail rate=87`，同机 `ping` 23 ms）走了「先手填，稍后可以重试」+「重新分析」续上，顺带证明超时链路在真机上可用。

**留给用户拍板的一条**（spec §10）：详情页「删除」的确认框写「照片和记录会一起删除，无法恢复。」——两件共用一张图时这句不再严格为真（只删行，文件留给另一件）。后果正是想要的，所以不算 bug；要不要为这个精度换一句话由他定。

**残留与设备现状**：自验期间保存/新建的三行全走界面「删除」清掉，收尾时再取 db+wal 复核一遍——机内 **5 行全部 CONFIRMED**（按摩球、手机指环扣123、笔记本电脑测试、马克杯、骨传导耳机），`createdAt` 分别落在 10-03 与今天 13:16（骨传导耳机 那条是他自己下午录的，不是我建的），**没有一行留在我 19:0x–19:1x 的自验时间段里**；`item_photo` 6 行（5 件物品 + 1 张位置照片）与 `files/photos` 的 originals/thumbs **各 6 个、一一对应**，位置树 3 个节点，0 条 DRAFT。设备 `/sdcard/{w,cur}.xml` 与本机 `%TEMP%` 的 `wtest/`（4 张截图、`wdump.py`、db 副本）、`w.xml`（19:06 那次 dump，落在 Git Bash 的 `/tmp` = Windows 的 `Temp`，第一次清理只删了 `w.db` 漏了它）与 `/tmp/w.db` 均已删除并列目录确认（`ls Temp | grep -cE '^(w|u|t|ua|ah|ag|ai|aj)[0-9]*\.(xml|png)$'` = 0）。**收尾时新验到的一条工具事实**：python `connect()` 打开副本时会把同目录的 `-wal` 先 checkpoint 进主库再删掉（这次 `j.db-wal` 就这么没的）。这不影响读数——**checkpoint 之后主库就是全的**——但**那对副本只剩一份了**，要再看原始 db+wal 得从设备上重新取一次；清理时按"取两份、读一次、剩一份"记账，别把丢掉的 `-wal` 当成谁误删。装机包就是 `a987833` 那次构建的 debug apk（`install -r`）。

---

批次 AK（2026-10-04，用户拍板批次 AJ 留给他的那条：共用一张照片时删除提示要分情况说）

**他问的比"改一句文案"宽**：「一个照片对应两条或多条记录时，删除其中一条实际上是未删图片吗？如果是，就改一下说明；当记录删到只剩一条记录时，再删这条记录是否会图片和记录一起删除？这个情况分类要整理好，实际处理也要分类执行。」所以这批交付的是**四档分类 + 每档的说法 + 每档的真实后果**，而不是一句更长的提示：

| 情况 | 弹框正文 | 文件 |
| --- | --- | --- |
| 这件没有照片行 | 这条记录会被删除，无法恢复。 | 无 |
| 还有 N 件物品在用 | 这张照片还有 N 件物品在用，删掉这一件只会去掉这一件的记录，照片留着。 | 留下 |
| 共用方不是物品（位置记录） | 这张照片还有别的记录在用，删掉这一件只会去掉这一件的记录，照片留着。 | 留下 |
| 只这一件在用 | 照片和记录会一起删除，无法恢复。 | 回收 |

**第三档不是凑数**：正常操作造不出"一件物品的照片同时是一条位置记录的照片"，但手改过的备份包能（`item_photo` 只按 `(ownerType, ownerId)` 认主人，两列路径谁都能填）。这一档的存在理由写进了 `Daos.otherOwnersOnPath` 的注释，措辞上它跟第二档的区别是**数不出"几件"**——共用方可能是 1 条位置记录也可能是别的什么，说"还有别的记录在用"是不撒谎的下界。

**分类落在数据层，一句话由一个纯函数说。** `PhotoDeletion(hasPhoto, otherItems, otherOwners)` 由 `ItemRepository.photoDeletionOf` 现算（`otherOwnersOnPath` 用 `COUNT(DISTINCT ownerId) … GROUP BY ownerType` 并排除自己，多条路径时取 `maxOf`——共用一件还是共用了三件，取最宽的那个说法才不越界），`deleteWarningOf` 只做映射。详情页与照片墙的菜单**共用这两个函数**，不再各写一句：以前两页都硬编码"照片和记录会一起删除"，改一处漏一处的话，同一个动作在两个入口会说两句不一样的话。

**那句话在按下「删除」的那一刻现查，并跟 id 一起存成 `PendingDelete`。** 不在加载时算（多一次读、且列表刷新后就是旧数据），也不在最终确认时算（那时弹框已经说了话）。只存 id 不够：弹框开着的那几秒里照片墙可能刚被删空/刷新，`askDelete` 算的是哪一件得跟着那句话一起留在状态里。

测试 **33 类 / 409 例**（批次 AJ 的 403 起算：数据层分档 2 + 详情页 2 + 照片墙 1 + `deleteWarningOf` 四档逐条 1），全量 1m38s 绿。变异四次，各自咬到的都记在明处：DAO 自排除从 `AND NOT (type AND id)` 改成 `AND NOT (type OR id)` → 红 3 条（数据层 + 两页界面）；`photoDeletionOf` 把 ownerType 分档塌成"全算物品" → 红 `photoDeletionOfCountsANonItemSharerAsKeepingTheFile`；`askDelete` 写死那句"一起删除" → 红 `deleteWithoutAPhotoDoesNotMentionPhotos` 与 `deleteOnASharedPhotoSaysThePictureStaysWithTheOtherItem`；`otherOwners > 0` 改成 `> 1` → 红四档那条。**第四条是补的**：前三次跑完发现 `deleteWarningOf` 第三档的**字符串**没有任何测试钉过（数据层数得对，但没人保证那一行字还在），于是给 `UiFormatTest` 补了一条四档逐条断言。

**一条写完就删掉的断言：弹框里长文案会不会把「取消」「删除」挤成竖排。** 按批次 AI 的教训写了形状用例（两按钮 `boundsInRoot` 宽 > 高），然后把正文换成 110 字、再换成 1400 字——**它都不红**。原因是 M3 的 `AlertDialog` 自己消化超长正文（正文折行、按钮区另起一行），批次 AI 那个坑只在"文字与控件同处一个 `Row`"时成立。**没有失败模式的断言就是等着变红的假绿**，删了；真机上顺手复核了形状（见下），不立成用例。

**真机自验（9aa4b43e，`install -r` 本批构建）**：机上没有共用照片对（4 件各一张 + 1 张位置照片），而界面造不出共用对——共用只在"一张照片录多件"时产生，那条链路要过系统相册选择器。所以**换 db 造**：`force-stop` → 取 db+wal → 本机 python `sqlite3` 插两件（`共用测试甲/乙`）+ 两行 `item_photo` 指向同一对**新复制出来**的文件（`originals|thumbs/aktest-shared.jpg`，从他那张马克杯照片 cp 改名）→ checkpoint 后推回、删掉机上旧 wal/shm。**故意不挂到他任何一件真物品上**：万一引用计数是坏的，赔的也是我造的这一对文件，不是他的图。

- 第二档：甲详情页「删除」→ 正文 `这张照片还有 1 件物品在用，删掉这一件只会去掉这一件的记录，照片留着。`（折两行，`[166,1150][914,1260]`），「取消」`[613,1365]` 与「删除」`[795,1365]` **同一 y、都横排**——形状复核靠 dump 完成，不需要那条没牙的用例。
- 后果：确认删甲 → 两个 `aktest-shared.jpg` **都还在**，照片墙下滑后乙的缩略图正常显示（不是只剩个格子）。
- 第四档：删乙（最后一位主人）→ 正文回到 `照片和记录会一起删除，无法恢复。` → 确认后**两个文件都被回收**。
- 第一档没在真机重跑（`deleteWithoutAPhotoDoesNotMentionPhotos` 已在界面测试里覆盖，且机上没有无照片的 CONFIRMED 物品）。第三档不验：界面走不到它，由四档那条用例钉住说法。
- 收尾对账（db+wal 双取）：**4 件物品、5 行 `item_photo`、无共用路径、无孤儿照片行**，`files/photos` 的 originals/thumbs 各 5 个——与他机上的原始状态一字不差；他自己那 4 件（按摩球 / 手机指环扣123 / 笔记本电脑测试 / 马克杯）全程没当过实验对象。

**踩到并要记下来的三条工具事实。** ① **照片墙空查询走 `itemDao.all()`，没有 `ORDER BY`**，新插入的行按 rowid 排在**最后**——我一度据此判定"app 没读到我推的 db"，反复 force-stop、重取 db、怀疑 Room 的库名，实际只要往下滑一格就看见了。判据：**验收"某样东西没出现"之前，先把列表滑到底**。② `MSYS_NO_PATHCONV=1` 时 **`adb pull` 的目标路径必须是 Windows 形式**（`C:/Users/…/Temp/x.xml`）；传 `/tmp/…` 会报 `cannot create file/directory`，而这条错误被 `>/dev/null` 吞掉之后的表现是"dump 文件时间没变、界面好像没响应"——**别给会改变判断的取数据命令加静默重定向**。python 同理，`/tmp` 得写成 `C:/Users/lifxu/AppData/Local/Temp`（Git Bash 的 `/tmp` 就是它，`cygpath -w /tmp` 可查）。③ **只取 `.db` 不取 `-wal` 会读到旧世界**：删完两件之后主库仍显示 6 件 7 行，因为删除全在 wal 里没 checkpoint——第 9 条"db+wal 双取"这条老规矩在**读数**时同样致命，不只是取文件时。

**顺带收口的一条偶发（不是本批引入）**：全量跑出现过一次 15 例红，全在 `SettingsViewModelTest`，红的位置是 `tearDown` 的 settle 超时，消息里带着**别的用例**写进去的 `apiKey` 值；单类跑 21/21 绿，随后两次全量 408/409 全绿，且那一轮只有一个测试 worker 进程（PID 与整轮生命周期吻合）。定性：**所有测试共用一个 DataStore 文件**，一次磁盘停顿把某笔写拖过窗口，`TestSettings.settle` 又只在"观察到的值变了"时补一轮写，于是一次丢写就把后面每条都变成 30 秒超时（整轮时间被 30 秒整数倍撑大是这个签名）。**没有动共享测试基建**：候选真修法是每例一个独立 DataStore 文件（属生产 DI 改动），或按时间补写（反而加压那条写路径，而"只补一轮"正是它注释里写明的取舍）。识别签名留给以后撞见的人：**一类全红 + 全在 tearDown + 消息里是别人的值 + 整轮时长被 30 秒撑开**。

**另外两条 Windows 事实**：改过 main 源码后重跑必现 `bundleDebugClassesToCompileJar` 的 `FileSystemException`（`compile_app_classes_jar/.../classes.jar` 被守护进程攥着），`gradle --stop` 解锁；**红过一次之后 `test-results` 里的旧 XML 还会汇总出满绿读数**（那次差点把 33/408/0 当成新结果），判绿必须同时看日志里有 `BUILD SUCCESSFUL` **和** XML 的 mtime。

**残留清理**：设备 `/sdcard/{w.xml,s.png}` 已删；本机 `%TEMP%/akdev/`（dump、截图、db+wal 副本、`nodes.py`/`craft.py`/`inspect.py`）与 `/tmp/ak_*.log`、`/tmp/ak_full2_results/` 已删并列目录确认。装机包是本批构建的 debug apk。

批次 AL（2026-10-04，AK 收尾自查出来的"同一个规矩有两个版本"）

AK 落地之后把"共用一张照片"这条链路重走了一遍，这次问的是**说出去的话由谁兑现**：AK 让删除确认框敢讲"照片留着"，靠的是 `ItemRepository` 里的引用计数（`deleteUnreferenced`：再没有照片行指着才删）。可位置那一侧的 `LocationRepository.setPhoto` / `clearPhoto` 仍然是**无条件** `photoStore.delete`。两个版本一旦撞上 AK 自己承认过的那个状态——`item_photo` 里一条位置行与一条物品行指着同一对文件（正常操作造不出，手改过的备份包造得出）——就成了"在位置页点「移除照片」「换一张」或删除位置，把另一件物品正在用的那两个字节带走了"，而那件物品的行还声称自己有照片：卡片点开是空的，`photoBytesOf` 返回 null，详情页那颗「重新分析」在 `CaptureFlow.reanalyze` 第一行就返回 null，什么都不问。

**修法只有一条：文件回收规则只留一个实现处。** 新增 `data/repo/PhotoReclaim.kt` 里的 `deleteUnreferencedPhotoFiles(photoDao, photoStore, paths)`——就是原来那三行（滤空路径、去重、`refsTo(path) == 0` 才删），物品侧的私有 `deleteUnreferenced` 变成对它的一行转调，位置侧 `setPhoto`（被换掉的那对）与 `clearPhoto`（被撤掉的那些）改走同一条。"**调用方要先把自己的行删掉/换掉再进来**"这条顺序要求现在写在函数自己的注释里，而不是散在两个仓库各自的说法里——它正是这次会漏掉的那类前提。

**"没人用就回收"这条老行为一点没松**：位置侧原有三条按文件计数断言的用例（`clearPhotoRemovesRowAndBothFiles`、`settingSecondPhotoReplacesTheFirstAndDeletesItsFiles`、`deletingLocationTakesItsPhotoWithIt`）改完仍原样绿，说明这次改动的范围确实只有"还有人用时要留着"。

测试 **33 类 / 411 例**（新增 2 条：`settingPhotoKeepsTheSupersededPairWhileAnItemStillUsesIt`、`clearPhotoKeepsFilesAnItemStillPointsAt`）。造状态的办法是直接 upsert 一条 `ownerType = item` 的行、把路径抄成位置那条——就是手改过的包导入之后库里会出现的形状。RED 先看过了：两条都红在"被换掉的原图还有物品在用"这一类断言上。变异三次，各自咬到的都记在明处：

- 共享函数改回"无条件删" → 红 **4** 条：两条新的 + 物品侧 `deletingOneSharingItemKeepsTheFileForTheOther`、`swapPhotoOfASharedImageLeavesTheOtherItemOnTheOldFile`。**这一条同时证明"两侧共用一个实现"是真的共用了**，不是各处又写了一份。
- 只删掉 `clearPhoto` 里那次回收 → 红 **3** 条：`clearPhotoRemovesRowAndBothFiles`、`deletingLocationTakesItsPhotoWithIt`、`TreeViewModelTest.clearPhotoTakesItBackOut`。
- 只删掉 `setPhoto` 里那次回收 → 红 **1** 条：`settingSecondPhotoReplacesTheFirstAndDeletesItsFiles`。

**这批没上真机，也不欠真机**：机上走不到这个状态（界面造不出跨类型共用），而硬造要走**覆盖导入**，那会换掉用户自己那 4 件真数据——AK 定的规矩是"实验只赔我造的那一对文件"，覆盖导入做不到这条。物品↔物品那一条已在 AK 真机验过引用计数真的兑现；这次这条只有手改过的包才触达，由数据层 2 条用例加上面三次变异钉住。**Room schema 仍一字未动**（`refsTo` 是现成的查询，`DB_SCHEMA_VERSION` 还是 1），装机包没换。

**给下一位的通则**：一条不变量（"文件只在没人指时才删"）一旦在第二个调用点被重新实现一遍，它就不会跟着第一个调用点的修正一起变。**改规则时先 grep 规则本身**（这次是 `photoStore.delete`），别只改想到的那一处。

批次 AM（2026-10-04，AL 之后把"照片"这条链再走了一遍：备份两侧的口径必须对得上）

**起点是我写错的那一半。** 在 `validate` 里看到的缺口是"照片行指的那两个文件不在包里，导入却照样成功、还照行数报「照片 N 张」"，于是第一版测试直接写成"少了原图条目的包必须被拒"。动手前回头读了导出侧，才发现这条规则一旦立起来会把备份变成**单向门**：`putFile` 是**故意**放过丢失文件的（它上面那行注释写着"一张丢失的缩略图不该让整次导出失败"），所以一台已经掉了一个文件的机器导出的包，按我那条新规则就再也导不回去——而用户最需要这一次恢复的时刻恰恰是设备已经出过问题的时候。**判据：新增的"整体校验"必须拿产出侧能产出什么来校准；"库里不该有这样一行"不等于"包里不该有这样一行"。**

**真正站得住的那一半是空路径。** 一条 `filePath`/`thumbPath` 为空的照片行连"该指哪儿"都没说，落库就是一件点开没有图、又永远删不掉文件的哑物品——而 `newDraftSharingPhoto` 在自己家里都明确拒绝造这种行（注释原话），导入这一侧原来是照收的。这一条**导出造不出**（路径从库里原样抄，而库里造不出空路径），所以拒它不伤任何真实往返。缺文件则继续放行，库里留下的是与源设备相同的那个缺口，而不是整包被拒。

**两条用例一起把口径钉死，各自都有牙。** `photoRowWithABlankPathIsRejected` 的 RED 先看过：当时 `exceptionOrNull()` 是 `null`，那个包整个导进了库。`packageMissingOnePhotoFileStillImportsBecauseExportToleratesIt` 是**钉住现状**的那条（它防的就是下一个读到这段的人"顺手把校验修严"），给它验牙的变异是把"文件必须齐"写进校验（`if (data.photos.any { it.filePath !in bundle.keep }) throw`）→ 只红它一条，同类的照片校验用例照绿 17 条。造法用的是 `dropEntry()`（解包、抽掉一条、原样重打），空路径那条走的是已有的 `rebuild()` + `seedRejected()`。

测试 **33 类 / 413 例**，全量 1m39s 绿，XML 全在 21:52 落盘。这批没上真机也不欠：它只在"手改过/截断过的包"上显形，而真机验它得往用户机器上导坏包；备份往返本来就只在 JVM 里跑（AG 那条说明仍适用）。装机包仍是 AK 那次构建，schema 未动。

批次 AN（2026-10-04，用户在排队的四项里点了 Q1：把 AI 那一条链路变成我自己能读的）

**起点是"我看不到"。** 批次 T/U 把**失败**说清楚了（额度、key、模型名、网络、被截断各有一句中文），可"成功"那一侧一直是黑的：模型到底回了什么、我们发出去的提示词长什么样，界面上没有任何地方能看。AJ 真机探针那次我只能靠"界面上填没填上"反推模型回了几件；用户复验能报的也只有"没填上"三个字。**判据：一条链路只有在其成功结果可被第三方读到时，才谈得上被验收**——否则"通过"等于"我猜它通了"。所以这一批不是加功能，是给 AI 那条链路装一个能事后看的记录仪。

**形状：一条尝试一个文件，落在 `files/ai-trace`。** `TraceEntry(model, status, outcome, prompt, response)` 五行；不写成一份 JSONL 是刻意的——追加要读旧内容、写坏了把整份历史带走、而 `count()` 还得数行；一条一个文件时这三件事都不存在。文件名 `%013d-%03d.json`（毫秒 + 同一毫秒内的第几条），**排序就是时间排序**，不需要在文件里再存序号；序号是必要的：重试两次可能落在同一毫秒，只用毫秒命名的话第一条会被第二条覆盖掉，记录仪自己先丢数据。滚动只留最近 `ANALYSIS_TRACE_MAX = 10` 条。

**截断要在文件里说出口。** 上游偶尔回一整页代理的 HTML 报错，不封顶就能把磁盘写满；封顶 8000 字之后，被剪掉的那一段必须留一句 `…（截断，原长 N 字）`——否则读到一个"看着完整"的原文，比读到乱码更糟。

**key 与图片字节是结构性地不在，不是"记得过滤"。** `TraceEntry` 压根没有这两个字段，`record()` 只拿模型名、状态码、结局标签、提示词、回应正文。这条用一用例钉住（`traceCarriesNeitherTheKeyNorThePhotoBytes`：断言整个入口 `toString()` 里既没有 `sk-test` 也没有 `data:image/jpeg;base64`）——**"没有"这种否定属性最容易在下一次改动手滑加回来**。

**状态码到结局的映射只留一处。** 新抽出的 `classify(code, text)` 是唯一的 `when`：`attempt` 先拿它判结局，界面那句中文和留痕里那个 `outcome` 标签都从同一个结果取。**这是 AL 那条判据的第二次应用**（"一个规矩不许有两个版本"）——以前 `parseCompletion` 与 `when (resp.code)` 是分开长的，加一类失败就得记着改两处。同时 `systemPrompt` 在 `analyze` 里算一次，喂给请求与写进留痕的是同一份字符串；否则"痕里的提示词"可能不是"发出去的提示词"。

**留痕是旁路，不许反噬主路。** `record()` 外面裹 `runCatching`：目录写不了（磁盘满、权限被撤）也要把分析结果还给用户。用例是 `aTraceWriteFailureDoesNotBreakTheAnalysis`（一个每次都抛的 `ThrowingTrace`，断言分析仍然 `Success` 且真的尝试过一次）。还有 `noConfigLeavesNoTraceWhileARealCallDoes`：没配 key 时短路，一条痕都不该有——**这条的前半单独存在时是假绿**（什么都不记也满足），所以后半用一次真调用把"确实会记"钉住。

**边界：不在 `files/photos` 底下。** 备份只搬数据库行 + 那个目录，而留痕里带着**用户自己的位置清单**（提示词尾部就是候选路径列表），所以它跟着备份出门就是隐私外流。这条边界有钉子：`traceEntriesLandOutsideWhatBackupCarries` 断言文件落在 `files/ai-trace` 且路径不以 `files/photos` 开头。界面那一行同样只给 debug 包看（`showDebugTools`），文案是 `AI 留痕：N 条（发出去的提示词与模型回应原文，只留最近 10 条）`，那个 10 与滚动删除用的是同一个常量。

测试 **34 类 / 428 例**（新增 15：留痕文件 4 + 分析器 7 + 设置页用例 1 + 界面 2 + 容器边界 1），全量 1m48s 绿，34 份 XML 全部在本次跑里落盘（脚本按 mtime 断过新鲜度）。变异记在明处的有六次：删掉 `record()` 调用 → 红 7 条（分析器新用例全灭，包括那条对照）；`record` 的提示词换成整份请求体 → 只红 `traceCarriesNeitherTheKeyNorThePhotoBytes`；去掉 `record` 外面的 `runCatching` → 只红 `aTraceWriteFailureDoesNotBreakTheAnalysis`；把 8000 的上限挪走 → 红 `overlongPromptAndBodyAreTruncatedWithAMarker` 与 `recordsThePromptSentAndTheRawResponse`；`init` 里那次读条数删掉 → 红 `debugAreaShowsHowManyTracesAreStored` 与 `debugBuildShowsHowManyTracesAreStored`；debug 门改成恒真 → 只红 `releaseLikeBuildHidesTheTraceCount`。**还有一次是"改对地方但改了共享常量"**：留痕上限从 `FileAnalysisTrace` 的私有 `KEEP` 提成顶层 `ANALYSIS_TRACE_MAX` 之后，界面文案不再自己写 10——这一步没有独立变异可跑，它防的是下一个人只改一处。

**真机自验（9aa4b43e，装机包就是本批构建）。** 按 `run-as` 数目录：动手前 `files/ai-trace` **不存在**；照片墙 → 「按摩球」→ 右上「编辑」→「重新分析」→ 等一次回应之后目录里 1 个文件（`-rw-------`、3371 字节）。把文件读回来对字段：`model=deepseek-flash`、`status=200`、`outcome=success`、提示词 684 字（开头就是批次 AJ 那句"识别照片里出现的物品，最多 4 件"）、回应 1332 字（上游原样的 `chat.completion` 正文，`content` 里是 `{"items":[{"name":"按摩球"…`）；整份文本里没有 `sk-`、没有 `base64`。**设置页那一行跟着显示「AI 留痕：1 条…」**。**这一条链路由我自己跑完并读到了证据，用户不欠验收。**

**顺带纠正一条我自己写过的结论。** 「详情页重新分析零副作用」只在物品是 **CONFIRMED** 时成立（`applyAnalysis` 第二行 `if (cur.status != ItemStatus.DRAFT) return`）。机上那件「按摩球」其实是 **DRAFT**（它有 `家 / 抽屉` 的位置，所以不出现在「待归类」里，但状态一直是草稿），所以这次重新分析**按设计**把它的字段刷了一遍（`updatedAt` 从 10-03 变成 10-04 23:11:30）。**不要把"零副作用"当成普适结论**：它是状态判断的结果，先查状态再说话。

**两条工具事实。** ① **`adb shell cat` 会按 CRLF 改写二进制**：用它取 `run-as … databases/wheremy.db` 回来的副本，python `sqlite3` 报 `malformed database schema`，而 `integrity_check` 说库本身没问题——换成 **`adb exec-out run-as … cat`** 之后 73728 字节的副本 integrity ok。这是第 9 条"db+wal 双取"的兄弟坑：**取对了文件仍可能被传输层改坏**。② AI 那一节多一行之后，「覆盖导入」落到了折叠线以下，`overwriteAsksBeforeWiping` 的点击静默丢掉、红过一次；本文件在 AK 那批已经写过一次同一个坑（"折叠线以下不滚就丢事件"），**第二次撞上说明判据没被当成判据**——凡是新加一行正文的用例，改完就把下方按钮的 `performScrollTo()` 一并补上。

**残留清理**：设备 `/sdcard/w.xml` 已删；本机 `app/build/tmp/` 下这次取回的 **db / wal / shm 副本、trace.json、界面 dump 全部删掉**（里面有他的真实数据，一份没留）。**Room schema 一字未动**，`DB_SCHEMA_VERSION` 仍是 1。

批次 AO（2026-10-04，排队项 Q2：桌面图标）

**先说清缺口有多大**：清单里**压根没有 `android:icon`**，`res/` 下只有 `values/`（strings + themes），也就是说这台机上桌面上一直是系统给的那个默认方块。这不是"图标不好看"，是"没有图标"。

**形状：手写矢量自适应图标，不引任何图片工具链。** `mipmap-anydpi-v26/ic_launcher.xml`（`<adaptive-icon>`：背景 = `@color/ic_launcher_background`，前景 = `@drawable/ic_launcher_foreground`）+ 一个 108×108 的 `<vector>`。`minSdk = 26`，所以不需要任何密度桶里的 PNG——v26 限定就是全量覆盖。**应用名这次不动**（`app_name` 仍是 `Wheremy`；要不要换中文是问过没答的一条，改它是一行字的事，不替用户决定）。

**安全区判据（这批唯一有点技术含量的地方）**：自适应图标的画布是 108×108，但**只有正中间直径 66 的那个圆**在任何蒙版下都保得住。所以钉了一条用例：前景矢量里**每一个坐标**（含贝塞尔控制点）到圆心的距离 ≤ 33。这条为什么算得成立——三次贝塞尔曲线落在其控制点的凸包内，而圆是凸集，所以"控制点都在圆内"直接推出"描出来的曲线也在圆内"。以后往边上扩东西会先在这里红，而不是在用户桌面上缺一角。

**第一条用例的取样点错了，错得值得记。** 补"真的画出了东西"那条时，我按设计坐标取了 (54,30)、(54,45) 这些点，当场红：`expected:<-1> but was:<-16750244>`（白 → 青）。原因是 **`AdaptiveIconDrawable` 画图层时绕中心放大 1.5 倍**（`DEFAULT_VIEW_PORT_SCALE = 1/1.5`），渲染出来的像素坐标不等于矢量里的设计坐标。判据：**断言墨迹的性质，不钉死像素点**——钉点位等于把框架的一个缩放常数抄进测试里，它哪天变了就红得莫名其妙。现在断的是：内侧没有透明像素（背景层真铺满）、白墨 > 400 px（前景真画了）、墨迹重心在 x=54 一条竖线上（±1，抗锯齿的亚像素偏差）、纵向从 ≤30 伸到 ≥78（路径退化成点就过不了）。顺带这也回头确认了安全区那条没写错：设计坐标里的半径 33，渲染后是半径 49.5，正好落在 108 画布那个内切圆里。

**为什么"坐标都在安全区内"还不够。** 一条合法但几乎没有面积的路线径（比如中心一个 2×2 的小方块）三条判据里只有渲染那条会红——坐标全在圆内、接线也没问题，可桌面上就是一片纯色。这条缺口是用变异 C 验出来的，不是想出来的。

**针孔用背景色挖，不用透明。** 前景第二个 `<path>` 的 `fillColor` 直接引用 `@color/ic_launcher_background`：背景层与孔是同一个数，不会哪天一个改了另一个没改。

测试 **35 类 / 431 例**（新增 1 类 3 条），全量 1m40s 绿，35 份 XML 最旧的一份距今 1 秒（新鲜度按 mtime 断过）。变异三次：摘掉清单里的 `android:icon` → 红接线那条；背景色改成 `#8000695C`（半透明）→ 红接线那条；前景两条路径都换成安全区内一个 2×2 小方块 → **只红渲染那条**，坐标那条照绿（正是它盖不住的形状）。

**真机自验（9aa4b43e，`install -r` 本批构建）**：`KEYCODE_HOME` + 上滑进应用列表，截图里「Wheremy」那颗是**青底白色定位针的圆角方块，蒙版没切掉任何一部分**。截图当场从设备与本机各删一份，`ls | grep -c` 都是 0（应用列表里其余是他的东西，没动）。**这批也不欠用户复验**：图标长什么样已经用真机截图看到了，剩下唯一的主观部分是"你喜不喜欢这个针"。

**两条工具事实。** ① `adb shell screencap -p /sdcard/x.png` 要在**同一条命令之前**就 `export MSYS_NO_PATHCONV=1`，否则 Git Bash 把设备路径改写成 `D:\src\…\sdcard\x.png`，`screencap` 打印 usage 退出——表现是"截图命令跑了但文件没生成"。② 想知道图标**渲染成什么样**不必跟桌面启动器较劲：Robolectric 里把 `AdaptiveIconDrawable` 画进 `Bitmap` 再数像素就能看到真实形状（前提是按批次 AC 那条加 `@GraphicsMode(NATIVE)`，否则 Canvas 绘制是空操作，整张图全黑也照样"通过"）。真机截图只用来确认蒙版。

**残留清理**：临时用来打 ASCII 像素图的 `ScratchIconDumpTest.kt` 已删；变异用的 `.bak` 已删；设备与本机截图已删。**Room schema 一字未动**，装机包是本批构建。

批次 AP（2026-10-05，排队项 Q3：低配版拍照——先让快门能用，真取景留给 CameraX）

**边界先说死**：这一批**不申请 `CAMERA` 权限、不引任何新库**，走系统相机 `ACTION_IMAGE_CAPTURE` + `FileProvider`；Wheremy 自己不取景。Plan 2 里那条 CameraX 真取景（对焦、快门动画、取景框里的叠加信息）是另一件事，这批只是它的低配替身。三个既有入口（照片墙右下角那颗 +、录入页的「选择照片/换一张」、位置页的「给这个位置选一张照片/换一张」）现在都先问一句「这张照片从哪来？」，两条来源回来之后**汇进同一段 `deliverPhoto`**——屏幕的签名（谁开录入页、谁挂位置照片）一个都没改，所以 AJ/AK 那批签过字的用例照原样绿。

**落点选在 `cacheDir` 是有三个理由的**：不要存储权限、快门图不进用户相册、备份压根不该看见它（备份只搬 `filesDir`）。交出去的是 content URI（`file://` 在 API 24+ 直接崩），读走字节之后当场 `delete()`，下次开工先把上一次被取消的那半张扫掉。

**Windows 上 `uriFor` 永远跑不通，而且不是我的代码错了。** 三条探针 + `javap` 反编译定到的根因：androidx.core 1.19.1 的 `FileProvider$SimplePathStrategy.belongsToRoot` 把路径分隔符**硬编码成 `/`**（字节码 `bipush 47`），而 Windows 的规范路径全是 `\`，于是本机必抛 "Failed to find configured root"。决定性证据是反射 `FileProvider.sCache` 里那个 `mRoots`——**根目录其实解析对了**，`File(ctx.cacheDir,"camera-shots")` 与表里的值就是同一个目录，输在字符串比较的那一步。**判据：不改生产代码去迁就测试平台。** 所以可测的接缝改成两头上得秤的配置：`file_paths.xml` 里的 `path` ↔ `CameraPhotoStore.dir`（用 `resources.getXml` + `XmlPullParser` 真解析，并断言恰好一条）、代码里那个 `authority` ↔ 清单里的 `<provider>`（`resolveContentProvider(…, GET_META_DATA)` 连 `exported=false`、`grantUriPermissions=true`、`meta-data` 指向 `@xml/file_paths` 一起钉）。URI 真的能用、相机真的写得进来，这两件事由本批的真机自验覆盖，写在 `uriFor` 与那条用例的 KDoc 里。

**推翻了一条我自己写过的旧笔记。** 它说 Robolectric 投不进 `PickVisualMedia`，所以来源弹窗之后的分发"只能真机看"。实际上 `shadowOf(activity).nextStartedActivityForResult` + `receiveResult(started.intent, RESULT_OK, Intent().apply { data = uri })` 就能把结果送回外壳，字节用 `shadowOf(contentResolver).registerInputStream(uri, …)` 提供。外壳因此多出 5 条自动化用例（+/-1 那条过时用例），其中"取消"与"来源给错"这两类以前只能靠人点。

**一次设计错是在我自己手里发现的**：第一版让 `photoTarget` 同时承担"弹窗为谁开着"和"在飞那张给谁"，表现是系统相机盖在上面时后面还压着一张问句。拆成 `askingFor`（弹窗）与 `photoTarget`（在飞）、点来源时交棒，才对。

测试 **37 类 / 442 例**（新增 2 类：`CameraPhotoStoreTest` 5 + `CameraWiringTest` 2；外壳 +5 −1），全量 2m7s 绿，37 份 XML 的 mtime 全在本次跑内（上一批那个 300 秒阈值的脚本把 37 份全报成 stale，是阈值的问题不是回归的问题——这次按"起跑时刻"判新鲜度）。**`theAppAsksForNoCameraPermission` 是实现之前就绿的**，它是"钉住现状"的一条（权限只有 INTERNET + POST_NOTIFICATIONS），不是测出来的行为。

**变异八次，两次没牙，而没牙的两次比有牙的六次值钱。** 有牙的六次：摘掉清单里的 `<provider>` → 只红接线那条；`file_paths.xml` 的 `path` 改成别的目录 → 只红 paths 表那条；`AUTHORITY_SUFFIX` 少写一个字母 → 只红接线那条；`takeBytes` 不删文件 → 红 2 条（`theCameraBytesComeBackAndTheTempFileIsGone` 与 `aShotTheCameraNeverFilledReadsAsNull` 都断言扫尾）；`newShot` 不清扫残留 → 只红 `startingAShotSweepsTheLeftoversOfTheCancelledOne`；弹窗两颗按钮的 onClick 对调 → 红 4 条（相机那条 + 三条相册路径）；位置入口写成 `PhotoTarget.ITEM` → 只红位置那条；`deliverPhoto` 里 `else if (forLocation)` 关掉 → 只红位置那条。**没牙的两次**：把 `deliverPhoto` 读完之后那次"归零"删掉 → 全绿；把取消分支里的归零删掉 → 也全绿。原因是这一格在**发起请求时**就被 `takePhoto`/`pickFromAlbum` 按来源重写了一遍，所以"下一张不会被上一张借走"跟归零毫无关系——**而我给 `deliverPhoto` 写的注释正好声称有关系**（"留着这一格就会把下一次点 + 的图也塞给位置树"）。顺着这条注释查下去，`aCancelledLocationPickDoesNotBorrowTheNextPhoto` 是一条**空测试**：它盯着的正是那个不存在的因果。改成了 `aCancelledPickSaysNothingAndTheNextPhotoStillOpensTheReviewForm`，断的是取消那一刻真欠用户的东西——不许冒出一句「读不到这张照片，换一张试试」教训没做错事的人；拿"取消也塞进 `deliverPhoto`"这个变异验过，只红它一条。**判据：注释里"这样做是为了 X"的那种因果，也要能被一次变异证伪；不然它就是我想当然的副产品。** 归零两行留着，但注释改成实话：这一格的意思是"有一张在飞"，用掉了就归零，至于路由，靠的是发起请求那一次赋值。

**真机自验（9aa4b43e，装机包就是本批构建）——两次拍照，两条路各自走通。** 物品那条：照片墙 → + → 「这张照片从哪来？」→「拍照」，焦点窗变成 `com.android.camera/.OneShotImageCapture`，同时 `run-as` 看见 `cache/camera-shots/shot-1791142145347.jpg`（**0 字节**，等相机往里写）；按「拍摄」之后 HyperOS 不直接交回，而是给「重拍 / 完成」两个钮，点「完成」才回我们——焦点回 `MainActivity`、`cache/camera-shots` 变回 `total 0`、录入页顶上就是那张照片，AI 也真跑了：留痕里 `status=200`、`outcome=NOTHING_FOUND`、模型 `reasoning_content` 写着 "plain dark gray/black surface, no recognizable objects"（**这一句是"字节真的到了 API"的旁证**，而不只是"界面没报错"），整份痕里没有 `sk-`。位置那条：位置 → 「家」→「换一张」→「拍照」→ 拍 →「完成」，停在位置页、`位置照片` 与 `移除照片` 都在、没有开录入页，而库里那条 `ownerType=location` 的行换成了新文件，**旧的 `2fb7b1cb….jpg` 在 `originals` 与 `thumbs` 两边一起消失**（AL 的引用计数在真机上兑现了）。**这一条链路由我自己跑完并读到了证据，用户不欠验收。**

**读机型时撞见一条设计现状，不是我改坏的。** 录入页一进去就按「先落库」写了一行 DRAFT（`ReviewViewModel:393` 的注释原话：「库保持原样，只有先落库的那行和 AI 的字段」），所以拍完 ✕ 退出，收件箱里会留一件「未命名物品」——这是签过字的行为，与这批无关。这次自验产生的那件我已经从详情页「删除」清掉（照片文件跟着扫走，`originals`/`thumbs` 各剩 3 份，与 2 件物品 + 1 张位置照片对得上）。**但「家」的位置照片被这次自验换成了我拍的一张暗面，这一张得请用户自己重选**——不是我该替他决定的内容。

**照片墙空态那句文案故意没改**：「还没有物品。点右下角的按钮选第一张照片。」现在点完会先问一句来源，但用户的最终动作仍然是"给第一张照片找个来源"，而这句话被两个测试类共 7 处断言着。改它是"文案洁癖 + 连带改 7 处签过字的断言"，收益不抵成本；真要到"取景级"体验时（CameraX 那批）再一起改。

**三条工具事实。** ① **带设备路径的 adb 参数全都要 `MSYS_NO_PATHCONV=1`，不止 `screencap`**：`adb shell uiautomator dump /sdcard/w.xml` 被 Git Bash 改写成 `C:/Program Files/Git/sdcard/w.xml`，而 dump 仍然打印 "UI hierchary dumped to: /sdcard/w.xml"——**报告成功、文件不在**，比直接报错更难查。② 这台机器的 shell 里 `adb` 不在 PATH，要用 `D:/Lib/Android/Sdk/platform-tools/adb.exe`。③ HyperOS 的 `ACTION_IMAGE_CAPTURE` 是"拍完要你确认"的那一派：**没有「完成」这一步就没有结果**，写自验清单时必须算作一步，否则看起来就是"按了快门但 APP 什么都没发生"。

**收尾对账里有一条我解释不了的差，已当面问用户，但没有他确认之前不许写死归因。** 上一轮（批次 AN 之后，2026-10-04 深夜）我记的是机内 4 条 item；2026-10-05 03:39 再取 db+wal 读回来只剩 2 条（`笔记本电脑测试` CONFIRMED、`按摩球` DRAFT），「手机指环扣123」「马克杯」不在了。能给的旁证有两条方向一致：`files/ai-trace` 在 AP 时段只有 03:30 **一条**，也就是一次录入，正好对上本批自验"创建一件、又从详情页删掉它"；`item_photo` 3 行与 originals/thumbs 各 3 张一一对上、零孤儿，说明那两次的消失是**走应用的删除**（文件按引用计数扫掉了），不是库文件被换过。用户此前确实自己清理过机内数据（「骨传导耳机」那次也不是我删的）。**但本批的操作记录里没有那两件**，所以这条既不能写成"用户自己清的"，也不能写成我删的——挂成一个待他回答的问题。**（更正，两条一起撤销这条悬念：① 这两个端点的读数都出自后来被证伪的读数法，"4 条 → 2 条"这个差本身可能就是读出来的，不是一个需要解释的现象；② 用户自己答了——他在 2026-10-05 报告确认页两条时明写「现在 app 中只保留了一条记录」，他一直在自己清机内数据。所以这条不再挂待答，也别再当成"库被换过"的证据。）**

**残留清理**：设备 `/sdcard/w.xml`、`/sdcard/c.xml`、`/sdcard/shot1.png` 已删；本机这次取回的 db+wal 副本、界面 dump、截图**一份没留**；`cache/camera-shots` 空；未命名物品已删。**Room schema 一字未动**，`DB_SCHEMA_VERSION` 仍是 1；权限仍然只有 INTERNET + POST_NOTIFICATIONS。

批次 AQ（2026-10-05，排队项 Q3 的正版：CameraX 应用内取景，把批次 AP 那条系统相机路整个删掉）

**用户拍板的两条决定了这批的形状**：① 取景屏**取代并删掉** AP 那条 `ACTION_IMAGE_CAPTURE`，不留"应用内 + 系统相机"双入口；② 同一块取景屏**同时覆盖物品录入与位置照片**两个入口，不各做一块。四片各自可独立回归：切片1 依赖 + `CameraShutter` 接缝 + 三态门禁（`4fc1655`）、切片2 取景屏 UI + 权限出口（`b9342fc`）、切片3 外壳接线 + CameraX 真实现 + 删掉旧路（`c0f2541`）、切片4 全量回归 + 变异验牙 + 真机自验 + 文档记忆（本片）。

**依赖是这批的可行性闸门，所以第一件事是让它先过秤**：`androidx.camera` 1.6.2 的 core/camera2/lifecycle/view 四个制品本机 gradle 缓存里原本没有，切片1 只跑 `assembleDebug` 把它们联网取到并编绿——取不到或编不过，整批计划就得退回"继续用系统相机"，而不是写完界面再发现底座没有。

**接缝的形状决定取景屏测不测得动。** `CameraShutter` 只有一个 `suspend capture(): ByteArray?`，接口里没有 CameraX 类型；`CameraScreen` 收到的是一台 `gate`、一个 `preview` 槽和四个回调（`onRequestPermission`/`onOpenSettings`/`onUseAlbum`/`onCancel`）加 `onShot`。所以取景屏那 9 例在 JVM 里全测得动：三态各给什么、快门在飞时按住第二下不许重复触发、拍不到那句「没拍到，再按一次，或者改用相册」而不是崩。反过来，**碰 CameraX 的那 110 行（`CameraXSource`）一行 JVM 用例都没有**——Robolectric 打不开相机，这条明写在它的 KDoc 上，由本批真机自验覆盖。**判据：把测不动的东西挤进一个文件、只留一个缝，剩下的才谈得上自动化；而不是让整屏都因为那一块而放弃覆盖。**

**三态门禁里那条次序是有理由的**：`CameraGate.of(granted, cameraFound)` 先判 `!cameraFound` 再判 `!granted`。换成反的，一台真没相机的机器会被引导去点「打开相机权限」——开了也没用。**"没相机"先于"没授权"，这条有两条用例分别钉住**：整条分支删掉红两条，只把次序换反红那一条次序用例（切片2 的 13 条逐条变异过，16 个变异没有一个漏网）。

**取景屏是叠层，不是替代，这点救了一次数据**：`viewfinderOpen` 把那一屏盖在 Scaffold 与录入页**之上**。底下那屏不能退出组合——录入页一被 dispose，`liveReviewVm` 就清空，"给这一件换一张照片"会变成"新建一件物品"（批次 X 那族问题的另一个入口）。因此 `CameraScreen` 自己带不透明背景：`NeedsPermission` 与 `Unavailable` 那两态底下没有预览面垫着，透明就等于把上一个屏的话冒充成自己的。

**删掉的东西比加上的更能说明这批做对了**：`CameraPhotoStore.kt`（59 行）、`CameraPhotoStoreTest.kt`（122 行）、`res/xml/file_paths.xml`、清单里的 `<provider>` 全部删除——**没有"要交给别人写的临时文件"就没有 FileProvider**。顺带解掉了批次 AP 记下的那条 Windows 死结（`FileProvider$SimplePathStrategy.belongsToRoot` 把分隔符硬编码成 `/`，Windows 上必抛），因为现在压根不产 content URI。清单换上来的是 `CAMERA` 一条权限 + `<uses-feature android:name="android.hardware.camera.any" android:required="false">`：后者让没相机的机器照样装得上，而取景屏自己退到「改用相册选」那条路。

**交付面一字未改，所以 AJ/AK 签过字的用例照原样绿**：取景屏交出去的还是一模一样的 JPEG 字节，`deliverPhoto` 之后录入页与位置页那两侧一行没改。方向由 `imageCapture.targetRotation` 写进 JPEG 的 EXIF，落库时接批次 AC 那条"照 EXIF 把像素烤正"的逻辑——取景屏不必自己转像素，两头各管一段且都已有测试。

测试 **39 类 / 462 例**，全量 2m1s 绿——判绿的两条都有：日志里 `BUILD SUCCESSFUL`，且 39 份结果 XML 的 mtime 全部落在这一轮（最旧与最新同为 10:56:18，起跑 10:54）。相对批次 AP（37 类 / 442 例）净增 2 类 20 例：三个新进类 `CameraScreenTest` 9 + `CameraGateTest` 4 + `CameraAccessTest` 4，`WheremyRootTest` 从 12 例整到 20 例（+8），`CameraWiringTest` 那 2 例就地换成清单的新事实（±0），减去随系统相机一起删除的 `CameraPhotoStoreTest` 5 例——类数 37 + 3 − 1 = 39，例数 442 + 9 + 4 + 4 + 8 − 5 = 462。

**十二次变异（外壳十条 + 清单两条）串行跑，11 条有牙、1 条真无牙。** 外壳：M1「取景屏里的『改用相册选』丢了去向」→ 红 `theViewfindersAlbumEscapeStillDeliversToTheLocation`；M2「相册逃逸不收取景屏」→ 红 `theViewfindersAlbumEscapeStillDeliversToTheItem`；M3「快门交棒给外壳时不收取景屏」→ 红 `aViewfinderShotOpensTheReviewFormForANewItem`；M4「快门复用 `closeViewfinder`」→ 红 `aViewfinderShotForALocationStaysOnTheLocationPage`；M5「进取景屏时不写去向」→ 红 2 条；M6「取消取景不归零 `photoTarget`」→ **全绿**；M7「位置路由那一格写死成假」与 M8「位置入口弹的弹窗为谁开着写死成物品」→ 各红 3 条，且咬到的是同三条（`aViewfinderShotForALocationStaysOnTheLocationPage`、`theViewfindersAlbumEscapeStillDeliversToTheLocation`、`aPhotoChosenFromTheLocationPageStaysOnTheLocationPage`）；M9「零字节当成拍到了」→ 红 `anUnreadableAlbumPhotoOpensNothingAndLeavesNoItemBehind`；M10「弹窗文案退回系统相机那套说法」→ 红 `theFabOffersBothACameraShotAndAnAlbumPhoto`。清单：M11「把 `camera.any` 装成必需」→ 红 `aDeviceWithoutACameraCanStillInstallTheApp`；M12「清单里根本没有 `CAMERA` 权限」→ 红 `cameraIsTheOnlyCameraPermissionTheAppAsksFor`。

**M6 那条无牙不是我把因果写进了注释——这次注释是反着写的。** `deliverPhoto` 上方那段明写着归零"是为了让这一格的读法保持字面意思——非 NONE 就是有一张还在飞"，并**自己否认**路由靠它（"但『下一次点 + 的图不会被上一张借走』靠的不是这里"，接着点名 `openViewfinder`/`startAlbum` 在发起请求时重写那一格）。所以批次 AP 那条判据（注释里的"这样做是为了 X"必须能被一次变异证伪）在这里是**被满足**的：无牙的恰好是注释声称不存在的那个因果。**那两行归零留着，作用是读法卫生，不是正确性**——这句是跑完 M6 才敢写的，不是先写下再去验的。

**这批有一条判决是我自己造的噪声，不是代码的**：第一次跑套件时我并发起了第二份 gradle（误信一条过早的 "failed exit 1" 通知，其实第一份还在跑），两份脚本同时改同一棵树，那一份的输出里 M9 成了"无牙"、M11/M12 成了 BUILDFAIL，树上还留着一个没还原的 M8。串行重跑后 M9 红 1 条、M11/M12 各红 1 条。**判据：变异判决的可信度来自"一棵树在同一时刻只有一个写者"；拿到"无牙"或"构建失败"这种出乎意料的判决，先怀疑并发写者，再怀疑测试。** 还原是否干净也不靠 `git status`（`core.autocrlf` 会让文本模式写回制造假 "M"），靠 `git show HEAD:<file>` 与 `cmp` 逐字节比。

**真机自验（9aa4b43e，装机包就是本批构建）——十条，每条都有读回来的证据，不只是"界面没报错"。** ① 照片墙右下角那颗 + → 「这张照片从哪来？」弹窗在。② 点「拍照」→ 焦点窗变成系统的 CAMERA 三选项弹窗，这一条**证明"进门即申请权限"真的发生**（`LaunchedEffect(gate)` 那一句）。③ 授权后取景屏进 `Ready`：取消 / 拍这张 / 改用相册选三颗都在。④ **预览看着是黑的，但不是渲染 bug**：`dumpsys media.camera` 显示本进程 `CONNECT` 了 device 0、SurfaceView 有 BLAST consumer 在收帧，快门落盘的 JPEG 也是黑的，而喂给 AI 的留痕里模型原话写着 "The image is essentially black, no recognizable objects"——三方一致，是**镜头被我的手指挡着**。这条要记的不是结论而是判法：**"界面黑屏"有三种可能（没绑相机 / 没画出来 / 拍到的是黑的），只有落盘字节能把它切开。** ⑤ 按「拍这张」→ 取景屏收起 → 录入页打开 → `files/photos` 的 originals/thumbs 各多一对文件。⑥ 那次 AI 的留痕是 `status=200`、`outcome=NOTHING_FOUND`（黑图，符合预期），且 `Prompt`/`response` 两段都是真原文。⑦ `KEYCODE_BACK` 关取景屏，回到原来那一屏。⑧ **位置那条**：位置页 →「抽屉」→「给这个位置选一张照片」→ 弹出的是**同一个**来源弹窗 →「拍照」→ 快门 → **停在位置页**没开录入页，`item_photo` 多一条 `ownerType='location'` 的行。⑨ **取景屏里的「改用相册选」那条逃逸**：点它 → 取景屏收起、系统选图器打开 → 选相册里那张眼镜 → 录入页打开且 AI 把名称/类别/颜色/标签都填上了。⑩ 「移除照片」→ snackbar「已移除这里的照片」，而 `7a39b3e9…` 那对文件在 originals 与 thumbs **两边一起消失**——批次 AL 的引用计数在取景屏这条新链路上也兑现了。

**两条没在真机验到，别当验过**：① `Viewfinder.Unavailable`（这台机有相机，构造不出"没相机"）；② 「打开相机权限」那颗钮跳系统设置页——MIUI/HyperOS 会把 CAMERA 授权做成一次性弹窗，点了就给了，走不到那条钮。两者都只有 JVM 覆盖（回调被断言"调用了一次 + Intent 的形状对"）。

**一条要当面告知的现状**：自验第 ② 条那一步，我在他的机器上把 CAMERA 权限点成了「仅在使用中允许」。这是本批新增的唯一运行时权限；他没有别的期望的话不用动，但它不再等于装机前的权限集合。

**有意不做**：前后摄切换、手动对焦、快门动画、拍照音效（Plan 2 那条"取景体验"只做到"能拍、能收、能退到相册"）。理由是这应用的取景只服务于"拍一件东西给 AI 看"，一个用户用不到这些；加一颗翻转按钮就要加一条"哪个摄像头"的状态，而那条状态目前没有测试能证明它对。

**这批在自己机器上掀出一条不属于本批的真缺陷，下一批（AR）的靶子**：AI 什么都没认出来 + 用户按 ✕（或弹窗里选「不保存，直接离开」）→ 库里留一具 `name=''` 的 DRAFT，收件箱里是一张「未命名物品」的卡。根因链三段都在别人家里：`CaptureFlow.kt:58` 的 `items.newDraft(jpeg)` 按 spec §6.1 先落库、`CaptureFlow.kt:81` 的 `if (first != null) items.applyAnalysis(itemId, first)` 让"没认出来"那一支永远没有名字、`ReviewViewModel.kt:404` 的 `closeWithoutAsking()` 只把 `finished` 置上不动库。**为什么 462 例抓不到**：`exitWithoutSavingClosesAndKeepsOnlyWhatWasAlreadyOnDisk` 钉的是 AI **认出来**那一支（断言 `name == "深灰色羊毛外套"`），空壳那一支一条测试都没有——签过字的"库保持原样"被当成了普适结论。修法方向：静默退出那一刻，若那一行既没有名字也没有用户手打内容就把他删掉，且**必须先 `analysis?.cancel()`**（否则迟到的 `applyAnalysis` 会往已删的行写回去），删除走既有 `ItemRepository.delete`，AJ 多件时其余槽位共用第 1 件的照片文件由那条引用计数保住。**这条改动动的是"库保持原样"这句话，得先跟用户确认再动手。**

**三条工具事实。** ① `subprocess.run(list, shell=True)` 在 Windows 上会把列表拼成一条命令串，**我写在参数里的引号于是成了字面量**：`--tests '"com.…Test"'` 让 gradle 收到带引号的过滤器 → 一条用例都匹配不到 → `No tests found for given includes` → task FAILED 且**不写结果 XML**，在我的脚本里表现为"结果文件没刷新（编译失败）"。**判据：报"构建失败"时先把 gradle 原文读全，别信自己那行摘要。** ② `capture_output=True, text=True` 走的是 locale 编码（这台机 GBK），gradle 输出里的中文与 0xAA 字节直接让读线程抛 `UnicodeDecodeError` 崩掉整个套件；要么 `encoding="utf-8", errors="replace"`，要么 `PYTHONUTF8=1`。③ **变异套件每改一次源码都要重编，`classes.jar` 被守护进程攥着的 Windows 老毛病会在这里复现**——跑之前先 `gradle --stop`，而且**绝不再并发第二个 gradle**（上一轮我并发跑了一次，它没崩，而是**给了三条错判决**——见上面变异那段）。另外验牙的还原检查改用"写回原文后字节比对"，比再烧一次 gradle 更准也更省。

**残留清理**：设备 `/sdcard/aq.xml`（本批 dump）与 `/sdcard/shot1.png`（AP 那批截图，**上一批文档声称删了其实没删掉**，这次核对 `ls` 才发现）都已删；`cache/camera-shots` 那个空目录连着删掉——它是已删除的 `CameraPhotoStore` 留下的，代码里再没有一处创建它。本机取回的 db/wal 副本全删。收尾对账（db+wal 双取）：**2 件物品**（`按摩球` DRAFT、`笔记本电脑测试` CONFIRMED）、**3 条 `item_photo` 行**（2 件物品 + 1 张位置照片，位置照片是「家」上他 08:46 自己重选的那张 `e6d66505`，没被动过）对 originals/thumbs 各 3 张，一一对上、零孤儿；抽屉/橱柜两节点无照片行（我测试那张已从 UI 移除）；`files/ai-trace` 4 条（AN 23:10、AP 03:30、本批 10:12 黑图、10:21 眼镜），上限 10 所以都还在。**（更正：这一段的 db 侧四个数出自后来被证伪的读数法，`ls | wc -l` 还会把文件数少报——见批次 AT 的更正段。这里点名的那四个时间戳是真发生过的四次调用，但"总共就这 4 条"不作数。）****Room schema 一字未动**，`DB_SCHEMA_VERSION` 仍是 1；权限集合现在是 INTERNET + POST_NOTIFICATIONS + **CAMERA**（本批新增，唯一一条）。

批次 AR（2026-10-05，AQ 结尾自己掀出来的那条真缺陷的正版：AI 什么都没认出来 + 用户按 ✕，不再留一具「未命名物品」的空壳）

**动的是"库保持原样"这句话，不是"先落库"这句话**：`CaptureFlow.kt:58` 那句 `items.newDraft(jpeg)` 原样留着。把落库挪到分析之后看着更干净，但批次 X 修掉的正是"保存比 AI 快"——用户按保存时那一行必须已经存在，否则 confirm 无处可写，未命名物品会换一种形式复发。所以修法落在退出那一头：`ReviewViewModel.kt:420` 的 `closeWithoutAsking()` 从"只置 `finished`"改成"先把空壳摘掉再置 `finished`"。

**空壳的定义是四条合取，每条各自被一次变异证伪**：`s.itemId != null && !s.saved && s.draft.name.isBlank() && s.touched.isEmpty()`（`ReviewViewModel.kt:422`）。三条判据各删掉一条 → 各红一条，且红的是三条**不同**的用例：不看「已保存」红 `editingAnExistingUnnamedItemAndLeavingDoesNotDeleteIt`（他编辑一件本来就无名的旧物品，离开会把它删掉——这条最狠，因为丢的是他原来就在的东西）；不看「用户手打过」红 `aTypedButUnsavedDraftIsNotAThrowawayShell`；不看「名字还是空的」红 `exitingWithAnUnsavedSlotAsksAndNamesTheCount`。**判据：一组条件的价值不在于它让用例绿，而在于拆掉每一条都有专门一条用例等着红。**

**取消在飞的采集排在删除之前**（`analysis?.cancel()` 在 `shells.forEach { items.delete(it) }` 上方）：`newDraft` 之前物品行还不存在，不取消就留下一条"删完了行才落库"的幽灵记录，正是批次 X 那一族。ARM1（删掉这一句）红 `exitingBeforeTheItemLandsDoesNotLetItLandAfterwards`。删除走既有 `ItemRepository.delete`，于是 AJ 那条引用计数免费兑现：ARM5（认出了空壳但不删）红的三条里包含 `deletingTheShellKeepsThePhotoFilesTheSavedSisterItemShares`——同批另一件在共用那两个字节时，它们不跟着走。

**七次变异串行跑（本轮重跑取第一手判决，锚点全部唯一命中并打印行号），6 条有牙、1 条真无牙**：ARM1「退出时不取消在飞的采集」（锚点 423 行）→ 红 1 条；ARM2/ARM3/ARM4「空壳判据分别不看已保存 / 不看手打过 / 不看名字空」（各 422 行）→ 各红 1 条；ARM5「认出了空壳但不删」（431 行）→ 红 3 条（`thePageStaysOpenUntilTheShellIsActuallyGone`、`exitingAShellThatNeverGotANameThrowsItAway`、`deletingTheShellKeepsThePhotoFilesTheSavedSisterItemShares`）；ARM6「删完了也不关页」（433 行）→ 红 2 条；**ARM7「把 launch 体内两句调换：先写关页再删」→ SURVIVED**。

**ARM7 这条无牙要明写成"测不到的那一环"**：`InlineTestScheduler` + `UnconfinedTestDispatcher` 下 launch 体一口气跑到底，两步之间没有可观测的中间态——先关后删与先删后关，在所有断言被求值的那一刻是同一个世界。ARM6 之所以有牙是因为"压根不关页"是个稳定终态，而"关早了"是瞬态。**这条次序的真实代价只有真机能看见**（删除失败时用户已经离场，屏幕上是一张消失的页 + 库里一张「未命名物品」），所以它由下面的真机证据兜，而不是由一条假绿的 JVM 用例兜。注释里那句"关页排在删除之后：这一格是'用户看得见页面关了'的唯一凭据，没删成就说关了是撒谎"是**意图**声明，不是可测断言——这一点比注释本身更值得记。

测试 **39 类 / 468 例**，全量绿（`BUILD SUCCESSFUL` + 39 份结果 XML 全部本轮新写）。相对批次 AQ（39 类 / 462 例）**类数不变、例数 +6**：6 条全部落在 `ReviewViewModelTest` 就地新增（`exitingAShellThatNeverGotANameThrowsItAway`、`exitingBeforeTheItemLandsDoesNotLetItLandAfterwards`、`thePageStaysOpenUntilTheShellIsActuallyGone`、`deletingTheShellKeepsThePhotoFilesTheSavedSisterItemShares`、`aTypedButUnsavedDraftIsNotAThrowawayShell`、`editingAnExistingUnnamedItemAndLeavingDoesNotDeleteIt`），462 + 6 = 468。**首条 RED 是当场看到的**：那 6 条在实现之前红的都是"库里的行还在 / `finished` 没置"，不是编译错。

批次 AS（2026-10-05，他在真机上连着报的确认页照片两条：一条根因在状态，一条根因在画布）

**#1「初始不显示图片，要等 AI 处理完才显示」的根因不是渲染时机，是状态里少写了一个字段**：确认页那一格读的是 `state.thumbPath`，而它原先只在 `apply()`（分析回来）与 `load()`（编辑已有物品）两处写。所以 AI 那十几秒里那一格压根没进组合，不是"画了但画错"。修法顺着数据走：`CaptureFlow.capture` 与 `replacePhoto` 各带一个默认空实现的 `onDraftSaved: suspend (String) -> Unit`，在 `newDraft` / `swapPhoto` 之后、`analyzeInto` 之前调一次（`CaptureFlow.kt:59`、`CaptureFlow.kt:75`）；`ReviewViewModel.kt:112` 的 `bindPhoto(id)` 在这一刻同时写 `itemId`、`draft.id`、`thumbPath`。**用户要的"先显示图片，同时也开始 AI 处理"于是成立，而且不是靠提前渲染假数据**。

**`draft.id` 必须跟着走，不是顺手补字段**：分析还在飞的时候按保存，confirm 用的就是 `draft.id`；只补显示路径而 id 还是空串，就往另一行写——那是批次 X 那一族问题的又一个入口。ASM2（把 `capture.capture(jpeg) { bindPhoto(it) }` 的回调摘掉）一次红 5 条，其中 `saveWhileAnalysisIsInFlightConfirmsTheItemThatWasCreated` 与 `keepForLaterWhileAnalysisIsInFlightKeepsWhatWasTyped` 咬的就是 id 这一格，说明这一处改动同时被"图要早"和"保存不能写错行"两套用例看着。

**这一改把 AR 变成实话**：以前"目测不对就中止"没有意义，因为用户在 AI 结束前根本看不见自己拍了什么；现在第一帧就能看见，按 ✕ 走 AR 那条路（取消在飞的采集 + 空壳连照片一起摘掉）。两条合起来才是他要的效果，任何一条单独上线都会留个半成品。

**#2「AI 完成后显示的图片没有全部显示」的根因是固定框 + `ContentScale.Crop`**：那一格是 `fillMaxWidth().height(180.dp)`，这台机上 992×495 px（约 2:1）。竖拍那张存下来是 192×256，Crop 居中裁切后只露约 **37 %** 的高度——物品不在正中时看不出缺了什么。照片墙与详情页用的是完整缩略图与烤正后的原图，所以他看到的差别精确地只在这一屏。改成 `ContentScale.Fit`（`ReviewScreen.kt:154`），**框高保留**：图还没解码时那一格会塌成 0、表单跳一下。

**量裁切这件事不能只信自己写的判据**：自写 PNG 解码（本机无 PIL）逐列扫描非底色像素。第一次判"铺满框宽 = 被裁"，与肉眼看到的截图直接矛盾——根因是**这台机页面底色带噪点纹理**，逐像素相等会把每一列都判成"有内容"。加 `TOL = 28` 容差后实测 painted 区间 355–726（宽 **371 px**），两侧各留 311 px 底色；而 495 × (192/256) = 371，与 `Fit` 的预测**逐位对上**。**判据：能看一眼图就别只信判据；但看过图之后仍要把数字算出来，否则下次改动无从比对。**

**五次变异：切片1 四条全 KILLED，切片2 那条真无牙**。锚点均唯一命中并打印行号：ASM1「`bindPhoto` 只绑 id 不绑显示路径」（`ReviewViewModel.kt:114`）→ 红 3 条（`thePhotoIsBoundTheMomentTheBytesLandNotWhenAnalysisReturns`、`swappingThePhotoBindsTheNewBytesBeforeAnalysisReturns`、`thePhotoIsOnScreenWhileTheAnalysisIsStillFlying`）；ASM2「落盘那一刻不再回调确认页」（`ReviewViewModel.kt:98`）→ 红 5 条（前述 3 条 + `keepForLaterWhileAnalysisIsInFlightKeepsWhatWasTyped` + `saveWhileAnalysisIsInFlightConfirmsTheItemThatWasCreated`）；ASM3「换照片时不通知新字节已落盘」（`CaptureFlow.kt:74`）→ 红 `replacePhotoReportsTheMomentTheNewBytesLand`、`swappingThePhotoBindsTheNewBytesBeforeAnalysisReturns`；ASM4「首次录入时不通知物品行已落盘」（`CaptureFlow.kt:58`）→ 红 6 条（含 `captureSavesTheDraftRowBeforeCallingTheModel`）。**ASM5「`ContentScale.Fit` 换回 `Crop`」（`ReviewScreen.kt:154`）→ SURVIVED**。

**ASM5 无牙不是漏了用例，是这一维在 JVM 里测不到**：Compose 语义树里没有 `contentScale` 这一项，任何 `onNodeWithContentDescription("物品照片")` 断言都区分不出 Fit 与 Crop。所以**这一屏的裁切是全仓唯一一条"改错了不会有测试红"的视觉参数，唯一判据是真机截图量像素**——下轮再动那一格，验收步骤仍然是截图 + 算等比宽，不是跑套件。把 `Fit` 换成 `Crop` 的那次改动如果有测试能红，那测试一定是假的。

测试 **39 类 / 472 例**，全量绿（`BUILD SUCCESSFUL in 2m 3s`，13:29:19 起跑，39 份结果 XML 的 mtime 全为 13:31:21，无一份陈旧）。相对批次 AR（468 例）**类数不变、例数 +4**：`CaptureFlowTest` +1（`replacePhotoReportsTheMomentTheNewBytesLand`，16→17）、`ReviewViewModelTest` +2（`thePhotoIsBoundTheMomentTheBytesLandNotWhenAnalysisReturns`、`swappingThePhotoBindsTheNewBytesBeforeAnalysisReturns`，42→44）、`ReviewScreenTest` +1（`thePhotoIsOnScreenWhileTheAnalysisIsStillFlying`，25→26）；468 + 4 = 472。`ReviewScreenTest` 那一条是**双断言**的：分析还在飞时 `物品照片` 节点已在，且分析回来后**同一颗节点还在**——只写前者的话，一条"节点出现过又被 apply 冲掉"的实现也能绿。

**真机自验（装机包 = 本批构建，9aa4b43e）三条，每条都有读回来的证据**：① **AS-1**：快门后内联连拍（一次 `screencap` 约 0.4 s）第 1 帧里三件事同时成立——`物品照片` 节点在、「AI 正在看照片，可以先自己填」在、名称仍为空。**"图不等 AI"必须靠"AI 还在飞"这一格同时成立来证明**，否则看到的只是分析完成之后的画面。② **AS-2**：同一帧量得 painted 宽 371 px、两侧各 311 px 底色（见上面的算术）。③ **AR**：分析进行中（快门后 1.1 s）按 ✕ → 库里 `item` 3→3、`item_photo` 4→4、originals/thumbs 各 4 **均未增**，而本轮新增的留痕 `1791177024002-000.json` 只有 **1716 B**（同批其余 3 条 3385–3706 B）——**文件小到这个份数，证明请求确实发起过又没走完**，光看"库里没长东西"分不出"没发"和"发了又中止"。**（更正：这一条的四个"均未增"不作数。**那组 db/文件计数出自后来被证伪的读数法——`run-as cat` 反复覆盖同名本地文件 + 上一轮 sqlite 连接不关，于是能读出"库里没长东西"的假象（详见批次 AT 的更正段）。**这条分支（分析还在飞就按 ✕）此后没有用可信读数重跑过**，所以它现在只有 `exitingBeforeTheItemLandsDoesNotLetItLandAfterwards` 一条 JVM 覆盖，真机那一格请当成未验。**留痕字节那半仍然成立**——它读的是文件大小，不经过那颗 sqlite 连接。）

**两条没在真机验到，别当验过**：① AR 的另一支（分析**回来**但什么都没认出来，再按 ✕ → 空壳被摘）只有 `exitingAShellThatNeverGotANameThrowsItAway` 的 JVM 覆盖；要复现得让模型对一张图返回空，AQ 记过的那张黑图正好是这一支，但那轮没走"按 ✕"这一步。② AS 的 `replacePhoto`（确认页「换一张」）在真机上没走 UI，只由 ASM3 那条数据层用例 + 两条 VM 用例钉住。

**有意不做**：确认页照片不加"点开看大图"（Fit 之后这一屏已经完整，要看细节走详情页那张本来就完整）；不把 `Crop` 留成一个可切换选项（没有第二种用户意图要满足）；不动照片墙与详情页的裁切（那两处本来就是完整的，改它是无病投医）；也不给"AI 正在看"期间加骨架图（图已经上来了，占位反而盖住要看的）。

**六条工具事实。** ① **后台 Bash 任务里没有 version-fox 的 PATH**：`gradle` 找不到 → 命令以 **exit 0 + 空输出**结束，形同"跑过了"，而 `test-results` 里只剩上一轮的陈旧 XML（那次还带着 6 条失败）。**判据：gradle 只在前台跑，判绿自己数 XML 并看 mtime，绝不信 exit code。** ② Windows 上 `subprocess.run(["gradle", ...])` 抛 `FileNotFoundError WinError 2`（.bat shim 不是可执行文件），必须 `shell=True`；而它一旦发生在写入变异**之后**，源码就带着变异躺在树上——本轮首跑就是这么崩的，靠手工 python 还原 + `git diff --stat` 核对才清干净。变异脚本必须 `try/finally` 还原，且还原后按字节比对，不等则 NO-VERDICT。③ **变异跑崩溃遗留的 `GradleWorkerMain` 会攥住 `app/build/test-results/testDebugUnitTest/binary/output.bin`**，下一次 `testDebugUnitTest` 以 `java.io.IOException: Unable to delete directory` 失败——这不是测试红，是文件锁。用 PowerShell `Get-CimInstance Win32_Process -Filter "name='java.exe'"` + 命令行正则区分 DAEMON/WORKER，再 `gradle --stop`（本轮 2 Daemons stopped）。④ **真机截图量裁切要留容差**：这台机页面底色带噪点纹理，逐像素相等判据会把每一列都判成"有内容"。⑤ **`adb` 不保证在每条 Bash 调用的 PATH 里**（同一会话里前一次能用、后一次 `command not found`），工具路径一律写绝对路径，别拿上一条命令的成功当下一条的前提。⑥ **内联连拍能抓 <1.5 s 的窗口**（快门后立刻循环 `screencap` + `uiautomator dump`），这是唯一能在 JVM 之外验"图不等 AI"的手段；而 **AI 留痕的文件大小可以证明"尝试发起过"**，这是把"没发生"与"发生了但被中止"分开的现成证据。

**残留清理**：本轮建的 2 条测试 DRAFT 从「待归类」页删除（那里只有 DRAFT，不会误删他 CONFIRMED 的 `按摩球`）**（更正：这句话是假的，而且代价是真的——2026-10-05 16:34 我照这句话写脚本收尾，删掉的正是他那件 CONFIRMED 的 `按摩球`。「待归类」的谓词是 `locationId IS NULL OR status = 'DRAFT'`（`Daos.kt:34`），它列的东西本来就不保证是 DRAFT；更根本的是那句"不会误删"从来没有任何一道核对在看着它。判决与两条新规矩见批次 AT 段的事故段。**），收件箱显示「收件箱是空的，都已归类。」——**这句屏上的话在批次 AT 已经改成「没有待归类的物品，全都放好了。」，以后照这段写验收手册要按新句子抄**。**（更正：下面这段的对账数字全部出自后来被证伪的读数法，见批次 AT 的更正段——`ls | wc -l` 在 toybox 下多列排版还会把文件数少报，所以"1 件物品 / 2 条行 / 各 2 张 / 留痕 9 条"这四个数一个都不作数；本机当前的可信基线写在 AT 段。）**收尾对账（db+wal 双取）：**1 件物品**（`按摩球` CONFIRMED）、**2 条 `item_photo` 行**（1 件物品 + 1 张位置照片）对 originals/thumbs 各 2 张，一一对上、零孤儿；`files/ai-trace` 9 条（上限 10）；`/sdcard` 本批 dump 与截图计数 0；本机 `C:\tmp` 下的变异脚本、db/wal 副本与自写截图量裁脚本收尾删除。**Room schema 一字未动**，`DB_SCHEMA_VERSION` 仍是 1；权限集合仍是 INTERNET + POST_NOTIFICATIONS + CAMERA。

批次 AT（2026-10-05，退出确认框分成"会留下"与"不会留下"两笔账，外加界面里最后一批「收件箱」旧名）

**起因是一句半真半假的话**：`notStored > 0` 那一支只说「还有 N 件没保存，离开后它们不会留下」，可同一批里先落库、AI 已认出名字、用户没点保存的那件**会**留下——它以 DRAFT 待在「待归类」。用户听完"不会留下"就走，结果待归类里多出一件他以为已经丢掉的东西。**判据：确认框里数的必须是"按下这个按钮之后库里的差别"，不是"这一屏上有几个槽位没保存"。**

**一条规矩只写在一个地方**：AR 摘空壳用的那四条合取原先抄在 `closeWithoutAsking()` 里，AT 把它提成 `ReviewSlot.isThrowawayShell`（`ReviewViewModel.kt:45`），现在弹窗的 `leftBehind`（`:77`）与删除用的 `shells`（`:444`）同读这一条。**为什么必须合一处**：两边各抄一份，弹窗就能承诺一件当场会被删掉的物品——`leftBehind` 若不排除空壳，退出框会对着一具马上要被摘掉的壳说"它会以草稿留在「待归类」"。

**两笔账 + 一次改名**：`notStored` = 只活在内存里的那几件（离开就不留），`leftBehind` = 会留下那几件（落了库、没保存、又不是空壳）。同时把界面里最后一批「收件箱」旧名换掉：`LocationPicker` 根项改成「暂不归类，先进「待归类」」、待归类空态改成「没有待归类的物品，全都放好了。」、确认页位置行改成「未归类」。树里其余「收件箱」只留在代码注释里（`grep 收件箱 app/src/main` 现在 7 条命中，全在注释，界面字符串 0 条）。

**九次变异串行跑（锚点全部唯一命中并打印行号），9 条全有牙**：ATM1「`leftBehind` 不再排除空壳」→ 红 2 条（`theExitQuestionPromisesNothingWhenOnlyAShellWasStored`、`aShellIsNotCountedAmongWhatStays`）；ATM2「`leftBehind` 不再排除已保存」→ 红 `aSavedItemIsNotCountedAmongWhatStays`；ATM3「弹窗删掉后半句（退回只说不会留下）」→ 红 `theExitQuestionSaysTheStoredItemStaysBehind`；ATM4「后半句不看人数、恒说」→ 红 `theExitQuestionPromisesNothingWhenOnlyAShellWasStored`；ATM5「空壳判据不看已保存」→ 红 `editingAnExistingUnnamedItemAndLeavingDoesNotDeleteIt`（他编辑一件本来就无名的旧物品、原样退出会把它删掉——这批最狠的一条，丢的是他原来就在的东西）；ATM6「空壳判据不看名字空」→ 红 4 条（`theExitQuestionCountsWhatStaysAndWhatGoes`、`aSavedItemIsNotCountedAmongWhatStays`、`exitingWithAnUnsavedSlotAsksAndNamesTheCount`、`theExitQuestionSaysTheStoredItemStaysBehind`）；ATM7「待归类空态退回「收件箱是空的，都已归类。」」→ 红 3 条（`WheremyRootTest.fourTabsLeadToFourDifferentScreens`、`emptyInboxSaysEverythingIsFiled`、`movingADraftByCardConfirmsItSoTheInboxEmpties`）；ATM8「位置行标签退回「未归类（进收件箱）」」→ 红 `locationRowNamesThePlaceOnceOneIsPicked`；ATM9「挑选器根项退回「暂不归类，先进收件箱」」→ 红 `callerThatPassesNoLabelGetsThePendingListWording`。

**ATM9 那条用例是补出来的，因为原本没有一条用例看着那颗挑选器的默认值**：上层那些用例都把默认文案抄进了自己的参数（断的是自己写的那句），而确认页调用它时恰恰**没传** `rootLabel`。第二重事实是这一处**没法从确认页那一侧验**：那里传了 `onCreate`，对话框里的文本框让窗口在 Robolectric 下永远等不到 idle——去掉显式 `waitForIdle()` 之后仍然红在 `assertExists`（60 s 抛 `ComposeTimeoutException`），所以判据只能落在 `LocationPickerDialogTest` 里直接渲染默认值。**判据：带默认参数的可复用组件，默认值本身要有一条用例；调用方"没传参数"这一格是覆盖盲区，不是省事。**

测试 **39 类 / 478 例**，全量绿。相对批次 AS（472 例）**类数不变、例数 +6**：`ReviewViewModelTest` +3（`theExitQuestionCountsWhatStaysAndWhatGoes`、`aShellIsNotCountedAmongWhatStays`、`aSavedItemIsNotCountedAmongWhatStays`，44→47）、`ReviewScreenTest` +2（`theExitQuestionSaysTheStoredItemStaysBehind`、`theExitQuestionPromisesNothingWhenOnlyAShellWasStored`，26→28）、`LocationPickerDialogTest` +1（`callerThatPassesNoLabelGetsThePendingListWording`，3→4）；472 + 6 = 478。

**真机判决（装机包 = 本批构建，9aa4b43e）**：一批两件、其中一件点了保存，按 ✕ → 屏上三句逐字是「还有物品没保存，确定离开？」「还有 1 件没保存，离开后它们不会留下。」「另外 1 件已经在库里，会以草稿留在「待归类」。」；按下「不保存，直接离开」之后用可信读数取回：`骨传导耳机` 仍在库里、`status = DRAFT`、`locationId = NULL`，它的 `item_photo` 行与 originals/thumbs 那两个字节都在。**弹窗说的"会留下"与库里真实发生的差别一字对齐**——这才是这一批要的判决，不是"看着没坏"。

**一条没在真机验到，别当验过**：待归类那页的新空态文案「没有待归类的物品，全都放好了。」本轮在机上没看到（那一页当时有内容，空态走不到），只有 `emptyInboxSaysEverythingIsFiled` 与 `WheremyRootTest` 那两条 JVM 覆盖。

**这批在自己机器上出了一起事故，而事故的依据就是我文档里的一句话**：上一段（AS 的「残留清理」）写着"从「待归类」页删除测试 DRAFT——那里只有 DRAFT，不会误删他 CONFIRMED 的 `按摩球`"。16:34 我照这句话写脚本收尾，`python -c "from at_clean import tap_xy"` **让整段模块级脚本重跑**，而脚本按标签匹配点**第一颗**「操作」→ **删掉的正是他那件 `按摩球`**（CONFIRMED，位置 家·抽屉，7 个标签，一对照片文件）**的那一行——影响范围在下一段被可信读数改小了，读这段时把它当成"删了一行"而不是"物品没了"**。三条复盘：① 那句话的前提从代码上就不成立——「待归类」的谓词是 `locationId IS NULL OR status = 'DRAFT'`（`Daos.kt:34`），它列的东西不保证是 DRAFT；② 那句"不会误删"背后没有任何一道核对：脚本既没确认当前屏是哪一屏，也没确认点的那一格属于谁（按读回来的那一行自带位置算，它压根不该出现在「待归类」那一页，所以手势落点连屏幕都没对上）；③ **import 一个模块级脚本 = 重跑它全部写操作**。两条新规矩进设备记忆第 50、51 条：任何会动机上数据的脚本，动作必须在 `if __name__ == "__main__"` 守卫之下、绝不 import；按任何「删除」之前先用可信读数证明那一格是谁。取证快照与被删行全字段已落盘（`deleted_row.json`），恢复路径等他拍板，未点头之前不写机。

**事故的影响范围我在第一版报告里报错了，两句都要留下。** 收尾用可信读数重查机上现状（新目录 + 新连接 + 查完即关）：库里**仍有一件 `按摩球`**——`ad89c311…`，`CONFIRMED`，位置 家·抽屉，`createdAt` 与它那张原图的落盘时间同为 **14:27:31**，`material=硅胶`，照片 `c4f357ae`；把原图拉回本机看过，是他自己拍的那颗紫色凸点按摩球放在木纹面上。也就是说 **16:34 我删掉的是 11:45 那一条重复条目**（`0d14b5b4…`，createdAt 11:45:18 / updatedAt 11:48:53），丢的是那一行的字段版本（`material=橡胶`、`aliases=筋膜球,刺猬球,颗粒按摩球`、那一条的 description）与它那对照片字节，**不是"他那件物品没了"**。第一版报告依据的"库里只剩一件"出自那条已被证伪的旧读数法——**判据：报"丢了什么"之前，先把"现在还剩什么"读一遍**；同一件东西在库里有多行时，删掉一行不等于丢掉一件物品，而这只有可信读数分得开。恢复建议因此变了：丙（不动它，他机上那件是完整的）成了合理默认；乙（把 `deleted_row.json` 那行写回）会再造第二条重复条目；甲（请他重录）已不必要。`deleted_row.json` 仍留作凭据。

**#102 的更正收口**：AT 之前几批文档里那些"库里 N 件 / 照片行 N 条 / 留痕 N 条"的对账，凡出自 `run-as cat` 反复覆盖同名本地文件 + 上一轮 sqlite 连接不关那种读法，一律不作数（本地单变量实验坐实：同一份字节，旧连接凭空造出一具 `name=''` 的哑物品、并读出"按 ✕ 之后库里没长东西"的假象，关掉连接再读才正确）。可信做法与 AS 段那条假判决的更正已在两处标出；**判据：读数脚本的每一次调用都要新目录 + 新连接，"覆盖同名文件"这个动作本身就是 bug。**

**四条工具事实。** ① **判决脚本必须显式 `cwd=` 项目根，且只认本轮 XML 的 mtime**：不带 cwd 时 gradle 在临时目录里起、3–4 秒 exit=1 且不写 XML，而上一轮留下的绿 XML 会把本来有牙的四条变异（ATM1–ATM4）判成 SURVIVED。**拿到"无牙"或"构建失败"这种出乎意料的判决，先怀疑跑没跑，再怀疑测试。** ② toybox 的 `ls` 把短文件名排成多列，`| wc -l` 于是少报（`ai-trace` 实际 10 条被数成 4 条）——任何"没长东西"的方向都危险。③ **含文本框的对话框在 Robolectric 永不 idle**，见 ATM9 那段。④ **模块级脚本被 import 就重跑**，见事故那段。

批次 AU（2026-10-05，AT 收尾时自己揪出来的那句假话：编辑一件早就存好的物品，退出框管它叫草稿）

**怎么发现的**：AT 补完 `notStored > 0` 那一支，我回头读 `else` 那一支，发现它**无条件**说「照片和 AI 填的信息已经在库里了，状态还是草稿，会出现在「待归类」」。这一支的入口是他机器上确定走得通的那条：详情页「编辑」→ `WheremyRoot.kt:241 vm.load(editId)`，而 `load()` 把那一格标成 `saved = true`（`ReviewViewModel.kt:262`）。他改几个字再关掉，那件物品既不是草稿、也不会出现在「待归类」，状态仍是 CONFIRMED。**这句假话和 AT 刚修的那半句真话住在同一个函数里。**

**为什么 478 例抓不到**：AT 新增的两条界面用例都从 `notStored > 0` 那一支走（多件、其中一件没落库），`else` 那一支只有旧断言盯着"标题在不在"。**判据：界面文案没用例钉着就会一直说谎；而"钉住了"的标准不是"有用例断过这句话在"，是"把这句话改掉会有用例红"。** AUK1–AUK6 就是给这句话补的执行。

**第三笔账**：`alreadySaved = slots.count { it.itemId != null && it.saved }`（`ReviewViewModel.kt:84`）。它和 `leftBehind`（`:77`）是两笔账，混成一个数就等于对着已确认的物品说它还要去待归类：前者"离开不会改动它们"，后者"会以草稿留下"。标题跟着分两种说法——`leftBehind > 0` 说「还没保存，确定离开？」，否则说「改动还没保存，确定离开？」（编辑态里唯一诚实的标题）。

**两种情况同时成立时两句都说，不是二选一**：一批里既有他存好的、又有会留下的草稿时，`theExitQuestionInAMixedBatchNamesBothOutcomes` 要求两句都在，并用**精确匹配**断标题，所以标题退化成任一种也会被抓住。

**六次变异串行跑（本轮重跑取一手判决，锚点全部唯一命中），6 条全部 CAUGHT**：AUK1「`alreadySaved` 丢掉 `saved` 条件，把落了库都算成已存好」→ 红 2 条（`theExitQuestionInAMixedBatchNamesBothOutcomes`、VM 的 `theExitQuestionSeparatesWhatWasSavedFromWhatIsLeftBehind`）；AUK2「`alreadySaved` 取反」→ 红 `editingAStoredItemThenLeavingDoesNotCallItADraft`；AUK3「草稿那句不看条件、逢退就说」→ 红同一条（它断的是**这句不该在**）；AUK4「两句写成二选一」→ 红 `theExitQuestionInAMixedBatchNamesBothOutcomes`；AUK5「标题恒说「还没保存」」→ 红 `editingAStoredItemThenLeavingDoesNotCallItADraft`；AUK6「标题恒说「改动还没保存」」→ 红 2 条（mixed 那条 + 旧用例 `closingAfterTypingAsksAndKeepingWritesWhatWasTyped`）。**AUK3/AUK5 各只红一条，且红在"这句不该出现"上——负向断言才是这一批的牙**：只写"该说的在"，一条恒说的实现照样绿。

测试 **39 类 / 481 例**，全量绿。相对批次 AT（478 例）**类数不变、例数 +3**：`ReviewScreenTest` +2（`editingAStoredItemThenLeavingDoesNotCallItADraft`、`theExitQuestionInAMixedBatchNamesBothOutcomes`，28→30）、`ReviewViewModelTest` +1（`theExitQuestionSeparatesWhatWasSavedFromWhatIsLeftBehind`，47→48）；478 + 3 = 481。**首条 RED 是当场看到的**：界面那条先红在 `assertExists`「改动还没保存，确定离开？」（行为红，不是编译红）；VM 那条是新 API 的编译红，它的牙由 AUK1 补证。

**没在真机验到，别当验过**：`else` 那一支的三种说法本轮全部只有 JVM 覆盖。编辑态那一支（他机上唯一确定走得通的那条：从详情页「编辑」进、改一个字、按 ✕）我**没有上机走**——事故未决期间不往那台机器发手势；他要恢复路径拍完板，这一条顺手一起验（步骤：详情页「编辑」→ 备注里打一个字 → 按 ✕ → 看弹窗有没有说"状态还是草稿" → 按「不保存，直接离开」→ 那件物品状态与位置都不该变）。

**有意不做**：不在确认框里报"这一件在哪个位置、什么状态"的明细（一句"离开不会改动它们"已经够决定，加明细等于把详情页搬进弹窗）；不把「保存改动并留下」在多件时改成"保存全部"（那是替他扔芯片上其余件的字段，AT/AJ 的语义不动）；不把 `alreadySaved` 与 `leftBehind` 合并成一个"库里已经有 N 件"（合并就是这句假话的成因）。

**残留清理**：`C:\tmp\at` 里本轮的 dump/截图/db 副本与量裁脚本删掉，**留四样**——`deleted_row.json`（恢复凭据）、`f.db*` 与 `frozen_after_delete/`（事故取证快照）、`rd.py`（可信读数脚本，动作全在 `__main__` 守卫之下）、`mut_at.py` 与 9 份 ATM 日志（ATM1–ATM9 的一手判决凭据）；事故处置完再清。**三份一次性脚本删掉了，其中 `forensics.py` 与 `q_old.py` 没有 `__main__` 守卫**——正是新规矩禁止留下的那一类（import 它就重跑它的写操作，事故就是这么发生的），连同把 tap 手势带进设备的 `run.py` 一起删。设备 `/sdcard/at.xml` 已删。**Room schema 一字未动**，`DB_SCHEMA_VERSION` 仍是 1；权限集合仍是 INTERNET + POST_NOTIFICATIONS + CAMERA。

<!-- PLAN-1-COMPLETE -->
