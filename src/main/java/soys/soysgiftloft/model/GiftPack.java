package soys.soysgiftloft.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import soys.soysgiftloft.manager.GiftLoftManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 礼包定义：显示信息 + 解锁条件 + 奖励列表 + 领取模式 + 领取特效。
 */
public class GiftPack {

    /**
     * 领取模式。
     * <ul>
     *   <li>{@link #ONCE}     —— 一次性，领取后永久不可再领（默认）。</li>
     *   <li>{@link #DAILY}    —— 每日礼包，滚动 24 小时窗口后可再领。</li>
     *   <li>{@link #WEEKLY}   —— 每周礼包，滚动 7 天窗口后可再领。</li>
     *   <li>{@link #MONTHLY}  —— 每月礼包，滚动 30 天窗口后可再领。</li>
     *   <li>{@link #COOLDOWN} —— 自定义冷却（秒），由 {@code cooldown} 字段指定。</li>
     * </ul>
     * 周期性礼包统一以「距上次领取的秒数 &lt; 窗口」判定是否仍处冷却中，
     * 避免跨时区/跨日历边界的复杂处理。
     */
    public enum ClaimMode {
        ONCE, DAILY, WEEKLY, MONTHLY, COOLDOWN;

        public static ClaimMode parse(String raw, ClaimMode def) {
            if (raw == null) {
                return def;
            }
            try {
                return valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return def;
            }
        }

        /** 该模式的冷却窗口（秒）。ONCE 不会被调用，返回 Long.MAX_VALUE 仅作占位。 */
        public long getPeriodSeconds(long customCooldown) {
            switch (this) {
                case DAILY: return 86400L;
                case WEEKLY: return 604800L;
                case MONTHLY: return 2592000L;
                case COOLDOWN: return Math.max(0L, customCooldown);
                case ONCE:
                default: return Long.MAX_VALUE;
            }
        }
    }

    /**
     * 领取特效配置。全部字段可选；任一为空则跳过对应效果。
     */
    public static class ClaimEffects {
        private String sound = null;        // 音效名（Sound 枚举名或自定义声音名）
        private float volume = 1.0f;
        private float pitch = 1.0f;
        private String particle = null;     // 粒子枚举名
        private int particleCount = 16;
        private double particleSpread = 0.5;
        private double particleSpeed = 0.1;
        private boolean broadcast = false;  // 是否全服广播

        public static ClaimEffects fromSection(ConfigurationSection sec) {
            if (sec == null) {
                return null;
            }
            ClaimEffects fx = new ClaimEffects();
            fx.sound = sec.getString("sound", null);
            fx.volume = (float) sec.getDouble("volume", 1.0);
            fx.pitch = (float) sec.getDouble("pitch", 1.0);
            fx.particle = sec.getString("particle", null);
            fx.particleCount = sec.getInt("particle-count", 16);
            fx.particleSpread = sec.getDouble("particle-spread", 0.5);
            fx.particleSpeed = sec.getDouble("particle-speed", 0.1);
            fx.broadcast = sec.getBoolean("broadcast", false);
            if (fx.sound == null && fx.particle == null && !fx.broadcast) {
                return null; // 无任何效果
            }
            return fx;
        }

        public String getSound() { return sound; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
        public String getParticle() { return particle; }
        public int getParticleCount() { return particleCount; }
        public double getParticleSpread() { return particleSpread; }
        public double getParticleSpeed() { return particleSpeed; }
        public boolean isBroadcast() { return broadcast; }
    }

    private final String id;
    private final String display;
    private final String description;
    private final ConditionNode root;       // 解锁条件树根（null / 空组 = 无限制）
    private final List<Reward> rewards;
    private final ClaimMode claimMode;
    private final long cooldownSeconds;   // 仅 COOLDOWN 模式有意义
    private final ClaimEffects claimEffects;

    public GiftPack(String id, String display, String description,
                    ConditionNode root, List<Reward> rewards,
                    ClaimMode claimMode, long cooldownSeconds, ClaimEffects claimEffects) {
        this.id = id;
        this.display = display;
        this.description = description;
        this.root = root;
        this.rewards = rewards;
        this.claimMode = claimMode;
        this.cooldownSeconds = cooldownSeconds;
        this.claimEffects = claimEffects;
    }

