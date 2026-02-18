package com.netflow.predict.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netflow.predict.data.model.*
import com.netflow.predict.data.repository.TrafficRepository
import com.netflow.predict.data.repository.VpnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vpnRepo: VpnRepository,
    private val trafficRepo: TrafficRepository
) : ViewModel() {

    val vpnState: StateFlow<VpnState> = vpnRepo.vpnState

    private val _trafficSummary = MutableStateFlow<TrafficSummary?>(null)
    val trafficSummary: StateFlow<TrafficSummary?> = _trafficSummary

    private val _prediction = MutableStateFlow<PredictionResult?>(null)
    val prediction: StateFlow<PredictionResult?> = _prediction

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            delay(1200) // simulate initial load
            trafficRepo.getTrafficSummary().collect { _trafficSummary.value = it }
        }
        viewModelScope.launch {
            delay(1500)
            trafficRepo.getPrediction().collect { _prediction.value = it }
        }
        viewModelScope.launch {
            delay(1200)
            _isLoading.value = false
        }
    }

    fun startVpn() { vpnRepo.startVpn() }
    fun stopVpn()  { vpnRepo.stopVpn() }
}

@HiltViewModel
class LiveTrafficViewModel @Inject constructor(
    private val trafficRepo: TrafficRepository
) : ViewModel() {

    private val _flows       = MutableStateFlow<List<TrafficFlow>>(emptyList())
    val flows: StateFlow<List<TrafficFlow>> = _flows

    private val _isCapturing = MutableStateFlow(true)
    val isCapturing: StateFlow<Boolean> = _isCapturing

    private val _activeFilter = MutableStateFlow<TrafficFilter>(TrafficFilter.ALL)
    val activeFilter: StateFlow<TrafficFilter> = _activeFilter

    init { startCapture() }

    private fun startCapture() {
        viewModelScope.launch {
            trafficRepo.liveTrafficFlow().collect { newBatch ->
                if (_isCapturing.value) {
                    val updated = (newBatch + _flows.value).take(200)
                    _flows.value = when (_activeFilter.value) {
                        TrafficFilter.ALL        -> updated
                        TrafficFilter.APPS       -> updated.filter { it.appPackage != "android" }
                        TrafficFilter.SYSTEM     -> updated.filter { it.appPackage == "android" }
                        TrafficFilter.SUSPICIOUS -> updated.filter { it.riskLevel == RiskLevel.HIGH }
                        TrafficFilter.BLOCKED    -> emptyList() // placeholder
                    }
                }
            }
        }
    }

    fun toggleCapture() { _isCapturing.value = !_isCapturing.value }
    fun setFilter(f: TrafficFilter) { _activeFilter.value = f }
}

enum class TrafficFilter { ALL, APPS, SYSTEM, SUSPICIOUS, BLOCKED }

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val trafficRepo: TrafficRepository
) : ViewModel() {

    private val _apps       = MutableStateFlow<List<AppNetworkInfo>>(emptyList())
    val apps: StateFlow<List<AppNetworkInfo>> = _apps

    private val _isLoading  = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _sortMode   = MutableStateFlow(AppSortMode.MOST_DATA)
    val sortMode: StateFlow<AppSortMode> = _sortMode

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredApps: StateFlow<List<AppNetworkInfo>> = combine(
        _apps, _searchQuery, _sortMode
    ) { apps, query, sort ->
        val filtered = if (query.isBlank()) apps
                       else apps.filter {
                           it.appName.contains(query, ignoreCase = true) ||
                           it.packageName.contains(query, ignoreCase = true)
                       }
        when (sort) {
            AppSortMode.MOST_DATA      -> filtered.sortedByDescending { it.dataSentToday + it.dataReceivedToday }
            AppSortMode.MOST_REQUESTS  -> filtered.sortedByDescending { it.requestCountToday }
            AppSortMode.HIGHEST_RISK   -> filtered.sortedBy { it.riskLevel.ordinal }.reversed()
            AppSortMode.RECENTLY_ACTIVE -> filtered
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            trafficRepo.getApps().collect {
                _apps.value = it
                _isLoading.value = false
            }
        }
    }

    fun setSearch(q: String) { _searchQuery.value = q }
    fun setSort(m: AppSortMode) { _sortMode.value = m }
}

enum class AppSortMode { MOST_DATA, MOST_REQUESTS, HIGHEST_RISK, RECENTLY_ACTIVE }

@HiltViewModel
class PredictionsViewModel @Inject constructor(
    private val trafficRepo: TrafficRepository
) : ViewModel() {

    val prediction: StateFlow<PredictionResult?> =
        trafficRepo.getPrediction().stateIn(viewModelScope, SharingStarted.Lazily, null)

    val alerts: StateFlow<List<NetworkAlert>> =
        trafficRepo.getAlerts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _dismissedIds = MutableStateFlow<Set<String>>(emptySet())

    val visibleAlerts: StateFlow<List<NetworkAlert>> = combine(alerts, _dismissedIds) { list, dismissed ->
        list.filter { it.id !in dismissed }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun dismissAlert(id: String) {
        _dismissedIds.value = _dismissedIds.value + id
    }
}
