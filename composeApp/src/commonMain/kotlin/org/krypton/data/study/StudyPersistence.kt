package org.krypton.data.study

/**
 * Platform-agnostic interface for persisting study data.
 * 
 * Each vault stores its study data in a JSON file at `.krypton/study-data.json`
 * relative to the vault root.
 */
interface StudyPersistence {
    /**
     * Loads study data for a vault.
     * 
     * @param vaultId Platform-specific vault identifier (file path or URI)
     * @return Study data if found, null if file doesn't exist or on error
     */
    suspend fun loadStudyData(vaultId: String): StudyData?
    
    /**
     * Saves study data for a vault.
     * 
     * @param vaultId Platform-specific vault identifier (file path or URI)
     * @param data Study data to save
     * @return true if successful, false on error
     */
    suspend fun saveStudyData(vaultId: String, data: StudyData): Boolean
}

