package ca.encodeous.mwx.commands;

import ca.encodeous.mwx.MissileWarsCommand;
import ca.encodeous.mwx.core.lang.Strings;
import ca.encodeous.mwx.core.utils.Chat;
import ca.encodeous.mwx.engines.lobby.Lobby;
import ca.encodeous.mwx.engines.lobby.LobbyEngine;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;

public class playersCommand extends MissileWarsCommand {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(sender instanceof Player){
            ((Player) sender).addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 10 * 20, -1, false, false, false), true);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Strings.PLAYERS_ONLINE, Bukkit.getOnlinePlayers().size()));
        for(Lobby lobby : LobbyEngine.Lobbies.values()){
            sb.append(Strings.MISSILE_WARS_BRAND + " &6" + lobby.lobbyId + ": " + Chat.FormatPlayerlist(new ArrayList<>(lobby.GetPlayers())) + "\n");
        }
        sender.sendMessage(Chat.FCL(sb.toString()));
        return true;
    }
}
