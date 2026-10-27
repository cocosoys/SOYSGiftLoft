package soys.soysgiftloft.spring.entity.vo;

/**
 * 发放结果 VO —— POST /api/plugins/SOYSGiftLoft/player/{name}/give。
 * 字段序对齐 v1.8：{@code player} → {@code pack} → {@code force} → {@code result}。
 */
public class GiveResultVO {

    private String player;
    private String pack;
    private Boolean force;
    private String result;

    public GiveResultVO() {
    }

    public GiveResultVO(String player, String pack, Boolean force, String result) {
        this.player = player;
        this.pack = pack;
        this.force = force;
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

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
