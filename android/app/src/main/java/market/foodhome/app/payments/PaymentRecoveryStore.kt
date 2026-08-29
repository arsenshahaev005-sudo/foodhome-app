package market.foodhome.app.payments

import android.content.Context
import android.content.SharedPreferences

interface PaymentRecoveryStore {
    fun read(): PaymentRecoverySnapshot?
    fun write(snapshot: PaymentRecoverySnapshot): Boolean
    fun clear(): Boolean
}

class SharedPreferencesPaymentRecoveryStore private constructor(
    private val preferences: SharedPreferences,
) : PaymentRecoveryStore {
    override fun read(): PaymentRecoverySnapshot? {
        val raw = preferences.getString(KEY_SNAPSHOT, null) ?: return null
        val decoded = PaymentRecoveryCodec.decode(raw)
        if (decoded == null) preferences.edit().remove(KEY_SNAPSHOT).commit()
        return decoded
    }

    override fun write(snapshot: PaymentRecoverySnapshot): Boolean =
        preferences.edit().putString(KEY_SNAPSHOT, PaymentRecoveryCodec.encode(snapshot)).commit()

    override fun clear(): Boolean = preferences.edit().remove(KEY_SNAPSHOT).commit()

    companion object {
        private const val PREFERENCES_NAME = "foodhome_payment_recovery"
        private const val KEY_SNAPSHOT = "snapshot"

        fun create(context: Context): SharedPreferencesPaymentRecoveryStore =
            SharedPreferencesPaymentRecoveryStore(
                context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
    }
}
