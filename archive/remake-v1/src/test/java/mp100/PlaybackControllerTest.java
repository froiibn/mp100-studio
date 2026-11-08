package mp100;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static mp100.PlaybackController.State.IDLE;
import static mp100.PlaybackController.State.PLAYING;
import static mp100.PlaybackController.State.PREPARING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackControllerTest {
    private final FakeEngine engine = new FakeEngine();
    private final Queue<Runnable> events = new ArrayDeque<>();
    private final Track first = new Track(Path.of("first.mp3"));
    private final Track second = new Track(Path.of("second.mp3"));
    private PlaybackController.View view;
    private final PlaybackController controller = new PlaybackController(engine, events::add, next -> view = next);

    @Test
    void repeatedlyStopsAndReplaysFromTheBeginning() {
        for (int attempt = 0; attempt < 3; attempt++) {
            controller.play(first);
            assertEquals(PREPARING, view.state());
            assertEquals(0, view.elapsedSeconds());
            engine.latest().started();
            engine.latest().positionChanged(4, 10);
            drainEvents();
            assertEquals(PLAYING, view.state());
            assertEquals(4, view.elapsedSeconds());
            assertEquals(10, view.durationSeconds());

            controller.stop();
            assertIdle();
        }
        assertEquals(List.of("play first.mp3", "stop", "play first.mp3", "stop", "play first.mp3", "stop"),
                engine.calls);
    }

    @Test
    void sameItemKeepsPositionAndSwitchingDiscardsOldEvents() {
        controller.play(first);
        PlaybackEngine.Listener old = engine.latest();
        old.started();
        old.positionChanged(5, 20);
        drainEvents();

        controller.play(first);
        assertEquals(5, view.elapsedSeconds());
        assertEquals(List.of("play first.mp3"), engine.calls);

        old.ended();
        controller.play(second);
        engine.latest().started();
        old.positionChanged(10, 20);
        old.failed("late error");
        drainEvents();
        assertEquals(List.of("play first.mp3", "stop", "play second.mp3"), engine.calls);
        assertEquals(PLAYING, view.state());
        assertEquals("second.mp3", view.message());
        assertEquals(0, view.elapsedSeconds());
    }

    @Test
    void deletingOneCopyOfAFileDoesNotStopAnotherCopy() {
        Track duplicate = new Track(first.path());
        controller.play(first);
        engine.latest().started();
        drainEvents();

        controller.trackRemoved(duplicate);
        assertEquals(PLAYING, view.state());
        assertEquals(List.of("play first.mp3"), engine.calls);

        controller.trackRemoved(first);
        assertIdle();
        assertEquals(List.of("play first.mp3", "stop"), engine.calls);
    }

    @Test
    void naturalEndAndFailureBothAllowAnotherPlayback() {
        controller.play(first);
        engine.latest().started();
        engine.latest().positionChanged(4, 4);
        engine.latest().ended();
        drainEvents();
        assertIdle();

        controller.play(second);
        engine.latest().failed("file missing or unreadable");
        drainEvents();
        assertEquals(IDLE, view.state());
        assertEquals(0, view.elapsedSeconds());
        assertTrue(view.message().contains("second.mp3"));

        controller.play(first);
        engine.latest().started();
        drainEvents();
        assertEquals(PLAYING, view.state());
    }

    @Test
    void stoppingDuringPreparationInvalidatesLateStartAndEnd() {
        controller.play(first);
        PlaybackEngine.Listener stopped = engine.latest();
        controller.stop();
        stopped.started();
        stopped.positionChanged(1, 5);
        stopped.ended();
        drainEvents();
        assertIdle();
        assertEquals(List.of("play first.mp3", "stop"), engine.calls);
    }

    @Test
    void emptySelectionAndIdleStopReportFeedback() {
        controller.play(null);
        assertEquals("File is not selected", view.message());
        controller.trackRemoved(null);
        assertEquals("File is not selected", view.message());
        controller.stop();
        assertEquals("Nothing for stop", view.message());
        assertTrue(engine.calls.isEmpty());
    }

    private void assertIdle() {
        assertEquals(IDLE, view.state());
        assertEquals(PlaybackController.IDLE_MESSAGE, view.message());
        assertEquals(0, view.elapsedSeconds());
        assertTrue(Double.isNaN(view.durationSeconds()));
    }

    private void drainEvents() {
        while (!events.isEmpty()) {
            events.remove().run();
        }
    }

    private static final class FakeEngine implements PlaybackEngine {
        private final List<String> calls = new ArrayList<>();
        private Listener latest;

        @Override
        public void play(Path path, Listener listener) {
            calls.add("play " + path);
            latest = listener;
        }

        @Override
        public void stop() {
            calls.add("stop");
        }

        Listener latest() {
            return latest;
        }
    }
}
