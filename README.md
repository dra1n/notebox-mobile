# Notebox Mobile

React Native app written in ClojureScript (Krell, re-frame, Integrant). The plan, the design
snapshot and the phase gates live in [`spec/`](spec/): start with
[`spec/roadmap.md`](spec/roadmap.md), and track gates in [`spec/progress.md`](spec/progress.md).

## Development

| Command | What |
|---|---|
| `npm start` | Metro |
| `npm run ios` | build and install the iOS app (after native dependency changes: `cd ios && bundle exec pod install` first) |
| `npm run cljs:repl` | Krell REPL + hot reload; `(require '[notebox.dev :as dev])`, `(dev/reset)` restarts the Integrant system |
| `npm run cljs:nrepl` | nREPL server (cider + piggieback) for an editor; in Calva, **Connect to a running REPL** → "notebox: Krell" starts the Krell REPL in it (or **Jack-in** with the same sequence, without this script). Stop `cljs:repl` first: both use port 5001 |
| `npm run cljs:build` | one-off dev compile (no REPL) |
| `npm run verify` | **before every commit**: JVM tests (+ coverage), node tests, clj-kondo, dependency rules, Jest |
| `npm run check:release` | `:advanced` build + Hermes check, into `target/release` |
| `npm run test:e2e` | Maestro flows against an `:e2e` build (needs Metro running and the app installed) |

Restart the REPL after changing `deps.edn`; its classpath is fixed at start.

The simulator runs the **dev** build in `target/`. It updates when the REPL recompiles (on save)
or after `npm run cljs:build` + reload (Cmd-R). `npm run test:e2e` and `check:release` compile
their own builds (`target/e2e`, `target/release`), so they don't update the app you're looking at;
after an e2e run, reload to get the dev build back. Restart Metro after `npm install`
(`npm start -- --reset-cache`).

---


# Getting Started

> **Note**: Make sure you have completed the [Set Up Your Environment](https://reactnative.dev/docs/set-up-your-environment) guide before proceeding.

## Step 1: Start Metro

First, you will need to run **Metro**, the JavaScript build tool for React Native.

To start the Metro dev server, run the following command from the root of your React Native project:

```sh
# Using npm
npm start

# OR using Yarn
yarn start
```

## Step 2: Build and run your app

With Metro running, open a new terminal window/pane from the root of your React Native project, and use one of the following commands to build and run your Android or iOS app:

### Android

```sh
# Using npm
npm run android

# OR using Yarn
yarn android
```

### iOS

For iOS, remember to install CocoaPods dependencies (this only needs to be run on first clone or after updating native deps).

The first time you create a new project, run the Ruby bundler to install CocoaPods itself:

```sh
bundle install
```

Then, and every time you update your native dependencies, run:

```sh
bundle exec pod install
```

For more information, please visit [CocoaPods Getting Started guide](https://guides.cocoapods.org/using/getting-started.html).

```sh
# Using npm
npm run ios

# OR using Yarn
yarn ios
```

If everything is set up correctly, you should see your new app running in the Android Emulator, iOS Simulator, or your connected device.

This is one way to run your app — you can also build it directly from Android Studio or Xcode.

## Step 3: Modify your app

Now that you have successfully run the app, let's make changes!

Open `App.tsx` in your text editor of choice and make some changes. When you save, your app will automatically update and reflect these changes — this is powered by [Fast Refresh](https://reactnative.dev/docs/fast-refresh).

When you want to forcefully reload, for example to reset the state of your app, you can perform a full reload:

- **Android**: Press the <kbd>R</kbd> key twice or select **"Reload"** from the **Dev Menu**, accessed via <kbd>Ctrl</kbd> + <kbd>M</kbd> (Windows/Linux) or <kbd>Cmd ⌘</kbd> + <kbd>M</kbd> (macOS).
- **iOS**: Press <kbd>R</kbd> in iOS Simulator.

## Congratulations! :tada:

You've successfully run and modified your React Native App. :partying_face:

### Now what?

- If you want to add this new React Native code to an existing application, check out the [Integration guide](https://reactnative.dev/docs/integration-with-existing-apps).
- If you're curious to learn more about React Native, check out the [docs](https://reactnative.dev/docs/getting-started).

# Troubleshooting

If you're having issues getting the above steps to work, see the [Troubleshooting](https://reactnative.dev/docs/troubleshooting) page.

# Learn More

To learn more about React Native, take a look at the following resources:

- [React Native Website](https://reactnative.dev) - learn more about React Native.
- [Getting Started](https://reactnative.dev/docs/environment-setup) - an **overview** of React Native and how setup your environment.
- [Learn the Basics](https://reactnative.dev/docs/getting-started) - a **guided tour** of the React Native **basics**.
- [Blog](https://reactnative.dev/blog) - read the latest official React Native **Blog** posts.
- [`@facebook/react-native`](https://github.com/facebook/react-native) - the Open Source; GitHub **repository** for React Native.
