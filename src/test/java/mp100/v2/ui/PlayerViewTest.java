package mp100.v2.ui;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.application.ConditionalFeature;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import mp100.v2.Track;
import mp100.v2.audio.PcmEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerViewTest {
    @BeforeAll
    static void startJavaFxWithoutDesktop() throws Exception {
        System.setProperty("glass.platform", "headless");
        System.setProperty("prism.order", "sw");
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(8, TimeUnit.SECONDS));
    }

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }

    @Test
    void rendersStyledLayoutAndPreservesActiveTrackWhenRowsMoveOrDisappear() throws Exception {
        onFx(() -> {
            try (PcmEngine engine = PcmEngine.create()) {
                PlayerView view = new PlayerView(engine);
                Scene scene = view.scene();
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                WritableImage image = scene.getRoot().snapshot(null, null);
                assertEquals(1060, image.getWidth());
                assertEquals(720, image.getHeight());
                assertEquals(Color.web("#111316"), image.getPixelReader().getColor(2, 150));
                assertEquals(256, view.studioIcon().getWidth());
                assertFalse(view.studioIcon().isError());
                assertEquals(Color.web("#7eadd2"), button(scene, "#play").getBackground().getFills().getFirst().getFill());
                var segments = scene.getRoot().lookupAll(".meter-segment");
                assertEquals(48, segments.size());
                for (var node : segments) {
                    node.pseudoClassStateChanged(PseudoClass.getPseudoClass("lit"), true);
                }
                scene.getRoot().applyCss();
                for (var node : segments) {
                    var siblings = node.getParent().getChildrenUnmodifiable();
                    String expected = siblings.indexOf(node) >= siblings.size() - 3 ? "#ee8a61" : "#7eadd2";
                    Region segment = (Region) node;
                    assertEquals(Color.web(expected), segment.getBackground().getFills().getFirst().getFill());
                }
                assertTrue(scene.lookup("#seek").isDisabled());
                assertSliderFill(scene, "#seek");
                assertSliderFill(scene, "#volume");
                HeaderBar header = (HeaderBar) scene.lookup("#studio-header");
                assertEquals(scene.getWidth(), header.getWidth());
                assertTrue(bounds(scene, "#now-playing").getMaxX() < bounds(scene, "#playlist").getMinX());
                assertEquals(bounds(scene, "#brand-mark").getCenterY(),
                        bounds(scene, "#brand-title").getCenterY(), 1);
                for (String selector : List.of("#add", "#remove", "#move-up", "#move-down", "#play", "#stop")) {
                    Button control = button(scene, selector);
                    assertEquals("", control.getText());
                    assertTrue(control.getGraphic() != null);
                    assertEquals(control.getAccessibleText(), control.getTooltip().getText());
                }

                assertFalse(view.addFiles(List.of(new File("ignored.txt"), new File("mp3"))));
                File copy = new File("nonexistent-audio.wav");
                assertTrue(view.addFiles(List.of(copy, copy, new File("other.FLAC"))));
                @SuppressWarnings("unchecked")
                TableView<Track> table = (TableView<Track>) scene.lookup("#playlist");
                assertEquals(3, table.getItems().size());
                Track first = table.getItems().getFirst();
                Track active = table.getItems().get(1);
                assertNotEquals(first.id(), active.id());
                table.getSelectionModel().select(active);
                button(scene, "#play").fire();
                assertEquals(active, engine.snapshot().track());

                button(scene, "#move-down").fire();
                assertEquals(active, table.getItems().getLast());
                assertEquals(active, table.getSelectionModel().getSelectedItem());
                assertTrue(view.dropTrack(active, 0));
                assertEquals(active, table.getItems().getFirst());
                assertTrue(view.dropTrack(active, table.getItems().size()));
                assertEquals(active, table.getItems().getLast());
                assertEquals(active, table.getSelectionModel().getSelectedItem());
                assertEquals(active, engine.snapshot().track());
                table.getSelectionModel().select(first);
                button(scene, "#remove").fire();
                assertEquals(2, table.getItems().size());
                assertEquals(active, engine.snapshot().track());

                table.getSelectionModel().select(active);
                button(scene, "#remove").fire();
                assertEquals(PcmEngine.State.IDLE, engine.snapshot().state());
                assertEquals(1, table.getItems().size());
            }
            return null;
        });
    }

    @Test
    void applicationStartsAndClosesInHeadlessMode() throws Exception {
        onFx(() -> {
            Mp100App app = new Mp100App();
            Stage stage = new Stage();
            try {
                app.start(stage);
                assertTrue(stage.isShowing());
                assertEquals("MP-100 Studio", stage.getTitle());
                assertEquals(256, stage.getIcons().getFirst().getWidth());
                assertEquals(820, stage.getMinWidth());
                if (Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW)) {
                    assertEquals(StageStyle.EXTENDED, stage.getStyle());
                }
                assertTrue(stage.getScene().lookup("#playlist").isVisible());
            } finally {
                stage.hide();
                app.stop();
            }
            return null;
        });
    }

    private static void onFx(Callable<Void> action) throws Exception {
        FutureTask<Void> scenario = new FutureTask<>(action);
        Platform.runLater(scenario);
        scenario.get(12, TimeUnit.SECONDS);
    }

    private static Button button(Scene scene, String selector) {
        return (Button) scene.lookup(selector);
    }

    private static Bounds bounds(Scene scene, String selector) {
        var node = scene.lookup(selector);
        return node.localToScene(node.getBoundsInLocal());
    }

    private static void assertSliderFill(Scene scene, String selector) {
        Slider slider = (Slider) scene.lookup(selector);
        Region fill = (Region) slider.lookup(".track-fill");
        assertTrue(fill.isMouseTransparent());
        Bounds track = slider.lookup(".track").getBoundsInParent();
        double original = slider.getValue();

        for (double value : new double[]{0, 0.5, 1}) {
            slider.setValue(value);
            if (value == 0) {
                assertEquals(0, fill.getWidth());
            } else {
                assertTrue(fill.getWidth() > 0);
                assertEquals(track.getMinX(), fill.getBoundsInParent().getMinX(), 0.5);
                assertEquals(slider.lookup(".thumb").getBoundsInParent().getCenterX(),
                        fill.getBoundsInParent().getMaxX(), 0.5);
                assertEquals(Color.web("#7eadd2"), fill.getBackground().getFills().getFirst().getFill());
            }
        }
        slider.setValue(original);
    }
}
