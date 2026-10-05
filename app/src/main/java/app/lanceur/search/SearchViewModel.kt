package app.lanceur.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(private val engine: SearchEngine) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val refreshTick = MutableStateFlow(0)

    /** `flatMapLatest` annule la recherche précédente : ses résultats lents ne s'affichent jamais. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<SearchResult>> = combine(_query, refreshTick) { q, _ -> q }
        .flatMapLatest { engine.search(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    /** Relance la requête en cours (par exemple après l'octroi des autorisations). */
    fun refresh() {
        refreshTick.value++
    }
}
