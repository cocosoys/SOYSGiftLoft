package soys.soysgiftloft.storage.impl;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.storage.DataStorage;
import soys.soysgiftloft.storage.PlayerRecord;
import soys.soysgiftloft.storage.StorageType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * YAML 文件存储后端。
 * <p>
 * 零外部依赖，默认启用。所有玩家写在同一个 players.yml 中，
 * 通过整对象加锁保证并发安全。适用于中小型服务器。
 * </p>
 */
public class YamlStorage implements DataStorage {

    private static final String ROOT = "players";

    private final SOYSGiftLoft plugin;
    private final Object lock = new Object();

    private File file;
    private YamlConfiguration config;
    private boolean available = false;
    private boolean backupOnSave = false;

    public YamlStorage(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    @Override
    public StorageType getType() {
        return StorageType.YAML;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfigManager().getBackendSection("yaml");
        String path = section == null ? "data/players.yml" : section.getString("file", "data/players.yml");
        this.backupOnSave = section != null && section.getBoolean("backup-on-save", false);

        this.file = new File(plugin.getDataFolder(), path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建数据目录: " + parent.getAbsolutePath());
        }
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("无法创建数据文件: " + file.getAbsolutePath());
        }
        synchronized (lock) {
            this.config = YamlConfiguration.loadConfiguration(file);
            if (!config.isConfigurationSection(ROOT)) {
                config.createSection(ROOT);
            }
        }
        this.available = true;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            try {
                if (config != null && file != null) {
                    config.save(file);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[YAML] 关闭时保存失败: " + e.getMessage());
            }
            available = false;
        }
    }

    @Override
    public void save() {
        synchronized (lock) {
            try {
                if (config != null && file != null) {
                    flush();
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[YAML] 保存失败: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String describe() {
        return file == null ? "未初始化" : file.getPath().replace('\\', '/');
    }

    // ================================================================
    //  读
    // ================================================================

    @Override
    public long getPlaytime(UUID uuid) {
        synchronized (lock) {
            return config.getLong(ROOT + "." + uuid + ".playtime", 0);
        }
    }

    @Override
    public boolean hasClaimed(UUID uuid, String packId) {
        synchronized (lock) {
            Object v = config.get(ROOT + "." + uuid + ".claimed." + packId);
            if (v == null) {
                return false;
            }
            // 兼容旧格式：boolean true 视为已领取；新格式存时间戳（>0 即已领取）
            if (v instanceof Boolean) {
                return (Boolean) v;
            }
            if (v instanceof Number) {
                return ((Number) v).longValue() > 0;
            }
            return true;
        }
    }

    @Override
    public long getClaimTime(UUID uuid, String packId) {
        synchronized (lock) {
            Object v = config.get(ROOT + "." + uuid + ".claimed." + packId);
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
            // 旧格式 boolean true 无法还原具体时间，返回 0（视为领取时刻未知）
            return 0L;
        }
    }

    @Override
    public PlayerRecord loadPlayer(UUID uuid) throws Exception {
        synchronized (lock) {
            ConfigurationSection section = config.getConfigurationSection(ROOT + "." + uuid);
            if (section == null) {
                return null;
            }
            return deserialize(uuid, section);
        }
    }

    @Override
    public Collection<PlayerRecord> loadAllPlayers() throws Exception {
        synchronized (lock) {
            Collection<PlayerRecord> records = new ArrayList<>();
            ConfigurationSection root = config.getConfigurationSection(ROOT);
            if (root == null) {
                return records;
            }
            for (String key : root.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                PlayerRecord record = deserialize(uuid, section);
                if (record != null) {
                    records.add(record);
                }
            }
            return records;
        }
    }

    // ================================================================
    //  写（常规）
    // ================================================================

    @Override
    public void addPlaytime(UUID uuid, long seconds) {
        synchronized (lock) {
            config.set(ROOT + "." + uuid + ".playtime", getPlaytime(uuid) + seconds);
        }
    }

    @Override
    public void setClaimed(UUID uuid, String packId) {
        synchronized (lock) {
            // 新格式存领取时间戳（毫秒），供周期性礼包冷却判定
            config.set(ROOT + "." + uuid + ".claimed." + packId, System.currentTimeMillis());
        }
    }

    @Override
    public void resetClaimed(UUID uuid, String packId) {
        synchronized (lock) {
            config.set(ROOT + "." + uuid + ".claimed." + packId, false);
        }
    }

    // ================================================================
    //  写（批量 / 整库）
    // ================================================================

    @Override
    public void savePlayer(PlayerRecord record) throws Exception {
        synchronized (lock) {
            serialize(record);
            flush();
        }
    }

    @Override
    public void savePlayers(Collection<PlayerRecord> records) throws Exception {
        synchronized (lock) {
            for (PlayerRecord record : records) {
                serialize(record);
            }
            flush();
        }
    }

    @Override
    public void deletePlayer(UUID uuid) throws Exception {
        synchronized (lock) {
            config.set(ROOT + "." + uuid, null);
            flush();
        }
    }

    @Override
    public void clear() throws Exception {
        synchronized (lock) {
            config.set(ROOT, null);
            config.createSection(ROOT);
            flush();
        }
    }

    // ================================================================
    //  内部
    // ================================================================

    private void flush() throws IOException {
        if (backupOnSave && file.exists()) {
            File backup = new File(file.getParentFile(), file.getName() + ".bak");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("[YAML] 备份失败: " + e.getMessage());
            }
        }
        config.save(file);
    }

    private void serialize(PlayerRecord record) {
        String base = ROOT + "." + record.getUuid();
        config.set(base + ".playtime", record.getPlaytime());
        // 整体重写领取节点，避免残留已重置的礼包
        config.set(base + ".claimed", null);
        for (Map.Entry<String, Long> entry : record.getClaimTimestamps().entrySet()) {
            config.set(base + ".claimed." + entry.getKey(), entry.getValue());
        }
    }

    private PlayerRecord deserialize(UUID uuid, ConfigurationSection section) {
        PlayerRecord record = new PlayerRecord();
        record.setUuid(uuid);
        record.setPlaytime(section.getLong("playtime", 0));
        ConfigurationSection claimed = section.getConfigurationSection("claimed");
        if (claimed != null) {
            for (String packId : claimed.getKeys(false)) {
                Object v = claimed.get(packId);
                if (v instanceof Number) {
                    // 新格式：时间戳（毫秒）
                    record.setClaimed(packId, ((Number) v).longValue());
                } else if (v instanceof Boolean && (Boolean) v) {
                    // 旧格式：boolean true → 视为领取时刻未知（0）
                    record.setClaimed(packId, 0L);
                }
            }
        }
        return record;
    }

    private UUID parseUuid(String input) {
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
