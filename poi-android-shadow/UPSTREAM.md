# Android Apache POI compatibility layer

The source shims in this module are copied from centic9/poi-on-android at commit
8e3cf66813feca0eef8283f0865145b4a80c02ee. The upstream project packages Apache
POI 5.5.1 and relocates the required StAX and selected AWT classes for Android.
The module intentionally does not include curvesapi, so chart/graphics paths are
not claimed. Source shims and POI are Apache License 2.0; see
app/src/main/assets/toxifiles360/LICENSE-APACHE-2.0.txt and the third-party notices.
