package com.autokeeper.carmaintenancetracker

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.autokeeper.carmaintenancetracker.data.AutoKeeperRepository
import com.autokeeper.carmaintenancetracker.data.LocationClient
import com.autokeeper.carmaintenancetracker.data.ServiceRecord
import com.autokeeper.carmaintenancetracker.data.UserProfile
import com.autokeeper.carmaintenancetracker.data.Vehicle
import com.autokeeper.carmaintenancetracker.notifications.ReminderScheduler
import com.autokeeper.carmaintenancetracker.util.filterServiceRecords
import com.autokeeper.carmaintenancetracker.util.lifetimeExpense
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

enum class AppTab { Vehicles, Services, Profile }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent { AutoKeeperApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoKeeperApp() {
    val context = LocalContext.current
    val repository = remember { AutoKeeperRepository() }
    val locationClient = remember { LocationClient(context) }
    val reminderScheduler = remember { ReminderScheduler(context) }

    var userId by remember { mutableStateOf(repository.currentUserId()) }
    var tab by remember { mutableStateOf(AppTab.Vehicles) }
    var message by remember { mutableStateOf("") }

    if (userId == null) {
        AuthScreen(
            onAuthSuccess = {
                userId = repository.currentUserId()
                tab = AppTab.Vehicles
            },
            repository = repository,
            onMessage = { message = it }
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AutoKeeper") }) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AppTab.entries.forEach { item ->
                    FilterChip(
                        selected = item == tab,
                        onClick = { tab = item },
                        label = { Text(item.name) }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(12.dp)) {
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            when (tab) {
                AppTab.Vehicles -> VehiclesScreen(repository, userId!!, onMessage = { message = it })
                AppTab.Services -> ServicesScreen(
                    repository = repository,
                    userId = userId!!,
                    locationClient = locationClient,
                    reminderScheduler = reminderScheduler,
                    onMessage = { message = it }
                )

                AppTab.Profile -> ProfileScreen(
                    repository = repository,
                    userId = userId!!,
                    onSignedOut = {
                        repository.signOut()
                        userId = null
                        message = "Signed out"
                    },
                    onMessage = { message = it }
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(
    repository: AutoKeeperRepository,
    onAuthSuccess: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to AutoKeeper", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("Display Name (for sign up)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    runCatching { repository.signIn(email.trim(), password) }
                        .onSuccess {
                            onMessage("Signed in")
                            onAuthSuccess()
                        }
                        .onFailure { onMessage(it.message ?: "Sign in failed") }
                }
            }) { Text("Sign In") }
            Button(onClick = {
                scope.launch {
                    runCatching { repository.signUp(email.trim(), password, name.trim()) }
                        .onSuccess {
                            onMessage("Account created")
                            onAuthSuccess()
                        }
                        .onFailure { onMessage(it.message ?: "Sign up failed") }
                }
            }) { Text("Sign Up") }
        }
    }
}

@Composable
private fun VehiclesScreen(repository: AutoKeeperRepository, userId: String, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val vehicles = remember { mutableStateListOf<Vehicle>() }
    var nickname by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        repository.observeVehicles(userId).collect {
            vehicles.clear()
            vehicles.addAll(it.sortedBy { v -> v.nickname })
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Vehicles", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(make, { make = it }, label = { Text("Make") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(year, { year = it }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(odometer, { odometer = it }, label = { Text("Current Mileage") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                scope.launch {
                    val vehicle = Vehicle(
                        userId = userId,
                        nickname = nickname,
                        make = make,
                        model = model,
                        year = year.toIntOrNull() ?: 0,
                        odometer = odometer.toIntOrNull() ?: 0
                    )
                    runCatching { repository.addVehicle(vehicle) }
                        .onSuccess {
                            onMessage("Vehicle added")
                            nickname = ""
                            make = ""
                            model = ""
                            year = ""
                            odometer = ""
                        }
                        .onFailure { onMessage(it.message ?: "Failed to add vehicle") }
                }
            }) { Text("Add Vehicle") }
        }

        items(vehicles) { vehicle ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(vehicle.nickname.ifBlank { "Unnamed Vehicle" }, style = MaterialTheme.typography.titleMedium)
                    Text("${vehicle.year} ${vehicle.make} ${vehicle.model}")
                    Text("Mileage: ${vehicle.odometer}")
                }
            }
        }
    }
}

