package soys.soysgiftloft.storage;

import java.util.Collection;
import java.util.UUID;

/**
 * 数据存储后端抽象。
 * <p>
 * 所有实现必须满足：
 * <ul>
 *   <li>方法可能在异步线程被调用，实现需自行保证线程安全；</li>
 *   <li>常规读写（{@code getPlaytime}/{@code hasClaimed}/{@code addPlaytime} 等）以非受检方式处理异常，
 *       由 {@link StorageManager} 统一降级处理；</li>
 *   <li>批量与整库操作（{@code loadAllPlayers}/{@code savePlayers}/{@code clear} 等）以抛出异常上报，
 *       供迁移 / 同步流程处理；</li>
 *   <li>{@link #savePlayer(PlayerRecord)} 语义为 upsert，记录不存在时插入，存在时整体覆盖。</li>
 * </ul>
 * 新增后端只需实现本接口并在 {@link StorageManager#buildStorage} 中注册。
 * </p>
 */
public interface DataStorage {

    /**
     * 后端类型。
     */
    StorageType getType();

    /**
     * 初始化连接 / 建表 / 创建数据文件。
     *
     * @throws Exception 初始化失败，该后端将被标记为不可用
     */
    void initialize() throws Exception;

    /**
     * 释放资源，关服或重载时调用。
     */
    void shutdown();

    /**
     * 将内存中的修改落盘（YAML 写文件；SQL 写入即时提交，一般为空实现）。
     */
    void save();

    /**
     * 后端当前是否可用。不可用的后端会被跳过而非导致插件崩溃。
     */
    boolean isAvailable();

    /**
     * 供 /sgiftloft storage 展示的简要描述，如文件路径或数据库地址。
     */
    String describe();

    // ================================================================
    //  读（常规，非受检）
    // ================================================================

    /**
     * 读取玩家累计在线时长（秒）。
     */
    long getPlaytime(UUID uuid);

    /**
     * 判断玩家是否已领取某礼包（存在领取记录即视为已领取，
     * 不区分冷却窗口——周期性礼包的冷却判定由上层管理器结合时间戳完成）。
     */
    boolean hasClaimed(UUID uuid, String packId);

    /**
     * 读取玩家最近一次领取某礼包的时间戳（毫秒），从未领取返回 0。
     * 用于周期性礼包（每日/每周/自定义冷却）判断是否仍在冷却窗口内。
     */
    long getClaimTime(UUID uuid, String packId);

    /**
     * 按 UUID 读取单条玩家记录。
     *
     * @return 记录，不存在时返回 null
     */
    PlayerRecord loadPlayer(UUID uuid) throws Exception;

    /**
     * 读取全部玩家记录。仅用于迁移、同步与管理指令，常规流程不应调用。
     */
    Collection<PlayerRecord> loadAllPlayers() throws Exception;

    // ================================================================
    //  写（常规，非受检）
    // ================================================================

    /**
     * 累加玩家在线时长。
     */
    void addPlaytime(UUID uuid, long seconds);

    /**
     * 标记玩家已领取某礼包。
     */
    void setClaimed(UUID uuid, String packId);

    /**
     * 清除玩家对某礼包的领取状态。
     */
    void resetClaimed(UUID uuid, String packId);

    // ================================================================
    //  写（批量 / 整库，受检）
    // ================================================================

    /**
     * 保存（upsert）单条玩家记录。
     */
    void savePlayer(PlayerRecord record) throws Exception;

    /**
     * 批量保存。实现应尽可能使用事务或单次落盘以提升性能。
     */
    void savePlayers(Collection<PlayerRecord> records) throws Exception;

    /**
     * 删除玩家记录。
     */
    void deletePlayer(UUID uuid) throws Exception;

    /**
     * 清空全部数据。仅由迁移覆盖流程调用。
     */
    void clear() throws Exception;
}
