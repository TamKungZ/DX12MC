package net.dx12mc.bridge.texture;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class UploadQueueTest {

  @Test
  void cursorAdvancesOnlyAfterSuccessfulSubmission() {
    Image.Upload upload = new Image.Upload(0, 3, 7, 4, 5, new byte[4 * 5 * 4]);
    UploadQueue queue = new UploadQueue(List.of(upload), 12L);
    UploadQueue.Band first = queue.next(32);
    assertEquals(2, first.rows());
    assertEquals(0, first.startRow());
    assertEquals(0, first.sourceOffset());
    assertEquals(first, queue.next(32));
    assertEquals(0L, queue.uploadedBytes());
    queue.submitted(first);
    UploadQueue.Band second = queue.next(32);
    assertEquals(2, second.startRow());
    assertEquals(32, second.sourceOffset());
    queue.submitted(second);
    UploadQueue.Band last = queue.next(32);
    assertEquals(1, last.rows());
    queue.submitted(last);
    assertTrue(queue.complete());
    assertEquals(upload.rgba().length, queue.uploadedBytes());
    assertEquals(12L, queue.revision());
    assertEquals(1, queue.regionCount());
  }

  @Test
  void oversizedAtlasIsSplitBelowTheStagingLimit() {
    Image.Upload upload = new Image.Upload(0, 0, 0, 16_384, 100, new byte[16_384 * 100 * 4]);
    UploadQueue queue = new UploadQueue(List.of(upload), 1L);
    UploadQueue.Band band = queue.next(4 * 1024 * 1024);
    assertEquals(64, band.rows());
    assertEquals(4 * 1024 * 1024, band.bytes());
    assertTrue(band.rows() < upload.height());
  }

  @Test
  void rejectsInvalidBudgetAndOutOfOrderAcknowledgement() {
    Image.Upload upload = new Image.Upload(0, 0, 0, 1, 1, new byte[4]);
    UploadQueue queue = new UploadQueue(List.of(upload), 1L);
    assertThrows(IllegalArgumentException.class, () -> queue.next(0));
    UploadQueue.Band current = queue.next(4);
    UploadQueue.Band wrong = new UploadQueue.Band(0, 1, upload, 1, 4, 4);
    assertThrows(IllegalStateException.class, () -> queue.submitted(wrong));
    queue.submitted(current);
    assertThrows(IllegalStateException.class, () -> queue.next(4));
  }
}
