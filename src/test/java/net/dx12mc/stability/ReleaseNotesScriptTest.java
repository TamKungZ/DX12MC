package net.dx12mc.stability;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseNotesScriptTest {
  private static final Path SCRIPT = Path.of(".github", "scripts", "release-notes.ps1");
  private static final Path CHANGELOG = Path.of("CHANGELOG.md");

  @TempDir Path temp;

  @Test
  void notesStartWithTheChangelogHeadingThenEveryNoteThenTheTaggedSection() throws Exception {
    for (String tag : new String[] {"0.11.0", "v0.11.0", "0.10.0", "0.9.0"}) {
      String version = tag.replaceFirst("^v", "");
      Result result = run(tag, CHANGELOG);
      assertEquals(0, result.exit(), tag + ": " + result.output());
      List<String> notes = result.lines();
      List<String> expected = new ArrayList<>();
      expected.add("# Changelog");
      for (String line : changelog()) {
        if (!line.contains("Note:")) continue;
        expected.add("");
        expected.add(line);
      }
      expected.add("");
      expected.addAll(section(version));
      assertEquals(expected, notes, tag);
      assertTrue(notes.get(notes.size() - 1).length() > 0, "trailing blank lines are trimmed");
    }
  }

  @Test
  void sectionStopsAtTheNextVersionHeading() throws Exception {
    List<String> notes = run("0.10.0", CHANGELOG).lines();
    assertTrue(notes.contains("## 0.10.0 - Rendering fidelity"));
    assertFalse(notes.stream().anyMatch(line -> line.startsWith("## 0.9.2")));
    assertFalse(notes.stream().anyMatch(line -> line.startsWith("## 0.11.0")));
  }

  @Test
  void topNoteLinesAlreadyCarryTheNotePrefix() throws IOException {
    List<String> lines = changelog();
    assertEquals("# Changelog", lines.get(0));
    assertTrue(lines.get(2).startsWith("Note: 0.9.0"));
    assertTrue(lines.get(4).startsWith("Note: 0.8.8"));
  }

  @Test
  void missingEmptyDuplicateOrMalformedReleasesFailWithoutWritingNotes() throws Exception {
    assertFails(run("0.0.1", CHANGELOG), "no '## 0.0.1' section");
    assertFails(run("release-1", CHANGELOG), "is not a release tag");
    Path empty = temp.resolve("empty.md");
    Files.writeString(empty, "# Changelog\n\nNote: n\n\n## 1.0.0 - Empty\n\n\n## 0.9.0\n\n- x\n");
    assertFails(run("1.0.0", empty), "is empty");
    Path duplicate = temp.resolve("duplicate.md");
    Files.writeString(duplicate, "# Changelog\n\n## 1.0.0\n\n- a\n\n## 1.0.0 - Again\n\n- b\n");
    assertFails(run("1.0.0", duplicate), "more than one section");
    Path prefix = temp.resolve("prefix.md");
    Files.writeString(prefix, "# Changelog\n\n## 1.0.10\n\n- a\n\n## 1.0.1\n\n- b\n");
    Result exact = run("1.0.1", prefix);
    assertEquals(0, exact.exit(), exact.output());
    assertEquals(List.of("# Changelog", "", "## 1.0.1", "", "- b"), exact.lines());
  }

  private void assertFails(Result result, String message) {
    assertNotEquals(0, result.exit());
    assertTrue(result.output().contains(message), result.output());
    assertFalse(Files.exists(result.notes()), "no notes file is written on failure");
  }

  private static List<String> changelog() throws IOException {
    return Files.readAllLines(CHANGELOG, StandardCharsets.UTF_8);
  }

  private static List<String> section(String version) throws IOException {
    List<String> lines = changelog();
    int start = -1;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.equals("## " + version) || line.startsWith("## " + version + " - ")) start = i;
    }
    assertTrue(start >= 0, version);
    int end = start + 1;
    while (end < lines.size() && !lines.get(end).startsWith("## ")) end++;
    List<String> section = new ArrayList<>(lines.subList(start, end));
    while (section.get(section.size() - 1).isBlank()) section.remove(section.size() - 1);
    return section;
  }

  private Result run(String tag, Path changelog) throws Exception {
    Path notes = temp.resolve(tag + ".md");
    Process process =
        new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                SCRIPT.toAbsolutePath().toString(),
                "-Tag",
                tag,
                "-Changelog",
                changelog.toAbsolutePath().toString(),
                "-Output",
                notes.toString())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Result(process.waitFor(), output, notes);
  }

  private record Result(int exit, String output, Path notes) {
    List<String> lines() throws IOException {
      String text = Files.readString(notes, StandardCharsets.UTF_8);
      assertFalse(text.contains("\r"), "notes use LF line endings");
      assertTrue(text.endsWith("\n"));
      return List.of(text.substring(0, text.length() - 1).split("\n", -1));
    }
  }
}
