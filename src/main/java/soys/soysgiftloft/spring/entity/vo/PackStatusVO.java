package soys.soysgiftloft.spring.entity.vo;

/**
 * 单礼包在玩家视角下的状态 VO —— 字段序对齐 v1.8 {@code packStatus} 键序。
 *
 * <p>{@code unlocked} 为 {@code Boolean}（可空）：离线玩家无法判定解锁条件时 v1.8 输出
 * {@code "unlocked":null}，本 VO <b>不</b>加 {@code @JsonInclude(NON_NULL)}，
 * 由 JsonWriter 原样输出 null，保持逐字段兼容。</p>
 */
public class PackStatusVO {

    private String id;
    private String display;
    private boolean claimed;
    private long cooldownRemaining;
    private String state;
    private Boolean unlocked;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public long getCooldownRemaining() {
        return cooldownRemaining;
    }

    public void setCooldownRemaining(long cooldownRemaining) {
        this.cooldownRemaining = cooldownRemaining;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Boolean getUnlocked() {
        return unlocked;
    }

    public void setUnlocked(Boolean unlocked) {
        this.unlocked = unlocked;
    }
}
