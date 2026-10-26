package net.dx12mc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
  void onlyTheMenuBlurButtonIsDisabledAndOnlyWithoutPostProcessing() {
    OptionInstance<Boolean> menuBlur = OptionInstance.createBoolean("test.menuBlur", true);
    OptionInstance<Boolean> other = OptionInstance.createBoolean("test.other", true);
    assertEquals(
        VideoOptions.MENU_BLUR_UNAVAILABLE,
        VideoOptions.unavailableReason(menuBlur, menuBlur, false));
    assertNull(VideoOptions.unavailableReason(other, menuBlur, false));
    assertNull(VideoOptions.unavailableReason(menuBlur, menuBlur, true));
  }
}
