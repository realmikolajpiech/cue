const { AndroidConfig, withAndroidManifest, withAppBuildGradle, withSettingsGradle } = require('expo/config-plugins');

module.exports = config => {
  config = withAndroidManifest(config, config => {
    const application = AndroidConfig.Manifest.getMainApplicationOrThrow(config.modResults);
    // Keep development controls available through the gesture/menu, clear of the product UI.
    AndroidConfig.Manifest.addMetaDataItemToMainApplication(application, 'EXDevMenuShowFloatingActionButton', 'false');
    return config;
  });
  config = withSettingsGradle(config, config => {
    const marker = "include ':cue-heliboard'";
    if (!config.modResults.contents.includes(marker)) {
      config.modResults.contents += `\n// Vendored HeliBoard keyboard engine.\n${marker}\nproject(':cue-heliboard').projectDir = new File(rootDir, '../vendor/heliboard/android')\n`;
    }
    return config;
  });
  return withAppBuildGradle(config, config => {
  const line = "implementation files('../../modules/subtext/android/libs/messagebridges.aar')";
  if (!config.modResults.contents.includes(line)) {
    config.modResults.contents += `\n// On-device Messenger and WhatsApp bridges (Arie/MirrorMsg).\ndependencies { ${line} }\n`;
  }
  const desugaring = "coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.1.5'";
  if (!config.modResults.contents.includes(desugaring)) {
    config.modResults.contents += `\n// Java library APIs used by HeliBoard on older Android versions.\nandroid { compileOptions { coreLibraryDesugaringEnabled true } }\ndependencies { ${desugaring} }\n`;
  }
    return config;
  });
};
