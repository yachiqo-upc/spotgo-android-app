package com.yachiqo.spotgo.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yachiqo.spotgo.data.remote.FcmDeviceRegistration

class SpotGoFirebaseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        FcmDeviceRegistration.onRegistered(applicationContext, installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        FcmDeviceRegistration.showNotification(
            context = this,
            title = message.notification?.title ?: message.data["title"],
            body = message.notification?.body ?: message.data["body"],
        )
    }
}
