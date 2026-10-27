package soys.soysgiftloft.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.Anonymous;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PathVariable;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

/**
 * 机器管理 API —— 礼包查询。
 * <ul>
 *   <li>GET /api/plugins/SOYSGiftLoft/packs      —— 礼包列表</li>
 *   <li>GET /api/plugins/SOYSGiftLoft/pack/{id}  —— 单个礼包详情（含奖励摘要）</li>
 * </ul>
 */
@Anonymous
public class GiftLoftPackController {

    private final GiftLoftWebService service;

    public GiftLoftPackController(GiftLoftWebService service) {
        this.service = service;
    }

    @ApiName("礼包列表")
    @GetMapping("/packs")
    public AjaxResult packs(ApiRequestContext ctx) {
        return service.packs(ctx);
    }

    @ApiName("礼包详情")
    @GetMapping("/pack/{id}")
    public AjaxResult packDetail(ApiRequestContext ctx, @PathVariable(name = "id") String id) {
        return service.packDetail(ctx, id);
    }
}
