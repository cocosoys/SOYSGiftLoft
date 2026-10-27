package soys.soysgiftloft.spring.entity.vo;

/**
 * 重载结果 VO —— POST /api/plugins/SOYSGiftLoft/reload。
 * 字段序对齐 v1.8：{@code result}。
 */
public class ReloadResultVO {

    private String result;

    public ReloadResultVO() {
    }

    public ReloadResultVO(String result) {
        this.result = result;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
