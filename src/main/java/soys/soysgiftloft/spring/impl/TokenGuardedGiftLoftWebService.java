package soys.soysgiftloft.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.spring.service.GiftLoftWebService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 机器管理 API 的 Bearer token 守卫装饰器。
 *
 * <p>端点经 {@code @Anonymous} 开放给网关认证门（放行 401），由本类在业务前完成
 * 与 v1.8 完全一致的 token 校验：{@code Authorization: Bearer <api-token>}
 * （config.yml {@code web.api-token}，{@link MessageDigest#isEqual} 常量时间比较防时序攻击）。
 * 现有调用方无需任何改动。</p>
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

    /** Bearer token 校验（常量时间比较）。 */
    private boolean authorize(ApiRequestContext ctx) {
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
