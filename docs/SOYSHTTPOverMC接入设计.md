# SOYSGiftLoft × SOYSHTTPOverMC / MCERP 接入设计（v1.9.0）

> 本文记录 v1.8.0 → v1.9.0 的接口层改造：退役内置 JDK `HttpServer`，改为声明 SOYSHTTPOverMC
> 模块扩展；同时接入 MCERP 管理面（ERP 菜单 + 后端端点）。**本条为破坏性变更**，契约由
> `{success,data}` 迁移为 RuoYi 风格 `{code,msg,data}`，见第 5 节。

---

## 1. 目标与边界

| 项 | v1.8.0（旧） | v1.9.0（新） |
|---|---|---|
| Web 容器 | 自建 `com.sun.net.httpserver.HttpServer`（`web.WebServer`，832 行） | SOYSHTTPOverMC 网关（注解式端点） |
| 监听/端口/前缀 | 插件自管 `web.host/port/path-prefix` | 由 SOYSHTTPOverMC 统一管理，本插件不再读取 |
| 端点命名空间 | `/api/...` | `/api/plugins/SOYSGiftLoft/...`（网关自动补前缀） |
| 认证（机器 API） | 手写 `Authorization: Bearer` 校验 | `@Anonymous` + `TokenGuardedGiftLoftWebService` 装饰器（恒定时间比较） |
| 认证（ERP 管理面） | — | 网关原生登录态 + `soysgiftloft.erp.*` 权限镜像 |
| 后端管理界面 | 无 | MCERP「礼品阁」菜单（后端先行，前端页面后置） |
| 零依赖约束 | 手写 JSON | 复用 SOYS `JsonWriter` / `JsonReader`（消费 jackson-annotations，不打包） |

**保持不变**：指令 `/sgiftloft`、领取逻辑、条件树、存储层、PlaceholderAPI 变量、配置格式（除 `web` 块语义）。

---

## 2. 分层结构

```
soys.soysgiftloft
├─ spring/                              ← 新增：Web 接口层（SOYSHTTPOverMC）
│  ├─ SoysGiftLoftExpansion             模块扩展入口（机器 API，identifier=soysgiftloft）
│  ├─ controller/                       端点声明（纯转发，无业务）
│  │  ├─ GiftLoftStatusController       GET  /status
│  │  ├─ GiftLoftPackController         GET  /packs、/pack/{id}
│  │  ├─ GiftLoftPlayerController       GET  /player/{name}、/player?uuid=、POST /player/{name}/give
│  │  ├─ GiftLoftAdminController        POST /reset、/reload
│  │  ├─ ErpGiftPackController          ERP：/erp/packs、/erp/pack/{id}、POST /erp/reload
│  │  └─ ErpGiftPlayerController        ERP：/erp/player/{name}、POST /erp/give、POST /erp/reset
│  ├─ service/GiftLoftWebService        业务接口（10 个方法，全部返回 AjaxResult）
│  ├─ impl/
│  │  ├─ GiftLoftWebServiceImpl         业务实现（原 WebServer 的全部装配逻辑）
│  │  └─ TokenGuardedGiftLoftWebService  装饰器：Bearer token 门禁
│  └─ entity/vo/                        出参 VO（11）+ 入参表单（2）
└─ erp/SoysGiftLoftErpExpansion         ← 新增：MCERP 管理面扩展（identifier=soysgiftlofterp）
```

**关键设计：装饰器而非基类**

机器 API 与 ERP 端点共用同一套业务实现，但认证方式完全不同：

```java
// spring 侧：包裹 token 门禁
GiftLoftWebService machineApi = new TokenGuardedGiftLoftWebService(plugin, impl);
// erp 侧：直接用裸实现，认证交给网关 + @ApiPermission
GiftLoftWebService erpApi = new GiftLoftWebServiceImpl(plugin);
```

