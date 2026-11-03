package com.fairshare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fairshare.FairShareApplication
import com.fairshare.data.FairShareRepository

/** Navigation argument with a group id, read by the ViewModels through SavedStateHandle. */
const val GROUP_ID_ARG = "groupId"

/** Creates every screen's ViewModel with the app-wide repository (simple manual DI). */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { DashboardViewModel(repository()) }
        initializer { GroupsViewModel(repository()) }
        initializer { GroupViewModel(createSavedStateHandle(), repository()) }
        initializer { AddExpenseViewModel(createSavedStateHandle(), repository()) }
        initializer { ProfileViewModel(repository()) }
        initializer { ScanViewModel(application(), createSavedStateHandle()) }
    }
}

private fun CreationExtras.application(): Application =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application

private fun CreationExtras.repository(): FairShareRepository = (application() as FairShareApplication).repository
