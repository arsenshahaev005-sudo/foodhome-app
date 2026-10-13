package market.foodhome.app.location

sealed interface LocationRequestResult {
    data class Granted(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Double,
        val precise: Boolean,
    ) : LocationRequestResult

    data class Failed(
        val code: String,
        val message: String,
        val retryable: Boolean = false,
    ) : LocationRequestResult
}
