<h1 align="center">
  <br>
  <img src="https://raw.githubusercontent.com/bruhmomentumtr/pagescan/main/app/src/main/res/mipmap-xxhdpi/ic_launcher.png" alt="PageScan" width="120">
  <br>
  PageScan
  <br>
</h1>

<h4 align="center">An intelligent, lightning-fast document scanning application for Android.</h4>

<p align="center">
  <a href="https://github.com/bruhmomentumtr/pagescan/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License">
  </a>
  <a href="https://developer.android.com/studio">
    <img src="https://img.shields.io/badge/Android-10.0%2B-brightgreen.svg" alt="Android Version">
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/Kotlin-1.9.0-purple.svg" alt="Kotlin">
  </a>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#building-from-source">Building from Source</a> •
  <a href="#license">License</a>
</p>

---

**PageScan** is a modern Android application built with cutting-edge technologies like **OpenCV**, **ML Kit**, and **CameraX**. It allows users to capture documents, automatically detect their edges in real-time, apply custom filters, extract text using OCR, and export the final result as a fully searchable, correctly-scaled Sandwich PDF or a high-resolution JPEG directly to their phone's Gallery.

## Features

* **📷 Real-Time Edge Detection**: Powered by OpenCV, the `CameraX` video feed dynamically analyzes and draws boundaries around documents in real-time.
* **✨ Intelligent Cropping & Perspective Warp**: Extracts the document precisely using an interactive 4-point scaling custom UI with a built-in magnifying glass (loupe).
* **🎨 Image Enhancements**: Apply multiple filters including *Magic Color*, *Grayscale*, and sharp *B&W* to improve document legibility and eliminate shadows. 
* **🔍 Optional OCR**: Leverage Google's ML Kit to instantly extract text from the scanned image.
* **📄 Sandwich PDF Exporting**: Generates sophisticated PDF files that layer the exact extracted text seamlessly and transparently over the processed document image (perfect for text selection and copying).
* **🖼️ MediaStore Integration**: Readily saves processed, filtered document images directly into the native Android Photos/Gallery.
* **📱 Material You Design**: A fully edge-to-edge UI leveraging modern Material 3 conventions, dynamic theming, bottom navigation, and fluid animations.

## Architecture

The application is written strictly in Kotlin using an MVVM architecture footprint. It relies on `StateFlow` and Coroutines for robust asynchronous image processing workflows.

**Key Libraries & Frameworks:**
- **Navigation Component**: Single-activity architecture using AndroidX Navigation.
- **CameraX**: Standard and reliable API lifecycle for camera streaming.
- **OpenCV (v4.9.0)**: Advanced computer vision utilized for image blurring, adaptive thresholds, edge contour detection (Canny), and perspective transformation.
- **Google ML Kit Vision**: Seamless on-device OCR logic.
- **Room Database**: Local SQL persistence layer for saved document objects and pages.
- **Material 3**: Comprehensive integration of Android UI components and typography.

## Building from Source

This project incorporates a custom build script that handles automated, fast compilation without requiring a full IDE environment sync.

### Prerequisites
- JDK 17
- Android SDK Platform 34
- CMake (for native libraries if modified, otherwise precompiled AARs are used via Maven)

### Running Locally
To compile a debugging version, run the custom PowerShell script in the repository root:

```powershell
.\smartbuild.ps1 debug
```

To create a release build:
```powershell
.\smartbuild.ps1 release
```

*Note: Generating a release build automatically relies on a generated `secrets.ps1` and `key.properties` configuration which are strictly `.gitignore`d for security.*

## License

This project is licensed under the GNU General Public License v3.0 (GPLv3).

See the [LICENSE](LICENSE) file for more information.
