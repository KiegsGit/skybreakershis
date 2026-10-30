package com.skybreaker.client;

import com.skybreaker.FxPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Cinematic overlay: letterbox bars, targeting HUD, giant countdown, FIRE call-out and the white impact flash. */
final class CinematicHud {
	private static final String[] NAMES = {"", "ORBITAL RAILGUN", "SATELLITE LASER", "METEOR BARRAGE"};
	private static final int CYAN = 0xFF7FE0FF;
	private static final int CYAN_DIM = 0xFF3FA8D8;

	private static boolean active = false;
	private static int age = 0;
	private static int total = 60;
	private static int weapon = 1;
	private static double distance = 0;
	private static int tx, ty, tz;

	private static float flashAlpha = 0f;
	private static float flashDecay = 0.05f;

	static void start(FxPayload p) {
		active = true;
		age = 0;
		total = Math.max(1, p.a());
		weapon = Math.max(1, Math.min(3, p.b()));
		distance = p.f();
		tx = (int) Math.floor(p.x());
		ty = (int) Math.floor(p.y());
		tz = (int) Math.floor(p.z());
	}

	static void flash(float intensity, int ticks) {
		if (intensity > flashAlpha) {
			flashAlpha = Math.min(1f, intensity);
		}
		flashDecay = Math.max(0.01f, flashAlpha / Math.max(1, ticks));
	}

	static void clear() {
		active = false;
		flashAlpha = 0f;
	}

	static void tick() {
		if (active) {
			age++;
			if (age > total + 26) {
				active = false;
			}
		}
		if (flashAlpha > 0f) {
			flashAlpha = Math.max(0f, flashAlpha - flashDecay);
		}
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	static void render(GuiGraphicsExtractor g, DeltaTracker tracker) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		var font = Minecraft.getInstance().font;

		if (active) {
			float in = Math.min(1f, age / 10f);
			float out = Math.max(0f, Math.min(1f, (age - (total + 10)) / 14f));
			float bar = in * (1f - out);
			int barH = (int) (h * 0.12f * bar);

			// cold blue tint, turning red in the final second
			int remaining = total - age;
			if (remaining > 0 && remaining < 20) {
				g.fill(0, 0, w, h, argb((int) (46 * (1f - remaining / 20f)), 0xFF2030));
			} else {
				g.fill(0, 0, w, h, argb((int) (14 * bar), 0x2090FF));
			}

			g.fill(0, 0, w, barH, 0xFF000000);
			g.fill(0, h - barH, w, h, 0xFF000000);

			if (bar > 0.6f) {
				g.centeredText(font, "SKYBREAKER // " + NAMES[weapon], w / 2, Math.max(2, barH / 2 - 4), CYAN);
				g.centeredText(font, "TGT " + tx + " " + ty + " " + tz + "   RANGE " + (int) distance + " m",
					w / 2, h - barH / 2 - 4, CYAN_DIM);
			}

			// corner brackets
			int m = barH + 12;
			int len = 26;
			int col = argb(150 + (int) (90 * Math.abs(Math.sin(age * 0.25))), 0x5CD6FF);
			bracket(g, m, m, len, 1, 1, col);
			bracket(g, w - m, m, len, -1, 1, col);
			bracket(g, m, h - m, len, 1, -1, col);
			bracket(g, w - m, h - m, len, -1, -1, col);

			// scanline sweeping down the screen
			int span = Math.max(1, h - 2 * barH);
			int sy = barH + (age * 9) % span;
			g.fill(0, sy, w, sy + 1, argb(70, 0x5CD6FF));

			if (age < total) {
				float prog = age / (float) total;
				int secs = (total - age + 19) / 20;
				float frac = ((total - age) % 20) / 20f;
				float scale = 4f + 3f * frac;
				g.pose().pushMatrix();
				g.pose().translate(w / 2f, h / 2f - 34f);
				g.pose().scale(scale);
				g.centeredText(font, String.valueOf(secs), 0, 0, remaining < 20 ? 0xFFFF5060 : 0xFFFFFFFF);
				g.pose().popMatrix();

				int bw = 150;
				int x0 = w / 2 - bw / 2;
				int y0 = h / 2 + 28;
				g.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + 5, 0xAA000000);
				g.fill(x0, y0, x0 + (int) (bw * prog), y0 + 4, CYAN);
				g.centeredText(font, prog > 0.92f ? "TARGET LOCKED" : "LOCKING  " + (int) (prog * 100) + "%", w / 2, y0 + 10, CYAN);
			} else if (age < total + 14) {
				float pop = 1f - (age - total) / 14f;
				g.pose().pushMatrix();
				g.pose().translate(w / 2f, h / 2f - 20f);
				g.pose().scale(5f + 3f * pop);
				g.centeredText(font, "FIRE", 0, 0, argb((int) (255 * Math.min(1f, pop + 0.3f)), 0xFF4050));
				g.pose().popMatrix();
			}
		}

		if (flashAlpha > 0.01f) {
			g.fill(0, 0, w, h, argb((int) (Math.min(1f, flashAlpha) * 255), 0xEAF6FF));
		}
	}

	private static void bracket(GuiGraphicsExtractor g, int x, int y, int len, int dx, int dy, int color) {
		// horizontal arm
		g.fill(Math.min(x, x + dx * len), Math.min(y, y + dy * 2), Math.max(x, x + dx * len), Math.max(y, y + dy * 2), color);
		// vertical arm
		g.fill(Math.min(x, x + dx * 2), Math.min(y, y + dy * len), Math.max(x, x + dx * 2), Math.max(y, y + dy * len), color);
	}

	private CinematicHud() {
	}
}
