package soys.soysgiftloft.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.manager.GiftLoftManager;
import soys.soysgiftloft.model.GiftPack;
import soys.soysgiftloft.model.Reward;
import soys.soysgiftloft.model.UnlockCondition;
import soys.soysgiftloft.storage.DataStorage;
import soys.soysgiftloft.storage.PlayerRecord;
import soys.soysgiftloft.storage.StorageManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Web / 后台管理接口。
 * <p>
 * 基于 JDK 内置 {@link HttpServer}（零第三方依赖，不增加 jar 体积），
 * 提供 REST 风格 JSON 接口，用于远程查看礼包与玩家状态、发放与重置礼包、重载配置。
 * </p>
 * <p>
 * <b>认证</b>：所有请求需携带 {@code Authorization: Bearer &lt;api-token&gt;}
 * （config.yml 中 {@code web.api-token}），使用常量时间比较防止时序攻击。
 * </p>
 * <p>
 * <b>线程模型</b>：HTTP 工作线程与 Bukkit 主线程隔离。所有涉及 Bukkit API 与存储
 * 读写的操作统一经 {@link #onMainThread(Callable)} 调度到主线程执行（与指令路径一致，
 * 保证并发安全），超时 5 秒。
 * </p>
 * <p>
 * <b>端点一览</b>（前缀默认 {@code /api}）：
 * <ul>
 *   <li>GET  /api/status             —— 插件概览与健康检查</li>
 *   <li>GET  /api/packs              —— 礼包列表</li>
 *   <li>GET  /api/pack/&lt;id&gt;     —— 单个礼包详情</li>
 *   <li>GET  /api/player/&lt;name&gt; —— 在线玩家状态（离线玩家请用 uuid 查询）</li>
 *   <li>GET  /api/player?uuid=&lt;uuid&gt; —— 按 UUID 查询玩家状态（在线/离线均可）</li>
 *   <li>POST /api/player/&lt;name&gt;/give —— 发放礼包（body: pack=&lt;id&gt;[&amp;force=true]，玩家需在线）</li>
 *   <li>POST /api/reset              —— 重置领取状态（body: player=&lt;name|uuid&gt;&amp;pack=&lt;id&gt;，可离线）</li>
 *   <li>POST /api/reload             —— 重载全部配置</li>
 * </ul>
 * </p>
 */
public class WebServer {

    private final SOYSGiftLoft plugin;
    private HttpServer server;
    private ExecutorService executor;
    private final long startTime = System.currentTimeMillis();

    public WebServer(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return server != null;
    }

    /**
     * 按当前配置启动 HTTP 服务。
     */
    public void start() {
        if (isRunning()) {
            return;
        }
        String host = plugin.getConfigManager().getWebHost();
        int port = plugin.getConfigManager().getWebPort();
        String prefix = plugin.getConfigManager().getWebPathPrefix();
        String token = plugin.getConfigManager().getWebToken();
        if ("change-me".equalsIgnoreCase(token)) {
            plugin.getLogger().warning("[Web] api-token 仍为默认值 'change-me'，强烈建议修改后再开放远程访问！");
        }
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 16);
            server.createContext(prefix, this::dispatch);
            executor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "SOYSGiftLoft-Web");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("[Web] 管理接口已启动: http://" + host + ":" + port + prefix
                    + "（token 认证已启用）");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[Web] 管理接口启动失败: " + e.getMessage(), e);
            server = null;
        }
    }

    /**
     * 停止 HTTP 服务并回收线程池。
     */
    public void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        plugin.getLogger().info("[Web] 管理接口已停止。");
    }

    /**
     * reload 后按新配置重启服务（配置变化如端口/token 立即生效）。
     */
    public void reloadFromConfig() {
        boolean enabled = plugin.getConfigManager().isWebEnabled();
        if (enabled && !isRunning()) {
            start();
        } else if (enabled) {
            stop();
            start();
        } else {
            stop();
        }
    }

    // ================================================================
    //  路由分发
    // ================================================================

    private void dispatch(HttpExchange ex) {
        try {
            if (!authenticate(ex)) {
                sendJson(ex, 401, errJson("未授权：请求需携带 Authorization: Bearer <api-token>"));
                return;
            }
            String method = ex.getRequestMethod();
            String prefix = plugin.getConfigManager().getWebPathPrefix();
            String rel = ex.getRequestURI().getPath().substring(prefix.length());
            while (rel.length() > 1 && rel.endsWith("/")) {
                rel = rel.substring(0, rel.length() - 1);
            }
            String[] seg = rel.split("/");
            // seg[0] 恒为空串（rel 以 / 开头）
            if (seg.length == 1) {
                if ("GET".equals(method)) {
                    handleStatus(ex);
                } else {
                    sendJson(ex, 405, errJson("方法不允许，该端点仅支持 GET"));
                }
                return;
            }
            switch (seg[1]) {
                case "packs":
                    if (seg.length == 2 && "GET".equals(method)) {
                        handlePacks(ex);
                    } else {
                        sendJson(ex, 405, errJson("方法不允许，该端点仅支持 GET"));
                    }
                    return;
                case "pack":
                    if (seg.length == 3 && "GET".equals(method)) {
                        handlePackDetail(ex, decode(seg[2]));
                    } else {
                        sendJson(ex, 404, errJson("路径不存在"));
                    }
                    return;
                case "player":
                    if (seg.length == 3 && "GET".equals(method)) {
                        handlePlayerByName(ex, decode(seg[2]));
                    } else if (seg.length == 2 && "GET".equals(method)) {
                        handlePlayerByUuid(ex);
                    } else if (seg.length == 4 && "give".equals(seg[3]) && "POST".equals(method)) {
                        handleGive(ex, decode(seg[2]));
                    } else {
                        sendJson(ex, 404, errJson("路径不存在"));
                    }
                    return;
                case "reset":
                    if (seg.length == 2 && "POST".equals(method)) {
                        handleReset(ex);
                    } else {
                        sendJson(ex, 405, errJson("方法不允许，该端点仅支持 POST"));
                    }
                    return;
                case "reload":
                    if (seg.length == 2 && "POST".equals(method)) {
                        handleReload(ex);
                    } else {
                        sendJson(ex, 405, errJson("方法不允许，该端点仅支持 POST"));
                    }
                    return;
                default:
                    sendJson(ex, 404, errJson("路径不存在: " + decode(seg[1])));
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[Web] 请求处理异常: " + t.getMessage(), t);
            try {
                sendJson(ex, 500, errJson("服务器内部错误"));
            } catch (IOException ignored) {
            }
        } finally {
            ex.close();
        }
    }

    // ================================================================
    //  端点实现
    // ================================================================

    /** GET /api/status —— 插件概览与健康检查。 */
    private void handleStatus(HttpExchange ex) throws IOException {
        Map<String, Object> data = onMainThread(() -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("plugin", "SOYSGiftLoft");
            d.put("version", plugin.getDescription().getVersion());
            d.put("packs", plugin.getManager().getPacks().size());
            StorageManager sm = plugin.getStorageManager();
            d.put("primaryStorage", sm.getPrimary() != null
                    ? sm.getPrimary().getType().getDisplayName() : "NONE");
            List<String> sec = new ArrayList<>();
            for (DataStorage s : sm.getSecondaries()) {
                sec.add(s.getType().getDisplayName());
            }
            d.put("secondaryStorages", sec);
            d.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
            d.put("uptimeSeconds", (System.currentTimeMillis() - startTime) / 1000);
            return d;
        });
        sendJson(ex, 200, okJson(data));
    }

    /** GET /api/packs —— 礼包列表。 */
    private void handlePacks(HttpExchange ex) throws IOException {
        Map<String, Object> data = onMainThread(() -> {
            List<Object> arr = new ArrayList<>();
            for (GiftPack pack : plugin.getManager().getPacks().values()) {
                arr.add(packSummary(pack));
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("total", arr.size());
            d.put("packs", arr);
            return d;
        });
        sendJson(ex, 200, okJson(data));
    }

    /** GET /api/pack/<id> —— 单个礼包详情（含奖励摘要）。 */
    private void handlePackDetail(HttpExchange ex, String id) throws IOException {
        Map<String, Object> data = onMainThread(() -> {
            GiftPack pack = plugin.getManager().getPack(id);
            if (pack == null) {
                return null;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", pack.getId());
            d.put("display", pack.getDisplay());
            d.put("description", pack.getDescription());
            d.put("claimMode", pack.getClaimMode().name());
            d.put("cooldown", pack.getCooldownSeconds());
            d.put("requiredSlots", pack.getRequiredSlots());
            List<String> types = new ArrayList<>();
            if (pack.getConditionRoot() != null) {
                Set<UnlockCondition.Type> set = new java.util.HashSet<>();
                pack.getConditionRoot().collectTypes(set);
                for (UnlockCondition.Type t : set) {
                    types.add(t.name());
                }
            }
            d.put("conditionTypes", types);
            d.put("conditionCount", types.size());
            List<Object> rewards = new ArrayList<>();
            for (Reward r : pack.getRewards()) {
                rewards.add(rewardSummary(r));
            }
            d.put("rewardCount", rewards.size());
            d.put("rewards", rewards);
            return d;
        });
        if (data == null) {
            sendJson(ex, 404, errJson("礼包不存在: " + id));
            return;
        }
        sendJson(ex, 200, okJson(data));
    }

    /** GET /api/player/<name> —— 在线玩家状态。 */
    private void handlePlayerByName(HttpExchange ex, String name) throws IOException {
        Map<String, Object> data = onMainThread(() -> buildPlayerJson(name));
        if (data == null) {
            sendJson(ex, 404, errJson("玩家 '" + name
                    + "' 不在线，且存储层无名字索引，无法查询离线记录；请改用 GET /api/player?uuid=<uuid>"));
            return;
        }
        sendJson(ex, 200, okJson(data));
    }

    /** GET /api/player?uuid=<uuid> —— 按 UUID 查询玩家（在线/离线均可）。 */
    private void handlePlayerByUuid(HttpExchange ex) throws IOException {
        Map<String, String> query = parseQuery(ex);
        String raw = query.get("uuid");
        if (raw == null || raw.isEmpty()) {
            sendJson(ex, 400, errJson("缺少 uuid 查询参数"));
            return;
        }
        final UUID uuid;
        try {
            uuid = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, errJson("uuid 格式非法: " + raw));
            return;
        }
        Map<String, Object> data = onMainThread(() -> {
            Player online = Bukkit.getPlayer(uuid);
            PlayerRecord rec;
            try {
                rec = plugin.getManager().getData().loadPlayer(uuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Web] 读取玩家记录失败: " + e.getMessage(), e);
                return null;
            }
            if (rec == null && online == null) {
                return null;
            }
            if (rec == null) {
                rec = new PlayerRecord();
                rec.setUuid(uuid);
            }
            return buildPlayerJson(online != null ? online.getName() : null, rec, online);
        });
        if (data == null) {
            sendJson(ex, 404, errJson("未找到玩家记录: " + uuid));
            return;
        }
        sendJson(ex, 200, okJson(data));
    }

    /** POST /api/player/<name>/give —— 发放礼包（玩家需在线）。 */
    private void handleGive(HttpExchange ex, String name) throws IOException {
        Map<String, String> form = parseBody(ex);
        String packId = form.get("pack");
        if (packId == null || packId.isEmpty()) {
            sendJson(ex, 400, errJson("缺少 pack 参数（要发放的礼包 ID）"));
            return;
        }
        boolean force = Boolean.parseBoolean(form.get("force"));
        GiftLoftManager.ClaimResult result = onMainThread(() -> {
            Player target = Bukkit.getPlayerExact(name);
            if (target == null) {
                return null;
            }
            GiftLoftManager mgr = plugin.getManager();
            return force ? mgr.adminGive(target, packId) : mgr.claim(target, packId);
        });
        if (result == null) {
            sendJson(ex, 400, errJson("玩家 '" + name + "' 不在线，无法发放（奖励发放需要玩家在线）"));
            return;
        }
        if (result == GiftLoftManager.ClaimResult.SUCCESS) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("player", name);
            data.put("pack", packId);
            data.put("force", force);
            data.put("result", "SUCCESS");
            sendJson(ex, 200, okJson(data));
            return;
        }
        sendJson(ex, 200, jsonObj("success", false, "error", claimResultMessage(result),
                "result", result.name()));
    }

    /** POST /api/reset —— 重置领取状态（支持在线玩家名或离线玩家 UUID）。 */
    private void handleReset(HttpExchange ex) throws IOException {
        Map<String, String> form = parseBody(ex);
        String playerParam = form.get("player");
        String packId = form.get("pack");
        if (playerParam == null || playerParam.isEmpty()) {
            sendJson(ex, 400, errJson("缺少 player 参数（玩家名或 UUID）"));
            return;
        }
        if (packId == null || packId.isEmpty()) {
            sendJson(ex, 400, errJson("缺少 pack 参数（要重置的礼包 ID）"));
            return;
        }
        Boolean done = onMainThread(() -> {
            UUID uuid = resolveUuid(playerParam);
            if (uuid == null) {
                return null;
            }
            plugin.getManager().resetClaimed(uuid, packId);
            plugin.getManager().getData().save();
            return Boolean.TRUE;
        });
        if (done == null) {
            sendJson(ex, 400, errJson("无法定位玩家 '" + playerParam
                    + "'（玩家需在线，离线玩家请传 UUID）"));
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("player", playerParam);
        data.put("pack", packId);
        data.put("result", "RESET");
        sendJson(ex, 200, okJson(data));
    }

    /** POST /api/reload —— 重载全部配置。 */
    private void handleReload(HttpExchange ex) throws IOException {
        Boolean done = onMainThread(() -> {
            plugin.reloadAll();
            return Boolean.TRUE;
        });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", "RELOADED");
        sendJson(ex, 200, okJson(data));
        if (done == null) {
            plugin.getLogger().warning("[Web] reload 执行异常（已尝试执行）");
        }
    }

    // ================================================================
    //  数据构建
    // ================================================================

    /** 礼包列表摘要。 */
    private static Map<String, Object> packSummary(GiftPack pack) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", pack.getId());
        d.put("display", pack.getDisplay());
        d.put("description", pack.getDescription());
        d.put("claimMode", pack.getClaimMode().name());
        d.put("cooldown", pack.getCooldownSeconds());
        d.put("requiredSlots", pack.getRequiredSlots());
        List<String> types = new ArrayList<>();
        if (pack.getConditionRoot() != null) {
            Set<UnlockCondition.Type> set = new java.util.HashSet<>();
            pack.getConditionRoot().collectTypes(set);
            for (UnlockCondition.Type t : set) {
                types.add(t.name());
            }
        }
        d.put("conditionTypes", types);
        d.put("rewardCount", pack.getRewards().size());
        return d;
    }

    /** 奖励摘要（按类型输出关键字段，不暴露物品 NBT 等内部细节）。 */
    private static Map<String, Object> rewardSummary(Reward r) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", r.getType().name());
        switch (r.getType()) {
            case MONEY:
                d.put("money", r.getMoney());
                break;
            case POINTS:
                d.put("points", r.getPoints());
                break;
            case EXP:
                d.put("expValue", r.getExpValue());
                d.put("expLevels", r.isExpLevels());
                break;
            case ITEM:
                if (r.getItem() != null) {
                    d.put("material", r.getItem().getType().name());
                    d.put("amount", r.getItem().getAmount());
                }
                break;
            case COMMAND:
                d.put("command", r.getCommand());
                d.put("console", r.isConsole());
                break;
            case POTION:
                d.put("effect", r.getPotionEffect());
                d.put("duration", r.getPotionDuration());
                d.put("amplifier", r.getPotionAmplifier());
                break;
            case CUSTOM_ITEM:
                d.put("plugin", r.getCustomPlugin() != null ? r.getCustomPlugin().name() : null);
                d.put("id", r.getCustomId());
                d.put("amount", r.getCustomAmount());
                break;
            case RANDOM:
                d.put("rolls", r.getRolls());
                d.put("replace", r.isReplace());
                d.put("poolSize", r.getRandomPool().size());
                break;
        }
        return d;
    }

    /**
     * 构建玩家状态 JSON（name 为 null 表示离线且未知名字，仅输出 uuid 与记录数据）。
     * 返回 null 表示玩家不存在且无记录。
     */
    private Map<String, Object> buildPlayerJson(String name, PlayerRecord rec, Player online) {
        if (rec == null && online == null) {
            return null;
        }
        GiftLoftManager mgr = plugin.getManager();
        UUID uuid = online != null ? online.getUniqueId() : rec.getUuid();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", name);
        d.put("uuid", uuid.toString());
        d.put("online", online != null);
        d.put("playtimeSeconds", mgr.getPlaytime(uuid));
        List<Object> packs = new ArrayList<>();
        for (GiftPack pack : mgr.getPacks().values()) {
            packs.add(packStatus(pack, uuid, rec, online, mgr));
        }
        d.put("packs", packs);
        return d;
    }

    /** 按名字构建玩家状态；玩家不在线且无记录时返回 null。 */
    private Map<String, Object> buildPlayerJson(String name) {
        Player online = Bukkit.getPlayerExact(name);
        PlayerRecord rec = null;
        if (online != null) {
            try {
                rec = plugin.getManager().getData().loadPlayer(online.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Web] 读取玩家记录失败: " + e.getMessage(), e);
            }
        }
        if (rec == null && online == null) {
            return null;
        }
        if (rec == null) {
            rec = new PlayerRecord();
            rec.setUuid(online.getUniqueId());
        }
        return buildPlayerJson(name, rec, online);
    }

    /** 单个礼包在玩家视角下的状态。 */
    private static Map<String, Object> packStatus(GiftPack pack, UUID uuid, PlayerRecord rec,
                                                  Player online, GiftLoftManager mgr) {
        Map<String, Object> d = new LinkedHashMap<>();
        String id = pack.getId();
        d.put("id", id);
        d.put("display", pack.getDisplay());
        boolean claimed = rec != null && rec.hasClaimed(id);
        d.put("claimed", claimed);
        long cd = mgr.getCooldownRemaining(uuid, pack);
        d.put("cooldownRemaining", cd);
        boolean once = pack.getClaimMode() == GiftPack.ClaimMode.ONCE;
        Boolean unlocked = null;
        String state;
        if (online != null) {
            unlocked = pack.isUnlocked(online, mgr);
            if (once) {
                state = claimed ? "CLAIMED" : (unlocked ? "AVAILABLE" : "LOCKED");
            } else {
                state = cd >= 0 ? "COOLDOWN" : (unlocked ? "AVAILABLE" : "LOCKED");
            }
        } else {
            if (once) {
                state = claimed ? "CLAIMED" : "UNKNOWN";
            } else {
                state = cd >= 0 ? "COOLDOWN" : "UNKNOWN";
            }
        }
        d.put("state", state);
        d.put("unlocked", unlocked);
        return d;
    }

    /** 在线玩家名或合法 UUID 解析为 UUID；无法解析返回 null。 */
    private UUID resolveUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            // 不是 UUID，尝试按在线玩家名解析
        }
        Player p = Bukkit.getPlayerExact(raw);
        return p != null ? p.getUniqueId() : null;
    }

    private String claimResultMessage(GiftLoftManager.ClaimResult result) {
        switch (result) {
            case NOT_FOUND:
                return "礼包不存在";
            case ALREADY_CLAIMED:
                return "玩家已领取该礼包或在冷却中";
            case NOT_UNLOCKED:
                return "玩家未满足解锁条件";
            case NO_SPACE:
                return "玩家背包空间不足";
            case GRANT_FAILED:
                return "奖励发放失败（已回滚领取状态）";
            default:
                return "未知结果: " + result.name();
        }
    }

    // ================================================================
    //  基础设施
    // ================================================================

    /** Bearer token 认证（常量时间比较）。 */
    private boolean authenticate(HttpExchange ex) {
        List<String> auth = ex.getRequestHeaders().get("Authorization");
        if (auth == null || auth.isEmpty()) {
            return false;
        }
        String line = auth.get(0);
        if (line == null || !line.startsWith("Bearer ")) {
            return false;
        }
        String token = line.substring(7).trim();
        if (token.isEmpty()) {
            return false;
        }
        byte[] a = token.getBytes(StandardCharsets.UTF_8);
        byte[] b = plugin.getConfigManager().getWebToken().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /** 把任务调度到 Bukkit 主线程执行并等待结果（超时 5 秒）。 */
    @SuppressWarnings("unchecked")
    private <T> T onMainThread(Callable<T> task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return task.call();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "[Web] 主线程任务异常: " + t.getMessage(), t);
                return null;
            }
        }
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    result.set(task.call());
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    latch.countDown();
                }
            });
        } catch (IllegalStateException e) {
            // 插件已禁用，调度器不可用
            plugin.getLogger().warning("[Web] 插件已禁用，无法调度主线程任务");
            return null;
        }
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[Web] 主线程任务执行超时（5 秒）");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        if (error.get() != null) {
            plugin.getLogger().log(Level.SEVERE, "[Web] 主线程任务异常: "
                    + error.get().getMessage(), error.get());
            return null;
        }
        return result.get();
    }

    /** 解析 POST 表单体（application/x-www-form-urlencoded），限制 2 KB。 */
    private static Map<String, String> parseBody(HttpExchange ex) throws IOException {
        Map<String, String> map = new HashMap<>();
        InputStream in = ex.getRequestBody();
        byte[] buf = new byte[2048];
        int n = in.read(buf);
        if (n > 0) {
            String body = new String(buf, 0, n, StandardCharsets.UTF_8);
            for (String pair : body.split("&")) {
                int idx = pair.indexOf('=');
                if (idx < 0) {
                    continue;
                }
                map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
        }
        return map;
    }

    /** 解析 URL 查询参数。 */
    private static Map<String, String> parseQuery(HttpExchange ex) throws java.io.UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null || q.isEmpty()) {
            return map;
        }
        for (String pair : q.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                continue;
            }
            map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return map;
    }

    private static String decode(String s) throws java.io.UnsupportedEncodingException {
        return URLDecoder.decode(s, "UTF-8");
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String okJson(Map<String, Object> data) {
        return jsonObj("success", true, "data", data);
    }

    private static String errJson(String error) {
        return jsonObj("success", false, "error", error);
    }

    private static String jsonObj(Object... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(esc(String.valueOf(kv[i]))).append("\":")
                    .append(jsonVal(kv[i + 1]));
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static String jsonVal(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Boolean || v instanceof Number) {
            return v.toString();
        }
        if (v instanceof String) {
            return '"' + esc((String) v) + '"';
        }
        if (v instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<Object> list = (List<Object>) v;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(jsonVal(list.get(i)));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(esc(String.valueOf(e.getKey()))).append("\":")
                        .append(jsonVal(e.getValue()));
            }
            return sb.append('}').toString();
        }
        return '"' + esc(String.valueOf(v)) + '"';
    }

    /** JSON 字符串转义。 */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
