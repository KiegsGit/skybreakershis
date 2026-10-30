package com.skybreaker;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Sound, networking, damage, spectacle and (safety-limited) terrain helpers shared by all strikes. */
public final class StrikeUtil {

	// ---------- sound (overloads so both SoundEvent and Holder<SoundEvent> constants work) ----------
	public static void sound(ServerLevel level, Vec3 p, SoundEvent s, float volume, float pitch) {
		level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, volume, pitch);
	}

	public static void sound(ServerLevel level, Vec3 p, Holder<SoundEvent> s, float volume, float pitch) {
		sound(level, p, s.value(), volume, pitch);
	}

	// ---------- networking ----------
	public static void broadcast(ServerLevel level, Vec3 center, FxPayload payload) {
		double range = SkybreakerConfig.get().effectBroadcastRange;
		double r2 = range * range;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(center.x, center.y, center.z) <= r2) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** Camera shake for everyone within the shake radius (falls off with distance on the client). */
	public static void shake(ServerLevel level, Vec3 center, int ticks, float intensity) {
		SkybreakerConfig cfg = SkybreakerConfig.get();
		if (!cfg.cameraShake) {
			return;
		}
		double r = cfg.shakeRadius;
		FxPayload payload = new FxPayload(FxPayload.SHAKE, center.x, center.y, center.z, ticks, (int) r, intensity, new float[0]);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(center.x, center.y, center.z) <= r * r) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** Strong short shake only for one player (weapon recoil). */
	public static void recoil(ServerPlayer player, int ticks, float intensity) {
		if (player == null || !SkybreakerConfig.get().cameraShake) {
			return;
		}
		ServerPlayNetworking.send(player, new FxPayload(FxPayload.SHAKE, player.getX(), player.getY(), player.getZ(),
			ticks, 8, intensity, new float[0]));
	}

	/** White screen flash, brighter the closer a player is. */
	public static void flash(ServerLevel level, Vec3 center, float maxIntensity, int ticks) {
		if (!SkybreakerConfig.get().cinematic) {
			return;
		}
		double range = 220.0;
		for (ServerPlayer p : level.players()) {
			double d = Math.sqrt(p.distanceToSqr(center.x, center.y, center.z));
			if (d > range) {
				continue;
			}
			float k = (float) Math.pow(1.0 - d / range, 0.7);
			float intensity = maxIntensity * k;
			if (intensity < 0.05f) {
				continue;
			}
			ServerPlayNetworking.send(p, new FxPayload(FxPayload.FLASH, center.x, center.y, center.z, ticks, 0, intensity, new float[0]));
		}
	}

	// ---------- spectacle ----------
	/** A real vanilla lightning bolt that does no damage and starts no fire (light, sound and sky flash only). */
	public static void lightning(ServerLevel level, double x, double y, double z) {
		if (!SkybreakerConfig.get().lightning) {
			return;
		}
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		bolt.snapTo(x, y, z);
		bolt.setVisualOnly(true);
		level.addFreshEntity(bolt);
	}

	/** Lightning on the ground at a random spot of a ring/disc around {@code c}. */
	public static void lightningNear(ServerLevel level, Vec3 c, double minR, double maxR) {
		RandomSource rnd = level.getRandom();
		double ang = rnd.nextDouble() * Math.PI * 2.0;
		double rr = minR + rnd.nextDouble() * (maxR - minR);
		double x = c.x + Math.cos(ang) * rr;
		double z = c.z + Math.sin(ang) * rr;
		double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		if (Math.abs(y - c.y) > 24) {
			y = c.y;
		}
		lightning(level, x, y, z);
	}

	// ---------- damage ----------
	/** Damages every living entity inside {@code radius}; damage falls off to 20% at the edge. */
	public static void blast(ServerLevel level, Vec3 c, double radius, float damage, UUID ownerId, boolean knockback, float igniteSeconds) {
		SkybreakerConfig cfg = SkybreakerConfig.get();
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		DamageSource source = owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().generic();
		AABB box = new AABB(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (e.isSpectator()) {
				continue;
			}
			if (e instanceof ServerPlayer sp) {
				if (!cfg.damagePlayers) {
					continue;
				}
				if (!cfg.damageOwner && sp.getUUID().equals(ownerId)) {
					continue;
				}
			}
			Vec3 mid = e.getBoundingBox().getCenter();
			double dist = mid.distanceTo(c);
			if (dist > radius) {
				continue;
			}
			float amount = (float) (damage * Math.max(0.2, 1.0 - dist / radius));
			e.hurtServer(level, source, amount);
			if (e.isAlive()) {
				if (igniteSeconds > 0) {
					e.igniteForSeconds(igniteSeconds);
				}
				if (knockback) {
					Vec3 dir = mid.subtract(c);
					double len = Math.max(0.001, dir.length());
					double power = 3.5 * Math.max(0.0, 1.0 - dist / radius);
					e.push(dir.x / len * power, 0.5 + 0.9 * power, dir.z / len * power);
				}
			}
		}
	}

	// ---------- terrain (all of it goes through here, so the safety rules live in one place) ----------
	/** Result of {@link #removeBlock}: 0 = untouched, 1 = removed, 2 = removed and flung as debris. */
	private static int removeBlock(ServerLevel level, BlockPos pos, Vec3 flingFrom, double flingChance) {
		SkybreakerConfig cfg = SkybreakerConfig.get();
		BlockState st = level.getBlockState(pos);
		if (st.isAir() || !st.getFluidState().isEmpty()) {
			return 0;
		}
		if (cfg.protectBlockEntities && st.hasBlockEntity()) {
			return 0;
		}
		float hardness = st.getDestroySpeed(level, pos);
		if (hardness < 0 || hardness > cfg.maxBlockHardness) {
			return 0;
		}
		RandomSource rnd = level.getRandom();
		if (flingFrom != null && flingChance > 0 && rnd.nextDouble() < flingChance) {
			FallingBlockEntity debris = FallingBlockEntity.fall(level, pos, st); // removes the block and spawns the entity
			debris.dropItem = false;
			debris.setHurtsEntities(2.0f, 40);
			Vec3 dir = new Vec3(pos.getX() + 0.5 - flingFrom.x, pos.getY() + 0.5 - flingFrom.y, pos.getZ() + 0.5 - flingFrom.z);
			double len = Math.max(0.5, dir.length());
			double speed = 0.5 + rnd.nextDouble() * 1.4;
			debris.setDeltaMovement(dir.x / len * speed, 0.5 + rnd.nextDouble() * 1.1, dir.z / len * speed);
			return 2;
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		return 1;
	}

	/** Removes one block if the safety rules allow it (used by the laser drill). */
	public static boolean tryRemove(ServerLevel level, BlockPos pos) {
		return removeBlock(level, pos, null, 0) > 0;
	}

	/**
	 * Carves a rough sphere, closest blocks first, never removing more than {@code budget} blocks.
	 * Up to {@code debrisMax} of them are flung outward as real falling blocks. Returns blocks removed.
	 */
	public static int crater(ServerLevel level, Vec3 center, double radius, int budget, int debrisMax) {
		SkybreakerConfig cfg = SkybreakerConfig.get();
		if (!cfg.terrainDestruction || budget <= 0) {
			return 0;
		}
		budget = Math.min(budget, cfg.maxBlocksPerStrike);
		radius = Math.min(radius, 24.0);
		int r = (int) Math.ceil(radius);
		BlockPos c = BlockPos.containing(center);
		RandomSource rnd = level.getRandom();
		List<BlockPos> candidates = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					double lim = radius * (0.85 + 0.3 * rnd.nextDouble());
					if (dx * dx + dy * dy + dz * dz > lim * lim) {
						continue;
					}
					candidates.add(c.offset(dx, dy, dz));
				}
			}
		}
		candidates.sort((p, q) -> Double.compare(dist2(p, center), dist2(q, center)));
		int maxDebris = cfg.debris ? Math.min(debrisMax, cfg.debrisCount) : 0;
		double chance = maxDebris <= 0 ? 0 : Math.min(0.5, 1.6 * maxDebris / Math.max(1.0, candidates.size()));
		int removed = 0;
		int flung = 0;
		for (BlockPos p : candidates) {
			if (removed >= budget) {
				break;
			}
			boolean allowFling = flung < maxDebris;
			int res = removeBlock(level, p, allowFling ? center : null, allowFling ? chance : 0);
			if (res > 0) {
				removed++;
				if (res == 2) {
					flung++;
				}
			}
		}
		return removed;
	}

	private static double dist2(BlockPos p, Vec3 c) {
		double dx = p.getX() + 0.5 - c.x;
		double dy = p.getY() + 0.5 - c.y;
		double dz = p.getZ() + 0.5 - c.z;
		return dx * dx + dy * dy + dz * dz;
	}

	private StrikeUtil() {
	}
}
