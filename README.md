# PulseForge

PulseForge is a compact professional metronome for macOS, built with Java 21 and a
Spring-managed application core. The packaged app includes its Java runtime and opens
like any other Mac app.

## Features

- Sample-positioned 48 kHz audio engine with fractional-frame accumulation (no tempo drift)
- 20–300 BPM tempo wheel with scroll, drag, arrow-key, fine-step, and tap-tempo controls
- Quarter, eighth, triplet, sixteenth, and sextuplet patterns
- Editable 1–12 beat meters in quarter- or eighth-note units
- Per-beat strong, normal, and muted accents
- 50–75% swing for even subdivisions
- Studio, wood-block, digital, and soft click sounds
- WAV/AIFF GarageBand recording import with automatic transient extraction
- Audio output and volume selection
- Latency-compensated beat lights and animated pendulum
- Always-on-top compact mode and persistent settings

## Open the app

After packaging, double-click `dist/PulseForge.app` in Finder. The app is self-contained;
the machine does not need a separate Java installation to run it.

Keyboard controls: `Space` starts/stops, `T` taps tempo, and the left/right arrow keys
adjust BPM.

## Build and package

Requirements: macOS and JDK 21 with `jpackage`.

```sh
./package-macos.sh
```

For development:

```sh
mvn test
mvn package
java -jar target/pulseforge.jar
```

## Timing design

PulseForge writes a continuous PCM stream from a dedicated maximum-priority thread.
Beat boundaries are calculated as floating-point sample positions rather than repeated
timer sleeps, so fractional intervals are carried forward instead of rounded on every
beat. Clicks are mixed at their exact offset within a 128-sample render block. Visual
events account for the audio line's queued frames.

As with any desktop audio application, the operating system and selected output device
add fixed playback latency. They do not change the generated tempo or cause cumulative
drift.
