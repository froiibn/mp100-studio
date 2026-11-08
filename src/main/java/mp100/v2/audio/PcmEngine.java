package mp100.v2.audio;

import mp100.v2.Track;

import javax.sound.sampled.LineUnavailableException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class PcmEngine implements AutoCloseable {
    public enum State { IDLE, PREPARING, PLAYING, PAUSED, ENDED, ERROR }

    public record Snapshot(State state, Track track, double position, double duration,
                           double leftLevel, double rightLevel, String error,
                           SourceFormat source, OutputFormat output) {
    }

    public record OutputFormat(PcmFormat pcm, String backend) {
    }

    private interface OutputSelector {
        PcmOutput open(SourceFormat source, BooleanSupplier cancelled) throws LineUnavailableException;
    }

    private static final Snapshot IDLE = new Snapshot(State.IDLE, null, 0, Double.NaN, 0, 0, null, null, null);
    private final PcmDecoder.Factory decoders;
    private final OutputSelector outputs;
    private final Thread worker;
    private volatile Session current;
    private volatile Snapshot snapshot = IDLE;
    private volatile double gain = 0.7;
    private volatile boolean closed;

    public static PcmEngine create() {
        return new PcmEngine(FfmpegDecoder.Tools.installed(), OutputFormats::open);
    }

    PcmEngine(PcmDecoder.Factory decoders, PcmOutput.Factory outputs) {
        this(decoders, (source, cancelled) -> OutputFormats.choose(source, 48_000, outputs, cancelled));
    }

    private PcmEngine(PcmDecoder.Factory decoders, OutputSelector outputs) {
        this.decoders = decoders;
        this.outputs = outputs;
        worker = Thread.ofPlatform().name("mp100-pcm").start(this::run);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public synchronized void play(Track track) {
        if (!closed) {
            replace(Objects.requireNonNull(track), 0, Double.NaN, null, false);
        }
    }

    public synchronized void pause() {
        if (current != null) {
            current.paused = true;
            snapshot = at(State.PAUSED, snapshot.track(), snapshot.position(), snapshot.duration(),
                    snapshot.source(), snapshot.output());
        }
    }

    public synchronized void resume() {
        if (current != null && current.paused) {
            current.paused = false;
            snapshot = at(State.PREPARING, snapshot.track(), snapshot.position(), snapshot.duration(),
                    snapshot.source(), snapshot.output());
        }
    }

    public synchronized void seek(double seconds) {
        if (closed || snapshot.track() == null || !Double.isFinite(seconds)
                || !Double.isFinite(snapshot.duration()) || snapshot.state() == State.ERROR) {
            return;
        }
        double position = Math.clamp(seconds, 0, snapshot.duration());
        if (position == snapshot.duration()) {
            current = null;
            snapshot = at(State.ENDED, snapshot.track(), position, snapshot.duration(),
                    snapshot.source(), snapshot.output());
            return;
        }
        State state = snapshot.state();
        replace(snapshot.track(), position, snapshot.duration(), snapshot.source(),
                state == State.PAUSED || state == State.ENDED);
    }

    public synchronized void stop() {
        current = null;
        snapshot = IDLE;
    }

    public synchronized void trackRemoved(Track track) {
        if (track.equals(snapshot.track())) {
            stop();
        }
    }

    public void setGain(double gain) {
        if (Double.isFinite(gain)) {
            this.gain = Math.clamp(gain, 0, 1);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        stop();
        worker.interrupt();
    }

    private void replace(Track track, double position, double duration, SourceFormat source, boolean paused) {
        current = new Session(track, position, duration, source, paused);
        snapshot = at(paused ? State.PAUSED : State.PREPARING, track, position, duration, source, null);
        notifyAll();
    }

    private void run() {
        try {
            Session previous = null;
            while (!closed) {
                Session session = nextSession(previous);
                if (session == null) {
                    return;
                }
                previous = session;
                try {
                    playSession(session);
                } catch (CancellationException ignored) {
                    // The next iteration takes the latest request.
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    fail(session, e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized Session nextSession(Session previous) throws InterruptedException {
        while (!closed && (current == null || current == previous)) {
            wait();
        }
        return closed ? null : current;
    }

    private void playSession(Session session) throws Exception {
        if (session.source == null) {
            session.source = decoders.probe(session.track.path(), () -> current != session);
            session.duration = session.source.duration();
        }
        if (current != session) {
            return;
        }
        publish(session, 0, false, PcmSamples.Level.SILENT);
        try (PcmOutput output = outputs.open(session.source, () -> current != session)) {
            if (current != session) {
                return;
            }
            session.output = new OutputFormat(output.format(), output.backend());
            publish(session, 0, false, PcmSamples.Level.SILENT);
            try (PcmDecoder decoder = decoders.open(session.track.path(), session.start, output.format())) {
                stream(session, decoder, output);
            }
        }
    }

    private void stream(Session session, PcmDecoder decoder, PcmOutput output) throws Exception {
        PcmFormat format = output.format();
        int frameBytes = format.frameBytes();
        byte[] raw = new byte[format.chunkBytes()];
        byte[] scaled = new byte[raw.length];
        int buffered = 0;
        boolean eof = false;
        boolean running = false;
        long written = 0;
        long firstFrame = output.framesPlayed();
        PcmSamples.Level level = PcmSamples.Level.SILENT;

        while (current == session) {
            if (session.paused) {
                if (running) {
                    output.pause();
                    running = false;
                }
                publish(session, Math.clamp(output.framesPlayed() - firstFrame, 0, written),
                        written > 0, PcmSamples.Level.SILENT);
                Thread.sleep(8);
                continue;
            }
            if (!running && written > 0) {
                output.start();
                running = true;
            }
            if (!eof && buffered < raw.length) {
                int read = decoder.read(raw, buffered, raw.length - buffered);
                eof = read == -1;
                buffered += Math.max(read, 0);
            }

            int count = Math.min(buffered, Math.max(0, output.available()));
            count -= count % frameBytes;
            int sent = 0;
            if (count > 0 && current == session && !session.paused) {
                level = PcmSamples.applyGain(format, raw, scaled, count, gain);
                sent = output.write(scaled, count);
                written += sent / frameBytes;
                buffered -= sent;
                System.arraycopy(raw, sent, raw, 0, buffered);
                if (!running && sent > 0 && current == session && !session.paused) {
                    output.start();
                    running = true;
                }
            }

            long played = Math.clamp(output.framesPlayed() - firstFrame, 0, written);
            if (eof && buffered < frameBytes) {
                if (buffered != 0) {
                    throw new IllegalStateException("Decoder returned an incomplete PCM frame");
                }
                if (played == written && !session.paused) {
                    if (written == 0 && session.start == 0) {
                        throw new IllegalStateException("Decoder produced no audio samples");
                    }
                    if (end(session, played)) {
                        return;
                    }
                }
            }

            publish(session, played, written > 0, played == written ? PcmSamples.Level.SILENT : level);
            if (sent == 0) {
                Thread.sleep(3);
            }
        }
    }

    private synchronized void publish(Session session, long frames, boolean queued,
                                      PcmSamples.Level level) {
        if (current == session) {
            State state = session.paused ? State.PAUSED : queued ? State.PLAYING : State.PREPARING;
            PcmSamples.Level shown = state == State.PLAYING ? level : PcmSamples.Level.SILENT;
            double position = position(session, frames);
            if (Double.isFinite(session.duration)) {
                position = Math.min(position, session.duration);
            }
            snapshot = new Snapshot(state, session.track, position, session.duration,
                    shown.left(), shown.right(), null, session.source, session.output);
        }
    }

    private synchronized boolean end(Session session, long frames) {
        if (current != session) {
            return true;
        }
        if (session.paused) {
            return false;
        }
        double length = position(session, frames);
        snapshot = at(State.ENDED, session.track, length, length, session.source, session.output);
        current = null;
        return true;
    }

    private synchronized void fail(Session session, Exception error) {
        if (current == session) {
            String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            snapshot = new Snapshot(State.ERROR, session.track, snapshot.position(), snapshot.duration(),
                    0, 0, detail, session.source, null);
            current = null;
        }
    }

    private static Snapshot at(State state, Track track, double position, double duration,
                               SourceFormat source, OutputFormat output) {
        return new Snapshot(state, track, position, duration, 0, 0, null, source, output);
    }

    private static double position(Session session, long frames) {
        return session.output == null ? session.start
                : session.start + frames / (double) session.output.pcm().sampleRate();
    }

    private static final class Session {
        final Track track;
        final double start;
        double duration;
        SourceFormat source;
        OutputFormat output;
        volatile boolean paused;

        Session(Track track, double start, double duration, SourceFormat source, boolean paused) {
            this.track = track;
            this.start = start;
            this.duration = duration;
            this.source = source;
            this.paused = paused;
        }
    }
}
