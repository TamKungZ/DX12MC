package net.dx12mc.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record PackManifest(
    int format,
    int contract,
    Map<String, Program> programs,
    Map<String, String> overrides,
    Map<String, Uniform> uniforms,
    Map<String, Texture> textures,
    Map<String, Target> targets,
    Scene scene,
    List<Pass> passes,
    Materials materials) {

  public static final int FORMAT = 1;
  static final int MAX_PROGRAMS = 32;
  static final int MAX_UNIFORMS = 16;
  static final int MAX_TEXTURES = 8;
  static final int MAX_TARGETS = 8;
  static final int MAX_PASSES = 16;
  private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");
  private static final Pattern UNIFORM = Pattern.compile("u_[A-Za-z0-9_]{1,30}");
  private static final Pattern SAMPLER = Pattern.compile("s_[A-Za-z0-9_]{1,30}");
  private static final Pattern PROVIDER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
  private static final Pattern FILE = Pattern.compile("[a-z0-9_./-]{1,128}");
  public static final Set<String> FORMATS =
      Set.of("rgba8", "rgba16f", "rg16f", "r16f", "r32f", "rgb10a2", "rg8", "r8");

  public record Program(String vertex, String fragment, Map<String, Program> variants) {
    public Program forLine(String line) {
      Program variant = variants.get(line);
      return variant == null ? this : variant;
    }
  }

  public record Uniform(String type, int count, float[] value, String provider) {
    public int floats() {
      return count * (type.equals("mat4") ? 16 : 4);
    }
  }

  public record Texture(String file, boolean linear, boolean repeat) {}

  public record Target(String format, float scale) {}

  public record Attachment(String name, String format) {}

  public record Scene(List<Attachment> gbuffer, boolean depth) {}

  public record Pass(
      String stage,
      String program,
      Map<String, String> inputs,
      String output,
      String blend,
      boolean linear) {}

  public record Materials(
      boolean normal, boolean specular, int[] normalDefault, int[] specularDefault) {}

  public static PackManifest parse(String json) {
    JsonObject root;
    try {
      JsonElement element = JsonParser.parseString(json);
      if (!element.isJsonObject()) throw new InvalidPackException("pack.json is not a JSON object");
      root = element.getAsJsonObject();
    } catch (JsonParseException exception) {
      throw new InvalidPackException("pack.json is not valid JSON: " + exception.getMessage());
    }
    allow(
        root,
        "pack.json",
        "format",
        "contract",
        "programs",
        "overrides",
        "uniforms",
        "textures",
        "targets",
        "scene",
        "passes",
        "materials");
    int format = integer(root, "format", -1);
    if (format != FORMAT)
      throw new InvalidPackException("pack.json format " + format + " is not " + FORMAT);
    int contract = integer(root, "contract", -1);
    if (contract != ShaderContract.VERSION) {
      throw new InvalidPackException(
          "pack.json targets shader contract "
              + contract
              + ", this DX12MC supports "
              + ShaderContract.VERSION);
    }
    Map<String, Program> programs = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : object(root, "programs").entrySet()) {
      name(entry.getKey(), "program");
      programs.put(
          entry.getKey(),
          program(object(entry.getValue(), "program " + entry.getKey()), entry.getKey(), true));
    }
    limit(programs.size(), MAX_PROGRAMS, "programs");
    Map<String, String> overrides = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : object(root, "overrides").entrySet()) {
      overrides.put(entry.getKey(), string(entry.getValue(), "override " + entry.getKey()));
    }
    Map<String, Uniform> uniforms = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : object(root, "uniforms").entrySet()) {
      if (!UNIFORM.matcher(entry.getKey()).matches()) {
        throw new InvalidPackException(
            "uniform name " + entry.getKey() + " must match u_[A-Za-z0-9_]");
      }
      uniforms.put(
          entry.getKey(),
          uniform(object(entry.getValue(), "uniform " + entry.getKey()), entry.getKey()));
    }
    limit(uniforms.size(), MAX_UNIFORMS, "uniforms");
    Map<String, Texture> textures = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : object(root, "textures").entrySet()) {
      if (!SAMPLER.matcher(entry.getKey()).matches()) {
        throw new InvalidPackException(
            "texture name " + entry.getKey() + " must match s_[A-Za-z0-9_]");
      }
      JsonObject texture = object(entry.getValue(), "texture " + entry.getKey());
      allow(texture, "texture " + entry.getKey(), "file", "filter", "wrap");
      textures.put(
          entry.getKey(),
          new Texture(
              file(texture, "file"),
              choice(texture, "filter", "nearest", Set.of("nearest", "linear")).equals("linear"),
              choice(texture, "wrap", "clamp", Set.of("clamp", "repeat")).equals("repeat")));
    }
    limit(textures.size(), MAX_TEXTURES, "textures");
    Map<String, Target> targets = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : object(root, "targets").entrySet()) {
      name(entry.getKey(), "target");
      JsonObject target = object(entry.getValue(), "target " + entry.getKey());
      allow(target, "target " + entry.getKey(), "format", "scale");
      float scale = number(target, "scale", 1.0f);
      if (!(scale >= 0.125f && scale <= 2.0f)) {
        throw new InvalidPackException(
            "target " + entry.getKey() + " scale must be between 0.125 and 2");
      }
      targets.put(entry.getKey(), new Target(choice(target, "format", "rgba8", FORMATS), scale));
    }
    limit(targets.size(), MAX_TARGETS, "targets");
    Scene scene = null;
    if (root.has("scene")) {
      JsonObject sceneObject = object(root.get("scene"), "scene");
      allow(sceneObject, "scene", "gbuffer", "depth");
      List<Attachment> gbuffer = new ArrayList<>();
      for (JsonElement element : array(sceneObject, "gbuffer")) {
        JsonObject attachment = object(element, "scene.gbuffer entry");
        allow(attachment, "scene.gbuffer entry", "name", "format");
        String name = string(attachment.get("name"), "scene.gbuffer name");
        name(name, "scene.gbuffer");
        gbuffer.add(new Attachment(name, choice(attachment, "format", "rgba8", FORMATS)));
      }
      limit(gbuffer.size(), ShaderContract.MAX_SCENE_ATTACHMENTS, "scene.gbuffer attachments");
      scene = new Scene(List.copyOf(gbuffer), bool(sceneObject, "depth", false));
    }
    List<Pass> passes = new ArrayList<>();
    for (JsonElement element : array(root, "passes")) {
      JsonObject pass = object(element, "pass");
      allow(pass, "pass", "stage", "program", "inputs", "output", "blend", "filter");
      Map<String, String> inputs = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> input : object(pass, "inputs").entrySet()) {
        if (!SAMPLER.matcher(input.getKey()).matches()) {
          throw new InvalidPackException(
              "pass input " + input.getKey() + " must match s_[A-Za-z0-9_]");
        }
        inputs.put(input.getKey(), string(input.getValue(), "pass input " + input.getKey()));
      }
      passes.add(
          new Pass(
              choice(pass, "stage", null, Set.copyOf(ShaderContract.STAGES)),
              string(pass.get("program"), "pass program"),
              Map.copyOf(inputs),
              string(pass.get("output"), "pass output"),
              choice(pass, "blend", "none", Set.of("none", "alpha", "add")),
              choice(pass, "filter", "nearest", Set.of("nearest", "linear")).equals("linear")));
    }
    limit(passes.size(), MAX_PASSES, "passes");
    Materials materials = null;
    if (root.has("materials")) {
      JsonObject object = object(root.get("materials"), "materials");
      allow(object, "materials", "normal", "specular", "normalDefault", "specularDefault");
      materials =
          new Materials(
              bool(object, "normal", false),
              bool(object, "specular", false),
              rgba(object, "normalDefault", new int[] {128, 128, 255, 255}),
              rgba(object, "specularDefault", new int[] {0, 0, 0, 0}));
    }
    return new PackManifest(
        format,
        contract,
        Map.copyOf(programs),
        Map.copyOf(overrides),
        Map.copyOf(uniforms),
        Map.copyOf(textures),
        Map.copyOf(targets),
        scene,
        List.copyOf(passes),
        materials);
  }

  private static Program program(JsonObject object, String name, boolean allowVariants) {
    if (allowVariants) allow(object, "program " + name, "vertex", "fragment", "variants");
    else allow(object, "program " + name, "vertex", "fragment");
    String vertex = object.has("vertex") ? file(object, "vertex") : null;
    String fragment = file(object, "fragment");
    Map<String, Program> variants = new LinkedHashMap<>();
    if (allowVariants) {
      for (Map.Entry<String, JsonElement> entry : object(object, "variants").entrySet()) {
        if (!entry.getKey().matches("[0-9]+\\.[0-9]+(\\.[0-9]+)?")) {
          throw new InvalidPackException(
              "program " + name + " variant key must be a Minecraft version");
        }
        variants.put(entry.getKey(), program(object(entry.getValue(), "variant"), name, false));
      }
    }
    return new Program(vertex, fragment, Map.copyOf(variants));
  }

  private static Uniform uniform(JsonObject object, String name) {
    allow(object, "uniform " + name, "type", "count", "value", "provider");
    String type = choice(object, "type", "vec4", Set.of("vec4", "mat4"));
    int count = integer(object, "count", 1);
    if (count < 1 || count > 4)
      throw new InvalidPackException("uniform " + name + " count must be 1 to 4");
    if (type.equals("mat4") && count != 1)
      throw new InvalidPackException("mat4 uniform " + name + " count must be 1");
    String provider = object.has("provider") ? string(object.get("provider"), "provider") : null;
    if (provider != null && !PROVIDER.matcher(provider).matches()) {
      throw new InvalidPackException("uniform " + name + " provider must be a namespaced id");
    }
    float[] value = new float[count * (type.equals("mat4") ? 16 : 4)];
    if (object.has("value")) {
      if (provider != null)
        throw new InvalidPackException("uniform " + name + " has both value and provider");
      JsonArray values = array(object, "value");
      if (values.size() != value.length) {
        throw new InvalidPackException("uniform " + name + " needs " + value.length + " values");
      }
      for (int i = 0; i < value.length; i++) value[i] = number(values.get(i), "uniform " + name);
    }
    return new Uniform(type, count, value, provider);
  }

  private static int[] rgba(JsonObject object, String key, int[] fallback) {
    if (!object.has(key)) return fallback;
    JsonArray values = array(object, key);
    if (values.size() != 4) throw new InvalidPackException("materials." + key + " needs 4 values");
    int[] result = new int[4];
    for (int i = 0; i < 4; i++) {
      float value = number(values.get(i), "materials." + key);
      if (value < 0 || value > 255 || value != Math.rint(value)) {
        throw new InvalidPackException("materials." + key + " values must be integers 0 to 255");
      }
      result[i] = (int) value;
    }
    return result;
  }

  private static void allow(JsonObject object, String where, String... keys) {
    Set<String> allowed = Set.of(keys);
    for (String key : object.keySet()) {
      if (!allowed.contains(key)) throw new InvalidPackException(where + " has unknown key " + key);
    }
  }

  private static void limit(int count, int max, String what) {
    if (count > max) throw new InvalidPackException("at most " + max + " " + what + " are allowed");
  }

  private static void name(String name, String what) {
    if (!NAME.matcher(name).matches()) {
      throw new InvalidPackException(what + " name " + name + " must match [a-z0-9_]{1,32}");
    }
  }

  private static JsonObject object(JsonObject parent, String key) {
    return parent.has(key) ? object(parent.get(key), key) : new JsonObject();
  }

  private static JsonObject object(JsonElement element, String where) {
    if (element == null || !element.isJsonObject())
      throw new InvalidPackException(where + " must be an object");
    return element.getAsJsonObject();
  }

  private static JsonArray array(JsonObject parent, String key) {
    if (!parent.has(key)) return new JsonArray();
    JsonElement element = parent.get(key);
    if (!element.isJsonArray()) throw new InvalidPackException(key + " must be an array");
    return element.getAsJsonArray();
  }

  private static String string(JsonElement element, String where) {
    if (element == null || !element.isJsonPrimitive() || !((JsonPrimitive) element).isString()) {
      throw new InvalidPackException(where + " must be a string");
    }
    return element.getAsString();
  }

  private static String file(JsonObject object, String key) {
    String path = string(object.get(key), key);
    if (!FILE.matcher(path).matches() || path.contains("..") || path.startsWith("/")) {
      throw new InvalidPackException(key + " path " + path + " is not a plain relative path");
    }
    return path;
  }

  private static String choice(
      JsonObject object, String key, String fallback, Set<String> allowed) {
    if (!object.has(key)) {
      if (fallback == null) throw new InvalidPackException(key + " is required");
      return fallback;
    }
    String value = string(object.get(key), key);
    if (!allowed.contains(value))
      throw new InvalidPackException(key + " " + value + " is not one of " + allowed);
    return value;
  }

  private static int integer(JsonObject object, String key, int fallback) {
    if (!object.has(key)) return fallback;
    float value = number(object.get(key), key);
    if (value != Math.rint(value)) throw new InvalidPackException(key + " must be an integer");
    return (int) value;
  }

  private static float number(JsonObject object, String key, float fallback) {
    return object.has(key) ? number(object.get(key), key) : fallback;
  }

  private static float number(JsonElement element, String where) {
    if (element == null || !element.isJsonPrimitive() || !((JsonPrimitive) element).isNumber()) {
      throw new InvalidPackException(where + " must be a number");
    }
    float value = element.getAsFloat();
    if (!Float.isFinite(value)) throw new InvalidPackException(where + " must be finite");
    return value;
  }

  private static boolean bool(JsonObject object, String key, boolean fallback) {
    if (!object.has(key)) return fallback;
    JsonElement element = object.get(key);
    if (!element.isJsonPrimitive() || !((JsonPrimitive) element).isBoolean()) {
      throw new InvalidPackException(key + " must be true or false");
    }
    return element.getAsBoolean();
  }
}
