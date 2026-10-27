# Controller 规范化整改范围分析（SOYSGiftLoft 工作间）

> 状态：**先分析，未改动任何代码**。分析范围仅限当前工作间 SOYSGiftLoft（22 个 java 文件，v1.8.0）。
> 用户四条规范化规则见 §2；本文档给出逐条审计结果（带文件行号）、目标结构、决策点与改动清单。

---

## 1. 现状盘点

| 层 | 现状 | 行数 | 问题定性 |
|----|------|------|---------|
| `web/WebServer.java` | JDK 内置 `HttpServer` 手写 REST：dispatch 路由 + 8 个端点处理器 + 数据装配 + JSON 序列化全在一个类 | 832 | **事实上的 controller 层，但混杂业务逻辑、私有工具方法、手撕 Map VO** |
| `manager/GiftLoftManager.java` | 具体类（无接口）：礼包加载、claim/adminGive、冷却、在线时长 | 526 | 事实上的 service+impl 合体，业务已基本内聚 |
| `storage/` | `DataStorage` 接口 + Yaml/Sqlite/Mysql/Sql 四实现 + StorageManager | ~950 | **无任何事务 API**（仅 save/异步保存/迁移/同步） |
| `model/` | GiftPack / Reward / UnlockCondition / ConditionNode / ConditionGroup | ~1470 | 领域实体，健全 |
| `command/GiftLoftCommand.java` | 指令层，10 个 private 方法 | 429 | 命令分发形态正当（不在 controller 规则范围） |
| `spring` 包 / `entity.vo` / `service` 接口 | **均不存在** | — | 规范化目标结构，需新建 |

pom.xml 依赖（L87-139）：spigot-api / VaultAPI / playerpoints / placeholderapi / mysql / sqlite，全部 provided。**无 spring-boot-starter-web**（符合约束）；**当前也无 SOYSHTTPOverMC 依赖**（见 §4 决策点）。

---

## 2. 规则逐条审计

### 2.1 规则 1：禁止手撕 Map 临时 VO，应实体化到 `entity.vo` ❌ 10 处

全部集中在 `web/WebServer.java`。响应用 `Map<String,Object>` + `LinkedHashMap` 逐键 put 装配，替代 typed VO：

| # | 位置 | 手撕内容（键数） | 目标 VO |
|---|------|-----------------|---------|
| V1 | `WebServer.java:222-246` `handleStatus` | plugin/version/packs/primaryStorage/secondaryStorages/onlinePlayers/uptimeSeconds（7） | `StatusVO` |
| V2 | `WebServer.java:244-261` `handlePacks` | total/packs（2） | `PackListVO` |
| V3 | `WebServer.java:259-296` `handlePackDetail` | id/display/description/claimMode/cooldown/requiredSlots/conditionTypes/conditionCount/rewardCount/rewards（10） | `PackDetailVO` |
| V4 | `WebServer.java:434-461` `packSummary` | id/display/description/claimMode/cooldown/requiredSlots/conditionTypes/rewardCount（9） | `PackSummaryVO` |
| V5 | `WebServer.java:456-505` `rewardSummary` | type + 按类型分支字段（money/points/exp*/material/amount/command/console/effect/.../rolls/poolSize） | `RewardSummaryVO`（全字段并集，按 type 置空） |
| V6 | `WebServer.java:501-528` `buildPlayerJson` | name/uuid/online/playtimeSeconds/packs（6） | `PlayerStatusVO` |
| V7 | `WebServer.java:542-582` `packStatus` | id/display/claimed/cooldownRemaining/state/unlocked（6） | `PackStatusVO` |
| V8 | `WebServer.java:369-382` `handleGive` 成功分支 | player/pack/force/result（4） | `GiveResultVO` |
| V9 | `WebServer.java:408-420` `handleReset` | player/pack/result（3） | `ResetResultVO` |
| V10 | `WebServer.java:421-431` `handleReload` | result（1） | `ReloadResultVO` |

**明确不是违规（不要动）**：

- `WebServer.java:671/691` `parseBody`/`parseQuery` 的 `Map<String,String>`——HTTP 输入解析，输入 DTO 的正确形态；
- `WebServer.java:754-790` `jsonVal`——JSON 序列化基础设施；
- `model/` 各 `fromMap`（GiftPack/Reward/UnlockCondition/ConditionNode）——YAML 配置解析，配置源本就是 Map 结构；
- `GiftLoftCommand.java:28` `SUB_PERMISSION`、`GiftLoftManager` 的 packs/joinTime/notifiedPacks、`PlayerRecord.claimed`、`SqlStorage/YamlStorage` 内部缓存——内部状态，非响应 VO。

