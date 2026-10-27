package soys.soysgiftloft;

import com.github.cocosoys.mc.soyshttpovermc.api.event.SoysReadyEvent;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import soys.soysgiftloft.command.GiftLoftCommand;
import soys.soysgiftloft.config.ConfigManager;
import soys.soysgiftloft.erp.SoysGiftLoftErpExpansion;
import soys.soysgiftloft.listener.GiftLoftListener;
import soys.soysgiftloft.manager.GiftLoftManager;
import soys.soysgiftloft.placeholder.GiftLoftExpansion;
import soys.soysgiftloft.spring.SoysGiftLoftExpansion;
import soys.soysgiftloft.storage.StorageManager;

import soys.soysgiftloft.util.ColorUtil;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SOYSGiftLoft —— 礼品阁插件主类。
 * 玩家满足解锁条件后可领取礼包（金钱 / 点券 / 物品 / 指令）。
 * 软依赖：Vault、PlayerPoints、PlaceholderAPI（缺失时自动降级，不影响加载）。
 *
 * <p>存储策略（与 SOYSLinkTeam 一致）：所有启用的后端按优先级选主存储
 * （MYSQL &gt; SQLITE &gt; YAML），其余作为辅助存储被镜像写入；MySQL 不可用时自动降级。
 * 启动时若开启 migrate-on-startup，会把 YAML 存量数据迁移进 MySQL。</p>
 *
 * <p><b>Web 管理接口</b>：自 v1.9.0 起不再内置 HttpServer，改为声明 SOYSHTTPOverMC
 * 模块扩展（{@link SoysGiftLoftExpansion}），端点由网关统一挂在
 * {@code /api/plugins/SOYSGiftLoft/*}；装 MCERP 时另注册 {@link SoysGiftLoftErpExpansion}
 * 提供 ERP 菜单与 {@code /erp/*} 管理面端点。两者均按 {@code web.enabled} 开关生效。</p>
 */
public final class SOYSGiftLoft extends JavaPlugin implements Listener {

    private Economy economy;
    private PlayerPointsAPI playerPoints;
    private GiftLoftManager manager;
    private ConfigManager configManager;
    private StorageManager storageManager;

    /** SOYSHTTPOverMC 机器管理 API 扩展（/api/plugins/SOYSGiftLoft/*；未装 SOYS 时为 null）。 */
    private SoysGiftLoftExpansion soysExpansion;

    /** MCERP 管理面扩展（ERP 菜单 + /erp/* 端点；未装 MCERP 时为 null）。 */
    private SoysGiftLoftErpExpansion erpExpansion;

    /** 插件启用时刻（毫秒），用于 status 端点的 uptimeSeconds。 */
    private long enabledAt;

    private File giftPackFile;
    private FileConfiguration giftPackConfig;

    /** 所有语言键的统一前缀（插件名小写），避免与其他插件的 i18n 命名空间冲突。 */
    private static final String LANG_PREFIX = "soysgiftloft.";

    private FileConfiguration messageConfig;
    private String messageLanguage = "zh_cn";

    private boolean debug = false;

    /** PlaceholderAPI 扩展是否已注册（防止 softdepend 晚加载时重复注册）。 */
    private boolean papiRegistered = false;

    @Override
    public void onEnable() {
        enabledAt = System.currentTimeMillis();
        // 1. 主配置文件与访问器
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        debug = configManager.isDebug();
        messageLanguage = configManager.getLanguage();

        // 2. 消息配置文件（多语言：language/<lang>.yml，对齐 SOYSHTTPOverMC 约定）
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
        // 主类自身作为监听器：承接 SoysReadyEvent（SOYS 晚于本插件就绪时补挂扩展）
        Bukkit.getPluginManager().registerEvents(this, this);

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

        // 11. Web / 后台管理接口（默认关闭，按 config.yml 中 web.enabled 启用）
        //     自 v1.9.0 起改为 SOYSHTTPOverMC 模块扩展注册，不再自建 HttpServer
        setupWebApi();

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
        // Web 接口：反注册扩展（端点 / ERP 菜单同步摘除），先于数据落盘
        if (erpExpansion != null) {
            erpExpansion.unregister();
            erpExpansion = null;
        }
        if (soysExpansion != null) {
            soysExpansion.unregister();
            soysExpansion = null;
        }
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
        } else if ("SOYSHTTPOverMC".equals(name) || "MCERP".equals(name)) {
            // 网关 / ERP 晚于本插件启用：补挂 Web 管理接口
            setupWebApi();
        }
    }

    // ---------------- Web 管理接口（SOYSHTTPOverMC / MCERP 扩展） ----------------

    /**
     * 挂载 Web 管理接口（幂等，可重复调用）。
     *
     * <p>注册两个互不影响的模块扩展：</p>
     * <ul>
     *   <li>{@link SoysGiftLoftExpansion} —— 机器管理 API，端点
     *       {@code /api/plugins/SOYSGiftLoft/*}，凭 {@code Authorization: Bearer <api-token>} 访问；</li>
     *   <li>{@link SoysGiftLoftErpExpansion} —— 仅当装有 MCERP 时注册，提供 ERP 菜单与
     *       {@code /erp/*} 端点，认证/授权走网关登录态 + {@code soysgiftloft.erp.*} 权限。</li>
     * </ul>
     *
     * <p>SOYS 网关未就绪（本插件先于 SOYS 加载）时注册会失败，此时由
     * {@link #onSoysReady(SoysReadyEvent)} 在其就绪事件里重试。</p>
     */
    private void setupWebApi() {
        if (configManager == null || !configManager.isWebEnabled()) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("SOYSHTTPOverMC")) {
            getLogger().warning("web.enabled=true 但未检测到 SOYSHTTPOverMC，Web 管理接口未挂载"
                    + "（安装该插件后执行 /sgiftloft reload 即可）。");
            return;
        }
        registerSoysExpansion();
        registerErpExpansion();
    }

    /**
     * 注册机器管理 API 扩展（已注册则跳过）。
     */
    private void registerSoysExpansion() {
        if (soysExpansion != null) {
            return;
        }
        SoysGiftLoftExpansion expansion = new SoysGiftLoftExpansion(this);
        if (expansion.register()) {
            soysExpansion = expansion;
            getLogger().info("Web 管理接口已挂载：/api/plugins/SOYSGiftLoft/*（Bearer token 鉴权）。");
        } else {
            getLogger().warning("Web 管理接口注册失败（SOYS 网关未就绪或 identifier 冲突），"
                    + "将由 SoysReadyEvent 重试。");
        }
    }

    /**
     * 注册 MCERP 管理面扩展（未装 MCERP 时静默跳过；已注册则跳过）。
     *
     * <p>注意：本方法内对 {@link SoysGiftLoftErpExpansion} 的首次主动引用才触发其父类
     * {@code McerpExpansion} 的类解析，故 {@code isPluginEnabled("MCERP")} 前置守卫可保证
     * 未安装 MCERP 时不出现 NoClassDefFoundError。</p>
     */
    private void registerErpExpansion() {
        if (erpExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MCERP")) {
            return;
        }
        SoysGiftLoftErpExpansion expansion = new SoysGiftLoftErpExpansion(this);
        if (expansion.register()) {
            erpExpansion = expansion;
            getLogger().info("MCERP 管理面已接入：菜单「礼品阁」+ /api/plugins/SOYSGiftLoft/erp/*。");
        } else {
            getLogger().warning("MCERP 管理面注册失败（SOYS 网关未就绪或 identifier 冲突），"
                    + "可由 /mcerp reload 或 /soyshttp reload 兜底重试。");
        }
    }

    /**
     * SOYS 网关就绪事件：解决「本插件先于 SOYS 加载」时的延迟注册（同步事件，主线程）。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoysReady(SoysReadyEvent event) {
        setupWebApi();
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
     *   <li>language/&lt;lang&gt;.yml（用户当前语言，缺失则从 jar 释放）</li>
     *   <li>jar 内置 language/zh_cn.yml（最终兜底，保证所有键可用）</li>
     * </ol>
     * <p>语言代码经 {@link #normalizeLanguage(String)} 归一化为小写下划线（zh/zh_CN/zh-CN → zh_cn）。</p>
     */
    public void reloadMessages() {
        if (configManager != null) {
            messageLanguage = normalizeLanguage(configManager.getLanguage());
        }

        // 最终兜底：jar 内置 language/zh_cn.yml
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(getResource("language/zh_cn.yml"), StandardCharsets.UTF_8));

        // 当前语言文件
        File langFile = new File(getDataFolder(), "language/" + messageLanguage + ".yml");
        if (!langFile.exists()) {
            String res = "language/" + messageLanguage + ".yml";
            if (getResource(res) != null) {
                saveResource(res, false);
            } else {
                messageLanguage = "zh_cn";
                langFile = new File(getDataFolder(), "language/zh_cn.yml");
                if (!langFile.exists()) {
                    saveResource("language/zh_cn.yml", false);
                }
            }
        }
        YamlConfiguration lang = YamlConfiguration.loadConfiguration(langFile);
        lang.setDefaults(defaults);
        this.messageConfig = lang;
    }

    /**
     * 归一化语言代码为小写下划线形式（zh / zh_CN / zh-CN / en_US / en-US → zh_cn / en_us）。
     */
    private String normalizeLanguage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "zh_cn";
        }
        String s = raw.trim().toLowerCase().replace('-', '_');
        // 兼容短代码 zh / en
        if ("zh".equals(s)) return "zh_cn";
        if ("en".equals(s)) return "en_us";
        return s;
    }

    /**
     * 读取一条消息（自动加 soysoygiftloft. 前缀、自动转色、自动替换 {0}/{1}... 占位符）。
     *
     * @param key 短键，如 "claim.already-claimed"（不含前缀）
     * @param args 按顺序替换 {0} {1} ... 的实参
     * @return 替换后的消息字符串；键缺失时返回空字符串
     */
    public String msg(String key, Object... args) {
        if (messageConfig == null) {
            return "";
        }
        String raw = messageConfig.getString(LANG_PREFIX + key, "");
        if (raw.isEmpty()) {
            return "";
        }
        raw = ColorUtil.color(raw);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                raw = raw.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        return raw;
    }

    /**
     * 读取多行消息（如 help），按 \n 拆分为列表。
     */
    public List<String> msgList(String key) {
        List<String> out = new ArrayList<>();
        if (messageConfig == null) {
            return out;
        }
        String raw = messageConfig.getString(LANG_PREFIX + key, "");
        if (raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split("\n")) {
            out.add(ColorUtil.color(line));
        }
        return out;
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
        // Web 接口：web.enabled 由关转开时补挂扩展（token 变更被适配器实时读取，无需重注册）
        if (configManager.isWebEnabled()) {
            setupWebApi();
        } else if (soysExpansion != null) {
            if (erpExpansion != null) {
                erpExpansion.unregister();
                erpExpansion = null;
            }
            soysExpansion.unregister();
            soysExpansion = null;
            getLogger().info("web.enabled=false，Web 管理接口已卸载。");
        }
    }

    public GiftLoftManager getManager() {
        return manager;
    }

    /** 插件启用时刻（status 端点 uptimeSeconds 用）。 */
    public long getEnabledAt() {
        return enabledAt;
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
