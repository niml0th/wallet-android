package com.mycelium.wallet.modularisation


import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.google.common.base.Optional
import com.mrd.bitlib.model.Address
import com.mrd.bitlib.util.Sha256Hash
import com.mycelium.spvmodule.IntentContract
import com.mycelium.spvmodule.providers.TransactionContract.AccountBalance
import com.mycelium.spvmodule.providers.TransactionContract.CurrentReceiveAddress
import com.mycelium.spvmodule.providers.TransactionContract.GetPrivateKeysCount
import com.mycelium.spvmodule.providers.TransactionContract.GetSyncProgress
import com.mycelium.wallet.WalletApplication
import com.mycelium.wallet.WalletApplication.getSpvModuleName
import com.mycelium.wapi.model.TransactionSummary
import com.mycelium.wapi.wallet.ConfirmationRiskProfileLocal
import com.mycelium.wapi.wallet.SpvBalanceFetcher
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.currency.CurrencyBasedBalance
import com.mycelium.wapi.wallet.currency.ExactBitcoinCashValue
import com.mycelium.wapi.wallet.currency.ExactBitcoinValue
import java.util.*
import com.mycelium.spvmodule.providers.TransactionContract.TransactionSummary as SpvTxSummary

class SpvBchFetcher(private val context: Context) : SpvBalanceFetcher {

    val sharedPreference = context.getSharedPreferences("spvbalancefetcher", Context.MODE_PRIVATE)

    override fun retrieveByHdAccountIndex(id: String, accountIndex: Int): CurrencyBasedBalance {
        val uri = AccountBalance.CONTENT_URI(getSpvModuleName(WalletAccount.Type.BCHBIP44)).buildUpon().appendEncodedPath(id).build()
        val selection = AccountBalance.SELECTION_ACCOUNT_INDEX
        return retrieveBalance(uri, selection, "" + accountIndex)
    }

    override fun retrieveBySingleAddressAccountId(id: String): CurrencyBasedBalance {
        val uri = AccountBalance.CONTENT_URI(getSpvModuleName(WalletAccount.Type.BCHSINGLEADDRESS)).buildUpon().appendEncodedPath(id).build()
        val selection = AccountBalance.SELECTION_SINGLE_ADDRESS_ACCOUNT_GUID
        return retrieveBalance(uri, selection, id)
    }

    private fun retrieveBalance(uri: Uri, selection: String, selectionArg: String): CurrencyBasedBalance {
        context.contentResolver.query(uri, null, selection, arrayOf(selectionArg), null).use {
            while (it?.moveToLast() == true) {
                return from(it)
            }
        }
        return CurrencyBasedBalance.ZERO_BITCOIN_CASH_BALANCE
    }

    private fun from(cursor: Cursor): CurrencyBasedBalance {
        // String id = cursor.getString(cursor.getColumnIndex(TransactionContract.AccountBalance._ID));
        val confirmed = cursor.getLong(cursor.getColumnIndex(AccountBalance.CONFIRMED))
        val receiving = cursor.getLong(cursor.getColumnIndex(AccountBalance.RECEIVING))
        val sending = cursor.getLong(cursor.getColumnIndex(AccountBalance.SENDING))

        return CurrencyBasedBalance(ExactBitcoinCashValue.from(confirmed),
                ExactBitcoinCashValue.from(sending), ExactBitcoinCashValue.from(receiving))
    }

    private fun txSummaryFromCursor(cursor: Cursor): TransactionSummary {
        val txId = cursor.getString(cursor.getColumnIndex(SpvTxSummary._ID))
        val valueSatoshis = cursor.getLong(cursor.getColumnIndex(SpvTxSummary.VALUE))
        val isIncoming = cursor.getInt(cursor.getColumnIndex(SpvTxSummary.IS_INCOMING))
        val time = cursor.getLong(cursor.getColumnIndex(SpvTxSummary.TIME))
        val height = cursor.getInt(cursor.getColumnIndex(SpvTxSummary.HEIGHT))
        val confirmations = cursor.getInt(cursor.getColumnIndex(SpvTxSummary.CONFIRMATIONS))
        val isQueuedOutgoing = cursor.getInt(cursor.getColumnIndex(SpvTxSummary.IS_QUEUED_OUTGOING))
        val unconfirmedChainLength = cursor.getInt(cursor.getColumnIndex(SpvTxSummary.CONFIRMATION_RISK_PROFILE_LENGTH))
        val hasRbfRisk = cursor.getInt(cursor.getColumnIndex(SpvTxSummary.CONFIRMATION_RISK_PROFILE_RBF_RISK))
        val hasDoubleSpend = cursor.getInt(cursor.getColumnIndex(SpvTxSummary.CONFIRMATION_RISK_PROFILE_DOUBLE_SPEND))
        val destinationAddress = cursor.getString(cursor.getColumnIndex(SpvTxSummary.DESTINATION_ADDRESS))
        val toAddresses = cursor.getString(cursor.getColumnIndex(SpvTxSummary.TO_ADDRESSES))

        return TransactionSummary(Sha256Hash.fromString(txId),
                ExactBitcoinValue.from(valueSatoshis),
                isIncoming != 0,
                time,
                height,
                confirmations,
                isQueuedOutgoing != 0,
                ConfirmationRiskProfileLocal(unconfirmedChainLength, hasRbfRisk != 0, hasDoubleSpend != 0),
                Optional.of(Address.fromString(destinationAddress)),
                retrieveAddresses(toAddresses))
    }

    private fun retrieveAddresses(toAddress: String) : List<Address> =
            toAddress.split(",".toRegex()).map { Address.fromString(it) }

