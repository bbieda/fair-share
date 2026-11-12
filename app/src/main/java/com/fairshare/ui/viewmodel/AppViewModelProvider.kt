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
        initializer { GroupsViewModel(repository(), application(), settings()) }
        initializer { GroupViewModel(createSavedStateHandle(), repository()) }
        initializer { AddExpenseViewModel(createSavedStateHandle(), repository(), application(), settings()) }
        initializer { ProfileViewModel(application(), repository(), settings()) }
        initializer { ScanViewModel(application(), createSavedStateHandle()) }
        initializer { RozliczViewModel(application(), repository(), settings()) }
        initializer { StatystykiViewModel(createSavedStateHandle(), repository(), settings()) }
        initializer { AktywnoscViewModel(repository()) }
    }
}

private fun CreationExtras.application(): Application =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application

private fun CreationExtras.repository(): FairShareRepository = (application() as FairShareApplication).repository

private fun CreationExtras.settings(): com.fairshare.data.AppSettings =
    (application() as FairShareApplication).settings