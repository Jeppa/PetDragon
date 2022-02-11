package com.ericdebouwer.petdragon.listeners;

import com.ericdebouwer.petdragon.PetDragon;
import com.ericdebouwer.petdragon.api.DragonSwoopEvent;
import com.jeppa.firebreath.FireBreath;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragon.Phase;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.util.Arrays;

public class DragonListener implements Listener {
	
	PetDragon plugin;
	
	public DragonListener(PetDragon plugin){
		this.plugin = plugin;
	}
	
	@EventHandler
	public void onJoin(PlayerJoinEvent e){
		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
			Entity vehicle = e.getPlayer().getVehicle();
			plugin.getFactory().handleDragonReset(vehicle);
		});
	}
	
	@EventHandler
	public void worldLoad(WorldLoadEvent e){
		for (Entity ent: e.getWorld().getEntitiesByClass(EnderDragon.class)){
			plugin.getFactory().handleDragonReset(ent);
		}
	}

	@EventHandler //should never happen, just in case //Jeppa: additional event for dragon reset (else, if dragon has lost focus and gets attacked, it will fly to 0/0...)
	public void onDragonChangePhase(EnderDragonChangePhaseEvent event){
		EnderDragon dragon=event.getEntity(); //Info: working PetDragons don't return a location here!? -> dragon.getLocation() results in 0/0/0 !
		if (!plugin.getFactory().isPetDragon(dragon)) return;
//		Phase phase = event.getNewPhase();
		Phase oldPhase=event.getCurrentPhase();
		if (oldPhase.equals(Phase.HOVER)){ //dragon tries to break free ;)
			event.setCancelled(true);
			dragon.setPhase(Phase.HOVER);
			plugin.getFactory().handleDragonReset(dragon);
		}
	}


	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSwoop(DragonSwoopEvent event){
		if (!(event.getTarget() instanceof Player)) return;
		Player target = (Player) event.getTarget();

		if (shouldCancelAttack(event.getEntity(), target)) event.setCancelled(true);
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled = true)
	public void entityDamage(EntityDamageByEntityEvent e){
		Entity damager = e.getDamager();
		if (damager instanceof AreaEffectCloud){
			AreaEffectCloud cloud = (AreaEffectCloud) damager;
			if (cloud.getSource() instanceof Entity) {
				damager = (Entity) cloud.getSource();
			}
		}

		if (!plugin.getFactory().isPetDragon(damager)) return;
		if (!plugin.getConfigManager().damageEntities) e.setCancelled(true);
		if (!(e.getEntity() instanceof Player)) return;
		
		Player player = (Player) e.getEntity();
		if (shouldCancelAttack((EnderDragon) damager, player)) e.setCancelled(true);
	}

	private boolean shouldCancelAttack(EnderDragon dragon, Player player){
		return player.getUniqueId().equals(plugin.getFactory().getOwner(dragon)) ||
				dragon.getPassengers().contains(player);
	}
	
	//stop kick for flying
	@EventHandler(priority=EventPriority.LOWEST)
	public void kick(PlayerKickEvent e){
		if (!e.getReason().toLowerCase().contains("flying")) return;
		if (e.getPlayer().getNoDamageTicks() > 10) e.setCancelled(true);
		if (plugin.getFactory().isPetDragon(e.getPlayer().getVehicle())) e.setCancelled(true);
	}
	
	@EventHandler
	public void dragonDismount(EntityDismountEvent e){
		if (!plugin.getFactory().isPetDragon(e.getDismounted())) return;
		if (!(e.getEntity() instanceof Player)) return;
		Player player = (Player) e.getEntity();
		//prevent fall damage
		player.setNoDamageTicks(150);
		//Jeppa: save dragon's location to file ...
		EnderDragon dragon=(EnderDragon)e.getDismounted();
		plugin.getLocationManager().addLocation(plugin.getFactory().getOwner(dragon), dragon.getLocation()); //not UUID of player, but of owner...
	}
	
	@EventHandler
	public void riderDamage(EntityDamageEvent e){
		if (!plugin.getFactory().isPetDragon(e.getEntity().getVehicle())) return;
		if (Arrays.asList(DamageCause.FLY_INTO_WALL, DamageCause.ENTITY_EXPLOSION, DamageCause.DRAGON_BREATH, DamageCause.FALL)
				.contains(e.getCause())) e.setCancelled(true);
	}

	
	@EventHandler
	public void interact(PlayerInteractEntityEvent e){
		if (e.getHand() != EquipmentSlot.HAND) return; //prevent double firing
		
		if (!plugin.getConfigManager().rightClickRide) return;
		if (!(e.getRightClicked() instanceof EnderDragonPart)) return;
		
		EnderDragonPart part = (EnderDragonPart) e.getRightClicked();
		EnderDragon dragon = part.getParent();
		plugin.getFactory().tryRide(e.getPlayer(), dragon);
	}

	@EventHandler //Jeppa: additional event for location file cleanup...
	public void onDragonDeath(EntityDeathEvent event){
		if (!plugin.getFactory().isPetDragon(event.getEntity())) return;
		// remove saved location from file...
		EnderDragon dragon=(EnderDragon)event.getEntity();
		plugin.getLocationManager().remLocation(plugin.getFactory().getOwner(dragon), dragon.getLocation());//should work, if the dragon is not moving right now :)
	}

	
	/** Here starts the Code added by Jeppa to implement FireBreath! */
	@EventHandler
	public void noDropFiretems(PlayerDropItemEvent event){
		Player player = event.getPlayer();
		ItemStack item=event.getItemDrop().getItemStack();
		commonEvent(event, player, item);
	}
	@EventHandler
	public void fireFire(PlayerInteractEvent event){
		Player player = event.getPlayer();
		ItemStack item=event.getItem();
		commonEvent(event, player, item);
	}
	private void commonEvent(Event event, Player player, ItemStack item) {
		Entity vehicle = player.getVehicle();
		if (vehicle!=null & plugin.getFactory().isPetDragon(vehicle)){
			boolean breathPerm = player.hasPermission("petdragon.breath");
			boolean ballsPerm = player.hasPermission("petdragon.shoot");
			if((!ballsPerm)&&(!breathPerm)){
				return;	//no permission at all...
			}
			//test for item:
			boolean itemFound=false;
			if (item!=null) {
				if(ballsPerm && item.getType().equals(Material.valueOf(plugin.getConfigManager().breathMat))){//breathMat is for FireBall!
					itemFound=true;
					fireTheBall(vehicle,player);
				} else if(breathPerm && item.getType().equals(Material.valueOf(plugin.getConfigManager().firebreathMat))){
					itemFound=true;
					fireTheBreath(player);
				}
				if (itemFound) {
					if (event instanceof PlayerDropItemEvent) ((PlayerDropItemEvent)event).setCancelled(true);
					else if (event instanceof PlayerInteractEvent) ((PlayerInteractEvent)event).setCancelled(true);
				}
			}
		}
	}
	private int burningLength = 20; //fixed values...
	private int spreadRange = 5;
	private void fireTheBreath(Player player){
		int time = 20; //one sec
		Vector direction = player.getEyeLocation().getDirection();
		final FireBreath fireBreath = new FireBreath(plugin, player.getEyeLocation(), direction);
		final int thisTaskInt = Bukkit.getServer().getScheduler().runTaskTimer(plugin, () -> {
			while(fireBreath.tick()); {
			}
		},5,time).getTaskId();
		//2nd task for stopping 1st task...
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
			Bukkit.getServer().getScheduler().cancelTask(thisTaskInt);
		}, (burningLength+spreadRange+5)*time);
	}
	private void fireTheBall(Entity dragon, Player player){
		//Direct call is possible again (no bounce...)
		Vector forwardDir = player.getEyeLocation().getDirection();
		Location loc = dragon.getLocation();
		loc.getWorld().spawn(loc.add(forwardDir.clone().multiply(10).setY(-1)), DragonFireball.class, fireball -> {
			fireball.setDirection(forwardDir);
			fireball.setShooter(player);
		});
	}

}
