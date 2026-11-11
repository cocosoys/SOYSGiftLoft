package soys.soysgiftloft.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.platform.PlatformYaml;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.ConfigUtil;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.spring.entity.GiftLoftConfigFile;
import soys.soysgiftloft.spring.entity.vo.GiftLoftConfigFileVo;
import soys.soysgiftloft.spring.entity.vo.GiftLoftConfigSaveVo;
import soys.soysgiftloft.spring.service.GiftLoftConfigService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 礼品阁配置文件编辑实现：目标目录 = owner 插件（SOYSGiftLoft）自己的数据目录。
 *
 * <p>与参考实现（SOYSHTTPOverMC-ERP）的关键差异：参考实现写死去读<b>另一个</b>插件
 * SOYSHTTPOverMC 的目录；本类要编辑的是礼品阁自己的 config.yml，故构造器注入
 * owner 插件实例，按 {@code plugin.getDataFolder()} 解析，不写死任何插件名。</p>
 *
 * <p>读写全部复用平台层：{@link PlatformYaml#load}/{@link PlatformYaml#save}
 * （统一接管编码差异，低版本 UTF-8 不乱码）+ {@link ConfigUtil#toMap}/{@link ConfigUtil#toYaml}
 * （YAML ↔ Map 递归转换）。本实现不做任何文件层解析，不绑定具体版本。
 * 全量写回（注释不保留），保存后提示 /sgiftloft reload 热重载。</p>
 */
public class GiftLoftConfigServiceImpl implements GiftLoftConfigService {

    private final SOYSGiftLoft plugin;

    public GiftLoftConfigServiceImpl(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    @Override
    public AjaxResult list() {
        try {
            List<GiftLoftConfigFileVo> rows = new ArrayList<>();
            for (GiftLoftConfigFile f : GiftLoftConfigFile.values()) {
                GiftLoftConfigFileVo vo = new GiftLoftConfigFileVo();
                vo.setId(f.getId());
                vo.setName(f.getName());
                vo.setFile(f.getFile());
                vo.setDesc(f.getDesc());
                rows.add(vo);
            }
            return AjaxResult.success(rows);
        } catch (Throwable t) {
            return AjaxResult.error("配置清单查询失败：" + t.getMessage());
        }
    }

    @Override
    public AjaxResult load(String fileId) {
        try {
            GiftLoftConfigFile f = requireFile(fileId);
            File file = resolve(f);
            if (!file.isFile()) {
                return AjaxResult.error("配置文件不存在，请先启动一次礼品阁生成默认配置: "
                        + file.getPath());
            }
            return AjaxResult.success(ConfigUtil.toMap(PlatformYaml.load(file)));
        } catch (Throwable t) {
            return AjaxResult.error("配置读取失败：" + t.getMessage());
        }
    }

    @Override
    public AjaxResult save(String fileId, Map<String, Object> data) {
        try {
            GiftLoftConfigFile f = requireFile(fileId);
            File file = resolve(f);
            if (!file.isFile()) {
                return AjaxResult.error("配置文件不存在，请先启动一次礼品阁生成默认配置: "
                        + file.getPath());
            }
            if (data == null) {
                return AjaxResult.error("配置内容为空");
            }
            PlatformYaml.save(ConfigUtil.toYaml(data), file);
            GiftLoftConfigSaveVo resp = GiftLoftConfigSaveVo.of(f.getFile(),
                    "已保存。执行 /sgiftloft reload 热重载生效（部分存储/后端项需重启服务器）");
            return AjaxResult.success(resp);
        } catch (Throwable t) {
            return AjaxResult.error("配置保存失败：" + t.getMessage());
        }
    }

    // ===== 内部 =====

    private static GiftLoftConfigFile requireFile(String fileId) {
        GiftLoftConfigFile f = GiftLoftConfigFile.byId(fileId);
        if (f == null) {
            throw new IllegalArgumentException("未知配置文件 id: " + fileId);
        }
        return f;
    }

    /** owner 插件自己的数据目录下解析目标文件。 */
    private File resolve(GiftLoftConfigFile f) {
        if (plugin == null) {
            throw new IllegalStateException("owner 插件实例未注入");
        }
        return new File(plugin.getDataFolder(), f.getFile());
    }
}
