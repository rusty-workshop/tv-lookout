# tv-lookout

**Lookout** for Family Room TV (Sony BRAVIA X90J, Google TV): the Android TV
version of [roku-lookout](../roku-lookout), written after the Family room
Roku's developer installer refused to start. Same three screens, fed by
[latitude-lookout](../latitude-lookout)'s `http://192.168.1.158:8473/api/feed`:

1. **Clock + weather**: clock, date, Jacksonville conditions, air quality/UV,
   the next visible space station pass, and the next 12 hours.
2. **Homelab**: service status tiles, last backup, DVD ripper progress.
3. **Today**: Wikipedia's "on this day" with a picture, and countdowns
   (managed at http://192.168.1.158:8473/countdowns).
4. **Homelab**: also AdGuard's blocked count and top blocked domain today.
   **Server**: the Latitude's CPU temp, load, memory, free disk and uptime,
   plus internet speed (latest and a 7-day chart, from a 3-hourly test).
5. **Chess**: Lichess correspondence boards from Gambit, a rating chart per
   speed (rebuilt from rated games), the last 10 results, and the daily
   puzzle position.
6. **Live**: the top Lichess TV game, updating every 3 seconds while shown.

NWS weather alerts show as a banner over every screen. Pages rotate every 30
seconds; left/right (or rewind/fast-forward) flips and pauses rotation for 5
minutes. **OK pins the current page** (a "Pinned" chip shows) so it stops
rotating; press OK again to unpin. The pin is remembered across restarts.
The screen stays on while the app is open.

`LookoutView` lays everything out on a fixed 1920x1080 canvas scaled to the
screen. v2 design: dark gradient with a soft purple glow, rounded translucent
cards, a thin clock, weather emoji and stat tiles, tabs instead of page dots,
fade-and-slide page changes, service tiles (red-tinted when down), chess
boards in cards with "Your move"/"Their move" badges. (The Roku version
still uses the original plain layout.) It's used by both
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
