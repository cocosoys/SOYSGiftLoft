package soys.soysgiftloft.manager;

import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.model.GiftPack;
import soys.soysgiftloft.model.Reward;
import soys.soysgiftloft.model.UnlockCondition;
import soys.soysgiftloft.storage.DataStorage;
import soys.soysgiftloft.storage.StorageManager;
import soys.soysgiftloft.util.ColorUtil;

import java.util.*;

/**
 * 礼品阁核心管理器：礼包加载、在线时长统计、领取与发放逻辑。
 */
public class GiftLoftManager {

    private final SOYSGiftLoft plugin;
    private Economy economy;
    private PlayerPointsAPI playerPoints;
    private final StorageManager storage;
    private final Map<String, GiftPack> packs = new LinkedHashMap<>();
    /** 按条件类型统计的礼包数量（加载时缓存，供 by_condition 占位符使用）。 */
    private final Map<UnlockCondition.Type, Integer> conditionTypeCounts =
            new java.util.EnumMap<>(UnlockCondition.Type.class);
    private boolean placeholderHook = false;

    /** 依赖缺失告警去重集合（按插件名，避免发放循环中刷屏）。reload 时清空。 */
    private final Set<String> missingDepWarned = new HashSet<>();

    /** 领取结果状态机。 */
    public enum ClaimResult {
        SUCCESS, NOT_FOUND, ALREADY_CLAIMED, NOT_UNLOCKED, NO_SPACE, GRANT_FAILED
    }

    public GiftLoftManager(SOYSGiftLoft plugin, Economy economy, PlayerPointsAPI playerPoints, StorageManager storage) {
        this.plugin = plugin;
        this.economy = economy;
        this.playerPoints = playerPoints;
        this.storage = storage;
        this.placeholderHook = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    /**
     * 从 giftpacks.yml 重新加载所有礼包定义，并校验配置合法性。
     * 存在致命错误（[错误]）的礼包会被跳过，不影响其余礼包加载；同时缓存条件类型统计。
     */
    public void load() {
        packs.clear();
        conditionTypeCounts.clear();
        missingDepWarned.clear();
        FileConfiguration gfc = plugin.getGiftPackConfig();
        if (gfc == null) {
            return;
        }
        ConfigurationSection root = gfc.getConfigurationSection("giftpacks");
        if (root == null) {
            plugin.getLogger().warning("[礼包配置] giftpacks.yml 中未找到 giftpacks 节点，未加载任何礼包。");
            return;
        }
        int fatalCount = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            List<String> issues = new ArrayList<>();
            GiftPack pack = GiftPack.fromConfig(id, sec, issues);
            boolean hasFatal = false;
            for (String iss : issues) {
                plugin.getLogger().warning("[礼包配置] " + id + ": " + iss);
                if (iss.startsWith("[错误]")) {
                    hasFatal = true;
                }
            }
            if (hasFatal || pack == null) {
                plugin.getLogger().severe("[礼包配置] 礼包 '" + id + "' 存在致命错误，已跳过加载（不影响其他礼包）。");
                fatalCount++;
                continue;
            }
            packs.put(id, pack);
            // 缓存按条件类型统计的礼包数
            Set<UnlockCondition.Type> types = new HashSet<>();
            if (pack.getConditionRoot() != null) {
                pack.getConditionRoot().collectTypes(types);
            }
            for (UnlockCondition.Type t : types) {
                conditionTypeCounts.merge(t, 1, Integer::sum);
            }
            // 软依赖缺失时，对依赖该插件的礼包给出明确告警（便于调试时定位「为何不满足条件/奖励未发放」）
            if (economy == null && types.contains(UnlockCondition.Type.MONEY)) {
                plugin.getLogger().warning("[依赖缺失] 礼包 '" + id + "' 含 MONEY 条件/奖励，但 Vault 未加载，相关功能不可用。");
            }
            if (playerPoints == null && types.contains(UnlockCondition.Type.POINTS)) {
                plugin.getLogger().warning("[依赖缺失] 礼包 '" + id + "' 含 POINTS 条件/奖励，但 PlayerPoints 未加载，相关功能不可用。");
            }
            if (!placeholderHook && types.contains(UnlockCondition.Type.PLACEHOLDER)) {
                plugin.getLogger().warning("[依赖缺失] 礼包 '" + id + "' 含 PLACEHOLDER 条件，但 PlaceholderAPI 未加载，该条件将永远不满足（礼包保持锁定）。");
            }
        }
        if (fatalCount > 0) {
            plugin.getLogger().warning("[礼包配置] 共 " + fatalCount
                    + " 个礼包因配置错误被跳过，请检查上方日志后修正并 /sgiftloft reload。");
        }
        // 重载时清空自动提示缓存，让玩家有机会再次被提示
        notifiedPacks.clear();
    }

