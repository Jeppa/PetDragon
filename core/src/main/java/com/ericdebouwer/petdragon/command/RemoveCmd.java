package com.ericdebouwer.petdragon.command;

import com.ericdebouwer.petdragon.PetDragon;
import com.ericdebouwer.petdragon.config.Message;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class RemoveCmd extends SubCommand {

    public RemoveCmd(PetDragon plugin) {
        super(plugin, "remove");
    }

    @Override
    boolean perform(CommandSender sender, String[] args) {

        Player player = (Player) sender;
        int range = 3;

        Location dragLoc=null;
        UUID owner = player.getUniqueId();

        //Jeppa: check if there are more args and if those are location data
        if (args.length >= 4){
        	try {
        		World world = Bukkit.getServer().getWorld(args[0]);
        		double x = Double.valueOf(args[1]);
        		double y = Double.valueOf(args[2]);
        		double z = Double.valueOf(args[3]);
        		dragLoc=new Location(world,x,y,z);
        		if (!dragLoc.getChunk().isLoaded()) dragLoc.getChunk().load();
        	} catch (NumberFormatException | NullPointerException e){
        		configManager.sendMessage(player, Message.VALUES_INVALID, null);
        		return true;
        	}
        } else { //won't work i guess.... but anyway... and the args given here were wrong: UUID was 1st arg, but was checked as 2nd...
        	if (args.length >= 1){ //the way is was: 1st = range, 2nd = UUID -> now it is only one arg, used as range OR as UUID !
        		try {		//check if 1st argument is UUID and get it's entity
        			Entity potentialDragon = Bukkit.getEntity(UUID.fromString(args[0]));
        			if (potentialDragon!=null) {
        				dragLoc=potentialDragon.getLocation().getBlock().getLocation();
        				if (!dragLoc.getChunk().isLoaded()) dragLoc.getChunk().load();
        			}
        		} catch(IllegalArgumentException ila){
                	try {	//check if argument is range...
                		int argRange = Integer.parseInt(args[0]);
                		if (argRange < 0 || argRange > 20) throw new NumberFormatException();
                		range = argRange;
                	} catch (NumberFormatException e){
                		configManager.sendMessage(player, Message.RANGE_INVALID, null);
                		return true;
                	}
                }
        	}
        }
        final Location dragLoc_=dragLoc;//make it final...
        final int range_=range;			//make it final...
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
        	boolean found = false;
        	if (args.length >= 1){
        		Entity potentialDragon=null;
        		try{//check again if UUID is given as 1st arg and get that entity
                	potentialDragon = Bukkit.getEntity(UUID.fromString(args[0]));
                } catch(IllegalArgumentException ila){}
                //Jeppa: check if there are more args and if those are location data
                if (args.length >= 4 && dragLoc_!=null){
    				Entity[] ents=dragLoc_.getChunk().getEntities();
    				for (Entity ent:ents) {
    					if (plugin.getFactory().isPetDragon(ent)) { //Check if first Entity found is a PetDragon
    						if (plugin.getFactory().getOwner((EnderDragon)ent).equals(owner)){
        						potentialDragon=ent;
        						break;
    						}
    					}
    				}
                }
                if (plugin.getFactory().isPetDragon(potentialDragon)){

                	Location dragLoc2=potentialDragon.getLocation().getBlock().getLocation();
                    plugin.getLocationManager().remLocation(owner, dragLoc2);
                    
                    potentialDragon.remove();
                    found = true;
                }
        	}
        	if (!found) {//search for dragon nearby... with range 3 or range from arg

        		List<Entity> nearbyEnts = (List<Entity>) player.getWorld().getNearbyEntities(
        				player.getLocation(), range_, range_, range_, (e) -> plugin.getFactory().isPetDragon(e));
        		nearbyEnts.sort(Comparator.comparingDouble((e) ->
                    e.getLocation().distanceSquared(player.getLocation())));

        		if (!nearbyEnts.isEmpty()){
        			EnderDragon dragon = (EnderDragon) nearbyEnts.get(0);
        			UUID owner_ = plugin.getFactory().getOwner(dragon);
        			if (!player.hasPermission("petdragon.bypass.remove") && !player.getUniqueId().equals(owner_)){
        				String ownerName = Bukkit.getOfflinePlayer(owner_).getName();
        				configManager.sendMessage(player, Message.NOT_YOURS_TO_REMOVE,
        						ImmutableMap.of("owner", ownerName == null ? "unknown" : ownerName));
        				return;// true;
        			}
        			//Jeppa: remove dragon Location from File...
        			plugin.getLocationManager().remLocation(owner_, dragon.getLocation());
                
        			dragon.remove();
        			found = true;
        		}
        	}

        	if (found) configManager.sendMessage(player, Message.DRAGON_REMOVED, null);
        	else configManager.sendMessage(player, Message.DRAGON_NOT_FOUND, null);
        },10);
    
        return true;
    }

    @Override
    public List<String> tabComplete(String[] args){
        if (args.length == 1)
            return this.filter(Arrays.asList("3", "5", "10"), args[0].toLowerCase());

        return super.tabComplete(args);
    }
}
