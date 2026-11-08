package mp100;

import java.nio.file.Path;

interface PlaybackEngine {
    void play(Path path, Listener listener);

    void stop();

    interface Listener {
        void started();

        void positionChanged(double elapsedSeconds, double durationSeconds);

        void ended();

        void failed(String reason);
    }
}
