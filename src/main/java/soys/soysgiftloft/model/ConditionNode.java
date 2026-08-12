package soys.soysgiftloft.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import soys.soysgiftloft.manager.GiftLoftManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解锁条件树节点（抽象基类）。
 * 条件系统支持递归的「与/或」分组：
 * <ul>
 *   <li>{@link UnlockCondition} —— 叶子条件（PLAYTIME / PERMISSION / ... / DATE / WORLD / GAMEMODE 等）</li>
 *   <li>{@link ConditionGroup} —— 条件组，按 {@code AND} 或 {@code OR} 组合若干子节点（可嵌套）</li>
 * </ul>
 *
 * 配置格式（向后兼容）：
 * <pre>
 *   # 旧式扁平列表 —— 等价于 AND 组
 *   unlock-conditions:
 *     - type: PLAYTIME
 *       value: 3600
 *   # 新式分组 —— 支持 OR 与嵌套
 *   unlock-conditions:
 *     mode: OR
 *     list:
 *       - type: MONEY
 *         value: 1000
 *       - type: POINTS
 *         value: 500
 *       - mode: AND            # 嵌套子组
 *         list:
 *           - type: PERMISSION
 *             value: foo.bar
 * </pre>
 */
public abstract class ConditionNode {

    /**
     * 该节点当前是否满足。
     */
    public abstract boolean isMet(Player player, GiftLoftManager mgr);

    /**
     * 人类可读的节点描述（含当前进度）。player 为空时进度显示未知。
     */
    public abstract String describe(Player player, GiftLoftManager mgr);

    /**
     * 返回该节点下「尚未满足」的条件描述列表；已满足则返回空列表。
     */
    public List<String> getUnmet(Player player, GiftLoftManager mgr) {
        if (isMet(player, mgr)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(describe(player, mgr)));
    }

    /**
     * 以缩进树形返回描述（供 info 指令展示）。
     */
    public List<String> describeLines(Player player, GiftLoftManager mgr, String indent) {
        List<String> out = new ArrayList<>();
        out.add(indent + describe(player, mgr));
        return out;
    }

    /**
     * 解锁进度（0~1）。AND 组取子节点均值，OR 组取子节点最大值。
     * 用于 PlaceholderAPI 的 {@code progress_<id>} 变量。仅数值型条件参与进度计算，
     * 二元条件（权限/世界/日期等）满足为 1、未满足为 0。
     */
    public double getProgress(Player player, GiftLoftManager mgr) {
        return isMet(player, mgr) ? 1.0 : 0.0;
    }

    /**
     * 该条件树是否包含指定类型（叶子）条件。用于「按条件分组统计」占位符。
     */
    public boolean containsType(UnlockCondition.Type type) {
        return false;
    }

    /**
     * 收集该树中所有出现的叶子条件类型（去重）。
     */
    public void collectTypes(Set<UnlockCondition.Type> out) {
        // 默认无叶子
    }

    /**
     * 把配置校验问题写入 issues（"[错误]" 前缀表示致命，将跳过该礼包；"[警告]" 为可继续）。
     * 默认实现为空，叶子与分组各自覆写。
     */
    public void validate(List<String> issues) {
        // 默认无校验
    }

    /**
     * 从 YAML 节点（List / Map / ConfigurationSection）解析条件树，并把解析问题写入 issues。
     */
    @SuppressWarnings("unchecked")
    public static ConditionNode fromObject(Object obj, List<String> issues) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof ConfigurationSection) {
            return fromSection((ConfigurationSection) obj, issues);
        }
        if (obj instanceof List) {
            return fromList((List<?>) obj, issues);
        }
        if (obj instanceof Map) {
            return fromMap((Map<String, Object>) obj, issues);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static ConditionNode fromList(List<?> list, List<String> issues) {
        List<ConditionNode> kids = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map) {
                ConditionNode n = fromMap((Map<String, Object>) o, issues);
                if (n != null) {
                    kids.add(n);
                }
            } else if (o instanceof ConfigurationSection) {
                ConditionNode n = fromSection((ConfigurationSection) o, issues);
                if (n != null) {
                    kids.add(n);
                }
            }
        }
        // 旧式扁平列表 = 全部满足（AND）
        return new ConditionGroup(ConditionGroup.Mode.AND, kids);
    }

    private static ConditionNode fromSection(ConfigurationSection sec, List<String> issues) {
        if (sec == null) {
            return null;
        }
        if (sec.contains("type")) {
            // 顶层为单条叶子条件
            return UnlockCondition.fromSection(sec, issues);
        }
        // 视为条件组
        ConditionGroup.Mode mode = ConditionGroup.Mode.parse(sec.getString("mode", "AND"), ConditionGroup.Mode.AND);
        List<ConditionNode> kids = new ArrayList<>();
        List<Map<?, ?>> list = sec.getMapList("list");
        if (list.isEmpty() && !sec.contains("list")) {
            // 既无 type 也无 list —— 视为空组（无限制）
            issues.add("[警告] 条件组缺少 list 子项，视为「无限制」");
        }
        for (Map<?, ?> m : list) {
            ConditionNode n = fromMap((Map<String, Object>) m, issues);
            if (n != null) {
                kids.add(n);
            }
        }
        return new ConditionGroup(mode, kids);
    }

    @SuppressWarnings("unchecked")
    private static ConditionNode fromMap(Map<String, Object> map, List<String> issues) {
        if (map == null) {
            return null;
        }
        Object typeObj = map.get("type");
        Object modeObj = map.get("mode");
        // 含 mode 或（无 type 但含 list）→ 条件组
        if (modeObj != null || (typeObj == null && map.containsKey("list"))) {
            ConditionGroup.Mode mode = ConditionGroup.Mode.parse(
                    modeObj != null ? String.valueOf(modeObj) : "AND", ConditionGroup.Mode.AND);
            List<ConditionNode> kids = new ArrayList<>();
            Object listObj = map.get("list");
            if (listObj instanceof List) {
                for (Object o : (List<?>) listObj) {
                    if (o instanceof Map) {
                        ConditionNode n = fromMap((Map<String, Object>) o, issues);
                        if (n != null) {
                            kids.add(n);
                        }
                    } else if (o instanceof ConfigurationSection) {
                        ConditionNode n = fromSection((ConfigurationSection) o, issues);
                        if (n != null) {
                            kids.add(n);
                        }
                    }
                }
            }
            return new ConditionGroup(mode, kids);
        }
        // 叶子条件
        return UnlockCondition.fromMap(map, issues);
    }
}
