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

    // ================================================================
    //  Web / 后台管理接口
    // ================================================================

    public boolean isWebEnabled() {
        return config.getBoolean("web.enabled", false);
    }

    /**
     * ERP 管理面开关（MCERP 内的「礼品阁」菜单 + {@code /api/plugins/SOYSGiftLoft/erp/*} 端点）。
     *
     * <p>与 {@link #isWebEnabled()} <b>刻意分离</b>：ERP 管理面走 SOYS 网关原生登录态
     * （不校验 api-token），与对外暴露 Bearer 机器 API 是两件事。共用一个开关会导致
     * "只想在 ERP 里管理礼包，却被要求一并开放机器 API"——本服务器此前就因为
     * {@code web.enabled: false} 连 ERP 模块都没注册上。</p>
     *
     * <p>默认 {@code true}（与生态内其他插件的 ERP 模块约定一致）；未装 MCERP 时无副作用。</p>
     */
    public boolean isErpEnabled() {
        return config.getBoolean("erp.enabled", true);
    }

    /** 监听地址，默认仅本机（127.0.0.1），远程访问需改为 0.0.0.0。 */
    public String getWebHost() {
        return config.getString("web.host", "127.0.0.1");
    }

    public int getWebPort() {
        return config.getInt("web.port", 8317);
    }

    /** API 访问令牌（Authorization: Bearer <token>），默认 change-me（启动时会告警）。 */
    public String getWebToken() {
        return config.getString("web.api-token", "change-me");
    }

    /** 接口路径前缀，默认 /api。 */
    public String getWebPathPrefix() {
        return config.getString("web.path-prefix", "/api");
    }
}