这样 v1.8 的调用语义（未带 token → 401）被完整保留，而 ERP 侧不会因缺 `Authorization: Bearer`
被误拦（浏览器同源 Cookie 登录态下不会有该头）。

---

## 3. 端点映射表（机器 API）

前缀：`/api/plugins/SOYSGiftLoft`（owner 插件名决定，与 `identifier` 无关）

| 方法 | 路径 | 入参 | 说明 |
|---|---|---|---|
| GET | `/status` | — | 插件版本、礼包数、主/备存储、在线人数、运行秒数 |
| GET | `/packs` | — | 礼包摘要分页对象 `{total, packs[]}` |
| GET | `/pack/{id}` | path `id` | 礼包详情（含 `conditionCount`、`rewards[]`） |
| GET | `/player/{name}` | path `name` | 在线玩家领取状态（离线返回 404） |
| GET | `/player?uuid=` | query `uuid`（必填） | 按 UUID 查询，在线/离线均可 |
| POST | `/player/{name}/give` | body `pack=<id>[&force=true]`，亦容忍 JSON | 发放礼包（玩家须在线） |
| POST | `/reset` | body `player=<name|uuid>&pack=<id>`，亦容忍 JSON | 重置领取状态 |
| POST | `/reload` | — | 重载全部配置 |

`give` / `reset` 的 `@RequestBody` 声明为 `String`，再经 `parseParams()` 双格式解析：

- `application/json`：`{"pack":"vip","force":true}` → `JsonReader.fromJson`
- `application/x-www-form-urlencoded`：`pack=vip&force=true` → `URLDecoder`

这样 v1.8 的老调用方（表单体）无需改动即可跑在新网关上。

---

## 4. 认证与授权（两套并行）

### 4.1 机器 API —— Bearer token 装饰器

```java
// TokenGuardedGiftLoftWebService.authorize()
String header = ctx.getHeaders().get("Authorization");   // 大小写不敏感
boolean ok = MessageDigest.isEqual(expected, actual);     // 恒定时间比较，防时序侧信道
```

失败统一返回 `AjaxResult.unauthorized("...")`（code 401）。token 来源 `config.yml → web.api-token`，
**每次请求实时读取**，改配置后无需重注册扩展。

### 4.2 ERP 管理面 —— 网关登录态 + 权限节点

ERP 控制器**不加** `@Anonymous`：无凭证请求被网关认证门 401 拒绝；方法上的 `@ApiPermission`
由网关做 Bukkit 权限镜像检查，未授权 403。节点同时被 `ControllerPermScanner` 投影为 ERP
菜单下的 F 按钮。

新增权限节点（`plugin.yml`，全部 `default: op`）：

| 节点 | 用途 |
|---|---|
| `soysgiftloft.erp` | ERP 管理面总权限（模块主菜单） |
| `soysgiftloft.erp.pack.list` | 礼包列表 |
| `soysgiftloft.erp.pack.query` | 礼包详情 |
| `soysgiftloft.erp.player.query` | 玩家领取状态 |
| `soysgiftloft.erp.give` | 发放礼包 |
| `soysgiftloft.erp.reset` | 重置领取状态 |
| `soysgiftloft.erp.reload` | 重载配置 |

---

## 5. 契约变更（破坏性）

### 5.1 包体形状

| | v1.8.0 | v1.9.0 |
|---|---|---|
| 成功 | `{"success":true,"data":{...}}` | `{"code":200,"msg":"操作成功","data":{...}}` |
| 失败 | `{"success":false,"error":"原因"}` | `{"code":400/404/401/500,"msg":"原因","data":...}` |

迁移依据：SOYSHTTPOverMC 的 `AjaxResult` 继承 `HashMap`，序列化即为 `code/msg/data` 三键，
与 RuoYi 前端（若依）默认响应解包逻辑一致，便于直接接 ERP 前端。

### 5.2 字段序与条件键

