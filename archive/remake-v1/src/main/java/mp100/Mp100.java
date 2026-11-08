package mp100;

import javafx.application.Platform;

import javax.swing.SwingUtilities;

public final class Mp100 {
    private Mp100() {
    }

    public static void main(String[] args) {
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            SwingUtilities.invokeLater(() ->
                    new PlayerWindow(new FxPlaybackEngine(),
                            () -> Platform.runLater(Platform::exit)).setVisible(true));
        });
    }
}
