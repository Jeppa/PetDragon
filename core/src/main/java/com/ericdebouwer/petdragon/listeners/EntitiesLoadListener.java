package com.ericdebouwer.petdragon.listeners;

import com.ericdebouwer.petdragon.PetDragon;

import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

public class EntitiesLoadListener {

    public EntitiesLoadListener(PetDragon plugin) {
        try {
            Class.forName("org.bukkit.event.world.EntitiesLoadEvent");
            plugin.getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                private void onEntitiesLoad(EntitiesLoadEvent event) {
                    for (Entity ent: event.getEntities()){
        				if (plugin.getFactory().checkPetDragonBroken(ent)) {
        					plugin.getFactory().handleDragonReset(ent);
        				}
                    }
                }
            }, plugin);
        } catch (ClassNotFoundException ex) {
            plugin.getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                private void onChunkLoad(ChunkLoadEvent event) {
                    for (Entity ent: event.getChunk().getEntities()){
        				if (plugin.getFactory().checkPetDragonBroken(ent)) {
        					plugin.getFactory().handleDragonReset(ent);
        				}
                    }
                }
            }, plugin);
        }

    }
}
