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

/** A group of sessions under the same workspace folder */
data class WorkspaceGroup(
    val workspaceName: String,
    val cwd: String,
    val sessions: List<Session>
) {
    val displayName: String
        get() = buildString {
            append(workspaceName)
            if (sessions.any { it.running }) append(" 🟢")
        }
}

data class SessionListUiState(
    val sessions: List<Session> = emptyList(),
    val workspaceGroups: List<WorkspaceGroup> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val showSubagents: Boolean = false // false = hide subagent sessions
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
                    val filtered = if (_uiState.value.showSubagents) {
                        sessions
                    } else {
                        sessions.filter { !it.isSubagent }
                    }
                    val groups = groupByWorkspace(filtered)
                    _uiState.value = _uiState.value.copy(
                        sessions = sessions,
                        workspaceGroups = groups,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
                }
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun toggleSubagents() {
        _uiState.value = _uiState.value.copy(
            showSubagents = !_uiState.value.showSubagents
        )
        applyFilters()
    }

    private fun applyFilters() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        var filtered = _uiState.value.sessions

        // Filter subagents
        if (!_uiState.value.showSubagents) {
            filtered = filtered.filter { !it.isSubagent }
        }

        // Search filter
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.lowercase().contains(query) ||
                it.workspaceName.lowercase().contains(query) ||
                it.model?.lowercase()?.contains(query) == true
            }
        }

        val groups = groupByWorkspace(filtered)
        _uiState.value = _uiState.value.copy(workspaceGroups = groups)
    }

    private fun groupByWorkspace(sessions: List<Session>): List<WorkspaceGroup> {
        return sessions
            .groupBy { it.cwd.ifBlank { "unknown" } }
            .toSortedMap(compareByDescending { dir ->
                // Sort by most recently updated session in group
                sessions.filter { it.cwd == dir }.maxOfOrNull { it.updatedAt } ?: 0L
            })
            .map { (cwd, groupSessions) ->
                WorkspaceGroup(
                    workspaceName = groupSessions.first().workspaceName,
                    cwd = cwd,
                    sessions = groupSessions.sortedByDescending { it.updatedAt }
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
