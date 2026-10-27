package soys.soysgiftloft.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;

/**
 * 礼品阁 Web 管理接口服务契约（SOYSHTTPOverMC 注解式 API 的业务面）。
 *
 * <p>本接口只声明<b>业务用例</b>，不持有认证职责：</p>
 * <ul>
 *   <li>机器管理 API（{@code /api/plugins/SOYSGiftLoft/*}，Bearer token）经
 *       {@link soys.soysgiftloft.spring.impl.TokenGuardedGiftLoftWebService} 装饰后注入控制器；</li>
 *   <li>ERP 管理面（{@code /api/plugins/SOYSGiftLoft/erp/*}）由网关原生认证 +
 *       {@code @ApiPermission} 权限节点把关，直接使用 {@code GiftLoftWebServiceImpl}。</li>
 * </ul>
 *
 * <p>{@code ctx} 参数为网关注入的请求上下文（传输层对象）：装饰器用于读取
 * {@code Authorization} 头完成 token 校验；业务实现体不依赖其内容（预留 IP/链路追踪日志）。</p>
 *
 * <p>返回体统一为 RuoYi 风格 {@link AjaxResult}（{@code {code,msg,data}}）；
 * 载荷字段序由各 VO 的 getter 声明序决定（对齐 v1.8 手写键序）。</p>
 */
public interface GiftLoftWebService {

    /** GET /status —— 插件概览与健康检查。 */
    AjaxResult status(ApiRequestContext ctx);

    /** GET /packs —— 礼包列表（{@code total} + {@code packs}）。 */
    AjaxResult packs(ApiRequestContext ctx);

    /** GET /pack/{id} —— 单个礼包详情（含奖励摘要）；不存在返回 404。 */
    AjaxResult packDetail(ApiRequestContext ctx, String id);

    /** GET /player/{name} —— 在线玩家状态（离线且无记录返回 404）。 */
    AjaxResult playerByName(ApiRequestContext ctx, String name);

    /** GET /player?uuid=<uuid> —— 按 UUID 查询玩家状态（在线/离线均可）。 */
    AjaxResult playerByUuid(ApiRequestContext ctx, String uuid);

    /**
     * POST /player/{name}/give —— 发放礼包（机器 API 形态）。
     * 请求体为 {@code application/x-www-form-urlencoded}（{@code pack=<id>[&force=true]}），
     * 亦容忍 JSON 对象体；玩家需在线。
     */
    AjaxResult give(ApiRequestContext ctx, String name, String body);

    /**
     * POST /player/{name}/give —— 发放礼包（结构化形态，ERP 管理面 JSON 绑定用）。
     */
    AjaxResult give(ApiRequestContext ctx, String name, String packId, boolean force);

    /**
     * POST /reset —— 重置领取状态（机器 API 形态）。
     * 请求体表单 {@code player=<name|uuid>&pack=<id>}，亦容忍 JSON 对象体。
     */
    AjaxResult reset(ApiRequestContext ctx, String body);

    /**
     * POST /reset —— 重置领取状态（结构化形态，ERP 管理面 JSON 绑定用）。
     */
    AjaxResult reset(ApiRequestContext ctx, String player, String packId);

    /** POST /reload —— 重载全部配置（主配置 / 礼包 / 消息 / 管理器缓存）。 */
    AjaxResult reload(ApiRequestContext ctx);
}
