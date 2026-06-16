package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.MelyndaViewModel
import kotlinx.coroutines.launch
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MelyndaDashboardScreen(
    viewModel: MelyndaViewModel,
    modifier: Modifier = Modifier
) {
    // --- State Collectors ---
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()

    val isWakeWordListening by viewModel.isWakeWordListening.collectAsStateWithLifecycle()
    val isAutonomousLoopActive by viewModel.isAutonomousLoopActive.collectAsStateWithLifecycle()
    val isProcessingTask by viewModel.isProcessingTask.collectAsStateWithLifecycle()

    val currentTask by viewModel.currentTask.collectAsStateWithLifecycle()
    val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()
    val currentSteps by viewModel.currentSteps.collectAsStateWithLifecycle()
    val criticReport by viewModel.criticReport.collectAsStateWithLifecycle()

    val terminalQuery by viewModel.terminalQuery.collectAsStateWithLifecycle()
    val isAudioActive by viewModel.isAudioActive.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.audioAmplitude.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val hostPcStatus by viewModel.hostPcStatus.collectAsStateWithLifecycle()
    val webBrowserState by viewModel.webBrowserState.collectAsStateWithLifecycle()
    val speechText by viewModel.speechText.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Dialog state for viewing files
    var selectedFileToView by remember { mutableStateOf<MockFileEntity?>(null) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace),
        topBar = {
            MelyndaTopAppBar(
                status = hostPcStatus,
                isWakeWordListening = isWakeWordListening,
                isAutonomousLoopActive = isAutonomousLoopActive,
                onToggleWakeWord = { viewModel.toggleWakeWord() },
                onToggleAutonomous = { viewModel.toggleAutonomousLoop() }
            )
        },
        bottomBar = {
            MelyndaBottomNavigation(
                activeTab = activeTab,
                onTabSelected = { viewModel.setTab(it) }
            )
        },
        containerColor = DeepSpace
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // API key status warning banner
            if (!viewModel.isApiKeyConfigured) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AlertOrange.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, AlertOrange.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = AlertOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "PROTOTYPE OFFLINE FALLBACK",
                                color = AlertOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Configure your GEMINI_API_KEY in the Secrets panel to activate full autonomous planning capabilities.",
                                color = TerminalStdout.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // --- Steps Progress panel if executing agents work loop ---
            if (isProcessingTask && currentSteps.isNotEmpty()) {
                AgentExecutionStepsPanel(
                    task = currentTask,
                    steps = currentSteps,
                    activeIndex = currentStepIndex,
                    criticReport = criticReport
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // --- Audio Pipeline Wave visualizer if mic active ---
            AnimatedVisibility(
                visible = isAudioActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SpeechRecordingOverlay(
                    speechText = speechText,
                    amplitude = audioAmplitude
                )
            }

            // --- Active workspace panel depending on tab selection ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Color(0xFF1E2E3D)), RoundedCornerShape(16.dp))
                    .background(SlateOverlay.copy(alpha = 0.6f))
                    .padding(12.dp)
            ) {
                when (activeTab) {
                    0 -> TerminalWorkspace(
                        logs = logs,
                        terminalQuery = terminalQuery,
                        isProcessingTask = isProcessingTask,
                        onQueryChange = { viewModel.setTerminalQuery(it) },
                        onSubmitQuery = {
                            viewModel.executeTask(terminalQuery)
                            viewModel.setTerminalQuery("")
                            focusManager.clearFocus()
                        },
                        onClearLogs = { viewModel.clearLogs() },
                        onVoiceClick = { viewModel.startVoiceInput() }
                    )
                    1 -> WebBrowserWorkspace(webBrowserState)
                    2 -> FileExplorerWorkspace(
                        files = files,
                        onFileClick = { selectedFileToView = it },
                        onResetFiles = { viewModel.clearFiles() }
                    )
                    3 -> LongTermMemoryWorkspace(
                        memories = memories,
                        onResetMemories = { viewModel.clearMemories() }
                    )
                    4 -> HardwareSandboxWorkspace(
                        status = hostPcStatus,
                        logs = logs
                    )
                }
            }
        }
    }

    // Modal dialog to view file contents in Editor
    selectedFileToView?.let { file ->
        MockFileEditorDialog(
            file = file,
            onDismiss = { selectedFileToView = null }
        )
    }
}

