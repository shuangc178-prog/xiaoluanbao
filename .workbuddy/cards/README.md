# 卡库

设计资产的归档处。**卡从这里进，但 Android 运行时不直接读这份 JSON。**
`cards.json` 是素材、尺寸、状态与生成目标的设计侧清单；运行时系列、卡表、稀有度权重与卡册模型在
`data/card/CardModels.kt`，上限、每日门禁、抽取算法、兑换资格与奖励常量在 `data/card/GachaRule.kt`。
以后每设计一张卡：复制同系列里的一张、改 `meta.json`、在 `cards.json` 的对应系列里加一条、跑 `sync_cards.py`。

## 目录

```text
.workbuddy/cards/
├─ cards.json                        设计资产总清单（素材侧事实来源，不是运行时卡表）
├─ README.md                         本文件
├─ sync_cards.py                     同步到 app/src/main/assets/ + 一致性自检
├─ prepare_art.py                    渲染稿 → 进 APK 的 drawable（去背 / 裁边 / 量化）
├─ preview/                          prepare_art.py 产出的对照图（浅/深底各一份，供肉眼查白边）
├─ rest/                             系列：休息卡（功能卡）
│  └─ rest_001_sleepy_cat/
│     ├─ meta.json                   这张卡的完整定义（口径 / 尺寸 / 动效 / 出处）
│     ├─ card_full.png               整卡 930×1330（= res/drawable-nodpi/rest_card_full.png）
│     └─ card_chip.png               插画区 930×732（= res/drawable-nodpi/rest_card_chip.png）
├─ isekai/                           第一弹：异世界之旅（收集卡，一套 5 张）
│  ├─ isekai_001_unicorn/   R      card_full.jpg  1086×1448
│  ├─ isekai_002_elf_king/  SR     card_full.jpg
│  ├─ isekai_003_hero/      SSR    card_full.jpg
│  ├─ isekai_004_black_mage/ SSSR  card_full.jpg
│  └─ isekai_005_ancient_dragon/ XR card_full.jpg
└─ sanguo/                            第二弹：三国演义（收集卡，一套 5 张）
   ├─ sanguo_001_taoyuan_r.png       R    桃园结义
   ├─ sanguo_002_changban_sr.png     SR   长坂孤骑
   ├─ sanguo_003_kongcheng_ssr.png   SSR  空城绝响
   ├─ sanguo_004_chibi_sssr.png      SSSR 赤壁焚天
   └─ sanguo_005_tianxia_xr.png      XR   天下三分
```

休息卡的设计工作文件（候选稿 `rest-card-proposals.html`、裁切源图 `crop-*.png`、造测试数据的脚本）
留在 `.workbuddy/rest-card/`，与本卡库分开放：那边是「做图的过程」，这里是「定稿的卡」。

## 三个系列，两种性质

| 系列 | kind | 是什么 | 现在怎么得到 |
|---|---|---|---|
| 休息卡 | `functional` | 功能卡：给漏掉的一天补记打卡 | 连续**真实**打卡满 5 天发一张 |
| 异世界之旅 | `collectible` | 第一弹收集卡，一套 5 张，R → XR | 抽卡（已上线） |
| 三国演义 | `collectible` | 第二弹收集卡，一套 5 张，R → XR | 抽卡（已上线） |

- **休息卡没有稀有度**，它是奖励物不是收集品，别给它硬套档位。
- **两弹的稀有度写法相同**：`R < SR < SSR < SSSR < XR`。第三档是 **SSSR**、最高档是 **XR**，都没用 UR——不要顺手改成常见写法。
- 收集卡每张的画里都有 App 的橙色像素主角「**小卵包**」出场（骑在独角兽背上、站在勇者脚边、仰头看龙……）。这是整套卡的设计母题，不是画错。
- 两弹 10 张收集卡的**效果都还没定**，`effect` 字段留 `null`：设计稿只画了形象与稀有度。第一弹清单里的「旅程启程」胶囊标签，是系列标签、卡包名还是技能名，设计稿没说——**别自行解读**，等产品口径定了再填。

