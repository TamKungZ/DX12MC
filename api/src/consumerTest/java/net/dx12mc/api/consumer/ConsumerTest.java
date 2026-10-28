package net.dx12mc.api.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dx12mc.api.AdapterInfo;
import net.dx12mc.api.ApiVersion;
import net.dx12mc.api.Capability;
import net.dx12mc.api.Dx12mc;
import net.dx12mc.api.Dx12mcEntrypoint;
import net.dx12mc.api.PackStatus;
import net.dx12mc.api.ReloadListener;
import net.dx12mc.api.UniformProvider;
import org.junit.jupiter.api.Test;

final class ConsumerTest {
  @Test
  void aModCanRegisterAgainstTheApiJarAlone() {
    FakeDx12mc host = new FakeDx12mc();
    Dx12mcEntrypoint entrypoint =
        dx12mc -> {
          if (!dx12mc.apiVersion().satisfies(new ApiVersion(0, 1))) return;
          dx12mc.registerUniformProvider(
              "consumer:tint", values -> values.put(0, 1.0f).put(1, 0.5f));
          dx12mc.addReloadListener(packs -> {});
        };
    entrypoint.onDx12mc(host);
    FloatBuffer values = FloatBuffer.allocate(4);
    host.providers.get(0).update(values);
    assertEquals(0.5f, values.get(1));
    assertEquals(1, host.listeners.size());
    assertFalse(host.supports(Capability.SCENE_TARGETS));
  }

  @Test
  void aModRefusesAnIncompatibleApi() {
    FakeDx12mc host = new FakeDx12mc();
    host.version = new ApiVersion(0, 2);
    Dx12mcEntrypoint entrypoint =
        dx12mc -> {
          if (!dx12mc.apiVersion().satisfies(new ApiVersion(0, 1))) return;
          dx12mc.registerUniformProvider("consumer:tint", values -> {});
        };
    entrypoint.onDx12mc(host);
    assertEquals(0, host.providers.size());
    assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("x"));
    assertEquals(
        PackStatus.State.FAILED,
        new PackStatus("p", "file/p.zip", PackStatus.State.FAILED, "bad").state());
  }

  private static final class FakeDx12mc implements Dx12mc {
    final List<UniformProvider> providers = new ArrayList<>();
    final List<ReloadListener> listeners = new ArrayList<>();
    ApiVersion version = new ApiVersion(0, 1);

    @Override
    public String modVersion() {
      return "test";
    }

    @Override
    public ApiVersion apiVersion() {
      return version;
    }

    @Override
    public boolean active() {
      return false;
    }

    @Override
    public Optional<AdapterInfo> adapter() {
      return Optional.empty();
    }

    @Override
    public boolean supports(Capability capability) {
      return false;
    }

    @Override
    public void registerUniformProvider(String name, UniformProvider provider) {
      providers.add(provider);
    }

    @Override
    public void addReloadListener(ReloadListener listener) {
      listeners.add(listener);
    }

    @Override
    public List<PackStatus> packs() {
      return List.of();
    }
  }
}
