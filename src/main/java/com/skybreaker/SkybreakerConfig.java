package com.skybreaker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON config stored at config/skybreaker.json. Missing keys are filled with defaults and written back. */
public final class SkybreakerConfig {
	private static SkybreakerConfig instance = new SkybreakerConfig();

	public static SkybreakerConfig get() {
		return instance;
	}

	// ---- global safety / behaviour ----
	/** Master switch. false = NO block is ever destroyed by any Skybreaker weapon. */
	public boolean terrainDestruction = true;
	/** Maximum number of blocks a single strike may remove. */
	public int maxBlocksPerStrike = 1500;
	/** Blocks harder than this (obsidian = 50, stone = 1.5) are never destroyed. Unbreakable blocks (bedrock) never are. */
	public float maxBlockHardness = 25.0f;
	/** Never destroy blocks that have inventories/data (chests, spawners, beacons...). */
	public boolean protectBlockEntities = true;
	public double maxTargetRange = 128.0;
	public boolean damagePlayers = true;
	public boolean damageOwner = false;
	public boolean cameraShake = true;
	public double shakeRadius = 64.0;
	/** Players farther than this from a strike do not receive its visual effects. */
	public double effectBroadcastRange = 320.0;

	// ---- Orbital Railgun ----
	public double railgunCooldownSeconds = 30.0;
	public double railgunCountdownSeconds = 3.0;
	public float railgunDamage = 120.0f;
	/** "Explosion strength": crater radius in blocks; damage radius is 2x this. */
	public double railgunExplosionRadius = 9.0;
	public boolean railgunTerrain = true;

	// ---- Satellite Laser ----
	public double laserCooldownSeconds = 45.0;
	public double laserCountdownSeconds = 3.0;
	public double laserDurationSeconds = 6.0;
	public float laserDamagePerSecond = 40.0f;
	public double laserBeamRadius = 3.0;
	public boolean laserTerrain = true;
	/** Maximum depth the sustained beam can bore into the ground. */
	public int laserMaxDepth = 18;

	// ---- Meteor Barrage ----
	public double meteorCooldownSeconds = 60.0;
	public double meteorCountdownSeconds = 3.0;
	public int meteorCount = 14;
	public double meteorAreaRadius = 14.0;
	public double meteorSpreadSeconds = 4.0;
	public float meteorDamage = 35.0f;
	public double meteorImpactRadius = 4.0;
	public boolean meteorTerrain = true;

	public static int ticks(double seconds) {
		return Math.max(1, (int) Math.round(seconds * 20.0));
	}

	private void validate() {
		maxBlocksPerStrike = Math.max(0, Math.min(maxBlocksPerStrike, 20000));
		maxBlockHardness = Math.max(0f, maxBlockHardness);
		maxTargetRange = Math.max(8.0, Math.min(maxTargetRange, SkybreakerMod.MAX_RANGE));
		shakeRadius = Math.max(1.0, shakeRadius);
		effectBroadcastRange = Math.max(32.0, Math.min(effectBroadcastRange, 1000.0));
		railgunCooldownSeconds = Math.max(0, railgunCooldownSeconds);
		railgunCountdownSeconds = Math.max(0.5, Math.min(railgunCountdownSeconds, 30));
		railgunExplosionRadius = Math.max(1.0, Math.min(railgunExplosionRadius, 16.0));
		laserCooldownSeconds = Math.max(0, laserCooldownSeconds);
		laserCountdownSeconds = Math.max(0.5, Math.min(laserCountdownSeconds, 30));
		laserDurationSeconds = Math.max(1.0, Math.min(laserDurationSeconds, 30));
		laserBeamRadius = Math.max(0.5, Math.min(laserBeamRadius, 8.0));
		laserMaxDepth = Math.max(0, Math.min(laserMaxDepth, 64));
		meteorCooldownSeconds = Math.max(0, meteorCooldownSeconds);
		meteorCountdownSeconds = Math.max(0.5, Math.min(meteorCountdownSeconds, 30));
		meteorCount = Math.max(1, Math.min(meteorCount, 64));
		meteorAreaRadius = Math.max(2.0, Math.min(meteorAreaRadius, 40.0));
		meteorSpreadSeconds = Math.max(0.0, Math.min(meteorSpreadSeconds, 20.0));
		meteorImpactRadius = Math.max(1.0, Math.min(meteorImpactRadius, 10.0));
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("skybreaker.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					SkybreakerConfig loaded = gson.fromJson(reader, SkybreakerConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			instance.validate();
			try (Writer writer = Files.newBufferedWriter(path)) {
				gson.toJson(instance, writer);
			}
		} catch (Exception e) {
			SkybreakerMod.LOGGER.error("Could not read/write config/skybreaker.json, using defaults", e);
			instance = new SkybreakerConfig();
			instance.validate();
		}
	}
}
