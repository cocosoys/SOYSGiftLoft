# SOYSGiftLoft 礼品阁

> 基于 Spigot 1.12.2 的礼品阁插件——玩家满足解锁条件后可领取礼包（金币 / 点券 / 物品 / 指令 / 经验 / 药水 / 自定义物品 / 随机抽奖）。

[![Build](https://img.shields.io/badge/build-Maven-blue.svg)](https://maven.apache.org/)
[![Java](https://img.shields.io/badge/java-8-orange.svg)](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html)
[![Platform](https://img.shields.io/badge/platform-Spigot%201.12.2-green.svg)](https://www.spigotmc.org/)
[![Version](https://img.shields.io/badge/version-1.7.0-brightgreen.svg)](#)

---

## 目录

- [功能特性](#功能特性)
- [安装方法](#安装方法)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
  - [主配置 config.yml](#主配置-configyml)
  - [礼包定义 giftpacks.yml](#礼包定义-giftpacksyml)
  - [多语言消息](#多语言消息)
- [指令与权限](#指令与权限)
- [PlaceholderAPI 变量](#placeholderapi-变量)
- [存储架构](#存储架构)
- [兼容插件](#兼容插件)
- [从源码构建](#从源码构建)
- [版本历史](#版本历史)
- [许可证](#许可证)

---

## 功能特性

### 解锁条件（11 种 + 递归与/或条件树）

| 条件类型 | 说明 |
|---------|------|
| `PLAYTIME` | 累计在线时长（秒） |
| `PERMISSION` | 拥有指定权限节点 |
| `POINTS` | 点券余额 ≥ 数值（需 PlayerPoints） |
| `MONEY` | 金币余额 ≥ 数值（需 Vault） |
| `PLACEHOLDER` | 解析占位符后做数值比较（需 PlaceholderAPI） |
| `DATE` | 在指定日期区间内（节日礼包） |
| `ONLINE_DAYS` | 累计在线满 N 天 |
| `JOIN_DAYS` | 加入服务器满 N 天 |
| `STAT` | Bukkit 内置统计（击杀/死亡/挖掘等，支持 entity/material 维度） |
| `PERMGROUP` | 属于某权限组（按 `group.<组名>` 权限约定） |
| `WORLD` | 身处指定世界之一 |
| `GAMEMODE` | 游戏模式为指定之一 |

**复合逻辑**：`unlock-conditions` 支持递归的「与/或」条件树，`mode: AND/OR` 分组可任意层级嵌套，同时向后兼容旧式扁平列表（全部 AND）。

```yaml
unlock-conditions:
  - type: WORLD
    value: world
  - type: GAMEMODE
    value: SURVIVAL
  - mode: OR          # 嵌套子组：满足任一即可
    list:
      - type: MONEY
        value: 5000
      - type: PERMGROUP
        value: vip
```

### 奖励类型（8 种）

| 奖励类型 | 说明 |
|---------|------|
| `MONEY` | 发放金币（Vault） |
| `POINTS` | 发放点券（PlayerPoints） |
| `ITEM` | 发放物品（支持自定义名称/描述/附魔） |
| `COMMAND` | 执行指令（支持 `%player%` 替换，控制台/玩家身份） |
| `EXP` | 发放经验（点数或等级） |
| `POTION` | 药水效果（效果/时长/等级/环境/粒子） |
| `CUSTOM_ITEM` | 第三方自定义物品（ItemsAdder / Oraxen / MMOItems，运行时反射对接） |
| `RANDOM` | 权重抽奖池（rolls/replace，支持嵌套 RANDOM） |

### 领取模式（5 种）

| 模式 | 说明 | 冷却窗口 |
|------|------|---------|
| `ONCE` | 一次性，领取后永久不可再领（默认） | — |
| `DAILY` | 每日礼包 | 滚动 24 小时 |
| `WEEKLY` | 每周礼包 | 滚动 7 天 |
| `MONTHLY` | 每月礼包 | 滚动 30 天 |
| `COOLDOWN` | 自定义冷却 | `cooldown` 字段指定秒数 |

> 周期性礼包统一以「距上次领取的秒数 < 窗口」判定，避免跨时区/跨日历边界的复杂处理。

### 其他特性

- **领取特效与提示音**：每个礼包可独立配置音效/粒子/全服广播，缺省时回退到全局默认
- **自动解锁提示**：玩家在线满足解锁条件时自动私聊 + 标题提示（每礼包每会话仅提示一次）
- **多语言支持**：内置中文/英文，三层 fallback（当前语言 → legacy message.yml → 内置 zh）
- **软依赖降级**：Vault / PlayerPoints / PlaceholderAPI 全部为 softdepend，缺失时自动降级并明确告警，不影响插件加载
- **配置校验**：加载时校验礼包配置合法性，`[错误]` 致命跳过该礼包，`[警告]` 可继续，启动日志明确
- **PlaceholderAPI 扩展**：提供 13+ 个变量（状态/进度/冷却/统计等）
- **领取原子性保证**：先记录领取状态再发放奖励，发放异常时自动回滚，杜绝重复领取漏洞

---

## 安装方法

### 环境要求

- Java 8 或更高版本
- Spigot / Paper 1.12.2 服务端

### 安装步骤

1. 从 [Releases](https://github.com/your-repo/SOYSGiftLoft/releases) 下载最新版 `SOYSGiftLoft-x.x.x.jar`
2. 将 jar 文件放入服务端的 `plugins/` 目录
3. 启动服务端，插件会自动生成配置文件（`plugins/SOYSGiftLoft/`）
4. 根据需要修改 `config.yml` 和 `giftpacks.yml`
5. 执行 `/sgiftloft reload` 重载配置（或重启服务端）

### 可选依赖（推荐安装）

| 插件 | 用途 | 下载 |
|------|------|------|
| [Vault](https://www.spigotmc.org/resources/vault.41918/) | 经济系统（MONEY 奖励/条件） | 官方页面 |
| [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.76252/) | 点券系统（POINTS 奖励/条件） | 官方页面 |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | 占位符（PLACEHOLDER 条件 + 变量扩展） | 官方页面 |

> 以上均为软依赖，不安装时插件仍可正常加载，仅对应功能自动降级。

---

## 快速开始

安装后，`giftpacks.yml` 自带 16 个示例礼包，可直接体验：

```
# 查看礼包列表
/sgiftloft list

# 查看礼包详情
/sgiftloft info welcome

# 检查解锁状态
/sgiftloft check daily

# 领取礼包
/sgiftloft claim daily
```

---

## 配置说明

### 主配置 config.yml

```yaml
general:
  debug: false                    # 调试模式（打印更多日志）
  language: "zh"                  # 语言文件标识（内置 zh / en）
  auto-notify: 0                  # 自动解锁提示间隔（秒），0 关闭
  default-claim-effects:          # 领取特效全局默认（礼包未配置时回退）
    sound: "ENTITY_PLAYER_LEVELUP"
    volume: 1.0
    pitch: 1.0
    particle: "VILLAGER_HAPPY"
    particle-count: 16
    particle-spread: 0.5
    particle-speed: 0.1
    broadcast: false

storage:
  backends:
    mysql:
      enabled: false              # 默认禁用，按需启用
      url: "jdbc:mysql://localhost:3306/minecraft?useSSL=false&characterEncoding=utf8&serverTimezone=UTC"
      username: root
      password: ""
      table-prefix: "mc_soysgl_"
      keepalive-interval: 1800
    sqlite:
      enabled: false
      file: "data/players.db"
      table-prefix: "mc_soysgl_"
    yaml:
      enabled: true               # 默认启用，开箱即用
      file: "data/players.yml"
      backup-on-save: false
  mirror:
    enabled: true                 # 写入镜像（主存储写成功后同步到辅助存储）
    async: true                   # 异步镜像
    sync-on-startup: false        # 启动时主存储全量覆盖同步到辅助存储
  memory:
    auto-save-interval: 300       # 自动保存间隔（秒），0 关闭
  migrate-on-startup: true        # 启动时 YAML → MySQL 自动迁移（仅 MySQL 为主存储时）
```

### 礼包定义 giftpacks.yml

每个礼包的完整结构：

```yaml
giftpacks:
  example_pack:
    display: "&a示例礼包"              # 显示名称（支持 & 颜色代码）
    description: "这是一个示例礼包"     # 一句话描述
    claim-mode: ONCE                   # 领取模式：ONCE/DAILY/WEEKLY/MONTHLY/COOLDOWN
    cooldown: 0                        # 仅 COOLDOWN 模式有意义（秒）
    unlock-conditions:                 # 解锁条件（扁平列表 = 全部 AND）
      - type: PLAYTIME
        value: 3600
    rewards:                           # 奖励列表（领取时依次发放）
      - type: MONEY
        value: 1000
      - type: ITEM
        material: DIAMOND
        amount: 2
        name: "&b欢迎钻石"
        lore:
          - "&7感谢你来到服务器！"
    claim-effects:                     # 领取特效（可选，缺省回退全局默认）
      sound: "ENTITY_PLAYER_LEVELUP"
      volume: 1.0
      pitch: 1.0
      particle: "VILLAGER_HAPPY"
      particle-count: 20
      broadcast: false
```

> 完整的 16 个示例礼包（含每日签到、周礼包、节日礼包、随机转盘、自定义物品等）见 `giftpacks.yml`。

### 多语言消息

- `lang/zh.yml`：简体中文（默认）
- `lang/en.yml`：英文
- `message.yml`：旧版兼容层（升级前的自定义消息会保留）

修改 `config.yml` 中的 `general.language` 切换语言，也可自行新增 `lang/xx.yml` 扩展其他语言。

---

## 指令与权限

### 玩家指令

| 指令 | 说明 | 权限 |
|------|------|------|
| `/sgiftloft list` | 查看礼包列表及状态 | `soysgiftloft.use` |
| `/sgiftloft info <id>` | 查看礼包详情（条件/奖励/模式） | `soysgiftloft.use` |
| `/sgiftloft check <id>` | 检查自身解锁状态及未满足条件 | `soysgiftloft.use` |
| `/sgiftloft claim <id>` | 领取礼包 | `soysgiftloft.use` |

> 指令别名：`/sgift`、`/sgl`

### 管理员指令

| 指令 | 说明 | 权限 |
|------|------|------|
| `/sgiftloft reload` | 重载全部配置 | `soysgiftloft.admin.reload` |
| `/sgiftloft give <玩家> <id>` | 强制发放礼包（绕过条件/领取状态） | `soysgiftloft.admin.give` |
| `/sgiftloft reset <玩家> <id>` | 重置玩家礼包领取状态 | `soysgiftloft.admin.reset` |
| `/sgiftloft migrate <目标后端> [源后端]` | 后端间数据迁移（yaml/mysql/sqlite） | `soysgiftloft.admin.migrate` |

### 权限节点

| 权限节点 | 说明 | 默认 |
|---------|------|------|
| `soysgiftloft.use` | 使用玩家指令 | true |
| `soysgiftloft.admin` | 管理员总权限 | op |
| `soysgiftloft.admin.reload` | 重载配置 | op |
| `soysgiftloft.admin.give` | 强制发放 | op |
| `soysgiftloft.admin.reset` | 重置领取状态 | op |
| `soysgiftloft.admin.migrate` | 数据迁移 | op |
| `soysgiftloft.pack.<id>` | 可用于 `PERMISSION` 条件的自定义权限 | 无 |

---

## PlaceholderAPI 变量

安装 PlaceholderAPI 后，本插件自动注册 `sgiftloft` 标识符的变量：

| 变量 | 说明 |
|------|------|
| `%sgiftloft_state_<id>%` | 礼包状态：`locked` / `available` / `claimed` |
| `%sgiftloft_total%` | 礼包总数 |
| `%sgiftloft_unlocked%` | 当前玩家已解锁数 |
| `%sgiftloft_claimed%` | 当前玩家已领取数 |
| `%sgiftloft_claimable%` | 当前玩家可立即领取数（已解锁且不在冷却中） |
| `%sgiftloft_locked%` | 当前玩家未解锁数 |
| `%sgiftloft_playtime%` | 累计在线时长（可读文本） |
| `%sgiftloft_playtime_raw%` | 累计在线秒数 |
| `%sgiftloft_progress_<id>%` | 解锁进度（0~100 整数百分比） |
| `%sgiftloft_progress_raw_<id>%` | 解锁进度（0.00~1.00 小数） |
| `%sgiftloft_cooldown_<id>%` | 距下次可领取的剩余秒数（无冷却为 0） |
| `%sgiftloft_unmet_<id>%` | 尚未满足的条件数量 |
| `%sgiftloft_by_condition_<TYPE>%` | 使用了指定条件类型的礼包数（如 `PLAYTIME`、`MONEY`） |

---

## 存储架构

本插件采用**主备镜像多后端**存储架构：

### 后端类型

| 后端 | 优先级 | 说明 |
|------|--------|------|
| MySQL | 30（最高） | 跨服共享玩家数据，支持连接保活 |
| SQLite | 20 | 本地文件数据库，零配置 |
| YAML | 10（最低） | 纯文本文件，开箱即用，便于手动编辑 |

### 工作原理

1. **主存储选举**：所有已启用后端中优先级最高者成为主存储，承担全部读操作
2. **辅助镜像**：其余启用后端作为辅助存储，写入时被镜像同步，充当热备份与降级方案
3. **串行写入**：所有写操作收敛到单线程执行器，保证同一玩家的写操作严格有序，避免并发错乱
4. **自动降级**：主存储初始化失败时自动降级到下一优先级后端
5. **跨后端迁移**：支持启动时自动迁移（YAML → MySQL）或手动 `/sgiftloft migrate` 迁移

### 数据结构

每个玩家记录包含：
- `uuid`：玩家 UUID
- `playtime`：累计在线秒数
- `claimed`：礼包 ID → 最近领取时间戳（毫秒）的 Map

> JDBC 驱动（MySQL Connector/J / SQLite JDBC）由服务端提供（provided scope），不打包进插件，避免 jar 膨胀与驱动冲突。

---

## 兼容插件

### 经济/点券

- **Vault**：MONEY 奖励与条件
- **PlayerPoints**：POINTS 奖励与条件

### 占位符

- **PlaceholderAPI**：PLACEHOLDER 条件 + 13+ 个变量扩展

### 自定义物品（运行时反射对接，无编译期依赖）

- **ItemsAdder**：`plugin: ITEMSADDER`
- **Oraxen**：`plugin: ORAXEN`
- **MMOItems**：`plugin: MMOITEMS`（需指定 `mmo-type`）
- **MMOCore**：`plugin: MMOCORE`（同 MMOItems 接口）

> 自定义物品插件未安装或物品不存在时仅告警，不中断整体领取。

---

## 从源码构建

### 环境要求

- JDK 8
- Maven 3.6+

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/your-repo/SOYSGiftLoft.git
cd SOYSGiftLoft

# 构建（跳过测试）
mvn clean package -DskipTests

# 产物位置
# target/SOYSGiftLoft-x.x.x.jar
```

### CI/CD

- 推送 tag（`v*`）时自动构建并发布 GitHub Release
- Push / PR 时自动编译检查（见 `.github/workflows/build.yml`）

---

## 版本历史

### v1.7.0 — 软依赖降级与 PlaceholderAPI 可选
- plugin.yml 由 `depend` 改为 `softdepend`，Vault / PlayerPoints / PlaceholderAPI 缺失时插件仍可加载
- 新增 `GiftLoftListener` 监听 `PluginEnableEvent` 懒加载挂钩
- `parsePlaceholder` 增设守卫避免 PAPI 缺失时 `NoClassDefFoundError`
- 加载时对含 MONEY/POINTS/PLACEHOLDER 但依赖缺失的礼包打印明确告警
- 发放 MONEY/POINTS 奖励而依赖缺失时调用 `notifyMissingDependency`（控制台去重告警 + 玩家友好提示）
- 启动时汇总一行依赖状态

### v1.6.0 — 命令与运维增强
- 命令补全增强（中心化权限门禁，Tab 按权限过滤子指令）
- PlaceholderAPI 新增 7 个变量（progress / claimable / locked / cooldown / unmet / by_condition）
- 配置校验与热修复（[错误]致命跳过 / [警告]可继续）
- 存储迁移命令 `/sgiftloft migrate`

### v1.5.0 — 奖励扩展
- 新增 EXP / POTION / CUSTOM_ITEM / RANDOM 四种奖励类型
- CUSTOM_ITEM 运行时反射对接 ItemsAdder / Oraxen / MMOItems
- RANDOM 权重抽奖池支持嵌套
- 背包空位计算统一为 `Reward.requiredSlots()`

### v1.4.0 — 解锁条件扩展
- 条件系统重构为递归「与/或」条件树（ConditionNode + ConditionGroup）
- 新增 DATE / ONLINE_DAYS / JOIN_DAYS / STAT / PERMGROUP / WORLD / GAMEMODE 七种条件
- 向后兼容旧扁平列表

### v1.3.0 — 玩家体验增强
- 自动解锁提示（定时轮询 + 每会话去重）
- 领取冷却/限购（ONCE/DAILY/WEEKLY/MONTHLY/COOLDOWN）
- 领取特效与提示音（per-pack + 全局默认 + 广播）
- 多语言支持（lang/zh.yml + lang/en.yml，三层 fallback）
- 指令 `giftloft` → `sgiftloft`

### v1.2.0 — 存储架构升级
- 移植 SOYSLinkTeam 的 storage 架构
- 主/备镜像多后端（YAML/SQLite/MySQL 三选）
- StorageManager 统一编排（启动迁移/同步、keepalive、异步镜像写）
- JDBC 驱动改为服务端提供（provided）

### v1.1.0 — 存储抽象层
- messages 拆分为独立 message.yml
- 存储抽象层（DataStorage 接口 + PlayerRecord 中间模型）
- 实现 YamlStorage 与 MySQLStorage，支持 YAML↔MySQL 互相迁移

### v1.0.0 — 初始版本
- 配置文件、领取逻辑、指令与权限、PlaceholderAPI 变量、在线时长统计

---

## 许可证

本项目仅供学习与个人服务器使用。

---

## 致谢

感谢以下开源项目：

- [SpigotMC](https://www.spigotmc.org/) — 插件 API
- [Vault](https://github.com/MilkBowl/Vault) — 经济系统抽象
- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) — 占位符框架
- [PlayerPoints](https://github.com/BlackIXT/PlayerPoints) — 点券系统
