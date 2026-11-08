package mp100.v2.ui;

import javafx.animation.AnimationTimer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import mp100.v2.Track;
import mp100.v2.audio.PcmEngine;
import mp100.v2.audio.PcmEngine.Snapshot;
import mp100.v2.audio.PcmEngine.State;
import mp100.v2.audio.SourceFormat;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

final class PlayerView {
    private static final PseudoClass ACTIVE_TRACK = PseudoClass.getPseudoClass("active-track");
    private static final PseudoClass DROP_READY = PseudoClass.getPseudoClass("drop-ready");
    private static final PseudoClass DROP_BEFORE = PseudoClass.getPseudoClass("drop-before");
    private static final PseudoClass DROP_AFTER = PseudoClass.getPseudoClass("drop-after");
    private static final PseudoClass RUNNING = PseudoClass.getPseudoClass("running");
    private static final PseudoClass FAULT = PseudoClass.getPseudoClass("fault");
    private static final DataFormat TRACK_DRAG = new DataFormat("application/x-mp100-playlist-track");

    private enum Icon {
        ADD("M8 2H10V8H16V10H10V16H8V10H2V8H8Z"),
        UP("M2 11L9 4L16 11L14.5 12.5L9 7L3.5 12.5Z"),
        DOWN("M2 7L3.5 5.5L9 11L14.5 5.5L16 7L9 14Z"),
        DELETE("M6 2H12V4H16V6H2V4H6Z M4 7H6V14H12V7H14V16H4Z M8 7H10V13H8Z"),
        PLAY("M4 2L16 9L4 16Z"),
        PAUSE("M4 2H7V16H4Z M11 2H14V16H11Z"),
        STOP("M3 3H15V15H3Z");

        private final String path;

        Icon(String path) {
            this.path = path;
        }

        Node graphic() {
            SVGPath glyph = new SVGPath();
            glyph.setContent(path);
            glyph.getStyleClass().add("button-glyph");
            StackPane area = new StackPane(glyph);
            area.setMinSize(18, 18);
            area.setPrefSize(18, 18);
            area.setMaxSize(18, 18);
            return area;
        }
    }
    private final PcmEngine engine;
    private final Playlist playlist = new Playlist();
    private final TableView<Track> table = new TableView<>(playlist.rows());
    private final BorderPane root = new BorderPane();
    private final Image studioIcon = new Image(Objects.requireNonNull(
            PlayerView.class.getResourceAsStream("/mp100/v2/studio-icon.png")));
    private final Label count = label("0곡", "muted");
    private final Label trackTitle = label("선택된 곡 없음", "track-title");
    private final Label trackDetail = label("재생 목록에서 곡을 선택하세요", "muted");
    private final Label sourceFormat = label("—", "format-value");
    private final Label outputFormat = label("—", "format-value");
    private final Label stateText = label("대기", "state-chip");
    private final Label elapsed = label("00:00", "clock");
    private final Label length = label("--:--", "clock");
    private final Label notice = label("파일을 추가하거나 이 창에 드래그하세요.", "muted");
    private final Label volumeValue = label("70%", "clock");
    private final Button play = button(Icon.PLAY, "재생", "primary-button", this::togglePlayback);
    private final Button stop = button(Icon.STOP, "정지", "secondary-button", this::stopPlayback);
    private final Button up = button(Icon.UP, "선택한 곡 위로 이동", "secondary-button", () -> move(-1));
    private final Button down = button(Icon.DOWN, "선택한 곡 아래로 이동", "secondary-button", () -> move(1));
    private final Button remove = button(Icon.DELETE, "목록에서 삭제", "secondary-button", this::removeSelected);
    private final Slider timeline = slider(0);
    private final Slider volume = slider(0.7);
    private final LevelMeter leftMeter = new LevelMeter("왼쪽 채널");
    private final LevelMeter rightMeter = new LevelMeter("오른쪽 채널");
    private final AnimationTimer animation;
    private Snapshot shown;
    private boolean seeking;
    private Track seekTrack;

