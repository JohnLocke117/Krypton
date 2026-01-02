package org.krypton.data.study

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.krypton.core.domain.study.StudyGoal
import org.krypton.core.domain.study.StudyGoalId
import org.krypton.core.domain.study.StudyItem
import org.krypton.core.domain.study.StudyItemId
import org.krypton.core.domain.study.StudyItemRepository
import org.krypton.core.domain.study.StudyGoalRepository
import org.krypton.util.AppLogger

/**
 * Implementation of StudyItemRepository using StudyPersistence.
 */
class StudyItemRepositoryImpl(
    private val persistence: StudyPersistence,
    private val goalRepository: StudyGoalRepository,
) : StudyItemRepository {
    
    // Share the cache with goal repository
    private val dataCache = MutableStateFlow<Map<String, StudyData>>(emptyMap())
    
    override fun observeItemsForGoal(goalId: StudyGoalId): Flow<List<StudyItem>> {
        return dataCache.map { cache ->
            cache.values
                .flatMap { it.items }
                .filter { it.goalId == goalId }
        }
    }
    
    override suspend fun getItem(id: StudyItemId): StudyItem? {
        return dataCache.value.values
            .flatMap { it.items }
            .firstOrNull { it.id == id }
    }
    
    override suspend fun getItemsDueNow(goalId: StudyGoalId, nowEpochMillis: Long): List<StudyItem> {
        val goal = goalRepository.getGoal(goalId) ?: return emptyList()
        val data = loadData(goal.vaultId)
        return data.items.filter { 
            it.goalId == goalId && it.nextDueAtEpochMillis <= nowEpochMillis
        }
    }
    
    override suspend fun upsertItems(items: List<StudyItem>) {
        if (items.isEmpty()) return
        
        try {
            // Group by vault (via goal)
            val itemsByVault = items.groupBy { item ->
                goalRepository.getGoal(item.goalId)?.vaultId
            }.filterKeys { it != null }
            
            // Update each vault's data
            itemsByVault.forEach { (vaultId, vaultItems) ->
                vaultId?.let {
                    val currentData = loadData(it)
                    val existingItemIds = vaultItems.map { item -> item.id }.toSet()
                    val updatedItems = currentData.items.filterNot { it.id in existingItemIds } + vaultItems
                    val updatedData = currentData.copy(items = updatedItems)
                    
                    if (persistence.saveStudyData(it, updatedData)) {
                        updateCache(it, updatedData)
                    } else {
                        AppLogger.e("StudyItemRepository", "Failed to save items for vault: $it")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("StudyItemRepository", "Error upserting items", e)
        }
    }
    
    override suspend fun updateItem(item: StudyItem) {
        try {
            val goal = goalRepository.getGoal(item.goalId) ?: return
            val currentData = loadData(goal.vaultId)
            val updatedItems = currentData.items.filterNot { it.id == item.id } + item
            val updatedData = currentData.copy(items = updatedItems)
            
            if (persistence.saveStudyData(goal.vaultId, updatedData)) {
                updateCache(goal.vaultId, updatedData)
            } else {
                AppLogger.e("StudyItemRepository", "Failed to update item: ${item.id}")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyItemRepository", "Error updating item: ${item.id}", e)
        }
    }
    
    private suspend fun loadData(vaultId: String): StudyData {
        // Check cache first
        dataCache.value[vaultId]?.let { return it }
        
        // Load from persistence
        val data = persistence.loadStudyData(vaultId) ?: StudyData()
        updateCache(vaultId, data)
        return data
    }
    
    private fun updateCache(vaultId: String, data: StudyData) {
        dataCache.value = dataCache.value + (vaultId to data)
    }
}

