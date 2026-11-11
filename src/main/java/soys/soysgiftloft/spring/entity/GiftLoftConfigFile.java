package soys.soysgiftloft.spring.entity;

/**
 * 礼品阁 ERP 可编辑的配置文件清单（数据目录 = plugins/SOYSGiftLoft/）。
 *
 * <p>数据驱动的唯一事实源：菜单、端点、文件解析全部从本枚举派生。
 * 当前仅开放 {@code config.yml}（{@code giftpacks.yml} 由专门的礼包管理页维护，
 * 不在此列）。新增可编辑文件只需在此加一项 + 加一个前端 .vue。</p>
 *
 * <p>修改保存后执行 /sgiftloft reload 热重载（{@code reloadAll}）。</p>
 */
public enum GiftLoftConfigFile {

    CONFIG("config", "核心配置", "config.yml",
            "通用开关、存储后端等");

    private final String id;
    private final String name;
    private final String file;
    private final String desc;

    GiftLoftConfigFile(String id, String name, String file, String desc) {
        this.id = id;
        this.name = name;
        this.file = file;
        this.desc = desc;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFile() {
        return file;
    }

    public String getDesc() {
        return desc;
    }

    public static GiftLoftConfigFile byId(String id) {
        if (id == null) {
            return null;
        }
        for (GiftLoftConfigFile f : values()) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }
}
