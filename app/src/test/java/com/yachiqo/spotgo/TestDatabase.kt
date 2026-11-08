package com.yachiqo.spotgo

import com.yachiqo.spotgo.data.local.SpotGoDatabase

/**
 * Snapshot of the in-memory database.
 *
 * Every repository is a singleton over the shared [SpotGoDatabase] and all the
 * local tests run in the same JVM, so a test that creates accounts, vehicles,
 * reservations or payments would leak into the next one. Constructing this
 * class captures the current content of every table and [restore] puts it back,
 * so each test starts from the seeds and cannot disturb the rest of the suite.
 */
internal class TestDatabase {

    private val tables: List<Pair<MutableList<*>, List<*>>> = listOf(
        SpotGoDatabase.accounts to SpotGoDatabase.accounts.toList(),
        SpotGoDatabase.credentials to SpotGoDatabase.credentials.toList(),
        SpotGoDatabase.sessions to SpotGoDatabase.sessions.toList(),
        SpotGoDatabase.profiles to SpotGoDatabase.profiles.toList(),
        SpotGoDatabase.vehicles to SpotGoDatabase.vehicles.toList(),
        SpotGoDatabase.tenants to SpotGoDatabase.tenants.toList(),
        SpotGoDatabase.parking_facilities to SpotGoDatabase.parking_facilities.toList(),
        SpotGoDatabase.parking_zones to SpotGoDatabase.parking_zones.toList(),
        SpotGoDatabase.parking_spots to SpotGoDatabase.parking_spots.toList(),
        SpotGoDatabase.reservations to SpotGoDatabase.reservations.toList(),
        SpotGoDatabase.favorites to SpotGoDatabase.favorites.toList(),
        SpotGoDatabase.occupancy_sensors to SpotGoDatabase.occupancy_sensors.toList(),
        SpotGoDatabase.occupancy_readings to SpotGoDatabase.occupancy_readings.toList(),
        SpotGoDatabase.guest_parking_sessions to SpotGoDatabase.guest_parking_sessions.toList(),
        SpotGoDatabase.payment_tokens to SpotGoDatabase.payment_tokens.toList(),
        SpotGoDatabase.digital_payments to SpotGoDatabase.digital_payments.toList(),
        SpotGoDatabase.payment_attempts to SpotGoDatabase.payment_attempts.toList(),
        SpotGoDatabase.refunds to SpotGoDatabase.refunds.toList(),
        SpotGoDatabase.billing_documents to SpotGoDatabase.billing_documents.toList(),
        SpotGoDatabase.additional_charges to SpotGoDatabase.additional_charges.toList(),
        SpotGoDatabase.outstanding_balances to SpotGoDatabase.outstanding_balances.toList(),
        SpotGoDatabase.subscriptions to SpotGoDatabase.subscriptions.toList(),
    )

    /** Puts every table back to the state captured when this object was created. */
    @Suppress("UNCHECKED_CAST")
    fun restore() {
        tables.forEach { (target, contents) ->
            val list = target as MutableList<Any?>
            list.clear()
            list.addAll(contents)
        }
    }
}
