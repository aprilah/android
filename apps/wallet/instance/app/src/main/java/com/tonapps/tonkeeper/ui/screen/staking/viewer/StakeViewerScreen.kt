package com.tonapps.tonkeeper.ui.screen.staking.viewer

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Adapter
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation

class StakeViewerScreen: BaseListFragment(), BaseFragment.SwipeBack {

    private val args: StakeViewerArgs by lazy { StakeViewerArgs(requireArguments()) }
    private val stakeViewerViewModel: StakeViewerViewModel by viewModel { parametersOf(args.address) }
    private val adapter = Adapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(args.name)
        setAdapter(adapter)
        stakeViewerViewModel.uiItemsFlow.catch {
            navigation?.toast(Localization.unknown_error)
            finish()
        }.onEach(adapter::submitList).launchIn(lifecycleScope)
    }

    companion object {

        fun newInstance(address: String, name: String): StakeViewerScreen {
            val fragment = StakeViewerScreen()
            fragment.setArgs(StakeViewerArgs(address, name))
            return fragment
        }
    }
}