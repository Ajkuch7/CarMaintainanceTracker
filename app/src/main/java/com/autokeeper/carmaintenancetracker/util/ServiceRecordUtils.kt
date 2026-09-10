package com.autokeeper.carmaintenancetracker.util

import com.autokeeper.carmaintenancetracker.data.ServiceRecord

fun filterServiceRecords(records: List<ServiceRecord>, query: String, serviceType: String): List<ServiceRecord> {
    val normalizedQuery = query.trim().lowercase()
    return records.filter { record ->
        val typeMatches = serviceType == "All" || record.serviceType == serviceType
        val queryMatches = normalizedQuery.isBlank() ||
            record.serviceType.lowercase().contains(normalizedQuery) ||
            record.notes.lowercase().contains(normalizedQuery) ||
            record.shopName.lowercase().contains(normalizedQuery)
        typeMatches && queryMatches
    }.sortedByDescending { it.serviceDateMillis }
}

fun lifetimeExpense(records: List<ServiceRecord>): Double = records.sumOf { it.cost }
