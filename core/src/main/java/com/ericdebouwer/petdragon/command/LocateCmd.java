package com.ericdebouwer.petdragon.command;

import com.ericdebouwer.petdragon.PetDragon;
import com.ericdebouwer.petdragon.config.Message;
import com.google.common.collect.ImmutableMap;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class LocateCmd extends SubCommand {

    public LocateCmd(PetDragon plugin) {
        super(plugin, "locate");
    }

    @Override
    boolean perform(CommandSender sender, String[] args) {

        Player player = (Player) sender;
        
        //Jeppa: 1st check all saved Locations if they are still valid and have dragons...
        UUID uuid = player.getUniqueId();
        //Collection<Location> locationList = //may be used....
        plugin.getDragonLocations().activateLocations(uuid); //activates the locations/chunks from list for this player!
        
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
		//now go on with main function...
	        Set<EnderDragon> dragons = plugin.getFactory().getDragons(player);
	
	        if (dragons.isEmpty()){
	            plugin.getConfigManager().sendMessage(player, Message.NO_LOCATE, null);
	        }
	        configManager.sendMessage(player, Message.LOCATED_DRAGONS, ImmutableMap.of("amount", "" + dragons.size()));
	        for (EnderDragon dragon: dragons){
	            Location loc = dragon.getLocation();
	            plugin.getDragonLocations().addLocation(uuid, loc.getBlock().getLocation()); //Jeppa: (re)adds a location if it's missing in the list... (This tries 'fixing' the Entity Bug in MC 1.17/1.18 under Paper... NOT 100% )
	            String text = configManager.parseMessage(Message.LOCATE_ONE, ImmutableMap.of("x", "" +loc.getBlockX(),
	                    "y", "" + loc.getBlockY(), "z", "" + loc.getBlockZ(), "world", loc.getWorld().getName()));
	
	            if (configManager.isClickToRemove()) {
	                TextComponent message = new TextComponent(text);
//	                message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + BaseCommand.NAME + " remove " + dragon.getUniqueId())); //'old' method by UUID, but UUID does not survive chunk reloads.. :(
	                message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + BaseCommand.NAME + " remove " + loc.getWorld().getName() + " " +loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));//Jeppa: new method using the location to find a dragon
	                if (plugin.getSubVer()>=17) {//Jeppa: keep compatibility to old versions...(deprecated...)
	                	message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(configManager.parseMessage(Message.LOCATED_HOVER, null))));
	                } else {
	                	message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(configManager.parseMessage(Message.LOCATED_HOVER, null))));
	                }
	                player.spigot().sendMessage(message);
	            }else {
	                player.sendMessage(text);
	            }
	        }
        },10);
        return true; //just return, no matter what happend...
    }
}
