package com.ericdebouwer.petdragon;

import com.ericdebouwer.petdragon.config.Message;
import com.ericdebouwer.petdragon.enderdragonNMS.PetEnderDragon;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DragonFactory {
	
	PetDragon plugin;
	Class<?> dragonClass;
	private final boolean correctVersion;
	public NamespacedKey ownerKey;

	public DragonFactory(PetDragon plugin){
		this.plugin = plugin;
		this.ownerKey = new NamespacedKey(plugin, PetEnderDragon.OWNER_ID);
		this.correctVersion = this.setUpDragonClass();
	}
	
	public boolean isCorrectVersion(){
		return correctVersion;
	}
	
	String nmsVersion; //make it usable from outside.. :)
	public boolean setUpDragonClass(){
		String packageName = plugin.getServer().getClass().getPackage().getName();
		nmsVersion = packageName.substring(packageName.lastIndexOf('.') + 1);

		// version did remap even though version number didn't increase
		String mcVersion = Bukkit.getBukkitVersion().substring(0, Bukkit.getBukkitVersion().indexOf('-'));
		if (mcVersion.equals("1.17.1")) {
			nmsVersion =  "v1_17_R1_2";
		}

    	try {
        	final Class<?> clazz = Class.forName("com.ericdebouwer.petdragon.enderdragonNMS.PetEnderDragon_" + nmsVersion);
        	if (PetEnderDragon.class.isAssignableFrom(clazz)) { 
        		this.dragonClass = clazz;
        		return true;
        	}
    	} catch (final Exception e) {
        	return false;
   		}
    	return false;
	}

	public PetEnderDragon create(Location loc, UUID owner){
		return this.create(loc, owner, false);
	}
	
	private PetEnderDragon create(Location loc, UUID owner, boolean replace){ //'replace' is obsolete... 
		try {
			PetEnderDragon dragon = (PetEnderDragon) dragonClass.getConstructor(Location.class, PetDragon.class).newInstance(loc, plugin);
			if (owner != null){
				dragon.getEntity().getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
				//Jeppa: Save dragon location to file
				plugin.getLocationManager().addLocation(owner, loc);
			}
			return dragon;
		} catch (Exception e){
			e.printStackTrace();
		}
		return null;
	}
	
	public boolean isPetDragon(Entity ent){
		if (!(ent instanceof EnderDragon)) return false;
		boolean isPet=ent.getScoreboardTags().contains(PetEnderDragon.DRAGON_ID);
		//check for existing owner...(if Tag fails...should never happen...)
		if (!isPet) {
			isPet=ent.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
		}
		return isPet;
	}
	
	public boolean canDamage(HumanEntity player, PetEnderDragon dragon){
		UUID owner = this.getOwner(dragon.getEntity());
		if (owner == null) return true;
		if (owner.equals(player.getUniqueId())) return player.hasPermission("petdragon.hurt.self");
		return player.hasPermission("petdragon.hurt.others");
	}
	
	public boolean tryRide(HumanEntity p, EnderDragon dragon){
		if (!isPetDragon(dragon)) return false;

		ItemStack handHeld = p.getInventory().getItemInMainHand();
		if ( !(handHeld == null || handHeld.getType().isAir())) return false;
		
		if (!p.hasPermission("petdragon.ride")) {
			plugin.getConfigManager().sendMessage(p, Message.NO_RIDE_PERMISSION, null);
			return true;
		}
		UUID owner = getOwner(dragon);
		if (!p.hasPermission("petdragon.bypass.owner") && owner != null && !p.getUniqueId().equals(owner)){
			String ownerName = Bukkit.getOfflinePlayer(owner).getName();
			plugin.getConfigManager().sendMessage(p, Message.NO_JOYRIDE, ImmutableMap.of("owner", ownerName == null ? "unknown" : ownerName));
			return true;
		}
		dragon.addPassenger(p);

		//Jeppa: Reset Dragon if needed...
		if (checkPetDragonBroken(dragon)) {
			handleDragonReset(dragon);
		}
		//Jeppa: remove saved location from file...
		plugin.getLocationManager().remLocation(owner, dragon.getLocation());
		
		return true;
	}
	
	public void reloadDragons(){ //by command
		for (World w: Bukkit.getWorlds()){
			for (EnderDragon dragon: w.getEntitiesByClass(EnderDragon.class)){
				plugin.getFactory().handleDragonReset(dragon);
			}
		}
	}

	public void handleDragonReset(Entity ent){
		if (!isPetDragon(ent)) return;
		EnderDragon dragon = (EnderDragon) ent;

		List<Entity> passengers = dragon.getPassengers();
//		dragon.remove();

		PetEnderDragon petDragon = this.create(dragon.getLocation(), null, true); //true -> obsolete!!
		petDragon.copyFrom(dragon);
		double health=dragon.getHealth();
		dragon.remove();
		petDragon.spawn();
		petDragon.getEntity().setHealth(health);//keep the damage/health?
		passengers.forEach(p -> petDragon.getEntity().addPassenger(p));
	}

	public Set<EnderDragon> getDragons(OfflinePlayer player){
		Set<EnderDragon> result = new HashSet<>();
		for (World world: Bukkit.getWorlds()){
			for (EnderDragon dragon: world.getEntitiesByClass(EnderDragon.class)){
				if (!isPetDragon(dragon)) continue;
				if (!player.getUniqueId().equals(getOwner(dragon))) continue;

				result.add(dragon);
			}
		}
		return result;
	}

	public UUID getOwner(EnderDragon dragon){
		if (!dragon.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) return null;
		
		String uuidText = dragon.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
		if (uuidText == null || uuidText.equals("")) return null;
		return UUID.fromString(uuidText);
	}

	//Jeppa: Check if dragon needs a reset...
	public boolean checkPetDragonBroken(Entity dragon) {
		boolean resetNeeded=false;
		if (dragon instanceof EnderDragon) {
			try {
				Class<?> CraftDragClass = Class.forName("org.bukkit.craftbukkit."+nmsVersion+".entity.CraftEnderDragon");
				PetEnderDragon petDragon = (PetEnderDragon) CraftDragClass.getDeclaredMethod("getHandle").invoke(CraftDragClass.cast(dragon));
			} catch (ClassCastException e) {//Dragon can not be cast from EntityEnderDragon to PetEnderDragon ! Dragon is broken! --> Reset!
				resetNeeded=true;//dragon can't be casted anymore -> Reset is needed!!!
			} catch (Exception e) {//Any other Error....
			}
		}
		return resetNeeded;
	}
}