@Composable
private fun ServicesScreen(
    repository: AutoKeeperRepository,
    userId: String,
    locationClient: LocationClient,
    reminderScheduler: ReminderScheduler,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records = remember { mutableStateListOf<ServiceRecord>() }
    val vehicles = remember { mutableStateListOf<Vehicle>() }

    var selectedVehicleId by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }

    var serviceType by remember { mutableStateOf("Oil Change") }
    var serviceDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var mileage by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var reminderDays by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationGranted = it
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    LaunchedEffect(userId) {
        repository.observeVehicles(userId).collect {
            vehicles.clear()
            vehicles.addAll(it)
            if (selectedVehicleId.isBlank() && it.isNotEmpty()) selectedVehicleId = it.first().id
        }
    }

    LaunchedEffect(userId, selectedVehicleId) {
        repository.observeServiceRecords(userId, selectedVehicleId.ifBlank { null }).collect {
            records.clear()
            records.addAll(it)
        }
    }

    val filtered = filterServiceRecords(records, search, filterType)
    val totalCost = lifetimeExpense(records)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Maintenance Records", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vehicles.forEach { vehicle ->
                    FilterChip(
                        selected = selectedVehicleId == vehicle.id,
                        onClick = { selectedVehicleId = vehicle.id },
                        label = { Text(vehicle.nickname.ifBlank { vehicle.model }) }
                    )
                }
            }
            OutlinedTextField(search, { search = it }, label = { Text("Search notes/type/shop") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Oil Change", "Tire Rotation", "Brake Service").forEach { type ->
                    FilterChip(selected = filterType == type, onClick = { filterType = type }, label = { Text(type) })
                }
            }
            Text("Lifetime maintenance expense: $${"%.2f".format(totalCost)}")
        }

        item {
            Text("Add Service", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(serviceType, { serviceType = it }, label = { Text("Service Type") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(serviceDate, { serviceDate = it }, label = { Text("Service Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(mileage, { mileage = it }, label = { Text("Mileage") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cost, { cost = it }, label = { Text("Cost") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(shopName, { shopName = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(reminderDays, { reminderDays = it }, label = { Text("Reminder in days (optional)") }, modifier = Modifier.fillMaxWidth())
            CameraCaptureSection(onCaptured = {
                receiptUri = it
                onMessage("Receipt captured")
            })
            Text(
                if (receiptUri == null) "No receipt selected" else "Receipt ready: ${receiptUri.toString().takeLast(24)}",
                style = MaterialTheme.typography.bodySmall
            )

            if (!locationGranted) {
                TextButton(onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Grant location permission")
                }
            }
            if (!notificationsGranted) {
                TextButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Grant notification permission")
                }
            }

            Button(onClick = {
                scope.launch {
                    if (selectedVehicleId.isBlank()) {
                        onMessage("Add a vehicle first")
                        return@launch
                    }

                    val parsedDate = runCatching { LocalDate.parse(serviceDate) }.getOrElse {
                        onMessage("Invalid date format")
                        return@launch
                    }
                    val serviceMillis = parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val reminderMillis = reminderDays.toIntOrNull()?.takeIf { it > 0 }?.let {
                        serviceMillis + it * 24L * 60L * 60L * 1000L
                    }

                    val location = if (locationGranted) runCatching { locationClient.lastLocation() }.getOrNull() else null
                    val uploadedUrl = receiptUri?.let { runCatching { repository.uploadReceiptImage(it, userId) }.getOrNull() }.orEmpty()

                    val record = ServiceRecord(
                        userId = userId,
                        vehicleId = selectedVehicleId,
                        serviceType = serviceType,
                        serviceDateMillis = serviceMillis,
                        mileage = mileage.toIntOrNull() ?: 0,
                        cost = cost.toDoubleOrNull() ?: 0.0,
                        notes = notes,
                        receiptUrl = uploadedUrl,
                        shopName = shopName,
                        latitude = location?.first,
                        longitude = location?.second,
                        nextReminderMillis = reminderMillis
                    )

                    runCatching { repository.addServiceRecord(record) }
                        .onSuccess { recordId ->
                            if (reminderMillis != null) {
                                reminderScheduler.schedule(
                                    recordId = recordId,
                                    triggerAtMillis = reminderMillis,
                                    title = "${record.serviceType} due",
                                    message = "${record.serviceType} reminder for ${shopName.ifBlank { "your vehicle" }}"
                                )
                            }
                            onMessage("Service record saved")
                            receiptUri = null
                            notes = ""
                            mileage = ""
                            cost = ""
                            reminderDays = ""
                        }
                        .onFailure { onMessage(it.message ?: "Failed to save record") }
                }
            }) {
                Text("Save Service Record")
            }
        }

        items(filtered) { record ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.serviceType, style = MaterialTheme.typography.titleMedium)
                    Text("Mileage: ${record.mileage}  Cost: $${"%.2f".format(record.cost)}")
                    Text("Shop: ${record.shopName.ifBlank { "N/A" }}")
                    Text("Notes: ${record.notes.ifBlank { "None" }}")
                    Text(
                        if (record.latitude != null && record.longitude != null) {
                            "Location: ${record.latitude}, ${record.longitude}"
                        } else "Location unavailable"
                    )
                    if (record.receiptUrl.isNotBlank()) Text("Receipt URL saved to Firestore")
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureSection(onCaptured: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!hasCameraPermission) {
            Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera permission")
            }
            return@Column
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        Button(onClick = {
            val file = File(context.cacheDir, "receipt-${System.currentTimeMillis()}.jpg")
            val output = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                output,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        onCaptured(Uri.fromFile(file))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Unit
                    }
                }
            )
        }) {
            Text("Capture Receipt")
        }
    }
}

@Composable
private fun ProfileScreen(
    repository: AutoKeeperRepository,
    userId: String,
    onSignedOut: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(UserProfile(uid = userId, email = repository.currentEmail())) }
    var displayName by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        repository.observeProfile(userId).collect {
            profile = it
            if (displayName.isBlank()) displayName = it.displayName
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Profile", style = MaterialTheme.typography.titleLarge)
        Text("Email: ${profile.email}")
        OutlinedTextField(displayName, { displayName = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            scope.launch {
                runCatching { repository.updateDisplayName(userId, displayName.trim()) }
                    .onSuccess { onMessage("Profile updated") }
                    .onFailure { onMessage(it.message ?: "Profile update failed") }
            }
        }) {
            Text("Save Profile")
        }
        TextButton(onClick = onSignedOut, modifier = Modifier.align(Alignment.Start)) { Text("Sign Out") }
    }
}
