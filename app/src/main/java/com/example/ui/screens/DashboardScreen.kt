package com.example.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.IncidentReport
import com.example.ui.viewmodels.DashboardViewModel
import com.example.ui.viewmodels.DashboardViewModelFactory
import com.example.util.ReportExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSimulateCall: () -> Unit,
    onReportIncident: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSimulateScamCall: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(LocalContext.current))
) {
    val incidents by viewModel.incidents.collectAsState()
    val highRiskCount by viewModel.highRiskCount.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionsGranted by remember { mutableStateOf(false) }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(context, "Call Screening role granted.", Toast.LENGTH_SHORT).show()
            permissionsGranted = true
        } else {
            Toast.makeText(context, "Call Screening role required.", Toast.LENGTH_LONG).show()
        }
    }

    // Pre-Android-10 needs WRITE_EXTERNAL_STORAGE to save the PDF to Downloads.
    val exportPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            exportReport(context, incidents, scope)
        } else {
            Toast.makeText(context, "Storage permission required to export report.", Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    roleLauncher.launch(intent)
                } else {
                    permissionsGranted = true
                    Toast.makeText(context, "Permissions granted. Background monitoring active.", Toast.LENGTH_SHORT).show()
                }
            } else {
                permissionsGranted = true
                Toast.makeText(context, "Permissions granted. Background monitoring active.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permissions required for real-time monitoring.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CALL_LOG
        )
        val hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        var hasRole = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            hasRole = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        }
        
        permissionsGranted = hasPermissions && hasRole
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TruDial", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, contentDescription = "Call History")
                    }
                    IconButton(onClick = {
                        // API 29+ writes via MediaStore (no permission). Older versions
                        // need WRITE_EXTERNAL_STORAGE granted at runtime first.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            exportReport(context, incidents, scope)
                        } else {
                            exportPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Report")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedContent(targetState = permissionsGranted, label = "fab_anim") { granted ->
                if (granted) {
                    ExtendedFloatingActionButton(
                        onClick = { Toast.makeText(context, "Monitoring is active.", Toast.LENGTH_SHORT).show() },
                        icon = { Icon(Icons.Default.Security, contentDescription = "Active") },
                        text = { Text("Protection Active") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(24.dp)
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.ANSWER_PHONE_CALLS,
                                    Manifest.permission.READ_CALL_LOG
                                )
                            )
                        },
                        icon = { Icon(Icons.Default.Call, contentDescription = "Enable") },
                        text = { Text("Enable Tracking") },
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SummaryCard(highRiskCount)

            // Demo controls — try the detection pipeline without a live call.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSimulateCall,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulate Call")
                }
                Button(
                    onClick = onSimulateScamCall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scam Demo")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Recent Incidents",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (incidents.isEmpty()) {
                    item {
                        EmptyStateCard()
                    }
                }
                itemsIndexed(incidents) { index, incident ->
                    AnimatedIncidentCard(
                        incident = incident,
                        index = index,
                        onReport = { onReportIncident(incident.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryCard(highRiskCount: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val isHighRisk = highRiskCount > 0
    val backgroundColor = if (isHighRisk) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isHighRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val iconColor = if (isHighRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .shadow(if (isHighRisk) 16.dp else 8.dp, RoundedCornerShape(24.dp), spotColor = iconColor),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Security",
                        modifier = Modifier.size(40.dp),
                        tint = iconColor
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        "Security Status", 
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (highRiskCount > 0) "$highRiskCount high-risk calls blocked" else "System is secure & actively monitoring.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No incidents recorded",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Your call logs are clean.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AnimatedIncidentCard(incident: IncidentReport, index: Int, onReport: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * 100L) // Staggered entry
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { 50 }) + fadeIn(tween(400)),
        exit = fadeOut(tween(200))
    ) {
        IncidentCard(incident, onReport)
    }
}

@Composable
fun IncidentCard(incident: IncidentReport, onReport: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = incident.callerId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                RiskBadge(incident.riskLevel)
            }
            Spacer(modifier = Modifier.height(12.dp))
            val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(incident.timestamp))
            Text(
                dateStr, 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                incident.transcriptSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (incident.isReported) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reported to Cybercell",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (incident.riskLevel != "Low") {
                Button(
                    onClick = onReport, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report to Cybercell", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RiskBadge(riskLevel: String) {
    val (bgColor, textColor) = when (riskLevel) {
        "Low" -> Color(0xFFFEF3C7) to Color(0xFFD97706)
        "Medium" -> Color(0xFFFFEDD5) to Color(0xFFEA580C)
        "High", "Extreme" -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
        else -> Color(0xFFF3F4F6) to Color(0xFF4B5563)
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = riskLevel.uppercase(),
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

fun exportReport(
    context: Context,
    incidents: List<IncidentReport>,
    scope: CoroutineScope
) {
    scope.launch {
        val location = withContext(Dispatchers.IO) {
            ReportExporter.exportToDownloads(context, incidents)
        }
        val message = if (location != null) "Report saved to $location" else "Export failed"
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
