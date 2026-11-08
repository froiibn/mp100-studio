package mp100.v2.audio;

public record SourceFormat(int sampleRate, int bitDepth, int channels, boolean floating, double duration) {
}
