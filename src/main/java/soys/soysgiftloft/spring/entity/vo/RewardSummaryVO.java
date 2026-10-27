package soys.soysgiftloft.spring.entity.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 奖励摘要 VO —— 按奖励类型输出关键字段，不暴露物品 NBT 等内部细节。
 *
 * <p>字段序对齐 v1.8 {@code rewardSummary} 键序。v1.8 按类型<b>有条件地</b> put 键
 * （如 MONEY 只输出 money，ITEM 只输出 material/amount）；本 VO 以类级
 * {@code @JsonInclude(NON_NULL)} 复现该语义——与当前奖励类型无关的字段为 null，
 * JsonWriter 序列化时自动裁剪，输出键集合与 v1.8 一致。</p>
 *
 * <p>已知边界差异：CUSTOM_ITEM 的 {@code customPlugin} 为 null 时 v1.8 输出
 * {@code "plugin":null}，本 VO 裁剪该键（非法配置下才会出现，正常配置 customPlugin 必非 null）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RewardSummaryVO {

    private String type;
    // MONEY / POINTS / EXP
    private Double money;
    private Integer points;
    private Double expValue;
    private Boolean expLevels;
    // ITEM
    private String material;
    private Integer amount;
    // COMMAND
    private String command;
    private Boolean console;
    // POTION
    private String effect;
    private Integer duration;
    private Integer amplifier;
    // CUSTOM_ITEM
    private String plugin;
    private String id;
    // RANDOM
    private Integer rolls;
    private Boolean replace;
    private Integer poolSize;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getMoney() {
        return money;
    }

    public void setMoney(Double money) {
        this.money = money;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public Double getExpValue() {
        return expValue;
    }

    public void setExpValue(Double expValue) {
        this.expValue = expValue;
    }

    public Boolean getExpLevels() {
        return expLevels;
    }

    public void setExpLevels(Boolean expLevels) {
        this.expLevels = expLevels;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Boolean getConsole() {
        return console;
    }

    public void setConsole(Boolean console) {
        this.console = console;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getAmplifier() {
        return amplifier;
    }

    public void setAmplifier(Integer amplifier) {
        this.amplifier = amplifier;
    }

    public String getPlugin() {
        return plugin;
    }

    public void setPlugin(String plugin) {
        this.plugin = plugin;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getRolls() {
        return rolls;
    }

    public void setRolls(Integer rolls) {
        this.rolls = rolls;
    }

    public Boolean getReplace() {
        return replace;
    }

    public void setReplace(Boolean replace) {
        this.replace = replace;
    }

    public Integer getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(Integer poolSize) {
        this.poolSize = poolSize;
    }
}
