package soys.soysgiftloft.spring.entity.vo;

/**
 * 重置结果 VO —— POST /api/plugins/SOYSGiftLoft/reset。
 * 字段序对齐 v1.8：{@code player} → {@code pack} → {@code result}。
 */
public class ResetResultVO {

    private String player;
    private String pack;
    private String result;

    public ResetResultVO() {
    }

    public ResetResultVO(String player, String pack, String result) {
        this.player = player;
        this.pack = pack;
        this.result = result;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getPack() {
        return pack;
    }

    public void setPack(String pack) {
        this.pack = pack;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
