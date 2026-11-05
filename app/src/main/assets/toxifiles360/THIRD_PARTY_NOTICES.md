# ToxiFiles 360 third-party notices

- Apache POI 5.5.1, packaged through the Android-specific centic9/poi-on-android shaded port at commit 8e3cf66813feca0eef8283f0865145b4a80c02ee: Apache License 2.0. This port relocates the required StAX and limited AWT types; graphics, image, chart, and other unsupported AWT-dependent paths are not claimed.
- Tesseract4Android 4.9.0: Apache License 2.0. Android JNI wrapper for local OCR.
- Tesseract 5 / Leptonica / tessdata_fast English model: upstream notices and licenses apply. English traineddata is stored as eight ordered asset parts and atomically reassembled on-device; reconstructed size: 4,113,088 bytes; SHA-256: 7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2.
- Apache PDFBox was evaluated but not included: the Android port is based on PDFBox 2.0.27 (2023) and is not maintained at a cadence suitable for processing untrusted PDFs. Native Android PdfRenderer/PdfDocument are used instead.
- LibreOfficeKit was evaluated but not embedded: its Android integration requires building and packaging a large multi-ABI native engine, not a safe drop-in Gradle dependency for this Java-only project.

All document processing is local. No runtime model download, cloud API, telemetry, or network conversion is used by this module.
