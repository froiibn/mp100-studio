package mp100;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

// Calls from the UI and queued engine callbacks share one event thread.
final class PlaybackController {
    static final String IDLE_MESSAGE = "Music Player Ver.1.00";
    private static final String NO_SELECTION = "File is not selected";

    enum State { IDLE, PREPARING, PLAYING }

    record View(State state, String message, double elapsedSeconds, double durationSeconds) {
    }

    private final PlaybackEngine engine;
    private final Executor events;
    private final Consumer<View> render;
    private View view = new View(State.IDLE, IDLE_MESSAGE, 0, Double.NaN);
    private Session current;

    PlaybackController(PlaybackEngine engine, Executor events, Consumer<View> render) {
        this.engine = engine;
        this.events = events;
        this.render = render;
        render.accept(view);
    }

    void play(Track track) {
        if (track == null) {
            noSelection();
            return;
        }
        if (current != null && current.track == track) {
            update(new View(view.state(), track.name(), view.elapsedSeconds(), view.durationSeconds()));
            return;
        }

        cancel();
        current = new Session(track);
        update(new View(State.PREPARING, track.name(), 0, Double.NaN));
        engine.play(track.path(), current);
    }

    void stop() {
        finish(current == null ? "Nothing for stop" : IDLE_MESSAGE);
    }

    void trackRemoved(Track track) {
        if (track == null) {
            noSelection();
        } else if (current != null && current.track == track) {
            finish(IDLE_MESSAGE);
        }
    }

    void close() {
        cancel();
    }

    private void noSelection() {
        update(new View(view.state(), NO_SELECTION, view.elapsedSeconds(), view.durationSeconds()));
    }

    private void finish(String message) {
        cancel();
        update(new View(State.IDLE, message, 0, Double.NaN));
    }

    private void cancel() {
        if (current != null) {
            current = null;
            engine.stop();
        }
    }

    private void update(View next) {
        view = next;
        render.accept(view);
    }

    private final class Session implements PlaybackEngine.Listener {
        private final Track track;

        Session(Track track) {
            this.track = track;
        }

        @Override
        public void started() {
            ifCurrent(() -> update(new View(State.PLAYING, track.name(),
                    view.elapsedSeconds(), view.durationSeconds())));
        }

        @Override
        public void positionChanged(double elapsedSeconds, double durationSeconds) {
            ifCurrent(() -> update(new View(view.state(), view.message(), elapsedSeconds, durationSeconds)));
        }

        @Override
        public void ended() {
            ifCurrent(() -> finish(IDLE_MESSAGE));
        }

        @Override
        public void failed(String reason) {
            ifCurrent(() -> finish("Cannot play " + track.name() + " (" + reason + ")"));
        }

        private void ifCurrent(Runnable action) {
            events.execute(() -> {
                if (current == this) {
                    action.run();
                }
            });
        }
    }
}
