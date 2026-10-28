package net.dx12mc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;
import net.dx12mc.api.ApiVersion;
import net.dx12mc.api.WrongThreadException;
import net.dx12mc.config.Config;
import net.dx12mc.config.Mode;
import net.dx12mc.render.Controller;
import org.junit.jupiter.api.Test;

final class ApiThreadTest {
  private static Api api() {
    return new Api(
        new Controller(new Config(Mode.NATIVE, 0, false)),
        true,
        new ApiVersion(0, 1),
        Thread.currentThread());
  }

  @Test
  void callsFromAnotherThreadFailWithAClearError() throws Exception {
    Api api = api();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread other =
        new Thread(
            () -> {
              try {
                api.packs();
              } catch (Throwable throwable) {
                failure.set(throwable);
              }
            },
            "worker");
    other.start();
    other.join();
    assertInstanceOf(WrongThreadException.class, failure.get());
    assertTrue(failure.get().getMessage().contains("worker"));
  }

  @Test
  void providersMustBeNamespacedAndUnique() {
    Api api = api();
    api.registerUniformProvider("mod:tint", values -> values.put(0, 2.0f));
    assertThrows(
        IllegalArgumentException.class, () -> api.registerUniformProvider("mod:tint", v -> {}));
    assertThrows(
        IllegalArgumentException.class, () -> api.registerUniformProvider("tint", v -> {}));
    FloatBuffer values = FloatBuffer.allocate(4);
    assertTrue(api.update("mod:tint", values));
    assertEquals(2.0f, values.get(0));
    assertFalse(api.update("mod:missing", values));
  }

  @Test
  void aThrowingProviderIsDisabledInsteadOfEndingTheGame() {
    Api api = api();
    api.registerUniformProvider(
        "mod:broken",
        values -> {
          throw new IllegalStateException("boom");
        });
    assertFalse(api.update("mod:broken", FloatBuffer.allocate(4)));
    assertFalse(api.update("mod:broken", FloatBuffer.allocate(4)));
  }

  @Test
  void capabilitiesFollowTheRenderMode() {
    assertTrue(api().supports(net.dx12mc.api.Capability.SCENE_TARGETS));
    Api probe =
        new Api(
            new Controller(new Config(Mode.PROBE, 0, false)),
            false,
            new ApiVersion(0, 1),
            Thread.currentThread());
    assertFalse(probe.supports(net.dx12mc.api.Capability.SHADER_PACKS));
  }
}