    PlayerView(PcmEngine engine) {
        this.engine = engine;
        root.getStyleClass().add("app");
        root.setTop(header());
        root.setCenter(content());
        VBox controls = transport();
        BorderPane.setMargin(controls, new Insets(0, 24, 24, 24));
        root.setBottom(controls);
        configureDrop();
        render(engine.snapshot());
        updateSelection();
        animation = new AnimationTimer() {
            @Override
            public void handle(long now) {
                Snapshot snapshot = engine.snapshot();
                if (!snapshot.equals(shown)) {
                    render(snapshot);
                }
            }
        };
    }

    Scene scene() {
        Scene scene = new Scene(root, 1060, 720);
        scene.getStylesheets().add(Objects.requireNonNull(
                PlayerView.class.getResource("/mp100/v2/studio.css")).toExternalForm());
        return scene;
    }

    Image studioIcon() {
        return studioIcon;
    }

    void start() {
        animation.start();
    }

    void stop() {
        animation.stop();
    }

    private HeaderBar header() {
        ImageView mark = new ImageView(studioIcon);
        mark.setFitWidth(45);
        mark.setFitHeight(45);
        mark.setPreserveRatio(true);
        mark.setId("brand-mark");
        Label title = label("MP-100 Studio", "brand-title");
        title.setId("brand-title");
        HBox identity = new HBox(12, mark, title);
        identity.setAlignment(Pos.CENTER_LEFT);
        Label formats = label("MP3   ·   FLAC   ·   WAV", "format-pill");
        HeaderBar.setDragType(identity, HeaderDragType.DRAGGABLE_SUBTREE);
        HeaderBar.setDragType(formats, HeaderDragType.DRAGGABLE);
        HeaderBar header = new HeaderBar(identity, null, formats);
        header.setId("studio-header");
        header.getStyleClass().add("studio-header");
        return header;
    }

    private HBox content() {
        VBox queue = queuePanel();
        HBox.setHgrow(queue, Priority.ALWAYS);
        VBox now = nowPlayingPanel();
        now.setMinWidth(255);
        now.setPrefWidth(305);
        HBox panels = new HBox(18, now, queue);
        BorderPane.setMargin(panels, new Insets(16, 24, 16, 24));
        return panels;
    }

    private VBox queuePanel() {
        Label heading = label("재생 목록", "section-title");
        Button add = button(Icon.ADD, "파일 추가", "add-button", this::chooseFiles);
        add.setId("add");
        HBox tools = new HBox(8, up, down, remove, add);
        tools.setAlignment(Pos.CENTER_RIGHT);
        up.setId("move-up");
        down.setId("move-down");
        remove.setId("remove");
        HBox queueHeader = new HBox(10, new VBox(3, heading, count), spacer(), tools);
        queueHeader.setAlignment(Pos.CENTER_LEFT);
        configureTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox panel = new VBox(17, queueHeader, table);
        panel.getStyleClass().add("panel");
        panel.setMinWidth(435);
        return panel;
    }

    private void configureTable() {
        table.setId("playlist");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<Track, String> title = column("파일 이름", Track::name);
        TableColumn<Track, String> format = column("형식", Track::format);
        format.setMinWidth(70);
        format.setMaxWidth(94);
        table.getColumns().setAll(List.of(title, format));
        VBox empty = new VBox(7, label("재생 목록이 비어 있습니다", "empty-title"),
                label("여기에 파일을 드래그하거나 파일 추가를 누르세요.", "muted"));
        empty.setAlignment(Pos.CENTER);
        table.setPlaceholder(empty);
        table.getSelectionModel().selectedIndexProperty().addListener((observable, old, value) -> updateSelection());
        playlist.rows().addListener((ListChangeListener<Track>) change -> {
            count.setText(playlist.rows().size() + "곡");
            updateSelection();
        });
        table.setRowFactory(ignored -> new PlaylistRow());
    }

