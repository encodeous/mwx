package ca.encodeous.mwx.mwxcore;

import ca.encodeous.mwx.configuration.*;
import ca.encodeous.mwx.mwxcore.gamestate.MatchType;
import ca.encodeous.mwx.mwxcore.gamestate.MissileWarsMatch;
import ca.encodeous.mwx.mwxcore.lang.Strings;
import ca.encodeous.mwx.mwxcore.trace.TrackedBreakage;
import ca.encodeous.mwx.mwxcore.utils.*;
import ca.encodeous.mwx.mwxcore.world.MissileBlock;
import ca.encodeous.mwx.mwxcore.world.MissileSchematic;
import ca.encodeous.mwx.mwxcore.world.PistonData;
import ca.encodeous.mwx.mwxstats.StatisticManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import ca.encodeous.mwx.lobbyengine.LobbyEngine;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pl.kacperduras.protocoltab.manager.TabManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CoreGame {
    static {
        ConfigurationSerialization.registerClass(MissileWarsConfiguration.class, "missilewars-settings");
        ConfigurationSerialization.registerClass(MissileWarsItem.class, "item");
        ConfigurationSerialization.registerClass(Missile.class, "missile");
        ConfigurationSerialization.registerClass(MissileBlock.class, "mblock");
        ConfigurationSerialization.registerClass(MissileSchematic.class, "mschem");
        ConfigurationSerialization.registerClass(PistonData.class, "pdata");
        ConfigurationSerialization.registerClass(Bounds.class, "bounds");
        ConfigurationSerialization.registerClass(LobbyConfiguration.class, "lobbies");
        ConfigurationSerialization.registerClass(LobbyInfo.class, "lobby");
    }

    public static CoreGame Instance = null;

    public CoreGame(MissileWarsImplementation implementation, JavaPlugin plugin) {
        mwImpl = implementation;
        mwPlugin = plugin;
    }

    public static MissileWarsImplementation GetImpl() {
        return Instance.mwImpl;
    }

    public static StructureInterface GetStructureManager() {
        return Instance.mwImpl.GetStructureManager();
    }

    public static StatisticManager Stats = null;
    public ProtocolManager protocolManager;
    public JavaPlugin mwPlugin;

    // Missile Wars
    private MissileWarsImplementation mwImpl;
    public HashMap<String, Missile> mwMissiles = null;
    public TabManager tabManager = null;
    public World mwAuto = null, mwManual = null;

    // Configuration
    public MissileWarsConfiguration mwConfig;
    public LobbyConfiguration mwLobbies;
    FileConfiguration config = new YamlConfiguration();
    FileConfiguration lobbyConfig = new YamlConfiguration();
    File configFile = null;
    File lobbyFile = null;

    public void LoadConfig() {
        // load configuration
        File configDir = mwPlugin.getDataFolder();
        configFile = new File(configDir, "config.yml");
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            mwConfig = (MissileWarsConfiguration) config.get("data");
        } else {
            configFile.getParentFile().mkdirs();
            mwConfig = new MissileWarsConfiguration();
            mwConfig.Items = mwImpl.CreateDefaultItems();
            mwConfig.BreakSpeeds = new HashMap<>();
            mwConfig.BreakSpeeds.put(Material.PISTON.name(), 300);
            mwConfig.BreakSpeeds.put(Material.PISTON_HEAD.name(), 300);
            mwConfig.BreakSpeeds.put(Material.STICKY_PISTON.name(), 300);
            mwConfig.BreakSpeeds.put("PISTON_BASE", 300);
            mwConfig.BreakSpeeds.put("PISTON_EXTENSION", 300);
            mwConfig.BreakSpeeds.put("PISTON_STICKY_BASE", 300);
            mwConfig.BreakSpeeds.put(Material.REDSTONE_BLOCK.name(), 800);
            config.set("data", mwConfig);
            try {
                config.save(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // load lobby config
        lobbyFile = new File(configDir, "lobbies.yml");
        if (lobbyFile.exists()) {
            lobbyConfig = YamlConfiguration.loadConfiguration(lobbyFile);
            mwLobbies = (LobbyConfiguration) lobbyConfig.get("data");
        } else {
            lobbyFile.getParentFile().mkdirs();
            mwLobbies = new LobbyConfiguration();
            lobbyConfig.set("data", mwLobbies);
            try {
                lobbyConfig.save(lobbyFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void Reload() {
        Bukkit.unloadWorld("mwx_template_auto", true);
        Bukkit.unloadWorld("mwx_template_manual", true);
        LobbyEngine.Shutdown();
        protocolManager.removePacketListeners(mwPlugin);
        Stats.close();
        InitializeGame();
    }

    public void InitializeGame() {
        if (Instance == null) {
            Instance = this;
            mwImpl.RegisterEvents(mwPlugin);
        }
        if (protocolManager == null) {
            protocolManager = ProtocolLibrary.getProtocolManager();
        }
        LoadConfig();
        Stats = new StatisticManager("mwxstats", mwPlugin);
        // load worlds
        mwPlugin.getLogger().info("Loading template worlds...");
        try {
            ResourceLoader.LoadWorldFiles(mwPlugin);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mwAuto = new WorldCreator("mwx_template_auto").environment(World.Environment.NORMAL).createWorld();
        mwManual = new WorldCreator("mwx_template_manual").environment(World.Environment.NORMAL).createWorld();

        mwAuto.setAutoSave(true);
        mwManual.setAutoSave(true);

        if (!new File(mwPlugin.getDataFolder(), "missiles").exists()) {
            mwPlugin.getLogger().info("Loading default missiles...");
            try {
                ResourceLoader.UnzipMissiles(mwPlugin);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mwPlugin.getLogger().info("Configuring scoreboards...");
        mwImpl.ConfigureScoreboards();
        mwPlugin.getLogger().info("Loading missiles...");
        mwMissiles = ResourceLoader.LoadMissiles(mwPlugin);
        mwPlugin.getLogger().info("MissileWarsX fully loaded!");

        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.runTask(mwPlugin, () -> {
            mwPlugin.getLogger().info("Creating lobbies...");
            for (LobbyInfo info : mwLobbies.Lobbies) {
                LobbyEngine.CreateLobby(info.MaxTeamSize, info.AutoJoin, info.LobbyType);
            }
        });
        scheduler.runTaskTimerAsynchronously(mwPlugin, TPSMon.Instance, 0, 20);
        scheduler.runTaskTimer(mwPlugin, RealTPS.Instance, 0, 20);
        tabManager = new TabManager(mwPlugin);

        protocolManager.addPacketListener(
                new PacketAdapter(mwPlugin, ListenerPriority.NORMAL,
                        PacketType.Play.Server.CHAT) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        if (event.getPacketType() ==
                                PacketType.Play.Server.CHAT) {
                            try {
                                if (event.getPacket().getChatComponents().getValues().size() == 0 ||
                                        event.getPacket().getChatComponents().getValues().get(0) == null
                                ) return;
                                JSONObject obj = new JSONObject(
                                        event.getPacket().getChatComponents().getValues().get(0).getJson());
                                if (obj.has("translate") && obj.has("with") && obj.getString("translate").startsWith("death")) {
                                    // dealing with death event
                                    JSONArray withArr = obj.getJSONArray("with");
                                    ArrayList<Player> players = new ArrayList<>();
                                    for (int i = 0; i < withArr.length(); i++) {
                                        players.add(Bukkit.getPlayer(withArr.getJSONObject(i).getString("insertion")));
                                    }
                                    HashSet<Player> allPlayers = new HashSet<>();
                                    for (Player p : players) {
                                        MissileWarsMatch match = LobbyEngine.FromWorld(p.getWorld());
                                        if (match != null) {
                                            allPlayers.addAll(match.Teams.keySet());
                                        }
                                    }
                                    if (!allPlayers.contains(event.getPlayer())) {
                                        event.setCancelled(true);
                                    }
                                }
                            } catch (JSONException e) {
                                // ignored
                            }
                        }
                    }
                }
        );
        if (mwConfig.AllowFastBreak) {
            protocolManager.addPacketListener(
                    new PacketAdapter(mwPlugin, ListenerPriority.NORMAL,
                            PacketType.Play.Client.BLOCK_DIG) {
                        @Override
                        public void onPacketReceiving(PacketEvent event) {
                            PacketContainer packet = event.getPacket();
                            Player p = event.getPlayer();
                            EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().getValues().get(0);
                            if (p.getGameMode() == GameMode.CREATIVE) return;
                            MissileWarsMatch match = LobbyEngine.FromPlayer(p);
                            if (match == null) return;
                            BlockPosition blockPosition = packet.getBlockPositionModifier().getValues().get(0);
                            double distanceX = blockPosition.getX() - p.getLocation().getX();
                            double distanceY = blockPosition.getY() - p.getLocation().getY();
                            double distanceZ = blockPosition.getZ() - p.getLocation().getZ();
                            if (distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ >= 1024.0D)
                                return;
                            Block b = p.getWorld().getBlockAt(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
                            if(!mwConfig.BreakSpeeds.containsKey(b.getType().name())) return;
                            if (digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
                                TrackedBreakage brk = match.Tracer.AddBreak(b, mwConfig.BreakSpeeds.get(b.getType().name()));
                                brk.startBreak(p);
                            } else if (digType == EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK) {
                                TrackedBreakage brk = match.Tracer.GetBreak(b);
                                if(brk != null){
                                    brk.cancelBreak();
                                }
                            }
                        }
                    }
            );
        }
    }

//    @EventHandler
//    public void onBlockDamage(BlockDamageEvent event) {
//        Player p = event.getPlayer();
//        if(p.getGameMode() == GameMode.CREATIVE) return;
//        if(event.getInstaBreak()) return;
//        MissileWarsMatch match = LobbyEngine.FromPlayer(p);
//        if(match != null){
//            match.Tracer.AddBreak(event.getBlock());
//        }
//    }

//    private Set<Material> transparentBlocks = new HashSet<>();
//    @EventHandler
//    public void onPlayerAnimation(PlayerAnimationEvent event) {
//        Player p = event.getPlayer();
//        if(p.getGameMode() == GameMode.CREATIVE) return;
//        MissileWarsMatch match = LobbyEngine.FromPlayer(p);
//        if(match == null){
//            return;
//        }
//        if(transparentBlocks.isEmpty()){
//            transparentBlocks.add(Material.WATER);
//            transparentBlocks.add(Material.AIR);
//            try{
//                transparentBlocks.add(Material.STATIONARY_WATER);
//            }catch (NoSuchFieldError e){
//                // ignored
//            }
//        }
//        Block block = p.getTargetBlock(transparentBlocks, 5);
//        Location blockPosition = block.getLocation();
//        TrackedBreakage bblk = match.Tracer.GetBreak(block);
//
//        if (bblk == null) return;
//
//        double distanceX = blockPosition.getX() - p.getLocation().getX();
//        double distanceY = blockPosition.getY() - p.getLocation().getY();
//        double distanceZ = blockPosition.getZ() - p.getLocation().getZ();
//
//        if (distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ >= 1024.0D) return;
//        bblk.incrementDamage(10);
//    }

    public MissileWarsItem GetItemById(String id) {
        for (MissileWarsItem i : mwConfig.Items) {
            if (i.MissileWarsItemId.equals(id)) return i;
        }
        return null;
    }

    public void StopGame(boolean save) {
        LobbyEngine.Shutdown();
        Stats.close();
        if (save) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                lobbyConfig.save(lobbyFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            ResourceLoader.SaveMissiles(mwPlugin, mwMissiles);
        }
    }

    public void PlayerJoined(Player p) {
        p.sendMessage(Strings.JOIN_MESSAGE);
        Stats.CreatePlayer(p);
        LobbyEngine.GetLobby(1).AddPlayer(p);
    }

    public void PlayerLeft(Player p) {
        MissileWarsMatch match = LobbyEngine.FromPlayer(p);
        if (match != null) {
            match.lobby.RemovePlayer(p);
        }
    }
}
