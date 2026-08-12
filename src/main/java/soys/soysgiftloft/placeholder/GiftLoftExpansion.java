package soys.soysgiftloft.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.manager.GiftLoftManager;
import soys.soysgiftloft.model.GiftPack;
import soys.soysgiftloft.model.UnlockCondition;
import soys.soysgiftloft.util.ColorUtil;

import java.util.Map;
import java.util.UUID;

/**
 * PlaceholderAPI 变量扩展。
 * 提供：
 *   %sgiftloft_state_<礼包ID>%      -> locked / available / claimed
 *   %sgiftloft_total%              -> 礼包总数
 *   %sgiftloft_unlocked%           -> 当前玩家已解锁数
 *   %sgiftloft_claimed%            -> 当前玩家已领取数
 *   %sgiftloft_claimable%          -> 当前玩家可立即领取数（已解锁且不在冷却中）
 *   %sgiftloft_locked%             -> 当前玩家未解锁数
 *   %sgiftloft_playtime%           -> 当前玩家累计在线时长（可读文本）
 *   %sgiftloft_playtime_raw%       -> 当前玩家累计在线秒数
 *   %sgiftloft_progress_<礼包ID>%  -> 解锁进度（0~100 整数百分比）
 *   %sgiftloft_progress_raw_<ID>%  -> 解锁进度（0.00~1.00 小数）
 *   %sgiftloft_cooldown_<礼包ID>%  -> 周期性礼包距下次可领取的剩余秒数（无冷却为 0）
 *   %sgiftloft_unmet_<礼包ID>%     -> 尚未满足的条件数量
 *   %sgiftloft_by_condition_<TYPE>%-> 使用了指定条件类型的礼包数（按条件分组统计）
 */
public class GiftLoftExpansion extends PlaceholderExpansion {

    private final SOYSGiftLoft plugin;

    public GiftLoftExpansion(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "sgiftloft";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        GiftLoftManager mgr = plugin.getManager();
        UUID uuid = player.getUniqueId();
        boolean online = player.isOnline();

        if (params.startsWith("state_")) {
            String id = params.substring(6);
            GiftPack pack = mgr.getPack(id);
            if (pack == null) {
                return "";
            }
            // 周期性礼包冷却到期后应显示为「可领取」而非「已领取」
            if (mgr.isPackClaimed(uuid, pack)) {
                return "claimed";
            }
            if (online && pack.isUnlocked(player.getPlayer(), mgr)) {
                return "available";
            }
            return "locked";
        }

        if (params.startsWith("progress_")) {
            String id = params.substring(8);
            GiftPack pack = mgr.getPack(id);
            if (pack == null) {
                return "";
            }
            double ratio = (online && pack.getConditionRoot() != null)
                    ? pack.getConditionRoot().getProgress(player.getPlayer(), mgr) : 0.0;
            return String.valueOf((int) Math.round(ratio * 100));
        }

        if (params.startsWith("progress_raw_")) {
            String id = params.substring(13);
            GiftPack pack = mgr.getPack(id);
            if (pack == null) {
                return "";
            }
            double ratio = (online && pack.getConditionRoot() != null)
                    ? pack.getConditionRoot().getProgress(player.getPlayer(), mgr) : 0.0;
            return String.format("%.2f", ratio);
        }

        if (params.startsWith("cooldown_")) {
            String id = params.substring(9);
            GiftPack pack = mgr.getPack(id);
            if (pack == null) {
                return "";
            }
            long remaining = mgr.getCooldownRemaining(uuid, pack);
            return String.valueOf(remaining > 0 ? remaining : 0);
        }

        if (params.startsWith("unmet_")) {
            String id = params.substring(6);
            GiftPack pack = mgr.getPack(id);
            if (pack == null) {
                return "";
            }
            if (!online) {
                return "0";
            }
            return String.valueOf(pack.getUnmetConditions(player.getPlayer(), mgr).size());
        }

        if (params.startsWith("by_condition_")) {
            String typeName = params.substring(12).toUpperCase();
            try {
                UnlockCondition.Type t = UnlockCondition.Type.valueOf(typeName);
                return String.valueOf(mgr.getConditionTypeCount(t));
            } catch (IllegalArgumentException e) {
                return "";
            }
        }

        switch (params) {
            case "total":
                return String.valueOf(mgr.getPacks().size());
            case "unlocked":
                if (!online) {
                    return "0";
                }
                int unlocked = 0;
                for (GiftPack p : mgr.getPacks().values()) {
                    if (p.isUnlocked(player.getPlayer(), mgr)) {
                        unlocked++;
                    }
                }
                return String.valueOf(unlocked);
            case "claimed":
                int claimed = 0;
                for (Map.Entry<String, GiftPack> entry : mgr.getPacks().entrySet()) {
                    if (mgr.isPackClaimed(uuid, entry.getValue())) {
                        claimed++;
                    }
                }
                return String.valueOf(claimed);
            case "claimable":
                if (!online) {
                    return "0";
                }
                int claimable = 0;
                for (GiftPack p : mgr.getPacks().values()) {
                    if (p.isUnlocked(player.getPlayer(), mgr) && !mgr.isPackClaimed(uuid, p)) {
                        claimable++;
                    }
                }
                return String.valueOf(claimable);
            case "locked":
                if (!online) {
                    return "0";
                }
                int unlockedCount = 0;
                for (GiftPack p : mgr.getPacks().values()) {
                    if (p.isUnlocked(player.getPlayer(), mgr)) {
                        unlockedCount++;
                    }
                }
                return String.valueOf(mgr.getPacks().size() - unlockedCount);
            case "playtime":
                return ColorUtil.formatPlaytime(mgr.getPlaytime(uuid));
            case "playtime_raw":
                return String.valueOf(mgr.getPlaytime(uuid));
            default:
                return null;
        }
    }
}
