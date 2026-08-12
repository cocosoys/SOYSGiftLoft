package soys.soysgiftloft.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家数据记录：存储后端的「通用货币」。
 * <p>
 * 任意后端都可通过 {@link DataStorage#loadPlayer(UUID)} / {@link DataStorage#loadAllPlayers()}
 * 读出该结构，并通过 {@link DataStorage#savePlayer(PlayerRecord)} / {@link DataStorage#savePlayers(java.util.Collection)}
 * 写入另一后端，从而实现 YAML / SQLite / MySQL 之间的互相转换。
 * </p>
 * <p>
 * {@code claimed} 记录的是「礼包ID -> 领取时间戳（毫秒）」。
 * 一次性礼包只要存在记录即视为已领取；周期性礼包则依据时间戳判断是否仍在冷却窗口内。
 * 兼容旧数据：旧格式中 boolean true 的记录会被解析为时间戳 0（领取时刻未知）。
 * </p>
 */
public class PlayerRecord {

    private UUID uuid;
    private long playtime = 0L;
    /** 礼包ID -> 领取时间戳（毫秒），0 表示领取时刻未知（旧数据）。 */
    private final Map<String, Long> claimed = new HashMap<>();

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public long getPlaytime() {
        return playtime;
    }

    public void setPlaytime(long playtime) {
        this.playtime = playtime;
    }

    public void addPlaytime(long seconds) {
        this.playtime += seconds;
    }

    /**
     * 礼包ID -> 领取时间戳的映射（不可变视图）。
     */
    public Map<String, Long> getClaimTimestamps() {
        return claimed;
    }

    /**
     * 是否存在领取记录（不区分冷却，仅判断「是否曾经领取过」）。
     */
    public boolean hasClaimed(String packId) {
        return claimed.containsKey(packId);
    }

    /**
     * 该礼包的领取时间戳（毫秒），无记录返回 0。
     */
    public long getClaimTime(String packId) {
        Long t = claimed.get(packId);
        return t == null ? 0L : t;
    }

    /**
     * 标记领取（使用当前时间）。
     */
    public void setClaimed(String packId) {
        claimed.put(packId, System.currentTimeMillis());
    }

    /**
     * 标记领取（指定时间戳，用于从存储加载 / 迁移）。
     */
    public void setClaimed(String packId, long timestamp) {
        claimed.put(packId, timestamp);
    }

    public void resetClaimed(String packId) {
        claimed.remove(packId);
    }
}
