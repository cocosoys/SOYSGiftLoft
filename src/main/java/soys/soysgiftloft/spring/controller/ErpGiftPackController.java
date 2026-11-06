package soys.soysgiftloft.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPermission;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PathVariable;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import soys.soysgiftloft.spring.entity.vo.GiftPackSaveVO;
import soys.soysgiftloft.spring.service.GiftPackAdminService;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

/**
 * ERP 管理面 —— 礼包查询与配置管理（MCERP 菜单「礼品阁 → 礼包管理」后端）。
 *
 * <p>与机器管理 API 的差异：</p>
 * <ul>
 *   <li>认证走 SOYS 网关原生体系（ERP 前端经 {@code /api/auth/login} 获得的令牌 / Cookie），
 *       端点<b>不</b>加 {@code @Anonymous}——无凭证请求被网关认证门 401 拒绝；</li>
 *   <li>授权走 Bukkit 权限镜像：{@code @ApiPermission} 声明的节点
 *       （{@code soysgiftloft.erp.*}，OP 默认放行），未授权返回 403；</li>
 *   <li>节点同时被 {@code ControllerPermScanner} 投影为 ERP 菜单下的 F 按钮。</li>
 * </ul>
 */
public class ErpGiftPackController {

    private final GiftLoftWebService queryService;
    private final GiftPackAdminService adminService;

    public ErpGiftPackController(GiftLoftWebService queryService, GiftPackAdminService adminService) {
        this.queryService = queryService;
        this.adminService = adminService;
    }

    // ===== 查询（只读） =====

    @ApiName("ERP-礼包列表")
    @ApiPermission("soysgiftloft.erp.pack.list")
    @GetMapping("/erp/packs")
    public AjaxResult packs(ApiRequestContext ctx) {
        return queryService.packs(ctx);
    }

    @ApiName("ERP-礼包详情")
    @ApiPermission("soysgiftloft.erp.pack.query")
    @GetMapping("/erp/pack/{id}")
    public AjaxResult packDetail(ApiRequestContext ctx, @PathVariable(name = "id") String id) {
        // 返回完整 GiftPackSaveVO（与 writePack 镜像），供 ERP 编辑页回显不丢字段；
        // 机器 API /pack/{id} 仍走 queryService.packDetail（PackDetailVO 摘要），契约不变。
        GiftPackSaveVO vo = adminService.readPack(id);
        return vo == null ? AjaxResult.notFound("礼包不存在: " + id) : AjaxResult.success(vo);
    }

    // ===== 写操作（CRUD） =====

    @ApiName("ERP-新建礼包")
    @ApiPermission("soysgiftloft.erp.pack.create")
    @PostMapping("/erp/pack/create")
    public AjaxResult create(@RequestBody GiftPackSaveVO vo) {
        return adminService.create(vo);
    }

    @ApiName("ERP-编辑礼包")
    @ApiPermission("soysgiftloft.erp.pack.update")
    @PostMapping("/erp/pack/update")
    public AjaxResult update(@RequestBody GiftPackSaveVO vo) {
        return adminService.update(vo);
    }

    @ApiName("ERP-删除礼包")
    @ApiPermission("soysgiftloft.erp.pack.remove")
    @PostMapping("/erp/pack/remove")
    public AjaxResult remove(@RequestBody GiftPackSaveVO vo) {
        return adminService.remove(vo == null ? null : vo.getId());
    }

    @ApiName("ERP-重载配置")
    @ApiPermission("soysgiftloft.erp.reload")
    @PostMapping("/erp/reload")
    public AjaxResult reload(ApiRequestContext ctx) {
        return queryService.reload(ctx);
    }
}
