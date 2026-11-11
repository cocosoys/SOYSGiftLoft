package soys.soysgiftloft.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import soys.soysgiftloft.SOYSGiftLoft;

/**
 * 主配置文件（config.yml）访问器。
 * <p>集中收敛所有配置读取，避免各模块散落硬编码的配置路径。</p>
 */
public class ConfigManager {

    private final SOYSGiftLoft plugin;
    private FileConfiguration config;

    public ConfigManager(SOYSGiftLoft plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    /**
     * 重新抓取配置对象（reload 指令调用）。
     */
    public void reload() {
        this.config = plugin.getConfig();
    }

    public FileConfiguration raw() {
        return config;
    }

    // ================================================================
    //  通用
    // ================================================================

    public boolean isDebug() {
        return config.getBoolean("general.debug", false);
    }

    /** 自动检测礼包解锁并提示玩家（秒），0 关闭。 */
    public int getAutoNotify() {
        return config.getInt("general.auto-notify", 0);
    }

    /** 自动提示间隔（tick），0 关闭。 */
    public long getAutoNotifyTicks() {
        long seconds = config.getInt("general.auto-notify", 0);
        return seconds <= 0 ? 0L : seconds * 20L;
    }

    /** 语言文件标识，对应 lang/<language>.yml，默认 zh。 */
    public String getLanguage() {
        return config.getString("general.language", "zh");
    }

    // ================================================================
    //  存储后端
    // ================================================================

    public boolean isBackendEnabled(String backendId) {
        return config.getBoolean("storage.backends." + backendId + ".enabled", false);
    }

    public ConfigurationSection getBackendSection(String backendId) {
        return config.getConfigurationSection("storage.backends." + backendId);
    }

    public boolean isMirrorEnabled() {
        return config.getBoolean("storage.mirror.enabled", true);
    }

    public boolean isMirrorAsync() {
        return config.getBoolean("storage.mirror.async", true);
    }

    public boolean isSyncOnStartup() {
        return config.getBoolean("storage.mirror.sync-on-startup", false);
    }

    /** 启动时把 YAML 存量数据迁移进 MySQL（仅 MySQL 为主存储时生效）。 */
    public boolean getMigrateOnStartup() {
        return config.getBoolean("storage.migrate-on-startup", true);
    }

    // ================================================================
    //  自动保存
    // ================================================================

    /** 自动保存间隔（tick），0 表示关闭 */
    public long getAutoSaveIntervalTicks() {
        long seconds = config.getLong("storage.memory.auto-save-interval", 300L);
        return seconds <= 0 ? 0L : seconds * 20L;
    }
}
