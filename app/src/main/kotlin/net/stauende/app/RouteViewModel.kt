package net.stauende.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * ViewModel for [MainActivity]. Bridges the UI layer with [RouteCalculator]
 * and [TrafficAnalyzer] while keeping all network work off the main thread.
 *
 * Jam history is scoped per route (see [routeId]) and persisted via
 * [TrafficHistoryStore] so it actually accumulates across app launches
 * instead of resetting every time the process restarts.
 */
class RouteViewModel(
    application: Application,
    private val calculator: RouteCalculator,
    private val analyzer: TrafficAnalyzer = TrafficAnalyzer()
) : AndroidViewModel(application) {

    private val historyStore = TrafficHistoryStore(application)

    init {
        analyzer.restore(historyStore.load())
    }

    private val _uiState = MutableLiveData<RouteUiState>(RouteUiState.Idle)
    val uiState: LiveData<RouteUiState> = _uiState

    private val _departureWindows = MutableLiveData<List<DepartureWindow>>(emptyList())
    val departureWindows: LiveData<List<DepartureWindow>> = _departureWindows

    private val _jamWindows = MutableLiveData<List<JamWindow>>(emptyList())
    val jamWindows: LiveData<List<JamWindow>> = _jamWindows

    /**
     * Calculates routes for the given [origin] and [destination] and updates
     * the UI state accordingly.
     */
    fun calculateRoutes(origin: String, destination: String) {
        if (origin.isBlank() || destination.isBlank()) {
            _uiState.value = RouteUiState.Error("Please enter both origin and destination.")
            return
        }

        _uiState.value = RouteUiState.Loading

        viewModelScope.launch {
            try {
                val routes = withContext(Dispatchers.IO) {
                    calculator.calculateRoutes(origin.trim(), destination.trim())
                }

                if (routes.isEmpty()) {
                    _uiState.value = RouteUiState.Error("No routes found.")
                } else {
                    _uiState.value = RouteUiState.RoutesLoaded(routes)
                    // Also kick off departure-window suggestions in the background.
                    loadDepartureWindows(origin.trim(), destination.trim())
                }
            } catch (e: RouteCalculationException) {
                _uiState.value = RouteUiState.Error(e.message ?: "Route calculation failed.")
            } catch (e: Exception) {
                _uiState.value = RouteUiState.Error("Network error: ${e.message}")
            }
        }
    }

    /**
     * Loads departure-window suggestions and updates historical jam analysis
     * for this specific origin/destination pair.
     */
    private fun loadDepartureWindows(origin: String, destination: String) {
        viewModelScope.launch {
            try {
                val routeId = routeId(origin, destination)
                val windows = withContext(Dispatchers.IO) {
                    calculator.suggestDepartureWindows(origin, destination)
                }
                _departureWindows.value = windows

                // Feed today's windows into the analyzer for historical tracking.
                val today = LocalDateTime.now(ZoneOffset.UTC).dayOfWeek
                windows.forEach { window ->
                    // Use the LOW-traffic duration as the baseline free-flow estimate.
                    val baseline = windows.minOfOrNull { it.estimatedDurationSeconds }
                        ?: window.estimatedDurationSeconds
                    if (baseline > 0) {
                        analyzer.record(routeId, today, window, baseline)
                    }
                }

                withContext(Dispatchers.IO) { historyStore.save(analyzer.snapshot()) }

                _jamWindows.value = analyzer.commonJamWindows(routeId, today, topN = 5)
            } catch (_: Exception) {
                // Departure window loading is best-effort; don't surface errors to the user.
            }
        }
    }

    private fun routeId(origin: String, destination: String) = "$origin -> $destination"
}

/** Sealed hierarchy representing the possible UI states for route loading. */
sealed class RouteUiState {
    data object Idle : RouteUiState()
    data object Loading : RouteUiState()
    data class RoutesLoaded(val routes: List<RouteOption>) : RouteUiState()
    data class Error(val message: String) : RouteUiState()
}
