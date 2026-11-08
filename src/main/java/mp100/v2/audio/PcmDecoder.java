package mp100.v2.audio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

interface PcmDecoder extends AutoCloseable {
    interface Factory {
        SourceFormat probe(Path path, BooleanSupplier cancelled) throws IOException, InterruptedException;

        PcmDecoder open(Path path, double startSeconds, PcmFormat format) throws IOException;
    }

    // Returns zero if a live decoder has no bytes ready, and -1 after a successful EOF.
    int read(byte[] buffer, int offset, int length) throws IOException;

    @Override
    void close() throws IOException;
}
