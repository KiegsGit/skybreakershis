package com.skybreaker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class RailgunStrike extends Strike {
	private final int countdown;

	public RailgunStrike(ServerLevel level, UUID owner, Vec3 pos) {
		super(level, owner, pos);
		this.countdown = SkybreakerConfig.ticks(cfg.railgunCountdownSeconds);
	}

	@Override
	public void start() {
		StrikeUtil.broadcast(level, pos, new FxPayload(FxPayload.RAILGUN, pos.x, pos.y, pos.z,
			countdown, FxPayload.BOLT_TICKS, (float) cfg.railgunExplosionRadius, new float[0]));
		StrikeUtil.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 4.0f, 1.7f);
		StrikeUtil.sound(level, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, 4.0f, 1.4f);
		countdownTick(countdown, "Orbital Railgun");
	}

	@Override
	public boolean tick() {
		age++;
		countdownTick(countdown, "Orbital Railgun");
		if (age == countdown) {
			// beam fires
			StrikeUtil.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0f, 2.0f);
			StrikeUtil.sound(level, pos, SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 1.6f);
		}
		if (age == countdown + FxPayload.BOLT_TICKS) {
			impact();
			return true;
		}
		return false;
	}

	private void impact() {
		double r = cfg.railgunExplosionRadius;
		StrikeUtil.sound(level, pos, SoundEvents.GENERIC_EXPLODE, 10.0f, 0.6f);
		StrikeUtil.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 12.0f, 0.5f);
		StrikeUtil.sound(level, pos, SoundEvents.WARDEN_SONIC_BOOM, 5.0f, 0.8f);
		StrikeUtil.blast(level, pos, r * 2.0, cfg.railgunDamage, owner, true);
		if (cfg.terrainDestruction && cfg.railgunTerrain) {
			StrikeUtil.crater(level, pos.add(0, -r * 0.25, 0), r, cfg.maxBlocksPerStrike);
		}
		StrikeUtil.shake(level, pos, 40, 2.2f);
	}
}
