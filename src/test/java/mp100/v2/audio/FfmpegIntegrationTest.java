package mp100.v2.audio;

import mp100.v2.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static mp100.v2.audio.PcmEngine.State.ENDED;
import static mp100.v2.audio.PcmEngine.State.ERROR;
import static mp100.v2.audio.PcmEngine.State.PLAYING;
import static mp100.v2.audio.PcmEngineTest.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
@EnabledIfSystemProperty(named = "mp100.test.ffmpeg", matches = "true")
class FfmpegIntegrationTest {
    private final FfmpegDecoder.Tools tools = new FfmpegDecoder.Tools(System.getenv("MP100_FFMPEG_DIR"));

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"mp3", "flac", "wav"})
    void decodesUnicodePathAndSeeksToKnownOffset(String extension) throws Exception {
        Path file = tone("테스트 곡." + extension);
        SourceFormat source = tools.probe(file, () -> false);
        double duration = source.duration();
        assertEquals(48_000, source.sampleRate());
        assertEquals(extension.equals("mp3") ? 0 : 16, source.bitDepth());
        assertEquals(1, source.channels());
        assertTrue(duration >= 1.55 && duration <= 1.68, "Reported duration was " + duration);
        PcmFormat format = new PcmFormat(48_000, 2, PcmFormat.Sample.S16);
        int total = 0;
        boolean signal = false;
        try (PcmDecoder decoder = tools.open(file, 0.6, format)) {
            byte[] data = new byte[3837];
            for (int read; (read = decoder.read(data, 0, data.length)) != -1; ) {
                if (read == 0) {
                    Thread.sleep(2);
                }
                total += read;
                for (int i = 0; i < read; i++) {
                    signal |= data[i] != 0;
                }
            }
        }
        assertTrue(signal);
        assertEquals(0, total % format.frameBytes());
        assertEquals(1.0, total / (double) (format.frameBytes() * format.sampleRate()), 0.04);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wav", "flac"})
    void probesAndDecodesNative24Bit96kHz(String extension) throws Exception {
        Path file = highResolutionTone("native." + extension, 96_000);
        SourceFormat source = tools.probe(file, () -> false);
        assertEquals(new SourceFormat(96_000, 24, 2, false, 1.6), source);
        PcmFormat format = new PcmFormat(96_000, 2, PcmFormat.Sample.S24);
        try (PcmDecoder decoder = tools.open(file, 0, format)) {
            byte[] bytes = new byte[format.chunkBytes()];
            int read;
            while ((read = decoder.read(bytes, 0, bytes.length)) == 0) {
                Thread.sleep(2);
            }
            assertTrue(read >= format.frameBytes());
            boolean lowBitsPresent = false;
            for (int i = 0; i < read; i += 3) {
                lowBitsPresent |= bytes[i] != 0;
            }
            assertTrue(lowBitsPresent, "Decoded samples should contain nonzero low bits");
        }
    }

    @Test
    void corruptFileProducesAnErrorSnapshot() throws Exception {
        Path invalid = Files.writeString(directory.resolve("broken.mp3"), "invalid audio");
        try (PcmEngine engine = new PcmEngine(tools, format -> {
            throw new AssertionError("Corrupt file should fail before opening audio output");
        })) {
            Track broken = new Track(invalid);
            engine.play(broken);
            await(() -> engine.snapshot().state() == ERROR);
            assertEquals(broken, engine.snapshot().track());
            assertNotNull(engine.snapshot().error());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "mp100.test.audioDevice", matches = "true")
    void writesToRealJavaSoundDeviceAndDrainsAfterSeeking() throws Exception {
        Track track = new Track(tone("device.wav"));
        try (PcmEngine engine = new PcmEngine(tools, JavaSoundOutput::new)) {
            engine.setGain(0.15);
            engine.play(track);
            await(() -> engine.snapshot().state() == ERROR || engine.snapshot().position() > 0.08);
            assertEquals(PLAYING, engine.snapshot().state(), () -> engine.snapshot().toString());
            engine.seek(0.8);
            await(() -> engine.snapshot().state() == ERROR || engine.snapshot().state() == ENDED);
            assertEquals(ENDED, engine.snapshot().state(), () -> engine.snapshot().toString());
            assertEquals(1.6, engine.snapshot().position(), 0.04);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "mp100.test.wasapi", matches = "true")
    void negotiatesHighPrecisionOnTheDefaultWindowsEndpoint() throws Exception {
        assertTrue(WasapiOutput.isAvailable(), "Load the compiled DLL via MP100_WASAPI_DLL");
        int rate = WasapiOutput.defaultSampleRate();
        assertTrue(rate > 0);
        Track track = new Track(highResolutionTone("wasapi.wav", rate));
        try (PcmEngine engine = PcmEngine.create()) {
            engine.setGain(0.08);
            engine.play(track);
            await(() -> engine.snapshot().state() == ERROR || engine.snapshot().position() > 0.08);
            assertEquals(PLAYING, engine.snapshot().state(), () -> engine.snapshot().toString());
            assertEquals(rate, engine.snapshot().source().sampleRate());
            assertEquals(24, engine.snapshot().source().bitDepth());
            PcmEngine.OutputFormat output = engine.snapshot().output();
            assertEquals("WASAPI 공유", output.backend());
            assertEquals(rate, output.pcm().sampleRate());
            assertTrue(output.pcm().sample().bits() >= 24, () -> output.toString());
            System.out.println("WASAPI actual output: " + output.pcm());
            engine.pause();
            await(() -> engine.snapshot().state() == PcmEngine.State.PAUSED);
            engine.seek(0.8);
            engine.resume();
            await(() -> engine.snapshot().state() == ERROR || engine.snapshot().state() == ENDED);
            assertEquals(ENDED, engine.snapshot().state(), () -> engine.snapshot().toString());
            assertEquals(1.6, engine.snapshot().position(), 0.04);
        }
    }

    private Path tone(String name) throws Exception {
        return tone(name, "sine=frequency=440:sample_rate=48000", List.of());
    }

    private Path highResolutionTone(String name, int rate) throws Exception {
        List<String> options = name.endsWith(".flac")
                ? List.of("-ac", "2", "-sample_fmt", "s32", "-bits_per_raw_sample", "24")
                : List.of("-ac", "2", "-c:a", "pcm_s24le");
        return tone(name, "aevalsrc=0.1*sin(2*PI*440*t):s=" + rate, options);
    }

    private Path tone(String name, String input, List<String> options) throws Exception {
        Path file = directory.resolve(name);
        String executable = "ffmpeg";
        String toolsDirectory = System.getenv("MP100_FFMPEG_DIR");
        if (toolsDirectory != null && !toolsDirectory.isBlank()) {
            boolean windows = System.getProperty("os.name").startsWith("Windows");
            executable = Path.of(toolsDirectory, windows ? "ffmpeg.exe" : "ffmpeg").toString();
        }
        List<String> command = new ArrayList<>(List.of(executable, "-nostdin", "-v", "error", "-f", "lavfi",
                "-i", input, "-t", "1.6"));
        command.addAll(options);
        command.add(file.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(8, TimeUnit.SECONDS), "FFmpeg did not finish fixture generation");
            assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
        } finally {
            process.destroyForcibly();
        }
        return file;
    }
}
