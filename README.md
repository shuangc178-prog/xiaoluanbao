# 大学生日常 · CampusLife Prototype

一个 Jetpack Compose 的 Android 应用，对应 `docs/大学生个人成长App-产品架构-v0.2.md`。
它从 **UI-first 原型**起步，页面层至今仍是那个约定：每个页面直接接收 `UiState` 与事件回调。
功能开发阶段在此之上加了每个功能目录自己的 controller，直接读写 DataStore 后把 `UiState`
交给页面——**没有 Repository、没有 ViewModel、也没有数据库**。

视觉风格参考 Typora 的 **Claude Desktop 主题**（明暗两套），取色与排版基线记录在
产品架构文档第 16.1 节；配色常量在 `ui/theme/Color.kt`，与主题 CSS 变量一一对应。

**这个应用采用本地优先存储**：打卡、计划、日记、学习资料与卡册全部落在本机的
Preferences DataStore 与应用私有文件中；网络仅用于把完整 `.xlbak` 备份通过 HTTPS WebDAV
上传到用户自己的坚果云。首页天气曾经接过在线接口，已随 `ui/weather/` 一起移出源码树。

接入真实运行时的部分包括：**打卡记录**、**每日计划**、**日记**、**学习资料**与**卡册**
（用 Preferences DataStore 持久化）、**坚果云备份与恢复**，以及**任务提醒**
（AlarmManager + 通知 + 悬浮卡片）。
每日计划支持不限数量的新增、编辑、删除与勾选；本日页面会按预期开始时间从早到晚自动排列，
未设置时间的任务排在末尾并保持原顺序。新的一天从空任务栏开始，只显示用户自己添加的内容
（不再用 Preview 示例任务建立初始快照）。
日记支持用户自建分类与完整增删改。学习资料支持导入副本、离线阅读、标题搜索、
删除和阅读进度保存。

项目已经进入功能开发阶段，打卡、计划、日记、学习与卡册均具备本地数据层。

## 技术栈

| 项 | 值 |
|---|---|
| 构建 | Gradle 9.1 + AGP 9.0.1，单模块 `:app` |
| Kotlin 支持 | AGP 9 内置 Kotlin（**不声明 `org.jetbrains.kotlin.android`**，AGP 9 起该插件已不再需要） |
| Compose 编译器 | `org.jetbrains.kotlin.plugin.compose` 2.2.10（与 AGP 内置的 KGP 版本一致） |
| Compose | BOM 2025.08.00 + Material 3 + `material-icons-extended` |
| 持久化 | `androidx.datastore:datastore-preferences`（打卡、每日计划、日记、学习资料索引、卡册、主题） |
| 云备份 | 坚果云 HTTPS WebDAV + WorkManager，每日完整 `.xlbak` 与启动补做 |
| SDK | compileSdk 36 / targetSdk 36 / minSdk 26 |
| Java | 17 |

## 在 Android Studio 中查看

1. 打开项目根目录。
2. 等待 Gradle Sync 完成并选择 `app` 的 debug variant。
3. 打开任意 `*Screen.kt`，切换到 Design 或 Split。
4. 在对应的 Preview 分组中查看各个状态（例如 `Today` 分组下的 Default / Check-in started / Check-in finished）。

所有 `*PreviewData` 都使用固定 Fake Data，可以脱离 Activity、设备和后端独立渲染。

## 主框架

底部导航由 `ui/navigation/CampusLifeApp.kt` 用 `Scaffold` + `NavigationBar` + `rememberSaveable`
实现，**刻意不引入 navigation-compose**：目前只有 Tab 级切换，没有深层路由需求。

