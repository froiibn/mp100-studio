package mp100.v2.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

final class FfmpegDecoder implements PcmDecoder {
    private final Process process;
    private final InputStream pcm;

    private FfmpegDecoder(Process process) {
        this.process = process;
        pcm = process.getInputStream();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int available = pcm.available();
        if (available > 0) {
            return pcm.read(buffer, offset, Math.min(length, available));
        }
        if (process.isAlive()) {
            return 0;
        }
        int remaining = pcm.read(buffer, offset, length);
        if (remaining == -1 && process.exitValue() != 0) {
            throw new IOException("FFmpeg decoding failed (exit " + process.exitValue() + ")");
        }
        return remaining;
    }

    @Override
    public void close() throws IOException {
        process.destroyForcibly();
        pcm.close();
    }

    static final class Tools implements Factory {
        private static final int MAX_PROBE_BYTES = 16_384;
        private static final long PROBE_TIMEOUT_NANOS = Duration.ofSeconds(10).toNanos();
        private final String ffmpeg;
        private final String ffprobe;

        static Tools installed() {
            String configured = System.getenv("MP100_FFMPEG_DIR");
            String directory = configured == null || configured.isBlank()
                    ? System.getProperty("mp100.ffmpeg.dir") : configured;
            return new Tools(directory);
        }

        Tools(String directory) {
            ffmpeg = binary("ffmpeg", directory);
            ffprobe = binary("ffprobe", directory);
        }

        @Override
        public SourceFormat probe(Path path, BooleanSupplier cancelled) throws IOException, InterruptedException {
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IOException("File is missing or unreadable");
            }
            ProcessBuilder command = new ProcessBuilder(ffprobe, "-v", "error",
                    "-protocol_whitelist", "file,pipe", "-select_streams", "a:0",
                    "-show_entries", "stream=codec_name,codec_type,sample_rate,channels,bits_per_sample,bits_per_raw_sample,duration:format=duration",
                    "-of", "default=noprint_wrappers=1", path.toString()).redirectErrorStream(true);
            Process process = start(command);
            try (InputStream input = process.getInputStream()) {
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                long started = System.nanoTime();
                while (process.isAlive()) {
                    if (cancelled.getAsBoolean()) {
                        throw new CancellationException();
                    }
                    if (System.nanoTime() - started > PROBE_TIMEOUT_NANOS) {
                        throw new IOException("FFprobe timed out");
                    }
                    collect(input, result, input.available());
                    Thread.sleep(10);
                }
                collect(input, result, MAX_PROBE_BYTES);
                String report = result.toString(StandardCharsets.UTF_8);
                if (process.exitValue() != 0) {
                    throw new IOException(report.lines().findFirst().orElse("FFprobe could not read the file"));
                }
                Map<String, String> fields = new HashMap<>();
                report.lines().forEach(line -> {
                    int separator = line.indexOf('=');
                    if (separator > 0) {
                        fields.putIfAbsent(line.substring(0, separator), line.substring(separator + 1));
                    }
                });
                if (!"audio".equals(fields.get("codec_type"))) {
                    throw new IOException("No audio stream was found");
                }
                int rate = positiveInt(fields.get("sample_rate"));
                int channels = positiveInt(fields.get("channels"));
                if (rate == 0 || channels == 0) {
                    throw new IOException("The audio stream has no valid sample rate or channel count");
                }
                String codec = fields.getOrDefault("codec_name", "");
                int depth = 0;
                if (codec.equals("flac") || codec.matches("pcm_[suf][0-9].*")) {
                    depth = positiveInt(fields.get("bits_per_raw_sample"));
                    if (depth == 0) {
                        depth = positiveInt(fields.get("bits_per_sample"));
                    }
                }
                double duration = report.lines().filter(line -> line.startsWith("duration="))
                        .mapToDouble(Tools::parseDuration).filter(Double::isFinite).findFirst().orElse(Double.NaN);
                return new SourceFormat(rate, depth, channels, codec.startsWith("pcm_f"), duration);
            } finally {
                process.destroyForcibly();
            }
        }

        @Override
        public PcmDecoder open(Path path, double startSeconds, PcmFormat format) throws IOException {
            String muxer = format.sample().muxer();
            ProcessBuilder command = new ProcessBuilder(ffmpeg, "-nostdin", "-v", "error", "-xerror",
                    "-protocol_whitelist", "file,pipe", "-ss", String.format(Locale.ROOT, "%.6f", startSeconds),
                    "-i", path.toString(), "-map", "0:a:0", "-vn", "-sn", "-dn",
                    "-ac", Integer.toString(format.channels()), "-ar", Integer.toString(format.sampleRate()),
                    "-c:a", "pcm_" + muxer, "-f", muxer, "pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            return new FfmpegDecoder(start(command));
        }

        private static Process start(ProcessBuilder command) throws IOException {
            try {
                return command.start();
            } catch (IOException e) {
                throw new IOException("Cannot start FFmpeg tools. Check PATH or MP100_FFMPEG_DIR.", e);
            }
        }

        private static void collect(InputStream input, ByteArrayOutputStream result, int requested) throws IOException {
            int remaining = MAX_PROBE_BYTES - result.size();
            if (remaining == 0) {
                throw new IOException("FFprobe returned too much output");
            }
            result.write(input.readNBytes(Math.min(remaining, requested)));
        }

        private static double parseDuration(String line) {
            try {
                double value = Double.parseDouble(line.substring("duration=".length()));
                return value > 0 ? value : Double.NaN;
            } catch (NumberFormatException ignored) {
                return Double.NaN;
            }
        }

        private static int positiveInt(String text) {
            try {
                return Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private static String binary(String name, String directory) {
            if (directory == null || directory.isBlank()) {
                return name;
            }
            boolean windows = System.getProperty("os.name").startsWith("Windows");
            return Path.of(directory, name + (windows ? ".exe" : "")).toAbsolutePath().toString();
        }
    }
}
