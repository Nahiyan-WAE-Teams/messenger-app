package com.nahiyan.miramessenger

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LovelyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LovelyRepository(LovelyDatabase.get(application).lovelyDao())
    val profiles = repository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unseen = repository.unseen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val likes = repository.likes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val matches = repository.matches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val messages = repository.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val preferences = repository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferencesEntity())
    val ownProfile = repository.ownProfile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OwnProfileEntity())
    val relationships = repository.relationships.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init { viewModelScope.launch { repository.initialize() } }
    fun act(profile: ProfileEntity, action: String) { viewModelScope.launch { repository.act(profile, action) } }
    fun save(profile: ProfileEntity) { viewModelScope.launch { repository.saveProfile(profile) } }
    fun delete(profile: ProfileEntity) { viewModelScope.launch { repository.deleteProfile(profile.id) } }
    fun send(profile: ProfileEntity, message: String) { viewModelScope.launch { repository.send(profile, message) } }
    fun savePreferences(value: PreferencesEntity) { viewModelScope.launch { repository.savePreferences(value) } }
    fun saveOwn(value: OwnProfileEntity) { viewModelScope.launch { repository.saveOwn(value) } }
    fun reset() { viewModelScope.launch { repository.reset() } }
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LovelyViewModel::class.java)) return LovelyViewModel(app) as T
            throw IllegalArgumentException("Unknown ViewModel")
        }
    }
}
