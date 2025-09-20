package com.amos_tech_code.domain.services

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification

object FirebaseService {

    init {
        try {
            val firebaseJson = System.getenv("FIREBASE_CONFIG")
                ?: throw Exception("FIREBASE_CONFIG environment variable not found")

            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(firebaseJson.byteInputStream()))
                .build()

            FirebaseApp.initializeApp(options)
            println("Firebase initialized successfully")
        } catch (e: Exception) {
            println("Firebase initialization failed: ${e.message}")
        }
    }

    fun sendNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        try {

            //println("Attempting to send notification to token: $token")

            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data)
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            println("Successfully sent message: $response")
        } catch (e: Exception) {
            println("Error sending Firebase notification")
            e.printStackTrace() // Print full stack trace
            // Handle the exception as needed

        }
    }
}