package soys.soysgiftloft.storage.impl;

import org.bukkit.configuration.ConfigurationSection;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.storage.StorageType;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite 存储后端。
 * <p>单文件数据库，无需额外服务，适合需要 SQL 查询能力但不想部署 MySQL 的场景。</p>
 */
public class SqliteStorage extends SqlStorage {

    private File databaseFile;

    public SqliteStorage(SOYSGiftLoft plugin) {
        super(plugin);
    }

    @Override
    public StorageType getType() {
        return StorageType.SQLITE;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfigManager().getBackendSection("sqlite");
        String path = section == null ? "data/players.db" : section.getString("file", "data/players.db");
        this.tablePrefix = section == null ? "mc_soysgl_" : section.getString("table-prefix", "mc_soysgl_");

        this.databaseFile = new File(plugin.getDataFolder(), path);
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建数据目录: " + parent.getAbsolutePath());
        }
        super.initialize();
    }

    @Override
    public String describe() {
        return databaseFile == null ? "未初始化" : databaseFile.getPath().replace('\\', '/');
    }

    @Override
    protected String getDriverClass() {
        return "org.sqlite.JDBC";
    }

    @Override
    protected Connection createConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databaseFile.getAbsolutePath());
        // 开启外键与 WAL，提升并发读性能
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    @Override
    protected String[] getSchemaStatements() {
        return new String[]{
                "CREATE TABLE IF NOT EXISTS " + playersTable() + " ("
                        + "uuid TEXT NOT NULL PRIMARY KEY,"
                        + "playtime INTEGER NOT NULL DEFAULT 0"
                        + ")",
                "CREATE TABLE IF NOT EXISTS " + claimedTable() + " ("
                        + "uuid TEXT NOT NULL,"
                        + "pack_id TEXT NOT NULL,"
                        + "claimed_at INTEGER NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (uuid, pack_id)"
                        + ")",
                "CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "claimed_uuid"
                        + " ON " + claimedTable() + " (uuid)"
        };
    }
}
