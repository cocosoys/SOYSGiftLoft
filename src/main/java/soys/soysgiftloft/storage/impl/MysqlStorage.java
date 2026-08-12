package soys.soysgiftloft.storage.impl;

import org.bukkit.configuration.ConfigurationSection;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.storage.StorageType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * MySQL 存储后端。
 * <p>启用后自动成为主存储，其余启用的后端降级为辅助镜像，可用于跨服共享玩家数据。</p>
 * <p>配置仅需要 {@code url / username / password / keepalive-interval}，通过 {@code url}
 * 直接建立并测试连接，无需拆分 host / port / database。</p>
 */
public class MysqlStorage extends SqlStorage {

    private String jdbcUrl;
    private String username = "root";
    private String password = "";
    private int keepAliveSeconds = 1800;

    public MysqlStorage(SOYSGiftLoft plugin) {
        super(plugin);
    }

    @Override
    public StorageType getType() {
        return StorageType.MYSQL;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfigManager().getBackendSection("mysql");
        if (section == null) {
            throw new IllegalStateException("config.yml 中缺少 storage.backends.mysql 配置节");
        }

        this.jdbcUrl = section.getString("url");
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException("storage.backends.mysql.url 未配置，无法建立 MySQL 连接");
        }
        if (!jdbcUrl.toLowerCase().startsWith("jdbc:mysql:")) {
            throw new IllegalStateException("storage.backends.mysql.url 不是合法的 MySQL JDBC 连接串");
        }

        this.username = section.getString("username", "root");
        this.password = section.getString("password", "");
        this.tablePrefix = section.getString("table-prefix", "mc_soysgl_");
        this.keepAliveSeconds = section.getInt("keepalive-interval", 1800);

        // 确保驱动已加载，然后通过 url 直接测试连接
        try {
            Class.forName(getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 MySQL JDBC 驱动，请确认服务端已提供该驱动", e);
        }
        try (Connection test = DriverManager.getConnection(jdbcUrl, username, password)) {
            plugin.getLogger().info("[MySQL] 连接测试成功: " + maskUrl(jdbcUrl));
        } catch (SQLException e) {
            throw new IllegalStateException("[MySQL] 连接测试失败: " + e.getMessage(), e);
        }

        super.initialize();
    }

    @Override
    public String describe() {
        return username + "@" + maskUrl(jdbcUrl);
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    @Override
    protected String getDriverClass() {
        // 优先使用新驱动，回退到旧驱动
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return "com.mysql.cj.jdbc.Driver";
        } catch (ClassNotFoundException e) {
            return "com.mysql.jdbc.Driver";
        }
    }

    @Override
    protected Connection createConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    @Override
    protected String[] getSchemaStatements() {
        return new String[]{
                "CREATE TABLE IF NOT EXISTS " + playersTable() + " ("
                        + "uuid VARCHAR(36) NOT NULL,"
                        + "playtime BIGINT NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (uuid)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS " + claimedTable() + " ("
                        + "uuid VARCHAR(36) NOT NULL,"
                        + "pack_id VARCHAR(64) NOT NULL,"
                        + "claimed_at BIGINT NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (uuid, pack_id),"
                        + "INDEX idx_" + tablePrefix + "claimed_uuid (uuid)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        };
    }

    /**
     * 将 url 中的明文口令掩码，避免日志泄露。
     */
    private static String maskUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(?i)password=[^&]*", "password=****")
                .replaceAll("(?i)user=[^&]*", "user=****");
    }
}
