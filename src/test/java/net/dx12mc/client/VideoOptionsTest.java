package net.dx12mc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
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
  void aSavedFabulousValueIsRejectedByTheFilteredValueSetSoTheOptionFallsBackToItsFancyDefault() {
    List<GraphicsStatus> filtered =
        VideoOptions.graphicsModes(Arrays.asList(GraphicsStatus.values()), false);
    OptionInstance.AltEnum<GraphicsStatus> valueSet =
        new OptionInstance.AltEnum<>(
            filtered,
            List.of(GraphicsStatus.FAST, GraphicsStatus.FANCY),
            () -> false,
            (option, value) -> {},
            Codec.INT.xmap(GraphicsStatus::byId, GraphicsStatus::getId));
    assertTrue(valueSet.validateValue(GraphicsStatus.FABULOUS).isEmpty());
    assertEquals(
        GraphicsStatus.FABULOUS,
        valueSet
            .codec()
            .parse(NbtOps.INSTANCE, IntTag.valueOf(GraphicsStatus.FABULOUS.getId()))
            .result()
            .orElseThrow());
    assertEquals(Optional.of(GraphicsStatus.FANCY), valueSet.validateValue(GraphicsStatus.FANCY));
  }
}
