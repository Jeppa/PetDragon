package com.jeppa.config;

import com.ericdebouwer.petdragon.PetDragon;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

//Jeppa: This file handles the DragonLocations file and it's usage!
public class DragonLocations {
	
	private final PetDragon plugin;
	
	public DragonLocations(PetDragon plugin) {
		this.plugin = plugin;
	}
	
	public FileConfiguration dragonLocations = null;
	private File locationFile = null;
	
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
	public void clearLocationList(){
		dragonLocations.set("Locations", null);
		saveLocationlist();
	}
	
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
	//Check the saved locations, and cleanup (returns the cleaned List, if needed for anything...)
	public Collection<Location> getLocationList(UUID uuid) {
		//First: check if any...!
		if(!getConfSec("Locations").getKeys(false).contains(uuid.toString())){
			return null;
		} else {
			Collection<Object> locs = getConfSec("Locations."+uuid).getValues(false).values();
			//check the locations, and activate chunks!
			Collection<Location> returnLocs= new ArrayList<Location>();
			for (Object loc:locs){
				Chunk chunk = ((Location)loc).getChunk();
				if (!chunk.isLoaded())chunk.load();
				Entity[] ents=chunk.getEntities();
				for (Entity ent:ents) {
					if (plugin.getFactory().isPetDragon(ent)) { //Check if Entity is a PetDragon
						Location dragLoc = ent.getLocation().getBlock().getLocation();
						if (loc.equals(dragLoc)) {
							returnLocs.add(dragLoc);
							break;
						}
					}
				}
			}
			//now write new 'returnLocs'-list to config!
			reorgLocationSection(uuid,(Collection<?>)returnLocs);
			//..and return the list...
			return returnLocs;
		}
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
	private void reorgLocationSection(UUID uuid, Collection<?> locs) {
		int i=1;
		dragonLocations.set("Locations."+uuid.toString(),null);
		for (Object loc:locs) {
			dragonLocations.set("Locations."+uuid.toString()+"."+i,loc);
			i++;
		}
		saveLocationlist();
	}
	
}
