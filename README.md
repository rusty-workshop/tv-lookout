# tv-lookout

**Lookout** for Family Room TV (Sony BRAVIA X90J, Google TV): the Android TV
version of [roku-lookout](../roku-lookout), written after the Family room
Roku's developer installer refused to start. Same three screens, fed by
[latitude-lookout](../latitude-lookout)'s `http://192.168.1.158:8473/api/feed`:

1. **Clock + weather**: clock, date, Jacksonville conditions, air quality/UV,
   the next visible space station pass, and the next 12 hours.
2. **Homelab**: service status dots, last backup, DVD ripper progress.
3. **Chess**: Lichess correspondence boards from Gambit, ratings, and the
   daily puzzle position.

NWS weather alerts show as a banner over every screen. Pages rotate every 30
seconds; left/right (or rewind/fast-forward) flips and pauses rotation for 5
minutes. The screen stays on while the app is open.

`LookoutView` lays everything out on a fixed 1920x1080 canvas scaled to the
screen, so positions match the Roku version. It's used by both
`MainActivity` (the app) and `LookoutDream` (a screensaver, selectable on
the latitude-screensaver picker page or in the TV's screensaver settings).

## Build and install

```bash
./gradlew assembleRelease
adb -s 192.168.1.75:5555 push app/build/outputs/apk/release/app-release.apk /data/local/tmp/lookout.apk
adb -s 192.168.1.75:5555 shell pm install -r -i com.android.vending /data/local/tmp/lookout.apk
```

(`-i com.android.vending` makes Google TV treat it like a Play install, so it
lands in the home screen's apps row.)

## License

AGPL-3.0-or-later
