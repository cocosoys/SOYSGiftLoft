package soys.soysgiftloft.shttp;

import com.github.cocosoys.mc.soyshttpovermc.api.SoysExpansion;
import soys.soysgiftloft.SOYSGiftLoft;

import java.util.Collections;
import java.util.List;

/**
 * 礼品阁的 SOYSHTTPOverMC 模块扩展：仅负责自包含前端 {@code dist} 页面托管。
 *
 * <p>页面统一挂在 {@code /web/plugins/SOYSGiftLoft}；ERP 管理面端点
 * （{@code /api/plugins/SOYSGiftLoft/erp/*}）由 {@link SoysGiftLoftErpExpansion}
 * 单独注册（仅当服务器装有 MCERP 时）。本扩展只托管页面，不登记任何控制器端点。</p>
 */
public class SoysGiftLoftExpansion extends SoysExpansion {

    private final SOYSGiftLoft plugin;

    public SoysGiftLoftExpansion(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "SOYSGiftLoft";
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
     * 页面托管：本插件自包含前端 dist（{@code /web/plugins/SOYSGiftLoft}）。
     * 由本网关扩展统一托管，ERP 扩展 resourceRoot 显式返回 null，避免同目录重复登记。
     */
    @Override
    protected String resourceRoot() {
        return "dist";
    }

    @Override
    protected List<Object> buildControllers() {
        return Collections.emptyList();
    }
}
