package mp100.v2.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import mp100.v2.Track;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class Playlist {
    private static final Set<String> FORMATS = Set.of("MP3", "FLAC", "WAV");
    private final ObservableList<Track> tracks = FXCollections.observableArrayList();
    private final ObservableList<Track> view = FXCollections.unmodifiableObservableList(tracks);

    ObservableList<Track> rows() {
        return view;
    }

    List<Track> add(List<Path> files) {
        List<Track> added = files.stream().filter(path -> path.getFileName() != null)
                .map(Track::new).filter(track -> FORMATS.contains(track.format())).toList();
        tracks.addAll(added);
        return added;
    }

    void remove(Track track) {
        tracks.remove(track);
    }

    void move(Track track, int delta) {
        int from = tracks.indexOf(track);
        moveToIndex(from, from + delta);
    }

    boolean moveToSlot(Track track, int slot) {
        if (slot < 0 || slot > tracks.size()) {
            return false;
        }
        int from = tracks.indexOf(track);
        return moveToIndex(from, slot > from ? slot - 1 : slot);
    }

    private boolean moveToIndex(int from, int to) {
        if (from < 0 || to < 0 || to >= tracks.size()) {
            return false;
        }
        if (from != to) {
            List<Track> reordered = new ArrayList<>(tracks);
            reordered.add(to, reordered.remove(from));
            tracks.setAll(reordered);
        }
        return true;
    }
}