    /**
     * 返回使用了指定类型条件的礼包数量（按条件分组统计占位符用）。
     */
    public int getConditionTypeCount(UnlockCondition.Type type) {
        return conditionTypeCounts.getOrDefault(type, 0);
    }

    public Map<String, GiftPack> getPacks() {
        return packs;
    }

    public GiftPack getPack(String id) {
        return packs.get(id);
    }

    public DataStorage getData() {
        return storage.getPrimary();
    }

    // ---------------- 存储读写封装（走 StorageManager，自动镜像到辅助存储） ----------------

    public boolean hasClaimed(UUID uuid, String packId) {
        return storage.hasClaimed(uuid, packId);
    }

    public void setClaimed(UUID uuid, String packId) {
        storage.setClaimed(uuid, packId);
    }

    public void resetClaimed(UUID uuid, String packId) {
        storage.resetClaimed(uuid, packId);
    }

    public Economy getEconomy() {
        return economy;
    }

    public PlayerPointsAPI getPlayerPoints() {
        return playerPoints;
    }

    public boolean getPlaceholderHook() {
        return placeholderHook;
    }

    /** 由主类在 Vault/PlayerPoints/PlaceholderAPI 启用（含 softdepend 晚加载）后调用。 */
    public void setPlaceholderHook(boolean hook) {
        this.placeholderHook = hook;
    }

    /** 暴露消息配置，供奖励发放（如自定义物品失败提示）读取文本。 */
    public org.bukkit.configuration.file.FileConfiguration getMessageConfig() {
        return plugin.getMessageConfig();
    }

    public String parsePlaceholder(Player player, String text) {
        if (!placeholderHook || player == null || text == null) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    /**
     * 依赖缺失时的友好提示：打印一次控制台告警（按插件名去重），并在首次触发时
     * 向玩家发送一条可读消息，说明该功能因对应插件未加载而降级。便于单机调试时
     * 明确「为什么金币/点券没发、占位符条件不满足」。
     *
     * @param player     触发该功能的玩家（可为 null）
     * @param pluginName 缺失的插件名（如 Vault / PlayerPoints / PlaceholderAPI）
     * @param feature    受影响的功能描述（如 MONEY / POINTS / PLACEHOLDER）
     */
    public void notifyMissingDependency(Player player, String pluginName, String feature) {
        if (missingDepWarned.add(pluginName)) {
            plugin.getLogger().warning("[依赖缺失] 未加载 " + pluginName + " 插件，" + feature
                    + " 相关功能已自动降级（不影响其余功能，可单机调试）。");
        }
        if (player != null) {
            String msg = message("dependency-missing", "plugin", pluginName, "feature", feature);
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(ColorUtil.color(msg));
            }
        }
    }

    // ---------------- 在线时长统计 ----------------

    private final Map<UUID, Long> joinTime = new LinkedHashMap<>();

    public void markJoin(UUID uuid) {
        joinTime.put(uuid, System.currentTimeMillis());
    }

    /**
     * 计算玩家累计在线时长（已存储值 + 本次会话进行中的时长）。
     */
    public long getPlaytime(UUID uuid) {
        long base = storage.getPlaytime(uuid);
        Long t = joinTime.get(uuid);
        if (t != null) {
            base += (System.currentTimeMillis() - t) / 1000;
        }
        return base;
    }

