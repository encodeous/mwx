package ca.encodeous.mwx.core.game;

import ca.encodeous.mwx.core.lang.Strings;
import ca.encodeous.mwx.data.Bounds;
import ca.encodeous.mwx.core.utils.Utils;
import com.fastasyncworldedit.core.extent.clipboard.ReadOnlyClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class MissileWarsMap {
    /**
     * Should there be a red/green join, or just one single join
     */
    public boolean SeparateJoin;
    public HashSet<Vector> RedJoin;
    public HashSet<Vector> GreenJoin;
    /**
     * Join box for automatic team selection
     */
    public HashSet<Vector> AutoJoin;
    /**
     * Return to lobby signs
     */
    public HashSet<Vector> ReturnToLobby;
    /**
     * Spectate Signs
     */
    public HashSet<Vector> Spectate;
    /**
     * Set players to face a direction
     */
    public float SpawnYaw;

    /**
     * Set players to face a direction
     */
    public float GreenYaw;

    /**
     * Set players to face a direction
     */
    public float RedYaw;


    public Vector Spawn;
    public Vector RedSpawn;
    public Vector RedLobby;
    public Vector GreenSpawn;
    public Vector GreenLobby;

    public Bounds GreenPortal;
    public Bounds RedPortal;

    public Bounds WorldBoundingBox;
    public Bounds WorldMaxBoundingBox;

    // Per instance values
    public World MswWorld = null;
    public World TemplateWorld;
    public boolean isFilled = false;
    public boolean isBusy = false;
    private static final ConcurrentHashMap<World, Clipboard> cachedClipboards = new ConcurrentHashMap<>();
    public String WorldName = "";
    private Clipboard getClipboard(){
        if(!cachedClipboards.containsKey(TemplateWorld)){
            CuboidRegion srcRegion = WorldBoundingBox.toWorldeditRegion(TemplateWorld);
            Clipboard board;
            try (EditSession session = Utils.GetEditSession(TemplateWorld)){
                session.setFastMode(true);
                board = ReadOnlyClipboard.of(
                        session, srcRegion, false, false
                );
            }
            cachedClipboards.put(TemplateWorld, board);
        }
        return cachedClipboards.get(TemplateWorld);
    }
    public void CreateMap(Runnable finished){
        if(!isFilled && !isBusy){
            isBusy = true;
            if(MswWorld == null){
                MswWorld = CoreGame.GetImpl().FastVoidWorld(WorldName);
            }
            Bukkit.getScheduler().runTaskAsynchronously(CoreGame.Instance.mwPlugin, new Runnable() {
                @Override
                public void run() {
                    isFilled = true;
                    CuboidRegion destRegion = WorldBoundingBox.toWorldeditRegion(MswWorld);
                    Clipboard board = getClipboard();

                    try (EditSession session = Utils.GetEditSession(MswWorld)) {
                        session.setFastMode(true);
                        Operation operation = new ClipboardHolder(board)
                                .createPaste(session)
                                .ignoreAirBlocks(true)
                                .copyEntities(false)
                                .build();
                        Operations.complete(operation);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    Bukkit.getScheduler().runTask(CoreGame.Instance.mwPlugin, new Runnable() {
                        @Override
                        public void run() {
                            finished.run();
                        }
                    });
                    isBusy = false;
                }
            });
        }
    }
    public void CleanMap(Runnable finished){
        if(isBusy) return;
        isBusy = true;
        Bukkit.getScheduler().runTaskAsynchronously(CoreGame.Instance.mwPlugin, new Runnable() {
            @Override
            public void run() {
                CuboidRegion destFullRegion = WorldMaxBoundingBox.toWorldeditRegion(MswWorld);
                CuboidRegion destRegion = WorldBoundingBox.toWorldeditRegion(MswWorld);
                Clipboard board = getClipboard();
                try (EditSession session = Utils.GetEditSession(MswWorld)) {
                    session.setFastMode(true);
                    session.setBlocks((Region) destFullRegion, BukkitAdapter.asBlockType(Material.AIR));
                    Operation operation = new ClipboardHolder(board)
                            .createPaste(session)
                            .ignoreAirBlocks(true)
                            .copyEntities(false)
                            .build();
                    Operations.complete(operation);
                }
                Bukkit.getScheduler().runTask(CoreGame.Instance.mwPlugin, new Runnable() {
                    @Override
                    public void run() {
                        for(Entity e : MswWorld.getEntities()){
                            if(!(e instanceof Player)){
                                e.remove();
                            }
                        }
                        finished.run();
                    }
                });
                isFilled = false;
                isBusy = false;
            }
        });
    }

    public void Delete(){
        File worldFolder = MswWorld.getWorldFolder();
        for(Entity e : MswWorld.getEntities()){
            try{
                e.remove();
            }catch (Exception ex){

            }
        }
        for(Player p : MswWorld.getPlayers()){
            p.kickPlayer(Strings.KICK_RESET);
        }
        boolean firstTry = Bukkit.unloadWorld(MswWorld.getName(), false);
        boolean success = firstTry;
        if(!firstTry){
            for(Player p : MswWorld.getPlayers()){
                p.kickPlayer(Strings.KICK_RESET);
            }
            success = Bukkit.unloadWorld(MswWorld.getName(), false);
        }
        if(!success){
            System.out.println("Unable to unload world " + MswWorld.getName() + " deleting anyways...");
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
    }
}