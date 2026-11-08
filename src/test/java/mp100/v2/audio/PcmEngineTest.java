package mp100.v2.audio;

import mp100.v2.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static mp100.v2.audio.PcmEngine.State.ENDED;
import static mp100.v2.audio.PcmEngine.State.ERROR;
import static mp100.v2.audio.PcmEngine.State.IDLE;
import static mp100.v2.audio.PcmEngine.State.PAUSED;
import static mp100.v2.audio.PcmEngine.State.PLAYING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class PcmEngineTest {
    private static final Track FIRST = new Track(Path.of("first.wav"));
    private static final Track SECOND = new Track(Path.of("second.wav"));
    private static final PcmFormat STEREO_16 = new PcmFormat(48_000, 2, PcmFormat.Sample.S16);

    @Test
    void gainChangesPcmSamplesAndBothMeterChannels() {
        byte[] raw = stereo((short) 16_000, (short) -8_000);
        byte[] output = new byte[raw.length];
        PcmSamples.Level half = PcmSamples.applyGain(STEREO_16, raw, output, raw.length, 0.5);
        ByteBuffer samples = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(8_000, samples.getShort());
        assertEquals(-4_000, samples.getShort());
        assertEquals(8_000 / 32768.0, half.left());
        assertEquals(4_000 / 32768.0, half.right());
        assertEquals(PcmSamples.Level.SILENT, PcmSamples.applyGain(STEREO_16, raw, output, raw.length, 0));
        assertEquals(0, ByteBuffer.wrap(output).getInt());
    }

    @Test
    void preservesAll24BitSamplesAtUnityGainAndMetersMono() {
        PcmFormat mono = new PcmFormat(96_000, 1, PcmFormat.Sample.S24);
        byte[] raw = {0x35, 0x12, (byte) 0x80};
        byte[] output = new byte[raw.length];
        PcmSamples.Level level = PcmSamples.applyGain(mono, raw, output, raw.length, 1);
        assertArrayEquals(raw, output);
        assertEquals(Math.abs(-0x7fedcb) / 8_388_608.0, level.left());
        assertEquals(level.left(), level.right());
    }

    @Test
    void measuresFloatAfterGain() {
        PcmFormat mono = new PcmFormat(44_100, 1, PcmFormat.Sample.F32);
        byte[] raw = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(-0.75f).array();
        byte[] output = new byte[raw.length];
        PcmSamples.Level level = PcmSamples.applyGain(mono, raw, output, raw.length, 0.5);
        assertEquals(-0.375f, ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN).getFloat());
        assertEquals(new PcmSamples.Level(0.375, 0.375), level);
    }

    @Test
    void negotiatesNativeFormatBeforeFallbackAndUsesOutputClock() throws Exception {
        SourceFormat source = new SourceFormat(44_100, 24, 2, false, 10);
        PcmFormat nativeFormat = new PcmFormat(44_100, 2, PcmFormat.Sample.S24);
        try (PcmOutput exact = OutputFormats.choose(source, 48_000, format -> {
            assertEquals(nativeFormat, format);
            return new FakeOutput(format, () -> { });
        }, () -> false)) {
            assertEquals(nativeFormat, exact.format());
        }

        PcmFormat fallback = new PcmFormat(44_100, 2, PcmFormat.Sample.S16);
        Fixture fixture = new Fixture(882, source);
        try (PcmEngine engine = new PcmEngine(fixture, format -> {
            if (!fallback.equals(format)) {
                throw new LineUnavailableException("Unsupported test format");
            }
            return fixture.open(format);
        })) {
            engine.play(FIRST);
            await(() -> engine.snapshot().state() == PLAYING);
            assertEquals(source, engine.snapshot().source());
            assertEquals(fallback, engine.snapshot().output().pcm());
            fixture.outputs.getFirst().played.set(441);
            await(() -> engine.snapshot().position() == 0.01);
        }
    }

    @Test
    void clockUsesConsumedFramesAndPauseRetainsBufferedSamples() throws Exception {
        Fixture fixture = new Fixture(960);
        try (PcmEngine engine = fixture.engine()) {
            engine.setGain(0.5);
            engine.play(FIRST);
            await(() -> engine.snapshot().state() == PLAYING && engine.snapshot().leftLevel() > 0);
            FakeOutput output = fixture.outputs.getFirst();
            assertEquals(0, engine.snapshot().position());
            assertEquals(8_000, output.firstLeft);
            assertEquals(8_000 / 32768.0, engine.snapshot().leftLevel());
            output.played.set(480);
            await(() -> engine.snapshot().position() == 0.01);

            engine.pause();
            await(() -> output.paused);
            assertEquals(PAUSED, engine.snapshot().state());
            assertEquals(0, engine.snapshot().leftLevel());
            assertEquals(0.01, engine.snapshot().position());
            engine.resume();
            await(() -> !output.paused && engine.snapshot().state() == PLAYING);
            assertEquals(1, fixture.outputs.size());

            output.played.set(960);
            await(() -> engine.snapshot().state() == ENDED);
            assertEquals(0.02, engine.snapshot().position());
            assertEquals(0.02, engine.snapshot().duration());
        }
    }

    @Test
    void rapidSeeksKeepOnlyLatestRequestAndCloseOldOutputBeforeOpeningNewOne() throws Exception {
        Fixture fixture = new Fixture(960);
        fixture.blockFirstClose = true;
        try (PcmEngine engine = fixture.engine()) {
            try {
                engine.play(FIRST);
                await(() -> engine.snapshot().state() == PLAYING);
                engine.seek(2);
                assertTrue(fixture.closing.await(2, TimeUnit.SECONDS));
                engine.seek(4);
                engine.seek(7);
                assertEquals(7, engine.snapshot().position());
                assertEquals(List.of(0.0), fixture.starts);
            } finally {
                fixture.releaseClose.countDown();
            }
            await(() -> engine.snapshot().state() == PLAYING);
            assertEquals(List.of(0.0, 7.0), fixture.starts);
            assertEquals(7, engine.snapshot().position());
            assertTrue(fixture.outputs.getFirst().closed);
            assertFalse(fixture.overlappingOutputs);
        }
    }

    @Test
    void pausedSeekDoesNotWriteBeforeResumeAndStopAllowsImmediateReplay() throws Exception {
        Fixture fixture = new Fixture(960);
        try (PcmEngine engine = fixture.engine()) {
            engine.play(FIRST);
            await(() -> engine.snapshot().state() == PLAYING);
            engine.pause();
            engine.seek(3);
            await(() -> fixture.outputs.size() == 2);
            FakeOutput sought = fixture.outputs.getLast();
            assertEquals(PAUSED, engine.snapshot().state());
            assertEquals(0, sought.written.get());
            engine.resume();
            await(() -> engine.snapshot().state() == PLAYING);
            assertEquals(3, engine.snapshot().position());

            engine.stop();
            engine.play(SECOND);
            await(() -> engine.snapshot().state() == PLAYING && SECOND.equals(engine.snapshot().track()));
            assertEquals(0, engine.snapshot().position());
            assertTrue(sought.closed);
            assertFalse(fixture.overlappingOutputs);
        }
    }

    @Test
    void deletingDuplicateDoesNotStopActiveTrackAndDecodeFailureAllowsRecovery() throws Exception {
        Fixture fixture = new Fixture(960);
        try (PcmEngine engine = fixture.engine()) {
            Track broken = new Track(Path.of("broken.wav"));
            engine.play(broken);
            await(() -> engine.snapshot().state() == ERROR);
            assertEquals("bad audio", engine.snapshot().error());

            engine.play(FIRST);
            await(() -> engine.snapshot().state() == PLAYING);
            engine.trackRemoved(new Track(FIRST.path()));
            assertEquals(FIRST, engine.snapshot().track());
            engine.trackRemoved(FIRST);
            assertEquals(IDLE, engine.snapshot().state());
            await(() -> fixture.outputs.getLast().closed);
        }
    }

    static void await(BooleanSupplier ready) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(ready.getAsBoolean(), "Timed out waiting for playback state");
    }

    private static byte[] stereo(short left, short right) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(left).putShort(right).array();
    }

    private static final class Fixture implements PcmDecoder.Factory, PcmOutput.Factory {
        final List<Double> starts = new CopyOnWriteArrayList<>();
        final List<FakeOutput> outputs = new CopyOnWriteArrayList<>();
        final CountDownLatch closing = new CountDownLatch(1);
        final CountDownLatch releaseClose = new CountDownLatch(1);
        final int frames;
        final SourceFormat source;
        volatile boolean overlappingOutputs;
        boolean blockFirstClose;

        Fixture(int frames) {
            this(frames, new SourceFormat(48_000, 16, 2, false, 10));
        }

        Fixture(int frames, SourceFormat source) {
            this.frames = frames;
            this.source = source;
        }

        PcmEngine engine() {
            return new PcmEngine(this, this);
        }

        @Override
        public SourceFormat probe(Path path, BooleanSupplier cancelled) {
            return source;
        }

        @Override
        public PcmDecoder open(Path path, double startSeconds, PcmFormat format) throws IOException {
            starts.add(startSeconds);
            if (path.getFileName().toString().equals("broken.wav")) {
                throw new IOException("bad audio");
            }
            return new PcmDecoder() {
                private final byte[] frame = stereo((short) 16_000, (short) -8_000);
                private int consumed;

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    int count = Math.min(Math.min(length, 701), frames * frame.length - consumed);
                    for (int i = 0; i < count; i++) {
                        buffer[offset + i] = frame[consumed++ % frame.length];
                    }
                    return count == 0 ? -1 : count;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public PcmOutput open(PcmFormat format) {
            if (!outputs.isEmpty() && !outputs.getLast().closed) {
                overlappingOutputs = true;
            }
            boolean waitForTest = blockFirstClose && outputs.isEmpty();
            FakeOutput output = new FakeOutput(format, waitForTest ? () -> {
                closing.countDown();
                try {
                    releaseClose.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } : () -> { });
            outputs.add(output);
            return output;
        }
    }

    private static final class FakeOutput implements PcmOutput {
        final AtomicLong written = new AtomicLong();
        final AtomicLong played = new AtomicLong();
        private final Runnable beforeClose;
        private final PcmFormat format;
        volatile boolean paused = true;
        volatile boolean closed;
        volatile int firstLeft;

        FakeOutput(PcmFormat format, Runnable beforeClose) {
            this.format = format;
            this.beforeClose = beforeClose;
        }

        @Override
        public PcmFormat format() {
            return format;
        }

        @Override
        public String backend() {
            return "Fake output";
        }

        @Override
        public void start() {
            paused = false;
        }

        @Override
        public void pause() {
            paused = true;
        }

        @Override
        public int available() {
            return format.chunkBytes();
        }

        @Override
        public int write(byte[] samples, int length) {
            if (written.getAndAdd(length / format.frameBytes()) == 0) {
                firstLeft = ByteBuffer.wrap(samples).order(ByteOrder.LITTLE_ENDIAN).getShort();
            }
            return length;
        }

        @Override
        public long framesPlayed() {
            return played.get();
        }

        @Override
        public void close() {
            beforeClose.run();
            closed = true;
        }
    }
}
