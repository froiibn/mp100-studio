package mp100.v2.ui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import javafx.scene.control.skin.SliderSkin;
import javafx.scene.layout.Region;

final class FilledSliderSkin extends SliderSkin {
    private final Node track;
    private final Node thumb;
    private final Region fill = new Region();

    FilledSliderSkin(Slider slider) {
        super(slider);
        track = childWithStyle("track");
        thumb = childWithStyle("thumb");
        fill.getStyleClass().add("track-fill");
        fill.setManaged(false);
        fill.setMouseTransparent(true);
        getChildren().add(getChildren().indexOf(thumb), fill);

        registerChangeListener(track.boundsInParentProperty(), ignored -> updateFill());
        registerChangeListener(thumb.boundsInParentProperty(), ignored -> updateFill());
        registerChangeListener(slider.valueProperty(), ignored -> updateFill());
        updateFill();
    }

    private Node childWithStyle(String style) {
        return getChildren().stream().filter(node -> node.getStyleClass().contains(style))
                .findFirst().orElseThrow();
    }

    private void updateFill() {
        Bounds rail = track.getBoundsInParent();
        double end = Math.clamp(thumb.getBoundsInParent().getCenterX(), rail.getMinX(), rail.getMaxX());
        double width = getSkinnable().getValue() > getSkinnable().getMin() ? end - rail.getMinX() : 0;
        fill.resizeRelocate(rail.getMinX(), rail.getMinY(), width, rail.getHeight());
    }
}