## 卡面文件怎么分工

| 角色 | 说明 |
|---|---|
| `card_full.*` | 整卡：外框、卡名、系列副题、胶囊标签、稀有度徽章都已在图里，代码不要再复述一遍 |
| `card_chip.*` | 只截插画区，卡槽/列表里的小卡面用它——整卡缩到几十 dp 时文字会糊。**目前只有休息卡有**，两弹 10 张收集卡的 chip 还没裁 |

三个落脚点，改图时注意别漏：

1. `.workbuddy/cards/<系列>/<卡>/` —— 设计归档（**源**）
2. `app/src/main/res/drawable-nodpi/` —— 打包进 APK，App 运行时从这取（放在 `drawable-nodpi` 是刻意的：像素画必须原样映射，不能被密度缩放糊掉）
3. `app/src/main/assets/cards/cards.json` —— 设计清单的打包副本，**由 `sync_cards.py` 生成，不要手改，也不是 Kotlin 运行时卡表**

> **两弹共 10 张收集卡已打包**（`packaged: true`）：需要处理的渲染稿经 `prepare_art.py`
> 去背裁边后进 `res/drawable-nodpi/`，drawable 名与卡 id 同名；`art.prepared` 里记着成品尺寸与宽高比，
> `sync_cards.py` 会拿它和实际文件对账。改图要重跑 `prepare_art.py` 再 `sync_cards.py`。

## 清单字段（cards.json）

顶层：`schemaVersion`、`rarityLadder`、`series[]`、`candidates[]`、`pending[]`、`gacha`。

每张卡：

| 字段 | 说明 |
|---|---|
| `id` | `<系列英文>_00N_短名`，全小写——要进文件名与资源名，别用中文 |
| `name` / `displayName` | 卡名（形象名）/ 玩家看到的名字 |
| `status` | `final` 定稿 / `proposal` 候选 / `planned` 规划中 |
| `rarity` | 收集卡填 R / SR / SSR / SSSR / XR；功能卡填 `null` |
| `effect` | 卡做什么。休息卡是 `skip_check_in` + `skipDays: 1`；收集卡**待定** |
| `acquire` | 获得方式：`streak`（连满 5 天）/ `gacha`（抽卡；已上线卡为 `status: active`） |
| `art` | 卡面路径 + 宽高比（`aspectRatioFull` / `aspectRatioChip`） |
| `drawables` | 对应的 `R.drawable` 名；没打包就是 `null` |
| `detail` | 指向该卡的 `meta.json` |

`meta.json` 比清单详细：像素尺寸与哈希、插画内容描述、边框装饰、配色、动效节拍表、以及「这张卡是什么口径」。收集卡的 meta 里还记了**各档边框装饰的差别**（R 只有四角装饰块 → SR 描金树叶 → SSR 月桂与王冠 → SSSR 紫色蝠翼 → XR 罗盘星与云纹），换图时能对上。

## 抽卡规则（第一弹 2026-09-18；第二弹 2026-09-20）

`cards.json` 的 `gacha.rules` 会镜像关键数字，供设计归档、同步与对账，但 Android 运行时不读取它。
运行时以 `CardModels.kt` 与 `GachaRule.kt` 为准；`CardCollectionStore.kt` / `CardController.kt`
分别负责持久化与抽卡、兑换动作编排。

| 项 | 口径 |
|---|---|
| 前置条件 | 当天打卡已完成 |
| 频率 | **每弹**每天各 1 次，不累积（与打卡共用凌晨 6 点换日）；抽过第一弹不影响第二弹 |
| 稀有度 | `R < SR < SSR < SSSR < XR`（写法照设计稿，别改成 UR） |
| 概率 | R 45% / SR 28% / SSR 17% / SSSR 8% / XR 2%（初版权重，`CardRarity.weight`） |
| 单卡上限 | 每种 2 张 |
| 抽空降档 | 当前弹拿满的档位退出抽取，权重在这一弹剩下的档位之间重新归一 |
| 满池 | 当前弹 5 种各 2 张后不再可抽；不影响其它弹 |
| 整套兑换 | 当前弹集齐 5 种各 ≥1 张 → 只清空这一弹全部卡（含重复）→ 2 张休息卡 |

