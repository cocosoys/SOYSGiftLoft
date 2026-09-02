package soys.soysgiftloft;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import soys.soysgiftloft.command.GiftLoftCommand;
import soys.soysgiftloft.config.ConfigManager;
import soys.soysgiftloft.listener.GiftLoftListener;
import soys.soysgiftloft.manager.GiftLoftManager;
import soys.soysgiftloft.placeholder.GiftLoftExpansion;
import soys.soysgiftloft.storage.StorageManager;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * SOYSGiftLoft —— 礼品阁插件主类。
 * 玩家满足解锁条件后可领取礼包（金钱 / 点券 / 物品 / 指令）。
 * 软依赖：Vault、PlayerPoints、PlaceholderAPI（缺失时自动降级，不影响加载）。
 *
 * <p>存储策略（与 SOYSLinkTeam 一致）：所有启用的后端按优先级选主存储
 * （MYSQL &gt; SQLITE &gt; YAML），其余作为辅助存储被镜像写入；MySQL 不可用时自动降级。
 * 启动时若开启 migrate-on-startup，会把 YAML 存量数据迁移进 MySQL。</p>
 */
public final class SOYSGiftLoft extends JavaPlugin {

    private Economy economy;
    private PlayerPointsAPI playerPoints;
    private GiftLoftManager manager;
    private ConfigManager configManager;
    private StorageManager storageManager;

    private File giftPackFile;
    private FileConfiguration giftPackConfig;

    private File messageFile;
    private FileConfiguration messageConfig;
    private String messageLanguage = "zh";

    private boolean debug = false;

    /** PlaceholderAPI 扩展是否已注册（防止 softdepend 晚加载时重复注册）。 */
    private boolean papiRegistered = false;

