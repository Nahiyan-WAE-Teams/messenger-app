package com.nahiyan.miramessenger

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LovelyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LovelyRepository(LovelyDatabase.get(application).lovelyDao())
    val discover = repository.discover.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val matches = repository.matches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { repository.initialize() } }
    fun like(profile: ProfileEntity) { viewModelScope.launch { repository.like(profile) } }
    fun pass(profile: ProfileEntity) { viewModelScope.launch { repository.pass(profile) } }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LovelyViewModel::class.java)) return LovelyViewModel(app) as T
            throw IllegalArgumentException("Unknown ViewModel")
        }
    }
}
