package com.ericdebouwer.petdragon.enderdragonNMS;

import com.ericdebouwer.petdragon.PetDragon;
import com.ericdebouwer.petdragon.api.DragonSwoopEvent;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.types.Type;
import net.minecraft.SharedConstants;
import net.minecraft.core.IRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutWorldEvent;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.MathHelper;
import net.minecraft.util.datafix.DataConverterRegistry;
import net.minecraft.util.datafix.fixes.DataConverterTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumCreatureType;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.boss.EntityComplexPart;
import net.minecraft.world.entity.boss.enderdragon.EntityEnderDragon;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.World;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftEnderDragon;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftLivingEntity;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

public class PetEnderDragon_v1_17_R1 extends EntityEnderDragon implements PetEnderDragon {

	@Getter @Setter
	private Location lastLocation;

	static Field jumpField;
	static Method checkWalls;
	static Method checkCrystals;
	static {
		MinecraftKey mcKey = new MinecraftKey(PetEnderDragon.ENTITY_ID);
		try {
			if (!IRegistry.Y.getOptional(mcKey).isPresent()) {
				@SuppressWarnings("unchecked")
				Map<String, Type<?>> types = (Map<String, Type<?>>) DataConverterRegistry.a().getSchema(
						DataFixUtils.makeKey(SharedConstants.getGameVersion().getWorldVersion()))
						.findChoiceType(DataConverterTypes.q).types();
				types.put(mcKey.toString(), types.get(IRegistry.Y.getKey(EntityTypes.v).toString()));
				IRegistry.a(IRegistry.Y, PetEnderDragon.ENTITY_ID,
						EntityTypes.Builder.a(PetEnderDragon_v1_17_R1::new, EnumCreatureType.a).a().a(PetEnderDragon.ENTITY_ID));
			}
			jumpField = EntityLiving.class.getDeclaredField("bn");
			jumpField.setAccessible(true);
			checkWalls = EntityEnderDragon.class.getDeclaredMethod("b", AxisAlignedBB.class);
			checkWalls.setAccessible(true);
			checkCrystals = EntityEnderDragon.class.getDeclaredMethod("fw");
			checkCrystals.setAccessible(true);
		} catch (NoSuchFieldException | NoSuchMethodException ignore) {
		}
	}

	private final PetDragon plugin;
	private long lastShot;
	private boolean didMove;
	int growlTicks = 100;

	public PetEnderDragon_v1_17_R1(EntityTypes<? extends EntityEnderDragon> entitytypes, World world) {
		this(world.getWorld());
	}

	public PetEnderDragon_v1_17_R1(org.bukkit.World world) {
		super(EntityTypes.v, ((CraftWorld)world).getHandle());
		this.plugin = JavaPlugin.getPlugin(PetDragon.class);

		this.setupDefault();
		this.getBukkitEntity().setSilent(plugin.getConfigManager().isSilent());
		this.P = plugin.getConfigManager().isFlyThroughBlocks();
	}
	
	@Override
	public void copyFrom(EnderDragon dragon) {
		EntityEnderDragon other = ((CraftEnderDragon) dragon).getHandle();
		NBTTagCompound nbt = other.save(new NBTTagCompound());
		nbt.remove("Passengers");
		nbt.remove("WorldUUIDLeast");
		nbt.remove("WorldUUIDMost");
		nbt.remove("UUID"); // probably not required for this version
		nbt.setBoolean("Silent", plugin.getConfigManager().isSilent());
		this.load(nbt);
	}

	@Override
	public void spawn(Vector location) {
		this.setPosition(location.getX(), location.getY(), location.getZ());
		this.t.addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
		plugin.getFactory().saveSpawnLocation(this);
	}
	
	@Override
	public EnderDragon getEntity() {
		return (EnderDragon) this.getBukkitEntity();
	}
	
	@Override
	protected boolean dA() { //affected by fluids
		return false;
	}

	
	@Override
	public boolean bC() { //ridable in water
		return true;
	}
		
	@Override
	public boolean e(NBTTagCompound nbttagcompound) {
		boolean result = super.e(nbttagcompound);
		nbttagcompound.setString("id", PetEnderDragon.ENTITY_ID);
		return result;
	}

	@Override
	public boolean a(EntityComplexPart entitycomplexpart, DamageSource damagesource, float f) {
		if (!(damagesource.getEntity() instanceof EntityHuman)) return false;
		HumanEntity damager = (HumanEntity) damagesource.getEntity().getBukkitEntity();
		
		if (plugin.getConfigManager().isLeftClickRide()){
			if (plugin.getFactory().tryRide(damager, (EnderDragon) this.getBukkitEntity())){
				return false; //cancel damage
			}
			
		}
		
		if (!plugin.getFactory().canDamage(damager, this)) return false;
		
		f = getDragonControllerManager().a().a(damagesource, f);
		f = f / (200.0F / MAX_HEALTH);
		
		//head 4x as much damage
		if (entitycomplexpart != this.e) {
			f = f / 4.0F + Math.min(f, 1.0F);
		}
		
		if (f < 0.01F) {
			return false;
		} else {
			damagesource = DamageSource.d(null); //fake explosion
			this.dealDamage(damagesource, f);
			return true;
		}
	}
	