```text
本日计划
├─ 顶部：时钟、日期、三态打卡按钮、打卡行下方的抽卡入口（一叠卡）
├─ 日期下方：明暗主题切换气泡（天使 / 恶魔，图标显示当前主题）
└─ 任务栏：任务数量不限，可新增、编辑、删除与勾选，超出一屏时滚动查看

日记随想
├─ 用户先创建、重命名或删除自己的分类
├─ 在当前分类下新建 / 编辑 / 删除日记，本地持久化
└─ 连续稿纸式编辑器：第一行作为标题，其余行作为正文；
   前 7 行从顶部往下排，写到第 8 行起页面自动跟走一行（光标固定在第 8 行）

学习
├─ 资料库：标题 / 目录 / 文件名搜索、继续阅读、文件列表
├─ 分类：背单词 / 看笔记 / 看文档，导入时落在当前分类，也可在列表里改
└─ 文档阅读器：Markdown 正文、代码、表格、相对路径资源与续读
   ├─ 排版按 Typora 的 Claude 主题（衬线正文、圆角表格与引用、主题色高亮）
   ├─ 点注释高亮的文字就地展开 Typora 的备注（只读）
   ├─ 任务列表可勾选；勾选存在 App 覆盖层，不改导入副本
   └─ 背单词分类下可点表头选一列遮起来，点单元格逐个翻开自测

统计
├─ 时间范围切换：近 7 / 14 / 30 天（窗口按日历天铺开，缺的那天留空）
├─ 连续打卡：当前连续、最长连续、累计完成、累计打卡（历史值，不随范围变）
├─ 休息卡：连续真实打卡满 5 天得一张，可在某一天用卡补记（补记不计入连续）
├─ 抽卡收藏：第一弹「异世界之旅」与第二弹「三国演义」各有独立收集进度，共用每天 1 次机会
├─ 每弹 5 种各至少 1 张即可兑换；只清空这一弹（含重复卡），获得 2 张休息卡
├─ 点收集格子 → 整屏看大图（与开卡同一屏，不播动画；点背景 / 「知道了」/ 返回键退出）
│  **还没到手的卡和格子里一样是灰的**（降饱和 + 压暗），到手过就是彩色
├─ 开始 / 结束打卡时间折线图（区间内的最早 / 最晚 / 平均）
├─ 每日打卡时长柱状图（区间内的最少 / 最多 / 平均）
└─ 点图表上的任意一天 → 底部弹层看当天详情（打卡、任务、用休息卡补记）

每日计划存档（首页头部入口）
├─ 按周收纳：每周 7 个日期气泡，颜色区分全完成 / 未完成 / 无存档
└─ 点某一天的气泡 → 那一天的只读存档详情（完成进度与逐条任务）
```

**学习资料只支持 Markdown**：`StudyDocumentFormat` 目前只有 `Markdown` 一项，
文件选择器只放行 `text/markdown` / `text/plain` / `application/zip`，
资料库里**没有格式筛选控件**，静态 HTML 阅读已随 `StudyHtmlRenderer` 一起移出源码树。

阅读器只读，但**注释与任务列表可以交互**：注释正文、颜色标记都写在正文的 HTML 注释里
（`typora-annotation-v1` / `typora-color-v1`），渲染时先换成安全的 `<span>` / `<mark>`，
页面脚本负责点开备注与勾选复选框；颜色只接受 `#rrggbb` 形式，其余取值一律忽略。
点表头遮一列只对「背单词」分类的 Markdown 生效。

「统计」固定放在最后一个 Tab：它是回顾性页面，不适合插在三个日常入口中间。

## 提醒（任务预期开始时间）

任务上可以设「预期开始时间」，到点**震三下 + 通知栏一条 + 浮一张卡片**：
亮屏在用手机时，卡片会浮在**当前应用上面**（系统级悬浮窗），卡片上点「知道了」就算处理完；
锁屏或息屏时卡片进不去（系统不允许悬浮窗压在锁屏上），只剩振动与通知。
提醒刻意**不抢屏、不响铃、不循环震**——中间试过整屏闹钟式弹出，看完真机之后撤掉了，
来龙去脉记在第 16.32 节；悬浮卡片记在第 16.33 节。
**「不准时」是这个功能唯一真正的失败方式**，口径与实现都围着它转，细节记在第 16.30 节。
这里只列结论：

- **浮卡片靠什么**：`TYPE_APPLICATION_OVERLAY` 悬浮窗 + 「显示在其他应用上层」这项**特殊权限**
  （MIUI / HyperOS 上叫「悬浮窗」）。没给权限时提醒照旧震、照旧进通知栏，只是不浮卡片，
  状态行会给一行「不浮卡片 · 去开启」。同一时刻只留一张卡（新的顶掉旧的），15 秒后自己消失；
  **锁屏与息屏时不弹**——悬浮窗画不到锁屏之上，这是系统的安全边界。

- **准时靠什么**：Android 13+ 走 `USE_EXACT_ALARM`（安装即授予，无需用户操作），
  排程用 `AlarmManager.setAlarmClock`——系统当「用户自己的闹钟」对待，不参与批处理、
  不随低电耗推迟。Android 12 用 `SCHEDULE_EXACT_ALARM`。
  `USE_EXACT_ALARM` 是 Google Play 受限权限，**本项目自用侧载不上架**，因此适用。
