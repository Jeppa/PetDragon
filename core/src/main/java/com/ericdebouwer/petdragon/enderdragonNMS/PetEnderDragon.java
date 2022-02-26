package com.ericdebouwer.petdragon.enderdragonNMS;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

public interface PetEnderDragon {
	
	String DRAGON_ID = "CustomPetDragon";
	
	String OWNER_ID = "OwnerOfTheDragon";
	
	float MAX_HEALTH = 60.0F;
	
	default void setupDefault(){
		EnderDragon dragon = this.getEntity();
		dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(MAX_HEALTH);
		dragon.setHealth(MAX_HEALTH);
		dragon.getScoreboardTags().add(PetEnderDragon.DRAGON_ID);
	}
	
	void copyFrom(EnderDragon dragon);
	
	void spawn();

	EnderDragon getEntity();

	//Jeppa: Add saving of last location and calling the DragonMoveEvent! (PlayerMoveEvent)
	Location getLastLocation();
	
	void setLastLocation(Location loc);
	
	default void tickEvent(Player rider){
		Location newLoc = rider.getLocation();
		if (getLastLocation() != null) {
			if (!getLastLocation().equals(newLoc)) {
				PlayerMoveEvent event = new PlayerMoveEvent(rider, getLastLocation(), newLoc); 
				Bukkit.getPluginManager().callEvent(event);
				setLastLocation(newLoc);
			}
	    } else setLastLocation(newLoc);
	}

	
}
