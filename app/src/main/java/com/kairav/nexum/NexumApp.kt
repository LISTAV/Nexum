package com.kairav.nexum

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kairav.nexum.utils.NotificationUtils
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * NexumApp is the main application class for the Nexum project.
 */
@HiltAndroidApp
class NexumApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createNotificationChannel(this)
    }

    override val workManagerConfiguration: Configuration
        get() {
            // Log to confirm custom configuration is being provided
            android.util.Log.d("NexumApp", "Providing custom WorkManager configuration with HiltWorkerFactory")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
        }
}
