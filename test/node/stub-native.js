// Preloaded by `npm run test:cljs` (node -r): stands in for the native JS
// modules the UI requires, so presentational components can be called in node
// and their hiccup inspected. A component is stubbed as its name (a string),
// so tests can find e.g. "Text" or "Pressable" in the hiccup.
const Module = require('module');

const named = (prefix) =>
  new Proxy({}, {
    get: (_, key) => (key === '__esModule' ? false : typeof key === 'string' ? prefix + key : undefined),
  });

const stubs = {
  'react-native': Object.assign(named(''), {
    Platform: { OS: 'ios' },
    Alert: { alert: () => {} },
  }),
  'react-native-safe-area-context': named(''),
  'react-native-svg': named('Svg.'),
  '@react-navigation/native': named('Nav.'),
  '@react-navigation/native-stack': { createNativeStackNavigator: () => ({ Navigator: 'Stack.Navigator', Screen: 'Stack.Screen' }) },
};

const load = Module._load;
Module._load = function (request, parent, isMain) {
  if (Object.prototype.hasOwnProperty.call(stubs, request)) return stubs[request];
  return load.call(this, request, parent, isMain);
};
