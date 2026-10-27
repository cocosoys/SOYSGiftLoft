package soys.soysgiftloft.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPermission;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PathVariable;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import soys.soysgiftloft.spring.entity.vo.ErpGiveForm;
import soys.soysgiftloft.spring.entity.vo.ErpResetForm;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

/**
 * ERP 管理面 —— 玩家查询、礼包发放与重置（MCERP 菜单「礼品阁 → 玩家查询」后端）。
 *
 * <p>请求体为 JSON（ERP 前端 RuoYi 形态），由网关 JsonReader 绑定到
 * {@link ErpGiveForm} / {@link ErpResetForm}。认证与授权模型同
 * {@link ErpGiftPackController}（SOYS 原生认证 + {@code soysgiftloft.erp.*} 权限镜像）。</p>
 */
public class ErpGiftPlayerController {

    private final GiftLoftWebService service;

    public ErpGiftPlayerController(GiftLoftWebService service) {
        this.service = service;
    }

    @ApiName("ERP-玩家查询")
    @ApiPermission("soysgiftloft.erp.player.query")
    @GetMapping("/erp/player/{name}")
    public AjaxResult playerByName(ApiRequestContext ctx, @PathVariable(name = "name") String name) {
        return service.playerByName(ctx, name);
    }

    @ApiName("ERP-发放礼包")
    @ApiPermission("soysgiftloft.erp.give")
    @PostMapping("/erp/give")
    public AjaxResult give(ApiRequestContext ctx, @RequestBody ErpGiveForm form) {
        if (form == null) {
            return AjaxResult.error(400, "请求体缺失或不是合法 JSON");
        }
        return service.give(ctx, form.getPlayer(), form.getPack(), form.isForce());
    }

    @ApiName("ERP-重置领取")
    @ApiPermission("soysgiftloft.erp.reset")
    @PostMapping("/erp/reset")
    public AjaxResult reset(ApiRequestContext ctx, @RequestBody ErpResetForm form) {
        if (form == null) {
            return AjaxResult.error(400, "请求体缺失或不是合法 JSON");
        }
        return service.reset(ctx, form.getPlayer(), form.getPack());
    }
}
