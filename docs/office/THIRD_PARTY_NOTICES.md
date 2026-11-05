# Office document engine third-party notices

- Apache POI 5.5.1, packaged through the Android-specific centic9/poi-on-android shaded port at commit `8e3cf66813feca0eef8283f0865145b4a80c02ee`, is licensed under Apache License 2.0. The port relocates required StAX and limited AWT types; unsupported graphics-dependent paths are not claimed.
- Apache Commons Lang 3.20.0, Commons Codec 1.20.0, and Commons Text 1.15.0 are used as declared by the Android build and are distributed under their upstream licenses.
- PDF rendering and PDF annotation use Android platform `PdfRenderer` and `PdfDocument` APIs.

All supported document parsing, editing, rendering and export operations run locally on the device. No cloud document service or runtime model download is used.
