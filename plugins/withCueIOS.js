const { withEntitlementsPlist, withInfoPlist, withDangerousMod, withXcodeProject } = require('@expo/config-plugins');
const fs = require('node:fs');
const path = require('node:path');
const plist = require('@expo/plist').default;

const TARGET = 'CueKeyboard';
const SOURCES = ['CueKeyboardViewController.swift', 'CueStore.swift', 'CueDraft.swift', 'CueAI.swift', 'CueMemory.swift'];
function sharedConfig(config) {
  const bundle = config.ios?.bundleIdentifier || 'com.mikolajpiech.guardian';
  return { bundle, group: `group.${bundle}`, keychain: `$(AppIdentifierPrefix)${bundle}.shared` };
}
function configureProject(project, config) {
  const { bundle } = sharedConfig(config);
  const targets = project.pbxNativeTargetSection();
  let target = Object.entries(targets).find(([key, value]) => !key.endsWith('_comment') && value.name?.replaceAll('"', '') === TARGET);
  if (!target) {
    const added = project.addTarget(TARGET, 'app_extension', TARGET, `${bundle}.CueKeyboard`);
    target = [added.uuid, added.pbxNativeTarget];
    project.addBuildPhase(SOURCES.map(file => `${TARGET}/${file}`), 'PBXSourcesBuildPhase', 'Sources', added.uuid);
    project.addBuildPhase([`${TARGET}/CueMascot.png`], 'PBXResourcesBuildPhase', 'Resources', added.uuid);
    project.addBuildPhase([], 'PBXFrameworksBuildPhase', 'Frameworks', added.uuid);
    const group = project.addPbxGroup([...SOURCES, 'CueMascot.png', 'Info.plist', `${TARGET}.entitlements`].map(file => `${TARGET}/${file}`), TARGET, '""', 'SOURCE_ROOT');
    const mainGroup = project.getFirstProject().firstProject.mainGroup;
    project.addToPbxGroup(group.uuid, mainGroup);
  }
  const sourceGroup = project.pbxGroupByName(TARGET);
  if (sourceGroup) { sourceGroup.path = '""'; sourceGroup.sourceTree = 'SOURCE_ROOT'; }
  const configurations = project.pbxXCBuildConfigurationSection();
  const list = project.pbxXCConfigurationList()[target[1].buildConfigurationList];
  for (const entry of list.buildConfigurations) {
    const settings = configurations[entry.value].buildSettings;
    Object.assign(settings, {
      PRODUCT_BUNDLE_IDENTIFIER: `"${bundle}.CueKeyboard"`, PRODUCT_NAME: TARGET, PRODUCT_MODULE_NAME: TARGET,
      INFOPLIST_FILE: `"${TARGET}/Info.plist"`, CODE_SIGN_ENTITLEMENTS: `"${TARGET}/${TARGET}.entitlements"`,
      SWIFT_VERSION: '5.0', IPHONEOS_DEPLOYMENT_TARGET: '16.4', TARGETED_DEVICE_FAMILY: '"1,2"',
      APPLICATION_EXTENSION_API_ONLY: 'YES', SKIP_INSTALL: 'YES', GENERATE_INFOPLIST_FILE: 'NO',
      CURRENT_PROJECT_VERSION: '1', MARKETING_VERSION: `"${config.version || '1.0.0'}"`,
      LD_RUNPATH_SEARCH_PATHS: '"$(inherited) @executable_path/Frameworks @executable_path/../../Frameworks"',
      CODE_SIGN_STYLE: 'Automatic', CLANG_ENABLE_MODULES: 'YES', SWIFT_EMIT_LOC_STRINGS: 'YES',
    });
    if (config.ios?.appleTeamId) settings.DEVELOPMENT_TEAM = config.ios.appleTeamId;
  }
  return project;
}
function writeExtensionFiles(config) {
  const { group, keychain } = sharedConfig(config);
  const root = config.modRequest.projectRoot, destination = path.join(config.modRequest.platformProjectRoot, TARGET);
  fs.mkdirSync(destination, { recursive: true });
  for (const file of SOURCES) fs.copyFileSync(path.join(root, file === SOURCES[0] ? 'extensions/cue-keyboard' : 'modules/subtext/shared-ios', file), path.join(destination, file));
  fs.copyFileSync(path.join(root, 'assets/cue-mascot-cutout.png'), path.join(destination, 'CueMascot.png'));
  fs.writeFileSync(path.join(destination, `${TARGET}.entitlements`), plist.build({ 'com.apple.security.application-groups': [group], 'keychain-access-groups': [keychain] }));
  fs.writeFileSync(path.join(destination, 'Info.plist'), plist.build({
    CFBundleDisplayName: 'Cue', CFBundleName: 'Cue', CFBundleIdentifier: '$(PRODUCT_BUNDLE_IDENTIFIER)', CFBundleExecutable: '$(EXECUTABLE_NAME)',
    CFBundlePackageType: 'XPC!', CFBundleShortVersionString: '$(MARKETING_VERSION)', CFBundleVersion: '$(CURRENT_PROJECT_VERSION)',
    CueAppGroup: group, CueKeychainGroup: keychain,
    NSExtension: { NSExtensionPointIdentifier: 'com.apple.keyboard-service', NSExtensionPrincipalClass: '$(PRODUCT_MODULE_NAME).CueKeyboardViewController',
      NSExtensionAttributes: { IsASCIICapable: true, PrefersRightToLeft: false, PrimaryLanguage: 'pl-PL', RequestsOpenAccess: true } },
  }));
}
function withCueIOS(config) {
  const { bundle, group, keychain } = sharedConfig(config);
  config = withEntitlementsPlist(config, config => {
    config.modResults['com.apple.security.application-groups'] = Array.from(new Set([...(config.modResults['com.apple.security.application-groups'] || []), group]));
    config.modResults['keychain-access-groups'] = Array.from(new Set([...(config.modResults['keychain-access-groups'] || []), keychain]));
    return config;
  });
  config = withInfoPlist(config, config => { config.modResults.CueAppGroup = group; config.modResults.CueKeychainGroup = keychain; return config; });
  config = withDangerousMod(config, ['ios', async config => {
    writeExtensionFiles(config);
    return config;
  }]);
  config = withXcodeProject(config, config => { config.modResults = configureProject(config.modResults, config); return config; });
  config.extra ??= {}; config.extra.eas ??= {}; config.extra.eas.build ??= {}; config.extra.eas.build.experimental ??= {}; config.extra.eas.build.experimental.ios ??= {};
  const extensions = config.extra.eas.build.experimental.ios.appExtensions || [];
  config.extra.eas.build.experimental.ios.appExtensions = [...extensions.filter(extension => extension.targetName !== TARGET), {
    targetName: TARGET, bundleIdentifier: `${bundle}.CueKeyboard`, entitlements: { 'com.apple.security.application-groups': [group], 'keychain-access-groups': [keychain] },
  }];
  return config;
}
module.exports = withCueIOS;
module.exports.configureProject = configureProject;

module.exports.writeExtensionFiles = writeExtensionFiles;