    /**
     * 从礼包配置节构造 GiftPack，并把配置校验问题写入 issues
     * （"[错误]" 致命，调用方应跳过该礼包；"[警告]" 可继续）。
     */
    @SuppressWarnings("unchecked")
    public static GiftPack fromConfig(String id, ConfigurationSection sec, List<String> issues) {
        if (sec == null) {
            return null;
        }
        String display = sec.getString("display", id);
        String description = sec.getString("description", "");

        ConditionNode root = ConditionNode.fromObject(sec.get("unlock-conditions"), issues);
        if (root == null) {
            root = new ConditionGroup(ConditionGroup.Mode.AND, new ArrayList<>());
        } else {
            root.validate(issues);
        }

        List<Reward> rewards = new ArrayList<>();
        List<Map<?, ?>> rewMaps = sec.getMapList("rewards");
        if (rewMaps.isEmpty()) {
            issues.add("[警告] 礼包 " + id + " 未配置任何奖励（rewards 为空）");
        }
        for (Map<?, ?> m : rewMaps) {
            Reward r = Reward.fromMap((Map<String, Object>) m, issues);
            if (r != null) {
                rewards.add(r);
            }
        }

        // claim-mode 合法性校验（ClaimMode.parse 静默回退 ONCE，这里显式告警）
        String modeRaw = sec.getString("claim-mode", "ONCE");
        ClaimMode mode = ClaimMode.parse(modeRaw, ClaimMode.ONCE);
        if (modeRaw != null && !modeRaw.trim().equalsIgnoreCase("ONCE") && mode == ClaimMode.ONCE) {
            issues.add("[警告] 礼包 " + id + ": claim-mode '" + modeRaw + "' 无效，已回退为 ONCE");
        }
        long cooldown = sec.getLong("cooldown", 0L);
        if (mode == ClaimMode.COOLDOWN && cooldown <= 0) {
            issues.add("[警告] 礼包 " + id + ": claim-mode 为 COOLDOWN 但 cooldown <= 0，将视为立即可再次领取");
        }

        ClaimEffects fx = ClaimEffects.fromSection(sec.getConfigurationSection("claim-effects"));

        return new GiftPack(id, display, description, root, rewards, mode, cooldown, fx);
    }

    /**
     * 是否所有解锁条件都满足（条件树根判定；root 为 null/空组时视为无限制）。
     */
    public boolean isUnlocked(Player player, GiftLoftManager mgr) {
        return root == null || root.isMet(player, mgr);
    }

    /**
     * 返回尚未满足的解锁条件描述（用于提示玩家）。
     */
    public List<String> getUnmetConditions(Player player, GiftLoftManager mgr) {
        if (root == null) {
            return new ArrayList<>();
        }
        return root.getUnmet(player, mgr);
    }

    /**
     * 以缩进树形返回条件描述（供 info 指令展示）。
     */
    public List<String> describeConditions(Player player, GiftLoftManager mgr) {
        if (root == null) {
            return new ArrayList<>();
        }
        return root.describeLines(player, mgr, "");
    }

    /**
     * 计算该礼包物品奖励所需的最小背包空位数量
     * （含 CUSTOM_ITEM 与 RANDOM 池的最坏情况估算）。
     */
    public int getRequiredSlots() {
        int n = 0;
        for (Reward r : rewards) {
            n += r.requiredSlots();
        }
        return n;
    }

    /** 该模式的冷却窗口（秒），ONCE 返回 Long.MAX_VALUE。 */
    public long getPeriodSeconds() {
        return claimMode.getPeriodSeconds(cooldownSeconds);
    }

    public boolean isPeriodic() {
        return claimMode != ClaimMode.ONCE;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public String getDescription() {
        return description;
    }

    public ConditionNode getConditionRoot() {
        return root;
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    public ClaimMode getClaimMode() {
        return claimMode;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public ClaimEffects getClaimEffects() {
        return claimEffects;
    }
}
