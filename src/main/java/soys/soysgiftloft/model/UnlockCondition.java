package soys.soysgiftloft.model;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import soys.soysgiftloft.manager.GiftLoftManager;
import soys.soysgiftloft.util.ColorUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * 礼包解锁条件（叶子节点）。
 * 支持类型：PLAYTIME / PERMISSION / POINTS / MONEY / PLACEHOLDER /
 *           DATE / ONLINE_DAYS / JOIN_DAYS / STAT / PERMGROUP / WORLD / GAMEMODE
 */
public class UnlockCondition extends ConditionNode {

    public enum Type {
        PLAYTIME, PERMISSION, POINTS, MONEY, PLACEHOLDER,
        DATE, ONLINE_DAYS, JOIN_DAYS, STAT, PERMGROUP, WORLD, GAMEMODE
    }

    private final Type type;
    private String value;       // PLAYTIME/PERMISSION/POINTS/MONEY/PERMGROUP/WORLD/GAMEMODE/ONLINE_DAYS/JOIN_DAYS
    private String placeholder;  // PLACEHOLDER
    private String operator;     // PLACEHOLDER / STAT
    private double compare;      // PLACEHOLDER / STAT

    // DATE
    private String startDate;    // 原始文本（用于展示）
    private String endDate;
    private long startMs = -1;   // 解析后的毫秒时间戳（含当天 00:00:00）
    private long endMs = -1;     // 解析后的毫秒时间戳（日期格式补到当天 23:59:59.999）

    // STAT
    private String stat;          // 统计项名（Statistic 枚举名）
    private String statEntity;    // 实体维度（EntityType，可选）
    private String statMaterial;  // 材料维度（Material，可选）

