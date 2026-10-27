package soys.soysgiftloft.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.JsonReader;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.manager.GiftLoftManager;
import soys.soysgiftloft.model.GiftPack;
import soys.soysgiftloft.model.Reward;
import soys.soysgiftloft.model.UnlockCondition;
import soys.soysgiftloft.spring.entity.vo.*;
import soys.soysgiftloft.spring.service.GiftLoftWebService;
import soys.soysgiftloft.storage.DataStorage;
import soys.soysgiftloft.storage.PlayerRecord;
import soys.soysgiftloft.storage.StorageManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * 礼品阁 Web 管理接口业务实现（v1.8 {@code web/WebServer} 的装配逻辑整体迁入）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>编排现有 {@link GiftLoftManager} / {@link StorageManager}，<b>不重写业务</b>；</li>
 *   <li>VO 装配（原 WebServer 的 packSummary / rewardSummary / buildPlayerJson / packStatus 等私有 helper 全部下沉到此）；</li>
 *   <li>线程模型：网关 worker 线程调用，所有涉及 Bukkit API 与存储读写的操作统一经
 *       {@link #onMainThread(Callable)} 调度到主线程（与指令路径一致），超时 5 秒；</li>
 *   <li>领取五态状态机（CLAIMED / AVAILABLE / LOCKED / COOLDOWN / UNKNOWN）原样保留；</li>
 *   <li>reset 原子性口径：{@code resetClaimed + save} 异常时执行补偿（恢复领取状态并二次落盘），
 *       与 claim / adminGive 的「先落盘后发放 + 异常回滚」保持一致的失败安全语义。</li>
 * </ul>
 */
public class GiftLoftWebServiceImpl implements GiftLoftWebService {

    private final SOYSGiftLoft plugin;

    public GiftLoftWebServiceImpl(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    // ================================================================
    //  端点实现
    // ================================================================

    @Override
    public AjaxResult status(ApiRequestContext ctx) {
        StatusVO vo = onMainThread(() -> {
            GiftLoftManager mgr = plugin.getManager();
            StorageManager sm = plugin.getStorageManager();
            StatusVO v = new StatusVO();
            v.setPlugin("SOYSGiftLoft");
            v.setVersion(plugin.getDescription().getVersion());
            v.setPacks(mgr.getPacks().size());
            v.setPrimaryStorage(sm.getPrimary() != null
                    ? sm.getPrimary().getType().getDisplayName() : "NONE");
            List<String> sec = new ArrayList<>();
            for (DataStorage s : sm.getSecondaries()) {
                sec.add(s.getType().getDisplayName());
            }
            v.setSecondaryStorages(sec);
            v.setOnlinePlayers(Bukkit.getOnlinePlayers().size());
            v.setUptimeSeconds((System.currentTimeMillis() - plugin.getEnabledAt()) / 1000);
            return v;
        });
        if (vo == null) {
            return AjaxResult.error("读取插件状态失败（主线程调度超时或插件已禁用）");
        }
        return AjaxResult.success(vo);
    }

    @Override
    public AjaxResult packs(ApiRequestContext ctx) {
        PackPageVO vo = onMainThread(() -> {
            List<PackSummaryVO> arr = new ArrayList<>();
            for (GiftPack pack : plugin.getManager().getPacks().values()) {
                arr.add(packSummary(pack));
            }
            PackPageVO v = new PackPageVO();
            v.setTotal(arr.size());
            v.setPacks(arr);
            return v;
        });
        if (vo == null) {
            return AjaxResult.error("读取礼包列表失败（主线程调度超时或插件已禁用）");
        }
        return AjaxResult.success(vo);
    }

    @Override
    public AjaxResult packDetail(ApiRequestContext ctx, String id) {
        PackDetailVO vo = onMainThread(() -> {
            GiftPack pack = plugin.getManager().getPack(id);
            if (pack == null) {
                return null;
            }
            PackDetailVO v = new PackDetailVO();
            v.setId(pack.getId());
            v.setDisplay(pack.getDisplay());
            v.setDescription(pack.getDescription());
            v.setClaimMode(pack.getClaimMode().name());
            v.setCooldown(pack.getCooldownSeconds());
            v.setRequiredSlots(pack.getRequiredSlots());
            List<String> types = conditionTypes(pack);
            v.setConditionTypes(types);
            v.setConditionCount(types.size());
            List<RewardSummaryVO> rewards = new ArrayList<>();
            for (Reward r : pack.getRewards()) {
                rewards.add(rewardSummary(r));
            }
            v.setRewardCount(rewards.size());
            v.setRewards(rewards);
            return v;
        });
        if (vo == null) {
            return AjaxResult.notFound("礼包不存在: " + id);
        }
        return AjaxResult.success(vo);
    }

    @Override
    public AjaxResult playerByName(ApiRequestContext ctx, String name) {
        PlayerVO vo = onMainThread(() -> buildPlayerJson(name));
        if (vo == null) {
            return AjaxResult.notFound("玩家 '" + name
                    + "' 不在线，且存储层无名字索引，无法查询离线记录；请改用 GET /api/plugins/SOYSGiftLoft/player?uuid=<uuid>");
        }
        return AjaxResult.success(vo);
    }

    @Override
    public AjaxResult playerByUuid(ApiRequestContext ctx, String uuid) {
        final UUID parsed;
        try {
            parsed = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(400, "uuid 格式非法: " + uuid);
        }
        PlayerVO vo = onMainThread(() -> {
            Player online = Bukkit.getPlayer(parsed);
            PlayerRecord rec;
            try {
                rec = plugin.getManager().getData().loadPlayer(parsed);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[WebAPI] 读取玩家记录失败: " + e.getMessage(), e);
                return null;
            }
            if (rec == null && online == null) {
                return null;
            }
            if (rec == null) {
                rec = new PlayerRecord();
                rec.setUuid(parsed);
            }
            return buildPlayerJson(online != null ? online.getName() : null, rec, online);
        });
        if (vo == null) {
            return AjaxResult.notFound("未找到玩家记录: " + parsed);
        }
        return AjaxResult.success(vo);
    }

    @Override
    public AjaxResult give(ApiRequestContext ctx, String name, String body) {
        Map<String, String> form = parseParams(body);
        String packId = form.get("pack");
        if (packId == null || packId.isEmpty()) {
            return AjaxResult.error(400, "缺少 pack 参数（要发放的礼包 ID）");
        }
        boolean force = Boolean.parseBoolean(form.get("force"));
        return doGive(name, packId, force);
    }

    @Override
    public AjaxResult give(ApiRequestContext ctx, String name, String packId, boolean force) {
        if (name == null || name.isEmpty()) {
            return AjaxResult.error(400, "缺少 player 参数（目标玩家名，奖励发放需要玩家在线）");
        }
        if (packId == null || packId.isEmpty()) {
            return AjaxResult.error(400, "缺少 pack 参数（要发放的礼包 ID）");
        }
        return doGive(name, packId, force);
    }

    @Override
    public AjaxResult reset(ApiRequestContext ctx, String body) {
        Map<String, String> form = parseParams(body);
        String playerParam = form.get("player");
        String packId = form.get("pack");
        if (playerParam == null || playerParam.isEmpty()) {
            return AjaxResult.error(400, "缺少 player 参数（玩家名或 UUID）");
        }
        if (packId == null || packId.isEmpty()) {
            return AjaxResult.error(400, "缺少 pack 参数（要重置的礼包 ID）");
        }
        return doReset(playerParam, packId);
    }

    @Override
    public AjaxResult reset(ApiRequestContext ctx, String player, String packId) {
        if (player == null || player.isEmpty()) {
            return AjaxResult.error(400, "缺少 player 参数（玩家名或 UUID）");
        }
        if (packId == null || packId.isEmpty()) {
            return AjaxResult.error(400, "缺少 pack 参数（要重置的礼包 ID）");
        }
        return doReset(player, packId);
    }

    @Override
    public AjaxResult reload(ApiRequestContext ctx) {
        Boolean done = onMainThread(() -> {
            plugin.reloadAll();
            return Boolean.TRUE;
        });
        if (done == null) {
            return AjaxResult.error(500, "重载执行异常（已尝试执行，请查看服务端日志）");
        }
        return AjaxResult.success(new ReloadResultVO("RELOADED"));
    }

    // ================================================================
    //  发放 / 重置（共享实现）
    // ================================================================

    private AjaxResult doGive(String name, String packId, boolean force) {
        GiftLoftManager.ClaimResult result = onMainThread(() -> {
            Player target = Bukkit.getPlayerExact(name);
            if (target == null) {
                return null;
            }
            GiftLoftManager mgr = plugin.getManager();
            return force ? mgr.adminGive(target, packId) : mgr.claim(target, packId);
        });
        if (result == null) {
            return AjaxResult.error(400, "玩家 '" + name + "' 不在线，无法发放（奖励发放需要玩家在线）");
        }
        GiveResultVO vo = new GiveResultVO(name, packId, force, result.name());
        if (result == GiftLoftManager.ClaimResult.SUCCESS) {
            return AjaxResult.success(vo);
        }
        // 业务失败（v1.8 语义：HTTP 200 + success:false + error + result）
        // 迁移后：code=500 + msg=原因 + data={player,pack,force,result}
        return new AjaxResult(AjaxResult.ERROR, claimResultMessage(result), vo);
    }

    private AjaxResult doReset(String playerParam, String packId) {
        String status = onMainThread(() -> {
            UUID uuid = resolveUuid(playerParam);
            if (uuid == null) {
                return "NO_PLAYER";
            }
            try {
                plugin.getManager().resetClaimed(uuid, packId);
                plugin.getManager().getData().save();
                return "OK";
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[WebAPI] 重置领取状态后落盘失败，尝试补偿恢复: " + e.getMessage(), e);
                try {
                    // 补偿：恢复领取状态（reset 的逆向操作）并二次落盘，避免内存/磁盘状态分歧
                    plugin.getManager().setClaimed(uuid, packId);
                    plugin.getManager().getData().save();
                } catch (Exception e2) {
                    plugin.getLogger().log(Level.SEVERE,
                            "[WebAPI] 补偿落盘亦失败（内存与磁盘可能分歧，下次成功保存会自动收敛）: "
                                    + e2.getMessage(), e2);
                }
                return "SAVE_FAILED";
            }
        });
        if ("NO_PLAYER".equals(status)) {
            return AjaxResult.error(400, "无法定位玩家 '" + playerParam
                    + "'（玩家需在线，离线玩家请传 UUID）");
        }
        if ("SAVE_FAILED".equals(status)) {
            return AjaxResult.error(500, "重置失败：存储写入异常（已尝试恢复领取状态）");
        }
        return AjaxResult.success(new ResetResultVO(playerParam, packId, "RESET"));
    }

    // ================================================================
    //  数据构建（原 WebServer 私有 helper 下沉）
    // ================================================================

    /** 礼包列表摘要。 */
    private static PackSummaryVO packSummary(GiftPack pack) {
        PackSummaryVO d = new PackSummaryVO();
        d.setId(pack.getId());
        d.setDisplay(pack.getDisplay());
        d.setDescription(pack.getDescription());
        d.setClaimMode(pack.getClaimMode().name());
        d.setCooldown(pack.getCooldownSeconds());
        d.setRequiredSlots(pack.getRequiredSlots());
        d.setConditionTypes(conditionTypes(pack));
        d.setRewardCount(pack.getRewards().size());
        return d;
    }

    /** 奖励摘要（按类型输出关键字段，不暴露物品 NBT 等内部细节）。 */
    private static RewardSummaryVO rewardSummary(Reward r) {
        RewardSummaryVO d = new RewardSummaryVO();
        d.setType(r.getType().name());
        switch (r.getType()) {
            case MONEY:
                d.setMoney(r.getMoney());
                break;
            case POINTS:
                d.setPoints(r.getPoints());
                break;
            case EXP:
                d.setExpValue(r.getExpValue());
                d.setExpLevels(r.isExpLevels());
                break;
            case ITEM:
                if (r.getItem() != null) {
                    d.setMaterial(r.getItem().getType().name());
                    d.setAmount(r.getItem().getAmount());
                }
                break;
            case COMMAND:
                d.setCommand(r.getCommand());
                d.setConsole(r.isConsole());
                break;
            case POTION:
                d.setEffect(r.getPotionEffect());
                d.setDuration(r.getPotionDuration());
                d.setAmplifier(r.getPotionAmplifier());
                break;
            case CUSTOM_ITEM:
                d.setPlugin(r.getCustomPlugin() != null ? r.getCustomPlugin().name() : null);
                d.setId(r.getCustomId());
                d.setAmount(r.getCustomAmount());
                break;
            case RANDOM:
                d.setRolls(r.getRolls());
                d.setReplace(r.isReplace());
                d.setPoolSize(r.getRandomPool().size());
                break;
            default:
                break;
        }
        return d;
    }

    /** 礼包条件类型集合（HashSet 迭代序与 v1.8 一致，不做排序改动）。 */
    private static List<String> conditionTypes(GiftPack pack) {
        List<String> types = new ArrayList<>();
        if (pack.getConditionRoot() != null) {
            Set<UnlockCondition.Type> set = new HashSet<>();
            pack.getConditionRoot().collectTypes(set);
            for (UnlockCondition.Type t : set) {
                types.add(t.name());
            }
        }
        return types;
    }

    /**
     * 构建玩家状态 VO（name 为 null 表示离线且未知名字，仅输出 uuid 与记录数据）。
     * 返回 null 表示玩家不存在且无记录。
     */
    private PlayerVO buildPlayerJson(String name, PlayerRecord rec, Player online) {
        if (rec == null && online == null) {
            return null;
        }
        GiftLoftManager mgr = plugin.getManager();
        UUID uuid = online != null ? online.getUniqueId() : rec.getUuid();
        PlayerVO d = new PlayerVO();
        d.setName(name);
        d.setUuid(uuid.toString());
        d.setOnline(online != null);
        d.setPlaytimeSeconds(mgr.getPlaytime(uuid));
        List<PackStatusVO> packs = new ArrayList<>();
        for (GiftPack pack : mgr.getPacks().values()) {
            packs.add(packStatus(pack, uuid, rec, online, mgr));
        }
        d.setPacks(packs);
        return d;
    }

    /** 按名字构建玩家状态；玩家不在线且无记录时返回 null。 */
    private PlayerVO buildPlayerJson(String name) {
        Player online = Bukkit.getPlayerExact(name);
        PlayerRecord rec = null;
        if (online != null) {
            try {
                rec = plugin.getManager().getData().loadPlayer(online.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[WebAPI] 读取玩家记录失败: " + e.getMessage(), e);
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

    /** 单个礼包在玩家视角下的状态（领取五态状态机）。 */
    private static PackStatusVO packStatus(GiftPack pack, UUID uuid, PlayerRecord rec,
                                           Player online, GiftLoftManager mgr) {
        PackStatusVO d = new PackStatusVO();
        String id = pack.getId();
        d.setId(id);
        d.setDisplay(pack.getDisplay());
        boolean claimed = rec != null && rec.hasClaimed(id);
        d.setClaimed(claimed);
        long cd = mgr.getCooldownRemaining(uuid, pack);
        d.setCooldownRemaining(cd);
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
        d.setState(state);
        d.setUnlocked(unlocked);
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

    /**
     * 解析请求体参数：容忍 JSON 对象体与 {@code application/x-www-form-urlencoded} 表单体。
     * <ul>
     *   <li>JSON：{@code {"pack":"x","force":true}} → map（经 SOYS JsonReader）；</li>
     *   <li>表单：{@code pack=x&force=true} → map（URLDecoder）。</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> parseParams(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null) {
            return map;
        }
        String text = body.trim();
        if (text.isEmpty()) {
            return map;
        }
        if (text.startsWith("{")) {
            try {
                Map<String, Object> json = (Map<String, Object>) JsonReader.fromJson(text, Map.class);
                if (json != null) {
                    for (Map.Entry<String, Object> e : json.entrySet()) {
                        Object v = e.getValue();
                        map.put(String.valueOf(e.getKey()), v == null ? null : String.valueOf(v));
                    }
                    return map;
                }
            } catch (Exception ignored) {
                // 解析失败回退表单解析
            }
        }
        for (String pair : text.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                continue;
            }
            try {
                map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            } catch (UnsupportedEncodingException ignored) {
                // UTF-8 必然受支持，忽略
            }
        }
        return map;
    }

    /** 把任务调度到 Bukkit 主线程执行并等待结果（超时 5 秒）。 */
    @SuppressWarnings("unchecked")
    private <T> T onMainThread(Callable<T> task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return task.call();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "[WebAPI] 主线程任务异常: " + t.getMessage(), t);
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
            plugin.getLogger().warning("[WebAPI] 插件已禁用，无法调度主线程任务");
            return null;
        }
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[WebAPI] 主线程任务执行超时（5 秒）");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        if (error.get() != null) {
            plugin.getLogger().log(Level.SEVERE, "[WebAPI] 主线程任务异常: "
                    + error.get().getMessage(), error.get());
            return null;
        }
        return result.get();
    }
}
