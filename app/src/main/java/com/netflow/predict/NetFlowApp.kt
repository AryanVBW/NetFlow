package com.netflow.predict

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class — required by Hilt for dependency injection setup.
 *
 * Declared in AndroidManifest.xml via android:name=".NetFlowApp"
 */
@HiltAndroidApp
class NetFlowApp : Application()
