package com.autokeeper.carmaintenancetracker

import com.autokeeper.carmaintenancetracker.data.ServiceRecord
import com.autokeeper.carmaintenancetracker.util.filterServiceRecords
import com.autokeeper.carmaintenancetracker.util.lifetimeExpense
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServiceRecordUtilsTest {

    @Test
    fun filterServiceRecords_filtersByQueryAndType() {
        val records = listOf(
            ServiceRecord(serviceType = "Oil Change", notes = "Synthetic", shopName = "Quick Lube", serviceDateMillis = 1000L),
            ServiceRecord(serviceType = "Brake Service", notes = "Pads replaced", shopName = "Brake Pros", serviceDateMillis = 2000L),
            ServiceRecord(serviceType = "Tire Rotation", notes = "Front to rear", shopName = "Quick Lube", serviceDateMillis = 3000L)
        )

        val filtered = filterServiceRecords(records, query = "quick", serviceType = "Oil Change")

        assertThat(filtered).hasSize(1)
        assertThat(filtered.first().serviceType).isEqualTo("Oil Change")
    }

    @Test
    fun lifetimeExpense_returnsTotalCost() {
        val records = listOf(
            ServiceRecord(cost = 35.5),
            ServiceRecord(cost = 120.0),
            ServiceRecord(cost = 44.5)
        )

        assertThat(lifetimeExpense(records)).isEqualTo(200.0)
    }
}
