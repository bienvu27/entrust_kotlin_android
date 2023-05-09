package com.entrust.identityGuard.mobile.sdk.example

import android.app.*
import com.entrust.identityGuard.mobile.sdk.PlatformDelegate
import com.entrust.identityGuard.mobile.sdk.tokenproviders.ThirdPartyTokenManagerFactory
import org.tinylog.Level
import org.tinylog.Logger
import org.tinylog.configuration.Configuration

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure Logger for APP with LOG_LEVEL
        configureLogger(Level.DEBUG)

        // Initialize Soft Token SDK
        ThirdPartyTokenManagerFactory.setContext(applicationContext)
        PlatformDelegate.initialize(applicationContext)
        PlatformDelegate.setApplicationScheme("sampleapp")
    }

    private fun configureLogger(logLevel: Level) {
        val logcat_writer = "writer1"
        try {
            /**
             * Configure writer1 for logcat'
             *
             * https://tinylog.org/v2/configuration/
             */
            Configuration.set(logcat_writer, "logcat")
            Configuration.set("$logcat_writer.level", logLevel.name)
            Configuration.set("$logcat_writer.format", "{message}")
        } catch (e: UnsupportedOperationException) {
//            Logger.error(e.message)
            Logger.tag(e.message)
        }
    }
}