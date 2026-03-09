package meldexun.parallelai.mixin;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.collect.ImmutableSetMultimap;

import meldexun.parallelai.ParallelAI;
import meldexun.parallelai.ParallelAI.TickStage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.ForgeEventFactory;

@Mixin(World.class)
public abstract class WorldMixin implements Supplier<TickStage> {

	@Shadow
	@Final
	public List<Entity> loadedEntityList;
	@Shadow
	@Final
	public Profiler profiler;
	@Shadow
	@Final
	public boolean isRemote;
	@Unique
	public TickStage tickStage = TickStage.ALL;

	@Override
	public TickStage get() {
		return tickStage;
	}

	@Redirect(method = "updateEntities", at = @At(value = "INVOKE", target = "size"))
	private int cancelEntityTicking(List<Entity> loadedEntityList) {
		return 0;
	}

	@Inject(method = "updateEntities", at = @At(value = "INVOKE_STRING", target = "endStartSection(Ljava/lang/String;)V", args = "ldc=regular", shift = Shift.AFTER))
	private void tickEntitiesParallel(CallbackInfo info) {
		// handle dismounting synchronously
		for (Entity entity : this.loadedEntityList) {
			Entity ridingEntity = entity.getRidingEntity();

			if (ridingEntity != null) {
				if (!ridingEntity.isDead && ridingEntity.isPassenger(entity)) {
					continue;
				}

				entity.dismountRidingEntity();
			}
		}

		// tick entities
		tickStage = TickStage.PRE;
		List<Entity> tickingEntities = IntStream.range(0, this.loadedEntityList.size())
				.mapToObj(this.loadedEntityList::get)
				.filter(e -> !e.isDead)
				.filter(e -> !(e instanceof EntityPlayerMP))
				.filter(entity -> {
					final boolean forceUpdate = true; // same as World.updateEntity

					if (!(entity instanceof EntityPlayer)) {
						int j2 = MathHelper.floor(entity.posX);
						int k2 = MathHelper.floor(entity.posZ);

						boolean isForced = !this.isRemote && this.getPersistentChunks().containsKey(new ChunkPos(j2 >> 4, k2 >> 4));
						int range = isForced ? 0 : 32;
						boolean canUpdate = !forceUpdate || this.isAreaLoaded(j2 - range, 0, k2 - range, j2 + range, 0, k2 + range, true);
						if (!canUpdate) {
							canUpdate = ForgeEventFactory.canEntityUpdate(entity);
						}

						if (!canUpdate) {
							return false;
						}
					}

					entity.lastTickPosX = entity.posX;
					entity.lastTickPosY = entity.posY;
					entity.lastTickPosZ = entity.posZ;
					entity.prevRotationYaw = entity.rotationYaw;
					entity.prevRotationPitch = entity.rotationPitch;

					if (forceUpdate && entity.addedToChunk) {
						++entity.ticksExisted;

						if (entity.isRiding()) {
							entity.updateRidden();
						} else {
							if (!entity.updateBlocked) {
								entity.onUpdate();
							}
						}
					}
					return true;
				})
				.collect(Collectors.toList());
		tickStage = TickStage.AI;
		if (!isRemote)
		tickingEntities.parallelStream().forEach(entity -> {
			if (entity instanceof EntityLiving) {
				EntityLiving livingEntity = (EntityLiving) entity;
				livingEntity.targetTasks.onUpdateTasks();
				livingEntity.tasks.onUpdateTasks();
				livingEntity.getNavigator().onUpdateNavigation();
			}
		});
		tickStage = TickStage.POST;
		tickingEntities.forEach(entity -> {
			final boolean forceUpdate = true; // same as World.updateEntity

			// TODO somehow execute rest of tick loop...
			if (forceUpdate && entity.addedToChunk) {
//				++entity.ticksExisted;

				if (entity.isRiding()) {
					entity.updateRidden();
				} else {
					if (!entity.updateBlocked) {
						entity.onUpdate();
					}
				}
			}

			this.profiler.startSection("chunkCheck");

			if (Double.isNaN(entity.posX) || Double.isInfinite(entity.posX)) {
				entity.posX = entity.lastTickPosX;
			}

			if (Double.isNaN(entity.posY) || Double.isInfinite(entity.posY)) {
				entity.posY = entity.lastTickPosY;
			}

			if (Double.isNaN(entity.posZ) || Double.isInfinite(entity.posZ)) {
				entity.posZ = entity.lastTickPosZ;
			}

			if (Double.isNaN(entity.rotationPitch) || Double.isInfinite(entity.rotationPitch)) {
				entity.rotationPitch = entity.prevRotationPitch;
			}

			if (Double.isNaN(entity.rotationYaw) || Double.isInfinite(entity.rotationYaw)) {
				entity.rotationYaw = entity.prevRotationYaw;
			}

			int i3 = MathHelper.floor(entity.posX / 16.0D);
			int j3 = MathHelper.floor(entity.posY / 16.0D);
			int k3 = MathHelper.floor(entity.posZ / 16.0D);

			if (!entity.addedToChunk || entity.chunkCoordX != i3 || entity.chunkCoordY != j3 || entity.chunkCoordZ != k3) {
				if (entity.addedToChunk && this.isChunkLoaded(entity.chunkCoordX, entity.chunkCoordZ, true)) {
					this.getChunk(entity.chunkCoordX, entity.chunkCoordZ).removeEntityAtIndex(entity, entity.chunkCoordY);
				}

				if (!entity.setPositionNonDirty() && !this.isChunkLoaded(i3, k3, true)) {
					entity.addedToChunk = false;
				} else {
					this.getChunk(i3, k3).addEntity(entity);
				}
			}

			this.profiler.endSection();

			if (forceUpdate && entity.addedToChunk) {
				for (Entity entity4 : entity.getPassengers()) {
					if (!entity4.isDead && entity4.getRidingEntity() == entity) {
						this.updateEntity(entity4);
					} else {
						entity4.dismountRidingEntity();
					}
				}
			}
		});
		tickStage = TickStage.ALL;

		// handle removing synchronously
		this.loadedEntityList.removeIf(entity -> {
			this.profiler.startSection("remove");
			try {
				if (entity.isDead) {
					int x = entity.chunkCoordX;
					int z = entity.chunkCoordZ;

					if (entity.addedToChunk && this.isChunkLoaded(x, z, true)) {
						this.getChunk(x, z).removeEntity(entity);
					}

					this.onEntityRemoved(entity);
					return true;
				} else {
					return false;
				}
			} finally {
				this.profiler.endSection();
			}
		});
	}

	@Shadow
	public abstract ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> getPersistentChunks();

	@Shadow
	public abstract boolean isAreaLoaded(int xStart, int yStart, int zStart, int xEnd, int yEnd, int zEnd, boolean allowEmpty);

	@Shadow
	public abstract void updateEntity(Entity ent);

	@Shadow
	public abstract boolean isChunkLoaded(int x, int z, boolean allowEmpty);

	@Shadow
	public abstract Chunk getChunk(int chunkX, int chunkZ);

	@Shadow
	public abstract void onEntityRemoved(Entity entityIn);

}
