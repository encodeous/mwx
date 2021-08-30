package ca.encodeous.mwx.mwxplugin;

import ca.encodeous.mwx.mwxcore.CoreGame;
import ca.encodeous.mwx.mwxcore.utils.Chat;
import lobbyengine.LobbyEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldInitEvent;
import pl.kacperduras.protocoltab.manager.PacketTablist;
import pl.kacperduras.protocoltab.manager.TabItem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiscEventHandler implements Listener {
    private static ConcurrentHashMap<Player, Integer> PlayerUpdateTasks = new ConcurrentHashMap<>();
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PacketTablist list = CoreGame.Instance.tabManager.get(player);
        LobbyEngine.BuildTablist(player, list);
        list.update();
        int taskId = Bukkit.getScheduler().runTaskTimer(MissileWarsX.Instance, () -> {
            if(player.isOnline()){
                PacketTablist nList = CoreGame.Instance.tabManager.get(player);
                LobbyEngine.BuildTablist(player, nList);
                nList.update();
            }else{
                int tId = PlayerUpdateTasks.get(player);
                Bukkit.getScheduler().cancelTask(tId);
            }
        }, 20L, 20L).getTaskId();
        PlayerUpdateTasks.put(player, taskId);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onLeave(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        CoreGame.Instance.tabManager.remove(player);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreProcess(PlayerCommandPreprocessEvent event){
        if(event.getMessage().toLowerCase().startsWith("/restart")){
            event.setCancelled(true);
            event.getPlayer().sendMessage("The server can only be restarted through console");
        } else if(event.getMessage().toLowerCase().startsWith("/reload")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("The server can only be reloaded through console");
        } else if(event.getMessage().toLowerCase().startsWith("/stop")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("The server can only be stopped through console");
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCHat(AsyncPlayerChatEvent event){
        LobbyEngine.FromPlayer(event.getPlayer()).lobby.SendMessage(event.getPlayer(), event.getMessage());
        if(LobbyEngine.FromPlayer(event.getPlayer()).lobby.lobbyId != LobbyEngine.FromWorld(event.getPlayer().getWorld()).lobby.lobbyId){
            event.getPlayer().sendMessage(Chat.FCL("&cYour message was sent to the lobby you were previously on. Please use /lobby to switch to your current lobby before chatting!"));
        }
        event.setCancelled(true);
    }
}