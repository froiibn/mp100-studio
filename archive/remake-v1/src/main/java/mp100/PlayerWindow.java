package mp100;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PlayerWindow extends JFrame {
    private final PlaylistModel playlist = new PlaylistModel();
    private final JTable table = new JTable(playlist);
    private final JTextField status = display("Playback status");
    private final JTextField elapsed = display("Elapsed time");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final PlaybackController controller;
    private JFileChooser chooser;

    PlayerWindow(PlaybackEngine engine, Runnable shutdown) {
        super("MP-100");
        setContentPane(createContent());
        controller = new PlaybackController(engine, SwingUtilities::invokeLater, this::render);
        setSize(280, 330);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                controller.close();
                shutdown.run();
            }
        });
    }

    private JPanel createContent() {
        JPanel displayRow = new JPanel(new BorderLayout(5, 0));
        displayRow.setOpaque(false);
        elapsed.setColumns(5);
        elapsed.setHorizontalAlignment(SwingConstants.CENTER);
        displayRow.add(status, BorderLayout.CENTER);
        displayRow.add(elapsed, BorderLayout.EAST);

        progress.setUI(new BasicProgressBarUI());
        progress.setPreferredSize(new Dimension(0, 10));
        progress.setBackground(new Color(35, 35, 35));
        progress.setForeground(Color.ORANGE);
        progress.setBorderPainted(false);
        progress.getAccessibleContext().setAccessibleName("Playback progress");

        JPanel display = new JPanel(new BorderLayout(0, 4));
        display.setOpaque(false);
        display.add(displayRow, BorderLayout.NORTH);
        display.add(progress, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        buttons.setOpaque(false);
        buttons.add(button("Add", "add.gif", this::addTrack));
        buttons.add(button("Play", "play.gif", () -> controller.play(selectedTrack())));
        buttons.add(button("Stop", "stop.gif", () -> controller.stop()));
        buttons.add(button("Delete", "del.gif", this::deleteTrack));

        JPanel controls = new JPanel(new BorderLayout(0, 6));
        controls.setOpaque(false);
        controls.add(display, BorderLayout.NORTH);
        controls.add(buttons, BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setBackground(Color.BLACK);
        table.setForeground(Color.LIGHT_GRAY);
        table.setSelectionBackground(Color.DARK_GRAY);
        table.setSelectionForeground(Color.WHITE);
        table.setShowGrid(false);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Color.BLACK);

        JPanel content = new JPanel(new BorderLayout(0, 7));
        content.setBackground(Color.BLACK);
        content.setBorder(BorderFactory.createEmptyBorder(8, 9, 9, 9));
        content.add(controls, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        return content;
    }

    private static JTextField display(String label) {
        JTextField field = new JTextField();
        field.setEditable(false);
        field.setBackground(Color.BLACK);
        field.setForeground(Color.YELLOW);
        field.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        field.getAccessibleContext().setAccessibleName(label);
        return field;
    }

    private static JButton button(String label, String icon, Runnable action) {
        JButton button = new JButton(new ImageIcon(PlayerWindow.class.getResource("/icons/" + icon)));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void addTrack() {
        if (chooser == null) {
            chooser = new JFileChooser();
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(new FileNameExtensionFilter("MP3 files", "mp3"));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath().toAbsolutePath();
            if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                playlist.add(new Track(path));
            }
        }
    }

    private Track selectedTrack() {
        int row = table.getSelectedRow();
        return row < 0 ? null : playlist.trackAt(row);
    }

    private void deleteTrack() {
        int row = table.getSelectedRow();
        controller.trackRemoved(selectedTrack());
        if (row >= 0) {
            playlist.remove(row);
        }
    }

    private void render(PlaybackController.View view) {
        if (!view.message().equals(status.getText())) {
            status.setText(view.message());
            status.setCaretPosition(0);
            status.setToolTipText(view.message());
        }

        long seconds = (long) view.elapsedSeconds();
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        if (!time.equals(elapsed.getText())) {
            elapsed.setText(time);
        }
        double duration = view.durationSeconds();
        boolean knownDuration = Double.isFinite(duration) && duration > 0;
        boolean playing = view.state() == PlaybackController.State.PLAYING;
        progress.setIndeterminate(playing && !knownDuration);
        int percent = playing && knownDuration ? (int) (100 * view.elapsedSeconds() / duration) : 0;
        progress.setValue(percent);
    }

    private static final class PlaylistModel extends AbstractTableModel {
        private final List<Track> tracks = new ArrayList<>();

        Track trackAt(int row) {
            return tracks.get(row);
        }

        void add(Track track) {
            int row = tracks.size();
            tracks.add(track);
            fireTableRowsInserted(row, row);
        }

        void remove(int row) {
            tracks.remove(row);
            fireTableRowsDeleted(row, row);
        }

        @Override
        public int getRowCount() {
            return tracks.size();
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public String getColumnName(int column) {
            return "Play List";
        }

        @Override
        public Object getValueAt(int row, int column) {
            return trackAt(row).name();
        }
    }
}
