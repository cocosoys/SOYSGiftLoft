package soys.soysgiftloft.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.manager.GiftLoftManager;
import soys.soysgiftloft.model.GiftPack;
import soys.soysgiftloft.model.Reward;
import soys.soysgiftloft.storage.StorageType;
import soys.soysgiftloft.util.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 礼品阁指令处理器：list / info / check / claim / reload / give / reset / migrate
 */
public class GiftLoftCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

    /** 子指令 -> 所需管理员权限节点（null 表示仅受指令级 soysgiftloft.use 约束）。 */
    private static final Map<String, String> SUB_PERMISSION = new HashMap<>();
    static {
        SUB_PERMISSION.put("reload", "soysgiftloft.admin.reload");
        SUB_PERMISSION.put("give", "soysgiftloft.admin.give");
        SUB_PERMISSION.put("reset", "soysgiftloft.admin.reset");
        SUB_PERMISSION.put("migrate", "soysgiftloft.admin.migrate");
    }

    private final SOYSGiftLoft plugin;
    private final GiftLoftManager mgr;

    public GiftLoftCommand(SOYSGiftLoft plugin) {
        this.plugin = plugin;
        this.mgr = plugin.getManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase();
        // 中心化权限门禁：缺权限时提示所需节点，避免玩家盲目试错
        String perm = SUB_PERMISSION.get(sub);
        if (perm != null && !sender.hasPermission(perm)) {
            sender.sendMessage(m("no-permission-detail", "perm", perm));
            return true;
        }
        switch (sub) {
            case "list":
                return cmdList(sender);
            case "info":
                return cmdInfo(sender, args);
            case "check":
                return cmdCheck(sender, args);
            case "claim":
                return cmdClaim(sender, args);
            case "reload":
                return cmdReload(sender);
            case "give":
                return cmdGive(sender, args);
            case "reset":
                return cmdReset(sender, args);
            case "migrate":
                return cmdMigrate(sender, args);
            default:
                sendHelp(sender, label);
                return true;
        }
    }

    // ---------------- 子指令 ----------------

    private boolean cmdList(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(m("player-only"));
            return true;
        }
        Player p = (Player) sender;
        sender.sendMessage(m("list-header"));
        for (Map.Entry<String, GiftPack> e : mgr.getPacks().entrySet()) {
            String id = e.getKey();
            GiftPack pack = e.getValue();
            String state;
            if (mgr.isPackClaimed(p.getUniqueId(), pack)) {
                state = m("state-claimed");
            } else if (pack.isUnlocked(p, mgr)) {
                state = m("state-available");
            } else {
                state = m("state-locked");
            }
            sender.sendMessage(m("list-format",
                    "state", state, "pack", id, "desc", pack.getDescription()));
        }
        return true;
    }

    private boolean cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(m("pack-not-found", "pack", ""));
            return true;
        }
        GiftPack pack = mgr.getPack(args[1]);
        if (pack == null) {
            sender.sendMessage(m("pack-not-found", "pack", args[1]));
            return true;
        }
        sender.sendMessage(m("info-header", "pack", pack.getId()));
        sender.sendMessage(m("info-desc", "desc", pack.getDescription()));
        sender.sendMessage(m("info-conditions"));
        Player viewer = (sender instanceof Player) ? (Player) sender : null;
        List<String> condLines = pack.describeConditions(viewer, mgr);
        if (condLines.isEmpty()) {
            sender.sendMessage(m("condition-line", "text", "无（任何玩家均可领取）"));
        } else {
            for (String line : condLines) {
                sender.sendMessage(m("condition-line", "text", line));
            }
        }
        sender.sendMessage(m("info-rewards"));
        for (Reward r : pack.getRewards()) {
            switch (r.getType()) {
                case MONEY:
                    sender.sendMessage(m("info-reward-money", "value", String.valueOf(r.getMoney())));
                    break;
                case POINTS:
                    sender.sendMessage(m("info-reward-points", "value", String.valueOf(r.getPoints())));
                    break;
                case ITEM:
                    String mat = r.getItem() != null ? r.getItem().getType().name() : "?";
                    int amt = r.getItem() != null ? r.getItem().getAmount() : 0;
                    sender.sendMessage(m("info-reward-item", "material", mat, "amount", String.valueOf(amt)));
                    break;
                case COMMAND:
                    sender.sendMessage(m("info-reward-command", "value", r.getCommand()));
                    break;
                case EXP:
                    sender.sendMessage(m("info-reward-exp",
                            "value", String.valueOf((int) r.getExpValue()),
                            "unit", r.isExpLevels() ? "级" : "点"));
                    break;
                case POTION:
                    sender.sendMessage(m("info-reward-potion",
                            "effect", r.getPotionEffect() == null ? "?" : r.getPotionEffect(),
                            "amplifier", String.valueOf(r.getPotionAmplifier() + 1),
                            "duration", String.valueOf(r.getPotionDuration())));
                    break;
                case CUSTOM_ITEM:
                    String plug = r.getCustomPlugin() == null ? "?" : r.getCustomPlugin().name();
                    sender.sendMessage(m("info-reward-custom",
                            "plugin", plug, "id", r.getCustomId() == null ? "?" : r.getCustomId(),
                            "amount", String.valueOf(r.getCustomAmount())));
                    break;
                case RANDOM:
                    sender.sendMessage(m("info-reward-random",
                            "rolls", String.valueOf(r.getRolls()),
                            "count", String.valueOf(r.getRandomPool().size())));
                    break;
            }
        }
        return true;
    }

    private boolean cmdCheck(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(m("player-only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(m("pack-not-found", "pack", ""));
            return true;
        }
        Player p = (Player) sender;
        GiftPack pack = mgr.getPack(args[1]);
        if (pack == null) {
            sender.sendMessage(m("pack-not-found", "pack", args[1]));
            return true;
        }
        String state;
        if (mgr.isPackClaimed(p.getUniqueId(), pack)) {
            state = m("state-claimed");
        } else if (pack.isUnlocked(p, mgr)) {
            state = m("state-available");
        } else {
            state = m("state-locked");
        }
        sender.sendMessage(m("check-format", "pack", args[1], "state", state));
        if (!mgr.isPackClaimed(p.getUniqueId(), pack) && !pack.isUnlocked(p, mgr)) {
            List<String> unmet = pack.getUnmetConditions(p, mgr);
            if (!unmet.isEmpty()) {
                sender.sendMessage(m("not-unlocked-reasons"));
                for (String s : unmet) {
                    sender.sendMessage(m("condition-line", "text", s));
                }
            }
        }
        return true;
    }

    private boolean cmdClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(m("player-only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(m("pack-not-found", "pack", ""));
            return true;
        }
        Player p = (Player) sender;
        GiftLoftManager.ClaimResult res = mgr.claim(p, args[1]);
        switch (res) {
            case NOT_FOUND:
                sender.sendMessage(m("pack-not-found", "pack", args[1]));
                break;
            case ALREADY_CLAIMED:
                GiftPack claimedPack = mgr.getPack(args[1]);
                long remaining = claimedPack != null
                        ? mgr.getCooldownRemaining(p.getUniqueId(), claimedPack) : -1;
                if (claimedPack != null && claimedPack.isPeriodic() && remaining > 0) {
                    sender.sendMessage(m("already-claimed-cooldown",
                            "pack", args[1], "time", ColorUtil.formatPlaytime(remaining)));
                } else {
                    sender.sendMessage(m("already-claimed", "pack", args[1]));
                }
                break;
            case NOT_UNLOCKED:
                sender.sendMessage(m("not-unlocked", "pack", args[1]));
                GiftPack pack = mgr.getPack(args[1]);
                if (pack != null) {
                    List<String> unmet = pack.getUnmetConditions(p, mgr);
                    if (!unmet.isEmpty()) {
                        sender.sendMessage(m("not-unlocked-reasons"));
                        for (String s : unmet) {
                            sender.sendMessage(m("condition-line", "text", s));
                        }
                    }
                }
                break;
            case NO_SPACE:
                sender.sendMessage(m("no-item-space",
                        "need", String.valueOf(mgr.getPack(args[1]).getRequiredSlots()),
                        "have", String.valueOf(countFree(p))));
                break;
            case GRANT_FAILED:
                sender.sendMessage(m("grant-failed", "pack", args[1]));
                break;
            case SUCCESS:
                sender.sendMessage(m("claimed", "pack", args[1]));
                break;
        }
        return true;
    }

    private boolean cmdReload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(m("reload-success", "count", String.valueOf(mgr.getPacks().size())));
        return true;
    }

    private boolean cmdGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(m("pack-not-found", "pack", ""));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(m("player-offline", "player", args[1]));
            return true;
        }
        GiftLoftManager.ClaimResult res = mgr.adminGive(target, args[2]);
        switch (res) {
            case NOT_FOUND:
                sender.sendMessage(m("pack-not-found", "pack", args[2]));
                break;
            case NO_SPACE:
                sender.sendMessage(m("admin-gave-fail", "pack", args[2], "player", args[1], "reason", "背包空间不足"));
                break;
            case GRANT_FAILED:
                sender.sendMessage(m("admin-gave-fail", "pack", args[2], "player", args[1], "reason", "奖励发放异常，已回滚领取状态"));
                break;
            case SUCCESS:
                sender.sendMessage(m("admin-gave", "pack", args[2], "player", args[1]));
                break;
        }
        return true;
    }

    private boolean cmdReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(m("pack-not-found", "pack", ""));
            return true;
        }
        UUID uuid = null;
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            uuid = online.getUniqueId();
        } else {
            // 尝试按名称在历史数据中查找（离线玩家取最后已知 UUID 不可得，这里仅支持在线）
            sender.sendMessage(m("player-offline", "player", args[1]));
            return true;
        }
        mgr.resetClaimed(uuid, args[2]);
        mgr.getData().save();
        sender.sendMessage(m("admin-reset", "pack", args[2], "player", args[1]));
        return true;
    }

    /**
     * 手动触发存储后端间数据迁移（不依赖启动配置）。
     * <pre>
     *   /sgiftloft migrate &lt;目标后端&gt;           从当前主存储迁移到 &lt;目标后端&gt;
     *   /sgiftloft migrate &lt;源后端&gt; &lt;目标后端&gt;  从 &lt;源后端&gt; 迁移到 &lt;目标后端&gt;
     * </pre>
     * 后端标识：yaml / mysql / sqlite。目标后端必须已在 config.yml 启用。
     */
    private boolean cmdMigrate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(m("migrate-usage"));
            return true;
        }
        StorageType target = StorageType.fromId(args[1]);
        if (target == null) {
            sender.sendMessage(m("migrate-invalid-backend", "backend", args[1]));
            return true;
        }
        StorageType source;
        if (args.length >= 3) {
            source = StorageType.fromId(args[2]);
            if (source == null) {
                sender.sendMessage(m("migrate-invalid-backend", "backend", args[2]));
                return true;
            }
        } else {
            source = plugin.getStorageManager().getPrimary().getType();
        }
        if (source == target) {
            sender.sendMessage(m("migrate-same", "backend", target.getId()));
            return true;
        }
        if (!plugin.getStorageManager().isEnabled(source)) {
            sender.sendMessage(m("migrate-backend-disabled", "backend", source.getId()));
            return true;
        }
        if (!plugin.getStorageManager().isEnabled(target)) {
            sender.sendMessage(m("migrate-backend-disabled", "backend", target.getId()));
            return true;
        }
        sender.sendMessage(m("migrate-start", "from", source.getId(), "to", target.getId()));
        // 迁移可能较重，放到异步线程执行，完成后回到主线程通知发送者
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int count = plugin.getStorageManager().migrate(source, target, true);
                final int n = count;
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(m("migrate-done", "from", source.getId(), "to", target.getId(), "count", String.valueOf(n))));
            } catch (Exception e) {
                final String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(m("migrate-fail", "from", source.getId(), "to", target.getId(), "reason", err)));
            }
        });
        return true;
    }

    // ---------------- 辅助 ----------------

    private void sendHelp(CommandSender sender, String label) {
        for (String line : plugin.getMessageConfig().getStringList("help")) {
            sender.sendMessage(ColorUtil.color(line).replace("%label%", label));
        }
        // 管理员可见时补充权限节点提示
        if (sender.hasPermission("soysgiftloft.admin")) {
            sender.sendMessage(ColorUtil.color("&7管理员权限节点：reload→soysgiftloft.admin.reload | give→soysgiftloft.admin.give | reset→soysgiftloft.admin.reset | migrate→soysgiftloft.admin.migrate"));
        }
    }

    private int countFree(Player p) {
        int n = 0;
        for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType() == org.bukkit.Material.AIR) {
                n++;
            }
        }
        return n;
    }

    private String m(String path, String... kv) {
        String s = ColorUtil.color(plugin.getMessageConfig().getString(path, ""));
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                s = s.replace("%" + kv[i] + "%", kv[i + 1]);
            }
        }
        return s;
    }

    // ---------------- Tab 补全 ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> res = new ArrayList<>();
        if (args.length == 1) {
            // 仅展示发送者有权使用的子指令（管理员子指令需具备对应权限节点）
            List<String> subs = Arrays.asList("list", "info", "check", "claim", "reload", "give", "reset", "migrate");
            for (String s : subs) {
                if (!s.startsWith(args[0].toLowerCase())) {
                    continue;
                }
                String perm = SUB_PERMISSION.get(s);
                if (perm != null && !sender.hasPermission(perm)) {
                    continue;
                }
                res.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("info") || sub.equals("check") || sub.equals("claim")
                    || sub.equals("give") || sub.equals("reset")) {
                for (String id : mgr.getPacks().keySet()) {
                    if (id.startsWith(args[1].toLowerCase())) {
                        res.add(id);
                    }
                }
            } else if (sub.equals("migrate")) {
                for (StorageType t : StorageType.values()) {
                    if (t.getId().startsWith(args[1].toLowerCase())) {
                        res.add(t.getId());
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        res.add(p.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("migrate")) {
                for (StorageType t : StorageType.values()) {
                    if (t.getId().startsWith(args[2].toLowerCase())) {
                        res.add(t.getId());
                    }
                }
            }
        }
        return res;
    }
}
