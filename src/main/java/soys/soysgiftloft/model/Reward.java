package soys.soysgiftloft.model;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import soys.soysgiftloft.manager.GiftLoftManager;
import soys.soysgiftloft.util.ColorUtil;
import soys.soysgiftloft.util.CustomItemProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 礼包奖励项。
 * 支持类型：MONEY / POINTS / ITEM / COMMAND / EXP / POTION / CUSTOM_ITEM / RANDOM
 */
public class Reward {

    public enum Type {
        MONEY, POINTS, ITEM, COMMAND, EXP, POTION, CUSTOM_ITEM, RANDOM
    }

    private final Type type;
    // MONEY / POINTS / EXP
    private double money;
    private int points;
    private double expValue;
    private boolean expLevels = false;
    // ITEM
    private ItemStack item;
    // COMMAND
    private String command;
    private boolean console;
    // POTION
    private String potionEffect = null;   // PotionEffectType 名
    private int potionDuration = 0;       // 秒
    private int potionAmplifier = 0;      // 0 基（0=I）
    private boolean potionAmbient = false;
    private boolean potionParticles = true;
    // CUSTOM_ITEM
    private CustomItemProvider.Plugin customPlugin = null;
    private String customId = null;
    private String customMmoType = null;
    private int customAmount = 1;
    // RANDOM
    private int rolls = 1;
    private boolean replace = true;
    private List<RandomEntry> randomPool = new ArrayList<>();

    private Reward(Type type) {
        this.type = type;
    }

    /** 随机奖励池中的单条：权重 + 嵌套奖励（奖励可继续是 RANDOM，支持嵌套）。 */
    public static class RandomEntry {
        final double weight;
        final Reward reward;
        RandomEntry(double weight, Reward reward) {
            this.weight = weight;
            this.reward = reward;
        }
    }

    @SuppressWarnings("unchecked")
    public static Reward fromMap(Map<String, Object> map, List<String> issues) {
        if (map == null || !map.containsKey("type")) {
            if (issues != null) {
                issues.add("[错误] 奖励缺少 type 字段，已跳过该奖励");
            }
            return null;
        }
        Type type;
        try {
            type = Type.valueOf(String.valueOf(map.get("type")).toUpperCase());
        } catch (IllegalArgumentException e) {
            if (issues != null) {
                issues.add("[错误] 奖励 type '" + map.get("type") + "' 不是合法类型，已跳过该奖励");
            }
            return null;
        }
        Reward r = new Reward(type);
        switch (type) {
            case MONEY: {
                r.money = toDouble(map.get("value"), 0);
                if (r.money <= 0 && issues != null) {
                    issues.add("[警告] 奖励 MONEY: value <= 0，将发放 0 金币");
                }
                break;
            }
            case POINTS: {
                r.points = (int) toDouble(map.get("value"), 0);
                if (r.points <= 0 && issues != null) {
                    issues.add("[警告] 奖励 POINTS: value <= 0，将发放 0 点券");
                }
                break;
            }
            case EXP: {
                r.expValue = toDouble(map.get("value"), 0);
                r.expLevels = Boolean.parseBoolean(String.valueOf(map.getOrDefault("levels", false)));
                if (r.expValue <= 0 && issues != null) {
                    issues.add("[警告] 奖励 EXP: value <= 0，将发放 0 经验");
                }
                break;
            }
            case ITEM:
                r.item = parseItem(map, issues);
                break;
            case COMMAND:
                r.command = String.valueOf(map.getOrDefault("value", ""));
                r.console = Boolean.parseBoolean(String.valueOf(map.getOrDefault("console", true)));
                break;
            case POTION:
                r.potionEffect = String.valueOf(map.getOrDefault("effect", "")).toUpperCase();
                r.potionDuration = (int) toDouble(map.getOrDefault("duration", 0), 0);
                r.potionAmplifier = (int) toDouble(map.getOrDefault("amplifier", 0), 0);
                r.potionAmbient = Boolean.parseBoolean(String.valueOf(map.getOrDefault("ambient", false)));
                r.potionParticles = Boolean.parseBoolean(String.valueOf(map.getOrDefault("particles", true)));
                if ((r.potionEffect.isEmpty() || r.potionDuration <= 0) && issues != null) {
                    issues.add("[警告] 奖励 POTION: effect 为空或 duration <= 0，届时将不发放药水效果");
                }
                break;
            case CUSTOM_ITEM:
                r.customPlugin = parsePlugin(String.valueOf(map.getOrDefault("plugin", "")));
                r.customId = String.valueOf(map.getOrDefault("id", ""));
                r.customMmoType = String.valueOf(map.getOrDefault("mmo-type", ""));
                if ("null".equals(r.customMmoType)) {
                    r.customMmoType = null;
                }
                r.customAmount = Math.max(1, (int) toDouble(map.getOrDefault("amount", 1), 1));
                if (r.customPlugin == null && issues != null) {
                    issues.add("[错误] 奖励 CUSTOM_ITEM: plugin '" + map.getOrDefault("plugin", "") + "' 无效（支持 ITEMSADDER/ORAXEN/MMOCORE/MMOITEMS）");
                }
                if ((r.customId == null || r.customId.isEmpty()) && issues != null) {
                    issues.add("[错误] 奖励 CUSTOM_ITEM: 缺少 id 字段");
                }
                break;
            case RANDOM:
                r.rolls = Math.max(1, (int) toDouble(map.getOrDefault("rolls", 1), 1));
                r.replace = Boolean.parseBoolean(String.valueOf(map.getOrDefault("replace", true)));
                Object poolObj = map.get("pool");
                if (poolObj instanceof List) {
                    for (Object o : (List<?>) poolObj) {
                        if (!(o instanceof Map)) {
                            continue;
                        }
                        Map<String, Object> entry = (Map<String, Object>) o;
                        double w = toDouble(entry.get("weight"), toDouble(entry.get("chance"), 0));
                        Object rewObj = entry.get("reward");
                        Reward rew = null;
                        if (rewObj instanceof Map) {
                            rew = fromMap((Map<String, Object>) rewObj, issues);
                        }
                        if (rew != null && w > 0) {
                            r.randomPool.add(new RandomEntry(w, rew));
                        }
                    }
                }
                if (r.randomPool.isEmpty() && issues != null) {
                    issues.add("[警告] 奖励 RANDOM: 奖励池为空或权重均 <= 0，抽取将无效果");
                }
                break;
        }
        return r;
    }

