package app.tonetempo.audio;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ChannelPickerTest {
    private static final int FRAMES = 1024;

    @Test void followsTheChannelCarryingTheSignal() {
        var picker = new ChannelPicker(2, 0);
        assertEquals(1, picker.pick(block(2, 0, 0), FRAMES) + 1, "silence keeps channel 1");
        assertEquals(2, picker.pick(block(2, 0, .3), FRAMES) + 1, "a guitar on input 2 is picked up");
        assertEquals(2, picker.channel());
        assertEquals(2, picker.pick(block(2, .2, .3), FRAMES) + 1, "a quieter input 1 does not steal it");
        assertEquals(2, picker.pick(block(2, 0, 0), FRAMES) + 1, "a pause between notes holds the choice");
        for (int i = 0; i < 4; i++) picker.pick(block(2, .6, .01), FRAMES);
        assertEquals(1, picker.channel(), "an input that is 6 dB louder takes over");
    }

    @Test void pinnedChannelIsUsedEvenWhenSilent() {
        var picker = new ChannelPicker(2, 1);
        assertEquals(0, picker.pick(block(2, 0, .5), FRAMES));
        assertEquals(1, picker.channel());
        var beyond = new ChannelPicker(2, 5);
        assertEquals(1, beyond.pick(block(2, 0, .5), FRAMES), "a pin beyond the device's channels falls back to automatic");
    }

    @Test void deliversTheSelectedChannelsSamples() throws Exception {
        var picker = new ChannelPicker(4, 0);
        float[] interleaved = block(4, 0, 0, .4, 0);
        int chosen = picker.pick(interleaved, FRAMES);
        assertEquals(2, chosen);
        assertEquals(interleaved[5 * 4 + 2], interleaved[5 * 4 + chosen]);
    }

    @Test void macDefaultInputIsReadFromSystemProfilerOutput() {
        String json = """
                {"SPAudioDataType":[{"_items":[
                  {"_name":"Scarlett Solo USB","coreaudio_device_input":2,"coreaudio_device_output":2},
                  {"_name":"MacBook Pro Microphone","coreaudio_default_audio_input_device":"spaudio_yes","coreaudio_device_input":1},
                  {"_name":"MacBook Pro Speakers","coreaudio_default_audio_output_device":"spaudio_yes","coreaudio_device_output":2}
                ],"_name":"Devices"}]}
                """;
        assertEquals("MacBook Pro Microphone", MacDefaultInput.parse(json));
        assertEquals("", MacDefaultInput.parse("{}"));
        assertEquals("Focus \"Solo\"", MacDefaultInput.parse(
                "{\"_name\":\"Focus \\\"Solo\\\"\",\"coreaudio_default_audio_input_device\":\"spaudio_yes\"}"));
    }

    private static float[] block(int channels, double... levels) {
        var random = new Random(1);
        float[] data = new float[FRAMES * channels];
        for (int i = 0; i < FRAMES; i++)
            for (int c = 0; c < channels; c++) data[i * channels + c] = (float) (random.nextGaussian() * levels[c]);
        return data;
    }
}
