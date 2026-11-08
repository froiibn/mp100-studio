package mp100.v2.audio;

final class PcmSamples {
    record Level(double left, double right) {
        static final Level SILENT = new Level(0, 0);
    }

    private PcmSamples() {
    }

    static Level applyGain(PcmFormat format, byte[] input, byte[] output, int length, double gain) {
        int bits = format.sample().bits();
        int bytes = bits / Byte.SIZE;
        int frameBytes = format.frameBytes();
        boolean mono = format.channels() == 1;
        boolean floating = format.sample().floating();
        double fullScale = Math.scalb(1.0, bits - 1);
        if (gain == 1) {
            System.arraycopy(input, 0, output, 0, length);
        }
        double leftSquares = 0;
        double rightSquares = 0;
        for (int i = 0; i < length; i += frameBytes) {
            double left = scale(input, output, i, bytes, floating, fullScale, gain);
            double right = mono ? left : scale(input, output, i + bytes, bytes, floating, fullScale, gain);
            leftSquares += left * left;
            rightSquares += right * right;
        }
        int frames = length / frameBytes;
        return new Level(Math.sqrt(leftSquares / frames), Math.sqrt(rightSquares / frames));
    }

    private static double scale(byte[] input, byte[] output, int offset, int bytes,
                                boolean floating, double fullScale, double gain) {
        long raw = 0;
        for (int i = 0; i < bytes; i++) {
            raw |= (input[offset + i] & 0xffL) << (Byte.SIZE * i);
        }
        long scaled;
        double level;
        if (floating) {
            double sample = bytes == Float.BYTES ? Float.intBitsToFloat((int) raw) : Double.longBitsToDouble(raw);
            double value = gain == 0 ? 0 : sample * gain;
            if (bytes == Float.BYTES) {
                float rounded = (float) value;
                scaled = Float.floatToRawIntBits(rounded);
                level = rounded;
            } else {
                scaled = Double.doubleToRawLongBits(value);
                level = value;
            }
            if (!Double.isFinite(level)) {
                level = 0;
            }
        } else {
            int shift = Integer.SIZE - Byte.SIZE * bytes;
            int signed = ((int) raw << shift) >> shift;
            scaled = Math.round(signed * gain);
            level = scaled / fullScale;
        }
        if (gain != 1) {
            for (int i = 0; i < bytes; i++) {
                output[offset + i] = (byte) (scaled >>> (Byte.SIZE * i));
            }
        }
        return level;
    }
}
