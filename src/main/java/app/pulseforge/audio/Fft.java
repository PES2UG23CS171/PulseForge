package app.pulseforge.audio;

/** In-place radix-2 complex FFT with precomputed tables. */
final class Fft {
    private final int size;
    private final int[] reversed;
    private final double[] cos;
    private final double[] sin;

    Fft(int size) {
        if (size < 2 || Integer.bitCount(size) != 1) throw new IllegalArgumentException("Size must be a power of two");
        this.size = size;
        int bits = Integer.numberOfTrailingZeros(size);
        reversed = new int[size];
        for (int i = 0; i < size; i++) reversed[i] = Integer.reverse(i) >>> (32 - bits);
        cos = new double[size / 2];
        sin = new double[size / 2];
        for (int i = 0; i < size / 2; i++) {
            cos[i] = Math.cos(2 * Math.PI * i / size);
            sin[i] = Math.sin(2 * Math.PI * i / size);
        }
    }

    int size() { return size; }

    void transform(double[] re, double[] im, boolean inverse) {
        for (int i = 0; i < size; i++) {
            int j = reversed[i];
            if (j > i) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int length = 2; length <= size; length <<= 1) {
            int half = length >> 1;
            int step = size / length;
            for (int start = 0; start < size; start += length) {
                for (int j = 0, k = 0; j < half; j++, k += step) {
                    double wr = cos[k];
                    double wi = inverse ? sin[k] : -sin[k];
                    int a = start + j;
                    int b = a + half;
                    double tr = re[b] * wr - im[b] * wi;
                    double ti = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
        if (inverse) for (int i = 0; i < size; i++) { re[i] /= size; im[i] /= size; }
    }
}
