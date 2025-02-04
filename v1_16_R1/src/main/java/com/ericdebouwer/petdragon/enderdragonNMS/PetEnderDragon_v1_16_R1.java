package com.ericdebouwer.petdragon.enderdragonNMS;

import com.ericdebouwer.petdragon.PetDragon;
import com.ericdebouwer.petdragon.api.DragonSwoopEvent;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.types.Type;
import net.minecraft.server.v1_16_R1.AxisAlignedBB;
import net.minecraft.server.v1_16_R1.BlockPosition;
import net.minecraft.server.v1_16_R1.DamageSource;
import net.minecraft.server.v1_16_R1.DataConverterRegistry;
import net.minecraft.server.v1_16_R1.DataConverterTypes;
import net.minecraft.server.v1_16_R1.Entity;
import net.minecraft.server.v1_16_R1.EntityComplexPart;
import net.minecraft.server.v1_16_R1.EntityEnderDragon;
import net.minecraft.server.v1_16_R1.EntityHuman;
import net.minecraft.server.v1_16_R1.EntityLiving;
import net.minecraft.server.v1_16_R1.EntityPlayer;
import net.minecraft.server.v1_16_R1.EntityTypes;
import net.minecraft.server.v1_16_R1.EnumCreatureType;
import net.minecraft.server.v1_16_R1.EnumMoveType;
import net.minecraft.server.v1_16_R1.IEntitySelector;
import net.minecraft.server.v1_16_R1.IRegistry;
import net.minecraft.server.v1_16_R1.MathHelper;
import net.minecraft.server.v1_16_R1.MinecraftKey;
import net.minecraft.server.v1_16_R1.MinecraftServer;
import net.minecraft.server.v1_16_R1.NBTTagCompound;
import net.minecraft.server.v1_16_R1.PacketPlayOutWorldEvent;
import net.minecraft.server.v1_16_R1.Particles;
import net.minecraft.server.v1_16_R1.SharedConstants;
import net.minecraft.server.v1_16_R1.SoundEffects;
import net.minecraft.server.v1_16_R1.Vec3D;
import net.minecraft.server.v1_16_R1.World;
import net.minecraft.server.v1_16_R1.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R1.entity.CraftEnderDragon;
import org.bukkit.craftbukkit.v1_16_R1.entity.CraftLivingEntity;
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

public class PetEnderDragon_v1_16_R1 extends EntityEnderDragon implements PetEnderDragon {

	@Getter @Setter
	private Location lastLocation;

	static Field jumpField;
	static Method checkWalls;
	static Method checkCrystals;
	static {
		MinecraftKey mcKey = new MinecraftKey(PetEnderDragon.ENTITY_ID);
		try {
			if (!IRegistry.ENTITY_TYPE.getOptional(mcKey).isPresent()) {
				@SuppressWarnings("unchecked")
				Map<String, Type<?>> types = (Map<String, Type<?>>) DataConverterRegistry.a().getSchema(
						DataFixUtils.makeKey(SharedConstants.getGameVersion().getWorldVersion()))
						.findChoiceType(DataConverterTypes.ENTITY).types();
				types.put(mcKey.toString(), types.get(IRegistry.ENTITY_TYPE.getKey(EntityTypes.ENDER_DRAGON).toString()));
				IRegistry.a(IRegistry.ENTITY_TYPE, PetEnderDragon.ENTITY_ID,
						EntityTypes.Builder.a(PetEnderDragon_v1_16_R1::new, EnumCreatureType.MONSTER).a().a(PetEnderDragon.ENTITY_ID));
			}
			jumpField = EntityLiving.class.getDeclaredField("jumping");
			jumpField.setAccessible(true);
			checkWalls = EntityEnderDragon.class.getDeclaredMethod("b", AxisAlignedBB.class);
			checkWalls.setAccessible(true);
			checkCrystals = EntityEnderDragon.class.getDeclaredMethod("eO");
			checkCrystals.setAccessible(true);
		} catch (NoSuchFieldException | NoSuchMethodException ignore) {
		}
	}

