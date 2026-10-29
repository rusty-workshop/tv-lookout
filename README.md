# tv-lookout

**Lookout** for Family Room TV (Sony BRAVIA X90J, Google TV): the Android TV
version of [roku-lookout](https://github.com/rusty-workshop/roku-lookout), written after the Family room
Roku's developer installer refused to start. Same three screens, fed by
[lookout-feed](https://github.com/rusty-workshop/latitude-services/tree/main/lookout-feed)'s `http://192.168.1.158:8473/api/feed`:

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
6. **Live**: the top Lichess TV game in real time. While the page is up the
   TV holds `lichess.org/api/tv/feed` open itself (no Latitude hop), draws
   the board locally (`BoardView`, cburnett pieces in `res/drawable-nodpi`)
   with a 160 ms slide per move, and counts the clocks down every 100 ms
   (tenths under 10 s). The stream closes when you leave the page.
   **Up/down** picks the game type (Classical by default, then Rapid, Blitz,
   Bullet, Chess960, Top rated), via `api/tv/<channel>/feed`; remembered.

7. **Sky**: a radar of aircraft within 40 nm (`RadarView`: range rings, a
   slow sweep, arrows along each track coloured by altitude, callsigns on the
   nearest), refreshed every 10 s while shown, with the nearest five flights
   and their routes; plus the next launch from Cape Canaveral/Kennedy and a
   live T-minus countdown to the next exact-time launch anywhere.

8. **Radar**: the NWS KJAX radar loop (animated GIF via `ImageDecoder`,
   checked every 30 s with If-Modified-Since, so a new scan shows within 30 s
   of NWS posting it) with conditions and 12-hour rain chances.
9. **Tonight**: Mercury to Saturn (visible or not, best time, direction,
    altitude, rise/set) and the next visible ISS pass.

A Port page (ships via aisstream.io) was built and then removed: aisstream
has no receiver covering Jacksonville. `RadarView` still knows how to draw
ships, and latitude-lookout still collects them, if coverage (or a local
RTL-SDR receiver) ever appears.

The Sky radar also plots lightning (NOAA GOES-19 GLM) as fading star bursts.
Requests send a `Lookout` User-Agent: NWS answers Android's default
`Dalvik/...` with 403.

The Weather page has a tides card (NOAA Mayport: next highs/lows, and how
far the water is running above or below prediction). Which tabs show, their
order, seconds per page and the start page come from the settings page at
http://192.168.1.158:8473/settings (`settings` in the feed; the app applies it
on every feed refresh). `PAGE_KEYS` must match the feed's `PAGES`.

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
