# Backgammon (offline, private)

Single-player backgammon for Android against a computer opponent. No internet permission, no ads, no accounts. Everything stays on the phone.

## Features
- Full rules: bar entry, hitting, bearing off, the larger-die rule, and forced moves.
- Doubling cube with take/pass. Gammons and backgammons count.
- Single game, or a match to 3, 5 or 7 points with the Crawford rule.
- Three AI levels:
  - **Easy:** makes mistakes and is loose with the cube.
  - **Medium:** solid greedy play.
  - **Hard:** looks one roll ahead at every reply you could make. In 1,200 test games it beat Medium about 58% of the time.
- Undo options (Settings): Off / This turn / Full take-back of your previous turn.
- Statistics per level: games, wins, gammons and backgammons won and lost, points, matches, doubles, streaks, and how often you roll doubles.
- Auto-save: quit anytime and press **Continue** to pick up where you left off.
- Felt and wood textures are generated in code. Checkers lift and slide; dice tumble.
- Dice use Android's secure random number generator.

## Build the APK — Option A: GitHub (no software to install)
1. Create a free account at github.com, then create a new **private** repository.
2. Upload every file and folder from this project. Use **Add file → Upload files** and drag the contents in.
   - The `.github` folder is hidden on Mac and some Windows setups. If it doesn't upload, go to the repo's **Actions** tab, choose **set up a workflow yourself**, and paste in the contents of `.github/workflows/build-apk.yml`.
3. Open the **Actions** tab. The "Build APK" job runs on every upload; you can also start it with **Run workflow**. It takes about 5–8 minutes.
4. Open the finished run and download **Backgammon-apk** under *Artifacts*. Unzip it to get `Backgammon.apk`.
5. Copy the APK to your phone, tap it, and allow "Install unknown apps" for your file manager when Android asks.

## Build the APK — Option B: Android Studio
Open this folder in Android Studio (Ladybug or newer) and let Gradle sync. Then:
- Plug in your phone and press **Run**, or
- Use **Build → Build App Bundle(s)/APK(s) → Build APK(s)**.

## Updates
The project includes its own signing key (`app/keystore/`), so a newer build installs over the old one and keeps your stats and saved game. Don't delete that file. The key is only for side-loading your own copy, not for publishing to the Play Store.

## Project layout
- `engine/`: pure Kotlin game logic: rules, AI, cube, match scoring, stats, and save format. No Android code.
- `GameViewModel.kt`: game flow, animation sequencing, and auto-save.
- `ui/`: Compose screens, board drawing, and textures.
