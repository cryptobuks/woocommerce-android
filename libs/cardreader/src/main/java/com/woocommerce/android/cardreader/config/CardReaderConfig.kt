package com.woocommerce.android.cardreader.config

import android.os.Parcelable
import com.woocommerce.android.cardreader.connection.SpecificReader
import com.woocommerce.android.cardreader.payments.CardPaymentStatus.PaymentMethodType
import kotlinx.parcelize.Parcelize

sealed class CardReaderConfig : Parcelable

@Suppress("LongParameterList")
sealed class CardReaderConfigForSupportedCountry(
    val currency: String,
    val countryCode: String,
    val supportedReaders: List<SpecificReader>,
    val paymentMethodTypes: List<PaymentMethodType>,
    val supportedExtensions: List<SupportedExtension>,
) : CardReaderConfig()

fun CardReaderConfigForSupportedCountry.isExtensionSupported(type: SupportedExtensionType) =
    supportedExtensions.any { it.type == type }

fun CardReaderConfigForSupportedCountry.minSupportedVersionForExtension(type: SupportedExtensionType) =
    supportedExtensions.first { it.type == type }.supportedSince

@Parcelize
object CardReaderConfigForUnsupportedCountry : CardReaderConfig()

data class SupportedExtension(
    val type: SupportedExtensionType,
    val supportedSince: String,
)

enum class SupportedExtensionType {
    WC_PAY, STRIPE
}
