package net.dx12mc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.OptionInstance;
import org.junit.jupiter.api.Test;

class VideoOptionsTest {

  @Test
  void fabulousIsRemovedFromTheGraphicsValueSetWithoutPostProcessing() {
    List<GraphicsStatus> all = Arrays.asList(GraphicsStatus.values());
    assertEquals(
        List.of(GraphicsStatus.FAST, GraphicsStatus.FANCY), VideoOptions.graphicsModes(all, false));
  }

  @Test
  void graphicsValueSetIsUntouchedWhenOpenGlStillRenders() {
    List<GraphicsStatus> all = Arrays.asList(GraphicsStatus.values());
    assertSame(all, VideoOptions.graphicsModes(all, true));
  }

  @Test
  void listsWithoutFabulousAreReturnedAsIs() {
    List<String> other = List.of("a", "b");
    assertSame(other, VideoOptions.graphicsModes(other, false));
  }

  @Test
  void fabulousIsNotAValidValueOfTheFilteredGraphicsValueSet() {
    List<GraphicsStatus> offered =
        VideoOptions.graphicsModes(Arrays.asList(GraphicsStatus.values()), false);
    OptionInstance.AltEnum<GraphicsStatus> values =
        new OptionInstance.AltEnum<>(
            offered,
            offered,
            () -> false,
            (option, value) -> {},
            Codec.INT.xmap(GraphicsStatus::byId, GraphicsStatus::getId));
    assertTrue(values.validateValue(GraphicsStatus.FABULOUS).isEmpty());
    assertTrue(values.validateValue(GraphicsStatus.FANCY).isPresent());
  }
}
