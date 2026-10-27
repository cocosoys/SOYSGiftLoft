package soys.soysgiftloft.spring.entity.vo;

import java.util.List;

/**
 * 礼包摘要 VO（列表项）—— 字段序对齐 v1.8 {@code packSummary} 键序。
 */
public class PackSummaryVO {

    private String id;
    private String display;
    private String description;
    private String claimMode;
    private long cooldown;
    private int requiredSlots;
    private List<String> conditionTypes;
    private int rewardCount;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getClaimMode() {
        return claimMode;
    }

    public void setClaimMode(String claimMode) {
        this.claimMode = claimMode;
    }

    public long getCooldown() {
        return cooldown;
    }

    public void setCooldown(long cooldown) {
        this.cooldown = cooldown;
    }

    public int getRequiredSlots() {
        return requiredSlots;
    }

    public void setRequiredSlots(int requiredSlots) {
        this.requiredSlots = requiredSlots;
    }

    public List<String> getConditionTypes() {
        return conditionTypes;
    }

    public void setConditionTypes(List<String> conditionTypes) {
        this.conditionTypes = conditionTypes;
    }

    public int getRewardCount() {
        return rewardCount;
    }

    public void setRewardCount(int rewardCount) {
        this.rewardCount = rewardCount;
    }
}