    private fun retrieveTransactionSummary(uri: Uri, selection: String, selectionArg: String): List<TransactionSummary> {
        val transactionSummariesList = ArrayList<TransactionSummary>()
        context.contentResolver.query(uri, null, selection, arrayOf(selectionArg), null).use {
            while (it?.moveToNext() == true) {
                val txSummary = txSummaryFromCursor(it)
                transactionSummariesList.add(txSummary)
            }
        }
        return transactionSummariesList
    }

    private fun retrieveTransactionSummary(uri: Uri, selection: String, selectionArgs: Array<String>): List<TransactionSummary> {
        val transactionSummariesList = ArrayList<TransactionSummary>()
        context.contentResolver.query(uri, null, selection, selectionArgs, null).use {
            while (it?.moveToNext() == true) {
                val txSummary = txSummaryFromCursor(it)
                transactionSummariesList.add(txSummary)
            }
        }
        return transactionSummariesList
    }

    override fun retrieveTransactionSummaryByHdAccountIndex(id: String, accountIndex: Int): List<TransactionSummary> {
        val uri = SpvTxSummary.CONTENT_URI(getSpvModuleName(WalletAccount.Type.BCHBIP44)).buildUpon().appendEncodedPath(id).build()
        val selection = SpvTxSummary.SELECTION_ACCOUNT_INDEX
        return retrieveTransactionSummary(uri, selection, "" + accountIndex)
    }

    override fun retrieveTransactionSummaryByHdAccountIndex(id: String?, accountIndex: Int, since: Long): List<TransactionSummary> {
        val uri = SpvTxSummary.CONTENT_URI(getSpvModuleName(WalletAccount.Type.BCHBIP44)).buildUpon().appendEncodedPath(id).build()
        val selection = SpvTxSummary.SELECTION_ACCOUNT_INDEX_SINCE
        return retrieveTransactionSummary(uri, selection, arrayOf("" + accountIndex, "" + since))
    }

    override fun retrieveTransactionSummaryBySingleAddressAccountId(id: String): List<TransactionSummary> {
        val uri = SpvTxSummary.CONTENT_URI(getSpvModuleName(WalletAccount.Type.BCHSINGLEADDRESS)).buildUpon().appendEncodedPath(id).build()
        val selection = SpvTxSummary.SELECTION_SINGLE_ADDRESS_ACCOUNT_GUID
        return retrieveTransactionSummary(uri, selection, id)
    }

    override fun retrieveTransactionSummaryBySingleAddressAccountId(id: String, since: Long): List<TransactionSummary> {
        val uri = SpvTxSummary.CONTENT_URI(getSpvModuleName(WalletAccount.Type.BCHSINGLEADDRESS)).buildUpon().appendEncodedPath(id).build()
        val selection = SpvTxSummary.SELECTION_SINGLE_ADDRESS_ACCOUNT_GUID_SINCE
        return retrieveTransactionSummary(uri, selection, arrayOf(id, "" + since))
    }

    override fun requestTransactionsAsync(accountId: Int) {
        val service = IntentContract.ReceiveTransactions.createIntent(accountId)
        WalletApplication.sendToSpv(service, WalletAccount.Type.BCHBIP44)
    }

    override fun requestTransactionsFromSingleAddressAccountAsync(guid: String) {
        val service = IntentContract.ReceiveTransactionsSingleAddress.createIntent(guid)
        WalletApplication.sendToSpv(service, WalletAccount.Type.BCHSINGLEADDRESS)
    }

    override fun requestHdWalletAccountRemoval(accountIndex: Int) {
        val service = IntentContract.RemoveHdWalletAccount.createIntent(accountIndex)
        WalletApplication.sendToSpv(service, WalletAccount.Type.BCHBIP44)
    }
    override fun requestSingleAddressWalletAccountRemoval(guid: String)  {
        val service = IntentContract.RemoveSingleAddressWalletAccount.createIntent(guid)
        WalletApplication.sendToSpv(service, WalletAccount.Type.BCHSINGLEADDRESS)
    }

    override fun getSyncProgressPercents(): Int {
        val uri = GetSyncProgress.CONTENT_URI(getSpvModuleName(WalletAccount.Type.BCHBIP44)).buildUpon().build()
        context.contentResolver.query(uri, null, null, null, null).use {
            var result = 0
            if (it != null && it.columnCount != 0) {
                it.moveToFirst()
                result = it.getInt(0)
            }
            if(result == 100) {
                sharedPreference.edit().putBoolean("is_first_sync", false).apply()
            }
            return result
        }
    }

    override fun isFirstSync(): Boolean {
        return sharedPreference.getBoolean("is_first_sync", true)
    }

    override fun getCurrentReceiveAddress(accountIndex: Int): Address? {
        val uri = CurrentReceiveAddress.CONTENT_URI(getSpvModuleName(WalletAccount.Type.BCHBIP44)).buildUpon().build()
        val selection = AccountBalance.SELECTION_ACCOUNT_INDEX
        context.contentResolver.query(uri, null, selection, arrayOf("" + accountIndex), null).use {
            if (it != null && it.columnCount != 0) {
                it.moveToFirst()
                val address = it.getString(it.getColumnIndex(CurrentReceiveAddress.ADDRESS))
                return Address.fromString(address)
            } else {
                return null
            }
        }
    }

    override fun getPrivateKeysCount(accountIndex: Int): Int {
        val uri = GetPrivateKeysCount.CONTENT_URI(getSpvModuleName(WalletAccount.Type.BCHBIP44)).buildUpon().build()
        val selection = AccountBalance.SELECTION_ACCOUNT_INDEX
        context.contentResolver.query(uri, null, selection, arrayOf("" + accountIndex), null).use {
            if (it != null && it.columnCount != 0) {
                it.moveToFirst()
                return it.getInt(0)
            } else {
                return 0
            }
        }
    }
}
