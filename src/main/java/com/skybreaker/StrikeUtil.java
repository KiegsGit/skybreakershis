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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Sound, networking, damage and (safety-limited) terrain helpers shared by all strikes. */
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

	// ---------- damage ----------
	/** Damages every living entity inside {@code radius} of {@code c}; damage falls off to 20% at the edge. */
	public static void blast(ServerLevel level, Vec3 c, double radius, float damage, UUID ownerId, boolean knockback) {
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
			if (knockback && e.isAlive()) {
				Vec3 dir = mid.subtract(c);
				double len = Math.max(0.001, dir.length());
				double power = 2.2 * Math.max(0.0, 1.0 - dist / radius);
				e.push(dir.x / len * power, 0.35 + 0.6 * power, dir.z / len * power);
			}
		}
	}

	// ---------- terrain (all of it goes through here, so the safety rules live in one place) ----------
	/** Removes one block if the safety rules allow it. */
	public static boolean tryRemove(ServerLevel level, BlockPos pos) {
		SkybreakerConfig cfg = SkybreakerConfig.get();
		BlockState st = level.getBlockState(pos);
		if (st.isAir() || !st.getFluidState().isEmpty()) {
			return false;
		}
		if (cfg.protectBlockEntities && st.hasBlockEntity()) {
			return false;
		}
		float hardness = st.getDestroySpeed(level, pos);
		if (hardness < 0 || hardness > cfg.maxBlockHardness) {
			return false;
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		return true;
	}

	/** Carves a rough sphere, closest blocks first, never removing more than {@code budget} blocks. Returns blocks removed. */
	public static int crater(ServerLevel level, Vec3 center, double radius, int budget) {
		SkybreakerConfig cfg = SkybreakerConfig.get();
		if (!cfg.terrainDestruction || budget <= 0) {
			return 0;
		}
		budget = Math.min(budget, cfg.maxBlocksPerStrike);
		radius = Math.min(radius, 16.0);
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
		int removed = 0;
		for (BlockPos p : candidates) {
			if (removed >= budget) {
				break;
			}
			if (tryRemove(level, p)) {
				removed++;
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