用户示例中的 `newCaptcha`/`ok.put("uuid",...)` 形状在本工作间的现存对应物即上表 10 处。

### 2.2 规则 2：禁止在 controller 中做具体逻辑，应经 service 抽象层 ❌ 8 处

`WebServer` 的 8 个端点处理器全部内联业务/装配逻辑，直接操作 `plugin.getManager()` / `plugin.getStorageManager()` / Bukkit API：

| # | 位置 | 内联逻辑 | 下沉目标 |
|---|------|---------|---------|
| L1 | `handleStatus` L222-246 | 读 manager/storageManager 状态、遍历辅助存储、算 uptime | `WebAdminServiceImpl.status()` |
| L2 | `handlePacks` L244-261 | 遍历 packs 装配摘要列表 | `impl.packs()` |
| L3 | `handlePackDetail` L259-296 | 条件树 `collectTypes` 遍历 + 奖励摘要装配 | `impl.packDetail(id)` |
| L4 | `handlePlayerByName`/`handlePlayerByUuid` L298-347 | `loadPlayer`、记录缺失兜底（new PlayerRecord）、UUID 解析 | `impl.playerStatus(name/uuid)` |
| L5 | `handleGive` L349-387 | force 分支 + claim/adminGive 调用编排 | `impl.give(name, packId, force)` |
| L6 | `handleReset` L383-420 | resolveUuid + resetClaimed + save 序列 | `impl.reset(player, packId)` |
| L7 | `handleReload` L421-435 | `plugin.reloadAll()` | `impl.reload()` |
| L8 | `buildPlayerJson`/`packStatus` L501-582 | **领取状态机推导**（claimed/cooldown/unlocked → CLAIMED/AVAILABLE/LOCKED/COOLDOWN/UNKNOWN 五态） | `impl.packStatus(...)`（最重的一块业务逻辑） |

现状 `GiftLoftManager` 已提供 claim/adminGive/resetClaimed/getCooldownRemaining/getPlaytime 等原子业务能力——service 层应是**对其之上的 Web 用例编排抽象**，不是重写业务。

### 2.3 规则 3：controller 中不应存在 private 工具方法 ❌ 7 处业务 helper（另有 10 处传输基础设施可留）

`WebServer` 全部 private 方法分两类：

**A. 业务 helper——按规则应下沉 impl（7 处）**：

| 方法 | 位置 | 下沉后归属 |
|------|------|-----------|
| `packSummary` | L434 | impl（PackSummaryVO 装配） |
| `rewardSummary` | L456 | impl（RewardSummaryVO 装配） |
| `buildPlayerJson(String,PlayerRecord,Player)` | L501 | impl（PlayerStatusVO 装配） |
| `buildPlayerJson(String)` | L521 | impl |
| `packStatus` | L542 | impl（状态机） |
| `resolveUuid` | L575 | impl（或 util） |
| `claimResultMessage` | L595 | impl（ClaimResult→文案映射） |

**B. 传输基础设施——属传输层职责，保留但应独立成类（不算违规，10 处）**：
`dispatch` L148、`authenticate` L616、`onMainThread` L635、`parseBody` L671、`parseQuery` L691、`decode` L719、`sendJson` L723、`okJson` L723-736、`errJson` L738、`jsonObj` L742、`jsonVal` L754、`esc` L793。这些是 HTTP 机制（认证/主线程调度/表单解析/JSON 序列化），不是业务工具方法；规范化后应集中到独立的传输类（如 `web/WebTransport`），薄控制器只做路由→调 service→转 JSON。

> 附注（不在规则范围但同觉问题）：`GiftLoftCommand.countFree` L381 与 `GiftLoftManager.countFreeSlots` L284 逻辑重复，可顺手收敛。

### 2.4 规则 4：impl 负责 service 抽象层的事务 ⚠️ 部分落地，口径不统一

本插件存储层为自研 `DataStorage`，**无事务 API**（scan 确认 StorageManager 仅有 save/savePlayersAsync/syncToSecondaries/migrate）。现有多步写入的原子性形态：

| # | 位置 | 多步写入 | 现状 |
|---|------|---------|------|
| T1 | `GiftLoftManager.claim` L316-346 | setClaimed → save → grantRewards → 异常时 resetClaimed+save | ✅ 已有「先落盘后发放 + 异常回滚」补偿式原子性（好实践） |
| T2 | `GiftLoftManager.adminGive` L352-377 | 同上 | ✅ 同 T1 |
| T3 | `WebServer.handleReset` L383-420 | resetClaimed → save | ❌ 无回滚保护：save 失败时内存态已改、落盘失败，窗口内重启丢重置 |
| T4 | `StorageManager.migrate` L390 / `syncToSecondaries` L410 | 跨后端批量迁移/同步 | ⚠️ 大体量多步，异常中断留半成品数据（运维命令，低频） |

