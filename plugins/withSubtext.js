const { withAppBuildGradle } = require('expo/config-plugins');

module.exports = config => withAppBuildGradle(config, config => {
  const line = "implementation files('../../modules/subtext/android/libs/messagebridges.aar')";
  if (!config.modResults.contents.includes(line)) {
    config.modResults.contents += `\n// On-device Messenger and WhatsApp bridges (Arie/MirrorMsg).\ndependencies { ${line} }\n`;
  }
  return config;
});