    private final class PlaylistRow extends TableRow<Track> {
        private Track draggedTrack;

        PlaylistRow() {
            setOnMouseClicked(event -> {
                if (!isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    startTrack(getItem());
                }
            });
            setOnDragDetected(event -> {
                if (!isEmpty() && event.isPrimaryButtonDown()) {
                    ClipboardContent data = new ClipboardContent();
                    draggedTrack = getItem();
                    data.put(TRACK_DRAG, draggedTrack.id().toString());
                    startDragAndDrop(TransferMode.MOVE).setContent(data);
                    event.consume();
                }
            });
            setOnDragOver(event -> {
                if (sourceTrack(event) != null) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    boolean after = afterMidpoint(event);
                    pseudoClassStateChanged(DROP_BEFORE, !after);
                    pseudoClassStateChanged(DROP_AFTER, after);
                    event.consume();
                }
            });
            setOnDragExited(event -> clearDropIndicator());
            setOnDragDropped(event -> {
                Track track = sourceTrack(event);
                if (track != null) {
                    int slot = isEmpty() ? table.getItems().size() : getIndex() + (afterMidpoint(event) ? 1 : 0);
                    clearDropIndicator();
                    event.setDropCompleted(dropTrack(track, slot));
                    event.consume();
                }
            });
            setOnDragDone(event -> {
                draggedTrack = null;
                clearDropIndicator();
            });
        }

        @Override
        protected void updateItem(Track track, boolean empty) {
            super.updateItem(track, empty);
            pseudoClassStateChanged(ACTIVE_TRACK, !empty && track.equals(engine.snapshot().track()));
            clearDropIndicator();
        }

        private Track sourceTrack(DragEvent event) {
            if (event.getGestureSource() instanceof PlaylistRow source && source.getTableView() == table
                    && source.draggedTrack != null && event.getDragboard().hasContent(TRACK_DRAG)) {
                return source.draggedTrack;
            }
            return null;
        }

        private boolean afterMidpoint(DragEvent event) {
            return !isEmpty() && sceneToLocal(event.getSceneX(), event.getSceneY()).getY() >= getHeight() / 2;
        }

