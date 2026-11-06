package soys.soysgiftloft.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import soys.soysgiftloft.spring.entity.vo.GiftPackSaveVO;

/**
 * 礼包 ERP 管理面服务契约 —— 礼包 CRUD 写操作。
 *
 * <p>与 {@link GiftLoftWebService} 的差异：本接口面向 ERP 管理端，
 * 直接读写 giftpacks.yml 文件并同步内存缓存；不负责查询列表（查询走 GiftLoftWebService）。</p>
 */
public interface GiftPackAdminService {

    /**
     * 新建礼包。
     * @return 成功返回 AjaxResult.success()；id 冲突/校验失败返回 error
     */
    AjaxResult create(GiftPackSaveVO vo);

    /**
     * 编辑已有礼包（整体覆盖 giftpacks.{id} 段）。
     * @return 成功返回 success；id 不存在返回 error
     */
    AjaxResult update(GiftPackSaveVO vo);

    /**
     * 删除礼包定义（不清除玩家领取记录）。
     * @return 成功返回 success；id 不存在返回 error
     */
    AjaxResult remove(String id);

    /**
     * 读取礼包的完整可编辑视图（GiftPackSaveVO）—— 直接从 giftpacks.yml 反序列化，
     * 与 {@code writePack} 写入格式严格镜像，保证 ERP 编辑页回显不丢字段（条件树 / 奖励 / 领取特效）。
     * @return 礼包不存在或配置未加载时返回 null
     */
    GiftPackSaveVO readPack(String id);
}
