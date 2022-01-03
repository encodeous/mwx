package ca.encodeous.mwx.command.commands;

import ca.encodeous.mwx.command.MissileWarsCommand;
import ca.encodeous.mwx.command.RootCommand;
import ca.encodeous.mwx.core.game.CoreGame;
import ca.encodeous.mwx.core.utils.Chat;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.apache.commons.lang.NotImplementedException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class mwreloadCommand extends MissileWarsCommand {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try{
            CoreGame.Instance.Reload();
            sender.sendMessage(Chat.FCL("Successfully reloaded game"));
            return true;
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public RootCommand BuildCommand() {
        throw new NotImplementedException("Building this command is not implemented");
    }

    @Override
    public String GetCommandName() {
        return "mwreload";
    }
}