	private final PetDragon plugin;
	private long lastShot;
	private boolean didMove;
	int growlTicks = 100;

	public PetEnderDragon_v1_16_R1(EntityTypes<? extends EntityEnderDragon> entitytypes, World world) {
		this(world.getWorld());
	}
	
	public PetEnderDragon_v1_16_R1(org.bukkit.World world) {
		super(EntityTypes.ENDER_DRAGON, ((CraftWorld)world).getHandle());
		this.plugin = JavaPlugin.getPlugin(PetDragon.class);
		
		this.setupDefault();
		this.getBukkitEntity().setSilent(plugin.getConfigManager().isSilent());
		this.noclip = plugin.getConfigManager().isFlyThroughBlocks();
	}
	
	@Override
	public void copyFrom(EnderDragon dragon) {
		EntityEnderDragon other = ((CraftEnderDragon) dragon).getHandle();
		NBTTagCompound nbt = other.save(new NBTTagCompound());
		nbt.remove("Passengers");
		nbt.remove("WorldUUIDLeast");
		nbt.remove("WorldUUIDMost");
		nbt.remove("UUID");
		nbt.setBoolean("Silent", plugin.getConfigManager().isSilent());
		this.load(nbt);
	}
	
	@Override
	public void spawn(Vector location) {
		this.setPosition(location.getX(), location.getY(), location.getZ());
		this.world.addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
		plugin.getFactory().saveSpawnLocation(this);
	}
	
	@Override
	public EnderDragon getEntity() {
		return (EnderDragon) this.getBukkitEntity();
	}
	
	@Override
	protected boolean cS() { //affected by fluids
		return false;
	};
	
	@Override
	public boolean bp() { //ridable in water
		return true;
	};
		
