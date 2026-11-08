package mp100;

import java.nio.file.Path;

// Each row has its own identity, even when two rows refer to the same file.
final class Track {
    private final Path path;

    Track(Path path) {
        this.path = path;
    }

    Path path() {
        return path;
    }

    String name() {
        return path.getFileName().toString();
    }
}
