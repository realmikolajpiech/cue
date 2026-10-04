const assert = require('node:assert/strict');
const xcode = require('xcode');
const { configureProject } = require('../../plugins/withCueIOS');
const project = xcode.project('ios/Cue.xcodeproj/project.pbxproj').parseSync();
const config = require('../../app.json').expo;
configureProject(project, config);
const first = project.writeSync();
configureProject(project, config);
assert.equal(project.writeSync(), first, 'Prebuild must not duplicate extension targets, sources, or embedding');
const targets = Object.entries(project.pbxNativeTargetSection()).filter(([key, value]) => !key.endsWith('_comment') && value.name?.replaceAll('"', '') === 'CueKeyboard');
assert.equal(targets.length, 1);
const extension = targets[0][1];
const phases = extension.buildPhases.map(entry => project.hash.project.objects.PBXSourcesBuildPhase[entry.value]).filter(Boolean);
assert.equal(phases.length, 1); assert.equal(phases[0].files.length, 5);
const configurations = project.pbxXCConfigurationList()[extension.buildConfigurationList].buildConfigurations;
for (const entry of configurations) {
  const settings = project.pbxXCBuildConfigurationSection()[entry.value].buildSettings;
  assert.equal(settings.APPLICATION_EXTENSION_API_ONLY, 'YES'); assert.equal(settings.PRODUCT_NAME, 'CueKeyboard');
}
assert.equal(extension.productType, '"com.apple.product-type.app-extension"');
console.log('iOS plugin idempotence, extension sources, and build settings passed');
