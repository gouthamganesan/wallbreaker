package dev.goutham.wallbreaker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.goutham.wallbreaker.CredentialStore
import dev.goutham.wallbreaker.ShareRepository
import dev.goutham.wallbreaker.db.ShareEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app

    val entries: StateFlow<List<ShareEntry>> =
        ShareRepository.observeEntries(ctx)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Int> =
        ShareRepository.observeTotal(ctx)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val unlocks: StateFlow<Int> =
        ShareRepository.observeUnlocks(ctx)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _configured = MutableStateFlow(true)
    val configured: StateFlow<Boolean> = _configured

    init { refreshConfigured() }

    /** Re-check whether an Instapaper account is set up (call on return to Home). */
    fun refreshConfigured() = viewModelScope.launch(Dispatchers.IO) {
        _configured.value = CredentialStore.load(ctx) != null
    }

    fun retry(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        ShareRepository.retry(ctx, id)
    }

    fun delete(entry: ShareEntry) = viewModelScope.launch(Dispatchers.IO) {
        ShareRepository.delete(ctx, entry)
    }
}
