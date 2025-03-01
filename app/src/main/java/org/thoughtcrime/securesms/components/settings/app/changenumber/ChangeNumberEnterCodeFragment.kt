package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.changeNumberSuccess
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.getCaptchaArguments
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.getViewModel
import org.thoughtcrime.securesms.registration.fragments.BaseEnterCodeFragment

class ChangeNumberEnterCodeFragment : BaseEnterCodeFragment<ChangeNumberViewModel>(R.layout.fragment_change_number_enter_code) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.title = viewModel.number.fullFormattedNumber
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    view.findViewById<View>(R.id.verify_header).setOnClickListener(null)
  }

  override fun getViewModel(): ChangeNumberViewModel {
    return getViewModel(this)
  }

  override fun handleSuccessfulVerify() {
    displaySuccess { changeNumberSuccess() }
  }

  override fun navigateToCaptcha() {
    findNavController().navigate(R.id.action_changeNumberEnterCodeFragment_to_captchaFragment, getCaptchaArguments())
  }

  override fun navigateToRegistrationLock(timeRemaining: Long) {
    findNavController().navigate(ChangeNumberEnterCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberRegistrationLock(timeRemaining))
  }

  override fun navigateToKbsAccountLocked() {
    findNavController().navigate(ChangeNumberEnterCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberAccountLocked())
  }
}
