package soys.soysgiftloft.storage.impl;

import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.storage.DataStorage;
import soys.soysgiftloft.storage.PlayerRecord;
import soys.soysgiftloft.storage.StorageType;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * SQL 存储后端的公共实现。
 * <p>
 * SQLite 与 MySQL 共用同一套表结构与 CRUD 逻辑，子类只需提供：
 * 驱动类名、JDBC URL、连接创建方式与建表语句方言。
 * </p>
 * <p>
 * 连接策略：维持单个长连接并在每次使用前做有效性探测，配合对象锁串行化访问。
 * 插件的写操作本身已经被 {@code StorageManager} 收敛到单线程，无需引入连接池。
 * </p>
 */
public abstract class SqlStorage implements DataStorage {

    protected final SOYSGiftLoft plugin;
    protected final Object lock = new Object();

    protected String tablePrefix = "soysgl_";
    protected volatile boolean available = false;

    private Connection connection;

    protected SqlStorage(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    // ================================================================
    //  子类需实现的方言部分
    // ================================================================

    /** JDBC 驱动类名 */
    protected abstract String getDriverClass();

    /** 创建一个全新的数据库连接 */
    protected abstract Connection createConnection() throws SQLException;

    /** 建表与建索引语句，按顺序执行 */
    protected abstract String[] getSchemaStatements();

    // ================================================================
    //  表名
    // ================================================================

    protected String playersTable() {
        return tablePrefix + "players";
    }

    protected String claimedTable() {
        return tablePrefix + "claimed";
    }

    // ================================================================
    //  生命周期
    // ================================================================

    @Override
    public void initialize() throws Exception {
        try {
            Class.forName(getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 JDBC 驱动 " + getDriverClass()
                    + "，请确认服务端已提供该驱动或手动放入 libraries 目录");
        }
        synchronized (lock) {
            connection = createConnection();
            try (Statement statement = connection.createStatement()) {
                for (String sql : getSchemaStatements()) {
                    statement.execute(sql);
                }
            }
            // 兼容旧表：若 claimed 表缺少 claimed_at 列则补建（幂等）
            ensureClaimedAtColumn(connection);
        }
        available = true;
    }

    /**
     * 通过数据库元数据检测 claimed 表是否已含 claimed_at 列，缺失则执行 ALTER 补建。
     * 这样对已存在旧表平滑升级，对新表无副作用。
     */
    private void ensureClaimedAtColumn(Connection conn) {
        ResultSet columns = null;
        try {
            DatabaseMetaData meta = conn.getMetaData();
            // SQLite / MySQL 均支持按表名+列名查询列；schema 传 null 使用默认
            columns = meta.getColumns(null, null, claimedTable(), "claimed_at");
            if (columns != null && columns.next()) {
                return; // 列已存在
            }
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE " + claimedTable()
                        + " ADD COLUMN claimed_at BIGINT NOT NULL DEFAULT 0");
                plugin.getLogger().info("[" + getType().getId()
                        + "] 已为 " + claimedTable() + " 补建 claimed_at 列");
            }
        } catch (SQLException e) {
            // 多数情况是列已存在（元数据在某些驱动下查不到），忽略即可
            if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[" + getType().getId()
                        + "] claimed_at 列检测/补建跳过: " + e.getMessage());
            }
        } finally {
            if (columns != null) {
                try {
                    columns.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            available = false;
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // 关闭失败无需处理
                }
                connection = null;
            }
        }
    }

    @Override
    public void save() {
        // SQL 写入即时提交，无需落盘动作
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * 获取一个可用连接，失效时自动重建。调用方必须持有 {@link #lock}。
     */
    protected Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // 旧连接关闭失败可忽略
                }
            }
            connection = createConnection();
        }
        return connection;
    }

    /**
     * 主动探测连接（保活任务使用）。
     */
    public void keepAlive() {
        synchronized (lock) {
            try {
                connection().isValid(3);
            } catch (SQLException e) {
                plugin.getLogger().warning("[" + getType().getId() + "] 保活探测失败: " + e.getMessage());
            }
        }
    }

    // ================================================================
    //  读
    // ================================================================

    @Override
    public long getPlaytime(UUID uuid) {
        synchronized (lock) {
            try {
                String sql = "SELECT playtime FROM " + playersTable() + " WHERE uuid = ?";
                try (PreparedStatement statement = connection().prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return rs.getLong("playtime");
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[" + getType().getId() + "] 读取在线时长失败: " + e.getMessage());
            }
        }
        return 0;
    }

    @Override
    public boolean hasClaimed(UUID uuid, String packId) {
        synchronized (lock) {
            try {
                String sql = "SELECT 1 FROM " + claimedTable() + " WHERE uuid = ? AND pack_id = ?";
                try (PreparedStatement statement = connection().prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, packId);
                    try (ResultSet rs = statement.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[" + getType().getId() + "] 查询领取状态失败: " + e.getMessage());
            }
        }
        return false;
    }

    @Override
    public long getClaimTime(UUID uuid, String packId) {
        synchronized (lock) {
            try {
                String sql = "SELECT claimed_at FROM " + claimedTable() + " WHERE uuid = ? AND pack_id = ?";
                try (PreparedStatement statement = connection().prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, packId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return rs.getLong("claimed_at");
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[" + getType().getId() + "] 读取领取时间失败: " + e.getMessage());
            }
        }
        return 0L;
    }

    @Override
    public PlayerRecord loadPlayer(UUID uuid) throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            PlayerRecord record = null;
            try (PreparedStatement statement =
                         conn.prepareStatement("SELECT playtime FROM " + playersTable() + " WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        record = new PlayerRecord();
                        record.setUuid(uuid);
                        record.setPlaytime(rs.getLong("playtime"));
                    }
                }
            }
            if (record == null) {
                return null;
            }
            try (PreparedStatement statement = conn.prepareStatement(
                    "SELECT pack_id, claimed_at FROM " + claimedTable() + " WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        record.setClaimed(rs.getString("pack_id"), rs.getLong("claimed_at"));
                    }
                }
            }
            return record;
        }
    }

    @Override
    public Collection<PlayerRecord> loadAllPlayers() throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            java.util.Map<UUID, PlayerRecord> map = new java.util.HashMap<>();

            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT uuid, playtime FROM " + playersTable())) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    PlayerRecord record = new PlayerRecord();
                    record.setUuid(uuid);
                    record.setPlaytime(rs.getLong("playtime"));
                    map.put(uuid, record);
                }
            }

            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT uuid, pack_id, claimed_at FROM " + claimedTable())) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    PlayerRecord record = map.computeIfAbsent(uuid, k -> {
                        PlayerRecord r = new PlayerRecord();
                        r.setUuid(k);
                        return r;
                    });
                    record.setClaimed(rs.getString("pack_id"), rs.getLong("claimed_at"));
                }
            }

            return new ArrayList<>(map.values());
        }
    }

    // ================================================================
    //  写（常规）
    // ================================================================

    @Override
    public void addPlaytime(UUID uuid, long seconds) {
        synchronized (lock) {
            try {
                String sql = "INSERT INTO " + playersTable() + " (uuid, playtime) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE playtime = playtime + ?";
                try (PreparedStatement statement = connection().prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.setLong(2, seconds);
                    statement.setLong(3, seconds);
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[" + getType().getId() + "] 写入在线时长失败: " + e.getMessage());
            }
        }
    }

    @Override
    public void setClaimed(UUID uuid, String packId) {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            try {
                // REPLACE INTO 在 MySQL / SQLite 均支持，按主键(uuid,pack_id)覆盖并写入时间戳
                String sql = "REPLACE INTO " + claimedTable()
                        + " (uuid, pack_id, claimed_at) VALUES (?, ?, ?)";
                try (PreparedStatement statement = connection().prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, packId);
                    statement.setLong(3, now);
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[" + getType().getId() + "] 写入领取状态失败: " + e.getMessage());
            }
        }
    }

    @Override
    public void resetClaimed(UUID uuid, String packId) {
        synchronized (lock) {
            try {
                String sql = "DELETE FROM " + claimedTable() + " WHERE uuid = ? AND pack_id = ?";
                try (PreparedStatement statement = connection().prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, packId);
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[" + getType().getId() + "] 重置领取状态失败: " + e.getMessage());
            }
        }
    }

    // ================================================================
    //  写（批量 / 整库）
    // ================================================================

    @Override
    public void savePlayer(PlayerRecord record) throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                writePlayer(conn, record);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void savePlayers(Collection<PlayerRecord> records) throws Exception {
        if (records == null || records.isEmpty()) {
            return;
        }
        synchronized (lock) {
            Connection conn = connection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (PlayerRecord record : records) {
                    writePlayer(conn, record);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void deletePlayer(UUID uuid) throws Exception {
        synchronized (lock) {
            try (PreparedStatement statement =
                         connection().prepareStatement("DELETE FROM " + claimedTable() + " WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement =
                         connection().prepareStatement("DELETE FROM " + playersTable() + " WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
        }
    }

    @Override
    public void clear() throws Exception {
        synchronized (lock) {
            try (Statement statement = connection().createStatement()) {
                statement.executeUpdate("DELETE FROM " + claimedTable());
                statement.executeUpdate("DELETE FROM " + playersTable());
            }
        }
    }

    // ================================================================
    //  内部
    // ================================================================

    /**
     * 整体写入一条玩家记录（upsert 玩家表，重写领取表），保证已重置的礼包被清除。
     */
    private void writePlayer(Connection conn, PlayerRecord record) throws SQLException {
        String playerSql = "REPLACE INTO " + playersTable() + " (uuid, playtime) VALUES (?, ?)";
        try (PreparedStatement statement = conn.prepareStatement(playerSql)) {
            statement.setString(1, record.getUuid().toString());
            statement.setLong(2, record.getPlaytime());
            statement.executeUpdate();
        }

        try (PreparedStatement statement =
                     conn.prepareStatement("DELETE FROM " + claimedTable() + " WHERE uuid = ?")) {
            statement.setString(1, record.getUuid().toString());
            statement.executeUpdate();
        }

        if (!record.getClaimTimestamps().isEmpty()) {
            String claimedSql = "INSERT IGNORE INTO " + claimedTable()
                    + " (uuid, pack_id, claimed_at) VALUES (?, ?, ?)";
            try (PreparedStatement statement = conn.prepareStatement(claimedSql)) {
                for (Map.Entry<String, Long> entry : record.getClaimTimestamps().entrySet()) {
                    statement.setString(1, record.getUuid().toString());
                    statement.setString(2, entry.getKey());
                    statement.setLong(3, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    protected UUID parseUuid(String input) {
        if (input == null) {
            return null;
        }
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
