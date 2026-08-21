package com.bicy.note.ui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import com.bicy.note.data.LocalRepository
import com.bicy.note.data.NoteRepository
import com.bicy.note.data.model.DayNotes
import com.bicy.note.data.model.MARKER_CIRCLE
import com.bicy.note.data.model.MARKER_STAR
import com.bicy.note.data.model.NoteEntry
import com.bicy.note.ui.components.MediaBadges
import com.bicy.note.ui.components.MonthCalendar
import com.bicy.note.ui.components.NoteDetailSheet
import com.bicy.note.ui.components.markerIcon
import com.bicy.note.ui.screens.settings.QuickNoteComposer
import com.bicy.note.ui.theme.寄意Theme
import com.bicy.note.util.WavAudioRecorder
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 随时记悬浮窗：可拖动悬浮球 + 点击展开面板。
 * 面板默认显示今日记录，可切月历选日期，可加号新建记录。
 */
class QuickNoteService : Service() {

    private lateinit var windowManager: WindowManager
    private var ballView: View? = null
    private var panelView: View? = null
    private val panelState = PanelState()

    /** 录音器挂在服务上：面板关闭/重开时录音不中断、仍可停止。 */
    private val recorder = WavAudioRecorder()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ballView == null) showBall()
        return START_STICKY
    }

    override fun onDestroy() {
        hidePanel()
        removeBall()
        super.onDestroy()
    }

    private fun showBall() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt()
        val ball = TextView(this).apply {
            text = "寄"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF3F51B5.toInt())
            }
            setOnTouchListener(ballTouchListener)
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - 24.dpToPx()
            y = (resources.displayMetrics.heightPixels / 2) - size / 2
        }
        try {
            windowManager.addView(ball, params)
            ballView = ball
        } catch (_: Exception) {
        }
    }

    private val ballTouchListener = View.OnTouchListener { view, event ->
        val params = view.layoutParams as WindowManager.LayoutParams
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
                startX = params.x.toFloat()
                startY = params.y.toFloat()
                moved = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                if (kotlin.math.abs(event.rawX - lastX) > touchSlop ||
                    kotlin.math.abs(event.rawY - lastY) > touchSlop
                ) {
                    moved = true
                }
                params.x = (startX + dx).toInt()
                params.y = (startY + dy).toInt()
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) {
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) togglePanel()
                true
            }
            else -> false
        }
    }

    private fun togglePanel() {
        if (panelView != null) hidePanel() else showPanel()
    }

    private fun showPanel() {
        val owners = PanelOwners(this)
        panelOwners = owners
        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { hidePanel() }
            setViewTreeLifecycleOwner(owners)
            setViewTreeViewModelStoreOwner(owners)
            setViewTreeSavedStateRegistryOwner(owners)
            addView(
                ComposeView(this@QuickNoteService).apply {
                    setViewTreeLifecycleOwner(owners)
                    setViewTreeViewModelStoreOwner(owners)
                    setViewTreeSavedStateRegistryOwner(owners)
                    setContent {
                        CompositionLocalProvider(
                            LocalRepository provides NoteRepository.get(this@QuickNoteService),
                            LocalActivityResultRegistryOwner provides owners,
                        ) {
                            寄意Theme {
                                QuickNotePanel(
                                    state = panelState,
                                    recorder = recorder,
                                    onClose = { hidePanel() },
                                )
                            }
                        }
                    }
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                owners.attach()
            }

            override fun onViewDetachedFromWindow(v: View) {
                owners.detach()
            }
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        try {
            windowManager.addView(root, params)
            panelView = root
            setPanelRoot(root)
            setActiveRegistry(owners.activityResultRegistry)
        } catch (_: Exception) {
        }
    }

    private fun hidePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        setPanelRoot(null)
        panelOwners = null
    }

    private fun removeBall() {
        ballView?.let { windowManager.removeView(it) }
        ballView = null
    }

    private var lastX = 0f
    private var lastY = 0f
    private var startX = 0f
    private var startY = 0f
    private var moved = false

    private val touchSlop by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics,
        )
    }

    private var panelOwners: PanelOwners? = null

    /**
     * 悬浮窗视图的宿主：生命周期 + ViewModel + SavedStateRegistry + ActivityResultRegistry 四合一。
     * 注意：初始化时必须保持 INITIALIZED（SavedStateRegistryController.performRestore
     * 与 Compose 内部 ViewModel 的创建都要求 owner 处于初始化阶段），
     * 视图挂载后再推进到 RESUMED，卸载时销毁。
     */
    private class PanelOwners(context: Context) :
        LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, ActivityResultRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val resultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                runCatching {
                    val intent = contract.createIntent(context, input)
                    Log.d("寄意中转", "onLaunch: ${contract.javaClass.simpleName} code=$requestCode")
                    // 悬浮窗是全屏 OVERLAY 窗口，会盖在相机/选择器等 Activity 之上，
                    // 必须先把面板整体隐藏（仅改可见性，窗口仍挂载、组合不销毁、
                    // ActivityResult 回调保持注册），结果回来后由 dispatchResult 恢复显示。
                    hidePanelForResult()
                    context.startActivity(
                        Intent(context, ResultTrampolineActivity::class.java)
                            .putExtra(EXTRA_REQUEST_CODE, requestCode)
                            .putExtra(EXTRA_INTENT, intent)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry
        override val activityResultRegistry: ActivityResultRegistry
            get() = resultRegistry

        fun attach() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun detach() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            viewModelStore.clear()
        }
    }

    companion object {
        const val EXTRA_REQUEST_CODE = "bicy_request_code"
        const val EXTRA_INTENT = "bicy_intent"

        @Volatile
        private var activeRegistry: ActivityResultRegistry? = null

        @Volatile
        private var panelRoot: View? = null

        fun setActiveRegistry(registry: ActivityResultRegistry?) {
            activeRegistry = registry
        }

        fun setPanelRoot(root: View?) {
            panelRoot = root
        }

        /** 外部 Activity（相机/选择器）打开时隐藏面板窗口：仅改可见性，组合与回调保持存活。 */
        fun hidePanelForResult() {
            panelRoot?.visibility = View.GONE
        }

        /** 结果返回后恢复面板显示。 */
        fun restorePanelAfterResult() {
            panelRoot?.visibility = View.VISIBLE
        }

        /** ResultTrampolineActivity 把结果转交回当前悬浮窗的注册表。 */
        fun dispatchResult(requestCode: Int, resultCode: Int, data: Intent?) {
            activeRegistry?.dispatchResult(requestCode, resultCode, data)
            restorePanelAfterResult()
        }
    }

    private fun Int.dpToPx(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, toFloat(), resources.displayMetrics).toInt()
}

