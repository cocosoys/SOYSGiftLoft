package soys.soysgiftloft.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
}
