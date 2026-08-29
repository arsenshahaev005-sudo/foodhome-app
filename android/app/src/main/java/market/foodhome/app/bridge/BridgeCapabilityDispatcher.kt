package market.foodhome.app.bridge

import org.json.JSONObject

fun interface BridgeCapabilityDispatcher {
    fun dispatch(request: BridgeRequest, completion: (BridgeDispatchResult) -> Unit)
}

sealed interface BridgeDispatchResult {
    data class Success(val result: JSONObject) : BridgeDispatchResult
    data class Failure(
        val code: String,
        val message: String,
        val retryable: Boolean = false,
    ) : BridgeDispatchResult
}

object UnavailableBridgeCapabilityDispatcher : BridgeCapabilityDispatcher {
    override fun dispatch(
        request: BridgeRequest,
        completion: (BridgeDispatchResult) -> Unit,
    ) {
        completion(
            BridgeDispatchResult.Failure(
                code = "CAPABILITY_UNAVAILABLE",
                message = "Capability is unavailable",
            ),
        )
    }
}