        private void clearDropIndicator() {
            pseudoClassStateChanged(DROP_BEFORE, false);
            pseudoClassStateChanged(DROP_AFTER, false);
        }
    }

    private VBox nowPlayingPanel() {
        SVGPath signal = new SVGPath();
        signal.setContent("M0 19H14L22 7L32 32L42 0L53 25L61 13L70 19H84");
        signal.getStyleClass().add("monitor-signal");
        StackPane disc = new StackPane(discRing(58, "disc-face"), discRing(50, "disc-groove"),
                discRing(43, "disc-groove"), discRing(37, "disc-groove"), signal);
        BorderPane artwork = new BorderPane(disc);
        artwork.setTop(label("SIGNAL MONITOR", "monitor-label"));
        sourceFormat.setId("source-format");
        outputFormat.setId("output-format");
        outputFormat.setTooltip(new Tooltip("앱에서 오디오 출력에 전달하는 PCM 포맷입니다."));
        VBox formats = new VBox(5, formatRow("원본", sourceFormat), formatRow("출력", outputFormat));
        artwork.setBottom(formats);
        artwork.getStyleClass().add("artwork");
        artwork.setMinHeight(172);
        VBox.setVgrow(artwork, Priority.ALWAYS);
        trackTitle.setTooltip(new Tooltip());
        trackTitle.getTooltip().textProperty().bind(trackTitle.textProperty());
        trackTitle.setMaxWidth(Double.MAX_VALUE);
        HBox scale = new HBox(label("−60", "meter-tick"), spacer(), label("−40", "meter-tick"),
                spacer(), label("−20", "meter-tick"), spacer(), label("0 dBFS", "meter-tick"));
        scale.setPadding(new Insets(0, 0, 0, 18));
        VBox meters = new VBox(5, scale, meterRow("L", leftMeter), meterRow("R", rightMeter));
        meters.getStyleClass().add("meter-bank");
        VBox panel = new VBox(12, label("NOW PLAYING", "eyebrow"), artwork, trackTitle, trackDetail, meters);
        panel.setId("now-playing");
        panel.getStyleClass().add("panel");
        return panel;
    }

    private static HBox formatRow(String name, Label value) {
        Label key = label(name, "monitor-label");
        key.setMinWidth(28);
        HBox row = new HBox(7, key, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Circle discRing(double radius, String style) {
        Circle circle = new Circle(radius);
        circle.getStyleClass().add(style);
        return circle;
    }

    private VBox transport() {
        timeline.setId("seek");
        timeline.setAccessibleText("재생 위치");
        timeline.setBlockIncrement(5);
        timeline.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                seeking = true;
                seekTrack = engine.snapshot().track();
            }
        });
        timeline.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (seeking && event.getButton() == MouseButton.PRIMARY) {
                seeking = false;
                if (Objects.equals(seekTrack, engine.snapshot().track())) {
                    engine.seek(timeline.getValue());
                }
                render(engine.snapshot());
            }
        });
        timeline.valueProperty().addListener((observable, old, value) -> {
            if (seeking) {
                elapsed.setText(time(value.doubleValue()));
            }
        });
        timeline.setOnKeyReleased(event -> {
            switch (event.getCode()) {
                case LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN -> {
                    engine.seek(timeline.getValue());
                    render(engine.snapshot());
                }
                default -> { }
            }
        });
        HBox progress = new HBox(11, elapsed, timeline, length);
        progress.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(timeline, Priority.ALWAYS);

        volume.setId("volume");
        volume.setAccessibleText("볼륨");
        volume.setPrefWidth(112);
        volume.valueProperty().addListener((observable, old, value) -> {
            engine.setGain(value.doubleValue());
            volumeValue.setText(Math.round(value.doubleValue() * 100) + "%");
        });
        HBox controls = new HBox(9, play, stop, stateText, notice, spacer(),
                label("VOL", "eyebrow"), volume, volumeValue);
        controls.setAlignment(Pos.CENTER_LEFT);
        notice.setId("notice");
        notice.setMinWidth(0);
        notice.setTooltip(new Tooltip());
        notice.getTooltip().textProperty().bind(notice.textProperty());
        play.setId("play");
        stop.setId("stop");
        VBox transport = new VBox(12, progress, controls);
        transport.getStyleClass().addAll("panel", "transport");
        return transport;
    }

    private void configureDrop() {
        root.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                table.pseudoClassStateChanged(DROP_READY, true);
                event.consume();
            }
        });
        root.setOnDragExited(event -> table.pseudoClassStateChanged(DROP_READY, false));
        root.setOnDragDropped(event -> {
            if (event.getDragboard().hasFiles()) {
                boolean added = addFiles(event.getDragboard().getFiles());
                table.pseudoClassStateChanged(DROP_READY, false);
                event.setDropCompleted(added);
                event.consume();
            }
        });
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("오디오 파일 추가");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("오디오 파일 (MP3, FLAC, WAV)",
                "*.mp3", "*.flac", "*.wav", "*.MP3", "*.FLAC", "*.WAV"));
        List<File> selected = chooser.showOpenMultipleDialog(root.getScene().getWindow());
        if (selected != null) {
            addFiles(selected);
        }
    }

    boolean addFiles(List<File> files) {
        List<Path> paths = files.stream().map(File::toPath).toList();
        List<Track> added = playlist.add(paths);
        if (added.isEmpty()) {
            notice.setText("MP3, FLAC 또는 WAV 파일을 선택하세요.");
            return false;
        }
        if (table.getSelectionModel().isEmpty()) {
            table.getSelectionModel().select(added.getFirst());
        }
        notice.setText(added.size() + "곡 추가됨");
        return true;
    }

    private void move(int direction) {
        Track selected = table.getSelectionModel().getSelectedItem();
        playlist.move(selected, direction);
        table.getSelectionModel().select(selected);
    }

    boolean dropTrack(Track track, int slot) {
        if (!playlist.moveToSlot(track, slot)) {
            return false;
        }
        table.getSelectionModel().select(track);
        return true;
    }

    private void removeSelected() {
        Track selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            engine.trackRemoved(selected);
            playlist.remove(selected);
            render(engine.snapshot());
            notice.setText("선택한 곡을 목록에서 제거했습니다.");
        }
    }

    private void updateSelection() {
        int index = table.getSelectionModel().getSelectedIndex();
        up.setDisable(index <= 0);
        down.setDisable(index < 0 || index >= playlist.rows().size() - 1);
        remove.setDisable(index < 0);
        updatePlayButton(engine.snapshot());
    }

    private void updatePlayButton(Snapshot snapshot) {
        Track selected = table.getSelectionModel().getSelectedItem();
        boolean same = selected == null || selected.equals(snapshot.track());
        boolean playing = snapshot.state() == State.PLAYING || snapshot.state() == State.PREPARING;
        boolean pause = same && playing;
        String description = pause ? "일시정지" : same && snapshot.state() == State.PAUSED ? "이어듣기" : "재생";
        if (!description.equals(play.getAccessibleText())) {
            setButtonIcon(play, pause ? Icon.PAUSE : Icon.PLAY, description);
        }
        play.setDisable(selected == null && snapshot.track() == null);
    }

    private void togglePlayback() {
        Snapshot snapshot = engine.snapshot();
        Track selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && !selected.equals(snapshot.track())) {
            startTrack(selected);
            return;
        }
        switch (snapshot.state()) {
            case PREPARING, PLAYING -> engine.pause();
            case PAUSED -> engine.resume();
            case ENDED, ERROR -> startTrack(snapshot.track());
            case IDLE -> { }
        }
        render(engine.snapshot());
    }

    private void startTrack(Track track) {
        engine.play(track);
        render(engine.snapshot());
    }

    private void stopPlayback() {
        engine.stop();
        render(engine.snapshot());
    }

    private void render(Snapshot snapshot) {
        Track active = snapshot.track();
        if (shown == null || !Objects.equals(shown.track(), active)) {
            trackTitle.setText(active == null ? "선택된 곡 없음" : active.name());
            trackDetail.setText(active == null ? "재생 목록에서 곡을 선택하세요" : active.format() + "  ·  로컬 파일");
            table.refresh();
        }
        if (shown == null || !Objects.equals(shown.source(), snapshot.source())) {
            SourceFormat source = snapshot.source();
            sourceFormat.setText(source == null ? "—"
                    : describeFormat(source.sampleRate(), source.bitDepth(), source.channels(), source.floating()));
        }
        if (shown == null || !Objects.equals(shown.output(), snapshot.output())) {
            var output = snapshot.output();
            if (output == null) {
                outputFormat.setText("—");
                outputFormat.getTooltip().setText("앱에서 오디오 출력에 전달하는 PCM 포맷입니다.");
            } else {
                var pcm = output.pcm();
                outputFormat.setText(describeFormat(pcm.sampleRate(), pcm.sample().bits(),
                        pcm.channels(), pcm.sample().floating()));
                outputFormat.getTooltip().setText(output.backend() + " · " + outputFormat.getText());
            }
        }
        if (shown == null || shown.state() != snapshot.state()) {
            stateText.setText(switch (snapshot.state()) {
                case IDLE -> "대기";
                case PREPARING -> "준비 중";
                case PLAYING -> "재생 중";
                case PAUSED -> "일시정지";
                case ENDED -> "재생 완료";
                case ERROR -> "재생 실패";
            });
            stateText.pseudoClassStateChanged(RUNNING, snapshot.state() == State.PLAYING);
            stateText.pseudoClassStateChanged(FAULT, snapshot.state() == State.ERROR);
            stop.setDisable(snapshot.state() == State.IDLE);
            updatePlayButton(snapshot);
            notice.setText(snapshot.state() == State.IDLE ? "파일을 추가하거나 이 창에 드래그하세요."
                    : snapshot.error() == null ? "" : snapshot.error());
        }
        if (!seeking) {
            boolean known = Double.isFinite(snapshot.duration()) && snapshot.duration() > 0;
            timeline.setDisable(!known || snapshot.state() == State.ERROR);
            timeline.setMax(known ? snapshot.duration() : 1);
            timeline.setValue(known ? snapshot.position() : 0);
            elapsed.setText(time(snapshot.position()));
            length.setText(known ? time(snapshot.duration()) : "--:--");
        }
        leftMeter.setLevel(meterValue(snapshot.leftLevel()));
        rightMeter.setLevel(meterValue(snapshot.rightLevel()));
        shown = snapshot;
    }

    private static String describeFormat(int rate, int depth, int channels, boolean floating) {
        String khz = BigDecimal.valueOf(rate, 3).stripTrailingZeros().toPlainString();
        String precision = depth == 0 ? "비트 깊이 없음" : depth + "-bit" + (floating ? " float" : "");
        return khz + " kHz · " + precision + " · " + channels + "ch";
    }

    private static String time(double seconds) {
        long value = (long) seconds;
        return String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60);
    }

    private static double meterValue(double rms) {
        return rms <= 0 ? 0 : Math.clamp(1 + 20 * Math.log10(rms) / 60, 0, 1);
    }

    private static HBox meterRow(String channel, LevelMeter meter) {
        HBox row = new HBox(8, label(channel, "meter-channel"), meter);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(meter, Priority.ALWAYS);
        return row;
    }

    private static final class LevelMeter extends HBox {
        private static final int SEGMENTS = 24;
        private static final PseudoClass LIT = PseudoClass.getPseudoClass("lit");
        private int lit;

        LevelMeter(String name) {
            super(3);
            setAccessibleRole(AccessibleRole.PROGRESS_INDICATOR);
            setAccessibleText(name);
            for (int i = 0; i < SEGMENTS; i++) {
                Region segment = new Region();
                segment.getStyleClass().add("meter-segment");
                if (i >= SEGMENTS - 3) {
                    segment.getStyleClass().add("meter-high");
                }
                HBox.setHgrow(segment, Priority.ALWAYS);
                getChildren().add(segment);
            }
        }

        void setLevel(double value) {
            int next = (int) Math.ceil(value * SEGMENTS);
            if (next != lit) {
                for (int i = Math.min(lit, next); i < Math.max(lit, next); i++) {
                    getChildren().get(i).pseudoClassStateChanged(LIT, i < next);
                }
                lit = next;
                notifyAccessibleAttributeChanged(AccessibleAttribute.VALUE);
            }
        }

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            return switch (attribute) {
                case VALUE -> lit / (double) SEGMENTS;
                case MIN_VALUE -> 0.0;
                case MAX_VALUE -> 1.0;
                default -> super.queryAccessibleAttribute(attribute, parameters);
            };
        }
    }

    private static TableColumn<Track, String> column(String label, Function<Track, String> text) {
        TableColumn<Track, String> column = new TableColumn<>(label);
        column.setSortable(false);
        column.setReorderable(false);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        return column;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private static Slider slider(double value) {
        Slider slider = new Slider(0, 1, value);
        slider.setSkin(new FilledSliderSkin(slider));
        return slider;
    }

    private static Button button(Icon icon, String description, String style, Runnable action) {
        Button button = new Button();
        button.getStyleClass().addAll("icon-button", style);
        setButtonIcon(button, icon, description);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static void setButtonIcon(Button button, Icon icon, String description) {
        button.setGraphic(icon.graphic());
        button.setAccessibleText(description);
        if (button.getTooltip() == null) {
            button.setTooltip(new Tooltip());
        }
        button.getTooltip().setText(description);
    }

    private static Node spacer() {
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        return space;
    }
}
