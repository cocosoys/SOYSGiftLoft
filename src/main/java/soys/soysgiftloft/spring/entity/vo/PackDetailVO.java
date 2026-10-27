package soys.soysgiftloft.spring.entity.vo;

import java.util.List;

/**
 * 礼包详情 VO —— GET /api/plugins/SOYSGiftLoft/pack/{id}。
 * 字段序对齐 v1.8 {@code handlePackDetail} 键序。
 */
public class PackDetailVO {

    private String id;
    private String display;
    private String description;
    private String claimMode;
    private long cooldown;
    private int requiredSlots;
    private List<String> conditionTypes;
    private int conditionCount;
    private int rewardCount;
    private List<RewardSummaryVO> rewards;

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

    public int getConditionCount() {
        return conditionCount;
    }

    public void setConditionCount(int conditionCount) {
        this.conditionCount = conditionCount;
    }

    public int getRewardCount() {
        return rewardCount;
    }

    public void setRewardCount(int rewardCount) {
        this.rewardCount = rewardCount;
    }

    public List<RewardSummaryVO> getRewards() {
        return rewards;
    }

    public void setRewards(List<RewardSummaryVO> rewards) {
        this.rewards = rewards;
    }
}