    private static CustomItemProvider.Plugin parsePlugin(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return CustomItemProvider.Plugin.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double toDouble(Object o, double def) {
        if (o == null) {
            return def;
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    private static ItemStack parseItem(Map<String, Object> map, List<String> issues) {
        String matName = String.valueOf(map.getOrDefault("material", "STONE"));
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            mat = Material.STONE;
            if (issues != null) {
                issues.add("[警告] 奖励 ITEM: 材质 '" + matName + "' 不存在，已回退为 STONE");
            }
        }
        int amount = (int) toDouble(map.getOrDefault("amount", 1), 1);
        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (map.containsKey("name")) {
            meta.setDisplayName(ColorUtil.color(String.valueOf(map.get("name"))));
        }
        if (map.containsKey("lore")) {
            Object loreObj = map.get("lore");
            if (loreObj instanceof List) {
                meta.setLore(ColorUtil.color(castStringList((List<?>) loreObj)));
            }
        }
        item.setItemMeta(meta);

        if (map.containsKey("enchant")) {
            Object enchObj = map.get("enchant");
            if (enchObj instanceof List) {
                for (Object o : (List<?>) enchObj) {
                    String[] parts = String.valueOf(o).split(":");
                    if (parts.length < 2) {
                        continue;
                    }
                    Enchantment ench = Enchantment.getByName(parts[0]);
                    if (ench != null) {
                        try {
                            item.addUnsafeEnchantment(ench, Integer.parseInt(parts[1]));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        // 隐藏附魔/属性花括号，保持物品显示整洁
        try {
            ItemMeta m2 = item.getItemMeta();
            if (m2 != null) {
                m2.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(m2);
            }
        } catch (Throwable ignored) {
            // 低版本可能不支持 ItemFlag，忽略
        }
        return item;
    }

    private static List<String> castStringList(List<?> list) {
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    /**
     * 将奖励发放给玩家。
     */
    public void grant(Player player, GiftLoftManager mgr) {
        switch (type) {
            case MONEY:
                if (mgr.getEconomy() != null) {
                    mgr.getEconomy().depositPlayer(player, money);
                }
                break;
            case POINTS:
                if (mgr.getPlayerPoints() != null) {
                    mgr.getPlayerPoints().give(player.getUniqueId(), points);
                }
                break;
            case EXP:
                if (expLevels) {
                    player.giveExpLevels((int) expValue);
                } else {
                    player.giveExp((int) expValue);
                }
                break;
            case ITEM:
                if (item != null) {
                    player.getInventory().addItem(item.clone());
                }
                break;
            case COMMAND:
                if (command != null && !command.isEmpty()) {
                    String cmd = command.replace("%player%", player.getName());
                    if (console) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    } else {
                        Bukkit.dispatchCommand(player, cmd);
                    }
                }
                break;
            case POTION:
                grantPotion(player);
                break;
            case CUSTOM_ITEM:
                grantCustomItem(player, mgr);
                break;
            case RANDOM:
                grantRandom(player, mgr);
                break;
        }
    }

    private void grantPotion(Player player) {
        if (potionEffect == null || potionEffect.isEmpty() || potionDuration <= 0) {
            return;
        }
        PotionEffectType type = PotionEffectType.getByName(potionEffect);
        if (type == null) {
            return;
        }
        int ticks = potionDuration * 20;
        PotionEffect effect = new PotionEffect(type, ticks,
                Math.max(0, potionAmplifier), potionAmbient, potionParticles);
        try {
            player.addPotionEffect(effect, true);
        } catch (Throwable ignored) {
            // 某些效果类型/版本不兼容时忽略
        }
    }

    private void grantCustomItem(Player player, GiftLoftManager mgr) {
        if (customPlugin == null || customId == null) {
            return;
        }
        ItemStack is = CustomItemProvider.resolve(customPlugin, customId, customMmoType);
        if (is != null) {
            is.setAmount(Math.max(1, customAmount));
            player.getInventory().addItem(is);
        } else {
            // 插件未安装或物品不存在：仅告警，不中断整体领取
            Bukkit.getLogger().warning("[SOYSGiftLoft] 自定义物品发放失败："
                    + customPlugin.name() + ":" + customId + "（插件未安装或物品不存在）");
            if (mgr != null) {
                String msg = ColorUtil.color(mgr.getMessageConfig().getString("custom-item-failed",
                        "&c自定义物品发放失败：%plugin%:%id%"));
                msg = msg.replace("%plugin%", customPlugin.name()).replace("%id%", customId);
                player.sendMessage(msg);
            }
        }
    }

    private static final Random RND = new Random();

    private void grantRandom(Player player, GiftLoftManager mgr) {
        if (randomPool.isEmpty() || rolls <= 0) {
            return;
        }
        List<RandomEntry> available = new ArrayList<>(randomPool);
        for (int i = 0; i < rolls; i++) {
            if (available.isEmpty()) {
                break;
            }
            double total = 0;
            for (RandomEntry e : available) {
                total += e.weight;
            }
            if (total <= 0) {
                break;
            }
            double pick = RND.nextDouble() * total;
            double acc = 0;
            RandomEntry chosen = available.get(available.size() - 1);
            for (RandomEntry e : available) {
                acc += e.weight;
                if (pick <= acc) {
                    chosen = e;
                    break;
                }
            }
            chosen.reward.grant(player, mgr);
            if (!replace) {
                available.remove(chosen);
            }
        }
    }

    /**
     * 计算该奖励发放所需的最小背包空位（用于领取前空间校验）。
     */
    public int requiredSlots() {
        switch (type) {
            case ITEM:
                return itemSlots(item);
            case CUSTOM_ITEM:
                return itemSlots(customAmount);
            case RANDOM: {
                int maxPerRoll = 0;
                for (RandomEntry e : randomPool) {
                    maxPerRoll = Math.max(maxPerRoll, e.reward.requiredSlots());
                }
                return maxPerRoll * Math.max(1, rolls);
            }
            default:
                return 0;
        }
    }

    private static int itemSlots(ItemStack is) {
        if (is == null) {
            return 0;
        }
        int max = is.getMaxStackSize();
        if (max <= 0) {
            max = 64;
        }
        return (int) Math.ceil((double) is.getAmount() / max);
    }

    private static int itemSlots(int amount) {
        if (amount <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) amount / 64);
    }

    public Type getType() {
        return type;
    }

    public double getMoney() {
        return money;
    }

    public int getPoints() {
        return points;
    }

    public double getExpValue() {
        return expValue;
    }

    public boolean isExpLevels() {
        return expLevels;
    }

    public ItemStack getItem() {
        return item;
    }

    public String getCommand() {
        return command;
    }

    public boolean isConsole() {
        return console;
    }

    public String getPotionEffect() {
        return potionEffect;
    }

    public int getPotionDuration() {
        return potionDuration;
    }

    public int getPotionAmplifier() {
        return potionAmplifier;
    }

    public boolean isPotionAmbient() {
        return potionAmbient;
    }

    public boolean isPotionParticles() {
        return potionParticles;
    }

    public CustomItemProvider.Plugin getCustomPlugin() {
        return customPlugin;
    }

    public String getCustomId() {
        return customId;
    }

    public String getCustomMmoType() {
        return customMmoType;
    }

    public int getCustomAmount() {
        return customAmount;
    }

    public int getRolls() {
        return rolls;
    }

    public boolean isReplace() {
        return replace;
    }

    public List<RandomEntry> getRandomPool() {
        return randomPool;
    }
}