	@Override
	public boolean d(NBTTagCompound nbttagcompound) {
		boolean result = super.d(nbttagcompound);
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
		if (entitycomplexpart != this.bv) {
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

		if (this.world.isClientSide) {
			this.setHealth(this.getHealth());
			if (!this.isSilent()) { //noises
				float f = MathHelper.cos(this.bx * 6.2831855F);
				float f1 = MathHelper.cos(this.bw * 6.2831855F);
				if (f1 <= -0.3F && f >= -0.3F) {
					this.world.a(this.locX(), this.locY(), this.locZ(), SoundEffects.ENTITY_ENDER_DRAGON_FLAP, this.getSoundCategory(), 5.0F, 0.8F + this.random.nextFloat() * 0.3F, false);
				}

				if (--this.growlTicks < 0) { //now also growl if stationary
					this.world.a(this.locX(), this.locY(), this.locZ(), SoundEffects.ENTITY_ENDER_DRAGON_GROWL, this.getSoundCategory(), 2.5F, 0.8F + this.random.nextFloat() * 0.3F, false);
					this.growlTicks = 200 + this.random.nextInt(200);
				}
			}
		}

		this.bw = this.bx; //flaptimes

		if (this.dk()) { //dead
			float f = (this.random.nextFloat() - 0.5F) * 8.0F;
			float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
			float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;
			this.world.addParticle(Particles.EXPLOSION, this.locX() + (double)f, this.locY() + 2.0D + (double)f1, this.locZ() + (double)f2, 0.0D, 0.0D, 0.0D);
		} else {
			try {
				checkCrystals.invoke(this); //crystals
			} catch (InvocationTargetException | IllegalAccessException ignore) {}

			this.bx += 0.1F;

			this.yaw = MathHelper.g(this.yaw);

			// update position information
			if (this.d < 0) {
				for(int i = 0; i < this.c.length; ++i) {
					this.c[i][0] = this.yaw;
					this.c[i][1] = this.locY();
				}
			}

			if (++this.d == this.c.length) {
				this.d = 0;
			}

			this.c[this.d][0] = this.yaw;
			this.c[this.d][1] = this.locY();
			this.aH = this.yaw;

			//store parts locations
			Vec3D[] avec3d = new Vec3D[this.children.length];

			for(int j = 0; j < this.children.length; ++j) {
				avec3d[j] = new Vec3D(this.children[j].locX(), this.children[j].locY(), this.children[j].locZ());
			}

			float f7 = (float)(this.a(5, 1.0F)[1] - this.a(10, 1.0F)[1]) * 10.0F * 0.017453292F;
			float f8 = MathHelper.cos(f7);
			float f9 = MathHelper.sin(f7);
			float f10 = this.yaw * 0.017453292F;
			float f11 = MathHelper.sin(f10);
			float f12 = MathHelper.cos(f10);

			// move body and wings
			this.children[2].setPosition(this.locX() + (double)(f11 * 0.5F) , this.locY(), this.locZ() + (double)(-f12 * 0.5F));
			this.children[6].setPosition(this.locX() + (double)(f12 * 4.5F) , this.locY() + 2.0D, this.locZ() + (double)(f11 * 4.5F));
			this.children[7].setPosition(this.locX() + (double)(f12 * -4.5F) , this.locY() + 2.0D, this.locZ() + (double)(f11 * -4.5F));

			// do knockback, wing hurt and head/neck attack
			if (!this.world.isClientSide && this.hurtTicks == 0) {

				this.knockBack(this.world.getEntities(this, this.children[6].getBoundingBox().grow(4.0D, 2.0D, 4.0D).d(0.0D, -2.0D, 0.0D), IEntitySelector.e));
				this.knockBack(this.world.getEntities(this, this.children[7].getBoundingBox().grow(4.0D, 2.0D, 4.0D).d(0.0D, -2.0D, 0.0D), IEntitySelector.e));
				this.hurt(this.world.getEntities(this, this.bv.getBoundingBox().g(1.0D), IEntitySelector.e));
				this.hurt(this.world.getEntities(this, this.children[1].getBoundingBox().g(1.0D), IEntitySelector.e));
			}

			float f13 = MathHelper.sin(this.yaw * 0.017453292F - this.bA * 0.01F);
			float f14 = MathHelper.cos(this.yaw * 0.017453292F - this.bA * 0.01F);
			float f15 = -1.0F;

			// update head and neck location
			this.children[0].setPosition(this.locX() + (double)(f13 * 6.5F * f8), this.locY() + (double)(f15 + f9 * 6.5F), this.locZ() + (double)(-f14 * 6.5F * f8));
			this.children[1].setPosition(this.locX() + (double)(f13 * 5.5F * f8), this.locY() + (double)(f15 + f9 * 5.5F), this.locZ() + (double)(-f14 * 5.5F * f8));
			double[] adouble = this.a(5, 1.0F);

			// move tail parts
			for(int k = 0; k < 3; ++k) {
				EntityComplexPart entitycomplexpart = this.children[k + 3];

				double[] adouble1 = this.a(12 + k * 2, 1.0F);
				float f16 = this.yaw * 0.017453292F + (float) MathHelper.g(adouble1[0] - adouble[0]) * 0.017453292F;
				float f17 = MathHelper.sin(f16);
				float f18 = MathHelper.cos(f16);
				float f4 = (float)(k + 1) * 2.0F;
				entitycomplexpart.setPosition(this.locX() + (double)(-(f11 * 1.5F + f17 * f4) * f8),
						this.locY() + adouble1[1] - adouble[1] - (double)((f4 + 1.5F) * f9) + 1.5D, this.locZ() +  (double)((f12 * 1.5F + f18 * f4) * f8));
			}

			if (!this.world.isClientSide && plugin.getConfigManager().isDoGriefing()) { //more efficient grieving check
				try {
					checkWalls.invoke(this, this.bv.getBoundingBox());
					checkWalls.invoke(this, this.children[1].getBoundingBox());
					checkWalls.invoke(this, this.children[2].getBoundingBox());
				} catch (IllegalAccessException | InvocationTargetException ignore){}
			}

			// update positions of children
			for(int k = 0; k < this.children.length; ++k) {
				this.children[k].lastX = avec3d[k].x;
				this.children[k].lastY = avec3d[k].y;
				this.children[k].lastZ = avec3d[k].z;
				this.children[k].D = avec3d[k].x;
				this.children[k].E = avec3d[k].y;
				this.children[k].F = avec3d[k].z;
			}
		}
		
		if (this.passengers.isEmpty() || !(this.passengers.get(0) instanceof EntityHuman)){
			this.didMove = false;
			return;
		}
		EntityHuman rider = (EntityHuman) this.passengers.get(0);
		
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
		
		this.setYawPitch(180 + rider.yaw, rider.pitch);
		this.setHeadRotation(rider.pitch);
		
		double speeder = plugin.getConfigManager().getSpeedMultiplier();
		double fwSpeed = rider.ba * speeder;
		double sideSpeed = -1 * rider.aY * speeder;
		
		Vector sideways = forwardDir.clone().crossProduct(new Vector(0,1,0));

		Vector total = forwardDir.multiply(fwSpeed).add(sideways.multiply(sideSpeed));
		this.move(EnumMoveType.SELF, new Vec3D(total.getX(), total.getY(), total.getZ()));

		// keep track of movement for wing hurting
		didMove = total.lengthSquared() > 0.1;
	}

	// called for wings
	private void knockBack(List<Entity> list) {
		double midBodyX = (this.children[2].getBoundingBox().minX + this.children[2].getBoundingBox().maxX) / 2.0D;
		double midBodyZ = (this.children[2].getBoundingBox().minZ + this.children[2].getBoundingBox().maxZ) / 2.0D;

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
					nmsEntity.h(swoopEvent.getVelocity().getX(), swoopEvent.getVelocity().getY(), swoopEvent.getVelocity().getZ());
				}

				if (didMove && ((EntityLiving) entity).cZ() < entity.ticksLived - 2) {
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
	public void cT(){
		
		++this.deathAnimationTicks;
		
		if (!plugin.getConfigManager().isDeathAnimation()){
			this.die();
			return;
		}
		
		// make players nearby aware of his death 
		
		if (this.deathAnimationTicks == 1 && !this.isSilent()) {
			int viewDistance = ((WorldServer) this.world).getServer()
					.getViewDistance() * 16;
			@SuppressWarnings("deprecation")
			Iterator<EntityPlayer> var5 = MinecraftServer.getServer().getPlayerList().players
					.iterator();

			label59 : while (true) {
				EntityPlayer player;
				double deltaX;
				double deltaZ;
				double distanceSquared;
				do {
					if (!var5.hasNext()) {
						break label59;
					}

					player = (EntityPlayer) var5.next();
					deltaX = this.locX() - player.locX();
					deltaZ = this.locZ() - player.locZ();
					distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
				} while (this.world.spigotConfig.dragonDeathSoundRadius > 0
						&& distanceSquared > (double) (this.world.spigotConfig.dragonDeathSoundRadius * this.world.spigotConfig.dragonDeathSoundRadius));

				if (distanceSquared > (double) (viewDistance * viewDistance)) {
					double deltaLength = Math.sqrt(distanceSquared);
					double relativeX = player.locX() + deltaX / deltaLength
							* (double) viewDistance;
					double relativeZ = player.locZ() + deltaZ / deltaLength
							* (double) viewDistance;
					player.playerConnection
							.sendPacket(new PacketPlayOutWorldEvent(1028,
									new BlockPosition((int) relativeX,
											(int) this.locY(),
											(int) relativeZ), 0, true));
				} else {
					player.playerConnection
							.sendPacket(new PacketPlayOutWorldEvent(1028,
									new BlockPosition((int) this.locX(),
											(int) this.locY(), (int) this
													.locZ()), 0, true));
				}
			}
		}
		
		
		if (this.deathAnimationTicks <= 100) {
			// particle stuff
			float f = (this.random.nextFloat() - 0.5F) * 8.0F;
			float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
			float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;
			this.world.addParticle(Particles.EXPLOSION_EMITTER, this.locX()
					+ (double) f, this.locY() + 2.0D + (double) f1, this.locZ()
					+ (double) f2, 0.0D, 0.0D, 0.0D);
		}
		else {
			this.die();
		}
		
	}
}