- **怎么提醒**：一次提醒**只震三下**（`0/400/250/400/250/400` 毫秒，`repeat = -1` 不循环），
  通知照旧留在通知栏。振动**由应用自己发**（`ReminderVibrator`，在 `ReminderReceiver` 里先震后弹），
  **不配在通知渠道上**：渠道那条链路上有 MIUI 的「振动」开关、勿扰与静音、不重要通知折叠
  这些应用看不见也改不了的静默开关，用户实测「装了没震」正是这一类。
  振动用途是 `USAGE_ALARM` —— 勿扰下仍然震得到，但**不出声**（想让它尊重勿扰就改成
  `USAGE_NOTIFICATION`，一处常量）。渠道 id 因此再换了一版
  （`campus_life_reminders_v3`，渠道的振动配置建好之后应用改不了），
  旧的 v1 / v2 由应用在启动时删掉。
- **MIUI / HyperOS 还会拦一层**：通知页里的「悬浮通知」与振动开关、以及不重要通知折叠
  都在渠道设置之外，应用读不到。状态行的「去开启」会跳进本应用通知页，那是用户唯一能自救的入口。
- **五类重排触发点**：计划变化、回到前台、开机 / 应用更新 / 改系统时间 / 换时区、
  周期自愈（WorkManager 6 小时）、状态行上的「重新排入」。
  所有入口都走 `ReminderBootstrap` 并串行化——并发重排会互相覆盖簿记。
- **闹钟到点是怎么被拉起的**：闹钟存在 system_server 里，所以进程被回收、
  从最近任务划掉都照响；`ReminderReceiver` 在 Manifest 里注册。
  **force-stop 会清空全部闹钟，WorkManager 也一并被阻塞**，这一层任何 API 都救不回来，
  只能靠系统白名单（自启动 + 省电策略无限制）避免被 force-stop。
- **能看见的状态**：系统清掉闹钟是静默的，所以读 `getNextAlarmClock()` 自检、
  按创建者包名判断归属（状态栏上的闹钟图标可能来自系统时钟），读不到时给「重新排入」。
  **首页平时一个字都不说**（16.34）：那条常驻提示只在系统侧真的坏了时才出现
  （通知未开启 / 精确闹钟未授权 / 排程丢失 / 悬浮窗没给），各带一个能直接解决问题的按钮；
  排程状态本身在**任务弹窗**里照常显示。
- **迟到多少也有数**：通知弹出时把计划时刻与实际弹出时刻一起落库（`ReminderDelivery`）。
  原来首页显示「上一次「某任务」准时 / 延迟 N 分钟」，按 16.34 的口径那一行撤掉之后，
  **这个数字暂时没有界面入口**——数据照旧在存，格式化仍是 `formatReminderLag` 一处。
- **凌晨不响的坑已修**：打卡日以凌晨 6 点换日，00:00–05:59 之间「今天」的计划还带着前一天的日期，
  按计划日期硬算会把凌晨设的钟点算到 23 小时之前、当成过期丢掉。现在只在这种情况下
  把时刻挪到打卡日当天（且只在计划日期正好比打卡日晚一天时动手）；当天真正已经过去的钟点
  仍然照旧丢弃，**不会**被推到次日变成一条凭空多出来的提醒。
- **仍未做**：安静时段、提前提醒、重复提醒。响铃与长振动已经明确不做。

## 打卡规则

| 状态 | 触发 | 按钮颜色 | 累计天数 |
|---|---|---|---|
| 未开始 | 首次进入 / 换日之后 | 无色 | 显示累计值 |
| 进行中 | 点击「开始打卡」 | 红色 | 不变 |
| 已完成 | 勾完全部任务后再点击 | 绿色 | +1，且不可再点击 |

- 必须勾完当天所有任务才能结束打卡，未勾完时按钮给出提示。
- **打卡一旦完成，当天任务的勾选就锁住，不能再取消**；未完成打卡（未开始 / 进行中）
  随时可以来回勾。锁定期间复选框是禁用态、任务栏不再收新任务，状态字末尾多一段「已打卡」。
  这道锁在 `DailyPlanController` 里（当天任务的写入路径都经过它），界面禁用只是为了不骗用户去点。
