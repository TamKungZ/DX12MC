package net.dx12mc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class ApiSnapshotTest {
  static final char BYTE_ORDER_MARK = (char) 0xFEFF;

  @Test
  void publicSignaturesMatchTheStoredSnapshot() throws Exception {
    Path snapshot = Path.of(System.getProperty("dx12mc.apiSnapshot"));
    String actual = render(signatures());
    if (Boolean.parseBoolean(System.getProperty("dx12mc.updateApiSnapshot", "false"))) {
      Files.writeString(snapshot, actual, StandardCharsets.UTF_8);
    }
    String expected = stored(snapshot);
    assertEquals(
        expected,
        actual,
        "The public API changed. If this is intended, bump api_version when needed and run"
            + " gradlew :api:test -PupdateApiSnapshot=true");
  }

  @Test
  void apiVersionPropertyMatchesTheSnapshotHeader() throws Exception {
    assertEquals("api " + System.getProperty("dx12mc.apiVersion"), signatures().get(0));
  }

  @Test
  void signaturesUseOnlyJavaAndApiTypes() throws Exception {
    for (String line : signatures()) {
      for (String token : line.split("[^A-Za-z0-9_.$]+")) {
        if (!token.contains(".") || token.matches("[0-9.]+")) continue;
        assertTrue(
            token.startsWith("java.") || token.startsWith("net.dx12mc.api."),
            "API signature uses a foreign type: " + token + " in " + line);
      }
    }
  }

  static String stored(Path snapshot) throws IOException {
    return Files.exists(snapshot)
        ? canonical(Files.readString(snapshot, StandardCharsets.UTF_8))
        : "";
  }

  static String render(List<String> lines) {
    return canonical(String.join("\n", lines));
  }

  static String canonical(String text) {
    String body = !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    body = body.replace("\r\n", "\n").replace('\r', '\n');
    int end = body.length();
    while (end > 0 && body.charAt(end - 1) == '\n') end--;
    return end == 0 ? "" : body.substring(0, end) + "\n";
  }

  static List<String> sortedMembers(List<String> members) {
    List<String> sorted = new ArrayList<>(members);
    sorted.sort(null);
    return sorted;
  }

  static List<String> signatures() throws IOException, URISyntaxException {
    Path root = Path.of(Dx12mc.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    List<String> classNames = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      files
          .filter(path -> path.toString().endsWith(".class"))
          .map(path -> root.relativize(path).toString().replace('\\', '/'))
          .filter(name -> name.startsWith("net/dx12mc/api/"))
          .map(name -> name.substring(0, name.length() - 6).replace('/', '.'))
          .sorted()
          .forEach(classNames::add);
    }
    List<String> lines = new ArrayList<>();
    lines.add("api " + System.getProperty("dx12mc.apiVersion"));
    for (String className : classNames) {
      Class<?> type;
      try {
        type = Class.forName(className, false, ApiSnapshotTest.class.getClassLoader());
      } catch (ClassNotFoundException exception) {
        throw new IllegalStateException(exception);
      }
      if (!Modifier.isPublic(type.getModifiers())) continue;
      lines.add(describe(type));
      List<String> members = new ArrayList<>();
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isPublic(field.getModifiers())) members.add("  " + field.toGenericString());
      }
      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        if (Modifier.isPublic(constructor.getModifiers()))
          members.add("  " + constructor.toGenericString());
      }
      for (Method method : type.getDeclaredMethods()) {
        if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
          members.add("  " + method.toGenericString());
      }
      lines.addAll(sortedMembers(members));
    }
    return lines;
  }

  private static String describe(Class<?> type) {
    StringBuilder text = new StringBuilder(Modifier.toString(type.getModifiers()));
    text.append(type.isInterface() ? " " : " class ").append(type.getName());
    if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
      text.append(" extends ").append(type.getGenericSuperclass().getTypeName());
    }
    for (var anInterface : type.getGenericInterfaces()) {
      text.append(" implements ").append(anInterface.getTypeName());
    }
    return text.toString();
  }
}
