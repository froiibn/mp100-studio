package mp100.v2.audio;

import javax.sound.sampled.LineUnavailableException;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static mp100.v2.audio.PcmFormat.Sample.*;

final class OutputFormats {
    private OutputFormats() {
    }

    static PcmOutput open(SourceFormat source, BooleanSupplier cancelled) throws LineUnavailableException {
        if (WasapiOutput.isAvailable()) {
            try {
                return choose(source, WasapiOutput.defaultSampleRate(), WasapiOutput::new, cancelled);
            } catch (LineUnavailableException ignored) {
                // Java Sound remains available when the native endpoint cannot be opened.
            }
        }
        return choose(source, 48_000, JavaSoundOutput::new, cancelled);
    }

    static PcmOutput choose(SourceFormat source, int deviceRate, PcmOutput.Factory factory,
                            BooleanSupplier cancelled) throws LineUnavailableException {
        int[] rates = IntStream.of(source.sampleRate(), deviceRate, 48_000, 44_100)
                .filter(rate -> rate > 0).distinct().toArray();
        int[] channels = IntStream.of(Math.min(source.channels(), 2), 2, 1).distinct().toArray();
        PcmFormat.Sample[] samples = sampleOrder(source);
        Exception last = null;
        for (int rate : rates) {
            for (int count : channels) {
                for (PcmFormat.Sample sample : samples) {
                    if (cancelled.getAsBoolean()) {
                        throw new CancellationException();
                    }
                    try {
                        return factory.open(new PcmFormat(rate, count, sample));
                    } catch (LineUnavailableException | IllegalArgumentException e) {
                        last = e;
                    }
                }
            }
        }
        LineUnavailableException error = new LineUnavailableException("No supported format for the default audio output");
        error.initCause(last);
        throw error;
    }

    private static PcmFormat.Sample[] sampleOrder(SourceFormat source) {
        if (source.floating()) {
            return source.bitDepth() > 32
                    ? new PcmFormat.Sample[]{F64, F32, S32, S24, S16, S8}
                    : new PcmFormat.Sample[]{F32, F64, S32, S24, S16, S8};
        }
        return switch (source.bitDepth()) {
            case 0 -> new PcmFormat.Sample[]{F32, S24, S32, S16, S8};
            case 8 -> new PcmFormat.Sample[]{S8, S16, S24, S32, F32};
            case 16 -> new PcmFormat.Sample[]{S16, S24, S32, F32, S8};
            case 24 -> new PcmFormat.Sample[]{S24, S32, F32, S16, S8};
            default -> source.bitDepth() < 24
                    ? new PcmFormat.Sample[]{S24, S32, F32, S16, S8}
                    : new PcmFormat.Sample[]{S32, F64, F32, S24, S16, S8};
        };
    }
}