- 按钮沿用最早的**日历气泡**形态（日历图标 + 一行状态 + 累计天数），三态只改配色，不改气泡形状。
- 「累计 N 天」= 历史已完成记录条数，**只增不减**，换日不归零。
- 打卡日以**凌晨 6 点**为界：00:00–05:59 仍算前一天；到点自动换日、按钮复位为无色、锁自动解开。
- 计划事项只有待开始 / 进行中 / 已完成三档；状态标签是**只读**展示，不再承担切换操作。
- 真实记录不足 5 天时，统计页的**打卡图表**用示例数据填充并**明确标注**，达到 5 天后自动切换为真实数据。
- 统计页当前只消费打卡、计划、休息卡与卡册数据，**不订阅日记或学习资料**；
  示例数据只用于打卡图表形态，不会伪造成就值或其他业务记录。

## 目录结构

```text
app/src/
├─ main/java/com/example/campuslife/
│  ├─ MainActivity.kt      入口：持有明暗主题状态（比 CampusLifeTheme 更靠外）、回前台重排提醒、启动动画
│  ├─ data/card/           多系列卡表、按弹抽卡规则与 DataStore 持久化
│  ├─ data/backup/         完整快照、`.xlbak`、坚果云 WebDAV、凭据加密与每日调度
│  ├─ data/checkin/        打卡模型、换日、休息卡推导口径与 DataStore 持久化
│  ├─ data/journal/        日记模型、编解码与 DataStore 持久化
│  ├─ data/plan/           每日计划与存档模型、DataStore 持久化
│  ├─ data/reminder/       任务提醒：排程、通知、重排入口、自检与延迟统计（见下）
│  ├─ data/study/          学习资料索引、导入器、大纲解析、任务勾选覆盖层与 DataStore 持久化
│  ├─ data/theme/          明暗主题选择的 DataStore 持久化
│  └─ ui/
│     ├─ navigation/       CampusLifeApp、MainTab
│     ├─ backup/           本日页的坚果云连接、立即备份与恢复确认
│     ├─ today/            首页（组件最多，已按职责拆分）
│     ├─ checkin/          打卡状态持有者（换日、开始 / 结束、休息卡写入）
│     ├─ plan/             每日计划控制器与存档页
│     ├─ journal/          日记随想（控制器、编辑与分类筛选）
│     ├─ reminder/         提醒状态行、时间选择控件、悬浮卡片（首页与任务弹窗共用）
│     ├─ study/            学习资料库、导入器与阅读器
│     ├─ stats/            统计页、手绘图表、休息卡与计划存档回看
│     ├─ card/             抽卡入口（一叠卡）、开卡动效与卡面
│     ├─ splash/           启动动画
│     └─ theme/            Claude 配色、排版、语义色与明暗切换控制器
└─ test/java/com/example/campuslife/
   ├─ data/backup/        备份往返、安全边界、调度与存储覆盖守卫
   ├─ data/card/          抽卡权重、单卡上限、按弹门禁与归一
   ├─ data/checkin/       换日、时长、格式化、休息卡奖励口径
   ├─ data/journal/       日记编解码
   ├─ data/plan/          存档编码、勾选、打卡锁、日期标签
   ├─ data/reminder/      排程规则、换日锚定、闹钟归属与延迟文案
   ├─ data/study/         资料索引编解码、Markdown 大纲解析、任务勾选覆盖层编解码与合并
   ├─ ui/card/            抽卡横滑切换（阈值、到头停住）
   ├─ ui/journal/         编辑器自适应滑动、空笔记提示的偏移映射
   ├─ ui/plan/            存档周分组（每周 7 个气泡、缺天可点）
   ├─ ui/reminder/        提醒状态行的文案判据（每种系统状态说哪句话）
   └─ ui/stats/           窗口切分、连续打卡、奖励库存、两图命中判定
```

> `ui/weather/` 已经没有文件了（目录还在），首页天气与 `data/weather/` 都不在源码树里，
> 详见文末「当前边界」。

`reminder/` 按职责拆分，排程这一层刻意与界面解耦：

```text
data/reminder/
├─ ReminderSchedule.kt       候选提醒与「该不该弹」的纯函数（可单测）
├─ ReminderStatus.kt         状态模型、换日锚定、延迟文案（纯函数）
├─ ReminderScheduler.kt      交给 AlarmManager：setAlarmClock 逐级降级
├─ ReminderNotifier.kt       通知渠道与样式
├─ ReminderVibrator.kt       三下短震（应用自己发，不配在渠道上）
├─ ReminderReceiver.kt       闹钟到点的落点：先震、再弹通知 + 记实际延迟
├─ ReminderSync.kt           闹钟重新对齐，以及 ReminderRestoreReceiver
│                            （开机 / 更新 / 改时间 / 换时区后重排）
├─ ReminderBootstrap.kt      唯一排程入口（Mutex 串行化，五类触发点共用）
├─ ReminderHealWorker.kt     周期自愈（WorkManager，6 小时）
├─ ReminderStatusReader.kt   回头问系统：闹钟还在不在、归属是谁
└─ ReminderStore.kt          调度簿记（已弹 token / 已排闹钟码 / 弹出实况）
```

