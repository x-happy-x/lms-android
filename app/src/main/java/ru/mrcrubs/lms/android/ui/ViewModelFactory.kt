package ru.mrcrubs.lms.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Builds screen ViewModels from the app container without a DI framework. */
fun <VM : ViewModel> factory(builder: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
    }
