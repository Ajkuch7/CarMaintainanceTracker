package com.autokeeper.carmaintenancetracker.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AutoKeeperRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    fun currentUserId(): String? = auth.currentUser?.uid

    fun currentEmail(): String = auth.currentUser?.email.orEmpty()

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
        ensureProfile()
    }

    suspend fun signUp(email: String, password: String, displayName: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
        ensureProfile(displayName)
    }

    fun signOut() = auth.signOut()

    private suspend fun ensureProfile(displayName: String = "") {
        val user = auth.currentUser ?: return
        val profile = UserProfile(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = displayName.ifBlank { user.email?.substringBefore("@").orEmpty() }
        )
        firestore.collection(USERS_COLLECTION).document(user.uid).set(profile).await()
    }

    fun observeVehicles(userId: String): Flow<List<Vehicle>> = callbackFlow {
        val listener = firestore.collection(VEHICLES_COLLECTION)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val vehicles = snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.toObject<Vehicle>()?.copy(id = document.id)
                }
                trySend(vehicles)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addVehicle(vehicle: Vehicle) {
        firestore.collection(VEHICLES_COLLECTION).add(vehicle).await()
    }

    fun observeProfile(userId: String): Flow<UserProfile> = callbackFlow {
        val listener = firestore.collection(USERS_COLLECTION).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val profile = snapshot?.toObject<UserProfile>() ?: UserProfile(uid = userId, email = currentEmail())
                trySend(profile.copy(uid = userId))
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateDisplayName(userId: String, displayName: String) {
        firestore.collection(USERS_COLLECTION).document(userId)
            .update(mapOf("displayName" to displayName))
            .await()
    }

    fun observeServiceRecords(userId: String, vehicleId: String?): Flow<List<ServiceRecord>> = callbackFlow {
        var query = firestore.collection(SERVICE_COLLECTION).whereEqualTo("userId", userId)
        if (!vehicleId.isNullOrBlank()) query = query.whereEqualTo("vehicleId", vehicleId)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val records = snapshot?.documents.orEmpty().mapNotNull { document ->
                document.toObject<ServiceRecord>()?.copy(id = document.id)
            }
            trySend(records)
        }
        awaitClose { listener.remove() }
    }

    suspend fun addServiceRecord(record: ServiceRecord): String {
        return firestore.collection(SERVICE_COLLECTION).add(record).await().id
    }

    suspend fun uploadReceiptImage(localUri: Uri, userId: String): String {
        val reference = storage.reference.child("receipts/$userId/${System.currentTimeMillis()}.jpg")
        reference.putFile(localUri).await()
        return reference.downloadUrl.await().toString()
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val VEHICLES_COLLECTION = "vehicles"
        const val SERVICE_COLLECTION = "serviceRecords"
    }
}
