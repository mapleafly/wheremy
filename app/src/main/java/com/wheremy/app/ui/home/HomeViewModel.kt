package com.wheremy.app.ui.home

import androidx.lifecycle.ViewModel
import com.wheremy.app.di.Greeter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
@Inject
constructor(greeter: Greeter) : ViewModel() {
    val greeting: String = greeter.greet()
}
