package mp100.v2.audio;

public record PcmFormat(int sampleRate, int channels, Sample sample) {
    public enum Sample {
        S8(8, false, "s8"),
        S16(16, false, "s16le"),
        S24(24, false, "s24le"),
        S32(32, false, "s32le"),
        F32(32, true, "f32le"),
        F64(64, true, "f64le");

        private final int bits;
        private final boolean floating;
        private final String muxer;

        Sample(int bits, boolean floating, String muxer) {
            this.bits = bits;
            this.floating = floating;
            this.muxer = muxer;
        }

        public int bits() {
            return bits;
        }

        public boolean floating() {
            return floating;
        }

        String muxer() {
            return muxer;
        }
    }

    int frameBytes() {
        return channels * sample.bits() / Byte.SIZE;
    }

    int chunkBytes() {
        return Math.max(1, sampleRate / 50) * frameBytes();
    }
}
