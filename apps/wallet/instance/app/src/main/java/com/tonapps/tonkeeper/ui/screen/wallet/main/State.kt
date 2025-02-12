package com.tonapps.tonkeeper.ui.screen.wallet.main

import android.util.Log
import com.tonapps.icu.Coins
import com.tonapps.icu.Coins.Companion.DEFAULT_DECIMALS
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item.BalanceType
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.NotificationEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.core.isAvailableBiometric
import com.tonapps.wallet.data.passcode.PasscodeBiometric
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.localization.Localization

sealed class State {

    private enum class SetupType {
        Push,
        Biometry,
        Telegram,
        Backup,
    }

    data class Setup(
        val pushEnabled: Boolean,
        val biometryEnabled: Boolean,
        val hasBackup: Boolean,
        val showTelegramChannel: Boolean,
    ): State()

    data class Assets(
        val currency: WalletCurrency,
        val list: List<AssetsEntity>,
        val fromCache: Boolean,
    ): State() {

        val size: Int
            get() = list.size

        private fun getTotalBalance(wallet: WalletEntity): Coins {
            return if (wallet.testnet) {
                list.first().fiat
            } else {
                list.map { it.fiat }.sumOf { it }
            }
        }

        fun getBalanceType(wallet: WalletEntity): BalanceType {
            val balance = getTotalBalance(wallet)
            return when {
                balance > Coins.of(20.0, DEFAULT_DECIMALS) -> BalanceType.Huge
                balance > Coins.of(2.0, DEFAULT_DECIMALS) -> BalanceType.Positive
                else -> BalanceType.Zero
            }
        }

        fun getTotalBalanceFormat(
            wallet: WalletEntity
        ): CharSequence {
            val total = getTotalBalance(wallet)
            return CurrencyFormatter.formatFiat(currency.code, total)
        }
    }

