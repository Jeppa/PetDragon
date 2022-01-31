package com.jeppa.firebreath;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

public class BurningBlock {

	private int ticksToBurn;
	private Location location;
	private boolean done = false;
	private Material oldMaterial;
	
	public BurningBlock(Location location, int ticksToBurn){
		this.ticksToBurn = ticksToBurn;
		this.location = location;
		this.oldMaterial = location.getBlock().getType();
	}
	
	public void tick(){
		Block Block = location.getBlock();
		if(ticksToBurn > 0){ //secs
			if (!(Block.getType().equals(Material.FIRE))) Block.setType(Material.FIRE);
			ticksToBurn--;
		} else {
			Block.setType(oldMaterial);
			done = true;
		}
	}

	public Location getLocation() {
		return location;
	}

	public boolean isDone() {
		return done;
	}
}