// --- Top App Bar Custom implementation ---
@Composable
fun MelyndaTopAppBar(
    status: HostPcStatus,
    isWakeWordListening: Boolean,
    isAutonomousLoopActive: Boolean,
    onToggleWakeWord: () -> Unit,
    onToggleAutonomous: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(DeepSpace)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .drawBehind {
                // Bottom divider line
                drawLine(
                    color = Color(0xFF1E2D3E),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ActiveGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MELYNDA ULTRA OS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Operator System Center",
                    fontSize = 10.sp,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            // Interactive toggles
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF101D2D))
                        .clickable { onToggleWakeWord() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isWakeWordListening) ActiveGreen else Color.Red)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "WAKE WORD: ${if (isWakeWordListening) "ON" else "OFF"}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isWakeWordListening) ActiveGreen else MutedText,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF101D2D))
                        .clickable { onToggleAutonomous() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isAutonomousLoopActive) ActiveGreen else MutedText)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "AUTO LOOP: ${if (isAutonomousLoopActive) "ACTIVE" else "IDLE"}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isAutonomousLoopActive) ActiveGreen else MutedText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HOST: ${status.computerName} (${status.connectionStatus})",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = SoftCyan
            )
            Row {
                Text(
                    text = "CPU: ${status.cpuUsage}% (${status.cpuTemperature}°C)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (status.cpuTemperature > 70) AlertOrange else SoftCyan,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "MEM: ${status.ramUsage}%",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SoftCyan
                )
            }
        }
    }
}

