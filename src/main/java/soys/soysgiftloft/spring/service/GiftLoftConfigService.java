package soys.soysgiftloft.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;

import java.util.Map;

/**
 * 礼品阁配置文件可视化编辑业务接口。
 * 读写均落在 owner 插件（SOYSGiftLoft）自己的数据目录，保存后提示热重载。
 */
public interface GiftLoftConfigService {

    /** 可编辑配置文件清单（文件元信息）。 */
    AjaxResult list();

    /** 读取单个配置文件 → 树形 Map（{@code data} 字段返回）。 */
    AjaxResult load(String fileId);

    /** 保存单个配置文件（全量树写回，注释不保留）。 */
    AjaxResult save(String fileId, Map<String, Object> data);
}
