package soys.soysgiftloft.spring;

import com.github.cocosoys.mc.soyshttpovermc.api.SoysExpansion;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.spring.controller.GiftLoftAdminController;
import soys.soysgiftloft.spring.controller.GiftLoftPackController;
import soys.soysgiftloft.spring.controller.GiftLoftPlayerController;
import soys.soysgiftloft.spring.controller.GiftLoftStatusController;
import soys.soysgiftloft.spring.impl.GiftLoftWebServiceImpl;
import soys.soysgiftloft.spring.impl.TokenGuardedGiftLoftWebService;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

import java.util.ArrayList;
import java.util.List;

/**
 * 礼品阁的 SOYSHTTPOverMC 模块扩展（机器管理 API 侧）。
 *
 * <p>注册后端点统一落在 {@code /api/plugins/SOYSGiftLoft/*}（网关自动补
 * {@code /plugins/<插件名>} 前缀，与 SOYS 自带端点零冲突）：</p>
 * <ul>
 *   <li>GET  /api/plugins/SOYSGiftLoft/status</li>
 *   <li>GET  /api/plugins/SOYSGiftLoft/packs</li>
 *   <li>GET  /api/plugins/SOYSGiftLoft/pack/{id}</li>
 *   <li>GET  /api/plugins/SOYSGiftLoft/player/{name} / /player?uuid=</li>
 *   <li>POST /api/plugins/SOYSGiftLoft/player/{name}/give</li>
 *   <li>POST /api/plugins/SOYSGiftLoft/reset / /reload</li>
 * </ul>
 *
 * <p>控制器实例在 {@link #buildControllers()} 内<b>缓存</b>：注册与反注册必须使用
 * 同一批实例（ApiRegistry 按实例反注册），故首次调用时构建并保留引用。</p>
 *
 * <p>ERP 管理面端点（{@code /erp/*}）由 {@link soys.soysgiftloft.erp.SoysGiftLoftErpExpansion}
 * 单独注册（仅当服务器装有 MCERP 时），与本扩展互不影响。</p>
 */
public class SoysGiftLoftExpansion extends SoysExpansion {

    private final SOYSGiftLoft plugin;

    /** 缓存的控制器实例列表（register/unregister 共用同一来源）。 */
    private List<Object> controllers;

    public SoysGiftLoftExpansion(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "soysgiftloft";
    }

    @Override
    public String getAuthor() {
        return "SOYS";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    protected List<Object> buildControllers() {
        if (controllers == null) {
            GiftLoftWebServiceImpl impl = new GiftLoftWebServiceImpl(plugin);
            GiftLoftWebService machineApi = new TokenGuardedGiftLoftWebService(plugin, impl);
            controllers = new ArrayList<>();
            controllers.add(new GiftLoftStatusController(machineApi));
            controllers.add(new GiftLoftPackController(machineApi));
            controllers.add(new GiftLoftPlayerController(machineApi));
            controllers.add(new GiftLoftAdminController(machineApi));
        }
        return controllers;
    }
}
