package mp100.v2.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

final class JavaSoundOutput implements PcmOutput {
    private final SourceDataLine line;
    private final PcmFormat format;

    JavaSoundOutput(PcmFormat requested) throws LineUnavailableException {
        AudioFormat.Encoding encoding = requested.sample().floating()
                ? AudioFormat.Encoding.PCM_FLOAT : AudioFormat.Encoding.PCM_SIGNED;
        AudioFormat audio = new AudioFormat(encoding, requested.sampleRate(), requested.sample().bits(),
                requested.channels(), requested.frameBytes(), requested.sampleRate(), false);
        line = AudioSystem.getSourceDataLine(audio, AudioSystem.getMixer(null).getMixerInfo());
        try {
            line.open(audio, 3 * requested.chunkBytes());
            AudioFormat opened = line.getFormat();
            if (!opened.matches(audio) || !audio.matches(opened)) {
                throw new LineUnavailableException("Java Sound changed the requested PCM format");
            }
            format = requested;
        } catch (LineUnavailableException | RuntimeException e) {
            line.close();
            throw e;
        }
    }

    @Override
    public PcmFormat format() {
        return format;
    }

    @Override
    public String backend() {
        return "Java Sound";
    }

    @Override
    public void start() {
        line.start();
    }

    @Override
    public void pause() {
        line.stop();
    }

    @Override
    public int available() {
        return line.available();
    }

    @Override
    public int write(byte[] samples, int length) {
        return line.write(samples, 0, length);
    }

    @Override
    public long framesPlayed() {
        return line.getLongFramePosition();
    }

    @Override
    public void close() {
        try {
            line.stop();
            line.flush();
        } finally {
            line.close();
        }
    }
}