    @Override
    public void onEnable() {
        // 1. 主配置文件与访问器
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        debug = configManager.isDebug();
        messageLanguage = configManager.getLanguage();

        // 2. 消息配置文件（多语言：lang/<language>.yml，回退 message.yml -> 内置 lang/zh.yml）
        reloadMessages();

        // 3. 礼包配置文件
        reloadGiftPacks();

        // 4. 依赖初始化（softdepend 可能晚于本插件加载，缺失时不在此告警，
        //    由 [依赖状态] 汇总 + 各依赖启用后 PluginEnableEvent 补挂的日志呈现）
        setupEconomy();
        setupPlayerPoints();

        // 5. 存储层初始化（按 storage.backends 选主存储，失败自动降级）
        storageManager = new StorageManager(this);
        storageManager.initialize();

        // 6. 核心管理器
        manager = new GiftLoftManager(this, economy, playerPoints, storageManager);
        manager.load();

        // 7. PlaceholderAPI 扩展（softdepend 可能在本插件之后加载，故仅尝试；缺失时由 PluginEnableEvent 补挂）
        setupPlaceholderAPI();

        // 8. 指令与监听
        GiftLoftCommand cmd = new GiftLoftCommand(this);
        if (getCommand("sgiftloft") != null) {
            getCommand("sgiftloft").setExecutor(cmd);
            getCommand("sgiftloft").setTabCompleter(cmd);
        }
        Bukkit.getPluginManager().registerEvents(new GiftLoftListener(this), this);

        // 9. 自动保存任务（在线时长 + 领取状态）
        long interval = configManager.getAutoSaveIntervalTicks();
        if (interval > 0) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (manager == null) {
                    return;
                }
                for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                    manager.flushPlaytime(p);
                }
                manager.getData().save();
                if (debug) {
                    getLogger().info("已自动保存玩家数据。");
                }
            }, interval, interval);
        }

        // 10. 自动解锁提示任务（每隔 auto-notify 秒检测在线玩家新解锁的礼包）
        long notifyTicks = configManager.getAutoNotifyTicks();
        if (notifyTicks > 0) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (manager == null) {
                    return;
                }
                for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                    manager.checkAndNotify(p);
                }
            }, notifyTicks, notifyTicks);
            getLogger().info("已启用自动解锁提示，间隔 " + configManager.getAutoNotify() + " 秒。");
        }

        // 依赖状态汇总：软依赖缺失时自动降级，便于单机调试
        getLogger().info("[依赖状态] Vault=" + (economy != null ? "已加载" : "未加载(已降级)")
                + " / PlayerPoints=" + (playerPoints != null ? "已加载" : "未加载(已降级)")
                + " / PlaceholderAPI=" + (manager != null && manager.getPlaceholderHook() ? "已加载" : "未加载(已降级)"));

        getLogger().info("SOYSGiftLoft 已启用，加载礼包 " + manager.getPacks().size()
                + " 个（主存储：" + storageManager.getPrimary().getType().getDisplayName()
                + "，语言：" + messageLanguage + "）。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                manager.flushPlaytime(p);
            }
            manager.getData().save();
        }
        if (storageManager != null) {
            storageManager.shutdown();
        }
        getLogger().info("SOYSGiftLoft 已停用。");
    }

    // ---------------- 依赖初始化 ----------------

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        if (manager != null) {
            manager.setEconomy(economy);
        }
        getLogger().info("已挂钩 Vault 经济提供者（MONEY 奖励/条件可用）。");
        return economy != null;
    }

    private void setupPlayerPoints() {
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            try {
                playerPoints = ((PlayerPoints)Bukkit.getPluginManager().getPlugin("PlayerPoints")).getAPI();
                if (manager != null) {
                    manager.setPlayerPoints(playerPoints);
                }
                getLogger().info("已挂钩 PlayerPoints（POINTS 奖励/条件可用）。");
            } catch (Exception e) {
                getLogger().warning("获取 PlayerPoints API 失败：" + e.getMessage());
            }
        }
    }

    /**
     * 初始化 / 补挂 PlaceholderAPI 扩展。
     * 由于 softdepend 顺序，PlaceholderAPI 可能在本插件之后才启用，故在
     * {@link #onOptionalPluginEnable(String)} 中于其 PluginEnableEvent 时再次调用。
     */
    private void setupPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            if (manager != null) {
                manager.setPlaceholderHook(true);
            }
            if (!papiRegistered) {
                new GiftLoftExpansion(this).register();
                papiRegistered = true;
                getLogger().info("已注册 PlaceholderAPI 变量扩展（%sgiftloft_*%）。");
            }
        }
    }

    /**
     * 软依赖插件启用后的懒加载挂钩（由 GiftLoftListener 的 PluginEnableEvent 调用）。
     * 这样即便 Vault / PlayerPoints / PlaceholderAPI 在本插件之后加载，对应功能仍能正确启用。
     *
     * @param name 启用插件的名称
     */
    public void onOptionalPluginEnable(String name) {
        if ("Vault".equals(name)) {
            setupEconomy();
        } else if ("PlayerPoints".equals(name)) {
            setupPlayerPoints();
        } else if ("PlaceholderAPI".equals(name)) {
            setupPlaceholderAPI();
        }
    }

    // ---------------- 配置访问 ----------------

    /**
     * 加载（或首次生成）giftpacks.yml。
     */
    public void reloadGiftPacks() {
        if (giftPackFile == null) {
            giftPackFile = new File(getDataFolder(), "giftpacks.yml");
        }
        if (!giftPackFile.exists()) {
            saveResource("giftpacks.yml", false);
        }
        giftPackConfig = YamlConfiguration.loadConfiguration(giftPackFile);
    }

    /**
     * 加载多语言消息文件。
     * <p>加载顺序（优先级从高到低）：</p>
     * <ol>
     *   <li>lang/&lt;language&gt;.yml（用户当前语言，缺失则从 jar 释放）</li>
     *   <li>message.yml（旧版独立消息文件，兼容升级前的自定义）</li>
     *   <li>jar 内置 lang/zh.yml（最终兜底，保证新键可用）</li>
     * </ol>
     */
    public void reloadMessages() {
        if (configManager != null) {
            messageLanguage = configManager.getLanguage();
        }

        // 最终兜底：jar 内置 lang/zh.yml
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(getResource("lang/zh.yml"), StandardCharsets.UTF_8));

        // 旧版 message.yml 兼容层：以 jar 内置 zh 为默认
        if (messageFile == null) {
            messageFile = new File(getDataFolder(), "message.yml");
        }
        if (!messageFile.exists()) {
            saveResource("message.yml", false);
        }
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(messageFile);
        legacy.setDefaults(defaults);

        // 当前语言文件
        File langFile = new File(getDataFolder(), "lang/" + messageLanguage + ".yml");
        if (!langFile.exists()) {
            // 从 jar 释放对应语言文件；若该语言在 jar 中不存在则回退到 zh
            String res = "lang/" + messageLanguage + ".yml";
            if (getResource(res) != null) {
                saveResource(res, false);
            } else {
                messageLanguage = "zh";
                langFile = new File(getDataFolder(), "lang/zh.yml");
                if (!langFile.exists()) {
                    saveResource("lang/zh.yml", false);
                }
            }
        }
        YamlConfiguration lang = YamlConfiguration.loadConfiguration(langFile);
        lang.setDefaults(legacy);
        this.messageConfig = lang;
    }

    public FileConfiguration getGiftPackConfig() {
        return giftPackConfig;
    }

    public FileConfiguration getMessageConfig() {
        return messageConfig;
    }

    public String getMessageLanguage() {
        return messageLanguage;
    }

    /**
     * reload 指令统一入口：重载主配置、礼包、消息，并刷新管理器缓存。
     */
    public void reloadAll() {
        reloadConfig();
        configManager.reload();
        messageLanguage = configManager.getLanguage();
        reloadMessages();
        reloadGiftPacks();
        manager.invalidateDefaultEffects();
        manager.load();
    }

    public GiftLoftManager getManager() {
        return manager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public boolean isDebug() {
        return debug;
    }
}
