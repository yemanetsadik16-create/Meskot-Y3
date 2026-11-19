package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType { CREATE, UPDATE, DELETE, LIST, GET, WRITE }

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): Nothing {
    val user = Firebase.auth.currentUser
    val providerArray = JSONArray().apply {
        user?.providerData?.forEach { info ->
            put(
                JSONObject().apply {
                    put("providerId", info.providerId)
                    put("displayName", info.displayName)
                    put("email", info.email)
                    put("photoUrl", info.photoUrl?.toString())
                }
            )
        }
    }
    val errJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.name.lowercase())
        put("path", path)
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", user?.uid)
                put("email", user?.email)
                put("emailVerified", user?.isEmailVerified)
                put("isAnonymous", user?.isAnonymous)
                put("tenantId", user?.tenantId)
                put("providerInfo", providerArray)
            }
        )
    }.toString()

    Log.e("FirestoreError", errJson, exception)
    throw RuntimeException(errJson, exception)
}
