package ca.encodeous.mwx.mwxcore.gamestate;

import ca.encodeous.mwx.configuration.BalanceStrategy;
import ca.encodeous.mwx.configuration.Missile;
import ca.encodeous.mwx.configuration.MissileWarsCoreItem;
import ca.encodeous.mwx.configuration.MissileWarsItem;
import ca.encodeous.mwx.mwxcore.CoreGame;
import ca.encodeous.mwx.mwxcore.missiletrace.TraceEngine;
import ca.encodeous.mwx.mwxcore.missiletrace.TrackedBlock;
import ca.encodeous.mwx.mwxcore.utils.Bounds;
import ca.encodeous.mwx.mwxcore.utils.Formatter;
import ca.encodeous.mwx.mwxcore.utils.Ref;
import ca.encodeous.mwx.mwxcore.utils.Utils;
import ca.encodeous.mwx.soundengine.SoundType;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.hamcrest.core.Is;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class MissileWarsMatch {
    // Map info
    public MissileWarsMap Map;
    // Teams
    public Team mwGreen, mwRed, mwSpectate, mwLobby;
    // Map State
    public HashSet<Player> Lobby;
    public HashSet<Player> Spectators;
    public HashSet<Player> Green;
    public HashSet<Player> Red;
    public HashMap<Player, PlayerTeam> Teams;
    public boolean isStarting;
    public boolean hasStarted;
    public boolean isEnding;
    public boolean isActivated;
    public int mwCnt;
    public int CountTaskId = -1;
    public int ItemTaskId = -1;
    public Scoreboard mwScoreboard;
    public TraceEngine Tracer;
    Random mwRand = new Random();

    public void Initialize(){
        isStarting = false;
        isActivated = true;
        hasStarted = false;
        Green = new HashSet<>();
        Lobby = new HashSet<>();
        Red = new HashSet<>();
        Spectators = new HashSet<>();
        Teams = new HashMap<>();
        Tracer = new TraceEngine();
        CoreGame.GetImpl().ConfigureScoreboards(this);
    }

    public void RedWin(ArrayList<Player> credits){
        if(!credits.isEmpty()){
            Bukkit.broadcastMessage(Formatter.FCL("&fThe &aGreen &fteam's portal was blown up by " + FormatCredits(credits) + "&r!"));
        }else{
            Bukkit.broadcastMessage(Formatter.FCL("&fThe &aGreen &fteam's portal was blown up!"));
        }
        Bukkit.broadcastMessage(Formatter.FCL("&6Congratulations &cRed &6team!"));
        for(Player p : Red){
            CoreGame.GetImpl().PlaySound(p, SoundType.WIN);
        }
        for(Player p : Teams.keySet()){
            CoreGame.GetImpl().SendTitle(p, "&6The &cred &6team has won!", "&6Congratulations!");
        }
        EndGame();
    }
    public void GreenWin(ArrayList<Player> credits){
        if(!credits.isEmpty()){
            Bukkit.broadcastMessage(Formatter.FCL("&fThe &cRed &fteam's portal was blown up by " + FormatCredits(credits) + "&r!"));
        }else{
            Bukkit.broadcastMessage(Formatter.FCL("&fThe &cRed &fteam's portal was blown up!"));
        }
        Bukkit.broadcastMessage(Formatter.FCL("&6Congratulations &aGreen &6team!"));
        for(Player p : Green){
            CoreGame.GetImpl().PlaySound(p, SoundType.WIN);
        }
        for(Player p : Teams.keySet()){
            CoreGame.GetImpl().SendTitle(p, "&6The &agreen &6team has won!", "&6Congratulations!");
        }
        EndGame();
    }

    public String FormatCredits(ArrayList<Player> credits){
        StringBuilder winString = new StringBuilder();
        if(credits.size() >= 2){
            for(int i = 0; i < credits.size() - 1; i++){
                if(i != 0) winString.append("&r, ");
                winString.append(credits.get(i).getDisplayName());
            }
            winString.append("&r and ");
            winString.append(credits.get(credits.size() - 1).getDisplayName());
        }else{
            winString.append(credits.get(0).getDisplayName());
        }
        return winString.toString();
    }


    public void EndGame(){
        if(hasStarted){
            this.hasStarted = false;
            this.isEnding = true;
            Bukkit.getScheduler().cancelTask(ItemTaskId);
            for(Player p : Teams.keySet()){
                CoreGame.GetImpl().PlaySound(p, SoundType.GAME_END);
                p.setGameMode(GameMode.SPECTATOR);
            }
            CoreGame.Instance.PrepareEndMatch();
            CoreGame.Instance.EndGameCountdown();
        }
    }

    public boolean CheckCanSpawn(PlayerTeam team, ArrayList<Vector> blocks, World world, boolean isShield){
        // referenced from OpenMissileWars
        int threshold = 0;
        Bounds bound = new Bounds();
        for(Vector vec : blocks){
            bound.stretch(vec);
        }
        for(int i = bound.getMinX(); i <= bound.getMaxX(); i++){
            for(int j = bound.getMinY(); j <= bound.getMaxY(); j++){
                for(int k = bound.getMinZ(); k <= bound.getMaxZ(); k++){
                    Vector vec = new Vector(i, j, k);
                    Block block = world.getBlockAt(vec.getBlockX(), vec.getBlockY(), vec.getBlockZ());
                    Material mat = block.getType();
                    if(mat == Material.OBSIDIAN || mat == Material.BEDROCK
                            || mat == CoreGame.GetImpl().GetPortalMaterial()
                            || mat == Material.BARRIER) return false;
                    if(IsInProtectedRegion(vec)) return false;
                    if(isShield) continue;
                    boolean crossMid;
                    if (team == PlayerTeam.Red) {
                        crossMid = vec.getBlockZ() >= 0;
                    } else {
                        crossMid = vec.getBlockZ() <= 0;
                    }

                    boolean isSameTeamBlock = CoreGame.GetImpl().GetStructureManager().IsBlockOfTeam(team, block);
                    if(!isSameTeamBlock && crossMid){
                        threshold--;
                    }
                    if(isSameTeamBlock){
                        threshold++;
                    }
                    if(CoreGame.GetImpl().GetStructureManager().IsBlockOfTeam(PlayerTeam.None, block) && !crossMid){
                        threshold++;
                    }
                }
            }
        }
        return threshold < 5;
    }

    public boolean IsInProtectedRegion(Vector vec){
        if(vec.getBlockX() <= -72) return true;
        if(vec.getBlockY() <= 0) return true;
        if(vec.getBlockZ() >= 120) return true;
        if(vec.getBlockZ() <= -120) return true;
        if(vec.getBlockY() >= 150) return true;
        return false;
    }

    public void GreenPad(Player p){
        if(!Map.SeparateJoin) return;
        RemovePlayer(p);
        AddPlayerToTeam(p, PlayerTeam.Green);
    }
    public void RedPad(Player p){
        if(!Map.SeparateJoin) return;
        RemovePlayer(p);
        AddPlayerToTeam(p, PlayerTeam.Red);
    }
    public void AutoPad(Player p){
        if(Map.SeparateJoin) return;
        RemovePlayer(p);
        PlayerTeam team = null;
        if(CoreGame.Instance.mwConfig.Strategy == BalanceStrategy.BALANCED_FIXED){
            if(Red.size() == Green.size()){
                if(p.getName().hashCode() % 2 == 0) team = PlayerTeam.Red;
                else team = PlayerTeam.Green;
            }else{
                if(Red.size() < Green.size()) team = PlayerTeam.Red;
                else team = PlayerTeam.Green;
            }
        }else if(CoreGame.Instance.mwConfig.Strategy == BalanceStrategy.BALANCED_GREEN){
            if(Red.size() == Green.size()){
                team = PlayerTeam.Green;
            }else{
                if(Red.size() < Green.size()) team = PlayerTeam.Red;
                else team = PlayerTeam.Green;
            }
        }else if(CoreGame.Instance.mwConfig.Strategy == BalanceStrategy.BALANCED_RANDOM){
            if(Red.size() == Green.size()){
                if(mwRand.nextInt() % 2 == 0) team = PlayerTeam.Red;
                else team = PlayerTeam.Green;
            }else{
                if(Red.size() < Green.size()) team = PlayerTeam.Red;
                else team = PlayerTeam.Green;
            }
        }
        AddPlayerToTeam(p, team);
    }
    public void GiveItems(){
        ItemTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(CoreGame.Instance.mwPlugin, new Runnable() {
            @Override
            public void run() {
                if(mwCnt <= 0){
                    while(true){
                        int item = mwRand.nextInt(CoreGame.Instance.mwConfig.Items.size());
                        mwCnt = CoreGame.Instance.mwConfig.ResupplySeconds;
                        MissileWarsItem mwItem = CoreGame.Instance.mwConfig.Items.get(item);
                        if(mwItem.IsExempt) continue;
                        for(Player p : Green){
                            GivePlayerItem(p, CoreGame.Instance.mwConfig.Items.get(item));
                        }
                        for(Player p : Red){
                            GivePlayerItem(p, CoreGame.Instance.mwConfig.Items.get(item));
                        }
                        break;
                    }
                }
                for(Player p : Green){
                    p.setLevel(mwCnt);
                }
                for(Player p : Red){
                    p.setLevel(mwCnt);
                }
                mwCnt--;
            }
        }, 0, 20);
    }

    public int CountItem(Player p, MissileWarsItem item){
        int curCnt = 0;
        if(CoreGame.GetImpl().GetItemId(p.getItemOnCursor()).equals(item.MissileWarsItemId)){
            curCnt += p.getItemOnCursor().getAmount();
        }
        for(ItemStack i : p.getOpenInventory().getBottomInventory()){
            String id = CoreGame.GetImpl().GetItemId(i);
            if(id.equals(item.MissileWarsItemId)){
                curCnt += i.getAmount();
            }
        }
        for(ItemStack i : p.getOpenInventory().getTopInventory()){
            String id = CoreGame.GetImpl().GetItemId(i);
            if(id.equals(item.MissileWarsItemId)){
                curCnt += i.getAmount();
            }
        }
        return curCnt;
    }

    public void GivePlayerItem(Player p, MissileWarsItem item){
        int curCnt = CountItem(p, item);
        if(item.MaxStackSize > curCnt){
            ItemStack citem = CoreGame.GetImpl().CreateItem(item);
            citem.setAmount(Math.min(item.StackSize, item.MaxStackSize - curCnt));
            if(!p.getInventory().addItem(citem).isEmpty()){
                CoreGame.GetImpl().PlaySound(p, SoundType.ITEM_NOT_GIVEN);
                CoreGame.GetImpl().SendActionBar(p, "&cYour inventory does not have enough space to receive any items.");
            }else{
                CoreGame.GetImpl().PlaySound(p, SoundType.ITEM_GIVEN);
            }
        }else{
            CoreGame.GetImpl().PlaySound(p, SoundType.ITEM_NOT_GIVEN);
            CoreGame.GetImpl().SendActionBar(p, "&6You already have a &f"+item.MissileWarsItemId + "&6.");
        }
    }

    public void CountdownGame(){
        if(CountTaskId != -1) return;
        mwCnt = 15;
        CountTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(CoreGame.Instance.mwPlugin, new Runnable() {
            @Override
            public void run() {
                if(!isStarting || hasStarted){
                    Bukkit.getScheduler().cancelTask(CountTaskId);
                    CountTaskId = -1;
                    return;
                }
                if (mwCnt == 60 || mwCnt == 45 || mwCnt == 30 || mwCnt == 15 || mwCnt == 10 || mwCnt == 5 || mwCnt == 4
                        || mwCnt == 3 || mwCnt == 2) {
                    Bukkit.broadcastMessage(Formatter.FCL("&aStarting game in &6" + mwCnt + "&a seconds!"));
                } else if (mwCnt == 1) {
                    Bukkit.broadcastMessage(Formatter.FCL("&aStarting game in &6" + mwCnt + "&a seconds!"));
                } else if (mwCnt == 0) {
                    Bukkit.broadcastMessage(Formatter.FCL("&cStarting now!"));
                    hasStarted = true;
                    isStarting = false;
                    for(Player p : Teams.keySet()){
                        p.setLevel(mwCnt);
                    }
                    GameStarted();
                    Bukkit.getScheduler().cancelTask(CountTaskId);
                }
                for(Player p : Teams.keySet()){
                    CoreGame.GetImpl().PlaySound(p, SoundType.COUNTDOWN);
                    p.setLevel(mwCnt);
                    p.setExp(0);
                }
                mwCnt--;
            }
        }, 0, 20);
    }

    public void GameStarted(){
        for(Player p : Green){
            TeleportPlayer(p, PlayerTeam.Green);
            p.setGameMode(GameMode.SURVIVAL);
        }
        for(Player p : Red){
            TeleportPlayer(p, PlayerTeam.Red);
            p.setGameMode(GameMode.SURVIVAL);
        }
        GiveItems();
    }

    public void CheckGameReadyState(){
        if(hasStarted) return;
        if(Red.size() != 0 && Green.size() != 0){
            isStarting = true;
            CountdownGame();
        }else{
            if(Red.size() + Green.size() == 1)
                Bukkit.broadcastMessage(Formatter.FCL("&9The game needs at least 1 player in each team to start. To forcefully start a game, run &6/start&9."));
            isStarting = false;
            for(Player p : Teams.keySet()){
                p.setLevel(0);
            }
        }
    }

    public Location GetTeamSpawn(PlayerTeam team){
        if(hasStarted){
            if(team == PlayerTeam.Green){
                Location loc = Utils.LocationFromVec(Map.GreenSpawn, Map.MswWorld);
                loc.setYaw(Map.GreenYaw);
                loc.setPitch(0);
                return loc;
            }else if(team == PlayerTeam.Red){
                Location loc = Utils.LocationFromVec(Map.RedSpawn, Map.MswWorld);
                loc.setYaw(Map.RedYaw);
                loc.setPitch(0);
                return loc;
            }else{
                Location loc = Utils.LocationFromVec(Map.Spawn, Map.MswWorld);
                loc.setYaw(Map.SpawnYaw);
                loc.setPitch(0);
                return loc;
            }
        }else{
            Location loc = null;
            if(team == PlayerTeam.Green){
                loc = Utils.LocationFromVec(Map.GreenLobby, Map.MswWorld);
            }else if(team == PlayerTeam.Red){
                loc = Utils.LocationFromVec(Map.RedLobby, Map.MswWorld);
            }else{
                loc = Utils.LocationFromVec(Map.Spawn, Map.MswWorld);
            }
            loc.setYaw(Map.SpawnYaw);
            loc.setPitch(0);
            return loc;
        }
    }

    public boolean IsPlayerInTeam(Player p, PlayerTeam team){
        if(Teams.containsKey(p)){
            return Teams.get(p) == team;
        }
        return false;
    }

    public void AddPlayerToTeam(Player p, PlayerTeam team){
        if(IsPlayerInTeam(p, team)) return;
        RemovePlayer(p);
        if(team == PlayerTeam.Green || team == PlayerTeam.Red){
            CoreGame.GetImpl().EquipPlayer(p, team == PlayerTeam.Red);
            if(hasStarted) p.setGameMode(GameMode.SURVIVAL);
            else p.setGameMode(GameMode.ADVENTURE);
        }
        Teams.put(p, team);
        SetPlayerDisplayName(team, p);
        if(team == PlayerTeam.Red){
            mwRed.addEntry(p.getName());
            Red.add(p);
            TeamColorBroadcast(p, p.getName() + " has joined the red team!");
        }else if(team == PlayerTeam.Green){
            mwGreen.addEntry(p.getName());
            Green.add(p);
            TeamColorBroadcast(p, p.getName() + " has joined the green team!");
        }else if(team == PlayerTeam.None){
            mwLobby.addEntry(p.getName());
            Lobby.add(p);
            Bukkit.broadcastMessage(Formatter.FCL("&7"+p.getDisplayName()+" has joined the lobby!"));
            p.setGameMode(GameMode.ADVENTURE);
        }else{
            mwSpectate.addEntry(p.getName());
            Spectators.add(p);
            TeamColorBroadcast(p, p.getName() + " is now spectating!");
            p.sendMessage(Formatter.FCL("&9You are now spectating. Type &6/lobby&9 to return to the lobby."));
            p.setGameMode(GameMode.SPECTATOR);
        }
        CheckGameReadyState();
        if(team != PlayerTeam.Spectator) TeleportPlayer(p, team);
    }

    public void TeamColorBroadcast(Player p, String message){
        if(IsPlayerInTeam(p, PlayerTeam.Green)){
            Bukkit.broadcastMessage(Formatter.FCL("&a" + message));
        }else if(IsPlayerInTeam(p, PlayerTeam.Red)){
            Bukkit.broadcastMessage(Formatter.FCL("&c" + message));
        }else if(IsPlayerInTeam(p, PlayerTeam.Spectator)){
            Bukkit.broadcastMessage(Formatter.FCL("&9" + message));
        }else{
            Bukkit.broadcastMessage(Formatter.FCL("&7" + message));
        }
    }

    public void TeleportPlayer(Player p, PlayerTeam team){
        if((team == PlayerTeam.Green || team == PlayerTeam.Red) && hasStarted){
            CoreGame.GetImpl().PlaySound(p, SoundType.START);
            p.sendMessage(Formatter.FCL("&cYou have entered the game, type &6/lobby &cto return to missile wars lobby."));
        }else{
            CoreGame.GetImpl().PlaySound(p, SoundType.TELEPORT);
        }
        Location loc = GetTeamSpawn(team);
        p.setBedSpawnLocation(loc, true);
        p.teleport(loc);
    }

    public void SetPlayerDisplayName(PlayerTeam team, Player p){
        p.setDisplayName(Formatter.ResolveTeamColor(team) + Formatter.FCL(p.getName() + "&r"));
    }

    public void CleanPlayer(Player p){
        for(PotionEffect effects : p.getActivePotionEffects()){
            p.removePotionEffect(effects.getType());
        }
        p.setExp(0);
        p.setLevel(0);
        p.setFireTicks(0);
        p.setFoodLevel(20);
        p.getInventory().clear();
        p.setHealth(20);
    }

    public void RemovePlayer(Player p){
        CleanPlayer(p);
        boolean affectGame = false;
        if(Green.contains(p) || Red.contains(p)) affectGame = true;
        Teams.remove(p);
        Lobby.remove(p);
        Spectators.remove(p);
        Green.remove(p);
        Red.remove(p);
        mwRed.removeEntry(p.getName());
        mwGreen.removeEntry(p.getName());
        mwSpectate.removeEntry(p.getName());
        mwLobby.removeEntry(p.getName());
        if(affectGame) CheckGameReadyState();
    }

    public static void SendCannotPlaceMessage(Player p){
        p.sendMessage(Formatter.FCL("&cYou cannot deploy that there."));
    }

    public void MissileWarsItemInteract(Player p, Action action, BlockFace face, Block target, String mwItemId, ItemStack item, boolean isInAir, Ref<Boolean> cancel, Ref<Boolean> use){
        if(hasStarted){
            if(IsPlayerInTeam(p, PlayerTeam.Red) || IsPlayerInTeam(p, PlayerTeam.Green)){
                if(CoreGame.Instance.mwMissiles.containsKey(mwItemId) && !isInAir){
                    Missile ms = CoreGame.Instance.mwMissiles.get(mwItemId);
                    boolean result = CoreGame.GetImpl().GetStructureManager().PlaceMissile(ms, target.getLocation().toVector(),
                            target.getWorld(), IsPlayerInTeam(p, PlayerTeam.Red), true, p);
                    if(result){
                        use.val = true;
                    }else{
                        use.val = false;
                        SendCannotPlaceMessage(p);
                    }
                    cancel.val = true;
                }else{
                    LaunchShield(p, mwItemId, cancel, use, IsPlayerInTeam(p, PlayerTeam.Red));
                    if(mwItemId.equals(MissileWarsCoreItem.FIREBALL.getValue())) DeployFireball(target, cancel, use, p);
                }
            }
        }
    }

    public HashSet<UUID> AliveSnowballs = new HashSet<>();
    private void DeployFireball(Block clickedBlock, Ref<Boolean> cancel, Ref<Boolean> use, Player p){
        if(clickedBlock == null) return;
        CoreGame.GetImpl().PlaySound(clickedBlock.getLocation().add(new Vector(0.5, 2, 0.5)), SoundType.FIREBALL);
        Vector loc = clickedBlock.getLocation().toVector().add(new Vector(0.5, 2, 0.5));
        CoreGame.GetImpl().SummonFrozenFireball(loc, clickedBlock.getWorld(), p);
        cancel.val = true;
        use.val = true;
    }
    private void LaunchShield(Player p, String mwItemId, Ref<Boolean> cancel, Ref<Boolean> use, boolean isRed) {
        if(mwItemId.equals(MissileWarsCoreItem.SHIELD.getValue())){
            final Snowball shield = p.launchProjectile(Snowball.class);
            AliveSnowballs.add(shield.getUniqueId());
            Bukkit.getScheduler().scheduleSyncDelayedTask(CoreGame.Instance.mwPlugin, new Runnable() {
                public void run() {
                    if(CoreGame.Instance.mwConfig.AllowShieldHit || AliveSnowballs.contains(shield.getUniqueId())){
                        boolean result = CoreGame.GetImpl().GetStructureManager().SpawnShield(shield.getLocation().toVector(), shield.getWorld(), isRed);
                        if(!result){
                            CoreGame.GetImpl().PlaySound(p, SoundType.ITEM_NOT_GIVEN);
                            MissileWarsMatch.SendCannotPlaceMessage(p);
                        }else{
                            CoreGame.GetImpl().PlaySound(shield.getLocation(), SoundType.SHIELD);
                        }
                        AliveSnowballs.remove(shield.getUniqueId());
                        shield.remove();
                    }
                }
            }, 20L);
            cancel.val = true;
            use.val = true;
        }
    }

    /**
     * Cleanup the world, and delete the map
     */
    public void Dispose(){
        if(isActivated){
            isActivated = false;
            if(ItemTaskId != -1){
                Bukkit.getScheduler().cancelTask(ItemTaskId);
                ItemTaskId = -1;
            }
            if(CountTaskId != -1){
                Bukkit.getScheduler().cancelTask(CountTaskId);
                CountTaskId = -1;
            }
            File worldFolder = Map.MswWorld.getWorldFolder();
            for(Entity e : Map.MswWorld.getEntities()){
                try{
                    e.remove();
                }catch (Exception ex){

                }
            }
            for(Player p : Map.MswWorld.getPlayers()){
                p.kickPlayer("Resetting Map");
            }
            boolean firstTry = Bukkit.unloadWorld(Map.MswWorld.getName(), false);
            boolean success = firstTry;
            if(!firstTry){
                for(Player p : Map.MswWorld.getPlayers()){
                    p.kickPlayer("Resetting Map");
                }
                success = Bukkit.unloadWorld(Map.MswWorld.getName(), false);
            }
            if(!success){
                System.out.println("Unable to unload world " + Map.MswWorld.getName() + " deleting anyways...");
            }
            try {
                FileUtils.deleteDirectory(worldFolder);
            } catch (IOException e) {
                try {
                    FileUtils.forceDeleteOnExit(worldFolder);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
            Map = null;
        }
    }
}
