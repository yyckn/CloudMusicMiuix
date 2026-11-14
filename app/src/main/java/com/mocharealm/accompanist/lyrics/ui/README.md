# Accompanist Lyrics UI local copy

Android source from `com.mocharealm.accompanist:lyrics-ui-android:2.0.0-rc.1`,
licensed under Apache-2.0; see `app/src/main/assets/licenses/Accompanist-Lyrics-UI-Apache-2.0.txt`.
Common and Android sources are compiled together: expect declarations were removed and
Android actual declarations made ordinary functions.

Local fixes: keep the outgoing interlude marker until its changing height is measured,
and restart the existing scroll chain when an interlude changes following-line offsets.
Tests: `InterludeCollapseTest` and `InterludeLayoutTest`.

AMLL backing vocals are nested under their preceding normal line. Nested vocals share a group
visibility interval, use 70% main text / 50% caption typography, and enter/exit with a reversible
200ms height/scale/fade transition. Their own syllable timestamps are preserved; the group extends
the owner's focus interval until every vocal ends. Covered by `BackingVocalsTest`,
`BackingVocalTimelineTest`, and `BackingVocalLayoutTest`.
Backing vocals that begin before their owner reveal at their own cue; that cue never advances
the owner's focus/scroll start. Breathing dots end at the first actual vocal cue.
