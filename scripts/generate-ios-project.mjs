// Deterministic Xcode project generation; no XcodeGen/CocoaPods dependency.
import { createHash } from 'node:crypto';
import { readdirSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'ios');
const check = process.argv.includes('--check');
const id = name => createHash('sha256').update(name).digest('hex').slice(0, 24).toUpperCase();
const quote = value => JSON.stringify(value);
function walk(folder) {
  return readdirSync(folder, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? walk(join(folder, entry.name)) : [join(folder, entry.name)]);
}
const sources = walk(join(root, 'Madus')).filter(file => file.endsWith('.swift')).map(file => relative(root, file).replaceAll('\\', '/')).sort();
const resources = ['Madus/Assets.xcassets', 'Madus/PrivacyInfo.xcprivacy'];
const files = [...sources, ...resources, 'Madus/Info.plist'];
const objects = [];
const add = (name, value) => objects.push(`\t\t${id(name)} = { ${value} };`);
const refs = values => `(${values.map(id).join(', ')},)`;
for (const file of files) {
  const type = file.endsWith('.swift') ? 'sourcecode.swift' : file.endsWith('.xcassets') ? 'folder.assetcatalog' : 'text.plist.xml';
  add(file, `isa = PBXFileReference; lastKnownFileType = ${quote(type)}; path = ${quote(file)}; sourceTree = "<group>";`);
  if (!file.endsWith('Info.plist')) add(`build:${file}`, `isa = PBXBuildFile; fileRef = ${id(file)};`);
}
add('app', 'isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = Madus.app; sourceTree = BUILT_PRODUCTS_DIR;');
add('products', `isa = PBXGroup; children = ${refs(['app'])}; name = Products; sourceTree = "<group>";`);
add('group', `isa = PBXGroup; children = ${refs([...files, 'products'])}; sourceTree = "<group>";`);
add('sources', `isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ${refs(sources.map(file => `build:${file}`))}; runOnlyForDeploymentPostprocessing = 0;`);
add('resources', `isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ${refs(resources.map(file => `build:${file}`))}; runOnlyForDeploymentPostprocessing = 0;`);
add('frameworks', 'isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0;');
add('target', `isa = PBXNativeTarget; buildConfigurationList = ${id('target-configs')}; buildPhases = ${refs(['sources', 'frameworks', 'resources'])}; buildRules = (); dependencies = (); name = Madus; productName = Madus; productReference = ${id('app')}; productType = "com.apple.product-type.application";`);
add('project', `isa = PBXProject; attributes = { BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 1600; TargetAttributes = { ${id('target')} = { CreatedOnToolsVersion = 16.0; SystemCapabilities = { com.apple.BackgroundModes = { enabled = 1; }; }; }; }; }; buildConfigurationList = ${id('project-configs')}; compatibilityVersion = "Xcode 14.0"; developmentRegion = zh_CN; hasScannedForEncodings = 0; knownRegions = (zh_CN, en, Base); mainGroup = ${id('group')}; productRefGroup = ${id('products')}; projectDirPath = ""; projectRoot = ""; targets = ${refs(['target'])};`);
for (const config of ['Debug', 'Release']) {
  const debug = config === 'Debug';
  add(`project-${config}`, `isa = XCBuildConfiguration; name = ${config}; buildSettings = { CLANG_ENABLE_MODULES = YES; CLANG_ENABLE_OBJC_ARC = YES; SDKROOT = iphoneos; IPHONEOS_DEPLOYMENT_TARGET = 16.0; SWIFT_VERSION = 5.0; SWIFT_STRICT_CONCURRENCY = targeted; DEBUG_INFORMATION_FORMAT = ${quote(debug ? 'dwarf' : 'dwarf-with-dsym')}; ENABLE_TESTABILITY = ${debug ? 'YES' : 'NO'}; SWIFT_OPTIMIZATION_LEVEL = ${quote(debug ? '-Onone' : '-O')}; ${debug ? 'SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;' : 'SWIFT_COMPILATION_MODE = wholemodule;'} };`);
  add(`target-${config}`, `isa = XCBuildConfiguration; name = ${config}; buildSettings = { ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon; CODE_SIGN_STYLE = Automatic; CURRENT_PROJECT_VERSION = 1; GENERATE_INFOPLIST_FILE = NO; INFOPLIST_FILE = Madus/Info.plist; LD_RUNPATH_SEARCH_PATHS = ("$(inherited)", "@executable_path/Frameworks"); MARKETING_VERSION = 0.1.0; PRODUCT_BUNDLE_IDENTIFIER = com.madus.ios; PRODUCT_NAME = "$(TARGET_NAME)"; SUPPORTED_PLATFORMS = "iphoneos iphonesimulator"; SUPPORTS_MACCATALYST = NO; TARGETED_DEVICE_FAMILY = "1,2"; };`);
}
for (const group of ['project', 'target']) add(`${group}-configs`, `isa = XCConfigurationList; buildConfigurations = ${refs([`${group}-Debug`, `${group}-Release`])}; defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;`);
function output(file, content) {
  const path = join(root, file);
  if (check) {
    if (readFileSync(path, 'utf8') !== content) throw new Error(`${file} is stale. Run node scripts/generate-ios-project.mjs`);
  } else { mkdirSync(dirname(path), { recursive: true }); writeFileSync(path, content); }
}
output('Madus.xcodeproj/project.pbxproj', `// !$*UTF8*$!\n{\n\tarchiveVersion = 1;\n\tclasses = {};\n\tobjectVersion = 56;\n\tobjects = {\n${objects.join('\n')}\n\t};\n\trootObject = ${id('project')};\n}\n`);
const target = `<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="${id('target')}" BuildableName="Madus.app" BlueprintName="Madus" ReferencedContainer="container:Madus.xcodeproj"/>`;
output('Madus.xcodeproj/xcshareddata/xcschemes/Madus.xcscheme', `<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
    <BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">${target}</BuildActionEntry>
  </BuildActionEntries></BuildAction>
  <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables/></TestAction>
  <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">${target}</BuildableProductRunnable></LaunchAction>
  <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">${target}</BuildableProductRunnable></ProfileAction>
  <AnalyzeAction buildConfiguration="Debug"/>
  <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
`);
console.log(`${check ? 'Checked' : 'Generated'} Madus Xcode project (${sources.length} Swift files).`);