「impl 负责事务」在本工作间的落地形态（无 DB.Tx 类设施可用）：

1. **统一补偿式原子性口径**：T3 的 resetClaimed+save 序列应收敛为 `GiftLoftManager` 的一个方法（如 `resetClaimedAndSave(uuid, packId)`），与 claim/adminGive 同一「先落盘/失败可回滚」口径；
2. T1/T2 保持现状（已是本插件能实现的最强原子性），改造时原样迁入 impl；
3. T4 迁移/同步建议增加「失败即整体放弃 + 明确错误返回」（或至少不在半途状态留下可用数据），优先级低。

---

## 3. 目标结构设计

```
soys.soysgiftloft.spring
├── controller/                  # 薄控制器：路由分发 + 参数绑定 + 调 service + VO→JSON
│   └── WebAdminController.java  #   8 个端点方法，每个 ≤5 行，零业务零 private helper
├── service/                     # 抽象接口层
│   └── WebAdminService.java     #   status/packs/packDetail/playerStatus/give/reset/reload
├── impl/                        # 实现 + 原子性编排（规则 4 落点）
│   └── WebAdminServiceImpl.java #   下沉 §2.2 全部装配逻辑 + §2.3-A 全部业务 helper + §2.4-T3 原子序列
├── entity/
│   └── vo/                      # 10 个 VO（§2.1 表）
│       ├── StatusVO / PackListVO / PackDetailVO / PackSummaryVO / RewardSummaryVO
│       └── PlayerStatusVO / PackStatusVO / GiveResultVO / ResetResultVO / ReloadResultVO
```

保留不动：`web/` 包瘦身为传输基础设施（WebServer 只留 HttpServer 生命周期 + dispatch + 认证 + onMainThread + JSON 序列化，或拆出 `web/WebTransport`）；`manager/GiftLoftManager`、`storage/`、`model/`、`command/` 全部不变。

**调用链**：`WebServer.dispatch` → `WebAdminController.xxx(参数)` → `WebAdminService.xxx()` → `WebAdminServiceImpl`（编排 GiftLoftManager + StorageManager）→ VO → controller 转 JSON。

---

## 4. 决策点（需用户拍板）

### 决策 1：传输层是否迁移到 SOYSHTTPOverMC —— ✅ 已决策：迁移（选项 2）

用户已确认引入 SOYSHTTPOverMC 获取 spring 能力。**已完成引入动作（2026-10-23）**：

| 文件 | 改动 |
|------|------|
| `pom.xml` | 新增 `com.github.cocosoys.mc:soyshttpovermc:1.4.1`（system scope，指向 `../SOYSHTTPOverMC/core/target/SOYSHTTPOverMC-1_12-1.4.1.jar`，与 MCERP 同一范式；jar 内 21 个集成类已逐一验证存在） |
| `src/main/resources/plugin.yml` | `softdepend` 增加 `SOYSHTTPOverMC`（缺失时回退内置 HttpServer 模式，不阻断加载） |

接入后能力变化：手写 dispatch/认证/JSON 序列化被注解式 API 替代（@RequestMapping/@GetMapping/@ApiPermission/@Anonymous 等 21 个注解 + AjaxResult/TableDataInfo/ApiResponse 统一返回体 + ApiRequestContext/CredentialPresentation 参数注入）；路由自动补 `/api/plugins/SOYSGiftLoft` 前缀；插件禁用时 ApiLifecycleListener 自动卸载。

> 原「选项 1（保留 HttpServer 只做分层重构）」方案作废——WebServer 的传输基础设施（dispatch/认证/onMainThread/parseBody/parseQuery/JSON 序列化）将整体退役，由网关头承担。

### 决策 2：命名空间（接入后新问题，待设计）

现端点 `/api/status` 等与 SOYS 自带端点（`/api/status`、`/api/ping`、`/api/version`、`/api/auth/*`）在全局 `/api` 命名空间**冲突**。两条路：
- **A. 标准命名空间**（推荐，MCERP 同款）：路由落 `/api/plugins/SOYSGiftLoft/*`，零冲突，但 URL 契约变更；
- **B. 代理命名空间**（buildProxyControllers 无前缀）：需改名避让（如 `/api/giftloft/status`），仍是契约变更且污染全局命名空间。

### 决策 3：认证模型映射（接入后新问题，待设计）