private enum class PanelMode { Records, Calendar, Compose }

/**
 * 面板跨开关存活的状态：关闭面板（窗口移除）后再打开，仍停留在关闭前的页面/日期/随写草稿。
 * 随服务存活；服务被系统销毁则归零。
 */
private class PanelState {
    var mode: PanelMode = PanelMode.Records
    var date: LocalDate = LocalDate.now()
    var month: YearMonth = YearMonth.now()
    val draft = ComposeDraft()

    /** 清空草稿请求计数：标题栏垃圾桶点击后 +1，composer 收到后执行清空。 */
    var clearSignal = 0
}

/** 随写草稿：面板收起（如发起拍照/选图/授权时自动收起）也不丢内容。 */
class ComposeDraft {
    var text: String = ""
    var imageUris: List<Uri> = emptyList()
    var videoUris: List<Uri> = emptyList()
    var audioUri: Uri? = null
    var recordedFile: File? = null
    var recording: Boolean = false
    var recordSeconds: Int = 0

    fun clear() {
        text = ""
        imageUris = emptyList()
        videoUris = emptyList()
        audioUri = null
        recordedFile = null
        recording = false
        recordSeconds = 0
    }
}

@Composable
private fun QuickNotePanel(state: PanelState, recorder: WavAudioRecorder, onClose: () -> Unit) {
    val repository = LocalRepository.current
    val notesVersion by repository.notesVersion.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(state.mode) }
    var date by remember { mutableStateOf(state.date) }
    var month by remember { mutableStateOf(state.month) }
    var clearSignal by remember { mutableStateOf(state.clearSignal) }
    var allNotes by remember { mutableStateOf<Map<LocalDate, DayNotes>>(emptyMap()) }
    var viewing by remember { mutableStateOf<NoteEntry?>(null) }

    LaunchedEffect(mode) { state.mode = mode }
    LaunchedEffect(date) { state.date = date }
    LaunchedEffect(month) { state.month = month }

    LaunchedEffect(notesVersion) {
        allNotes = repository.loadAllNotes()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxHeight = maxHeight
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .heightIn(max = maxHeight * 0.8f)
                .shadow(20.dp, RoundedCornerShape(20.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(20.dp),
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (mode) {
                            PanelMode.Records -> date.format(dateLabelFormatter)
                            PanelMode.Calendar -> "选择日期"
                            PanelMode.Compose -> "新建记录 · ${date.format(dateLabelFormatter)}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (mode == PanelMode.Records) {
                        Text(
                            text = "共 ${allNotes[date]?.records?.size ?: 0} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            if (mode == PanelMode.Compose) {
                IconButton(
                    onClick = {
                        state.clearSignal++
                        clearSignal = state.clearSignal
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "清空草稿",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = "关闭")
            }
            }

            when (mode) {
                PanelMode.Records -> {
                    val records = allNotes[date]?.records.orEmpty()
                    if (records.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "这一天没有记录",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        ) {
                            itemsIndexed(records, key = { index, entry -> "$index|${entry.time}|${entry.text}" }) { index, entry ->
                                PanelRecordRow(
                                    entry = entry,
                                    onClick = { viewing = entry },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                month = YearMonth.from(date)
                                mode = PanelMode.Calendar
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = "选择其他日期",
                            )
                        }
                        FloatingActionButton(
                            onClick = { mode = PanelMode.Compose },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(imageVector = Icons.Outlined.Add, contentDescription = "新建记录")
                        }
                    }
                }
                PanelMode.Calendar -> {
                    MonthCalendar(
                        month = month,
                        markedDates = allNotes.keys,
                        selectedDate = date,
                        onMonthChange = { month = it },
                        onDateClick = { picked ->
                            date = picked
                            mode = PanelMode.Records
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = { mode = PanelMode.Records },
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    ) {
                        Text(text = "返回")
                    }
                }
                PanelMode.Compose -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        QuickNoteComposer(
                            date = date,
                            draft = state.draft,
                            recorder = recorder,
                            showMedia = true,
                            clearSignal = clearSignal,
                            onClearConsumed = {
                                state.clearSignal = 0
                                clearSignal = 0
                            },
                            onSaved = {
                                date = LocalDate.now()
                                mode = PanelMode.Records
                            },
                        )
                    }
                }
            }
        }
    }

    val viewingEntry = viewing
    if (viewingEntry != null) {
        NoteDetailSheet(
            entry = viewingEntry,
            date = date,
            onDismiss = { viewing = null },
        )
    }
}

@Composable
private fun PanelRecordRow(
    entry: NoteEntry,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val marker = entry.effectiveMarker()
            if (marker != MARKER_CIRCLE) {
                Icon(
                    imageVector = markerIcon(marker),
                    contentDescription = if (marker == MARKER_STAR) "临时标记" else "收藏标记",
                    tint = if (marker == MARKER_STAR) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = entry.time,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (entry.text.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "（媒体记录）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (entry.images.isNotEmpty() || entry.videos.isNotEmpty() || entry.audios.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            MediaBadges(entry)
        }
    }
}

private val dateLabelFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")