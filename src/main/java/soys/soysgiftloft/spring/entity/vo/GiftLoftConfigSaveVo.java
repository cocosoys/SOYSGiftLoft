package soys.soysgiftloft.spring.entity.vo;

/**
 * 配置文件保存结果：文件名 + 热重载提示。
 * 字段名 tip 与前端 configPage mixin 读取的 {@code res.data.tip} 对齐。
 */
public class GiftLoftConfigSaveVo {

    /** 已保存的文件名。 */
    private String file;
    /** 热重载提示。 */
    private String tip;

    /** 静态工厂。 */
    public static GiftLoftConfigSaveVo of(String file, String tip) {
        GiftLoftConfigSaveVo vo = new GiftLoftConfigSaveVo();
        vo.file = file;
        vo.tip = tip;
        return vo;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getTip() {
        return tip;
    }

    public void setTip(String tip) {
        this.tip = tip;
    }
}
