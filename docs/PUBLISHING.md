# Publishing

The public repository (github.com/gregoryjayuk/thought-capture) is a single-commit snapshot built from two committed trees: this repo's `HEAD`, plus `android-thought-capture/`'s own `HEAD` (it is a separate git repository). No history from either is published.

## Excluded from the snapshot

| Path | Why |
|---|---|
| `third_party/` | the whisper.cpp clone and the ~550 MB model: third-party, fetched by the README's build steps (gitignored) |
| `android-sdk/`, `.tools/`, `.gradle-home/`, `.downloads/` | local toolchains and installers, about 2.5 GB (gitignored) |
| `android-thought-capture/local.properties`, `.gradle/`, `build/` | machine paths and build output (gitignored in the app repo) |
| `icons/` | Inkscape source art; `edit.svg` embeds a reference raster of unknown origin. The app ships its own PNGs |
| `docs/LOCAL.md` | the maintainer's local setup and the original README (absolute paths) |
| `android-thought-capture/README.md` | build notes with the maintainer's absolute paths and folder names; the top-level README covers the same steps |

## Scrub steps

One: the `android-thought-capture/` line (and its comment) is removed from `.gitignore`, because in the snapshot the app is part of the tree rather than a nested repository. The app's save folder was already user-chosen (default: app-private storage), and `run_demo.sh` takes its paths and port from the environment with the original defaults.

## Rebuild

```sh
STAGE=/path/to/staging/thought-capture
rm -rf "$STAGE" && mkdir -p "$STAGE/android-thought-capture"
git -C ~/Code/lab/tts-one archive HEAD | tar -x -C "$STAGE"
git -C ~/Code/lab/tts-one/android-thought-capture archive HEAD | tar -x -C "$STAGE/android-thought-capture"
cd "$STAGE" && rm -rf icons docs/LOCAL.md android-thought-capture/README.md
sed -i '' -e '/The Android app is its own git repository/d' -e '/^android-thought-capture\/$/d' .gitignore   # here it is part of the tree
git init -b main && git add -A
git -c user.name=Gregoryjay -c user.email=gregoryjayuk@users.noreply.github.com commit -m "Initial public release"
```

Then run the privacy scan before pushing.
