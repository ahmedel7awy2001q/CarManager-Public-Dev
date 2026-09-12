package com.ahmed.carmanager.data.settings

internal class FuelPriceUpdater(
    private val store: FuelPriceStore
) {
    private val api = FuelPriceApi()

    suspend fun refreshIfNeeded(
        force: Boolean = false
    ): Result<Boolean> {
        if (!force && !store.needsRefresh()) {
            return Result.success(false)
        }

        return runCatching {
            val snapshot = api.fetchOfficialEgyptPrices()

            validateSnapshot(snapshot)

            store.saveMarketPrices(
                gasoline80 = snapshot.gasoline80,
                gasoline92 = snapshot.gasoline92,
                gasoline95 = snapshot.gasoline95,
                diesel = snapshot.diesel,
                source = snapshot.source,
                updatedAt = snapshot.fetchedAt
            )

            true
        }
    }

    private fun validateSnapshot(
        snapshot: FuelPriceSnapshot
    ) {
        require(snapshot.gasoline80 in VALID_PRICE_RANGE)
        require(snapshot.gasoline92 in VALID_PRICE_RANGE)
        require(snapshot.gasoline95 in VALID_PRICE_RANGE)
        require(snapshot.diesel in VALID_PRICE_RANGE)

        require(snapshot.gasoline92 >= snapshot.gasoline80) {
            "ترتيب أسعار بنزين 80 و92 غير منطقي."
        }

        require(snapshot.gasoline95 >= snapshot.gasoline92) {
            "ترتيب أسعار بنزين 92 و95 غير منطقي."
        }

        require(snapshot.source.isNotBlank()) {
            "مصدر أسعار الوقود غير محدد."
        }

        require(snapshot.fetchedAt > 0L) {
            "تاريخ تحديث أسعار الوقود غير صالح."
        }
    }

    private companion object {
        val VALID_PRICE_RANGE = 1.0..100.0
    }
}