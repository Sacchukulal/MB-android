package com.magicbill.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MagicBillApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initPushNotifications()
    }

    /**
     * Hook for Firebase Cloud Messaging (planned, later phase).
     * When FCM lands: add the google-services plugin + firebase-messaging
     * dependency, then initialize and register the token here.
     */
    private fun initPushNotifications() {
        // Intentionally empty for now.
    }
}
