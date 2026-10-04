#!/usr/bin/env python3
import os, pathlib, plistlib, subprocess, sys
root = pathlib.Path(__file__).resolve().parents[2]
app = root / '.artifacts/ios/CueKeyboardSmoke.app'
app.mkdir(parents=True, exist_ok=True)
device = sys.argv[1]
sdk = subprocess.check_output(['xcrun','--sdk','iphonesimulator','--show-sdk-path'], text=True).strip()
sources = list((root/'modules/subtext/shared-ios').glob('*.swift')) + [root/'extensions/cue-keyboard/CueKeyboardViewController.swift', root/'scripts/ios/keyboard-smoke.swift']
subprocess.run(['xcrun','swiftc','-swift-version','5','-target','arm64-apple-ios16.4-simulator','-sdk',sdk,'-module-name','CueKeyboardSmoke','-o',str(app/'CueKeyboardSmoke'),*map(str,sources)],check=True)
(app/'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier':'com.mikolajpiech.guardian.keyboard-smoke','CFBundleExecutable':'CueKeyboardSmoke','CFBundleName':'Cue Keyboard Smoke','CFBundlePackageType':'APPL','CFBundleVersion':'1','CFBundleShortVersionString':'1.0','MinimumOSVersion':'16.4','CueAppGroup':'group.com.mikolajpiech.guardian.keyboard-smoke','UILaunchScreen':{},'UIApplicationSceneManifest':{'UIApplicationSupportsMultipleScenes':False,'UISceneConfigurations':{'UIWindowSceneSessionRoleApplication':[{'UISceneConfigurationName':'Default','UISceneDelegateClassName':'CueKeyboardSmoke.Scene'}]}}}))
import shutil
shutil.copy(root/'assets/cue-mascot-cutout.png', app/'CueMascot.png')
entitlements = root / '.artifacts/ios/smoke.entitlements'
entitlements.write_bytes(plistlib.dumps({'com.apple.security.application-groups':['group.com.mikolajpiech.guardian.keyboard-smoke']}))
subprocess.run(['codesign','--force','--sign','-','--entitlements',str(entitlements),str(app)],check=True)
subprocess.run(['xcrun','simctl','install',device,str(app)],check=True)
import time
started = time.time() * 1000
subprocess.run(['xcrun','simctl','launch','--terminate-running-process',device,'com.mikolajpiech.guardian.keyboard-smoke'],check=True)

import json, time
container = pathlib.Path(subprocess.check_output(['xcrun','simctl','get_app_container',device,'com.mikolajpiech.guardian.keyboard-smoke','data'],text=True).strip())
for _ in range(40):
    try:
        result = json.loads((container/'Documents/smoke.json').read_text())
        if result.get('finishedAt', 0) < started:
            time.sleep(0.25); continue
        if 'failure' in result: raise RuntimeError(result['failure'])
        print(json.dumps(result)); break
    except FileNotFoundError: time.sleep(0.25)
else: raise RuntimeError('Keyboard test did not finish')