为让 `data` 内部结构与 v1.8 **逐字节兼容**，各 VO 的 getter 声明顺序严格对齐旧
`LinkedHashMap` 的 key 插入序（`JsonWriter` 按 getter 反射顺序输出）。条件键用
`@JsonInclude(NON_NULL)` 复刻旧实现的「按需剔除」行为。

> **已知差异（唯一）**：`CUSTOM_ITEM` 奖励在依赖缺失时，v1.8 输出 `"plugin":null`，
> v1.9 因 `NON_NULL` 直接省略该键。属良性差异，消费方应容忍缺键。

### 5.3 业务失败的表达

`give` / `reset` 的**业务失败**（如已领取、条件未满足）语义：

- v1.8：HTTP 200 + `success:false` + `error` + `result`
- v1.9：`code=500` + `msg=失败原因` + `data={player,pack,force,result}`（`result` 保留机器可读枚举）

即"HTTP 层仍成功、应用层标记失败"，`result` 字段保证程序化判断不需要解析中文文案。

---

## 6. 注册时机与生命周期

```
SOYSGiftLoft.onEnable()
 └─ step 11: setupWebApi()
     ├─ web.enabled == false                → 直接返回（插件其余功能不受影响）
     ├─ SOYSHTTPOverMC 未启用               → 告警「安装后 /sgiftloft reload 即可挂载」
     ├─ registerSoysExpansion()             ← 幂等（soysExpansion != null 则跳过）
     │   └─ new SoysGiftLoftExpansion(this).register()
     └─ registerErpExpansion()
         ├─ MCERP 未启用                    → 静默跳过
         └─ new SoysGiftLoftErpExpansion(this).register()
```

**三类补挂路径**（覆盖所有加载顺序）：

1. **SOYS 先启用**（softdepend 正常情形）→ `onEnable` 内直接注册成功；
2. **SOYS 后启用** → 主类 `implements Listener` 监听 `SoysReadyEvent`（`EventPriority.MONITOR`），
   在网关就绪后补挂；
3. **运行时开关切换** → `/sgiftloft reload` 时 `web.enabled` 由关转开会补挂、由开转关会 `unregister()`。

`onDisable()` 按 ERP → 机器 API 顺序反注册（端点与 ERP 菜单同步摘除），再落盘玩家数据。

### 类加载安全（重要）

`SoysGiftLoftErpExpansion` 的父类 `McerpExpansion` 来自 MCERP 插件。若服务器没装 MCERP，
对该类的首次**主动引用**会抛 `NoClassDefFoundError`。因此：

- `plugin.yml` 的 `softdepend` 含 `MCERP`，保证 Bukkit 的 `PluginClassLoader` 能跨插件共享
  `McerpExpansion` 的**同一个** Class 对象（否则 `McerpExpansion.mcerp` 静态字段会出现双份，
  ERP 登记永远看不到本模块）；
- `registerErpExpansion()` 内先 `isPluginEnabled("MCERP")` 守卫，再 `new`，
  未安装时压根不触发类解析。

---

## 7. ERP 管理面（MCERP）

`erp/SoysGiftLoftErpExpansion extends McerpExpansion`：

| 声明钩子 | 值 |
|---|---|
| `getIdentifier()` | `soysgiftlofterp` |
| `displayName()` | 礼品阁 |
| `icon()` / `sortOrder()` | `gift` / `80` |
| `permission()` | `soysgiftloft.erp` |
| `menus()` | `dir("giftloft","礼品阁")` → `menu("pack","礼包管理",ErpGiftPackController.class)` / `menu("player","玩家查询",ErpGiftPlayerController.class)` |

`menu(..., Controller.class)` 让 `ControllerPermScanner` 自动把控制器上的 `@ApiPermission`
方法投影为 F 按钮，**无需手写按钮表**。

**`onRegister()` 容错**：MCERP 未就绪时 `McerpExpansion.onRegister()` 静默跳过（返回 true，
不阻断端点注册），待 `/mcerp reload` 或 `/soyshttp reload` 兜底补登记。

### 待交付（下一阶段）