	@Override
	public boolean canPortal(){
		return true;
	}
    
	@Override
	// each movement update
	public void movementTick(){
		this.au();
		if (this.t.y) {
			this.setHealth(this.getHealth());
			if (!this.isSilent() && --this.growlTicks < 0) { //noises & now also growl if stationary
				this.t.a(this.locX(), this.locY(), this.locZ(), SoundEffects.fm, this.getSoundCategory(), 2.5F, 0.8F + this.Q.nextFloat() * 0.3F, false);
				this.growlTicks = 200 + this.Q.nextInt(200);

			}
		}

		this.bS = this.bT; //flaptimes

		if (this.dV()) { //dead
			float f = (this.Q.nextFloat() - 0.5F) * 8.0F;
			float f1 = (this.Q.nextFloat() - 0.5F) * 4.0F;
			float f2 = (this.Q.nextFloat() - 0.5F) * 8.0F;
			this.t.addParticle(Particles.y, this.locX() + (double)f, this.locY() + 2.0D + (double)f1, this.locZ() + (double)f2, 0.0D, 0.0D, 0.0D);
		} else {
			try {
				checkCrystals.invoke(this); //crystals
			} catch (InvocationTargetException | IllegalAccessException ignore) {}

			this.bT += 0.1F;

			this.setYRot(MathHelper.g(this.getYRot()));

			// update position information
			if (this.d < 0) {
				for(int i = 0; i < this.c.length; ++i) {
					this.c[i][0] = this.getYRot();
					this.c[i][1] = this.locY();
				}
			}

			if (++this.d == this.c.length) {
				this.d = 0;
			}

			this.c[this.d][0] = this.getYRot();
			this.c[this.d][1] = this.locY();
			this.aX = this.getYRot();

			//store parts locations
			Vec3D[] avec3d = new Vec3D[this.cf.length];

			for(int j = 0; j < this.cf.length; ++j) {
				avec3d[j] = new Vec3D(this.cf[j].locX(), this.cf[j].locY(), this.cf[j].locZ());
			}

			float f7 = (float)(this.a(5, 1.0F)[1] - this.a(10, 1.0F)[1]) * 10.0F * 0.017453292F;
			float f8 = MathHelper.cos(f7);
			float f9 = MathHelper.sin(f7);
			float f6 = this.getYRot() * 0.017453292F;
			float f11 = MathHelper.sin(f6);
			float f12 = MathHelper.cos(f6);

			// move body and wings
			this.cf[2].setPosition(this.locX() + (double)(f11 * 0.5F) , this.locY(), this.locZ() + (double)(-f12 * 0.5F));
			this.cf[6].setPosition(this.locX() + (double)(f12 * 4.5F) , this.locY() + 2.0D, this.locZ() + (double)(f11 * 4.5F));
			this.cf[7].setPosition(this.locX() + (double)(f12 * -4.5F) , this.locY() + 2.0D, this.locZ() + (double)(f11 * -4.5F));

			// do knockback, wing hurt and head/neck attack
			if (!this.t.y && this.aK == 0) {

				this.knockBack(this.t.getEntities(this, this.cf[6].getBoundingBox().grow(4.0D, 2.0D, 4.0D).d(0.0D, -2.0D, 0.0D), IEntitySelector.e));
				this.knockBack(this.t.getEntities(this, this.cf[7].getBoundingBox().grow(4.0D, 2.0D, 4.0D).d(0.0D, -2.0D, 0.0D), IEntitySelector.e));
				this.hurt(this.t.getEntities(this, this.e.getBoundingBox().g(1.0D), IEntitySelector.e));
				this.hurt(this.t.getEntities(this, this.cf[1].getBoundingBox().g(1.0D), IEntitySelector.e));
			}

			float f13 = MathHelper.sin(this.getYRot() * 0.017453292F - this.bW * 0.01F);
			float f14 = MathHelper.cos(this.getYRot() * 0.017453292F - this.bW * 0.01F);
			float f15 = -1.0F;

			// update head and neck location
			this.cf[0].setPosition(this.locX() + (double)(f13 * 6.5F * f8), this.locY() + (double)(f15 + f9 * 6.5F), this.locZ() + (double)(-f14 * 6.5F * f8));
			this.cf[1].setPosition(this.locX() + (double)(f13 * 5.5F * f8), this.locY() + (double)(f15 + f9 * 5.5F), this.locZ() + (double)(-f14 * 5.5F * f8));
			double[] adouble = this.a(5, 1.0F);

			// move tail parts
			for(int k = 0; k < 3; ++k) {
				EntityComplexPart entitycomplexpart = this.cf[k + 3];

				double[] adouble1 = this.a(12 + k * 2, 1.0F);
				float f16 = this.getYRot() * 0.017453292F + (float) MathHelper.g(adouble1[0] - adouble[0]) * 0.017453292F;
				float f3 = MathHelper.sin(f16);
				float f4 = MathHelper.cos(f16);
				float f17 = (float)(k + 1) * 2.0F;
				entitycomplexpart.setPosition(this.locX() +(double)(-(f11 * 1.5F + f3 * f17) * f8),
						this.locY() + adouble1[1] - adouble[1] - (double)((f17 + 1.5F) * f9) + 1.5D,
						this.locZ() + (double)((f12 * 1.5F + f4 * f17) * f8));
			}

			if (!this.t.y && plugin.getConfigManager().isDoGriefing()) { //more efficient grieving check
				try {
					checkWalls.invoke(this, this.e.getBoundingBox());
					checkWalls.invoke(this, this.cf[1].getBoundingBox());
					checkWalls.invoke(this, this.cf[2].getBoundingBox());
				} catch (IllegalAccessException | InvocationTargetException ignore){}
			}

			// update positions of children
			for(int k = 0; k < this.cf.length; ++k) {
				this.cf[k].u = avec3d[k].b;
				this.cf[k].v = avec3d[k].c;
				this.cf[k].w = avec3d[k].d;
				this.cf[k].L = avec3d[k].b;
				this.cf[k].M = avec3d[k].c;
				this.cf[k].N = avec3d[k].d;
			}
		}

		if (this.getPassengers().isEmpty() || !(this.getPassengers().get(0) instanceof EntityHuman)){
			didMove = false;
			return;
		}
		EntityHuman rider = (EntityHuman) this.getPassengers().get(0);

		//Jeppa: Add saving of last location and calling the MoveEvent!
		tickEvent((org.bukkit.entity.Player)rider.getBukkitEntity());
	    
		Vector forwardDir = rider.getBukkitEntity().getLocation().getDirection();
		
		if (rider.getBukkitEntity().hasPermission("petdragon.shoot") && jumpField != null){
			try {
				boolean jumped = false;
				if(plugin.getConfigManager().isFireOnJump()){ //Jeppa: only if activated in config...
					jumped = jumpField.getBoolean(rider);
				}
				if ((jumped) && plugin.getConfigManager().getShootCooldown() * 1000 <= (System.currentTimeMillis() - lastShot)){

					Location loc = this.getBukkitEntity().getLocation();
					loc.add(forwardDir.clone().multiply(10).setY(-1));

					loc.getWorld().spawn(loc, DragonFireball.class, (fireball) -> {
						fireball.setDirection(forwardDir);
						fireball.setShooter(this.getEntity());
					});

					lastShot = System.currentTimeMillis();
				}
			} catch (IllegalArgumentException | IllegalAccessException ignore){
			}
		}
		
		this.setYawPitch(180 + rider.getYRot(), rider.getXRot());
		this.setHeadRotation(rider.getXRot());
		
		double speeder = plugin.getConfigManager().getSpeedMultiplier();
		double fwSpeed = rider.bq * speeder;
		double sideSpeed = -1 * rider.bo * speeder;
		
		Vector sideways = forwardDir.clone().crossProduct(new Vector(0,1,0));
    
		Vector total = forwardDir.multiply(fwSpeed).add(sideways.multiply(sideSpeed));
		this.move(EnumMoveType.a, new Vec3D(total.getX(), total.getY(), total.getZ()));

		// keep track of movement for wing hurting
		didMove = total.lengthSquared() > 0.1;
	}


