package com.ericdebouwer.petdragon.listeners;

import com.ericdebouwer.petdragon.PetDragon;
import com.ericdebouwer.petdragon.config.Message;
import com.ericdebouwer.petdragon.enderdragonNMS.PetEnderDragon;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

@RequiredArgsConstructor
public class EggListener implements Listener {
	
	private final PetDragon plugin;

	@EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
	public void eggPlace(BlockPlaceEvent e){
		if (!e.getItemInHand().isSimilar(plugin.getCustomItems().getEgg())) return;

		if (!e.getPlayer().hasPermission("petdragon.spawnegg")) {
			plugin.getConfigManager().sendMessage(e.getPlayer(), Message.NO_EGG, null);
			e.setCancelled(true);
			return;
		}

		//Jeppa: new version of egg place: event is ALWAYS canceled, check is delayed, egg gets removed from hand an given back if no spawn is possible!
		ItemStack dragEgg=e.getItemInHand().clone(); 			//save the egg
		dragEgg.setAmount(1); 									//only ONE egg...
		fixItemInHand(e); 										//remove the egg
		e.setCancelled(true); 									//stop the event
		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
			e.getBlock().setType(Material.DRAGON_EGG,false); 	//place an egg (fake the event result), block is protectet by interact event...for 0.5 seconds..
			e.getBlock().setMetadata("isPetEgg", new FixedMetadataValue(plugin,true));//Set a marker for the 'dontTouch'-event :)
		});

		UUID uuid = e.getPlayer().getUniqueId();
		plugin.getDragonLocations().getLocationList(uuid); //returns a list with valid Locations for this player (not used yet) and activates the chunks!
		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
			if (plugin.getConfigManager().isEggAbidesDragonMax() && !e.getPlayer().hasPermission("petdragon.bypass.dragonlimit")) {
				int dragonCount = plugin.getFactory().getDragons(e.getPlayer()).size();
				if (dragonCount >= plugin.getConfigManager().getMaxDragons()){
					plugin.getConfigManager().sendMessage(e.getPlayer(), Message.DRAGON_LIMIT, ImmutableMap.of("amount", String.valueOf(plugin.getConfigManager().getMaxDragons()))); //config says the given values is maximum amount! so don't use dragonCount (real amount)!!
					// "cancel" the event...
					e.getBlock().setType(Material.AIR); 		//remove the placed egg
					if (e.getBlock().hasMetadata("isPetEgg")) {
						e.getBlock().removeMetadata("isPetEgg", plugin);
					}
					giveItemInHand(e, dragEgg);					//give back the egg
					return;
				}
			}

			if (e.getPlayer().getGameMode() == GameMode.CREATIVE && !plugin.getConfigManager().isAlwaysUseUpEgg()){
				giveItemInHand(e, dragEgg);						//give back the egg
			}

			PetEnderDragon dragon = plugin.getFactory().create(e.getBlock().getLocation().add(0, 3, 0), e.getPlayer().getUniqueId());
			dragon.spawn();
			plugin.getConfigManager().sendMessage(e.getPlayer(), Message.EGG_HATCHED, null);
			Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
				e.getBlock().setType(Material.AIR);
				if (e.getBlock().hasMetadata("isPetEgg")) {
					e.getBlock().removeMetadata("isPetEgg", plugin);
				}
			});
		},10);
	}

	private void fixItemInHand(BlockPlaceEvent e) {
		if (e.getItemInHand().getAmount() > 1) {
			e.getItemInHand().setAmount(e.getItemInHand().getAmount() - 1);
			e.getPlayer().getInventory().setItem(e.getHand(), e.getItemInHand());
		} else {
			e.getItemInHand().setAmount(0);//clear event's amount...
			e.getPlayer().getInventory().setItem(e.getHand(), null);
		}
	}
	
	private void giveItemInHand(BlockPlaceEvent e, ItemStack item) {
		ItemStack it = e.getPlayer().getInventory().getItem(e.getHand());
		if (it.getAmount() > 0 && it.isSimilar(item)) {
			it.setAmount(it.getAmount() + 1);
			e.getPlayer().getInventory().setItem(e.getHand(), it);
		} else {
			if (e.getPlayer().getInventory().getItem(e.getHand()).getType().equals(Material.AIR)) {
				e.getPlayer().getInventory().setItem(e.getHand(), item);
//				e.getItemInHand().setAmount(1);
			} else { //Hand has Item, but not a PetDragonEgg, drop the item, so the player can pick it up...
				e.getPlayer().getWorld().dropItem(e.getPlayer().getLocation(), item);
			}
		}
	}
	
	@EventHandler
	public void dontTouchTheEgg(PlayerInteractEvent event){
		if (event.getClickedBlock()!=null && event.getClickedBlock().hasMetadata("isPetEgg")){ //Block has temporary meta -> it is placed egg for hatching...
			event.setCancelled(true);
		}
	}
}
