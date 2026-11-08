package mp100.v2.audio;

import javax.sound.sampled.LineUnavailableException;

interface PcmOutput extends AutoCloseable {
    interface Factory {
        PcmOutput open(PcmFormat format) throws LineUnavailableException;
    }

    PcmFormat format();

    String backend();

    void start();

    void pause();

    int available();

    int write(byte[] samples, int length);

    long framesPlayed();

    @Override
    void close();
}
