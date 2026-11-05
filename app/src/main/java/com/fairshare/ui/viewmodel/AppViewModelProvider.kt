package com.fairshare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fairshare.FairShareApplication
import com.fairshare.data.FairShareRepository

const val GROUP_ID_ARG = "groupId"

object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { DashboardViewModel(repository()) }
        initializer { GroupsViewModel(repository()) }
        initializer { GroupViewModel(createSavedStateHandle(), repository()) }
        initializer { AddExpenseViewModel(createSavedStateHandle(), repository()) }
        initializer { ProfileViewModel(repository()) }
        initializer { ScanViewModel(application(), createSavedStateHandle()) }
        initializer { RozliczViewModel(repository()) }
        initializer { StatystykiViewModel(repository()) }
        initializer { AktywnoscViewModel(repository()) }
    }
}

private fun CreationExtras.application(): Application =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application

private fun CreationExtras.repository(): FairShareRepository = (application() as FairShareApplication).repository