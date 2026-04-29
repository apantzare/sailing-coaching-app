# Start/Stop Tracker

Tiny Android app: tap **Start** to record your GPS position, walk somewhere, tap **Stop** to see the straight-line distance (meters) and bearing (cardinal + degrees) between the two points.

## Install on your Pixel

1. Open the latest successful run on the [Actions](../../actions) tab.
2. Download the **app-debug** artifact (a zip containing `app-debug.apk`).
3. Transfer the APK to your phone (Drive, USB, email).
4. Tap the APK in your file manager. You'll be prompted to allow installs from that source — enable it. Play Protect will warn the app is unverified; tap **More details → Install anyway**.
5. Open the app and grant the location permission.

Note: each CI build is signed with a fresh debug keystore, so reinstalling a newer build over an older one will fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall the previous version first.
