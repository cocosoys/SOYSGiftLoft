package soys.soysgiftloft.model;

import org.bukkit.entity.Player;
import soys.soysgiftloft.manager.GiftLoftManager;

import java.util.List;
import java.util.Set;

/**
 * 条件组：按 {@link Mode#AND}（全部满足）或 {@link Mode#OR}（满足任一）组合若干子节点。
 * 子节点本身也可以是 {@link ConditionGroup}，从而支持任意层级的「与/或」嵌套。
 */
public class ConditionGroup extends ConditionNode {

    public enum Mode {
        AND, OR;

        public static Mode parse(String raw, Mode def) {
            if (raw == null) {
                return def;
            }
            String s = raw.trim().toUpperCase();
            if (s.equals("OR") || s.equals("ANY") || s.equals("||")) {
                return OR;
            }
            if (s.equals("AND") || s.equals("ALL") || s.equals("&&")) {
                return AND;
            }
            return def;
        }
    }

    private final Mode mode;
    private final List<ConditionNode> children;

    public ConditionGroup(Mode mode, List<ConditionNode> children) {
        this.mode = mode != null ? mode : Mode.AND;
        this.children = children;
    }

    @Override
    public boolean isMet(Player player, GiftLoftManager mgr) {
        if (children == null || children.isEmpty()) {
            // 空组视为无限制，始终满足
            return true;
        }
        if (mode == Mode.OR) {
            for (ConditionNode c : children) {
                if (c.isMet(player, mgr)) {
                    return true;
                }
            }
            return false;
        }
        for (ConditionNode c : children) {
            if (!c.isMet(player, mgr)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String describe(Player player, GiftLoftManager mgr) {
        if (children == null || children.isEmpty()) {
            return "(无条件)";
        }
        String join = (mode == Mode.OR) ? " 或 " : " 且 ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                sb.append(join);
            }
            sb.append(children.get(i).describe(player, mgr));
        }
        return sb.toString();
    }

    @Override
    public List<String> getUnmet(Player player, GiftLoftManager mgr) {
        if (isMet(player, mgr)) {
            return new java.util.ArrayList<>();
        }
        List<String> out = new java.util.ArrayList<>();
        if (mode == Mode.OR) {
            // OR 组整体未满足：列出全部子条件（满足任一即可）
            for (ConditionNode c : children) {
                out.add(c.describe(player, mgr));
            }
        } else {
            // AND 组：仅列出未满足的子条件
            for (ConditionNode c : children) {
                if (!c.isMet(player, mgr)) {
                    out.addAll(c.getUnmet(player, mgr));
                }
            }
        }
        return out;
    }

    @Override
    public List<String> describeLines(Player player, GiftLoftManager mgr, String indent) {
        List<String> out = new java.util.ArrayList<>();
        String label = (mode == Mode.OR) ? "[满足任一即可]" : "[全部满足]";
        out.add(indent + label);
        if (children != null) {
            for (ConditionNode c : children) {
                out.addAll(c.describeLines(player, mgr, indent + "  "));
            }
        }
        return out;
    }

    public Mode getMode() {
        return mode;
    }

    public List<ConditionNode> getChildren() {
        return children;
    }

    @Override
    public double getProgress(Player player, GiftLoftManager mgr) {
        if (children == null || children.isEmpty()) {
            // 空组视为无限制，满进度
            return 1.0;
        }
        if (mode == Mode.OR) {
            double max = 0.0;
            for (ConditionNode c : children) {
                max = Math.max(max, c.getProgress(player, mgr));
            }
            return max;
        }
        // AND：子节点进度均值（整体完成度）
        double sum = 0.0;
        for (ConditionNode c : children) {
            sum += c.getProgress(player, mgr);
        }
        return sum / children.size();
    }

    @Override
    public boolean containsType(UnlockCondition.Type type) {
        if (children == null) {
            return false;
        }
        for (ConditionNode c : children) {
            if (c.containsType(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void collectTypes(Set<UnlockCondition.Type> out) {
        if (children == null) {
            return;
        }
        for (ConditionNode c : children) {
            c.collectTypes(out);
        }
    }

    @Override
    public void validate(List<String> issues) {
        if (children == null) {
            return;
        }
        for (ConditionNode c : children) {
            c.validate(issues);
        }
    }
}
