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

    /** 领取结果状态机。 */
    public enum ClaimResult {
        SUCCESS, NOT_FOUND, ALREADY_CLAIMED, NOT_UNLOCKED, NO_SPACE
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

    /** 暴露消息配置，供奖励发放（如自定义物品失败提示）读取文本。 */
    public org.bukkit.configuration.file.FileConfiguration getMessageConfig() {
        return plugin.getMessageConfig();
    }

    public String parsePlaceholder(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
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

    private boolean reserveAndGrant(Player player, GiftPack pack) {
        int required = pack.getRequiredSlots();
        int free = countFreeSlots(player);
        if (required > free) {
            return false;
        }
        for (Reward r : pack.getRewards()) {
            r.grant(player, this);
        }
        return true;
    }

    /**
     * 玩家主动领取（需满足解锁条件且未领取过/不在冷却中）。
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
        if (!reserveAndGrant(player, pack)) {
            return ClaimResult.NO_SPACE;
        }
        storage.setClaimed(player.getUniqueId(), packId);
        storage.getPrimary().save();
        playClaimEffects(player, pack);
        return ClaimResult.SUCCESS;
    }

    /**
     * 管理员强制发放（绕过解锁条件与已领取状态）。
     */
    public ClaimResult adminGive(Player target, String packId) {
        GiftPack pack = packs.get(packId);
        if (pack == null) {
            return ClaimResult.NOT_FOUND;
        }
        if (!reserveAndGrant(target, pack)) {
            return ClaimResult.NO_SPACE;
        }
        storage.setClaimed(target.getUniqueId(), packId);
        storage.getPrimary().save();
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
