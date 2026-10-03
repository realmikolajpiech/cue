const { withProjectBuildGradle } = require('expo/config-plugins');

// RN's unversioned classpath defaults to Kotlin 2.1.20 even when a newer
// android.kotlinVersion property exists. LiteRT-LM 0.15.0 uses metadata 2.3.
module.exports = function withGuardianAndroid(config) {
  return withProjectBuildGradle(config, config => {
    config.modResults.contents = config.modResults.contents.replace(
      /classpath\(['"]org\.jetbrains\.kotlin:kotlin-gradle-plugin(?::[^'"]+)?['"]\)/,
      "classpath('org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20')",
    );
    return config;
  });
};
