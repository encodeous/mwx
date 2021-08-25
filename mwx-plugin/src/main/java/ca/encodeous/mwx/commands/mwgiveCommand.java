package ca.encodeous.mwx.commands;

import ca.encodeous.mwx.configuration.MissileWarsItem;
import ca.encodeous.mwx.mwxcore.CoreGame;
import ca.encodeous.mwx.mwxcore.gamestate.PlayerTeam;
import ca.encodeous.mwx.mwxcore.utils.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class mwgiveCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try{
            Player p = null;
            boolean successMsg;
            int aidx = 0;
            if(args.length == 1){
                if(sender instanceof Player) p = (Player) sender;
                else{
                    sender.sendMessage("You are not a player...");
                    return true;
                }
                successMsg = false;
            }else{
                p = Bukkit.getPlayer(args[0]);
                if(p == null){
                    sender.sendMessage("Player not found!");
                    return true;
                }
                successMsg = true;
                aidx = 1;
            }
            if(args[aidx].equals("*")){
                if(CoreGame.GetMatch().IsPlayerInTeam(p, PlayerTeam.Red) || CoreGame.GetMatch().IsPlayerInTeam(p, PlayerTeam.Green)){
                    for(MissileWarsItem item : CoreGame.GetImpl().CreateDefaultItems()){
                        ItemStack ritem = CoreGame.GetImpl().CreateItem(item);
                        p.getInventory().addItem(ritem);
                    }
                    p.sendMessage(Formatter.FCL("&6You have been given all the items"));
                    if(successMsg){
                        sender.sendMessage("Success!");
                    }
                }else{
                    sender.sendMessage("Player must be on a team!");
                }
            }else{
                MissileWarsItem item = CoreGame.Instance.GetItemById(args[aidx]);
                if(item == null){
                    sender.sendMessage("Item id not found! See items by running /mwitems");
                    return true;
                }
                if(CoreGame.GetMatch().IsPlayerInTeam(p, PlayerTeam.Red) || CoreGame.GetMatch().IsPlayerInTeam(p, PlayerTeam.Green)){
                    ItemStack ritem = CoreGame.GetImpl().CreateItem(item);
                    p.getInventory().addItem(ritem);
                    p.sendMessage(Formatter.FCL("&6You have been given &c" + item.MissileWarsItemId + "&6!"));
                    if(successMsg){
                        sender.sendMessage("Success!");
                    }
                }else{
                    sender.sendMessage("Player must be on a team!");
                }
            }
            return true;
        }catch (Exception e){
            return false;
        }
    }
}
