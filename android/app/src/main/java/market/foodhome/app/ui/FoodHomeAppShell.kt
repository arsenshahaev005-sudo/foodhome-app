package market.foodhome.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.webkit.ValueCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import market.foodhome.app.R
import market.foodhome.app.bridge.BridgeManifest
import market.foodhome.app.bridge.NativeEventQueue
import market.foodhome.app.capabilities.AndroidCapabilityCoordinator
import market.foodhome.app.config.AppEnvironment
import market.foodhome.app.location.AndroidLocationProvider
import market.foodhome.app.location.LocationRequestResult
import market.foodhome.app.media.MediaRequest
import market.foodhome.app.media.MediaRequestPolicy
import market.foodhome.app.media.ResolvedMediaRequest
import market.foodhome.app.media.TemporaryCaptureStore
import market.foodhome.app.media.VisualMediaKind
import market.foodhome.app.navigation.NavigationCoordinator
import market.foodhome.app.notifications.AndroidNotificationCoordinator
import market.foodhome.app.notifications.NotificationPermissionResult
import market.foodhome.app.recovery.CrashLoopBreaker
import market.foodhome.app.payments.AndroidPaymentReturnRouter
import market.foodhome.app.payments.PaymentCoordinator
import market.foodhome.app.telemetry.TelemetryReporter
import market.foodhome.app.web.FoodHomeWebView
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private data class PendingLocationRequest(
    val purpose: String,
    val completion: (LocationRequestResult) -> Unit,
)

private data class PendingNotificationRequest(
    val purpose: String?,
    val completion: (NotificationPermissionResult) -> Unit,
)

private data class PendingMediaRequest(
    val callback: ValueCallback<Array<Uri>>,
    val request: ResolvedMediaRequest,
)

