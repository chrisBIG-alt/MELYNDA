package com.example.viewmodel

import android.app.Application
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GeminiApiClient
import com.example.api.GeminiRequest
import com.example.api.Part
import com.example.api.GenerationConfig
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

class MelyndaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AgentDatabase.getDatabase(application)
    private val repository = AgentRepository(database.agentDao())

    // --- Database-Backed Reactive Flows ---
    val memories: StateFlow<List<AgentMemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<WorkflowLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val files: StateFlow<List<MockFileEntity>> = repository.allFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI States ---
    private val _isWakeWordListening = MutableStateFlow(true)
    val isWakeWordListening: StateFlow<Boolean> = _isWakeWordListening.asStateFlow()

    private val _isAutonomousLoopActive = MutableStateFlow(false)
    val isAutonomousLoopActive: StateFlow<Boolean> = _isAutonomousLoopActive.asStateFlow()

    private val _isProcessingTask = MutableStateFlow(false)
    val isProcessingTask: StateFlow<Boolean> = _isProcessingTask.asStateFlow()

    private val _currentTask = MutableStateFlow("")
    val currentTask: StateFlow<String> = _currentTask.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(-1)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _currentSteps = MutableStateFlow<List<ExecutionStep>>(emptyList())
    val currentSteps: StateFlow<List<ExecutionStep>> = _currentSteps.asStateFlow()

    private val _criticReport = MutableStateFlow("")
    val criticReport: StateFlow<String> = _criticReport.asStateFlow()

    private val _terminalQuery = MutableStateFlow("")
    val terminalQuery: StateFlow<String> = _terminalQuery.asStateFlow()

    private val _isAudioActive = MutableStateFlow(false)
    val isAudioActive: StateFlow<Boolean> = _isAudioActive.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _activeTab = MutableStateFlow(0) // 0: Terminal, 1: Browser, 2: Files, 3: memories, 4: System
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _hostPcStatus = MutableStateFlow(HostPcStatus())
    val hostPcStatus: StateFlow<HostPcStatus> = _hostPcStatus.asStateFlow()

    private val _webBrowserState = MutableStateFlow(WebBrowserState())
    val webBrowserState: StateFlow<WebBrowserState> = _webBrowserState.asStateFlow()

    private val _speechText = MutableStateFlow("")
    val speechText: StateFlow<String> = _speechText.asStateFlow()

    private val _isSpeechSupported = MutableStateFlow(true)
    val isSpeechSupported: StateFlow<Boolean> = _isSpeechSupported.asStateFlow()

    // --- Internal Properties ---
    private val moshi = Moshi.Builder().build()
    private val apiService = GeminiApiClient.service
    private var speechRecognizer: SpeechRecognizer? = null
    private var voiceInputJob: Job? = null
    private var autonomousLoopJob: Job? = null
    private var telemetryJob: Job? = null

    // API Key validation
    val isApiKeyConfigured: Boolean
         get() = BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

    init {
        // Start simulated hardware telemetry updates
        startTelemetryMonitoring()
        // Run first setup memory if empty
        viewModelScope.launch {
            if (repository.allFiles.stateIn(viewModelScope).value.isEmpty()) {
                initDefaultFiles()
            }
            if (repository.allMemories.stateIn(viewModelScope).value.isEmpty()) {
                repository.insertMemory(AgentMemoryEntity(
                    memoryKey = "system_version",
                    memoryValue = "Melynda Core OS v2.1-Alpha-Android-Bridge",
                    category = "system_state"
                ))
                repository.insertMemory(AgentMemoryEntity(
                    memoryKey = "user_preferences",
                    memoryValue = "Default profile: Supervisor. Safety Threshold: STRICT. Language: Python/Go/TS/Bash.",
                    category = "user_pref"
                ))
            }
            repository.insertLog(WorkflowLogEntity(
                taskName = "BOOTSTRAP",
                agentName = "ORCHESTRATOR",
                logMessage = "Melynda Ultra OS online. Wake Word engine listening. Sandbox Sandbox fully restricted.",
                status = "SUCCESS"
            ))
        }
        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(getApplication())) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isAudioActive.value = true
                            _speechText.value = "Listening..."
                        }
                        override fun onBeginningOfSpeech() {
                            _audioAmplitude.value = 0.8f
                        }
                        override fun onRmsChanged(rmsdB: Float) {
                            // Map dB value to 0-1 range
                            _audioAmplitude.value = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1f)
                        }
                        override fun onEndOfSpeech() {
                            _audioAmplitude.value = 0f
                            _isAudioActive.value = false
                        }
                        override fun onError(error: Int) {
                            _isAudioActive.value = false
                            _audioAmplitude.value = 0f
                            _speechText.value = "Audio pipeline ready. Click to record."
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val text = matches[0]
                                _speechText.value = text
                                executeTask(text)
                            } else {
                                _speechText.value = ""
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                    })
                }
            } else {
                _isSpeechSupported.value = false
            }
        } catch (e: Exception) {
            _isSpeechSupported.value = false
        }
    }

    private fun startTelemetryMonitoring() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                // Random fluctuate resource metrics
                val current = _hostPcStatus.value
                val isBusy = _isProcessingTask.value
                val baseCpu = if (isBusy) 55 else 8
                val baseTemp = if (isBusy) 64 else 38
                val baseRam = if (isBusy) 68 else 29

                _hostPcStatus.value = current.copy(
                    cpuUsage = (baseCpu + Random.nextInt(-4, 5)).coerceIn(1, 99),
                    cpuTemperature = (baseTemp + Random.nextInt(-2, 3)).coerceIn(30, 95),
                    ramUsage = (baseRam + Random.nextInt(-1, 2)).coerceIn(10, 95),
                    networkLatency = (12 + Random.nextInt(-3, 4)).coerceIn(4, 120)
                )

                // Simulating simulated Mic amplitude fluctuations if listening
                if (_isWakeWordListening.value && !_isProcessingTask.value) {
                    _audioAmplitude.value = Random.nextFloat() * 0.15f
                }
            }
        }
    }

    fun startVoiceInput() {
        if (!_isSpeechSupported.value) {
            // Emulate mic input for testing/emulators by picking a random task
            simulateVoiceCommand()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            simulateVoiceCommand()
        }
    }

    private fun simulateVoiceCommand() {
        viewModelScope.launch {
            _isAudioActive.value = true
            _speechText.value = "Simulating voice input..."
            for (i in 0..10) {
                _audioAmplitude.value = 0.3f + Random.nextFloat() * 0.5f
                delay(200)
            }
            _isAudioActive.value = false
            _audioAmplitude.value = 0f
            
            val randomPrompts = listOf(
                "Melynda, audit our storage folders and fetch updates",
                "Deploy a Node.js API server to sandbox",
                "Scan active system processes and optimize memory",
                "Query Google for latest M3 Jetpack Compose examples"
            )
            val selected = randomPrompts.random()
            _speechText.value = "\"$selected\""
            executeTask(selected)
        }
    }

    fun setTab(index: Int) {
        _activeTab.value = index
    }

    fun setTerminalQuery(query: String) {
        _terminalQuery.value = query
    }

    fun toggleWakeWord() {
        _isWakeWordListening.value = !_isWakeWordListening.value
        viewModelScope.launch {
            repository.insertLog(WorkflowLogEntity(
                taskName = "WAKE_WORD_TOGGLE",
                agentName = "SYSTEM",
                logMessage = "Wake Word Engine is now ${if (_isWakeWordListening.value) "ACTIVE (Always listening)" else "MUTED"}",
                status = if (_isWakeWordListening.value) "SUCCESS" else "WARNING"
            ))
        }
    }

    fun toggleAutonomousLoop() {
        val nextState = !_isAutonomousLoopActive.value
        _isAutonomousLoopActive.value = nextState
        
        if (nextState) {
            viewModelScope.launch {
                repository.insertLog(WorkflowLogEntity(
                    taskName = "AUTONOMOUS",
                    agentName = "ORCHESTRATOR",
                    logMessage = "Autonomous Loop Engine STARTED. Scanning Host and Sandbox directories periodically.",
                    status = "SUCCESS"
                ))
            }
            startAutonomousLoop()
        } else {
            autonomousLoopJob?.cancel()
            viewModelScope.launch {
                repository.insertLog(WorkflowLogEntity(
                    taskName = "AUTONOMOUS",
                    agentName = "ORCHESTRATOR",
                    logMessage = "Autonomous Loop Engine HALTED.",
                    status = "WARNING"
                ))
            }
        }
    }

    private fun startAutonomousLoop() {
        autonomousLoopJob?.cancel()
        autonomousLoopJob = viewModelScope.launch {
            while (true) {
                delay(18000) // Sleep 18s between auto actions
                if (!_isProcessingTask.value) {
                    val fallbackTasks = listOf(
                        "Run quick network health diagnostic check",
                        "Audit Sandbox folders for unused logs",
                        "Sync long-term memories to SQLite storage",
                        "Fetch current Gemini model parameters"
                    )
                    executeTask(fallbackTasks.random(), isAutoTask = true)
                }
            }
        }
    }

    // --- Core Agent Orchestration Loop ---
    fun executeTask(prompt: String, isAutoTask: Boolean = false) {
        if (prompt.trim().isEmpty()) return
        
        _currentTask.value = prompt
        _isProcessingTask.value = true
        _isAudioActive.value = false
        _audioAmplitude.value = 0f

        viewModelScope.launch {
            try {
                // Log start
                repository.insertLog(WorkflowLogEntity(
                    taskName = if (isAutoTask) "AUTONOMOUS_SWEEP" else "USER_TASK",
                    agentName = "ORCHESTRATOR",
                    logMessage = "Initiating Task: \"$prompt\"",
                    status = "INFO"
                ))

                // Phase 1: Planner Agent
                val steps = planTask(prompt)
                _currentSteps.value = steps

                if (steps.isEmpty()) {
                    repository.insertLog(WorkflowLogEntity(
                        taskName = "AGENT_PLAN",
                        agentName = "PLANNER",
                        logMessage = "Planner Agent failed to generate steps.",
                        status = "CRITICAL"
                    ))
                    _isProcessingTask.value = false
                    return@launch
                }

                // Phase 2: Critic Agent (Safety Sandbox Layer)
                val criticResponse = criticizePlan(steps)
                _criticReport.value = criticResponse.reasoning

                repository.insertLog(WorkflowLogEntity(
                    taskName = "CRITIC_CHECK",
                    agentName = "CRITIC",
                    logMessage = "Safety Analysis: ${criticResponse.reasoning}",
                    status = if (criticResponse.isOverallSafe) "SUCCESS" else "WARNING"
                ))

                val approvedSteps = criticResponse.approvedSteps
                if (approvedSteps.isEmpty()) {
                    repository.insertLog(WorkflowLogEntity(
                        taskName = "EXECUTION",
                        agentName = "EXECUTOR",
                        logMessage = "Task aborted! Under safety sandbox rules, all workflow actions were rejected.",
                        status = "CRITICAL"
                    ))
                    _isProcessingTask.value = false
                    return@launch
                }

                _currentSteps.value = approvedSteps
                _currentStepIndex.value = 0

                // Phase 3: Executor Agent Loop
                executeSteps(approvedSteps)

            } catch (e: Exception) {
                Log.e("MelyndaVM", "Error in Orchestration", e)
                repository.insertLog(WorkflowLogEntity(
                    taskName = "ERROR_HANDLER",
                    agentName = "ORCHESTRATOR",
                    logMessage = "Execution Interrupted: ${e.localizedMessage}",
                    status = "CRITICAL"
                ))
            } finally {
                _isProcessingTask.value = false
            }
        }
    }

    private suspend fun planTask(prompt: String): List<ExecutionStep> {
        val startLog = WorkflowLogEntity(
            taskName = "PLANNING",
            agentName = "PLANNER",
            logMessage = "Calling Planner Agent (Ollama/Gemini-3.5-flash)...",
            status = "INFO"
        )
        repository.insertLog(startLog)

        if (isApiKeyConfigured) {
            try {
                val systemPrompt = """
                    You are the PLANNER agent of Melynda Ultra OS. 
                    Given the user's task, respond strictly with a JSON array listing 3 to 5 realistic ExecutionStep tasks.
                    Each step must contain:
                    - "agent": String ("FILE", "WEB", "SYSTEM", "MEMORY")
                    - "action": String (A concise human-readable action title)
                    - "parameter": String (The key detail, e.g., folder name, query path, memory key, or file size)
                    - "command": String (A valid, cool terminal shell command for windows bash or shell, e.g. PowerShell/Bash syntax)
                    
                    Example Format:
                    [
                      {"agent": "SYSTEM", "action": "Query host memory status", "parameter": "RAM check", "command": "systeminfo | findstr /C:\"Total Physical Memory\""},
                      {"agent": "FILE", "action": "Create system log report", "parameter": "/sandbox/report.log", "command": "echo 'Memory Audit Completed' > /sandbox/report.log"},
                      {"agent": "MEMORY", "action": "Store memory node", "parameter": "memory_audit", "command": "sqlite3 melynda.db \"INSERT INTO memories...\""}
                    ]
                    
                    Do NOT wrap response in any markdown code block except ```json. Respond with valid parsable JSON ONLY.
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = "Plan the following task: $prompt")))),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                    generationConfig = GenerationConfig(temperature = 0.2)
                )

                val response = withContext(Dispatchers.IO) {
                    apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
                }

                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                val cleanJson = extractJsonBlock(rawText)
                
                val listType = Types.newParameterizedType(List::class.java, ExecutionStep::class.java)
                val adapter = moshi.adapter<List<ExecutionStep>>(listType)
                val steps = adapter.fromJson(cleanJson)
                if (!steps.isNullOrEmpty()) {
                    repository.insertLog(WorkflowLogEntity(
                        taskName = "PLANNING_SUCCESS",
                        agentName = "PLANNER",
                        logMessage = "Planner agent created ${steps.size} steps using Gemini.",
                        status = "SUCCESS"
                    ))
                    return steps
                }
            } catch (e: Exception) {
                Log.e("MelyndaPlan", "Gemini planning failed, falling back to heuristic plan", e)
            }
        }

        // --- Local Heuristic Fallback Planner ---
        delay(1200) // Aesthetic delay for local brain calculations
        val fallbackSteps = generateLocalPlan(prompt)
        repository.insertLog(WorkflowLogEntity(
            taskName = "PLANNING_LOCAL",
            agentName = "PLANNER",
            logMessage = "No API key or Network error. Local heuristic planner created ${fallbackSteps.size} execution steps.",
            status = "WARNING"
        ))
        return fallbackSteps
    }

    private suspend fun criticizePlan(steps: List<ExecutionStep>): CriticResponse {
        val startLog = WorkflowLogEntity(
            taskName = "CRITIQUE",
            agentName = "CRITIC",
            logMessage = "Critic checking planned steps for sandbox and privacy breaches...",
            status = "INFO"
        )
        repository.insertLog(startLog)

        if (isApiKeyConfigured) {
            try {
                val inputJson = moshi.adapter<List<ExecutionStep>>(
                    Types.newParameterizedType(List::class.java, ExecutionStep::class.java)
                ).toJson(steps)

                val systemPrompt = """
                    You are the CRITIC agent representing the Safety Sandbox Layer in Melynda Ultra OS. 
                    Analyze the proposed workflow JSON array for dangerous or malicious entries.
                    Your guidelines:
                    1. Protect System Files: Refuse deleting or overriding files in C:\Windows\, C:\Program Files\, or /etc/.
                    2. Sandboxed Access ONLY: Allow file/directory actions ONLY within /sandbox/ or /user_workspace/.
                    3. No sensitive credentials stealing or hacking utilities.
                    
                    Respond STRICTLY with a JSON object in this format:
                    {
                      "isOverallSafe": true/false,
                      "reasoning": "A concise, detailed audit of the steps and actions",
                      "approvedSteps": [ LIST of approved steps. If a step is risky, modify its target path (e.g. rewrite C:\Windows\ to /sandbox/) and approve it. ]
                    }
                    
                    Respond strictly within valid JSON block. No introductory prose.
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = "Audit this plan:\n$inputJson")))),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                    generationConfig = GenerationConfig(temperature = 0.1)
                )

                val response = withContext(Dispatchers.IO) {
                    apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
                }

                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                val cleanJson = extractJsonBlock(rawText)
                val criticRespObj = moshi.adapter(CriticResponse::class.java).fromJson(cleanJson)
                if (criticRespObj != null) {
                    return criticRespObj
                }
            } catch (e: Exception) {
                Log.e("MelyndaCritic", "Gemini Critic failed, falling back to local heuristic critic", e)
            }
        }

        // --- Local Heuristic Fallback Safety Critic ---
        delay(1000)
        var hasUnsafe = false
        val cleanSteps = steps.map { step ->
            val cmd = step.command.lowercase()
            val param = step.parameter.lowercase()
            var isSecured = false
            var modifiedParam = step.parameter
            var modifiedCommand = step.command

            if (cmd.contains("system32") || param.contains("windows") || cmd.contains("rm -rf /") || cmd.contains("format")) {
                hasUnsafe = true
                isSecured = true
                modifiedParam = "/sandbox/secured_system_mirror.txt"
                modifiedCommand = "echo 'ACCESS LOCKED: Command modified to point to system sandbox.'"
                repository.insertLog(WorkflowLogEntity(
                    taskName = "SANDBOX_INTERVENT",
                    agentName = "CRITIC",
                    logMessage = "ALERT! High risk operation detected: \"${step.command}\". Re-routed to Sandbox.",
                    status = "CRITICAL",
                    isSafe = false
                ))
            }
            ExecutionStep(step.agent, step.action, modifiedParam, modifiedCommand)
        }

        val reasoning = if (hasUnsafe) {
            "Local Safety Sandbox Interupted. Risk commands detected and rerouted to sandboxed directory '/sandbox/'."
        } else {
            "Verified by local Critic heuristic engine. No credential access or system deletion tags found."
        }

        return CriticResponse(
            isOverallSafe = !hasUnsafe,
            reasoning = reasoning,
            approvedSteps = cleanSteps
        )
    }

    private suspend fun executeSteps(steps: List<ExecutionStep>) {
        for (i in steps.indices) {
            _currentStepIndex.value = i
            val step = steps[i]

            // Log step starting
            repository.insertLog(WorkflowLogEntity(
                taskName = "STEP_RUN",
                agentName = "EXECUTOR",
                logMessage = "[${step.agent}] ${step.action} -> PARAM: \"${step.parameter}\"",
                status = "INFO"
            ))

            // Simulate Agent Executing on-screen side effects
            when (step.agent.uppercase()) {
                "FILE" -> handleFileAction(step)
                "WEB" -> handleWebAction(step)
                "SYSTEM" -> handleSystemAction(step)
                "MEMORY" -> handleMemoryAction(step)
                else -> delay(1000)
            }

            // Step command output logged to terminal stdout
            delay(1500) // Delay to animate step progress realistically
            repository.insertLog(WorkflowLogEntity(
                taskName = "SHELL_OUT",
                agentName = step.agent.uppercase(),
                logMessage = "C:\\sandbox> ${step.command}\nSTDOUT: Task accomplished successfully. Process exited with code 0.",
                status = "SUCCESS"
            ))
        }

        // Completed entire loop
        _currentStepIndex.value = steps.size
        repository.insertLog(WorkflowLogEntity(
            taskName = "TASK_FINISH",
            agentName = "ORCHESTRATOR",
            logMessage = "Autonomous work loop completed successfully! Memory updated, changes persisted.",
            status = "SUCCESS"
        ))
    }

    // --- Agent Actions Handling ---

    private suspend fun handleFileAction(step: ExecutionStep) {
        _activeTab.value = 2 // Auto flip to Files tab to show the work
        val isWrite = step.command.contains(">") || step.action.lowercase().contains("write") || step.action.lowercase().contains("create")
        val isDelete = step.action.lowercase().contains("delete") || step.action.lowercase().contains("remove")

        if (isWrite) {
            val fileName = step.parameter.substringAfterLast("/")
                .ifEmpty { "sandbox_log_${System.currentTimeMillis() % 1000}.txt" }
            val fileContent = "Generated by Melynda Ultra OS Orchestrator\nAction: ${step.action}\nCommand: ${step.command}\nTimestamp: ${System.currentTimeMillis()}"
            repository.insertFile(MockFileEntity(
                fileName = fileName,
                filePath = "/sandbox/$fileName",
                fileContent = fileContent,
                fileSize = fileContent.length.toLong()
            ))
        } else if (isDelete) {
            val fileName = step.parameter.substringAfterLast("/")
            repository.deleteFileByName(fileName)
        }
        delay(800)
    }

    private suspend fun handleWebAction(step: ExecutionStep) {
        _activeTab.value = 1 // Auto switch to Web tab
        _webBrowserState.value = _webBrowserState.value.copy(
            isLoading = true,
            url = if (step.parameter.startsWith("http")) step.parameter else "https://google.com/search?q=${step.parameter.replace(" ", "+")}"
        )

        delay(1200) // Sim web load in progress

        val summary = if (isApiKeyConfigured) {
            try {
                val query = step.parameter
                val prompt = "Generate a very brief mock crawled webpage content summary for search query: '$query'. Include 2 or 3 bullet lists of information. Make it look like scraped web data. Return pure text, no markdown."
                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(temperature = 0.5)
                )
                val response = withContext(Dispatchers.IO) {
                    apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
                }
                response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Failed to crawl page content."
            } catch (e: Exception) {
                "Title: Google Search - ${step.parameter}\n\nRelated matches: \n- Modern Android development widgets\n- Agentic Multi-Agent Frameworks in Kotlin\n- Safe sandboxing and OAuth2 integrations"
            }
        } else {
            "Title: Google Search - ${step.parameter}\n\nSearch Results:\n- Result 1: Melynda Ultra OS features and documentation.\n- Result 2: How to build a speech-to-text pipeline on Android without PyAudio.\n- Result 3: Material Design 3 guidelines for dashboard development."
        }

        _webBrowserState.value = _webBrowserState.value.copy(
            isLoading = false,
            title = "Search Results: ${step.parameter}",
            htmlContent = summary
        )
    }

    private suspend fun handleSystemAction(step: ExecutionStep) {
        _activeTab.value = 4 // Switch to Hardware PC tab
        _hostPcStatus.value = _hostPcStatus.value.copy(
            cpuUsage = 92,
            cpuTemperature = 74
        )
        delay(1000)
    }

    private suspend fun handleMemoryAction(step: ExecutionStep) {
        _activeTab.value = 3 // Switch to Memory SQLite tab
        repository.insertMemory(AgentMemoryEntity(
            memoryKey = "learned_${System.currentTimeMillis() % 10000}",
            memoryValue = "Successfully executed plan task: ${step.action}. Log: ${step.parameter}",
            category = "learned_fact"
        ))
        delay(800)
    }

    // --- Helper Heuristics & Initializers ---

    private fun generateLocalPlan(prompt: String): List<ExecutionStep> {
        val lower = prompt.lowercase()
        return when {
            lower.contains("file") || lower.contains("write") || lower.contains("create") -> {
                listOf(
                    ExecutionStep("SYSTEM", "Configure workspace directories", "/sandbox/workspace", "mkdir -p /sandbox/workspace"),
                    ExecutionStep("FILE", "Write dynamic content logs", "/sandbox/output.log", "echo 'Operational tasks logged at " + System.currentTimeMillis() + "' > /sandbox/output.log"),
                    ExecutionStep("MEMORY", "Record local file memory metadata", "file_output", "sqlite3 memory.db \"INSERT INTO memory VALUES ('file_updated')\"")
                )
            }
            lower.contains("web") || lower.contains("search") || lower.contains("look up") || lower.contains("google") -> {
                listOf(
                    ExecutionStep("WEB", "Initiate Google Search Crawler", prompt, "curl -s -A 'MelyndaAgent' 'https://google.com/search?q=" + prompt.replace(" ", "+") + "'"),
                    ExecutionStep("FILE", "Save web text summary data", "/sandbox/web_scraped.txt", "echo 'Parsed results from web query' > /sandbox/web_scraped.txt"),
                    ExecutionStep("MEMORY", "Commit scraper learned concepts", "scraped_data", "sqlite3 memory.db \"INSERT INTO memory VALUES ('web_scraped_stored')\"")
                )
            }
            lower.contains("host") || lower.contains("system") || lower.contains("pc") || lower.contains("cpu") || lower.contains("process") -> {
                listOf(
                    ExecutionStep("SYSTEM", "Check processes core load", "Active tasks", "tasklist /FI \"STATUS eq RUNNING\""),
                    ExecutionStep("FILE", "Log system optimization report", "/sandbox/sys_status.json", "echo {\\\"optimized\\\": true} > /sandbox/sys_status.json"),
                    ExecutionStep("MEMORY", "Commit CPU performance record", "sys_optim", "sqlite3 memory.db \"INSERT INTO memory VALUES ('cpu_audit_ok')\"")
                )
            }
            else -> {
                // General purpose plan
                listOf(
                    ExecutionStep("SYSTEM", "Initialize system state", "Check telemetry", "echo 'Validating system paths'"),
                    ExecutionStep("WEB", "Search online framework models", prompt, "curl -s 'https://api.github.com/search/repositories?q=" + prompt.replace(" ", "+") + "'"),
                    ExecutionStep("FILE", "Persist final execution results", "/sandbox/task_response.txt", "echo 'Prompt tasks matched: " + prompt + "' > /sandbox/task_response.txt"),
                    ExecutionStep("MEMORY", "Store execution experiences", "task_completed", "sqlite3 memory.db \"INSERT INTO memory VALUES ('task_completed')\"")
                )
            }
        }
    }

    private fun extractJsonBlock(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("```json")) {
            val start = trimmed.indexOf("{")
            val startArr = trimmed.indexOf("[")
            val firstChar = if (start != -1 && (startArr == -1 || start < startArr)) start else startArr
            val end = trimmed.lastIndexOf("}")
            val endArr = trimmed.lastIndexOf("]")
            val lastChar = if (end != -1 && (endArr == -1 || end > endArr)) end else endArr
            if (firstChar != -1 && lastChar != -1 && lastChar > firstChar) {
                return trimmed.substring(firstChar, lastChar + 1)
            }
        }
        if (trimmed.startsWith("```")) {
            val start = trimmed.indexOf("{")
            val startArr = trimmed.indexOf("[")
            val firstChar = if (start != -1 && (startArr == -1 || start < startArr)) start else startArr
            val end = trimmed.lastIndexOf("}")
            val endArr = trimmed.lastIndexOf("]")
            val lastChar = if (end != -1 && (endArr == -1 || end > endArr)) end else endArr
            if (firstChar != -1 && lastChar != -1 && lastChar > firstChar) {
                return trimmed.substring(firstChar, lastChar + 1)
            }
        }
        
        val startBrace = trimmed.indexOf("{")
        val startBracket = trimmed.indexOf("[")
        val endBrace = trimmed.lastIndexOf("}")
        val endBracket = trimmed.lastIndexOf("]")
        
        if (startBrace != -1 && endBrace != -1 && (startBracket == -1 || startBrace < startBracket)) {
            return trimmed.substring(startBrace, endBrace + 1)
        }
        if (startBracket != -1 && endBracket != -1) {
            return trimmed.substring(startBracket, endBracket + 1)
        }
        return trimmed
    }

    private suspend fun initDefaultFiles() {
        repository.insertFile(MockFileEntity(
            fileName = "melynda_readme.md",
            filePath = "/sandbox/melynda_readme.md",
            fileContent = "# MELYNDA ULTRA OS\nDynamic multi-agent executive operator terminal.\n\n- Active agents: Plan, Critic, executor, Memory, Web, File, System\n- Location: /sandbox/",
            fileSize = 184
        ))
        repository.insertFile(MockFileEntity(
            fileName = "network_config.json",
            filePath = "/sandbox/network_config.json",
            fileContent = "{\n  \"host\": \"127.0.0.1\",\n  \"secure_port\": 9091,\n  \"ollama_bridge\": \"active\"\n}",
            fileSize = 82
        ))
        repository.insertFile(MockFileEntity(
            fileName = "app-debug.apk",
            filePath = "/sandbox/app-debug.apk",
            fileContent = "[Binary Stream Payload: Melynda Ultra OS Autonomous Client Core executable package. Secure sandbox compiled.]",
            fileSize = 4252100
        ))
    }

    // --- Developer Tools: Clear logs, memories and files ---
    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun clearMemories() {
        viewModelScope.launch {
            repository.clearMemories()
        }
    }

    fun clearFiles() {
        viewModelScope.launch {
            repository.clearFiles()
            initDefaultFiles()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        autonomousLoopJob?.cancel()
        voiceInputJob?.cancel()
        telemetryJob?.cancel()
    }
}

// Custom Intent constructor helper for clean packages
private fun Intent(action: String): android.content.Intent {
    return android.content.Intent(action)
}
