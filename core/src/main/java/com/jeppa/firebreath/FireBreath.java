package com.jeppa.firebreath;
/** Code is based on tobiyas, modified by Jeppa and implemented into PetDragon by Jeppa */

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import com.ericdebouwer.petdragon.PetDragon;

public class FireBreath {
	
	PetDragon plugin;

	private Location location;
	private Vector direction;
	
	private double speed = 1;
	private boolean alive = true;
	private boolean alreadyHit = false;
	private int distance= 200; //Jeppa: max distance
	private final int baseDistance=distance;
	
	boolean TimerReset = false;
	
	private BurningBlocksContainer blockContainer;
	
	
	public FireBreath(PetDragon plugin, Location location, Vector direction){
		this.plugin = plugin;
		this.location = location;
		this.direction = direction.normalize();
		blockContainer = new BurningBlocksContainer();
	}
	
	/**
	 * Ticks this pseudo Entity
	 * Returns true if the entity is still alive.
	 * Returns false if the entity is dead and will not be called.
	 * 
	 * @return
	 */
	public boolean tick(){
		if(!alive){
			return false;
		}
		if(!alreadyHit){
			checkCollision();
			if(!alreadyHit) {
				tickLocation();
				distance--;
				if (distance >0) { 
					return true;
				} else this.alive = false;
			} else 	{
				spreadFire();
			}
		}else{
			spreadFire();
		}
		return false;
	}
	
	private void checkCollision(){
		Material mat = location.getBlock().getType();
		if((mat != Material.AIR) && (mat != Material.FIRE)){
			Material lava;
			Material water;
			try {
				lava = Material.valueOf("STATIONARY_LAVA");
				water= Material.valueOf("STATIONARY_WATER");
			} catch (Exception e){
				lava = Material.LAVA;
				water= Material.WATER;
			}
			alreadyHit = true;
			if ((mat.isSolid()) || (mat == lava)) antitickLocation();
			Location Blockloc = location.getBlock().getLocation();
			if (mat != water) blockContainer.addBlock(Blockloc);
			else this.alive = false;
		}
	}
	
	//spreads the fire one block in all directions , ticks the blocks
	private void spreadFire(){
		blockContainer.tick();
		if(blockContainer.areAllTicksDone()){
			this.alive = false;
		}
	}
	
	private void tickLocation(){
		this.location = location.add(direction);
		if(baseDistance-distance>8)location.getWorld().playEffect(location, Effect.MOBSPAWNER_FLAMES, 1);
	}
	private void antitickLocation(){
		this.location = location.subtract(direction);
	}
	
//	/**
//	 * Updates speed + Vector of speed
//	 * @param newSpeed
//	 */
//	public void setSpeed(double newSpeed){
//		this.speed = newSpeed;
//		this.direction = direction.normalize().multiply(newSpeed);
//	}
//	
//	public double getSpeed(){
//		return speed;
//	}
}