	// called for wings
	private void knockBack(List<Entity> list) {
		double midBodyX = (this.cf[2].getBoundingBox().a + this.cf[2].getBoundingBox().d) / 2.0D;
		double midBodyZ = (this.cf[2].getBoundingBox().c + this.cf[2].getBoundingBox().f) / 2.0D;

		for (Entity entity : list) {
			if (entity instanceof EntityLiving) {
				double disX = entity.locX() - midBodyX;
				double disZ = entity.locZ() - midBodyZ;
				double totalDis = Math.max(disX * disX + disZ * disZ, 0.1D);

				DragonSwoopEvent swoopEvent = new DragonSwoopEvent(this.getEntity(), (LivingEntity) entity.getBukkitEntity(),
						new Vector(disX / totalDis * 4.0D,  0.20000000298023224D, disZ / totalDis * 4.0D));
				swoopEvent.setCancelled(!plugin.getConfigManager().isInteractEntities());
				Bukkit.getPluginManager().callEvent(swoopEvent);

				if (!swoopEvent.isCancelled() && swoopEvent.getTarget() != null){
					EntityLiving nmsEntity = ((CraftLivingEntity) swoopEvent.getTarget()).getHandle();
					nmsEntity.i(swoopEvent.getVelocity().getX(), swoopEvent.getVelocity().getY(), swoopEvent.getVelocity().getZ());
				}

				if (didMove && ((EntityLiving) entity).dH() < entity.R - 2) {
					entity.damageEntity(DamageSource.mobAttack(this), plugin.getConfigManager().getWingDamage());
					this.a(this, entity);
				}
			}
		}

	}