`stats/` 与其他功能目录一样按职责拆分：

```text
stats/
├─ StatsScreen.kt            页面骨架、页头、时间范围芯片、Preview
├─ StatsSections.kt          各分区卡片（连续打卡 / 两个图表）
├─ StatsDayDetailSheet.kt    点选某一天后的详情弹层（打卡 + 当天任务 + 用休息卡）
├─ StatsUiState.kt           UI State、日历窗口切分、汇总口径、奖励口径
├─ StatsController.kt        范围、选中日、用卡回调
├─ StatsCharts.kt            手绘折线 / 柱状图（含点选与高亮）
├─ RestCardArt.kt            休息卡卡面（用户提供的像素画素材）
├─ RestCardReveal.kt         获得休息卡的三段式动效
├─ RestCardSections.kt       休息卡分区（库存 / 进度 / 已获得的卡）
└─ StatsPreviewData.kt       Fake Data（populated / week / month / sparse / plansOnly / staleWindow / empty）
```

`card/` 是抽卡入口、卡面与「一整屏看一张卡」：

```text
card/
├─ CardStage.kt               整屏展示一张卡（压暗背景 + 稀有度柔光 + 卡面 + 底部一行）
├─ CardController.kt          各弹抽卡门禁、卡册、兑换与开卡状态（GachaEntryUiState）
├─ CardViewer.kt              卡册里点开看大图：全屏 Dialog 包一个 CardStage，不播动画
├─ GachaReveal.kt             开卡动效：同一屏的四拍节奏（落暗 → 浮现 → 成形 → 呼吸）
├─ GachaSeriesStack.kt        「一叠卡」入口：层叠 3° 旋转 + 左右滑动切弹
├─ CardArt.kt                 卡位与卡面绘制（宽高比写死在代码里）
└─ CardPreviewData.kt         Fake Data
```

`CardStage` 是**开卡与看卡共用的同一屏**——看卡就是它停下来、不播动画的样子，
两处画面因此不会各写一套再慢慢跑偏。卡面尺寸在根节点按整屏宽高算
（`calculateCardArtSize`，宽高两个方向都收），屏幕一矮也不会顶出屏幕。

运行时系列、卡表、稀有度权重与卡册模型在 `data/card/CardModels.kt`；上限、每日门禁、抽取算法、
兑换资格与奖励常量在 `GachaRule.kt`，Store / Controller 负责持久化和动作编排。
`.workbuddy/cards/cards.json` 是设计资产清单，`gacha.rules` 也只是同步侧镜像；打包到 assets 的副本不参与运行时抽取。

**「是不是灰的」只有一份口径**：`cardArtAlpha(owned)` + `Desaturate`（都在 `CardArt.kt`）。
收集格子和整屏看卡都读它，所以未到手的卡在两处都是灰底剪影。
这里踩过一次：整屏那边一开始直接铺原图，格子里灰、点开却是彩色，等于把「还没到手」弄丢了。

`today/` 内部按职责拆分，不再有单文件堆积：

```text
today/
├─ TodayScreen.kt              页面骨架与状态
├─ TodayUiState.kt             UI State 与枚举
├─ TodayPreviewData.kt         Fake Data（default / empty / populated）
├─ TodayHeaderSection.kt       头部、三态打卡按钮、主题气泡、抽卡入口、打卡弹窗
├─ TodayTaskSection.kt         任务栏
├─ TodayCommonComponents.kt    分区标题、空状态卡
└─ CurrentDateTime.kt          当前时间
```

天气相关的 `TodayWeatherCard.kt` 与 `WeatherAnimations.kt` 已随天气功能一起移出源码树。

## 配色约定

不要在组件里写颜色字面量，也不要在组件里判断明暗：

- 标准语义角色 → `MaterialTheme.colorScheme.*`（由 `ui/theme/Theme.kt` 从 Claude 色板映射）
- Material 覆盖不到的色（打卡状态、分类标签、图表系列）→ `MaterialTheme.claude.*`
  （由 `ui/theme/ClaudeAccents.kt` 通过 CompositionLocal 按明暗注入）