// --- Dynamic Visual Steps Stepper ---
@Composable
fun AgentExecutionStepsPanel(
    task: String,
    steps: List<ExecutionStep>,
    activeIndex: Int,
    criticReport: String
) {
    var expandedCritic by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateOverlay),
        border = BorderStroke(1.dp, Color(0xFF1F2F3B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ORCHESTRATOR EXECUTION workflow",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        task,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TerminalStdout,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (criticReport.isNotEmpty()) {
                    IconButton(
                        onClick = { expandedCritic = !expandedCritic },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (expandedCritic) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle Critic Panel",
                            tint = AlertOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Collapsible Safety Review Audit
            AnimatedVisibility(visible = expandedCritic && criticReport.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AlertOrange.copy(alpha = 0.1f))
                        .border(BorderStroke(0.5.dp, AlertOrange.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Shield Audit",
                            tint = AlertOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "CRITIC AGENT SAFETY SANDBOX ANALYSIS",
                            color = AlertOrange,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        criticReport,
                        color = TerminalStdout.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Step timeline nodes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                steps.forEachIndexed { idx, step ->
                    val isActive = idx == activeIndex
                    val isCompleted = idx < activeIndex
                    val frameColor = when {
                        isActive -> CyberCyan
                        isCompleted -> ActiveGreen
                        else -> Color(0xFF1E2D3E)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(frameColor.copy(alpha = 0.15f))
                                .border(BorderStroke(1.dp, frameColor), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Done",
                                            tint = ActiveGreen,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else if (isActive) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            color = CyberCyan,
                                            strokeWidth = 1.dp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = step.agent.uppercase(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isActive || isCompleted) frameColor else MutedText
                                    )
                                }
                                Text(
                                    text = step.action,
                                    fontSize = 10.sp,
                                    color = if (isActive) TerminalStdout else TerminalStdout.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                        }
                        if (idx < steps.size - 1) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Next Step",
                                tint = Color(0xFF1F2E3E),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Beautiful Custom drawn voice recording overlay ---
@Composable
fun SpeechRecordingOverlay(
    speechText: String,
    amplitude: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_anim")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sine_shift"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1625)),
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CyberCyan)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "AUDIO PIPELINE CONNECTED",
                    color = CyberCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            
            // Customized waves drawing
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp)
            ) {
                val width = size.width
                val height = size.height
                val midY = height / 2f
                val points = 100
                val dx = width / points

                // Multilayer sine wave overlays
                for (waveIndex in 0..1) {
                    val color = if (waveIndex == 0) CyberCyan else SoftCyan.copy(alpha = 0.4f)
                    val strokeW = if (waveIndex == 0) 2.dp.toPx() else 1.dp.toPx()
                    val path = androidx.compose.ui.graphics.Path()

                    for (i in 0..points) {
                        val x = i * dx
                        val freqFactor = if (waveIndex == 0) 3.5f else 5f
                        val theta = (i.toFloat() / points) * (2 * Math.PI) * freqFactor + phaseOffset
                        val ampFactor = amplitude.coerceIn(0.1f, 1f) * 15.dp.toPx()
                        
                        // Attenuation factor at ends to blend wave gracefully
                        val attenuation = sin((i.toFloat() / points) * Math.PI).toFloat()
                        val y = midY + sin(theta).toFloat() * ampFactor * attenuation

                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = strokeW)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = speechText.ifEmpty { "Audio signal processed..." },
                fontSize = 13.sp,
                color = TerminalStdout,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// --- WORKSPACE TABS ---

// Tab 0: Terminal
@Composable
fun TerminalWorkspace(
    logs: List<WorkflowLogEntity>,
    terminalQuery: String,
    isProcessingTask: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    onClearLogs: () -> Unit,
    onVoiceClick: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll terminal when logs update
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SYSTEM RECENT WORK DICTIONARY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = { onClearLogs() },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear logs",
                    tint = MutedText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Monospace log printouts
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF060B13))
                .border(BorderStroke(1.dp, Color(0xFF131D2A)), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (logs.isEmpty()) {
                item {
                    Text(
                        "C:\\melynda> _\n\n[SYSTEM] No active runtime records found. Send a command to initialize orchestrations.",
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                items(logs) { log ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            val tagColor = when (log.status) {
                                "SUCCESS" -> ActiveGreen
                                "WARNING" -> AlertOrange
                                "CRITICAL" -> Color.Red
                                else -> CyberCyan
                            }
                            Text(
                                "[${log.agentName}]",
                                color = tagColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                log.logMessage,
                                color = if (log.status == "CRITICAL" || !log.isSafe) Color.Red else TerminalStdout,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Glowing Audio Core Mic + Command Input Line
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(CyberCyan.copy(alpha = 0.4f), Color.Transparent)
                        )
                    )
                    .border(BorderStroke(1.dp, CyberCyan), CircleShape)
                    .clickable { onVoiceClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Activate mic input",
                    tint = CyberCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text input
            OutlinedTextField(
                value = terminalQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_input"),
                placeholder = {
                    Text(
                        "Send execution instructions...",
                        color = MutedText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = TerminalStdout
                ),
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (terminalQuery.trim().isNotEmpty() && !isProcessingTask) {
                            onSubmitQuery()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color(0xFF1D2C3E),
                    focusedContainerColor = Color(0xFF040A12),
                    unfocusedContainerColor = Color(0xFF040A12)
                ),
                shape = RoundedCornerShape(25.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (terminalQuery.trim().isNotEmpty() && !isProcessingTask) {
                                onSubmitQuery()
                            }
                        },
                        enabled = !isProcessingTask
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Submit instruction",
                            tint = if (isProcessingTask) MutedText else CyberCyan
                        )
                    }
                }
            )
        }
    }
}

// Tab 1: Fake Browser Sandbox
@Composable
fun WebBrowserWorkspace(state: WebBrowserState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = "Web Link", tint = SoftCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    "AUTONOMOUS WEB SEARCH CLIENT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Isolated browser emulator inside safety layer",
                    fontSize = 9.sp,
                    color = MutedText
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Browser Address Frame
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF060B13))
                .border(BorderStroke(1.dp, Color(0xFF1E2D3E)), RoundedCornerShape(8.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF152233))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text("GET", color = ActiveGreen, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                state.url,
                color = TerminalStdout.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = CyberCyan,
                    strokeWidth = 1.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "reloading",
                    tint = MutedText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = CyberCyan,
                trackColor = Color.Transparent
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Crawled Frame Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A101C))
                .border(BorderStroke(1.dp, Color(0xFF131E2C)), RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                state.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                state.htmlContent,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = TerminalStdout.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// Tab 2: Code/File Explorer
@Composable
fun FileExplorerWorkspace(
    files: List<MockFileEntity>,
    onFileClick: (MockFileEntity) -> Unit,
    onResetFiles: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "SANDBOX FILESYSTEM VIEWER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Melynda virtual disk in /sandbox/",
                    fontSize = 9.sp,
                    color = MutedText
                )
            }
            Button(
                onClick = onResetFiles,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF1E2F3E)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("RESET SYSTEM", fontSize = 8.sp, color = AlertOrange, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (files.isEmpty()) {
                item {
                    Text(
                        "No files found in sandbox directory. Instruct Melynda to write a file.",
                        color = MutedText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                items(files) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0B1423))
                            .border(BorderStroke(1.dp, Color(0xFF1D2F44)), RoundedCornerShape(10.dp))
                            .clickable { onFileClick(file) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info, // custom doc representing
                            contentDescription = "Document",
                            tint = SoftCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                file.fileName,
                                color = TerminalStdout,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${file.filePath}  •  ${file.fileSize} bytes",
                                color = MutedText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Open Editor",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Tab 3: SQLite Memories Grid
@Composable
fun LongTermMemoryWorkspace(
    memories: List<AgentMemoryEntity>,
    onResetMemories: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "LONG-TERM SQLite MEMORY NODE SYSTEM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "SQLite persistence backend via Room Database",
                    fontSize = 9.sp,
                    color = MutedText
                )
            }
            Button(
                onClick = onResetMemories,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF1E2F3E)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("WIPE SQLite", fontSize = 8.sp, color = Color.Red, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grid of persisted learnings
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (memories.isEmpty()) {
                item {
                    Text(
                        "Long-term memory is empty. Initialize execution states to record memories.",
                        color = MutedText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                items(memories) { node ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF050B13))
                            .border(BorderStroke(0.5.dp, Color(0xFF1D2C3E)), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "KEY: ${node.memoryKey}",
                                color = SoftCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1A1A1A))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    node.category.uppercase(),
                                    color = AlertOrange,
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            node.memoryValue,
                            color = TerminalStdout,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// Tab 4: Sandbox & Hardware Dials
@Composable
fun HardwareSandboxWorkspace(
    status: HostPcStatus,
    logs: List<WorkflowLogEntity>
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "HARDWARE DIAGNOSTICS & SANDBOX SAFETY LOGS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "Live connection statistics into isolated virtual system hooks",
            fontSize = 8.sp,
            color = MutedText
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Simulated visual telemetry dials row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CircularLoadingDial(
                label = "CPU CORE LOAD",
                value = status.cpuUsage,
                suffix = "%",
                color = if (status.cpuUsage > 80) Color.Red else CyberCyan
            )
            CircularLoadingDial(
                label = "CORE TEMPERATURE",
                value = status.cpuTemperature,
                suffix = "°C",
                color = if (status.cpuTemperature > 65) AlertOrange else SoftCyan
            )
            CircularLoadingDial(
                label = "RAM USED",
                value = status.ramUsage,
                suffix = "%",
                color = SoftCyan
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Safety Auditing Records console
        Text(
            "SAFETY CRITIC AUDIT TRACE LOGS",
            fontSize = 9.sp,
            color = AlertOrange,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))

        val sandboxRecords = logs.filter { !it.isSafe || it.agentName == "CRITIC" }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0E0A0E))
                .border(BorderStroke(0.5.dp, AlertOrange.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (sandboxRecords.isEmpty()) {
                item {
                    Text(
                        "[SANDBOX] Passive monitoring... No security policies violated. High-risk command lines are safe.",
                        color = MutedText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                items(sandboxRecords) { audit ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "[${audit.taskName}] ${audit.logMessage}",
                            color = if (!audit.isSafe) Color.Red else AlertOrange,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// Custom circular dial visualizer component
@Composable
fun CircularLoadingDial(
    label: String,
    value: Int,
    suffix: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(85.dp)
    ) {
        val sweepAngle = (value.toFloat() / 100f) * 360f

        Box(
            modifier = Modifier
                .size(60.dp)
                .drawBehind {
                    // Draw outer background circle
                    drawCircle(
                        color = Color(0xFF131F2E),
                        style = Stroke(width = 4.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(54.dp)) {
                // Draw color arc
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
            Text(
                text = "$value$suffix",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 7.sp,
            color = MutedText,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- Dialog: Mock Custom Visual IDE Code Editor ---
@Composable
fun MockFileEditorDialog(
    file: MockFileEntity,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF090E17)),
            border = BorderStroke(1.dp, CyberCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ActiveGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MELYNDA CODE EDITOR",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Close description",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Path bar
                Text(
                    text = "Location: ${file.filePath}  (${file.fileSize} bytes)",
                    color = MutedText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF131C2A))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Text Editor Window Box
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF04070D))
                        .border(BorderStroke(1.dp, Color(0xFF162537)), RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    val lines = file.fileContent.split("\n")
                    lines.forEachIndexed { num, content ->
                        Row(modifier = Modifier.padding(vertical = 1.dp)) {
                            Text(
                                String.format("%02d │", num + 1),
                                color = MutedText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(28.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                content,
                                color = TerminalStdout,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF101C2D)),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
                    ) {
                        Text("CLOSE EDITOR", color = CyberCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Bottom Navigation Implementation ---
@Composable
fun MelyndaBottomNavigation(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = DeepSpace,
        tonalElevation = 8.dp,
        modifier = Modifier.drawBehind {
            // Draw top boundary divider
            drawLine(
                color = Color(0xFF1E2D3E),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
        }.navigationBarsPadding() // Notch & gesture navigation bars safe areas handled beautifully!
    ) {
        NavigationBarItem(
            selected = activeTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Terminal Workspace") },
            label = { Text("TERM", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DeepSpace,
                selectedTextColor = CyberCyan,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = CyberCyan
            )
        )
        NavigationBarItem(
            selected = activeTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Web Scraper browser") },
            label = { Text("BROWSER", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DeepSpace,
                selectedTextColor = CyberCyan,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = CyberCyan
            )
        )
        NavigationBarItem(
            selected = activeTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(imageVector = Icons.Default.Folder, contentDescription = "Sandbox files explorer") },
            label = { Text("FILES", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DeepSpace,
                selectedTextColor = CyberCyan,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = CyberCyan
            )
        )
        NavigationBarItem(
            selected = activeTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Memory node SQLite registry") },
            label = { Text("MEMORY", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DeepSpace,
                selectedTextColor = CyberCyan,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = CyberCyan
            )
        )
        NavigationBarItem(
            selected = activeTab == 4,
            onClick = { onTabSelected(4) },
            icon = { Icon(imageVector = Icons.Default.Info, contentDescription = "Hardware stats sandbox panel") }, // Computes telemetry
            label = { Text("HARDWARE", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DeepSpace,
                selectedTextColor = CyberCyan,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = CyberCyan
            )
        )
    }
}
