Pod::Spec.new do |s|
  s.name = 'Subtext'
  s.version = '1.0.0'
  s.summary = 'Cue messaging and AI support for iOS'
  s.description = 'Native messaging connections and shared conversation storage for Cue.'
  s.license = { :type => 'MIT' }
  s.author = 'Cue'
  s.homepage = 'https://github.com/killdano/mirrormsg'
  s.platforms = { :ios => '16.4' }
  s.swift_version = '5.0'
  s.source = { :git => '' }
  s.static_framework = true
  s.dependency 'ExpoModulesCore'
  s.source_files = 'ios/*.swift', 'shared-ios/*.swift'
  s.vendored_frameworks = 'ios/Frameworks/Messagebridges.xcframework'
  s.frameworks = 'WebKit', 'Security'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
end
