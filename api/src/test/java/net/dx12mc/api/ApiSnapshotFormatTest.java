package net.dx12mc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ApiSnapshotFormatTest {
  private static Path snapshot() {
    return Path.of(System.getProperty("dx12mc.apiSnapshot"));
  }

  @Test
  void lineEndingsByteOrderMarkAndTrailingNewlinesDoNotChangeTheSnapshot() {
    String lf = "api 0.1\npublic interface net.dx12mc.api.Dx12mc\n  public abstract void x()\n";
    String crlf =
        ApiSnapshotTest.BYTE_ORDER_MARK
            + "api 0.1\r\n"
            + "public interface net.dx12mc.api.Dx12mc\r\n"
            + "  public abstract void x()\r\n\r\n";
    String cr = "api 0.1\rpublic interface net.dx12mc.api.Dx12mc\r  public abstract void x()";
    assertEquals(lf, ApiSnapshotTest.canonical(crlf));
    assertEquals(lf, ApiSnapshotTest.canonical(cr));
    assertEquals(lf, ApiSnapshotTest.canonical(lf));
    assertEquals("", ApiSnapshotTest.canonical("\r\n"));
  }

  @Test
  void theStoredSnapshotMatchesWhenCheckedOutWithCrlf() throws Exception {
    String stored = ApiSnapshotTest.stored(snapshot());
    String crlf = stored.replace("\n", "\r\n");
    assertEquals(stored, ApiSnapshotTest.canonical(crlf));
    assertEquals(stored, ApiSnapshotTest.render(ApiSnapshotTest.signatures()));
  }

  @Test
  void memberOrderDoesNotChangeTheSnapshot() throws Exception {
    List<String> members = new ArrayList<>();
    for (String line : ApiSnapshotTest.signatures()) if (line.startsWith("  ")) members.add(line);
    List<String> shuffled = new ArrayList<>(members);
    Collections.shuffle(shuffled, new Random(7));
    assertNotEquals(members, shuffled);
    assertEquals(ApiSnapshotTest.sortedMembers(members), ApiSnapshotTest.sortedMembers(shuffled));
  }

  @Test
  void anAddedRemovedOrChangedSignatureStillFails() throws Exception {
    String stored = ApiSnapshotTest.stored(snapshot());
    List<String> lines = ApiSnapshotTest.signatures();
    int method = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).contains("registerUniformProvider")) method = i;
    }
    List<String> changed = new ArrayList<>(lines);
    changed.set(method, changed.get(method).replace("registerUniformProvider", "registerUniform"));
    List<String> added = new ArrayList<>(lines);
    added.add(method + 1, "  public abstract void net.dx12mc.api.Dx12mc.reset()");
    List<String> removed = new ArrayList<>(lines);
    removed.remove(method);
    for (List<String> mutation : List.of(changed, added, removed)) {
      assertNotEquals(stored, ApiSnapshotTest.render(mutation));
      assertNotEquals(
          stored,
          ApiSnapshotTest.canonical(ApiSnapshotTest.render(mutation).replace("\n", "\r\n")));
    }
  }
}
