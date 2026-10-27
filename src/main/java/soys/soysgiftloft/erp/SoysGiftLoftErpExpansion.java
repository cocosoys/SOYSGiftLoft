package soys.soysgiftloft.erp;

import com.github.cocosoys.mc.mcerp.ErpMenus;
import com.github.cocosoys.mc.mcerp.McerpExpansion;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.spring.controller.ErpGiftPackController;
import soys.soysgiftloft.spring.controller.ErpGiftPlayerController;
import soys.soysgiftloft.spring.impl.GiftLoftWebServiceImpl;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

import java.util.ArrayList;
import java.util.List;

/**
 * 礼品阁的 MCERP 管理面接入（ERP 侧端点 + 菜单登记）。
 *
 * <p>与机器管理 API 扩展（{@link soys.soysgiftloft.spring.SoysGiftLoftExpansion}）的分工：</p>
 * <ul>
 *   <li>本扩展仅在服务器装有 MCERP 时由主类实例化并注册（未装则礼品阁独立运行）；</li>
 *   <li>端点同样落在 {@code /api/plugins/SOYSGiftLoft/erp/*}（owner 插件名决定前缀，
 *       与 identifier 无关），但<b>不走</b> Bearer token——采用 SOYS 网关原生认证
 *       （ERP 前端登录态）+ {@code soysgiftloft.erp.*} 权限镜像；</li>
 *   <li>{@link #menus()} 声明 ERP 菜单树，{@code menu(..., Controller.class)} 由
 *       {@code ControllerPermScanner} 把 {@code @ApiPermission} 端点自动投影为 F 按钮。</li>
 * </ul>
 *
 * <p>页面（{@code component}）暂指向 {@code /web/plugins/SOYSGiftLoft/...} 下尚未发布的
 * 前端路由——后端先行，ERP 前端页面（RuoYi-Vue 子应用）在后续阶段交付，见
 * {@code docs/SOYSHTTPOverMC接入设计.md} 的 ERP 规划章节。</p>
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
        return "soysgiftlofterp";
    }

    @Override
    public String getAuthor() {
        return "SOYS";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
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

    @Override
    protected ErpMenus menus() {
        return ErpMenus.create()
                .dir("giftloft", "礼品阁", "gift", d -> d
                        .menu("pack", "礼包管理", "list", ErpGiftPackController.class)
                        .menu("player", "玩家查询", "user", ErpGiftPlayerController.class))
                .orderNum(80);
    }

    // ===== 端点注册（ERP 管理面） =====

    @Override
    protected List<Object> buildControllers() {
        if (controllers == null) {
            GiftLoftWebService service = new GiftLoftWebServiceImpl(plugin);
            controllers = new ArrayList<>();
            controllers.add(new ErpGiftPackController(service));
            controllers.add(new ErpGiftPlayerController(service));
        }
        return controllers;
    }
}
