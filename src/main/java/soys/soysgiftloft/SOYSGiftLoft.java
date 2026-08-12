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
 * 强制依赖：Vault、PlayerPoints、PlaceholderAPI。
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

        // 4. 依赖初始化
        if (!setupEconomy()) {
            getLogger().warning("未获取到 Vault 经济提供者，MONEY 类奖励/条件将不可用。");
        }
        setupPlayerPoints();

        // 5. 存储层初始化（按 storage.backends 选主存储，失败自动降级）
        storageManager = new StorageManager(this);
        storageManager.initialize();

        // 6. 核心管理器
        manager = new GiftLoftManager(this, economy, playerPoints, storageManager);
        manager.load();

        // 7. PlaceholderAPI 扩展
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GiftLoftExpansion(this).register();
        }

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
        return economy != null;
    }

    private void setupPlayerPoints() {
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            try {
                playerPoints = ((PlayerPoints)Bukkit.getPluginManager().getPlugin("PlayerPoints")).getAPI();
                if (manager != null) {
                    manager.setPlayerPoints(playerPoints);
                }
            } catch (Exception e) {
                getLogger().warning("获取 PlayerPoints API 失败：" + e.getMessage());
            }
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
