package net.dx12mc.bridge.mesh;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dx12mc.graphics.bgfx.Packing;
import net.minecraft.client.Options;
import org.junit.jupiter.api.Test;

class VanillaVertexFormatTest {
  private static final String CORE = "assets/minecraft/shaders/core/";
  private static final int VERTICES = 4;

  @Test
  void everyVanillaCoreProgramReadsAnAttributeListOfADefaultVertexFormat() throws Exception {
    Map<String, VertexFormat> formats = defaultFormats();
    for (Map.Entry<String, List<String>> program : programAttributes().entrySet()) {
      boolean matched =
          formats.values().stream()
              .anyMatch(format -> attributeNames(format).equals(program.getValue()));
      assertTrue(matched, program.getKey() + " attributes " + program.getValue());
    }
  }

  @Test
  void encoderDecodesEveryVanillaFormatIntoTheValuesTheBuilderWrote() throws Exception {
    for (Map.Entry<String, VertexFormat> entry : defaultFormats().entrySet()) {
      VertexFormat format = entry.getValue();
      BufferBuilder builder = new BufferBuilder(256);
      builder.begin(VertexFormat.Mode.QUADS, format);
      for (int vertex = 0; vertex < VERTICES; vertex++) write(builder, format, vertex);
      BufferBuilder.RenderedBuffer rendered = builder.end();
      try {
        Batch batch = Encoder.encode(rendered);
        String name = entry.getKey();
        assertNotNull(batch, name);
        assertEquals(VERTICES, batch.vertexCount(), name);
        assertEquals(6, batch.indexCount(), name);
        for (int vertex = 0; vertex < VERTICES; vertex++) {
          assertEquals(vertex + 0.5f, batch.positions()[vertex * 3], name);
          assertEquals(2.5f, batch.positions()[vertex * 3 + 1], name);
          assertEquals(3.5f, batch.positions()[vertex * 3 + 2], name);
        }
        boolean color = has(format, DefaultVertexFormat.ELEMENT_COLOR);
        for (int vertex = 0; vertex < VERTICES; vertex++) {
          int expected = color ? rgba(10 + vertex, 20 + vertex, 30 + vertex, 250 - vertex) : -1;
          assertEquals(expected, batch.colors()[vertex], name);
        }
        assertEquals(has(format, DefaultVertexFormat.ELEMENT_UV0), batch.hasUv0(), name);
        assertEquals(has(format, DefaultVertexFormat.ELEMENT_UV1), batch.hasUv1(), name);
        assertEquals(has(format, DefaultVertexFormat.ELEMENT_UV2), batch.hasUv2(), name);
        assertEquals(has(format, DefaultVertexFormat.ELEMENT_NORMAL), batch.hasNormal(), name);
        for (int vertex = 0; vertex < VERTICES; vertex++) {
          if (batch.hasUv0()) {
            assertEquals(vertex * 0.25f, batch.uv0()[vertex * 2], name);
            assertEquals(0.5f, batch.uv0()[vertex * 2 + 1], name);
          }
          if (batch.hasUv1()) {
            assertEquals(vertex, batch.uv1()[vertex * 2], name);
            assertEquals(10, batch.uv1()[vertex * 2 + 1], name);
          }
          if (batch.hasUv2()) {
            assertEquals(0xA0 + vertex, batch.uv2()[vertex * 2], name);
            assertEquals(0xB0 - vertex, batch.uv2()[vertex * 2 + 1], name);
          }
          if (batch.hasNormal()) {
            assertEquals(0, batch.normals()[vertex * 3], name);
            assertEquals(127, batch.normals()[vertex * 3 + 1], name);
            assertEquals(-127, batch.normals()[vertex * 3 + 2], name);
          }
        }
      } finally {
        rendered.release();
      }
    }
  }

  @Test
  void blockAndEntityFormatsPackIntoTheTwentyEightAndFortyByteLayouts() {
    Batch block = encode(DefaultVertexFormat.BLOCK);
    ByteBuffer packed = ByteBuffer.allocate(VERTICES * Packing.VERTEX_STRIDE);
    Packing.vertices(packed, block);
    for (int vertex = 0; vertex < VERTICES; vertex++) {
      int base = vertex * Packing.VERTEX_STRIDE;
      assertEquals(vertex + 0.5f, packed.getFloat(base));
      assertEquals(10 + vertex, Byte.toUnsignedInt(packed.get(base + 12)));
      assertEquals(20 + vertex, Byte.toUnsignedInt(packed.get(base + 13)));
      assertEquals(30 + vertex, Byte.toUnsignedInt(packed.get(base + 14)));
      assertEquals(250 - vertex, Byte.toUnsignedInt(packed.get(base + 15)));
      assertEquals(vertex * 0.25f, packed.getFloat(base + 16));
      assertEquals(0.5f, packed.getFloat(base + 20));
      assertEquals(0xA0 + vertex, packed.getShort(base + 24));
      assertEquals(0xB0 - vertex, packed.getShort(base + 26));
    }
    Batch entity = encode(DefaultVertexFormat.NEW_ENTITY);
    ByteBuffer entityPacked = ByteBuffer.allocate(VERTICES * Packing.ENTITY_VERTEX_STRIDE);
    Packing.entityVertices(entityPacked, entity);
    for (int vertex = 0; vertex < VERTICES; vertex++) {
      int base = vertex * Packing.ENTITY_VERTEX_STRIDE;
      assertEquals(vertex * 0.25f, entityPacked.getFloat(base + 16));
      assertEquals(vertex, entityPacked.getFloat(base + 24));
      assertEquals(10, entityPacked.getFloat(base + 28));
      assertEquals(0xA0 + vertex, entityPacked.getShort(base + 32));
      assertEquals(0xB0 - vertex, entityPacked.getShort(base + 34));
      assertEquals(128, Byte.toUnsignedInt(entityPacked.get(base + 36)));
      assertEquals(255, Byte.toUnsignedInt(entityPacked.get(base + 37)));
      assertEquals(1, Byte.toUnsignedInt(entityPacked.get(base + 38)));
    }
    assertEquals(28, Packing.VERTEX_STRIDE);
    assertEquals(40, Packing.ENTITY_VERTEX_STRIDE);
  }