    data class Main(
        val wallet: WalletEntity,
        val assets: Assets,
        val hasBackup: Boolean,
    ): State() {

        private val totalBalanceFormat: CharSequence
            get() = assets.getTotalBalanceFormat(wallet)

        private val balanceType: BalanceType
            get() = assets.getBalanceType(wallet)

        private fun uiItemsTokens(hiddenBalance: Boolean): List<Item> {
            val currencyCode = assets.currency.code
            val uiItems = mutableListOf<Item>()
            uiItems.add(Item.Space(true))

            for ((index, asset) in assets.list.withIndex()) {
                val position = ListCell.getPosition(assets.list.size, index)
                if (asset is AssetsEntity.Staked) {
                    val staked = asset.staked
                    val item = Item.Stake(
                        position = position,
                        poolAddress = staked.pool.address,
                        poolName = staked.pool.name,
                        poolImplementation = staked.pool.implementation,
                        balance = staked.amount,
                        balanceFormat = CurrencyFormatter.format(value = staked.amount),
                        message = null,
                        fiat = staked.fiatBalance,
                        fiatFormat = CurrencyFormatter.formatFiat(currencyCode, staked.fiatBalance),
                    )
                    uiItems.add(item)
                } else if (asset is AssetsEntity.Token) {
                    val item = Item.Token(
                        position = position,
                        token = asset.token,
                        hiddenBalance = hiddenBalance,
                        testnet = wallet.testnet,
                        currencyCode = currencyCode
                    )
                    uiItems.add(item)
                }
            }
            uiItems.add(Item.Space(true))
            uiItems.add(Item.Manage(true))
            return uiItems.toList()
        }

        private fun uiItemBalance(
            hiddenBalance: Boolean,
            status: Item.Status,
            lastUpdatedFormat: String,
        ): Item.Balance {
            return Item.Balance(
                balance = totalBalanceFormat,
                address = wallet.address,
                walletType = wallet.type,
                walletVersion = wallet.version,
                status = status,
                hiddenBalance = hiddenBalance,
                hasBackup = hasBackup,
                balanceType = balanceType,
                lastUpdatedFormat = lastUpdatedFormat
            )
        }

        private fun uiItemActions(
            config: ConfigEntity
        ): Item.Actions {
            return Item.Actions(
                address = wallet.address,
                token = TokenEntity.TON,
                walletType = wallet.type,
                swapUri = config.swapUri,
                disableSwap = config.flags.disableSwap
            )
        }

        private fun uiItemsSetup(
            walletId: String,
            config: ConfigEntity,
            setupTypes: List<SetupType>
        ): List<Item> {
            if (1 >= setupTypes.size) {
                return emptyList()
            }
            val uiItems = mutableListOf<Item>()
            uiItems.add(Item.SetupTitle(
                walletId = walletId,
                showDone = !setupTypes.contains(SetupType.Backup)
            ))
            for ((index, setupType) in setupTypes.withIndex()) {
                val position = ListCell.getPosition(setupTypes.size, index)
                val item = when (setupType) {
                    SetupType.Backup -> Item.SetupLink(
                        position = position,
                        iconRes = UIKitIcon.ic_key_28,
                        textRes = Localization.setup_finish_backup,
                        link = "tonkeeper://backups",
                        external = false,
                        blue = false,
                    )
                    SetupType.Telegram -> Item.SetupLink(
                        position = position,
                        iconRes = UIKitIcon.ic_telegram_28,
                        textRes = Localization.setup_finish_telegram,
                        link = config.tonkeeperNewsUrl,
                        external = true,
                        blue = true
                    )
                    SetupType.Biometry -> Item.SetupSwitch(
                        position = position,
                        iconRes = UIKitIcon.ic_faceid_28,
                        textRes = Localization.setup_finish_biometry,
                        enabled = false,
                        isPush = false,
                        walletId = wallet.id
                    )
                    SetupType.Push -> Item.SetupSwitch(
                        position = ListCell.Position.FIRST,
                        iconRes = UIKitIcon.ic_bell_28,
                        textRes = Localization.setup_finish_push,
                        enabled = false,
                        isPush = true,
                        walletId = wallet.id
                    )
                }
                uiItems.add(item)
            }
            return uiItems.toList()
        }

        private fun createSetupTypes(
            setup: Setup,
        ): List<SetupType> {
            val setupTypes = mutableListOf<SetupType>()
            if (!setup.pushEnabled) {
                setupTypes.add(SetupType.Push)
            }
            if (!setup.biometryEnabled && isAvailableBiometric(App.instance)) {
                setupTypes.add(SetupType.Biometry)
            }
            if (setup.showTelegramChannel) {
                setupTypes.add(SetupType.Telegram)
            }
            if (!hasBackup) {
                setupTypes.add(SetupType.Backup)
            }
            return setupTypes.toList()
        }

        fun uiItems(
            wallet: WalletEntity,
            hiddenBalance: Boolean,
            status: Item.Status,
            config: ConfigEntity,
            alerts: List<NotificationEntity>,
            dAppNotifications: DAppNotifications,
            setup: Setup?,
            lastUpdatedFormat: String,
        ): List<Item> {
            val uiItems = mutableListOf<Item>()
            if (alerts.isNotEmpty()) {
                for (alert in alerts) {
                    uiItems.add(Item.Alert(alert))
                    uiItems.add(Item.Space(true))
                }
            }
            uiItems.add(uiItemBalance(hiddenBalance, status, lastUpdatedFormat))
            uiItems.add(uiItemActions(config))
            if (!dAppNotifications.isEmpty) {
                uiItems.add(Item.Push(dAppNotifications.notifications, dAppNotifications.apps))
            }

            setup?.let {
                val setupTypes = createSetupTypes(it)
                if (setupTypes.isNotEmpty()) {
                    uiItems.addAll(uiItemsSetup(wallet.id, config, setupTypes))
                }
            }

            uiItems.addAll(uiItemsTokens(hiddenBalance))
            return uiItems.toList()
        }
    }

    data class DAppNotifications(
        val notifications: List<AppPushEntity> = emptyList(),
        val apps: List<DAppEntity> = emptyList(),
    ): State() {

        val isEmpty: Boolean
            get() = notifications.isEmpty() || apps.isEmpty()
    }

    data class Settings(
        val hiddenBalance: Boolean,
        val config: ConfigEntity,
        val status: Item.Status,
        val telegramChannel: Boolean,
    ): State()
}