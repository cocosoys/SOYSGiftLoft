package soys.soysgiftloft.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.Anonymous;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

/**
 * 机器管理 API —— 重置领取状态与重载配置。
 * <ul>
 *   <li>POST /api/plugins/SOYSGiftLoft/reset  —— 重置（表单体 {@code player=<name|uuid>&pack=<id>}，
 *       亦容忍 JSON；支持在线玩家名或离线玩家 UUID）</li>
 *   <li>POST /api/plugins/SOYSGiftLoft/reload —— 重载全部配置</li>
 * </ul>
 */
@Anonymous
public class GiftLoftAdminController {

    private final GiftLoftWebService service;

    public GiftLoftAdminController(GiftLoftWebService service) {
        this.service = service;
    }

    @ApiName("重置领取状态")
    @PostMapping("/reset")
    public AjaxResult reset(ApiRequestContext ctx, @RequestBody String body) {
        return service.reset(ctx, body);
    }

    @ApiName("重载配置")
    @PostMapping("/reload")
    public AjaxResult reload(ApiRequestContext ctx) {
        return service.reload(ctx);
    }
}