- [ ] ERP 前端页面：`/web/plugins/SOYSGiftLoft/...` 下的礼包管理 / 玩家查询两个路由
      （RuoYi-Vue 子应用，wujie 微前端模式，`componentMode=WUJIE` 已就位）；
- [ ] `give` / `reset` 的前端二次确认与批量操作；
- [ ] ERP 侧审计日志（谁在什么时刻发放了哪个礼包）——当前仅记录到插件日志。

---

## 8. 配置与依赖

### config.yml `web` 块

```yaml
web:
  # 是否向 SOYSHTTPOverMC 注册管理接口端点
  enabled: false
  # 机器 API 令牌：Authorization: Bearer <api-token>
  api-token: "change-me"
  # 以下为 v1.8 内置 HttpServer 遗留配置，已不再读取（保留仅为兼容旧文件）
  host: "127.0.0.1"
  port: 8317
  path-prefix: "/api"
```

### pom.xml

| 依赖 | scope | 说明 |
|---|---|---|
| `soyshttpovermc` 1.4.1 | `system` → `../SOYSHTTPOverMC/core/target/SOYSHTTPOverMC-1_12-1.4.1.jar` | 注解、`AjaxResult`、`SoysExpansion`、JSON 读写 |
| `MCERP` 1.0-SNAPSHOT | `system` → `../MCERP/target/original-MCERP-1.0-SNAPSHOT.jar` | `McerpExpansion` / `ErpMenus` / `ControllerPermScanner` |

> 引用 MCERP 的 **original**（shade 前）jar：其中不含内联的 SOYS / jackson 传递依赖，
> 避免与上一条依赖产生重复类。构建产出的插件 jar 已核验**不含**任何 `com/github/cocosoys/**`
> 或 `com/fasterxml/**` 类（shade 不打包 `system` scope 依赖）。

---

## 9. 验证清单

```bash
# 1) 状态（需先在 config.yml 打开 web.enabled 并设置 api-token）
curl -H "Authorization: Bearer <token>" \
     http://127.0.0.1:<网关端口>/api/plugins/SOYSGiftLoft/status

# 2) 礼包列表
curl -H "Authorization: Bearer <token>" \
     http://127.0.0.1:<port>/api/plugins/SOYSGiftLoft/packs

# 3) 表单体发放（v1.8 兼容写法）
curl -X POST -H "Authorization: Bearer <token>" -d "pack=vip&force=true" \
     http://127.0.0.1:<port>/api/plugins/SOYSGiftLoft/player/Steve/give

# 4) JSON 体发放
curl -X POST -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
     -d '{"pack":"vip","force":true}' \
     http://127.0.0.1:<port>/api/plugins/SOYSGiftLoft/player/Steve/give

# 5) 未带 token → code 401
curl -i http://127.0.0.1:<port>/api/plugins/SOYSGiftLoft/status

# 6) ERP 端点（无 @Anonymous）→ 无登录态时被网关 401 拒绝
curl -i http://127.0.0.1:<port>/api/plugins/SOYSGiftLoft/erp/packs
```

编译/打包：

```powershell
$env:JAVA_HOME="D:\WorkTools\JDK\8"
& "D:\WorkTools\Maven\apache-maven-3.9.9\bin\mvn.cmd" -B -o clean package
```

---

## 10. 遗留与后续

- [x] 内置 HttpServer 退役（`web` 包已删除）
- [x] 业务逻辑零重复（机器 API / ERP 共用 `GiftLoftWebServiceImpl`）
- [x] `doReset` 补补偿事务（保存失败回滚已领取状态，与 `claim` / `adminGive` 对齐）
- [x] 传输层主线程调度切换（`onMainThread`，5s 超时；网关 worker 线程不直碰 Bukkit API）
- [ ] `db.Tx.run` 事务化（当前存储层仅"先落盘后发放 + 失败补偿"，非真事务）
- [ ] ERP 前端页面与审计日志
