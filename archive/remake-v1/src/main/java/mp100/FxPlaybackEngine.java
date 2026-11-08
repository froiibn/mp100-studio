package mp100;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

final class FxPlaybackEngine implements PlaybackEngine {
    private record Request(Path path, Listener listener) {
    }

    // Cancellation is visible immediately, before the JavaFX queue processes disposal.
    private final AtomicReference<Request> requested = new AtomicReference<>();
    private MediaPlayer player;

    @Override
    public void play(Path path, Listener listener) {
        Request request = new Request(path, listener);
        requested.set(request);
        Platform.runLater(() -> open(request));
    }

    @Override
    public void stop() {
        requested.set(null);
        Platform.runLater(this::disposePlayer);
    }

    private void open(Request request) {
        if (requested.get() != request) {
            return;
        }
        disposePlayer();
        if (!Files.isRegularFile(request.path()) || !Files.isReadable(request.path())) {
            fail(request, "file missing or unreadable");
            return;
        }
        try {
            Media media = new Media(request.path().toUri().toASCIIString());
            MediaPlayer opened = new MediaPlayer(media);
            player = opened;
            media.setOnError(() -> fail(request, describe(media.getError())));
            opened.setOnError(() -> fail(request, describe(opened.getError())));
            opened.setOnHalted(() -> fail(request, describe(opened.getError())));
            opened.setOnReady(() -> startIfRequested(request, opened));
            opened.setOnPlaying(() -> {
                if (requested.get() == request) {
                    request.listener().started();
                    publishPosition(request, opened);
                }
            });
            opened.setOnEndOfMedia(() -> {
                if (requested.compareAndSet(request, null)) {
                    disposePlayer();
                    request.listener().ended();
                }
            });
            opened.currentTimeProperty().addListener(observable -> publishPosition(request, opened));
            opened.totalDurationProperty().addListener(observable -> publishPosition(request, opened));

            if (opened.getError() != null || media.getError() != null) {
                fail(request, describe(opened.getError() != null ? opened.getError() : media.getError()));
            } else if (opened.getStatus() == MediaPlayer.Status.READY) {
                startIfRequested(request, opened);
            }
        } catch (MediaException | IllegalArgumentException e) {
            fail(request, describe(e));
        }
    }

    private void startIfRequested(Request request, MediaPlayer opened) {
        if (requested.get() == request) {
            opened.play();
        }
    }

    private void publishPosition(Request request, MediaPlayer opened) {
        if (requested.get() == request && opened.getStatus() == MediaPlayer.Status.PLAYING) {
            double elapsed = opened.getCurrentTime().toSeconds();
            double duration = opened.getTotalDuration().toSeconds();
            request.listener().positionChanged(Double.isFinite(elapsed) ? Math.max(0, elapsed) : 0, duration);
        }
    }

    private void fail(Request request, String reason) {
        if (requested.compareAndSet(request, null)) {
            disposePlayer();
            request.listener().failed(reason);
        }
    }

    private static String describe(RuntimeException error) {
        return error == null || error.getMessage() == null ? "audio playback error" : error.getMessage();
    }

    private void disposePlayer() {
        if (player != null) {
            MediaPlayer previous = player;
            player = null;
            previous.dispose();
        }
    }
}