- 排版 → `ui/theme/Type.kt`：标题走衬线族，界面与正文走无衬线族

## UI-first 约定

- Screen 接收 `UiState` 和 Event Callback（回调一律给空实现默认值）。
- Fake / Preview Data 放在对应功能目录，不从运行时服务读取。
- 每个主要 Screen 至少提供 Default、Empty、Populated 三种 Preview。
- 特殊流程再增加 Loading、In Progress、Conflict 或 Partial Completion Preview。
- 原型阶段的按钮允许空 callback，不能因为业务层缺失而阻止页面显示。

## 构建与验证

```bash
./gradlew :app:assembleDebug            # 打包 debug APK
./gradlew :app:testDebugUnitTest        # 跑单元测试
./gradlew :app:lintDebug                # 静态检查
./gradlew :app:assembleRelease          # 验证 R8 + lintVitalRelease
```

最近一次代码侧验证（2026-09-20）：`:app:testDebugUnitTest` 共执行 32 个测试类、246 个用例，
0 失败、0 错误、0 跳过。界面与真机行为不在这项验证范围内，仍按人工清单检查。

release 构建已打开 R8（`isMinifyEnabled` + `isShrinkResources`），规则在 `app/proguard-rules.pro`。
图表（折线 / 柱状）用 Compose Canvas 手绘，未引入第三方图表库。

## 当前边界

- **资料**：全文搜索、公式 / 图表增强、批注编写、收藏、重新导入版本对比尚未实现；
  阅读器已有 Markdown 目录抽屉与文内查找 / 上下跳转。静态 HTML 与格式筛选已移出源码树。
  阅读器对 Typora 扩展提供两条**只读交互**：点注释高亮就地展开备注（正文写在
  `<!--typora-annotation-v1-…-->` 的 base64url 里），点任务复选框即勾选 / 取消。
  勾选状态存在 `study` DataStore 的 `task_states` 覆盖层里（按资料 id + 正文指纹索引），
  **不改写导入副本**，也不回写电脑上的原稿；重新导入后指纹变化，旧勾选整条作废。
- **首页天气已移出**：在线天气与定位（`data/weather/`、`ui/weather/`、WMO 天气码映射）
  都不在源码树里，`ui/weather/` 只剩一个空目录。当前 `INTERNET` 权限只服务坚果云 WebDAV，
  不用于天气、定位或业务数据同步。
- **健身、饮食、课表与任务的完整业务逻辑尚未实现**（原「运动健康」页面已移出源码树，
  备份在 `.workbuddy/backup/fitness/`）。
- **已实现坚果云完整备份与最新备份恢复，不是多设备实时同步**：WorkManager 以每天 06:00
  为目标执行，系统省电或断网时可能延后并在启动后补做。当前不提供云端历史列表、自动清理和
  `.xlbak` 内容加密。新增持久化功能必须按开发说明同步扩展备份覆盖范围。
- **Android 系统备份与 `.xlbak` 是两套独立机制**：Manifest 当前仍为 `allowBackup="true"`，
  且尚未声明 `dataExtractionRules` / `fullBackupContent`。系统云备份或设备迁移可能带走 DataStore
  （包括日记正文）；公开分发前必须明确选择“关闭系统备份”或“配置白名单/排除规则”，不能把
  坚果云备份的覆盖清单误当成系统备份边界。
- 抽卡已上线两弹：第一弹「异世界之旅」与第二弹「三国演义」。两弹共用一套稀有度权重，
  卡册进度、满池判定与整套兑换**按弹独立**，但每天的 1 次抽卡机会由所有已上线弹共享；
  兑换只清空当前弹并获得 2 张休息卡，不影响另一弹。第三至第五弹仍为空位。
- `card_collection` 在一个 DataStore 中按全局卡 id 保存数量，用
  `lastDrawEpochDays: Map<系列 id, 逻辑日>` 分别保存每弹最后抽卡日，并保存新版整套兑换得到的
  休息卡数量；完整备份覆盖这些字段。旧版 `check_in.bonus_rest_cards` 仍兼容计入库存。

`docs/` 中的产品架构文档第 7、12 节记录口径与迭代顺序，第 16 节记录本轮界面更正；
备份的数据清单、格式与新增存储检查见 `docs/坚果云备份与恢复-开发说明.md`。
