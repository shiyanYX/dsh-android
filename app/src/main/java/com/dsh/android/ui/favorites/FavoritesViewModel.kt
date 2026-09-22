package com.dsh.android.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.android.data.local.DshPreferences
import com.dsh.android.domain.model.Favorite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val favorites: List<Favorite> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val preferences: DshPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            preferences.favorites.collect { favorites ->
                _uiState.value = FavoritesUiState(
                    favorites = favorites.sortedByDescending { it.favoritedAt }
                )
            }
        }
    }

    fun removeFavorite(sessionId: String, serverAddress: String) {
        viewModelScope.launch {
            preferences.removeFavorite(sessionId, serverAddress)
        }
    }
}
