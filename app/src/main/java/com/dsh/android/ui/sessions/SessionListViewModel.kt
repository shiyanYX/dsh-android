package com.dsh.android.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.android.domain.model.Session
import com.dsh.android.domain.repository.DshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionListUiState(
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val repository: DshRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getSessions()
            result.fold(
                onSuccess = { sessions ->
                    _uiState.value = _uiState.value.copy(sessions = sessions, isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
                }
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            loadSessions()
        } else {
            searchSessions(query)
        }
    }

    private fun searchSessions(query: String) {
        viewModelScope.launch {
            val result = repository.searchSessions(query)
            result.fold(
                onSuccess = { sessions ->
                    _uiState.value = _uiState.value.copy(sessions = sessions)
                },
                onFailure = { /* Ignore search errors */ }
            )
        }
    }

    fun createSession() {
        viewModelScope.launch {
            val result = repository.createSession("New Session")
            result.fold(
                onSuccess = { loadSessions() },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            )
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val result = repository.deleteSession(sessionId)
            result.fold(
                onSuccess = { loadSessions() },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            )
        }
    }
}
