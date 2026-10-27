package soys.soysgiftloft.spring.entity.vo;

/**
 * ERP 重置请求表单 —— POST /api/plugins/SOYSGiftLoft/erp/reset（JSON 绑定）。
 */
public class ErpResetForm {

    /** 玩家名（在线）或 UUID（离线亦可）。 */
    private String player;
    /** 要重置的礼包 ID。 */
    private String pack;

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
}
