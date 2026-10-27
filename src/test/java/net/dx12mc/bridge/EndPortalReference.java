package net.dx12mc.bridge;

final class EndPortalReference {
  private static final double[] SCALE_TRANSLATE = {
    0.5, 0.0, 0.0, 0.25, 0.0, 0.5, 0.0, 0.25, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0
  };

  private EndPortalReference() {}

  static double[] color(
      double[][] colors,
      int[][] sky,
      int[][] portal,
      int layers,
      double gameTime,
      int pixelX,
      int pixelY,
      int size) {
    double ndcX = (pixelX + 0.5) / size * 2.0 - 1.0;
    double ndcY = 1.0 - (pixelY + 0.5) / size * 2.0;
    double[] texProj = {ndcX * 0.5 + 0.5, ndcY * 0.5 + 0.5, 0.0, 1.0};
    double[] color = new double[3];
    double[] first = tile(sky, texProj[0] / texProj[3], texProj[1] / texProj[3]);
    for (int channel = 0; channel < 3; channel++)
      color[channel] = first[channel] * colors[0][channel];
    for (int i = 0; i < layers; i++) {
      double[] projected = rowTimesMatrix(texProj, layer(i + 1, gameTime));
      double[] sample = tile(portal, projected[0] / projected[3], projected[1] / projected[3]);
      for (int channel = 0; channel < 3; channel++)
        color[channel] += sample[channel] * colors[i][channel];
    }
    for (int channel = 0; channel < 3; channel++) color[channel] = Math.min(1.0, color[channel]);
    return color;
  }

  static double[] layer(double layer, double gameTime) {
    double[] translate = {
      1.0,
      0.0,
      0.0,
      17.0 / layer,
      0.0,
      1.0,
      0.0,
      (2.0 + layer / 1.5) * (gameTime * 1.5),
      0.0,
      0.0,
      1.0,
      0.0,
      0.0,
      0.0,
      0.0,
      1.0
    };
    double radians = Math.toRadians((layer * layer * 4321.0 + layer * 9.0) * 2.0);
    double s = (4.5 - layer / 4.0) * 2.0;
    double[] rotate = {Math.cos(radians), -Math.sin(radians), Math.sin(radians), Math.cos(radians)};
    double[] scaleRotate = {s * rotate[0], s * rotate[1], s * rotate[2], s * rotate[3]};
    double[] scaleMatrix = {
      scaleRotate[0],
      scaleRotate[1],
      0.0,
      0.0,
      scaleRotate[2],
      scaleRotate[3],
      0.0,
      0.0,
      0.0,
      0.0,
      1.0,
      0.0,
      0.0,
      0.0,
      0.0,
      1.0
    };
    return multiply(multiply(scaleMatrix, translate), SCALE_TRANSLATE);
  }

  static double[] multiply(double[] a, double[] b) {
    double[] result = new double[16];
    for (int column = 0; column < 4; column++) {
      for (int row = 0; row < 4; row++) {
        double sum = 0.0;
        for (int k = 0; k < 4; k++) sum += a[k * 4 + row] * b[column * 4 + k];
        result[column * 4 + row] = sum;
      }
    }
    return result;
  }

  static double[] rowTimesMatrix(double[] v, double[] m) {
    double[] result = new double[4];
    for (int column = 0; column < 4; column++) {
      for (int i = 0; i < 4; i++) result[column] += v[i] * m[column * 4 + i];
    }
    return result;
  }

  private static double[] tile(int[][] tiles, double u, double v) {
    double fu = u - Math.floor(u);
    double fv = v - Math.floor(v);
    int[] rgb = tiles[(int) Math.floor(fv * 2.0) * 2 + (int) Math.floor(fu * 2.0)];
    return new double[] {rgb[0] / 255.0, rgb[1] / 255.0, rgb[2] / 255.0};
  }
}
