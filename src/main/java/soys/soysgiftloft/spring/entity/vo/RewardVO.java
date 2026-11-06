package soys.soysgiftloft.spring.entity.vo;

import java.util.List;

/**
 * 奖励项 VO —— 胖节点模式，按 type 取用对应字段。
 *
 * <p>type 与字段对应关系：</p>
 * <ul>
 *   <li>MONEY / POINTS / EXP：value（EXP 另用 levels 布尔）</li>
 *   <li>ITEM：material / amount / name / lore / enchant</li>
 *   <li>COMMAND：value（指令文本）/ console</li>
 *   <li>POTION：effect / duration(秒) / amplifier / particles</li>
 *   <li>CUSTOM_ITEM：plugin / id / amount / mmoType</li>
 *   <li>RANDOM：rolls / replace / pool（List&lt;PoolEntry&gt;）</li>
 * </ul>
 */
public class RewardVO {

    private String type;

    // MONEY / POINTS / COMMAND / EXP(数值)
    private Double value;

    // EXP
    private Boolean levels;

    // ITEM
    private String material;
    private Integer amount;
    private String name;
    private List<String> lore;
    private List<String> enchant;

    // COMMAND
    private Boolean console;

    // POTION
    private String effect;
    private Integer duration;
    private Integer amplifier;
    private Boolean particles;

    // CUSTOM_ITEM
    private String plugin;
    private String id;
    private String mmoType;

    // RANDOM
    private Integer rolls;
    private Boolean replace;
    private List<PoolEntry> pool;

    /** RANDOM 池条目：权重 + 嵌套奖励。 */
    public static class PoolEntry {
        private double weight;
        private RewardVO reward;

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }

        public RewardVO getReward() { return reward; }
        public void setReward(RewardVO reward) { this.reward = reward; }
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public Boolean getLevels() { return levels; }
    public void setLevels(Boolean levels) { this.levels = levels; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore; }

    public List<String> getEnchant() { return enchant; }
    public void setEnchant(List<String> enchant) { this.enchant = enchant; }

    public Boolean getConsole() { return console; }
    public void setConsole(Boolean console) { this.console = console; }

    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public Integer getAmplifier() { return amplifier; }
    public void setAmplifier(Integer amplifier) { this.amplifier = amplifier; }

    public Boolean getParticles() { return particles; }
    public void setParticles(Boolean particles) { this.particles = particles; }

    public String getPlugin() { return plugin; }
    public void setPlugin(String plugin) { this.plugin = plugin; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMmoType() { return mmoType; }
    public void setMmoType(String mmoType) { this.mmoType = mmoType; }

    public Integer getRolls() { return rolls; }
    public void setRolls(Integer rolls) { this.rolls = rolls; }

    public Boolean getReplace() { return replace; }
    public void setReplace(Boolean replace) { this.replace = replace; }

    public List<PoolEntry> getPool() { return pool; }
    public void setPool(List<PoolEntry> pool) { this.pool = pool; }
}
