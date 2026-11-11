package soys.soysgiftloft.shttp;

import com.github.cocosoys.mc.mcerp.ErpMenus;
import com.github.cocosoys.mc.mcerp.McerpExpansion;
import com.github.cocosoys.mc.soyshttpovermc.api.SoysHttpOverMcApi;
import org.bukkit.plugin.Plugin;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.spring.controller.ErpConfigController;
import soys.soysgiftloft.spring.controller.ErpGiftPackController;
import soys.soysgiftloft.spring.controller.ErpGiftPlayerController;
import soys.soysgiftloft.spring.impl.GiftLoftConfigServiceImpl;
import soys.soysgiftloft.spring.impl.GiftLoftWebServiceImpl;
import soys.soysgiftloft.spring.impl.GiftPackAdminServiceImpl;
import soys.soysgiftloft.spring.service.GiftLoftConfigService;
import soys.soysgiftloft.spring.service.GiftLoftWebService;
import soys.soysgiftloft.spring.service.GiftPackAdminService;

import java.util.ArrayList;
import java.util.List;

/**
 * 礼品阁的 MCERP 管理面接入（ERP 侧端点 + 菜单登记）。
 *
 * <p>与机器管理 API 扩展（{@link SoysGiftLoftExpansion}）的分工：</p>
 * <ul>
 *   <li>本扩展仅在服务器装有 MCERP 时由主类实例化并注册（未装则礼品阁独立运行）；</li>
 *   <li>端点同样落在 {@code /api/plugins/SOYSGiftLoft/erp/*}（owner 插件名决定前缀，
 *       与 identifier 无关），但<b>不走</b> Bearer token——采用 SOYS 网关原生认证
 *       （ERP 前端登录态）+ {@code soysgiftloft.erp.*} 权限镜像；</li>
 *   <li>{@link #menus()} 声明 ERP 菜单树，{@code menu(..., Controller.class)} 由
 *       {@code ControllerPermScanner} 把 {@code @ApiPermission} 端点自动投影为 F 按钮。</li>
 * </ul>
 *
 * <p><b>页面归属（自包含）</b>：前端 dist 由 owner 插件自己的网关扩展
 * {@link SoysGiftLoftExpansion}（{@code resourceRoot=dist}）按 owner 插件名托管在
 * {@code /web/plugins/SOYSGiftLoft}。本扩展<b>不</b>重复托管（{@link #resourceRoot()}
 * 返回 null），只把菜单 component 指向该命名空间。框架默认 {@code pageRoot()} 取
 * identifier（{@code SOYSGiftLoft-ERP}）会得到不存在的 {@code /web/plugins/SOYSGiftLoft-ERP}，
 * 故覆写为 owner 插件名；{@code pageDirRoot} 沿用父类默认（pageRoot +
 * {@code /soysgiftlofterp}），与前端 moduleRoot 顶层段对齐。</p>
 */
public final class SoysGiftLoftErpExpansion extends McerpExpansion {

    private final SOYSGiftLoft plugin;

    /** 缓存的控制器实例列表（register/unregister 共用同一来源）。 */
    private List<Object> controllers;

    public SoysGiftLoftErpExpansion(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "SOYSGiftLoft-ERP";
    }

    @Override
    public String getAuthor() {
        return "SOYS";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * 页面前缀 = owner 插件自己的命名空间（{@code /web/plugins/SOYSGiftLoft}），
     * 与 dist 托管路径一致。服务器可改 page 前缀（config.yml），故经 toolkit 求值。
     */
    @Override
    public String pageRoot() {
        Plugin owner = getOwner();
        String name = owner == null ? "SOYSGiftLoft" : owner.getName();
        SoysHttpOverMcApi api = getApi();
        String prefix = api == null ? null : api.getToolkit().pageFullPrefix(name);
        if (prefix == null || prefix.isEmpty()) {
            return "/web/plugins/" + name;
        }
        return prefix;
    }

    /** 不托管页面：dist 由网关扩展 {@link SoysGiftLoftExpansion} 统一托管，避免重复登记与 tag 分裂。 */
    @Override
    protected String resourceRoot() {
        return null;
    }

    // pageDirRoot 沿用父类默认：pageRoot() + "/" + NameSafe.path(identifier)
    // = /web/plugins/SOYSGiftLoft/soysgiftlofterp，与前端 moduleRoot 顶层段对齐。

    // ===== ERP 声明钩子 =====

    @Override
    protected String displayName() {
        return "礼品阁";
    }

    @Override
    protected String icon() {
        return "gift";
    }

    @Override
    protected int sortOrder() {
        return 80;
    }

    @Override
    protected String permission() {
        return "soysgiftloft.erp";
    }

    /**
     * 菜单树：根级叶子「礼包管理」+ 根级目录「插件配置」。
     *
     * <p>MCERP 会把模块本身（{@link #displayName()} = 礼品阁）渲染成侧边栏一级节点，
     * 模块的 children 即二级。</p>
     *
     * <p>① {@code menu("pack",…)} 是根级叶子，不写 component/path：component 由框架自动拼成
     * {@code pageDirRoot + "/pack"} = {@code /web/plugins/SOYSGiftLoft/soysgiftlofterp/pack}，
     * 与前端路由 {@code /soysgiftlofterp/pack} 同源。</p>
     *
     * <p>② {@code dir("config","插件配置")} 是根级 M 目录，其下叶子
     * {@code menu("config","核心配置")} 的 component 自动拼成
     * {@code pageDirRoot + "/config/config"} =
     * {@code /web/plugins/SOYSGiftLoft/soysgiftlofterp/config/config}，
     * 与前端路由 {@code /soysgiftlofterp/config/config} 同源。叶子带
     * {@link ErpConfigController}，其三个端点自动投影为 F 按钮。</p>
     *
     * <p>玩家查询（{@link ErpGiftPlayerController}）端点已注册、可直连
     * {@code /api/plugins/SOYSGiftLoft/erp/player/*}，但前端尚无对应路由与页面，
     * 故不挂菜单（挂上去必然 404）。等前端补页面与路由后再加回。</p>
     */
    @Override
    protected ErpMenus menus() {
        return ErpMenus.create()
                .menu("pack", "礼包管理", "list", ErpGiftPackController.class)
                .orderNum(1)
                .dir("config", "插件配置", "edit", d -> d
                        .menu("config", "核心配置", "input", ErpConfigController.class)
                        .orderNum(10));
    }

    // ===== 端点注册（ERP 管理面） =====

    @Override
    protected List<Object> buildControllers() {
        if (controllers == null) {
            GiftLoftWebService service = new GiftLoftWebServiceImpl(plugin);
            GiftPackAdminService adminService = new GiftPackAdminServiceImpl(plugin, plugin.getManager());
            GiftLoftConfigService configService = new GiftLoftConfigServiceImpl(plugin);
            controllers = new ArrayList<>();
            controllers.add(new ErpGiftPackController(service, adminService));
            controllers.add(new ErpGiftPlayerController(service));
            controllers.add(new ErpConfigController(configService));
        }
        return controllers;
    }
}
