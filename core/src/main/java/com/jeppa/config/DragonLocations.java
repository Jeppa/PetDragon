package com.jeppa.config;

import com.ericdebouwer.petdragon.PetDragon;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

//Jeppa: This file handles the DragonLocations file and it's usage!
public class DragonLocations {
	
	private final PetDragon plugin;
	
	public DragonLocations(PetDragon plugin) {
		this.plugin = plugin;
	}
	
	public FileConfiguration dragonLocations = null;
	private File locationFile = null;
	
	private Set<UUID> markerForRunningCleanup=new HashSet<UUID>();
	
	private void reloadLocations() {
		if (locationFile == null) {
			locationFile = new File(plugin.getDataFolder(), "DragonLocations.yml");
		}
		dragonLocations = YamlConfiguration.loadConfiguration(locationFile);
	}
	public FileConfiguration getDragonLocations() {
		if (dragonLocations == null) {
			reloadLocations();
		}
		return dragonLocations;
	}
	void saveLocationlist() {
		if (dragonLocations == null || locationFile == null) {
			return;
		}
		try {
			getDragonLocations().save(locationFile);
		} catch (IOException ex) {
			plugin.getLogger().warning("Could not save location list to " + locationFile);
		}
	}
//	public void clearLocationList(){
//		dragonLocations.set("Locations", null);
//		saveLocationlist();
//	}
	
	//Usage/handling: add a location to the list
	public void addLocation(UUID uid, Location loc) {
		if (uid!=null) {
		String uuid = uid.toString();
		loc = loc.getBlock().getLocation();
		if(!getConfSec("Locations").getKeys(false).contains(uuid)){
			dragonLocations.set("Locations."+uuid+".1", loc);
		} else {
			Collection<Object> locs = getConfSec("Locations."+uuid).getValues(false).values();
			if (locs.contains(loc))return;
			dragonLocations.set("Locations."+uuid+"."+(locs.size()+1),loc);
		}
		saveLocationlist();
		}
	}
	//remove a location from the saved list:
	public void remLocation(UUID uuid, Location loc) {
		if(!getConfSec("Locations").getKeys(false).contains(uuid.toString())){
			return;
		} else {
			Collection<Object> locs = getConfSec("Locations."+uuid).getValues(false).values();
			loc = loc.getBlock().getLocation();
			if (locs.contains(loc)) {
				locs.remove(loc);
				reorgLocationSection(uuid,locs);
			}
		}
	}
	//Check the saved locations, and cleanup
	public void activateLocations(UUID uuid) {
		//First: check if any...!
		if(!getConfSec("Locations").getKeys(false).contains(uuid.toString()))return;

		Collection<Object> locs = getConfSec("Locations."+uuid).getValues(false).values();
		//activate chunks!
		locs.forEach(loc -> {Chunk chunk=((Location)loc).getChunk();if(!chunk.isLoaded())chunk.load();});
		cleanupLocations(locs, uuid); //check the locations, reorg and save the locations (after 10 ticks...)
	}
	//Check the given List and return List with Locations of existing Dragons (No delay here)
	private Collection<Location> getEntLocations(Collection<Object> oldLocs, UUID uuid){
		Collection<Location> returnLocs= new ArrayList<Location>();
		for (Object loc:oldLocs){
			Chunk chunk = ((Location)loc).getChunk();
			if (chunk.isLoaded()) {
				Entity[] ents=chunk.getEntities();
				for (Entity ent:ents) {
					if (plugin.getFactory().isPetDragon(ent) && plugin.getFactory().getOwner((EnderDragon)ent).equals(uuid)) {
						Location dragLoc = ent.getLocation().getBlock().getLocation();
						if (loc.equals(dragLoc)) {
							returnLocs.add(dragLoc);
							break;
						}
					}
				}
			}
		}
		return returnLocs; //List with dragons at given locations
	}
	//get a ConfigurationSection, even if it's not existing, yet...
	private ConfigurationSection getConfSec(String section) {
		ConfigurationSection configSec=dragonLocations.getConfigurationSection(section);
		if (configSec==null) {
			dragonLocations.createSection(section);
			configSec=dragonLocations.getConfigurationSection(section);
		}
		return configSec;
	}
	private void cleanupLocations(Collection<Object> locs, UUID uuid) {
		if (!markerForRunningCleanup.contains(uuid))markerForRunningCleanup.add(uuid);//mark this UUID
		Collection<Location> returnLocs_1 = getEntLocations(locs, uuid); //could be empty, depends on chunk loaded or not...
		//get Location-list a few ticks later, so Paper can load the entities after chunk has been loaded
		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
			Collection<Location> returnLocs = getEntLocations(locs, uuid);//could be empty too, depends on chunk loaded or not...
			returnLocs_1.forEach(loc -> {if(!returnLocs.contains(loc))returnLocs.add(loc);});
			reorgLocationSection(uuid, returnLocs);	//write new list to config!
			markerForRunningCleanup.remove(uuid);	//remove this UUID marker
		},10);
	}
	private void reorgLocationSection(UUID uuid, Collection<?> locs) {
		int i=1;
		dragonLocations.set("Locations."+uuid.toString(),null);
		for (Object loc:locs) {
			dragonLocations.set("Locations."+uuid.toString()+"."+i,loc);
			i++;
		}
		saveLocationlist();
	}
	//getter for the UUID-marker..
	public boolean isUUIDMarked(UUID uuid){
		return markerForRunningCleanup.contains(uuid);
	}
}