	// called for head
	private void hurt(List<Entity> list) {
		for (Entity entity : list) {
			if (entity instanceof EntityLiving) {
				entity.damageEntity(DamageSource.mobAttack(this), plugin.getConfigManager().getHeadDamage());
				this.a(this, entity);
			}
		}
	}

	
	@Override
	public void dB(){
		++this.bV;
		
		if (!plugin.getConfigManager().isDeathAnimation()){
			this.a(RemovalReason.a);
			return;
		}
		// make players nearby aware of his death 
		
		if (this.bV == 1 && !this.isSilent()) {

			int viewDistance = (this.t).getCraftServer().getViewDistance() * 16;
			int deathSoundRadius=this.t.spigotConfig.dragonDeathSoundRadius;

/*			Iterator<EntityPlayer> var5 = this.t.getMinecraftServer().getPlayerList().j.iterator();

			label59 : while (true) {
				EntityPlayer player;
				double deltaX;
				double deltaZ;
				double distanceSquared;
				do {
					if (!var5.hasNext()) {
						break label59;
					}

					player = var5.next();
					deltaX = this.locX() - player.locX();
					deltaZ = this.locZ() - player.locZ();
					distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
				} while (this.t.spigotConfig.dragonDeathSoundRadius > 0
						&& distanceSquared > (double) (this.t.spigotConfig.dragonDeathSoundRadius * this.t.spigotConfig.dragonDeathSoundRadius));

				if (distanceSquared > (double) (viewDistance * viewDistance)) {
					double deltaLength = Math.sqrt(distanceSquared);
					double relativeX = player.locX() + deltaX / deltaLength
							* (double) viewDistance;
					double relativeZ = player.locZ() + deltaZ / deltaLength
							* (double) viewDistance;
					player.b
							.sendPacket(new PacketPlayOutWorldEvent(1028,
									new BlockPosition((int) relativeX,
											(int) this.locY(),
											(int) relativeZ), 0, true));
				} else {
					player.b
							.sendPacket(new PacketPlayOutWorldEvent(1028,
									new BlockPosition((int) this.locX(),
											(int) this.locY(), (int) this
													.locZ()), 0, true));
				}
			}*/
		      //Jeppa-Version with Player
		      for (org.bukkit.entity.Player player:Bukkit.getServer().getOnlinePlayers()) {
		    	  Location loc = this.getBukkitEntity().getLocation();
		    	  double distance = player.getLocation().distance(loc);
		    	  if (deathSoundRadius > 0 && distance > deathSoundRadius)continue;
		    	  if (!player.getWorld().getName().equals(this.getBukkitEntity().getWorld().getName()))continue;
		    	  if (distance > viewDistance) {
		            PlayDragonSound(player, loc, (float)(distance/16.1)+1);
				} else {
					PlayDragonSound(player, player.getLocation(), 0.9F);
				}
		      } 
		}
		
		
		if (this.bV <= 100) {
			// particle stuff
			float f = (this.Q.nextFloat() - 0.5F) * 8.0F;
			float f1 = (this.Q.nextFloat() - 0.5F) * 4.0F;
			float f2 = (this.Q.nextFloat() - 0.5F) * 8.0F;
			this.t.addParticle(Particles.x, this.locX()
					+ (double) f, this.locY() + 2.0D + (double) f1, this.locZ()
					+ (double) f2, 0.0D, 0.0D, 0.0D);
		}
		else {
			this.a(RemovalReason.a);
		}
		
	}
	private void PlayDragonSound(org.bukkit.entity.Player player, Location dragonPos, float distance) {
		   Sound sound=null;
		   try{
			   sound = (org.bukkit.Sound.valueOf("ENTITY_ENDER_DRAGON_DEATH")); //1.13+
		   }catch (IllegalArgumentException e3){
			   //No Sound ????
		   }
		   if (sound != null) player.getWorld().playSound(dragonPos, sound, distance, 1.0F);
	}
}
