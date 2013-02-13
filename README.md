DrawableBackgroundDownloader
============================

This was my ever expanding class to handle background queueing and downloading of images on an infinite scroll. Being unable to deallocate memory immediately in an Android app posed a bigger problem than I thought. This uses SoftReference and caching to keep as well as non-blocking threads to keep scrolling smooth.

Last used in https://play.google.com/store/apps/details?id=com.theknot.gowns