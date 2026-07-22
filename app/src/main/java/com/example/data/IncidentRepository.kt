package com.example.data

import kotlinx.coroutines.flow.Flow

class IncidentRepository(private val incidentDao: IncidentDao) {
    val allIncidents: Flow<List<IncidentReport>> = incidentDao.getAllIncidents()
    val highRiskCount: Flow<Int> = incidentDao.getHighRiskCount()

    suspend fun addIncident(incident: IncidentReport) {
        incidentDao.insertIncident(incident)
    }

    suspend fun reportIncident(id: Int) {
        incidentDao.markAsReported(id)
    }
}