    private static final String[] DATE_FORMATS = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};

    private UnlockCondition(Type type) {
        this.type = type;
    }

    /**
     * 从 YAML 解析出的 Map 构造条件（兼容旧式扁平列表），并把校验问题写入 issues。
     */
    public static UnlockCondition fromMap(Map<String, Object> map, List<String> issues) {
        if (map == null || !map.containsKey("type")) {
            if (issues != null) {
                issues.add("[错误] 条件缺少 type 字段，已跳过该条件");
            }
            return null;
        }
        Type type;
        try {
            type = Type.valueOf(String.valueOf(map.get("type")).toUpperCase());
        } catch (IllegalArgumentException e) {
            if (issues != null) {
                issues.add("[错误] 条件 type '" + map.get("type") + "' 不是合法类型，已跳过该条件");
            }
            return null;
        }
        UnlockCondition c = new UnlockCondition(type);
        switch (type) {
            case PLAYTIME:
            case PERMISSION:
            case POINTS:
            case MONEY:
            case PERMGROUP:
            case WORLD:
            case GAMEMODE:
            case ONLINE_DAYS:
            case JOIN_DAYS:
                c.value = String.valueOf(map.getOrDefault("value", ""));
                break;
            case DATE:
                c.startDate = map.get("start") != null ? String.valueOf(map.get("start")) : null;
                c.endDate = map.get("end") != null ? String.valueOf(map.get("end")) : null;
                c.startMs = parseDate(c.startDate, false);
                c.endMs = parseDate(c.endDate, true);
                break;
            case STAT:
                c.stat = String.valueOf(map.getOrDefault("stat", ""));
                Object ent = map.getOrDefault("entity", "");
                c.statEntity = (ent == null || "null".equals(String.valueOf(ent))) ? "" : String.valueOf(ent);
                Object mat = map.getOrDefault("material", "");
                c.statMaterial = (mat == null || "null".equals(String.valueOf(mat))) ? "" : String.valueOf(mat);
                c.operator = String.valueOf(map.getOrDefault("operator", ">="));
                Object v = map.get("value");
                c.compare = (v instanceof Number) ? ((Number) v).doubleValue() : parseDouble(v, 0);
                break;
            case PLACEHOLDER:
                c.placeholder = String.valueOf(map.getOrDefault("placeholder", ""));
                c.operator = String.valueOf(map.getOrDefault("operator", ">="));
                Object vp = map.get("value");
                c.compare = (vp instanceof Number) ? ((Number) vp).doubleValue() : parseDouble(vp, 0);
                break;
        }
        return c;
    }

    /**
     * 从 ConfigurationSection 构造条件（用于条件组顶层单条叶子）。
     */
    public static UnlockCondition fromSection(ConfigurationSection sec, List<String> issues) {
        if (sec == null) {
            return null;
        }
        return fromMap(sec.getValues(false), issues);
    }

    private static long parseDate(String s, boolean isEnd) {
        if (s == null || s.trim().isEmpty()) {
            return -1;
        }
        boolean hasTime = s.contains(":");
        for (String f : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(f);
                long t = sdf.parse(s.trim()).getTime();
                if (isEnd && !hasTime) {
                    t += 86399999L; // 日期格式补到当天 23:59:59.999
                }
                return t;
            } catch (ParseException ignored) {
                // 尝试下一种格式
            }
        }
        return -1;
    }

    private static double parseDouble(Object o, double def) {
        if (o == null) {
            return def;
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean compare(double val, String op, double target) {
        if (op == null) {
            return val >= target;
        }
        switch (op) {
            case ">":  return val > target;
            case ">=": return val >= target;
            case "<":  return val < target;
            case "<=": return val <= target;
            case "==": return val == target;
            case "!=": return val != target;
            default:   return val >= target;
        }
    }

    /**
     * 判断该条件当前是否满足。
     */
    @Override
    public boolean isMet(Player player, GiftLoftManager mgr) {
        switch (type) {
            case PLAYTIME: {
                long need = (long) parseDouble(value, 0);
                return mgr.getPlaytime(player.getUniqueId()) >= need;
            }
            case PERMISSION:
                return player.hasPermission(value);
            case POINTS: {
                int need = parseInt(value, 0);
                return mgr.getPlayerPoints() != null
                        && mgr.getPlayerPoints().look(player.getUniqueId()) >= need;
            }
            case MONEY: {
                double need = parseDouble(value, 0);
                return mgr.getEconomy() != null
                        && mgr.getEconomy().getBalance(player) >= need;
            }
            case PLACEHOLDER: {
                if (!mgr.getPlaceholderHook() || placeholder == null || placeholder.isEmpty()) {
                    return false;
                }
                String parsed = mgr.parsePlaceholder(player, placeholder);
                double val = parseDouble(parsed.replaceAll("[^0-9.\\-]", ""), Double.NaN);
                if (Double.isNaN(val)) {
                    return false;
                }
                return compare(val, operator, compare);
            }
            case DATE: {
                long now = System.currentTimeMillis();
                if (startMs > 0 && now < startMs) {
                    return false;
                }
                if (endMs > 0 && now > endMs) {
                    return false;
                }
                return true;
            }
            case ONLINE_DAYS: {
                long days = (long) parseDouble(value, 0);
                return mgr.getPlaytime(player.getUniqueId()) >= days * 86400L;
            }
            case JOIN_DAYS: {
                long days = (long) parseDouble(value, 0);
                long first = player.getFirstPlayed();
                if (first <= 0) {
                    return false;
                }
                long joinedDays = (System.currentTimeMillis() - first) / 86400000L;
                return joinedDays >= days;
            }
            case STAT: {
                if (stat == null || stat.isEmpty()) {
                    return false;
                }
                try {
                    Statistic st = Statistic.valueOf(stat.toUpperCase());
                    double val;
                    if (statEntity != null && !statEntity.isEmpty()) {
                        val = player.getStatistic(st, EntityType.valueOf(statEntity.toUpperCase()));
                    } else if (statMaterial != null && !statMaterial.isEmpty()) {
                        val = player.getStatistic(st, Material.valueOf(statMaterial.toUpperCase()));
                    } else {
                        val = player.getStatistic(st);
                    }
                    return compare(val, operator, compare);
                } catch (IllegalArgumentException | NoSuchFieldError e) {
                    return false;
                }
            }
            case PERMGROUP:
                return value != null && player.hasPermission("group." + value);
            case WORLD: {
                if (value == null) {
                    return false;
                }
                String cur = player.getWorld().getName();
                for (String w : value.split(",")) {
                    if (cur.equalsIgnoreCase(w.trim())) {
                        return true;
                    }
                }
                return false;
            }
            case GAMEMODE: {
                if (value == null) {
                    return false;
                }
                String cur = player.getGameMode().name();
                for (String g : value.split(",")) {
                    if (cur.equalsIgnoreCase(g.trim())) {
                        return true;
                    }
                }
                return false;
            }
            default:
                return false;
        }
    }

    /**
     * 生成人类可读的条件描述（含当前进度）。player 为空时进度显示未知。
     */
    @Override
    public String describe(Player player, GiftLoftManager mgr) {
        switch (type) {
            case PLAYTIME: {
                long need = (long) parseDouble(value, 0);
                String cur = player != null ? ColorUtil.formatPlaytime(mgr.getPlaytime(player.getUniqueId())) : "?";
                return "累计在线 " + ColorUtil.formatPlaytime(need) + "（当前 " + cur + "）";
            }
            case PERMISSION:
                return "拥有权限 " + value;
            case POINTS: {
                int need = parseInt(value, 0);
                String cur = (player != null && mgr.getPlayerPoints() != null)
                        ? String.valueOf(mgr.getPlayerPoints().look(player.getUniqueId())) : "?";
                return "点券 ≥ " + need + "（当前 " + cur + "）";
            }
            case MONEY: {
                double need = parseDouble(value, 0);
                String cur = (player != null && mgr.getEconomy() != null)
                        ? String.valueOf(mgr.getEconomy().getBalance(player)) : "?";
                return "金币 ≥ " + need + "（当前 " + cur + "）";
            }
            case PLACEHOLDER:
                return "占位符 " + placeholder + " " + operator + " " + compare
                        + (player == null ? "（需玩家在线查看进度）" : "");
            case DATE: {
                String fmt = (startDate != null ? startDate : "*")
                        + " ~ " + (endDate != null ? endDate : "*");
                return "在 " + fmt + " 期间可领取";
            }
            case ONLINE_DAYS: {
                long need = (long) parseDouble(value, 0);
                String cur = player != null
                        ? String.valueOf(mgr.getPlaytime(player.getUniqueId()) / 86400L) : "?";
                return "累计在线满 " + need + " 天（当前 " + cur + " 天）";
            }
            case JOIN_DAYS: {
                long need = (long) parseDouble(value, 0);
                String cur = "?";
                if (player != null) {
                    long first = player.getFirstPlayed();
                    cur = first > 0 ? String.valueOf((System.currentTimeMillis() - first) / 86400000L) : "?";
                }
                return "加入服务器满 " + need + " 天（当前 " + cur + " 天）";
            }
            case STAT: {
                StringBuilder sb = new StringBuilder("统计 ");
                sb.append(stat);
                if (statEntity != null && !statEntity.isEmpty()) {
                    sb.append("[").append(statEntity).append("]");
                } else if (statMaterial != null && !statMaterial.isEmpty()) {
                    sb.append("[").append(statMaterial).append("]");
                }
                sb.append(" ").append(operator).append(" ").append(compare);
                return sb.toString();
            }
            case PERMGROUP:
                return "属于权限组 " + value;
            case WORLD:
                return "身处世界 " + value;
            case GAMEMODE:
                return "游戏模式为 " + value;
            default:
                return "";
        }
    }

    public Type getType() {
        return type;
    }

    /**
     * 配置校验：把字段合法性问题写入 issues（"[错误]" 致命 / "[警告]" 可继续）。
     */
    @Override
    public void validate(List<String> issues) {
        if (issues == null) {
            return;
        }
        switch (type) {
            case PLAYTIME:
            case PERMISSION:
            case POINTS:
            case MONEY:
            case PERMGROUP:
            case WORLD:
            case GAMEMODE:
            case ONLINE_DAYS:
            case JOIN_DAYS:
                if (value == null || value.trim().isEmpty()) {
                    issues.add("[错误] 条件 " + type + " 缺少 value 字段");
                }
                break;
            case DATE:
                if ((startDate == null || startDate.trim().isEmpty())
                        && (endDate == null || endDate.trim().isEmpty())) {
                    issues.add("[错误] 条件 DATE 至少需要 start 或 end 之一");
                }
                if (startDate != null && !startDate.trim().isEmpty() && startMs < 0) {
                    issues.add("[警告] 条件 DATE: start '" + startDate + "' 无法解析，将被忽略");
                }
                if (endDate != null && !endDate.trim().isEmpty() && endMs < 0) {
                    issues.add("[警告] 条件 DATE: end '" + endDate + "' 无法解析，将被忽略");
                }
                if (startMs > 0 && endMs > 0 && startMs > endMs) {
                    issues.add("[警告] 条件 DATE: start 晚于 end，该礼包期间将永远不可领取");
                }
                break;
            case STAT:
                if (stat == null || stat.trim().isEmpty()) {
                    issues.add("[错误] 条件 STAT 缺少 stat 字段");
                } else {
                    try {
                        Statistic.valueOf(stat.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        issues.add("[警告] 条件 STAT: '" + stat + "' 不是合法 Statistic 枚举（运行时将不满足）");
                    }
                    if (statEntity != null && !statEntity.isEmpty()) {
                        try {
                            EntityType.valueOf(statEntity.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            issues.add("[警告] 条件 STAT: entity '" + statEntity + "' 不是合法实体（将被忽略）");
                        }
                    }
                    if (statMaterial != null && !statMaterial.isEmpty()) {
                        try {
                            Material.valueOf(statMaterial.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            issues.add("[警告] 条件 STAT: material '" + statMaterial + "' 不是合法材料（将被忽略）");
                        }
                    }
                }
                break;
            case PLACEHOLDER:
                if (placeholder == null || placeholder.trim().isEmpty()) {
                    issues.add("[错误] 条件 PLACEHOLDER 缺少 placeholder 字段");
                }
                break;
            default:
                break;
        }
    }

    /**
     * 解锁进度（0~1）。已满足返回 1；数值型条件按 current/target 估算，二元条件未满足返回 0。
     */
    @Override
    public double getProgress(Player player, GiftLoftManager mgr) {
        if (isMet(player, mgr)) {
            return 1.0;
        }
        if (player == null) {
            return 0.0;
        }
        switch (type) {
            case PLAYTIME:
            case ONLINE_DAYS: {
                double targetDays = (type == PLAYTIME) ? 0 : parseDouble(value, 0);
                double target = (type == PLAYTIME) ? parseDouble(value, 0) : targetDays * 86400L;
                if (target <= 0) {
                    return 0.0;
                }
                return clamp01((double) mgr.getPlaytime(player.getUniqueId()) / target);
            }
            case JOIN_DAYS: {
                double target = parseDouble(value, 0);
                if (target <= 0) {
                    return 0.0;
                }
                long first = player.getFirstPlayed();
                if (first <= 0) {
                    return 0.0;
                }
                return clamp01((double) ((System.currentTimeMillis() - first) / 86400000L) / target);
            }
            case POINTS: {
                double target = parseInt(value, 0);
                if (target <= 0) {
                    return 0.0;
                }
                double cur = (mgr.getPlayerPoints() != null) ? mgr.getPlayerPoints().look(player.getUniqueId()) : 0;
                return clamp01(cur / target);
            }
            case MONEY: {
                double target = parseDouble(value, 0);
                if (target <= 0) {
                    return 0.0;
                }
                double cur = (mgr.getEconomy() != null) ? mgr.getEconomy().getBalance(player) : 0;
                return clamp01(cur / target);
            }
            case STAT: {
                if (stat == null || stat.isEmpty()) {
                    return 0.0;
                }
                try {
                    Statistic st = Statistic.valueOf(stat.toUpperCase());
                    double target = compare;
                    if (target <= 0) {
                        return 0.0;
                    }
                    double cur;
                    if (statEntity != null && !statEntity.isEmpty()) {
                        cur = player.getStatistic(st, EntityType.valueOf(statEntity.toUpperCase()));
                    } else if (statMaterial != null && !statMaterial.isEmpty()) {
                        cur = player.getStatistic(st, Material.valueOf(statMaterial.toUpperCase()));
                    } else {
                        cur = player.getStatistic(st);
                    }
                    return clamp01(cur / target);
                } catch (IllegalArgumentException | NoSuchFieldError e) {
                    return 0.0;
                }
            }
            case PLACEHOLDER: {
                if (!mgr.getPlaceholderHook() || placeholder == null || placeholder.isEmpty()) {
                    return 0.0;
                }
                String parsed = mgr.parsePlaceholder(player, placeholder);
                double cur = parseDouble(parsed.replaceAll("[^0-9.\\-]", ""), Double.NaN);
                if (Double.isNaN(cur) || compare <= 0) {
                    return 0.0;
                }
                return clamp01(cur / compare);
            }
            default:
                return 0.0;
        }
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    @Override
    public boolean containsType(Type t) {
        return type == t;
    }

    @Override
    public void collectTypes(Set<Type> out) {
        if (out != null) {
            out.add(type);
        }
    }
}
