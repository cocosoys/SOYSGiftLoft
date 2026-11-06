package soys.soysgiftloft.shttp;

import com.github.cocosoys.mc.mcerp.ErpMenus;
import com.github.cocosoys.mc.mcerp.McerpExpansion;
import com.github.cocosoys.mc.soyshttpovermc.api.SoysHttpOverMcApi;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.spring.controller.ErpGiftPackController;
import soys.soysgiftloft.spring.controller.ErpGiftPlayerController;
import soys.soysgiftloft.spring.impl.GiftLoftWebServiceImpl;
import soys.soysgiftloft.spring.impl.GiftPackAdminServiceImpl;
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
 * <p><b>页面归属</b>：ERP 管理面全局只发布一份前端 SPA——宿主插件
 * {@code SOYSHTTPOverMC-ERP} 托管的 dist（{@code /web/plugins/SOYSHTTPOverMC-ERP}），
 * 礼品阁页面（前端路由 {@code /giftloft/pack}）编译进同一 SPA。因此菜单 component 必须落在
 * 该 SPA 的 pageRoot 下（见 {@link #pageRoot()} / {@link #pageDirRoot()}）；若沿用父类按
 * identifier 推导会得到 {@code /web/plugins/SOYSGiftLoft-ERP}，该前缀不对应任何已加载插件名，
 * 托管侧不会伺服，菜单必然点不开。</p>
 */
public final class SoysGiftLoftErpExpansion extends McerpExpansion {

    /**
     * 前端 SPA 的宿主插件名：页面资源与路由都挂在这个命名空间下。
     * 与 {@link #getIdentifier()}（ERP 模块 id）刻意分离——模块 id 只用于菜单/权限分组。
     */
    private static final String ERP_SPA_PLUGIN = "SOYSHTTPOverMC-ERP";

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
     * 页面前缀 = 前端 SPA 的宿主命名空间（{@code /web/plugins/SOYSHTTPOverMC-ERP}）。
     * 服务器可改 page 前缀（config.yml），故经 toolkit 求值，回退手写根路径。
     */
    @Override
    public String pageRoot() {
        SoysHttpOverMcApi api = getApi();
        if (api == null) {
            return "/web/plugins/" + ERP_SPA_PLUGIN;
        }
        String prefix = api.getToolkit().pageFullPrefix(ERP_SPA_PLUGIN);
        if (prefix == null || prefix.isEmpty()) {
            return "/web/plugins/" + ERP_SPA_PLUGIN;
        }
        return prefix;
    }

    /**
     * 菜单 component 前缀：直接等于 pageRoot，<b>不追加</b> identifier 安全目录段。
     *
     * <p>父类会拼 {@code /<NameSafe(identifier)>}（如 {@code SOYSHTTPOverMC-ERP→soyshttpovermcerp}），
     * 因为 ERP 宿主的前端路由顶层路径正是该段（{@code /soyshttpovermcerp/user}）；礼品阁的
     * 前端路由没有这个段，component 需与其对齐为 {@code /giftloft/pack}。</p>
     */
    @Override
    public String pageDirRoot() {
        return pageRoot();
    }

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
     * 菜单树。component 由 {@link #pageDirRoot()} 自动拼接，最终为
     * {@code /web/plugins/SOYSHTTPOverMC-ERP/giftloft/pack}，与前端路由
     * {@code /giftloft/pack}（views/giftloft/pack/index.vue）一一对应。
     *
     * <p>只登记 SPA 里真实存在的页面：玩家查询（{@link ErpGiftPlayerController}）端点已注册、
     * 可直连 {@code /api/plugins/SOYSGiftLoft/erp/player/*}，但前端尚无对应路由与页面，
     * 故不挂菜单（挂上去必然 404）。等前端补 {@code views/giftloft/player} 与路由后再加回。</p>
     */
    @Override
    protected ErpMenus menus() {
        return ErpMenus.create()
                .dir("giftloft", "礼品阁", "gift", d -> d
                        .menu("pack", "礼包管理", "list", ErpGiftPackController.class))
                .orderNum(80);
    }

    // ===== 端点注册（ERP 管理面） =====

    @Override
    protected List<Object> buildControllers() {
        if (controllers == null) {
            GiftLoftWebService service = new GiftLoftWebServiceImpl(plugin);
            GiftPackAdminService adminService = new GiftPackAdminServiceImpl(plugin, plugin.getManager());
            controllers = new ArrayList<>();
            controllers.add(new ErpGiftPackController(service, adminService));
            controllers.add(new ErpGiftPlayerController(service));
        }
        return controllers;
    }
}
