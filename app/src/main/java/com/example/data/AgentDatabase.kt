package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "agent_memories")
data class AgentMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val memoryKey: String,
    val memoryValue: String,
    val category: String, // "user_pref", "learned_fact", "system_state"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "workflow_logs")
data class WorkflowLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskName: String,
    val agentName: String, // "ORCHESTRATOR", "PLANNER", "CRITIC", "EXECUTOR", "MEMORY", "WEB", "FILE", "SYSTEM"
    val logMessage: String,
    val isSafe: Boolean = true,
    val status: String = "INFO", // "INFO", "SUCCESS", "WARNING", "CRITICAL"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "mock_files")
data class MockFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val fileContent: String,
    val fileSize: Long,
    val lastModified: Long = System.currentTimeMillis()
)

@Dao
interface AgentDao {
    // --- Memory Access ---
    @Query("SELECT * FROM agent_memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<AgentMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: AgentMemoryEntity)

    @Query("DELETE FROM agent_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Int)

    @Query("DELETE FROM agent_memories")
    suspend fun clearAllMemories()

    // --- Workflow Logs Access ---
    @Query("SELECT * FROM workflow_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<WorkflowLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: WorkflowLogEntity)

    @Query("DELETE FROM workflow_logs")
    suspend fun clearLogs()

    // --- Mock Files Access ---
    @Query("SELECT * FROM mock_files ORDER BY lastModified DESC")
    fun getAllFiles(): Flow<List<MockFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: MockFileEntity)

    @Query("DELETE FROM mock_files WHERE id = :id")
    suspend fun deleteFileById(id: Int)

    @Query("DELETE FROM mock_files WHERE fileName = :name")
    suspend fun deleteFileByName(name: String)

    @Query("DELETE FROM mock_files")
    suspend fun clearAllFiles()
}

@Database(entities = [AgentMemoryEntity::class, WorkflowLogEntity::class, MockFileEntity::class], version = 1, exportSchema = false)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao

    companion object {
        @Volatile
        private var INSTANCE: AgentDatabase? = null

        fun getDatabase(context: android.content.Context): AgentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgentDatabase::class.java,
                    "melynda_os_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
