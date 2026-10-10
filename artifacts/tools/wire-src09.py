# FR-SRC-09：无照片手填。
# 1) 仓库 newBlankDraft（不写 item_photo 那一行）
# 2) CaptureFlow.startBlank
# 3) CaptureViewModel.startBlank（复用 captured 通道）
# 4) 来源弹窗第三个选项「不拍照，手动填」（只在新建时出现，换照片时不出现）
# 5) ReviewViewModel.start：没照片就不跑分析（否则会说"找不到这张照片了"）
# 6) 确认页：没照片时给占位与说明
import io
import sys

sys.stdout.reconfigure(errors="replace")
SRC = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app"

def rw(path, pairs):
    p = SRC + "\\" + path
    t = io.open(p, encoding="utf-8").read()
    for old, new in pairs:
        if old in t:
            t = t.replace(old, new, 1)
        else:
            print("  !! %s 没匹配：%s" % (path, old.strip().splitlines()[0][:60]))
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("  已改 %s" % path)

# 1) 仓库
rw(
    r"data\repo\ItemRepository.kt",
    [
        (
            "    suspend fun newDraftSharingPhoto(sourceItemId: String): String {",
            '''    /**
     * 建一条**没有照片**的草稿（`FR-SRC-09`）：给"不想拍、只想记一条"的东西。
     *
     * 与 [newDraft] 的差别只有一处：**不写 `item_photo` 那一行**，也不留待分析的照片。
     * 其余（空名称、`DRAFT` 状态、进"待归类"）完全一样——所以后面每一步（确认、检索、删除）
     * 都不需要为"没照片"另开一条路。
     */
    suspend fun newBlankDraft(): String {
        val id = UUID.randomUUID().toString()
        db.withWriteTransaction { itemDao.upsert(blankItem(id)) }
        return id
    }

    suspend fun newDraftSharingPhoto(sourceItemId: String): String {''',
        ),
    ],
)

# 2) CaptureFlow
rw(
    r"domain\CaptureFlow.kt",
    [
        (
            "    suspend fun replacePhoto(itemId: String, jpeg: ByteArray): ItemDraft {",
            '''    /** 「不拍照，手动填」（`FR-SRC-09`）：开一条没有照片的草稿，收尾与 [capture] 一样。 */
    suspend fun startBlank(): String = items.newBlankDraft()

    suspend fun replacePhoto(itemId: String, jpeg: ByteArray): ItemDraft {''',
        ),
    ],
)

# 3) CaptureViewModel
rw(
    r"ui\capture\CaptureViewModel.kt",
    [
        (
            "    fun consumeCaptured() {",
            '''    /**
     * 「不拍照，手动填」（`FR-SRC-09`）：复用 `captured` 这条通道——
     * 外壳那边只认"有个新 itemId 要去确认页"，不关心它是不是拍的。
     */
    fun startBlank() {
        viewModelScope.launch {
            runCatching { flow.startBlank() }
                .onSuccess { _captured.value = it }
                .onFailure { _error.value = it.message ?: "建不了这条，再试一次" }
        }
    }

    fun consumeCaptured() {''',
        ),
    ],
)

# 4) 来源弹窗
rw(
    r"ui\WheremyRoot.kt",
    [
        (
            '''                  title = { Text("这张照片从哪来？") },
                  text = { Text("拍照就在 Wheremy 里取景，按下快门才拍；它只要相机权限，不进相册。") },''',
            '''                  title = { Text("这张照片从哪来？") },
                  text = {
                      Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
                          Text("拍照就在 Wheremy 里取景，按下快门才拍；它只要相机权限，不进相册。")
                          // 「不拍照，手动填」（FR-SRC-09）：只在**新建**时给——
                          // 换照片的时候这条没有意义（那条记录本来就有照片）
                          if (backStack.lastOrNull() !is ReviewKey) {
                              TextButton(
                                  onClick = {
                                      askingFor = PhotoTarget.NONE
                                      captureVm.startBlank()
                                  },
                              ) { Text("不拍照，手动填") }
                          }
                      }
                  },''',
        ),
    ],
)

# 5) ReviewViewModel.start：没照片不跑分析
rw(
    r"ui\review\ReviewViewModel.kt",
    [
        (
            """            _state.update { it.copy(slots = listOf(slot), selected = 0, finished = false) }
            analyze(itemId)""",
            """            _state.update { it.copy(slots = listOf(slot), selected = 0, finished = false) }
            // 没有照片就不跑分析（`FR-SRC-09`）：手动填的条目**不是**"照片丢了"，
            // 说那句"找不到这张照片了"会让人以为出了问题
            if (slot.thumbPath != null) {
                analyze(itemId)
            } else {
                _state.update { it.copy(analyzing = false, failure = null, guess = null) }
            }""",
        ),
    ],
)

# 6) 确认页：照片占位 + 状态行说明
rw(
    r"ui\review\ReviewScreen.kt",
    [
        (
            """          if (thumbPath != null) {
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(WheremySpacing.photoBox)
                      .clip(MaterialTheme.shapes.medium),
              ) {
                  coil3.compose.AsyncImage(
                      model = File(thumbPath),
                      contentDescription = null,
                      contentScale = ContentScale.Fit,
                      modifier = Modifier.fillMaxSize(),
                  )
              }
          }
          OutlinedButton(onClick = onPick) {
              Text(if (hasStoredItem) "换一张" else "选择照片")
          }""",
            """          if (thumbPath != null) {
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(WheremySpacing.photoBox)
                      .clip(MaterialTheme.shapes.medium),
              ) {
                  coil3.compose.AsyncImage(
                      model = File(thumbPath),
                      contentDescription = null,
                      contentScale = ContentScale.Fit,
                      modifier = Modifier.fillMaxSize(),
                  )
              }
          } else {
              // 没照片是**正常形态**（FR-SRC-09），不是缺东西：说清"这条就是手填的"，再给补照片的路
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(WheremySpacing.photoBox)
                      .clip(MaterialTheme.shapes.medium)
                      .background(MaterialTheme.colorScheme.surfaceVariant),
                  contentAlignment = Alignment.Center,
              ) {
                  Text("没有照片（手动填的条目）", style = MaterialTheme.typography.bodyMedium)
              }
          }
          OutlinedButton(onClick = onPick) {
              Text(
                  when {
                      thumbPath == null -> "加一张照片"
                      hasStoredItem -> "换一张"
                      else -> "选择照片"
                  },
              )
          }""",
        ),
        (
            "            AiStatusLine(state = state, onRetry = vm::retryAnalysis)",
            """            // 没照片时那颗「重新分析」没有意义（没有可分析的东西），换成一句说明
            if (state.current?.thumbPath == null) {
                Text(
                    text = "没有照片，不跑识别——下面的字段自己填就行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AiStatusLine(state = state, onRetry = vm::retryAnalysis)
            }""",
        ),
    ],
)
print("完成")
