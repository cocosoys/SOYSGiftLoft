package soys.soysgiftloft.spring.entity.vo;

import java.util.List;

/**
 * 礼包保存 VO —— POST /erp/pack/create 与 POST /erp/pack/update 的请求体。
 *
 * <p>新建时 id 必填且不能与已有礼包冲突；编辑时 id 必须存在。</p>
 */
public class GiftPackSaveVO {

    /** 礼包唯一标识（小写字母/数字/连字符）。 */
    private String id;

    /** 显示名称（支持颜色码 &）。 */
    private String display;

    /** 一句话描述。 */
    private String description;

    /** 领取模式：ONCE / DAILY / WEEKLY / MONTHLY / COOLDOWN。 */
    private String claimMode = "ONCE";

    /** COOLDOWN 模式专用：冷却秒数。 */
    private long cooldownSeconds = 0L;

    /** 稀有度：COMMON / UNCOMMON / RARE / EPIC / LEGENDARY / MYTHIC（默认 COMMON）。 */
    private String rarity = "COMMON";

    /** 解锁条件树（null/空 = 无限制）。 */
    private ConditionNodeVO conditions;

    /** 奖励列表。 */
    private List<RewardVO> rewards;

    /** 领取特效（null = 不配置）。 */
    private ClaimEffectsVO claimEffects;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getClaimMode() { return claimMode; }
    public void setClaimMode(String claimMode) { this.claimMode = claimMode; }

    public long getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(long cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public ConditionNodeVO getConditions() { return conditions; }
    public void setConditions(ConditionNodeVO conditions) { this.conditions = conditions; }

    public List<RewardVO> getRewards() { return rewards; }
    public void setRewards(List<RewardVO> rewards) { this.rewards = rewards; }

    public ClaimEffectsVO getClaimEffects() { return claimEffects; }
    public void setClaimEffects(ClaimEffectsVO claimEffects) { this.claimEffects = claimEffects; }
}
