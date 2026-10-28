package net.dx12mc.bridge;

import static org.lwjgl.bgfx.BGFX.*;

import net.dx12mc.graphics.FrameSize;
import net.dx12mc.graphics.bgfx.Backend;
import net.dx12mc.graphics.bgfx.StagingMemory;
import net.dx12mc.graphics.pipeline.PipelineSet;
import org.lwjgl.bgfx.BGFXStats;

final class UploadBench {
  private static final int FRAMES = 240;
  private static final int WARMUP = 60;

  private UploadBench() {}

  static void run(Backend backend, PipelineSet pipelines) {
    for (int[] shape :
        new int[][] {
          {0, 0},
          {1, 160 << 10},
          {4, 160 << 10},
          {16, 160 << 10},
          {16, 10 << 10},
          {64, 10 << 10},
          {1, 2560 << 10},
          {4, 640 << 10}
        }) {
      measure(backend, pipelines, shape[0], shape[1]);
    }
    for (int pages : new int[] {4, 16}) arena(backend, pipelines, pages, 160 << 10);
  }

  private static void arena(Backend backend, PipelineSet pipelines, int pages, int bytes) {
    int vertices = Math.max(4, bytes / 28 / 4 * 4);
    net.dx12mc.graphics.bgfx.VertexArena arena =
        new net.dx12mc.graphics.bgfx.VertexArena(pipelines.layout(), 28);
    java.util.List<net.dx12mc.graphics.bgfx.VertexArena.Allocation> live =
        new java.util.ArrayList<>();
    double waitRender = 0;
    double frame = 0;
    for (int f = 0; f < WARMUP + FRAMES; f++) {
      backend.beginFrame(new FrameSize(64, 64));
      for (net.dx12mc.graphics.bgfx.VertexArena.Allocation old : live) arena.free(old);
      live.clear();
      for (int p = 0; p < pages; p++) live.add(arena.allocate(vertices, data -> {}));
      arena.flush();
      backend.endFrame();
      arena.onFrameBoundary();
      if (f < WARMUP) continue;
      BGFXStats stats = bgfx_get_stats();
      waitRender += stats.waitRender() * 1000.0 / stats.cpuTimerFreq();
      frame += stats.cpuTimeFrame() * 1000.0 / stats.cpuTimerFreq();
    }
    arena.close();
    backend.beginFrame(new FrameSize(64, 64));
    backend.endFrame();
    System.out.printf(
        "arena  %3d pages x %6d KiB per frame: frame %.3f ms, waitRender %.3f ms%n",
        pages, vertices * 28 / 1024, frame / FRAMES, waitRender / FRAMES);
  }

  private static void measure(Backend backend, PipelineSet pipelines, int calls, int bytes) {
    int vertices = Math.max(1, bytes / 28);
    short[] buffers = new short[calls];
    for (int i = 0; i < calls; i++) {
      buffers[i] =
          bgfx_create_dynamic_vertex_buffer(vertices, pipelines.layout(), BGFX_BUFFER_NONE);
    }
    byte[] payload = new byte[vertices * 28];
    double waitRender = 0;
    double renderThread = 0;
    double frame = 0;
    for (int f = 0; f < WARMUP + FRAMES; f++) {
      backend.beginFrame(new FrameSize(64, 64));
      for (short buffer : buffers) {
        bgfx_update_dynamic_vertex_buffer(buffer, 0, StagingMemory.copy(payload));
      }
      backend.endFrame();
      if (f < WARMUP) continue;
      BGFXStats stats = bgfx_get_stats();
      waitRender += stats.waitRender() * 1000.0 / stats.cpuTimerFreq();
      frame += stats.cpuTimeFrame() * 1000.0 / stats.cpuTimerFreq();
      renderThread += (stats.cpuTimeEnd() - stats.cpuTimeBegin()) * 1000.0 / stats.cpuTimerFreq();
    }
    for (short buffer : buffers) bgfx_destroy_dynamic_vertex_buffer(buffer);
    backend.beginFrame(new FrameSize(64, 64));
    backend.endFrame();
    System.out.printf(
        "upload %3d calls x %6d KiB per frame: frame %.3f ms, render thread %.3f ms, waitRender"
            + " %.3f ms%n",
        calls, vertices * 28 / 1024, frame / FRAMES, renderThread / FRAMES, waitRender / FRAMES);
  }
}
