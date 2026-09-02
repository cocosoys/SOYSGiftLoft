package soys.soysgiftloft.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import soys.soysgiftloft.SOYSGiftLoft;

/**
 * 在线时长统计监听：玩家进出时记录会话时间。
 */
public class GiftLoftListener implements Listener {

    private final SOYSGiftLoft plugin;

    public GiftLoftListener(SOYSGiftLoft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getManager().markJoin(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getManager().flushPlaytime(e.getPlayer());
        plugin.getManager().clearNotified(e.getPlayer().getUniqueId());
    }

    /**
     * 软依赖懒加载挂钩：Vault / PlayerPoints / PlaceholderAPI 作为 softdepend 在本插件之后启用，
     * 启用后补挂经济 / 点券 API 与 PlaceholderAPI 扩展，使对应功能在缺失插件加载后自动生效。
     */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        plugin.onOptionalPluginEnable(e.getPlugin().getName());
    }
}