    /**
     * 将会话期间的在线时长结算进存储（用于定时保存 / 退出时）。
     */
    public void flushPlaytime(Player player) {
        Long t = joinTime.remove(player.getUniqueId());
        if (t != null) {
            long secs = (System.currentTimeMillis() - t) / 1000;
            if (secs > 0) {
                storage.addPlaytime(player.getUniqueId(), secs);
            }
            // 重置会话起点，避免重复结算
            joinTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    // ---------------- 领取冷却判定 ----------------

    /**
     * 按礼包领取模式判定该玩家当前是否仍处于「已领取/冷却中」状态。
     * <ul>
     *   <li>ONCE：存在领取记录即视为已领取。</li>
     *   <li>DAILY/WEEKLY/MONTHLY/COOLDOWN：距上次领取的秒数 &lt; 窗口即视为冷却中。</li>
     * </ul>
     */
    public boolean isPackClaimed(UUID uuid, GiftPack pack) {
        if (pack.getClaimMode() == GiftPack.ClaimMode.ONCE) {
            return storage.hasClaimed(uuid, pack.getId());
        }
        long t = storage.getClaimTime(uuid, pack.getId());
        if (t <= 0) {
            return false;
        }
        long period = pack.getPeriodSeconds();
        if (period <= 0) {
            // 冷却 0 视为可立即再次领取
            return false;
        }
        long elapsed = (System.currentTimeMillis() - t) / 1000;
        return elapsed < period;
    }

    /**
     * 周期性礼包距下次可领取的剩余秒数；非冷却中或非周期礼包返回 -1。
     */
    public long getCooldownRemaining(UUID uuid, GiftPack pack) {
        if (pack.getClaimMode() == GiftPack.ClaimMode.ONCE) {
            return -1;
        }
        long t = storage.getClaimTime(uuid, pack.getId());
        if (t <= 0) {
            return -1;
        }
        long period = pack.getPeriodSeconds();
        if (period <= 0) {
            return -1;
        }
        long elapsed = (System.currentTimeMillis() - t) / 1000;
        long remaining = period - elapsed;
        return remaining > 0 ? remaining : -1;
    }

    // ---------------- 领取逻辑 ----------------

    private static int countFreeSlots(Player player) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null || it.getType() == Material.AIR) {
                n++;
            }
        }
        return n;
    }

    /** 检查背包空间是否足够（不发放任何奖励）。 */
    private boolean hasEnoughSpace(Player player, GiftPack pack) {
        return pack.getRequiredSlots() <= countFreeSlots(player);
    }

    /**
     * 发放礼包的全部奖励。调用方需保证已记录领取状态；
     * 本方法抛出异常时由调用方回滚领取状态，保证「已记录则已发放、发放失败则回滚」的原子性。
     *
     * @throws RuntimeException 任一奖励发放过程中抛出未捕获异常时向上抛出，触发回滚
     */
    private void grantRewards(Player player, GiftPack pack) {
        for (Reward r : pack.getRewards()) {
            r.grant(player, this);
        }
    }

    /**
     * 玩家主动领取（需满足解锁条件且未领取过/不在冷却中）。
     * <p>原子性保证：先记录领取状态并落盘，再发放奖励；发放异常时回滚领取状态，
     * 避免「部分奖励已发放但领取状态未记录」导致的重复领取漏洞。</p>
     */
    public ClaimResult claim(Player player, String packId) {
        GiftPack pack = packs.get(packId);
        if (pack == null) {
            return ClaimResult.NOT_FOUND;
        }
        if (isPackClaimed(player.getUniqueId(), pack)) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (!pack.isUnlocked(player, this)) {
            return ClaimResult.NOT_UNLOCKED;
        }
        if (!hasEnoughSpace(player, pack)) {
            return ClaimResult.NO_SPACE;
        }
        // 1. 先记录领取状态并保存（发放前落盘，防止崩溃后重复领取）
        storage.setClaimed(player.getUniqueId(), packId);
        storage.getPrimary().save();
        // 2. 发放奖励（异常时回滚领取状态）
        try {
            grantRewards(player, pack);
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "礼包 '" + packId + "' 发放奖励时异常，已回滚领取状态: " + t.getMessage(), t);
            storage.resetClaimed(player.getUniqueId(), packId);
            storage.getPrimary().save();
            return ClaimResult.GRANT_FAILED;
        }
        // 3. 发放成功，播放特效
        playClaimEffects(player, pack);
        return ClaimResult.SUCCESS;
    }

    /**
     * 管理员强制发放（绕过解锁条件与已领取状态）。
     * <p>原子性保证同 {@link #claim(Player, String)}。</p>
     */
    public ClaimResult adminGive(Player target, String packId) {
        GiftPack pack = packs.get(packId);
        if (pack == null) {
            return ClaimResult.NOT_FOUND;
        }
        if (!hasEnoughSpace(target, pack)) {
            return ClaimResult.NO_SPACE;
        }
        // 1. 先记录领取状态并保存
        storage.setClaimed(target.getUniqueId(), packId);
        storage.getPrimary().save();
        // 2. 发放奖励（异常时回滚）
        try {
            grantRewards(target, pack);
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "管理员发放礼包 '" + packId + "' 给玩家 " + target.getName()
                            + " 时异常，已回滚领取状态: " + t.getMessage(), t);
            storage.resetClaimed(target.getUniqueId(), packId);
            storage.getPrimary().save();
            return ClaimResult.GRANT_FAILED;
        }
        // 3. 播放特效
        playClaimEffects(target, pack);
        return ClaimResult.SUCCESS;
    }

    // ---------------- 领取特效与提示音 ----------------

    /**
     * 播放该礼包的领取特效。优先使用礼包自身配置，缺省时回退到全局默认。
     */
    public void playClaimEffects(Player player, GiftPack pack) {
        GiftPack.ClaimEffects fx = pack.getClaimEffects();
        if (fx == null) {
            fx = getDefaultClaimEffects();
        }
        if (fx == null) {
            return;
        }
        if (fx.getSound() != null) {
            try {
                try {
                    Sound sound = Sound.valueOf(fx.getSound().toUpperCase());
                    player.playSound(player.getLocation(), sound, fx.getVolume(), fx.getPitch());
                } catch (IllegalArgumentException notEnum) {
                    // 自定义声音（资源包声音），使用字符串重载
                    player.playSound(player.getLocation(), fx.getSound(), fx.getVolume(), fx.getPitch());
                }
            } catch (Throwable ignored) {
                // 音效播放失败不影响发放
            }
        }
        if (fx.getParticle() != null) {
            try {
                Particle particle = Particle.valueOf(fx.getParticle().toUpperCase());
                double spread = fx.getParticleSpread();
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0),
                        fx.getParticleCount(), spread, spread, spread, fx.getParticleSpeed());
            } catch (IllegalArgumentException ignored) {
                // 未知粒子类型，跳过
            } catch (Throwable ignored) {
            }
        }
        if (fx.isBroadcast()) {
            String msg = message("claim-broadcast", "pack", pack.getDisplay(), "player", player.getName());
            if (msg != null && !msg.isEmpty()) {
                Bukkit.broadcastMessage(msg);
            }
        }
    }

    private GiftPack.ClaimEffects cachedDefaultEffects = null;
    private boolean defaultEffectsLoaded = false;

    private GiftPack.ClaimEffects getDefaultClaimEffects() {
        if (defaultEffectsLoaded) {
            return cachedDefaultEffects;
        }
        defaultEffectsLoaded = true;
        FileConfiguration cfg = plugin.getConfig();
        if (cfg == null) {
            return null;
        }
        ConfigurationSection sec = cfg.getConfigurationSection("general.default-claim-effects");
        if (sec == null) {
            return null;
        }
        cachedDefaultEffects = GiftPack.ClaimEffects.fromSection(sec);
        return cachedDefaultEffects;
    }

    /** reload 时重置默认特效缓存，使配置变更生效。 */
    public void invalidateDefaultEffects() {
        cachedDefaultEffects = null;
        defaultEffectsLoaded = false;
    }

    // ---------------- 自动解锁提示 ----------------

    /** 每个玩家本次会话内已被提示过的礼包，避免重复刷屏。 */
    private final Map<UUID, Set<String>> notifiedPacks = new LinkedHashMap<>();

    /**
     * 检测玩家是否新解锁了礼包，若有则发送私聊 + 标题提示（每个礼包仅提示一次）。
     */
    public void checkAndNotify(Player player) {
        UUID uuid = player.getUniqueId();
        Set<String> done = notifiedPacks.get(uuid);
        if (done == null) {
            done = new HashSet<>();
            notifiedPacks.put(uuid, done);
        }
        for (Map.Entry<String, GiftPack> e : packs.entrySet()) {
            String id = e.getKey();
            if (done.contains(id)) {
                continue;
            }
            GiftPack pack = e.getValue();
            // 仅提示：已解锁、当前可领取（未领取/不在冷却中）的礼包
            if (pack.isUnlocked(player, this) && !isPackClaimed(uuid, pack)) {
                done.add(id);
                String chat = message("notify-unlock", "pack", pack.getDisplay(),
                        "desc", pack.getDescription(), "id", id);
                if (chat != null && !chat.isEmpty()) {
                    player.sendMessage(chat);
                }
                String title = message("notify-title", "pack", pack.getDisplay(), "id", id);
                String subtitle = message("notify-subtitle", "pack", pack.getDisplay(), "id", id);
                if (title != null || subtitle != null) {
                    try {
                        player.sendTitle(
                                ColorUtil.color(title == null ? "" : title),
                                ColorUtil.color(subtitle == null ? "" : subtitle),
                                10, 60, 10);
                    } catch (NoSuchMethodError ignored) {
                        // 极旧版本不支持 sendTitle，回退到聊天
                        if (chat == null || chat.isEmpty()) {
                            player.sendMessage(ColorUtil.color(title == null ? "" : title));
                        }
                    }
                }
            }
        }
    }

    /** 玩家退出时清理提示缓存。 */
    public void clearNotified(UUID uuid) {
        notifiedPacks.remove(uuid);
    }

    public void setEconomy(Economy economy) {
        this.economy = economy;
    }

    public void setPlayerPoints(PlayerPointsAPI playerPoints) {
        this.playerPoints = playerPoints;
    }

    // ---------------- 消息工具 ----------------

    private String message(String path, String... kv) {
        FileConfiguration msgs = plugin.getMessageConfig();
        if (msgs == null) {
            return "";
        }
        String s = ColorUtil.color(msgs.getString(path, ""));
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                s = s.replace("%" + kv[i] + "%", kv[i + 1]);
            }
        }
        return s;
    }
}