  @Test
  void vanillaLightCoordinatesUseTwoShortsPerVertexInEveryLightmapFormat() throws Exception {
    for (Map.Entry<String, VertexFormat> entry : defaultFormats().entrySet()) {
      VertexFormat format = entry.getValue();
      if (!has(format, DefaultVertexFormat.ELEMENT_UV2)) continue;
      assertEquals(
          VertexFormatElement.Type.SHORT,
          DefaultVertexFormat.ELEMENT_UV2.getType(),
          entry.getKey());
      assertEquals(2, DefaultVertexFormat.ELEMENT_UV2.getCount());
      assertEquals(4, DefaultVertexFormat.ELEMENT_UV2.getByteSize());
    }
    assertEquals(32, DefaultVertexFormat.BLOCK.getVertexSize());
    assertEquals(36, DefaultVertexFormat.NEW_ENTITY.getVertexSize());
    assertEquals(28, DefaultVertexFormat.PARTICLE.getVertexSize());
  }

  private static Batch encode(VertexFormat format) {
    BufferBuilder builder = new BufferBuilder(256);
    builder.begin(VertexFormat.Mode.QUADS, format);
    for (int vertex = 0; vertex < VERTICES; vertex++) write(builder, format, vertex);
    BufferBuilder.RenderedBuffer rendered = builder.end();
    try {
      return Encoder.encode(rendered);
    } finally {
      rendered.release();
    }
  }

  private static void write(BufferBuilder builder, VertexFormat format, int vertex) {
    for (VertexFormatElement element : format.getElements()) {
      if (element.equals(DefaultVertexFormat.ELEMENT_POSITION)) {
        builder.vertex(vertex + 0.5, 2.5, 3.5);
      } else if (element.equals(DefaultVertexFormat.ELEMENT_COLOR)) {
        builder.color(10 + vertex, 20 + vertex, 30 + vertex, 250 - vertex);
      } else if (element.equals(DefaultVertexFormat.ELEMENT_UV0)) {
        builder.uv(vertex * 0.25f, 0.5f);
      } else if (element.equals(DefaultVertexFormat.ELEMENT_UV1)) {
        builder.overlayCoords(vertex, 10);
      } else if (element.equals(DefaultVertexFormat.ELEMENT_UV2)) {
        builder.uv2(0xA0 + vertex, 0xB0 - vertex);
      } else if (element.equals(DefaultVertexFormat.ELEMENT_NORMAL)) {
        builder.normal(0.0f, 1.0f, -1.0f);
      } else {
        assertEquals(VertexFormatElement.Usage.PADDING, element.getUsage());
      }
    }
    builder.endVertex();
  }

  private static boolean has(VertexFormat format, VertexFormatElement element) {
    return format.getElements().contains(element);
  }

  private static int rgba(int r, int g, int b, int a) {
    return (r << 24) | (g << 16) | (b << 8) | a;
  }

  private static List<String> attributeNames(VertexFormat format) {
    return format.getElementAttributeNames().stream()
        .filter(name -> !name.equals("Padding"))
        .collect(Collectors.toList());
  }

  private static Map<String, VertexFormat> defaultFormats() throws IllegalAccessException {
    Map<String, VertexFormat> formats = new LinkedHashMap<>();
    for (Field field : DefaultVertexFormat.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          && Modifier.isPublic(field.getModifiers())
          && field.getType() == VertexFormat.class) {
        formats.put(field.getName(), (VertexFormat) field.get(null));
      }
    }
    assertFalse(formats.isEmpty());
    return formats;
  }

  private static Map<String, List<String>> programAttributes()
      throws IOException, URISyntaxException {
    Map<String, List<String>> programs = new TreeMap<>();
    Path jar = Path.of(Options.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Pattern vertexName = Pattern.compile("\"vertex\": \"([a-z_0-9]+)\"");
    Pattern input = Pattern.compile("(?m)^in\\s+\\w+\\s+(\\w+);");
    try (JarFile file = new JarFile(jar.toFile())) {
      Enumeration<JarEntry> entries = file.entries();
      while (entries.hasMoreElements()) {
        String name = entries.nextElement().getName();
        if (!name.startsWith(CORE) || !name.endsWith(".json")) continue;
        Matcher vertex = vertexName.matcher(read(file, name));
        assertTrue(vertex.find(), name);
        List<String> list = new ArrayList<>();
        Matcher in = input.matcher(read(file, CORE + vertex.group(1) + ".vsh"));
        while (in.find()) list.add(in.group(1));
        programs.put(name.substring(CORE.length(), name.length() - 5), list);
      }
    }
    assertFalse(programs.isEmpty());
    return programs;
  }

  private static String read(JarFile file, String name) throws IOException {
    try (InputStream input = file.getInputStream(file.getEntry(name))) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
