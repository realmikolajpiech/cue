// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "CueShared", platforms: [.macOS(.v13)], products: [.library(name: "CueShared", targets: ["CueShared"])], targets: [
  .target(name: "CueShared", path: "shared-ios"),
  .testTarget(name: "CueSharedTests", dependencies: ["CueShared"], path: "ios-tests"),
])
