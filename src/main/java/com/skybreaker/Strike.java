package com.skybreaker;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** A running weapon effect. All gameplay (timing, damage, terrain) is decided here on the server. */
public abstract class Strike {
	public final ServerLevel level;
	protected final UUID owner;
	protected final Vec3 pos;
	protected final SkybreakerConfig cfg = SkybreakerConfig.get();
	protected int age = 0;

	protected Strike(ServerLevel level, UUID owner, Vec3 pos) {
		this.level = level;
		this.owner = owner;
		this.pos = pos;
	}

	/** Called once when the strike is created. */
	public abstract void start();

	/** Called every server tick. Returns true when the strike is finished. */
	public abstract boolean tick();

	protected void tell(String message) {
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
		if (p != null) {
			p.sendOverlayMessage(Component.literal(message));
		}
	}

	/** Beeps and shows "T-n" to the owner once per second of the countdown. */
	protected void countdownTick(int countdownTicks, String label) {
		if (age < countdownTicks && age % 20 == 0) {
			int n = (countdownTicks - age + 19) / 20;
			float pitch = 0.9f + 0.7f * (age / (float) countdownTicks);
			StrikeUtil.sound(level, pos, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 4.0f, pitch);
			tell("\u00a7b" + label + " \u00a7fT-" + n);
		}
	}
}
