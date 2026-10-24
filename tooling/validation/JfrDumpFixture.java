import com.juicyslew.moonstation14.ms14.animals.diagnostics.AnimalPopulationRecording;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;

import java.nio.file.Path;

public final class JfrDumpFixture {
    @Name("ms14.fixture.Event")
    static final class FixtureEvent extends Event {
        String marker;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected dump destination");
        if ("missing".equals(args[0])) {
            try {
                AnimalPopulationRecording.dumpCurrentRecording(Path.of("unused.jfr"));
                throw new AssertionError("Missing recording unexpectedly dumped");
            } catch (IllegalStateException expected) {
                if (!expected.getMessage().contains(AnimalPopulationRecording.RECORDING_NAME)) throw expected;
                System.out.println("MISSING_RECORDING_REJECTED");
                return;
            }
        }
        try (var recording = new Recording()) {
            recording.setName(AnimalPopulationRecording.RECORDING_NAME);
            recording.enable(FixtureEvent.class);
            recording.start();
            var event = new FixtureEvent();
            event.marker = "fixture";
            event.commit();
            AnimalPopulationRecording.dumpCurrentRecording(Path.of(args[0]));
        }
        System.out.println("DONE");
    }
}