现网 JWT 式 Bearer api-token（config.yml web.api-token）→ SOYS 网关认证三选一：
- **A. SOYS 原生 X-API-Key**（AuthPolicy.ApiKeyStore，L288-311）：机器访问语义，端点默认受保护（无需 @Anonymous），运维在 SOYS 侧管 key；
- **B. 保留自有 Bearer token**：端点标 @Anonymous（或 auth.yml exempt），token 校验下沉 impl 层——现有调用方零改动；
- **C. 全量 SOYS 会话认证**（AuthMe 登录桥）：面向浏览器/玩家，与机器 API 语义不同。

### 决策 4：VO 序列化键名兼容

VO 化后 `LinkedHashMap` 的手工键序需与 VO 字段序一致（分析已按现状键序设计 VO 字段），保证 JSON 输出逐字节兼容。选项 1 下无兼容问题；若采用选项 2，SOYS 的 JsonWriter 序列化 VO 同样按字段序输出，但 `data` 包层级需与现契约对齐（现值就是 `data` 内嵌，天然一致）。

---

## 5. 改动清单（待确认后实施）

### P0 — 分层重构主体（规则 1/2/3）

- [ ] 新建 `spring/entity/vo/` 10 个 VO（字段序严格对齐 §2.1 各表现状键序）
- [ ] 新建 `spring/service/WebAdminService.java`（7 个用例方法）
- [ ] 新建 `spring/impl/WebAdminServiceImpl.java`（迁入 §2.2 八处装配逻辑 + §2.3-A 七个业务 helper + 状态机；编排现有 GiftLoftManager/StorageManager，不重写业务）
- [ ] 新建 `spring/controller/WebAdminController.java`（8 个端点，薄委托）
- [ ] `web/WebServer.java` 瘦身：dispatch 改为委托 controller；保留认证/onMainThread/parseBody/parseQuery/JSON 序列化等传输基础设施（可拆 `web/WebTransport`）
- [ ] 装配：`SOYSGiftLoft` 主类 L122-125 与 L306-314 处创建 controller/service/impl 并注入 WebServer（参照现有手动 DI 风格）

### P1 — 原子性口径统一（规则 4）

- [ ] `GiftLoftManager` 新增 `resetClaimedAndSave(uuid, packId)`（T3 收敛，与 claim/adminGive 同口径）
- [ ] `WebAdminServiceImpl.reset` 改调该方法
- [ ] （低优先）`StorageManager.migrate/syncToSecondaries` 失败即整体放弃语义评估

### P2 — 顺带收敛（不在规则范围）

- [ ] `GiftLoftCommand.countFree` 与 `GiftLoftManager.countFreeSlots` 去重

### 验证闭环（既定流程）

- [ ] `mvn package` 通过（JDK 8 + 本机 `mvn.cmd` 规则）
- [ ] 部署 1.12.2 实机测试服，web.enabled=true 启动，逐端点比对重构前后 JSON 响应（键名/键序/值）一致：status / packs / pack/<id> / player/<name> / player?uuid= / give / reset / reload
- [ ] 401（无 token/错 token）/400（缺参）/404（不存在）/405（方法错）错误分支回归
- [ ] give/reset 实机发放与重置验证（在线玩家 + UUID 离线重置）

---

## 6. 影响面与风险

| 项 | 评估 |
|----|------|
| Web API 契约 | **零变化**（选项 1）：VO 字段序对齐现状键序，`{success,data}/{success,error}` 包不变 |
| 新增依赖 | 零（选项 1） |
| 改动文件数 | 新增 ~13 个（10 VO + service + impl + controller）；修改 2 个（WebServer 瘦身、SOYSGiftLoft 装配） |
| 行为回归风险 | 低-中：状态机与装配逻辑原样迁移，主要风险在键序/字段遗漏——靠逐端点 JSON 比对兜底 |
| 性能 | 无变化（仍走 onMainThread 调度；VO 替代 LinkedHashMap 略减开销） |
| 其他入口 | command 层、placeholder 层不触碰，无影响 |

---

## 7. 明确不在范围

- `command/GiftLoftCommand.java`：指令层非 spring.controller，其 private 方法为命令分发正当形态（仅 P2 去重一项）。
- `manager/GiftLoftManager.java`：已是内聚的具体类，本次不抽接口（如后续多实现需求再议）；其 claim/adminGive 原子性保持现状。
- `storage/`、`model/`、`config/`、`listener/`、`placeholder/`、`util/`：不动。
- plugin.yml / pom.xml：选项 1 下不动；选项 2 需新增 SOYSHTTPOverMC 依赖与 softdepend（待决策 1）。
