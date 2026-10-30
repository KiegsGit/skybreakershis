package com.skybreaker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.UUID;

public final class MeteorStrike extends Strike {
	private final int countdown;
	private final int count;
	private final int[] delay;
	private final Vec3[] impact;
	private final int fall = FxPayload.METEOR_FALL_TICKS;
	private int maxDelay = 0;

	public MeteorStrike(ServerLevel level, UUID owner, Vec3 pos) {
		super(level, owner, pos);
		this.countdown = SkybreakerConfig.ticks(cfg.meteorCountdownSeconds);
		this.count = cfg.meteorCount;
		this.delay = new int[count];
		this.impact = new Vec3[count];
		Random rnd = new Random();
		int spread = SkybreakerConfig.ticks(cfg.meteorSpreadSeconds);
		for (int i = 0; i < count; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2.0;
			double rr = cfg.meteorAreaRadius * Math.sqrt(rnd.nextDouble());
			double x = pos.x + Math.cos(ang) * rr;
			double z = pos.z + Math.sin(ang) * rr;
			double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
			if (y > pos.y + 20.0 || y < pos.y - 40.0) {
				y = pos.y; // roofs / deep pits: fall back to the aimed height
			}
			delay[i] = rnd.nextInt(spread + 1);
			maxDelay = Math.max(maxDelay, delay[i]);
			impact[i] = new Vec3(x, y, z);
		}
	}

	@Override
	public void start() {
		// data[0] = area radius; then 4 floats per meteor: delay, dx, dy, dz (relative to the aimed point)
		float[] data = new float[1 + count * 4];
		data[0] = (float) cfg.meteorAreaRadius;
		for (int i = 0; i < count; i++) {
			data[1 + i * 4] = delay[i];
			data[2 + i * 4] = (float) (impact[i].x - pos.x);
			data[3 + i * 4] = (float) (impact[i].y - pos.y);
			data[4 + i * 4] = (float) (impact[i].z - pos.z);
		}
		StrikeUtil.broadcast(level, pos, new FxPayload(FxPayload.METEOR, pos.x, pos.y, pos.z,
			countdown, fall, (float) cfg.meteorImpactRadius, data));
		StrikeUtil.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 4.0f, 0.8f);
		StrikeUtil.sound(level, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, 4.0f, 0.8f);
		countdownTick(countdown, "Meteor Barrage");
	}

	@Override
	public boolean tick() {
		age++;
		countdownTick(countdown, "Meteor Barrage");
		int perMeteorBudget = Math.max(1, cfg.maxBlocksPerStrike / Math.max(1, count));
		for (int i = 0; i < count; i++) {
			int startTick = countdown + delay[i];
			if (age == startTick) {
				StrikeUtil.sound(level, impact[i], SoundEvents.FIRECHARGE_USE, 3.0f, 0.5f);
			}
			if (age == startTick + fall) {
				double r = cfg.meteorImpactRadius;
				StrikeUtil.sound(level, impact[i], SoundEvents.GENERIC_EXPLODE, 6.0f, 0.75f);
				StrikeUtil.blast(level, impact[i], r * 1.6, cfg.meteorDamage, owner, true);
				if (cfg.terrainDestruction && cfg.meteorTerrain) {
					StrikeUtil.crater(level, impact[i].add(0, -r * 0.3, 0), r, perMeteorBudget);
				}
				StrikeUtil.shake(level, impact[i], 14, 0.9f);
			}
		}
		return age > countdown + maxDelay + fall + 1;
	}
}
