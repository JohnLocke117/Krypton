package org.krypton.core.domain.study

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing study goals.
 */
interface StudyGoalRepository {
    /**
     * Observes all goals for a vault.
     * 
     * @param vaultId ID of the vault
     * @return Flow of goals list
     */
    fun observeGoals(vaultId: String): Flow<List<StudyGoal>>
    
    /**
     * Gets a specific goal by ID.
     * 
     * @param id Goal ID
     * @return Goal if found, null otherwise
     */
    suspend fun getGoal(id: StudyGoalId): StudyGoal?
    
    /**
     * Creates or updates a goal.
     * 
     * @param goal Goal to upsert
     */
    suspend fun upsert(goal: StudyGoal)
    
    /**
     * Deletes a goal.
     * 
     * @param id Goal ID to delete
     */
    suspend fun deleteGoal(id: StudyGoalId)
}

/**
 * Repository for managing study items.
 */
interface StudyItemRepository {
    /**
     * Observes all items for a goal.
     * 
     * @param goalId Goal ID
     * @return Flow of items list
     */
    fun observeItemsForGoal(goalId: StudyGoalId): Flow<List<StudyItem>>
    
    /**
     * Gets a specific item by ID.
     * 
     * @param id Item ID
     * @return Item if found, null otherwise
     */
    suspend fun getItem(id: StudyItemId): StudyItem?
    
    /**
     * Gets items that are due for review now.
     * 
     * @param goalId Goal ID
     * @param nowEpochMillis Current time in milliseconds
     * @return List of due items
     */
    suspend fun getItemsDueNow(goalId: StudyGoalId, nowEpochMillis: Long): List<StudyItem>
    
    /**
     * Creates or updates multiple items.
     * 
     * @param items Items to upsert
     */
    suspend fun upsertItems(items: List<StudyItem>)
    
    /**
     * Updates a single item.
     * 
     * @param item Item to update
     */
    suspend fun updateItem(item: StudyItem)
}

