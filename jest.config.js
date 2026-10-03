module.exports = {
  preset: '@react-native/jest-preset',
  // JS tests only; target/ holds compiled ClojureScript (some files end in test.js).
  roots: ['<rootDir>/__tests__'],
  modulePathIgnorePatterns: ['<rootDir>/target/'],
};
