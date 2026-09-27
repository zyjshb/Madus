// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MadusCore",
    platforms: [.macOS(.v13), .iOS(.v16)],
    products: [.library(name: "MadusCore", targets: ["MadusCore"])],
    targets: [
        .target(name: "MadusCore", path: "Madus/Core"),
        .testTarget(name: "MadusCoreTests", dependencies: ["MadusCore"], path: "Tests")
    ]
)
