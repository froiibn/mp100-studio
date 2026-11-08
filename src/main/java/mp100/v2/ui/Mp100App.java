package mp100.v2.ui;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.layout.HeaderBar;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import mp100.v2.audio.PcmEngine;

public final class Mp100App extends Application {
    private PcmEngine engine;
    private PlayerView view;

    @Override
    public void start(Stage stage) {
        if (Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW)) {
            stage.initStyle(StageStyle.EXTENDED);
            HeaderBar.setSystemColorScheme(stage, ColorScheme.DARK);
            HeaderBar.setSystemButtonHeight(stage, 52);
        }
        engine = PcmEngine.create();
        view = new PlayerView(engine);
        stage.getIcons().add(view.studioIcon());
        stage.setTitle("MP-100 Studio");
        stage.setMinWidth(820);
        stage.setMinHeight(650);
        stage.setScene(view.scene());
        stage.show();
        view.start();
    }

    @Override
    public void stop() {
        if (view != null) {
            view.stop();
        }
        if (engine != null) {
            engine.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
