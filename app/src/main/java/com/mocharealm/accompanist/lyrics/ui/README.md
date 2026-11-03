# Accompanist Lyrics UI local copy

Android source from `com.mocharealm.accompanist:lyrics-ui-android:2.0.0-rc.1`,
licensed under Apache-2.0; see `app/src/main/assets/licenses/Accompanist-Lyrics-UI-Apache-2.0.txt`.
Common and Android sources are compiled together: expect declarations were removed and
Android actual declarations made ordinary functions.

Local fixes: keep the outgoing interlude marker until its changing height is measured,
and restart the existing scroll chain when an interlude changes following-line offsets.
Tests: `InterludeCollapseTest` and `InterludeLayoutTest`.
