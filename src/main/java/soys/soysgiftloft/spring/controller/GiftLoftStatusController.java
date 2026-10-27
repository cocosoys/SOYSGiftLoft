package soys.soysgiftloft.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.Anonymous;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

/**
 * 机器管理 API —— 插件状态（GET /api/plugins/SOYSGiftLoft/status）。
 *
 * <p>类级 {@code @Anonymous}：端点经 token 守卫装饰器自校验 Bearer api-token，
 * 不需要 SOYS 网关登录态（机器访问语义）。</p>
 */
@Anonymous
public class GiftLoftStatusController {

    private final GiftLoftWebService service;

    public GiftLoftStatusController(GiftLoftWebService service) {
        this.service = service;
    }

    @ApiName("插件状态")
    @GetMapping("/status")
    public AjaxResult status(ApiRequestContext ctx) {
        return service.status(ctx);
    }
}
