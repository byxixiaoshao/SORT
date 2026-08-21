# 寄意 (SORT)

一款纯 Compose 实现的个人记录与生活管理 Android 应用。零数据库，全部数据以 JSON 文件存储，轻量且可离线使用。

## 功能概览

### 随写
随开随记的轻量编辑器，支持文本、图片、视频、音频（文件/录音），自动保存草稿，中断编辑不丢内容。

### 日历
月历视图展示有记录的日期，支持频率折线图（24 小时 / 7 天 / 30 天）。点击日期查看当天全部记录，支持按标记筛选。

### 时钟
模拟指针时钟 + 数字时间 + 日期显示。勿扰管理：立刻勿扰（持续/倒计时）+ 定时勿扰规则（按星期 + 时段自动开关系统勿扰），进程被杀/重启后仍生效。

### 悬浮窗（随时记）
悬浮球常驻桌面，可拖动，点击展开面板（记录列表 / 月历 / 随写编辑器），随时记录不打断当前操作。

### 通知监听
后台监听指定应用通知，自动记录通知内容并打上临时标记，过期自动清理。

### 全局搜索
搜索记录、日程、邮件内容，支持按月份和标记类型筛选。

### 邮箱
写一封回忆邮件寄给未来的自己，到期提醒查看。

### 日程表
添加课程/作息日程，按星期固定重复。

## 技术架构

```
┌─────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose + Material3)         │
│  ├── screens/  Calendar / Clock / Settings       │
│  ├── components/  MonthCalendar / MediaViewer    │
│  ├── FeatureDockBar  顶部功能 Dock 栏            │
│  ├── QuickNoteService  悬浮窗服务                 │
│  └── theme/  Material3 主题                      │
├─────────────────────────────────────────────────┤
│  Navigation                                      │
│  ├── AppNavigation (NavHost + 弹窗层叠管理)      │
│  └── MainTab (Calendar / Clock / Settings)       │
├─────────────────────────────────────────────────┤
│  Data Layer                                      │
│  ├── NoteRepository  核心数据仓库（单例）         │
│  ├── JsonStore  轻量 JSON 文件存储（原子写入）    │
│  ├── SystemDndManager  系统勿扰管理              │
│  └── Models  数据类                              │
├─────────────────────────────────────────────────┤
│  Service Layer                                   │
│  ├── AppNotificationListener  通知监听（独立进程）│
│  └── ScheduledDnd  勿扰调度（AlarmManager）      │
└─────────────────────────────────────────────────┘
```

## 数据存储结构

```
filesDir/
├── notes/
│   ├── text/yyyy_MM_dd_text.json          每日记录
│   ├── image_and_video/                   图片与视频
│   ├── audio/                             音频文件
│   └── drafts/draft.json                  随写草稿
├── emails/emails.json                     回忆邮件
├── schedules/schedules.json               日程表
└── settings.json                          应用设置
```

## 项目结构

```
app/src/main/java/com/bicy/note/
├── MainActivity.kt
├── data/
│   ├── JsonStore.kt                       JSON 原子写入
│   ├── LocalRepository.kt                 CompositionLocal
│   ├── NoteRepository.kt                  核心数据仓库
│   ├── SystemDndManager.kt                系统勿扰管理
│   └── model/Models.kt                    数据模型
├── ui/
│   ├── AppNavigation.kt                   主导航 + 弹窗管理
│   ├── FeatureDockBar.kt                  顶部功能 Dock 栏
│   ├── QuickNoteService.kt                悬浮窗服务
│   ├── ScheduledDnd.kt                    勿扰调度
│   ├── components/
│   │   ├── CommonUi.kt                    通用 UI 组件
│   │   ├── MediaViewer.kt                 记录详情弹窗
│   │   ├── MediaViews.kt                  媒体缩略图
│   │   └── MonthCalendar.kt               共享月历组件
│   ├── screens/
│   │   ├── CalendarScreen.kt              日历页
│   │   ├── ClockScreen.kt                 时钟页
│   │   ├── SettingsScreen.kt              设置页
│   │   ├── calendar/
│   │   │   ├── DayDetailOverlay.kt        日期记录详情
│   │   │   └── FrequencyChart.kt          频率折线图
│   │   ├── clock/WheelTimePicker.kt       滚轮时间选择器
│   │   └── settings/
│   │       ├── DockWindows.kt             Dock 窗口内容
│   │       ├── QuickNoteComposer.kt       随写编辑器
│   │       ├── SettingComponents.kt       设置项组件
│   │       └── SettingsPopups.kt          设置弹窗
│   ├── search/GlobalSearch.kt             全局搜索
│   └── theme/                             主题定义
└── util/
    ├── TimeFormat.kt                      时间格式化
    └── WavAudioRecorder.kt               WAV 录音器
```

## 环境要求

| 项目 | 版本 |
|------|------|
| Android Studio | Meerkat+ |
| Kotlin | 2.2.10 |
| AGP | 9.2.1 |
| Compose BOM | 2026.08.00 |
| minSdk | 29 (Android 10) |
| targetSdk | 36 |
| compileSdk | 37 |

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗（随时记浮球 + 面板） |
| `RECORD_AUDIO` | 随写录音功能 |
| `ACCESS_NOTIFICATION_POLICY` | 读写系统勿扰状态 |
| `RECEIVE_BOOT_COMPLETED` | 开机后重挂勿扰调度 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟（勿扰调度） |
| `QUERY_ALL_PACKAGES` | 枚举已安装应用（监听应用选择） |
| `WRITE_SECURE_SETTINGS` | 自授勿扰权限（需 adb 授予） |

## 构建

```bash
git clone https://github.com/byxixiaoshao/SORT.git
cd SORT
./gradlew assembleDebug
```

## 设计特点

- **零数据库**：全部数据以 JSON 文件存储，schema 变更只需调整默认值
- **原子写入**：临时文件 + 重命名 + `.bak` 备份，防止写入中断损坏
- **跨进程同步**：通知监听进程（`:listener`）通过广播通知主进程刷新
- **精确调度**：AlarmManager 精确闹钟 + 重启/时间变更自动恢复
- **草稿机制**：防抖 400ms 自动保存，关面板/杀进程不丢内容

## License

MIT