@Composable
fun FoodHomeAppShell(
    environment: AppEnvironment,
    navigationCoordinator: NavigationCoordinator,
    manifest: BridgeManifest,
    paymentCoordinator: PaymentCoordinator,
    paymentReturnRouter: AndroidPaymentReturnRouter,
    telemetry: TelemetryReporter,
    onOpenExternal: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.share_chooser_title)
    var state: AppShellState by remember { mutableStateOf(AppShellState.Loading) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var lastCommittedUrl by remember { mutableStateOf(environment.baseUrl.toASCIIString()) }
    val crashLoopBreaker = remember { CrashLoopBreaker() }
    val locationProvider = remember { AndroidLocationProvider(context.applicationContext) }
    val notificationCoordinator = remember {
        AndroidNotificationCoordinator(context.applicationContext)
    }
    val captureStore = remember { TemporaryCaptureStore(context.cacheDir) }
    var pendingLocation by remember { mutableStateOf<PendingLocationRequest?>(null) }
    var showLocationConfirmation by remember { mutableStateOf(false) }
    var pendingNotification by remember { mutableStateOf<PendingNotificationRequest?>(null) }
    var showNotificationConfirmation by remember { mutableStateOf(false) }
    var pendingMedia by remember { mutableStateOf<PendingMediaRequest?>(null) }
    var showMediaSourceChoice by remember { mutableStateOf(false) }
    var activeCaptureFile by remember { mutableStateOf<File?>(null) }
    var activeCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var paymentEventRevision by remember { mutableIntStateOf(0) }
    val lastWebUserActionAt = remember { AtomicLong(Long.MIN_VALUE) }
    val nativeEventQueue = remember(manifest, environment.trustedOrigin, paymentCoordinator) {
        NativeEventQueue(manifest, environment.trustedOrigin, paymentCoordinator)
    }

    fun completeMedia(uris: List<Uri>?) {
        val callback = pendingMedia?.callback ?: return
        pendingMedia = null
        showMediaSourceChoice = false
        callback.onReceiveValue(uris?.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    val singleVisualPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        completeMedia(uri?.let(::listOf))
    }
    val multipleVisualPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        completeMedia(uris.takeIf { it.isNotEmpty() })
    }
    val singleDocumentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        completeMedia(uri?.let(::listOf))
    }
    val multipleDocumentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        completeMedia(uris.takeIf { it.isNotEmpty() })
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { captured ->
        val uri = activeCaptureUri
        if (captured && uri != null) {
            completeMedia(listOf(uri))
        } else {
            captureStore.delete(activeCaptureFile)
            completeMedia(null)
        }
        activeCaptureFile = null
        activeCaptureUri = null
    }

    fun launchPicker(request: ResolvedMediaRequest) {
        if (request.kind == VisualMediaKind.Documents) {
            val types = request.acceptedTypes.toTypedArray()
            if (request.allowMultiple) multipleDocumentPicker.launch(types)
            else singleDocumentPicker.launch(types)
            return
        }
        val mediaType = when (request.kind) {
            VisualMediaKind.Images -> ActivityResultContracts.PickVisualMedia.ImageOnly
            VisualMediaKind.Videos -> ActivityResultContracts.PickVisualMedia.VideoOnly
            VisualMediaKind.ImagesAndVideos -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
            VisualMediaKind.Documents -> error("Document picker handled above")
        }
        val pickerRequest = PickVisualMediaRequest(mediaType)
        if (request.allowMultiple) multipleVisualPicker.launch(pickerRequest)
        else singleVisualPicker.launch(pickerRequest)
    }

    fun launchCamera() {
        val file = runCatching { captureStore.createImageFile() }.getOrNull()
        if (file == null) {
            completeMedia(null)
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrNull()
        if (uri == null) {
            captureStore.delete(file)
            completeMedia(null)
            return
        }
        activeCaptureFile = file
        activeCaptureUri = uri
        cameraLauncher.launch(uri)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val request = pendingLocation ?: return@rememberLauncherForActivityResult
        if (context.hasLocationPermission()) {
            locationProvider.requestCurrentLocation { result ->
                if (pendingLocation === request) pendingLocation = null
                request.completion(result)
            }
        } else {
            pendingLocation = null
            request.completion(
                LocationRequestResult.Failed(
                    code = "CAPABILITY_UNAVAILABLE",
                    message = "Location permission was denied",
                ),
            )
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val request = pendingNotification ?: return@rememberLauncherForActivityResult
        pendingNotification = null
        request.completion(
            NotificationPermissionResult.Status(
                notificationCoordinator.authorizationStatus(),
            ),
        )
    }

    val capabilityDispatcher = remember(manifest, environment.trustedOrigin, shareChooserTitle) {
        AndroidCapabilityCoordinator(
            manifest = manifest,
            trustedOrigin = environment.trustedOrigin,
            presentShare = { payload ->
                val items = buildList {
                    payload.title?.takeIf(String::isNotBlank)?.let(::add)
                    payload.text?.takeIf(String::isNotBlank)?.let(::add)
                    add(payload.url.toASCIIString())
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, items.joinToString("\n"))
                    payload.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            intent,
                            shareChooserTitle,
                        ),
                    )
                }.isSuccess
            },
            requestLocation = { purpose, completion ->
                pendingLocation?.completion?.invoke(
                    LocationRequestResult.Failed("CANCELLED", "Location request was replaced"),
                )
                pendingLocation = PendingLocationRequest(purpose, completion)
                showLocationConfirmation = true
            },
            notificationStatus = notificationCoordinator::authorizationStatus,
            requestNotificationPermission = { purpose, completion ->
                pendingNotification?.completion?.invoke(NotificationPermissionResult.Cancelled)
                pendingNotification = PendingNotificationRequest(purpose, completion)
                showNotificationConfirmation = true
            },
            paymentCoordinator = paymentCoordinator,
            hasRecentPaymentUserAction = {
                val elapsed = SystemClock.elapsedRealtime() - lastWebUserActionAt.get()
                elapsed in 0..2_000
            },
            telemetry = telemetry,
        )
    }

    LaunchedEffect(Unit) {
        notificationCoordinator.createChannels()
        captureStore.cleanupStale()
    }
    DisposableEffect(Unit) {
        val paymentAttachment = paymentReturnRouter.attach { paymentEventRevision += 1 }
        onDispose {
            paymentAttachment.close()
            locationProvider.cancel()
            pendingLocation?.completion?.invoke(
                LocationRequestResult.Failed("CANCELLED", "Location request was cancelled"),
            )
            pendingNotification?.completion?.invoke(NotificationPermissionResult.Cancelled)
            pendingMedia?.callback?.onReceiveValue(null)
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FoodHomeBackground)
                .safeDrawingPadding()
                .imePadding(),
        ) {
            key(webViewGeneration) {
                FoodHomeWebView(
                    environment = environment,
                    navigationCoordinator = navigationCoordinator,
                    manifest = manifest,
                    crashLoopBreaker = crashLoopBreaker,
                    initialUrl = lastCommittedUrl,
                    onStateChanged = { state = it },
                    onTrustedUrlCommitted = { lastCommittedUrl = it },
                    onRendererGone = { loopBlocked ->
                        completeMedia(null)
                        locationProvider.cancel()
                        pendingLocation?.completion?.invoke(
                            LocationRequestResult.Failed("CANCELLED", "Location request was cancelled"),
                        )
                        pendingLocation = null
                        pendingNotification?.completion?.invoke(NotificationPermissionResult.Cancelled)
                        pendingNotification = null
                        if (loopBlocked) {
                            state = AppShellState.RendererUnavailable(loopBlocked = true)
                        } else {
                            paymentReturnRouter.onWebViewRecovered()
                            state = AppShellState.Loading
                            webViewGeneration += 1
                        }
                    },
                    onOpenExternal = onOpenExternal,
                    capabilityDispatcher = capabilityDispatcher,
                    nativeEventQueue = nativeEventQueue,
                    nativeEventRevision = paymentEventRevision,
                    telemetry = telemetry,
                    onPaymentUserAction = {
                        lastWebUserActionAt.set(SystemClock.elapsedRealtime())
                    },
                    onFileRequest = { callback, mediaRequest: MediaRequest ->
                        completeMedia(null)
                        captureStore.cleanupStale()
                        val resolved = MediaRequestPolicy.resolve(mediaRequest)
                        pendingMedia = PendingMediaRequest(callback, resolved)
                        if (resolved.offerCamera) showMediaSourceChoice = true
                        else launchPicker(resolved)
                        true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AppShellSurface(
                state = state,
                onRetry = {
                    completeMedia(null)
                    state = AppShellState.Loading
                    webViewGeneration += 1
                },
            )
        }

        if (showLocationConfirmation) {
            val request = pendingLocation
            AlertDialog(
                onDismissRequest = {
                    showLocationConfirmation = false
                    pendingLocation = null
                    request?.completion?.invoke(
                        LocationRequestResult.Failed("CANCELLED", "Location request was cancelled"),
                    )
                },
                title = { Text(stringResource(R.string.location_confirmation_title)) },
                text = { Text(request?.purpose.orEmpty()) },
                dismissButton = {
                    TextButton(onClick = {
                        showLocationConfirmation = false
                        pendingLocation = null
                        request?.completion?.invoke(
                            LocationRequestResult.Failed("CANCELLED", "Location request was cancelled"),
                        )
                    }) { Text(stringResource(R.string.permission_cancel)) }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLocationConfirmation = false
                        if (request == null) return@TextButton
                        if (context.hasLocationPermission()) {
                            locationProvider.requestCurrentLocation { result ->
                                if (pendingLocation === request) pendingLocation = null
                                request.completion(result)
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ),
                            )
                        }
                    }) { Text(stringResource(R.string.permission_continue)) }
                },
            )
        }

        if (showNotificationConfirmation) {
            val request = pendingNotification
            AlertDialog(
                onDismissRequest = {
                    showNotificationConfirmation = false
                    pendingNotification = null
                    request?.completion?.invoke(NotificationPermissionResult.Cancelled)
                },
                title = { Text(stringResource(R.string.notification_confirmation_title)) },
                text = { Text(request?.purpose ?: "Узнавать об актуальных событиях Food&Home") },
                dismissButton = {
                    TextButton(onClick = {
                        showNotificationConfirmation = false
                        pendingNotification = null
                        request?.completion?.invoke(NotificationPermissionResult.Cancelled)
                    }) { Text(stringResource(R.string.permission_cancel)) }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showNotificationConfirmation = false
                        if (request == null) return@TextButton
                        notificationCoordinator.markPermissionRequested()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            pendingNotification = null
                            request.completion(
                                NotificationPermissionResult.Status(
                                    notificationCoordinator.authorizationStatus(),
                                ),
                            )
                        }
                    }) { Text(stringResource(R.string.permission_continue)) }
                },
            )
        }

        if (showMediaSourceChoice) {
            val request = pendingMedia?.request
            AlertDialog(
                onDismissRequest = { completeMedia(null) },
                title = { Text(stringResource(R.string.media_source_title)) },
                dismissButton = {
                    TextButton(onClick = {
                        showMediaSourceChoice = false
                        request?.let(::launchPicker) ?: completeMedia(null)
                    }) { Text(stringResource(R.string.media_source_photos)) }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showMediaSourceChoice = false
                        launchCamera()
                    }) { Text(stringResource(R.string.media_source_camera)) }
                },
            )
        }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
