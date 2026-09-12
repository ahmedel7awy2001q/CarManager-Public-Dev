package com.ahmed.carmanager.data.repository

/**
 * Pure fuel-cycle math shared by add/edit/delete paths.
 *
 * Consumption is only measured when a full-tank record closes a cycle that started at an earlier
 * full-tank record. Every partial refill inside that cycle is included in both liters and cost.
 */
internal object FuelCycleMath {
    data class Row(
        val key: String,
        val odometerKm: Double,
        val liters: Double,
        val amountPaid: Double,
        val isFullTank: Boolean
    )

    data class Derived(
        val distanceSincePreviousKm: Double?,
        val consumptionLitersPer100Km: Double?,
        val kmPerLiter: Double?,
        val costPerKm: Double?
    )

    fun recalculate(rows: List<Row>): Map<String, Derived> {
        var previousOdometer: Double? = null
        var previousFullOdometer: Double? = null
        var litersSinceFull = 0.0
        var amountSinceFull = 0.0
        val result = LinkedHashMap<String, Derived>(rows.size)

        rows.forEach { row ->
            val distanceSincePrevious = previousOdometer
                ?.let { row.odometerKm - it }
                ?.takeIf { it.isFinite() && it >= 0.0 }

            if (previousFullOdometer != null) {
                litersSinceFull += row.liters
                amountSinceFull += row.amountPaid
            }

            var consumption: Double? = null
            var costPerKm: Double? = null
            if (row.isFullTank) {
                previousFullOdometer?.let { anchorOdometer ->
                    val fullDistance = row.odometerKm - anchorOdometer
                    if (fullDistance.isFinite() && fullDistance > 0.0) {
                        consumption = (litersSinceFull / fullDistance * 100.0)
                            .takeIf { it.isFinite() && it > 0.0 }
                        costPerKm = (amountSinceFull / fullDistance)
                            .takeIf { it.isFinite() && it >= 0.0 }
                    }
                }
                previousFullOdometer = row.odometerKm
                litersSinceFull = 0.0
                amountSinceFull = 0.0
            }

            val kmPerLiter = consumption
                ?.takeIf { it > 0.0 }
                ?.let { 100.0 / it }
                ?.takeIf { it.isFinite() && it > 0.0 }

            result[row.key] = Derived(
                distanceSincePreviousKm = distanceSincePrevious,
                consumptionLitersPer100Km = consumption,
                kmPerLiter = kmPerLiter,
                costPerKm = costPerKm
            )
            previousOdometer = row.odometerKm
        }

        return result
    }

    fun estimatedRangeKm(
        isFullTank: Boolean,
        purchasedLiters: Double,
        tankCapacityLiters: Double?,
        consumptionLitersPer100Km: Double?
    ): Double? {
        val consumption = consumptionLitersPer100Km
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return null
        val rangeLiters = if (isFullTank) {
            tankCapacityLiters?.takeIf { it.isFinite() && it > 0.0 } ?: purchasedLiters
        } else {
            purchasedLiters
        }
        return (rangeLiters / consumption * 100.0)
            .takeIf { it.isFinite() && it >= 0.0 }
    }
}
