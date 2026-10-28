package net.dx12mc.harness;

import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlightHarness {
  static final boolean ENABLED = "flight".equals(System.getProperty("dx12mc.harness"));
  private static final Logger LOGGER = LoggerFactory.getLogger("DX12MC harness");
  private static final long SEED = Long.getLong("dx12mc.harness.seed", 4242L);
  private static final double SPEED =
      Double.parseDouble(System.getProperty("dx12mc.harness.speed", "20"));
  private static final double HEIGHT = 120.0;
  private static final Phase[] PHASES = {
    new Phase("load", 40, 0, false),
    new Phase("settled", 10, 0, true),
    new Phase("moving_new", 30, 1, true),
    new Phase("catch_up", 25, 0, false),
    new Phase("settled_after", 10, 0, true),
    new Phase("moving_back", 30, -1, true),
    new Phase("settled_home", 10, 0, false),
    new Phase("moving_again", 30, 1, true),
    new Phase("end", 5, 0, false)
  };
  private static final FlightHarness INSTANCE = new FlightHarness();

  private record Phase(String name, double seconds, int direction, boolean measured) {}

  private boolean worldRequested;
  private long startNanos;
  private long lastFrameNanos;
  private double startX;
  private double startZ;
  private int phase = -1;
  private long phaseStartNanos;
  private double phaseStartX;
  private double x;
  private float[] frameMillis = new float[1 << 16];
  private int frames;
  private boolean finished;

  static FlightHarness instance() {
    return INSTANCE;
  }

  void onFrame(Minecraft minecraft) {
    if (finished) return;
    long now = System.nanoTime();
    if (!worldRequested) {
      if (minecraft.screen instanceof TitleScreen) createWorld(minecraft);
      return;
    }
    LocalPlayer player = minecraft.player;
    if (player == null || minecraft.level == null || minecraft.screen != null) {
      lastFrameNanos = now;
      return;
    }
    if (startNanos == 0L) {
      startNanos = now;
      startX = 0.5;
      startZ = 0.5;
      x = startX;
      player.getAbilities().flying = true;
      LOGGER.info("HARNESS start seed={} x={} z={} speed={}", SEED, startX, startZ, SPEED);
      begin(0, now);
    } else if (phase >= 0 && PHASES[phase].measured && lastFrameNanos != 0L) {
      if (frames == frameMillis.length) frameMillis = Arrays.copyOf(frameMillis, frames * 2);
      frameMillis[frames++] = (now - lastFrameNanos) / 1_000_000.0f;
    }
    lastFrameNanos = now;
    Phase current = PHASES[phase];
    double elapsed = (now - phaseStartNanos) / 1e9;
    if (elapsed >= current.seconds) {
      end(current, elapsed);
      if (phase + 1 == PHASES.length) {
        finished = true;
        LOGGER.info("HARNESS done");
        minecraft.stop();
        return;
      }
      begin(phase + 1, now);
      current = PHASES[phase];
      elapsed = 0.0;
    }
    x = phaseStartX + current.direction * SPEED * elapsed;
    player.getAbilities().flying = true;
    player.setDeltaMovement(0.0, 0.0, 0.0);
    player.moveTo(x, HEIGHT, startZ, current.direction < 0 ? 90.0f : -90.0f, 20.0f);
  }

  private void createWorld(Minecraft minecraft) {
    worldRequested = true;
    LOGGER.info("HARNESS creating world dx12mc-harness seed={}", SEED);
    LevelSettings settings =
        new LevelSettings(
            "dx12mc-harness",
            GameType.CREATIVE,
            false,
            Difficulty.PEACEFUL,
            true,
            new GameRules(),
            WorldDataConfiguration.DEFAULT);
    minecraft
        .createWorldOpenFlows()
        .createFreshLevel(
            "dx12mc-harness",
            settings,
            new WorldOptions(SEED, true, false),
            WorldPresets::createNormalWorldDimensions,
            new TitleScreen());
  }

  private void begin(int index, long now) {
    phase = index;
    phaseStartNanos = now;
    phaseStartX = x;
    frames = 0;
    Timing.reset();
    LOGGER.info(
        "HARNESS phase {} begins at x={}",
        PHASES[index].name,
        String.format(Locale.ROOT, "%.1f", x));
  }

  private void end(Phase phase, double seconds) {
    if (!phase.measured || frames == 0) {
      LOGGER.info("HARNESS phase {} ends", phase.name);
      return;
    }
    float[] sorted = Arrays.copyOf(frameMillis, frames);
    Arrays.sort(sorted);
    double total = 0.0;
    for (int i = 0; i < frames; i++) total += sorted[i];
    LOGGER.info(
        String.format(
            Locale.ROOT,
            "HARNESS phase %s ends: frames %d, fps %.1f, frame ms avg %.2f p50 %.2f p95 %.2f p99"
                + " %.2f max %.2f",
            phase.name,
            frames,
            frames / seconds,
            total / frames,
            sorted[frames / 2],
            sorted[Math.min(frames - 1, frames * 95 / 100)],
            sorted[Math.min(frames - 1, frames * 99 / 100)],
            sorted[frames - 1]));
    LOGGER.info("HARNESS timing {} {}", phase.name, Timing.drain(frames));
  }
}
