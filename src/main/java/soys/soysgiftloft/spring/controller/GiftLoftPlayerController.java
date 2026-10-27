package soys.soysgiftloft.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.Anonymous;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PathVariable;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestParam;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

/**
 * 机器管理 API —— 玩家状态查询与礼包发放。
 * <ul>
 *   <li>GET  /api/plugins/SOYSGiftLoft/player/{name}    —— 在线玩家状态</li>
 *   <li>GET  /api/plugins/SOYSGiftLoft/player?uuid=     —— 按 UUID 查询（在线/离线均可）</li>
 *   <li>POST /api/plugins/SOYSGiftLoft/player/{name}/give —— 发放礼包
 *       （表单体 {@code pack=<id>[&force=true]}，与 v1.8 一致；亦容忍 JSON 对象体；玩家需在线）</li>
 * </ul>
 */
@Anonymous
public class GiftLoftPlayerController {

    private final GiftLoftWebService service;

    public GiftLoftPlayerController(GiftLoftWebService service) {
        this.service = service;
    }

    @ApiName("玩家状态(在线名)")
    @GetMapping("/player/{name}")
    public AjaxResult playerByName(ApiRequestContext ctx, @PathVariable(name = "name") String name) {
        return service.playerByName(ctx, name);
    }

    @ApiName("玩家状态(UUID)")
    @GetMapping("/player")
    public AjaxResult playerByUuid(ApiRequestContext ctx,
                                   @RequestParam(name = "uuid", required = true) String uuid) {
        return service.playerByUuid(ctx, uuid);
    }

    @ApiName("发放礼包")
    @PostMapping("/player/{name}/give")
    public AjaxResult give(ApiRequestContext ctx,
                           @PathVariable(name = "name") String name,
                           @RequestBody String body) {
        return service.give(ctx, name, body);
    }
}
