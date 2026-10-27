package soys.soysgiftloft.spring.entity.vo;

/**
 * ERP 发放请求表单 —— POST /api/plugins/SOYSGiftLoft/erp/give（JSON 绑定）。
 * 由网关 JsonReader 按 JSON 键 → setter 绑定。
 */
public class ErpGiveForm {

    /** 目标玩家名（需在线）。 */
    private String player;
    /** 礼包 ID。 */
    private String pack;
    /** 是否强制发放（绕过解锁条件与已领取状态）。 */
    private boolean force;

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

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}
