package com.woocommerce.android.cardreader

import android.app.Application
import com.woocommerce.android.cardreader.connection.CardReader
import com.woocommerce.android.cardreader.connection.CardReaderDiscoveryEvents
import com.woocommerce.android.cardreader.connection.CardReaderStatus
import com.woocommerce.android.cardreader.connection.CardReaderTypesToDiscover
import com.woocommerce.android.cardreader.connection.event.SoftwareUpdateAvailability
import com.woocommerce.android.cardreader.connection.event.SoftwareUpdateStatus
import com.woocommerce.android.cardreader.internal.connection.BluetoothCardReaderMessages
import com.woocommerce.android.cardreader.payments.PaymentInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for consumers who want to start accepting POC card payments.
 */
@Suppress("TooManyFunctions")
interface CardReaderManager {
    val isInitialized: Boolean
    val readerStatus: StateFlow<CardReaderStatus>
    val softwareUpdateStatus: Flow<SoftwareUpdateStatus>
    val softwareUpdateAvailability: Flow<SoftwareUpdateAvailability>

    fun initialize(app: Application)
    fun discoverReaders(
        isSimulated: Boolean,
        cardReaderTypesToDiscover: CardReaderTypesToDiscover,
    ): Flow<CardReaderDiscoveryEvents>

    suspend fun connectToReader(cardReader: CardReader): Boolean
    suspend fun disconnectReader(): Boolean

    suspend fun collectPayment(paymentInfo: PaymentInfo): Flow<CardPaymentStatus>

    suspend fun retryCollectPayment(orderId: Long, paymentData: PaymentData): Flow<CardPaymentStatus>
    fun cancelPayment(paymentData: PaymentData)

    suspend fun listenForBluetoothCardReaderMessagesAsync(): Flow<BluetoothCardReaderMessages>

    suspend fun startAsyncSoftwareUpdate()
    suspend fun clearCachedCredentials()
    fun cancelOngoingFirmwareUpdate()
}
