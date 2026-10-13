package com.wheremy.app.ui.detail

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.testJpeg
import com.wheremy.app.ui.theme.WheremyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 详情页照片区（`FR-SRC-08` / `UI.md` §5.9）的界面规矩。
 *
 * 钉三件事：**能给已有物品加照片**（以前根本没有这个入口）、**每张都能单独删**、
 * **只剩一张时确认文案要换一句**（"这件物品就没有照片了"）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DetailScreenPhotoTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository
    private var addPhotoClicks = 0

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val store = PhotoStore(java.io.File(tmp.root, "photos"))
        items = ItemRepository(db, store)
        locations = LocationRepository(db, store)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun newItem(photos: Int): String = runBlocking {
        val id = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = id, name = "电钻"))
        repeat(photos - 1) { items.addPhotoToItem(id, testJpeg(width = 500, height = 500)) }
        id
    }

    private fun render(itemId: String): DetailViewModel {
        val vm = DetailViewModel(items, locations)
        compose.setContent {
            WheremyTheme(darkTheme = false) {
                DetailScreen(
                    itemId = itemId,
                    onBack = {},
                    onEdit = {},
                    onAddPhoto = { addPhotoClicks++ },
                    vm = vm,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) { vm.state.value.summary != null }
        return vm
    }

    @Test
    fun `给已有物品加照片：按钮在、点了走外壳那颗来源弹窗`() {
        val id = newItem(photos = 1)
        render(id)

        compose.onNodeWithTag("detail_add_photo").assertIsDisplayed().assertIsEnabled().performClick()

        assertEquals("点一下要请外壳弹来源（拍照/相册）", 1, addPhotoClicks)
    }

    @Test
    fun `每张照片都有一颗删掉这张`() {
        // 两张开屏都看得见（LazyRow 只组合可见的，所以这条用两张来数）
        val id = newItem(photos = 2)
        render(id)

        compose.onAllNodesWithContentDescription("删掉这张").assertCountEquals(2)
    }

    @Test
    fun `删掉这张只问一句，取消就什么都不动`() {
        val id = newItem(photos = 2)
        val vm = render(id)

        compose.onAllNodesWithContentDescription("删掉这张")[0].performClick()

        compose.onNodeWithText("删掉这张照片？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { vm.state.value.pendingRemove == null }
        assertEquals("两张都还在", 2, vm.state.value.photos.size)
    }

    @Test
    fun `只剩一张时说清删完就没有照片了`() {
        val id = newItem(photos = 1)
        render(id)

        compose.onAllNodesWithContentDescription("删掉这张")[0].performClick()

        compose.onNodeWithText("删掉这张照片？这件物品就没有照片了").assertIsDisplayed()
    }

    @Test
    fun `到 9 张上限时加照片置灰`() {
        val id = newItem(photos = ItemRepository.MAX_PHOTOS_PER_ITEM)
        render(id)

        // 「加照片」在横向列表的**末尾**，先滑到它（LazyRow 不会一次组合九个格子）
        compose.onNodeWithTag("detail_photo_row")
            .performScrollToIndex(ItemRepository.MAX_PHOTOS_PER_ITEM)

        compose.onNodeWithTag("detail_add_photo").assertIsNotEnabled()
    }
}