**运行时的系列与卡表**是 `data/card/CardModels.kt` 里的 `CardSeries` 与 `GachaCard` 枚举，
不是这份 JSON——Android 侧要 `R.drawable`，走不了运行时读文件。所以**加一张卡要改两处**：
这里登记设计资产，那边加 `GachaCard` 枚举项（`id` 必须与卡 id 一致，`artRes` 指向对应 drawable）；
新系列还要增加 `CardSeries` 与首页卡堆槽位。

抽到卡之后的展示与统计页里的 `n/2`、稀有度配色都由卡表推出来，不需要额外登记。

卡册数量虽然统一存在一个 `card_collection` DataStore、按全局卡 id 记数，但所有进度、满池与兑换
都必须显式传入 `CardSeries`。最后抽卡日用 `lastDrawEpochDays: Map<String, Long>` 按系列 id 保存；
兑换只清当前弹，不删除其它弹，也不刷新任何一弹当天的机会。新版兑换奖励数量与清卡在同一个
`card_collection` 提交中落盘，旧版 `check_in.bonus_rest_cards` 仍兼容读取；这些字段都进入完整备份。

## 加一张新卡

1. 建目录 `<系列>/<系列英文>_00N_短名/`，放 `card_full.*`（如已有 chip 一并放）。
2. 复制同系列现有 `meta.json` 改写。
3. 往 `cards.json` 对应系列的 `cards` 数组里加一条（**新系列要在 `series[]` 里新建一项**）。
4. 跑 `python .workbuddy/cards/sync_cards.py`，它会校验 JSON、卡面是否存在、**宽高比是否与清单一致**、`drawables` 在 `res/drawable-nodpi/` 是否真有，再写 assets。
5. 若这张卡要进 App：把图复制到 `res/drawable-nodpi/` 并填 `drawables`。
6. **真机/模拟器上打开一次**——卡面出错只在渲染时暴露，编译期不报。

## 休息卡的口径（代码已落地，别改错）

- 每连续**真实**打卡满 **5** 天发一张（`REST_CARD_STREAK_DAYS`，第 5/10/15… 天到手）。
- 用卡补出来的那天**不算真实打卡**：不进连续天数、不喂养发卡进度——否则补一天就能再滚出一张卡。
- 发卡是**纯推导、不落库**（`earnedRestCards` / `restCardStreakProgress`），历史怎么变都能重算，不会出现「库存有卡、记录里找不到来源」的漂移。
- 补记的起止时刻取历史**真实**记录的平均值（`averageCheckInTimes`），没有历史时兜底 09:00–17:00；不把之前补出来的时刻再平均进去。
- 记录编码第 4 段标识来源：`R` = 休息卡补记、`C` = 真实；3 段老格式照旧解得出来（当真实）。
- UI 挂在统计页（`RestCardSections.kt` / `RestCardArt.kt` / `RestCardReveal.kt`）：入口是「点某一天」，而点某一天只发生在统计页图表上。
- 测试：`RestCardRewardTest`（发卡口径）、`StatsRestCardTest`（库存与补记）。

## 待办

已上线的两弹都能抽取与兑换；以下产品项仍未决定，`cards.json` 的 `pending` 也保持同一口径：

- 保底、额外抽卡货币与后续重复卡策略；当前已经落地的是单卡 2 张上限与整弹兑换。
- 两弹收集卡的 `effect`，以及第一弹「旅程启程」的含义。
- 收集卡的 chip 缩略图；当前运行时直接使用已经处理并打包的整卡素材。
