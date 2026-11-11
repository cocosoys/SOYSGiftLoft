package soys.soysgiftloft.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPermission;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestParam;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import soys.soysgiftloft.spring.service.GiftLoftConfigService;

import java.util.Map;

/**
 * ERP 管理面 —— 配置文件可视化编辑端点（MCERP 菜单「礼品阁 → 插件配置」后端）。
 * 路径：/api/plugins/SOYSGiftLoft/erp/config/*。
 *
 * <p>认证走 SOYS 网关原生体系（ERP 登录态），授权走 Bukkit 权限镜像
 * （{@code soysgiftloft.erp.config.*}，OP 默认放行）；节点同时被
 * {@code ControllerPermScanner} 投影为 ERP 菜单下的 F 按钮。本类只声明接口并转发
 * {@link GiftLoftConfigService}。</p>
 */
@RequestMapping("/erp/config")
public class ErpConfigController {

    private final GiftLoftConfigService configService;

    public ErpConfigController(GiftLoftConfigService configService) {
        this.configService = configService;
    }

    @ApiName("配置文件清单")
    @ApiPermission("soysgiftloft.erp.config.list")
    @GetMapping("/files")
    public AjaxResult files() {
        return configService.list();
    }

    @ApiName("配置文件读取")
    @ApiPermission("soysgiftloft.erp.config.query")
    @GetMapping("/load")
    public AjaxResult load(@RequestParam(name = "file", required = true) String file) {
        return configService.load(file);
    }

    @ApiName("配置文件保存")
    @ApiPermission("soysgiftloft.erp.config.edit")
    @PostMapping("/save")
    public AjaxResult save(@RequestParam(name = "file", required = true) String file,
                           @RequestBody Map<String, Object> data) {
        return configService.save(file, data);
    }
}
