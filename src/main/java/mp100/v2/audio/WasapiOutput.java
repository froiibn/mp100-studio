package mp100.v2.audio;

import javax.sound.sampled.LineUnavailableException;
import java.nio.file.Path;

final class WasapiOutput implements PcmOutput {
    private static final boolean AVAILABLE = loadLibrary();
    private final PcmFormat format;
    private final long handle;

    WasapiOutput(PcmFormat format) throws LineUnavailableException {
        this.format = format;
        handle = open0(format.sampleRate(), format.channels(), format.sample().bits(), format.sample().floating());
        if (handle == 0) {
            throw new LineUnavailableException("WASAPI shared output rejected " + format);
        }
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    private static boolean loadLibrary() {
        String path = System.getenv("MP100_WASAPI_DLL");
        if (path == null || path.isBlank()) {
            path = System.getProperty("mp100.wasapi.dll");
        }
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            System.load(Path.of(path).toAbsolutePath().toString());
            return true;
        } catch (UnsatisfiedLinkError | IllegalArgumentException | SecurityException e) {
            return false;
        }
    }

    @Override
    public PcmFormat format() {
        return format;
    }

    @Override
    public String backend() {
        return "WASAPI 공유";
    }

    @Override
    public void start() {
        setRunning0(handle, true);
    }

    @Override
    public void pause() {
        setRunning0(handle, false);
    }

    @Override
    public int available() {
        return available0(handle);
    }

    @Override
    public int write(byte[] samples, int length) {
        return write0(handle, samples, length);
    }

    @Override
    public long framesPlayed() {
        return framesPlayed0(handle);
    }

    @Override
    public void close() {
        close0(handle);
    }

    static native int defaultSampleRate();

    private static native long open0(int rate, int channels, int bits, boolean floating);
    private static native void setRunning0(long handle, boolean running);
    private static native int available0(long handle);
    private static native int write0(long handle, byte[] samples, int length);
    private static native long framesPlayed0(long handle);
    private static native void close0(long handle);
}
