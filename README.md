# Breathe

Guided breathing app: 40 breaths (4 s in / 3 s out) → breath hold (double-tap to stop, time is recorded) → 15 s recovery hold → bong → repeat until you press Stop.

This is a very simple app. It requests no permissions (not even internet access), has no ads or tracking, and stores its data only on your device. Nothing is sent anywhere, so there are no privacy concerns.

## Safety
Breathe guides fast, deep breathing followed by breath holds. This can make you light-headed and can cause fainting without warning.

- **Never** use it in or near water (bath, shower, pool), while driving, or while operating anything. Fainting in water can be fatal (shallow-water blackout).
- Always sit or lie down. Never practise standing up.
- Talk to a doctor first if you are pregnant, or have epilepsy, a heart condition, high blood pressure, a history of stroke or fainting, or a breathing condition.
- Stop if you feel unwell. Tingling and light-headedness are common; chest pain, fainting or severe discomfort are not.

This app is for general wellness only and is not medical advice, diagnosis or treatment. You use it at your own risk; the author accepts no liability for any injury or harm arising from its use. The app shows this notice on first launch.

## Get the APK (no Android Studio needed)
1. Create a new repository on github.com (private is fine).
2. Upload the contents of this folder (keep the `.github` folder!) and commit to `main`.
3. Open the **Actions** tab, where "Build APK" runs automatically (~3–4 min).
4. When it's green, open **Releases** (right-hand side of the repo page) on your phone and tap `Breathe.apk`.
5. Allow your browser to "Install unknown apps" when Android asks, then install.

## Or build locally
Open the folder in Android Studio and press Run, or run `./gradlew assembleDebug`.

The app UI lives in `app/src/main/assets/index.html`; the Java wrapper adds native text-to-speech and keeps the screen on.

## Where your data is stored
Every completed round is appended as one line to a plain CSV file on the phone:

    Documents/Breathe/breath_holds.csv

Columns (one row per fully completed round: breaths, hold and 15 s hold; stopped rounds are not saved):

| column | meaning |
|---|---|
| timestamp | when the breath hold ended |
| session_start | when you pressed Start (groups rounds into sessions) |
| round | round number within the session |
| hold_seconds | how long you held your breath |
| breaths_done | breaths actually completed (lower if you double-tapped to skip) |
| breaths_target | breaths-per-round setting |
| in_seconds / out_seconds | breathe-in / breathe-out time settings |
| recovery_hold_seconds | the inhale-and-hold time (15) |
| ring_enabled | yes / no |
| settings_changed_mid_round | yes if in/out time, breath count or ring was changed during that round (values shown are the ones at the end of the round) |
| app_version | app version that recorded the row |
| voice_enabled | breathe in/out voice prompts on (yes / no) |
Open it with any Files app, a spreadsheet app, or copy it to a computer over USB. The app's history screen reads from this same file.
Nothing is sent anywhere. If you uninstall and reinstall the app, Android may not let the new install reuse the old file, so the app will start a new one (for example `breath_holds (1).csv`). The old file stays in the folder.

## Updates
Every build is signed with the same fixed key, so each new build installs over the previous one and your data stays put.
The key is not in the repo. GitHub Actions reads it from these repository secrets (Settings > Secrets and variables > Actions):

| secret | value |
|---|---|
| `BREATHE_KEYSTORE_BASE64` | `base64 -w0 breathe-debug.jks` |
| `BREATHE_STORE_PASSWORD` | keystore password |
| `BREATHE_KEY_PASSWORD` | key password |
| `BREATHE_KEY_ALIAS` | key alias (`breathe`) |

For local builds, put the keystore at `app/breathe-debug.jks` (gitignored) and set the same names (except `BREATHE_KEYSTORE_BASE64`) as environment variables or in `~/.gradle/gradle.properties`. Without them, local builds use the default Android debug key and won't install over the GitHub builds.
Keep a backup of the keystore: if it is lost, the next build can't update the installed app, and you have to uninstall it (Android may then start a new CSV file).
Requires Android 10 or newer.

## License
Breathe is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md). You may use, copy, modify and share it for any noncommercial purpose. Commercial use is not permitted.
Anyone who passes on a copy, changed or not, must include the license terms and this line:

    Required Notice: Copyright (c) 2026 Omkar Karve (https://github.com/omitheaussie/breatheApp)
