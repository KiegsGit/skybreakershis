package com.skybreaker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class LaserStrike extends Strike {
	private final int countdown;
	private final int duration;
	private final BlockPos base;
	private int bored = 0;
	private int removedTotal = 0;

	public LaserStrike(ServerLevel level, UUID owner, Vec3 pos) {
		super(level, owner, pos);
		this.countdown = SkybreakerConfig.ticks(cfg.laserCountdownSeconds);
		this.duration = SkybreakerConfig.ticks(cfg.laserDurationSeconds);
		this.base = BlockPos.containing(pos.x, pos.y - 0.05, pos.z);
	}

	@Override
	public void start() {
		StrikeUtil.broadcast(level, pos, new FxPayload(FxPayload.LASER, pos.x, pos.y, pos.z,
			countdown, duration, (float) cfg.laserBeamRadius, new float[0]));
		StrikeUtil.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 4.0f, 1.3f);
		StrikeUtil.sound(level, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, 4.0f, 1.0f);
		countdownTick(countdown, "Satellite Laser");
	}

	@Override
	public boolean tick() {
		age++;
		countdownTick(countdown, "Satellite Laser");
		if (age < countdown) {
			return false;
		}
		int t = age - countdown;
		if (t == 0) {
			StrikeUtil.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0f, 1.8f);
			StrikeUtil.shake(level, pos, duration, 0.6f);
		}
		if (t % 40 == 0) {
			StrikeUtil.sound(level, pos, SoundEvents.BEACON_AMBIENT, 4.0f, 1.5f);
		}
		if (t % 10 == 0) {
			burn();
		}
		if (cfg.terrainDestruction && cfg.laserTerrain) {
			bore(t);
		}
		if (t >= duration) {
			StrikeUtil.sound(level, pos, SoundEvents.BEACON_DEACTIVATE, 4.0f, 1.2f);
			StrikeUtil.sound(level, pos, SoundEvents.GENERIC_EXPLODE, 5.0f, 0.9f);
			return true;
		}
		return false;
	}

	/** Hits everything standing in the beam column (every 10 ticks = every damage-immunity window). */
	private void burn() {
		double r = cfg.laserBeamRadius;
		float perHit = cfg.laserDamagePerSecond / 2.0f;
		AABB box = new AABB(pos.x - r, pos.y - 2.0, pos.z - r, pos.x + r, pos.y + 64.0, pos.z + r);
		var source = level.damageSources().generic();
		var ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
		if (ownerPlayer != null) {
			source = level.damageSources().playerAttack(ownerPlayer);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (e.isSpectator()) {
				continue;
			}
			if (e instanceof net.minecraft.server.level.ServerPlayer sp) {
				if (!cfg.damagePlayers || (!cfg.damageOwner && sp.getUUID().equals(owner))) {
					continue;
				}
			}
			double dx = e.getX() - pos.x;
			double dz = e.getZ() - pos.z;
			if (dx * dx + dz * dz > (r + 0.5) * (r + 0.5)) {
				continue;
			}
			e.hurtServer(level, source, perHit);
			e.igniteForSeconds(4.0f);
		}
	}

	/** The sustained beam slowly drills a shaft downward (2 ticks per block of depth), within the safety limits. */
	private void bore(int t) {
		int targetDepth = Math.min(cfg.laserMaxDepth, t / 2);
		int radius = Math.max(1, (int) Math.round(cfg.laserBeamRadius * 0.6));
		while (bored < targetDepth && removedTotal < cfg.maxBlocksPerStrike) {
			bored++;
			int y = base.getY() - bored + 1;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dz * dz > radius * radius + 1) {
						continue;
					}
					if (removedTotal >= cfg.maxBlocksPerStrike) {
						return;
					}
					if (StrikeUtil.tryRemove(level, new BlockPos(base.getX() + dx, y, base.getZ() + dz))) {
						removedTotal++;
					}
				}
			}
		}
	}
}
