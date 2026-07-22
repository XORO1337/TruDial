package com.example.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.IncidentReport
import com.example.data.IncidentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: IncidentRepository) : ViewModel() {
    val incidents: StateFlow<List<IncidentReport>> = repository.allIncidents
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val highRiskCount: StateFlow<Int> = repository.highRiskCount
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
        
    fun addIncident(incident: IncidentReport) {
        viewModelScope.launch {
            repository.addIncident(incident)
        }
    }
    
    fun reportIncident(id: Int) {
        viewModelScope.launch {
            repository.reportIncident(id)
        }
    }
}

class DashboardViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(
                IncidentRepository(AppDatabase.getDatabase(context).incidentDao())
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
