package com.tonapps.tonkeeper.ui.screen.backup.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class BackupViewModel(
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
    private val passcodeManager: PasscodeManager
): ViewModel() {

    val uiItemsFlow = combine(backupRepository.stream, accountRepository.selectedWalletFlow) { backups, wallet ->
        backups.filter { it.walletId == wallet.id }
    }.map {
        val backupsCount = it.size
        val items = mutableListOf<Item>()
        items.add(Item.Header)
        items.add(Item.Space)

        for ((index, backup) in it.withIndex()) {
            val position = ListCell.getPosition(backupsCount, index)
            items.add(Item.Backup(position, backup))
        }
        if (backupsCount > 0) {
            items.add(Item.Space)
            items.add(Item.RecoveryPhrase)
        } else {
            items.add(Item.ManualBackup)
        }
        items.toList()
    }.flowOn(Dispatchers.IO)

    fun getRecoveryPhrase(
        context: Context
    ) = accountRepository.selectedWalletFlow.combine(passcodeManager.confirmationFlow(context, context.getString(Localization.app_name))) { wallet, _ ->
        accountRepository.getMnemonic(wallet.id)
    }.take(1)
}