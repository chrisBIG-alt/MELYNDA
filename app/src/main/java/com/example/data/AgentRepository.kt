package com.example.data

import kotlinx.coroutines.flow.Flow

class AgentRepository(private val agentDao: AgentDao) {

    val allMemories: Flow<List<AgentMemoryEntity>> = agentDao.getAllMemories()
    val recentLogs: Flow<List<WorkflowLogEntity>> = agentDao.getRecentLogs()
    val allFiles: Flow<List<MockFileEntity>> = agentDao.getAllFiles()

    suspend fun insertMemory(memory: AgentMemoryEntity) {
        agentDao.insertMemory(memory)
    }

    suspend fun deleteMemory(id: Int) {
        agentDao.deleteMemoryById(id)
    }

    suspend fun clearMemories() {
        agentDao.clearAllMemories()
    }

    suspend fun insertLog(log: WorkflowLogEntity) {
        agentDao.insertLog(log)
    }

    suspend fun clearLogs() {
        agentDao.clearLogs()
    }

    suspend fun insertFile(file: MockFileEntity) {
        agentDao.insertFile(file)
    }

    suspend fun deleteFile(id: Int) {
        agentDao.deleteFileById(id)
    }

    suspend fun deleteFileByName(name: String) {
        agentDao.deleteFileByName(name)
    }

    suspend fun clearFiles() {
        agentDao.clearAllFiles()
    }
}
