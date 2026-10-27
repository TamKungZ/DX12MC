package net.dx12mc.stability;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReleaseWorkflowTest {
  private static final Path WORKFLOW = Path.of(".github", "workflows", "publish.yml");

  @Test
  void everyActionIsPinnedToAFullCommitSha() throws IOException {
    Matcher uses = Pattern.compile("uses:\\s*(\\S+)").matcher(workflow());
    int count = 0;
    while (uses.find()) {
      count++;
      assertTrue(uses.group(1).matches("[\\w.-]+/[\\w./-]+@[0-9a-f]{40}"), uses.group(1));
    }
    assertEquals(3, count);
  }

  @Test
  void runsForReleaseTagsOrARepeatedTagWithMinimalPermissions() throws IOException {
    String text = workflow();
    assertTrue(text.contains("- '[0-9]*.[0-9]*.[0-9]*\\+mc*'"));
    assertTrue(text.contains("- 'v[0-9]*.[0-9]*.[0-9]*\\+mc*'"));
    assertTrue(text.contains("workflow_dispatch:\n    inputs:\n      tag:"));
    assertTrue(text.contains("permissions:\n  contents: write\n  packages: write\n"));
    assertTrue(text.contains("ref: refs/tags/${{ env.RELEASE_TAG }}"));
  }

  @Test
  void releaseIsCreatedLastWithNotesAndNoAssets() throws IOException {
    String text = workflow();
    List<String> order =
        List.of(
            "Refuse a tag that already has a release",
            "Check out the tagged commit",
            "Check tag, commit and version",
            "Assemble release notes",
            "Build and test",
            "Import signing key",
            "Publish to GitHub Packages",
            "Create the GitHub release",
            "Remove the signing key");
    int previous = -1;
    for (String step : order) {
      int at = text.indexOf("- name: " + step);
      assertTrue(at > previous, step);
      previous = at;
    }
    Matcher create = Pattern.compile("gh release create ([^\\n]+)").matcher(text);
    assertTrue(create.find());
    assertEquals(
        "$env:RELEASE_TAG --verify-tag --title $env:RELEASE_TAG --notes-file"
            + " \"$env:RUNNER_TEMP\\release-notes.md\"",
        create.group(1).strip());
    assertFalse(create.find(), "the release is created once");
    assertTrue(text.contains("if: always()"));
    assertTrue(text.contains("--delete-secret-and-public-key $fingerprint"));
    assertTrue(text.contains("-Pdx12mc.requireSigning=true"));
  }

  @Test
  void projectVersionIsModVersionPlusMinecraftVersion() throws IOException {
    String build = Files.readString(Path.of("build.gradle.kts"));
    assertTrue(
        build.contains("version = \"${prop(\"mod_version\")}+mc${prop(\"minecraft_version\")}\""));
    assertTrue(workflow().contains("$declared = \"$mod+mc$mc\""));
  }

  @Test
  void workflowsRunGradleOnJdk21AndCompileWithTheJdk17Toolchain() throws IOException {
    String properties = Files.readString(Path.of("gradle.properties"));
    assertTrue(properties.contains("\njava_version=17"));
    for (String file : List.of("build.yml", "publish.yml")) {
      String text = Files.readString(Path.of(".github", "workflows", file)).replace("\r\n", "\n");
      assertTrue(text.contains("java-version: |\n            17\n            21\n"), file);
      assertTrue(text.contains("-Dorg.gradle.java.home=$env:JAVA_HOME"), file);
      assertTrue(text.contains("-Dorg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64"), file);
    }
  }

  @Test
  void signingCandidatesAreFingerprintsOnly() throws IOException {
    String properties = Files.readString(Path.of("gradle.properties"));
    Matcher candidates = Pattern.compile("(?m)^signing_key_candidates=(.+)$").matcher(properties);
    assertTrue(candidates.find());
    for (String candidate : candidates.group(1).strip().split(",")) {
      assertTrue(candidate.matches("[0-9A-F]{40}!?"), candidate);
    }
    assertFalse(candidates.group(1).contains("@"));
    String build = Files.readString(Path.of("build.gradle.kts"));
    assertTrue(build.contains("\"--list-secret-keys\", keyName"));
    assertTrue(build.contains("dx12mc.requireSigning"));
    assertTrue(build.contains("withJavadocJar()"));
  }

  private static String workflow() throws IOException {
    return Files.readString(WORKFLOW).replace("\r\n", "\n");
  }
}
