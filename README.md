# Sailing Coaching App

Tiny Android app: tap **Start** to record your GPS position, sail/walk somewhere, tap **Stop** to see the straight-line distance (meters) and bearing (cardinal + degrees) between the two points.

## Install on your Pixel

On your phone, open [the latest release](../../releases/latest) in Chrome, tap the `.apk` file, and follow the install prompts. You'll need to allow installs from Chrome the first time, and tap through a Play Protect "unverified app" warning.

Open the app and grant the location permission. Done.

Each push to `main` produces a new release automatically — bookmark `releases/latest` and you'll always get the freshest build.

Note: each CI build is signed with a fresh debug keystore, so reinstalling a newer build over an older one fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall the previous version first.
