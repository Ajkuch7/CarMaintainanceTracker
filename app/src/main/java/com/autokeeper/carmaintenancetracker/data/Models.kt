package com.autokeeper.carmaintenancetracker.data

data class Vehicle(
    val id: String = "",
    val userId: String = "",
    val nickname: String = "",
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val odometer: Int = 0
)

data class ServiceRecord(
    val id: String = "",
    val userId: String = "",
    val vehicleId: String = "",
    val serviceType: String = "",
    val serviceDateMillis: Long = 0L,
    val mileage: Int = 0,
    val cost: Double = 0.0,
    val notes: String = "",
    val receiptUrl: String = "",
    val shopName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val nextReminderMillis: Long? = null
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = ""
)
