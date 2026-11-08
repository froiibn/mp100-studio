package mp100.v2;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record Track(UUID id, Path path) {
    public Track {
        Objects.requireNonNull(id);
        path = path.toAbsolutePath().normalize();
    }

    public Track(Path path) {
        this(UUID.randomUUID(), path);
    }

    public String name() {
        return path.getFileName().toString();
    }

    public String format() {
        String name = name();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toUpperCase(Locale.ROOT);
    }
}
