package ca.encodeous.mwx.mwxcompat1_13;

import ca.encodeous.mwx.mwxcore.CoreGame;
import ca.encodeous.mwx.mwxcore.MissileWarsEvents;
import ca.encodeous.mwx.mwxcore.missiletrace.TraceEngine;
import ca.encodeous.mwx.mwxcore.utils.Ref;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Fire;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static ca.encodeous.mwx.mwxcore.missiletrace.TraceEngine.PropagatePortalBreak;

public class MissileWarsEventHandler extends ca.encodeous.mwx.mwxcompat1_8.MissileWarsEventHandler {
    private MissileWarsEvents mwEvents;

    public MissileWarsEventHandler(MissileWarsEvents events) {
        super(events);
        mwEvents = events;
    }

    @EventHandler
    public void PlayerInteractEvent(PlayerInteractEvent e){
        Ref<Boolean> cancel = new Ref<>(false);
        Ref<Boolean> use = new Ref<>(false);
        mwEvents.PlayerInteractEvent(e.getPlayer(), e.getAction(), e.getBlockFace(), e.getClickedBlock(), e.getItem(), cancel, use);
        if(use.val){
            if(e.getHand() == EquipmentSlot.HAND){
                ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
                if(item.getAmount() == 1){
                    item.setType(Material.AIR);
                }else{
                    item.setAmount(item.getAmount() - 1);
                }
                e.getPlayer().getInventory().setItemInMainHand(item);
            }
            else{
                ItemStack item = e.getPlayer().getInventory().getItemInOffHand();
                if(item.getAmount() == 1){
                    item.setType(Material.AIR);
                }else{
                    item.setAmount(item.getAmount() - 1);
                }
                e.getPlayer().getInventory().setItemInOffHand(item);
            }
        }
        if(cancel.val){
            e.setCancelled(true);
        }
        Player p = (Player) e.getPlayer().getInventory().getHolder();
        ReequipGunblade(p);
    }
    @EventHandler
    public void PlayerCombatEvent(EntityDamageByEntityEvent event){
        if(event.getEntity() instanceof Player){
            Player p = ((Player) event.getEntity()).getPlayer();
            if(event.getDamager() instanceof Arrow){
                Arrow arrow = (Arrow)event.getDamager();
                if(arrow.getShooter() instanceof Player){
                    Player p2 = (Player) arrow.getShooter();
                    if(CoreGame.Instance.mwMatch.Teams.get(p) == CoreGame.Instance.mwMatch.Teams.get(p2)){
                        event.setCancelled(true);
                    }
                }
            } else if(event.getDamager() instanceof Fireball){
                Fireball fb = (Fireball)event.getDamager();
                ProjectileSource source = TraceEngine.ResolveShooter(fb);
                if(source instanceof Player){
                    Player p2 = (Player) source;
                    if(CoreGame.Instance.mwMatch.Teams.get(p) == CoreGame.Instance.mwMatch.Teams.get(p2)){
                        event.setCancelled(true);
                    }
                }
            } else if(event.getDamager() instanceof TNTPrimed){
                TNTPrimed tnt = (TNTPrimed)event.getDamager();
                if(CoreGame.Instance.mwMatch.Tracer.IsRedstoneActivated(tnt)) return;
                UUID id = CoreGame.Instance.mwMatch.Tracer.FindRootCause(tnt);
                if(id != null){
                    Player p2 = Bukkit.getPlayer(id);
                    if(p2 != null){
                        if(CoreGame.Instance.mwMatch.Teams.get(p) == CoreGame.Instance.mwMatch.Teams.get(p2)){
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }

    }
    @EventHandler
    public void ExplodeEvent(EntityExplodeEvent e){
        if(e.getEntity() instanceof Fireball){
            e.blockList().removeIf(block ->
                    block.getType() != Material.TNT
                            && block.getType() != Material.SLIME_BLOCK
                            && block.getType() != Material.PISTON
                            && block.getType() != Material.PISTON_HEAD
                            && !block.getType().toString().contains("TERRACOTTA")
                            && block.getType() != Material.REDSTONE_BLOCK);
        }
        if(e.getEntity() instanceof TNTPrimed){
            Optional<Block> block = e.blockList().stream().filter(x->x.getType() == CoreGame.Instance.mwImpl.GetPortalMaterial()).findAny();
            block.ifPresent(value -> {
                mwEvents.PortalChangedEvent(value, (TNTPrimed) e.getEntity());
                PropagatePortalBreak(value);
            });
        }
    }
}