package soys.soysgiftloft.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.Credential;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 机器管理 API 的 token 守卫装饰器（C-secure：鉴权逻辑委托 SOYSHTTPOverMC，自研仅留 v1.8 兜底）。
 *
 * <p>端点经 {@code @Anonymous} 开放给网关认证门（放行 401），故网关 AuthPolicy 不对其做 token 校验；
 * 本类在业务前完成身份闸门。校验分两级：</p>
 * <ol>
 *   <li><b>网关凭证（优先）</b>：实时取 {@code HttpOverMcPlugin.getGateway().resolveCredential(headers)}，
 *       由 SOYSHTTPOverMC 统一校验 X-API-Key / Bearer / Cookie（对 soys_api_key 表或启用颁发器）。
 *       此路径把现代鉴权完全交给 SOYSHTTPOverMC，自研零逻辑。</li>
 *   <li><b>v1.8 web.api-token 兜底</b>：未带有效网关凭证时，回退校验 config.yml {@code web.api-token}
 *       （{@link MessageDigest#isEqual} 常量时间比较），保持与 v1.8 完全一致的契约，既有机器客户端无需改动。</li>
 * </ol>
 *
 * <p><b>安全红线</b>：绝不可用 {@code ApiRequestContext.isAuthenticated()} 作闸门——{@code @Anonymous} 路径下
 * 网关仅解析（extractPresentation）不校验（resolveCredential），{@code isAuthenticated()} 仅表示"请求头带了
 * 凭证形状的材料"，乱填亦为真，直接放行即敞开 API。</p>
 *
 * <p>ERP 管理面（{@code /erp/*}）不使用本装饰器——其认证由 SOYS 网关原生体系
 * （登录令牌 / Cookie）与 {@code @ApiPermission} 权限节点负责。</p>
 */
public class TokenGuardedGiftLoftWebService implements GiftLoftWebService {

    private final SOYSGiftLoft plugin;
    private final GiftLoftWebService delegate;

    public TokenGuardedGiftLoftWebService(SOYSGiftLoft plugin, GiftLoftWebService delegate) {
        this.plugin = plugin;
        this.delegate = delegate;
    }

    @Override
    public AjaxResult status(ApiRequestContext ctx) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.status(ctx);
    }

    @Override
    public AjaxResult packs(ApiRequestContext ctx) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.packs(ctx);
    }

    @Override
    public AjaxResult packDetail(ApiRequestContext ctx, String id) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.packDetail(ctx, id);
    }

    @Override
    public AjaxResult playerByName(ApiRequestContext ctx, String name) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.playerByName(ctx, name);
    }

    @Override
    public AjaxResult playerByUuid(ApiRequestContext ctx, String uuid) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.playerByUuid(ctx, uuid);
    }

    @Override
    public AjaxResult give(ApiRequestContext ctx, String name, String body) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.give(ctx, name, body);
    }

    @Override
    public AjaxResult give(ApiRequestContext ctx, String name, String packId, boolean force) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.give(ctx, name, packId, force);
    }

    @Override
    public AjaxResult reset(ApiRequestContext ctx, String body) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.reset(ctx, body);
    }

    @Override
    public AjaxResult reset(ApiRequestContext ctx, String player, String packId) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.reset(ctx, player, packId);
    }

    @Override
    public AjaxResult reload(ApiRequestContext ctx) {
        if (!authorize(ctx)) {
            return unauthorized();
        }
        return delegate.reload(ctx);
    }

    // ================================================================
    //  内部工具
    // ================================================================

    /**
     * 身份闸门：优先委托 SOYSHTTPOverMC 校验网关凭证，v1.8 web.api-token 仅作兜底。
     * 网关实例每次实时获取（不缓存）——{@code /soyshttp reload} 会重建 GatewayFilter，
     * 缓存旧引用将导致网关凭证路径失效。
     */
    private boolean authorize(ApiRequestContext ctx) {
        // 1) 委托 SOYSHTTPOverMC 校验（X-API-Key / Bearer / Cookie 对 soys_api_key 表或启用颁发器）
        HttpOverMcPlugin soys = HttpOverMcPlugin.getInstance();
        GatewayFilter gateway = soys == null ? null : soys.getGateway();
        if (gateway != null) {
            Credential gw = gateway.resolveCredential(ctx.getHeaders());
            if (gw != null) {
                return true;
            }
        }
        // 2) 兜底：v1.8 遗留 web.api-token（向后兼容既有机器客户端；常量时间比较防时序攻击）
        String expected = plugin.getConfigManager().getWebToken();
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        String token = bearerToken(ctx);
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    /** 从请求头解析 {@code Authorization: Bearer <token>}（大小写不敏感）。 */
    private String bearerToken(ApiRequestContext ctx) {
        if (ctx == null) {
            return null;
        }
        Map<String, String> headers = ctx.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String line = headers.get("Authorization");
        if (line == null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if ("authorization".equalsIgnoreCase(e.getKey())) {
                    line = e.getValue();
                    break;
                }
            }
        }
        if (line == null || !line.startsWith("Bearer ")) {
            return null;
        }
        String token = line.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private AjaxResult unauthorized() {
        return AjaxResult.unauthorized("未授权：请求需携带 Authorization: Bearer <api-token>");
    }
}
