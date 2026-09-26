package market.foodhome.app.updates

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import market.foodhome.app.BuildConfig
import market.foodhome.app.R

/** Native-only optional prompt. Never blocks login, checkout or offline use. */
@Composable
internal fun ApkUpdatePrompt(contentReady: Boolean, testRepository: ApkUpdateRepository? = null) {
    if (!BuildConfig.DIRECT_APK_UPDATES_ENABLED || BuildConfig.DEBUG) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val repository = remember { testRepository ?: ApkUpdateRepository(context.applicationContext) }
    val preferences = remember { context.getSharedPreferences("apk_updates", 0) }
    val scope = rememberCoroutineScope()
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var session by remember {
        mutableStateOf(ApkUpdateSessionPolicy.restore(
            preferences.getString(ApkUpdateSessionPolicy.PREFERENCE_KEY, null),
            BuildConfig.VERSION_NAME, System.currentTimeMillis(),
        ))
    }
    var recoveryPending by remember { mutableStateOf(session?.fileName != null) }
    var externalActivity by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var operation by remember { mutableStateOf<Job?>(null) }

    fun persist(next: ApkUpdateSession): Boolean {
        // Commit before external UI: granting source permission can kill/recreate this process.
        val saved = preferences.edit().putString(
            ApkUpdateSessionPolicy.PREFERENCE_KEY, ApkUpdateSessionPolicy.encode(next),
        ).commit()
        session = if (saved) next else next.copy(stage = UpdateStage.Error)
        return saved
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); repository.cancel() }
    }
    LaunchedEffect(foreground, contentReady) {
        if (!foreground || !contentReady) return@LaunchedEffect
        val pending = session
        if (pending != null) {
            if (recoveryPending) {
                try {
                    persist(pending.copy(stage = UpdateStage.Verify))
                    repository.validate(repository.cachedFile(requireNotNull(pending.fileName)), pending.release)
                    persist(pending.copy(stage = UpdateStage.Ready))
                    recoveryPending = false
                } catch (cancelled: CancellationException) {
                    throw cancelled // Retry recovery on next foreground, never auto-install.
                } catch (_: Exception) {
                    persist(pending.copy(stage = UpdateStage.Error, fileName = null))
                    recoveryPending = false
                }
            }
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val last = preferences.getLong("checked_at", 0)
        val deferred = preferences.getLong("deferred_until", 0)
        if (last <= now && now - last < 6 * 60 * 60 * 1000L) return@LaunchedEffect
        if (deferred > now && deferred - now <= 24 * 60 * 60 * 1000L) return@LaunchedEffect
        preferences.edit().putLong("checked_at", now).apply()
        try {
            repository.latest(BuildConfig.VERSION_NAME)?.let {
                persist(ApkUpdateSession(it, UpdateStage.Offer, now))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { /* Offline/untrusted metadata: keep the product usable. */ }
    }

    fun later() {
        operation?.cancel()
        repository.cancel()
        val name = session?.fileName
        preferences.edit().remove(ApkUpdateSessionPolicy.PREFERENCE_KEY)
            .putLong("deferred_until", System.currentTimeMillis() + ApkUpdateSessionPolicy.MAX_AGE_MS).commit()
        session = null
        recoveryPending = false
        name?.let { runCatching { repository.cachedFile(it).delete() } }
    }

    val installerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        externalActivity = false
        session?.let { persist(it.copy(stage = UpdateStage.Ready)) }
        // A result is not proof of installation; restore drops releases <= installed version.
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        externalActivity = false
        session?.let {
            persist(it.copy(stage = if (context.packageManager.canRequestPackageInstalls())
                UpdateStage.Ready else UpdateStage.Permission))
        }
        // Return to an explicit Install button; never install from this callback.
    }

    fun install() {
        val selected = session ?: return
        val name = selected.fileName ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            persist(selected.copy(stage = UpdateStage.Permission))
            return
        }
        if (!persist(selected.copy(stage = UpdateStage.Verify))) return
        operation = scope.launch {
            try {
                val downloaded = repository.cachedFile(name)
                repository.validate(downloaded, selected.release)
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    persist(selected.copy(stage = UpdateStage.Ready))
                    return@launch
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", downloaded)
                // Persist a resumable button before handing the URI to Android.
                if (!persist(selected.copy(stage = UpdateStage.Ready))) return@launch
                externalActivity = true
                installerLauncher.launch(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri("FoodHome update", uri)
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                externalActivity = false
                persist(selected.copy(stage = UpdateStage.Error, fileName = null))
            }
        }
    }

    val selected = session ?: return
    val stage = selected.stage
    if (!foreground || externalActivity) return
    AlertDialog(
        // Hiding a dialog for system UI is not the user's Later action.
        onDismissRequest = { if (foreground && !externalActivity && stage != UpdateStage.Verify) later() },
        title = { Text(stringResource(R.string.apk_update_title)) },
        text = { Text(when (stage) {
            UpdateStage.Offer -> stringResource(R.string.apk_update_offer, selected.release.version)
            UpdateStage.Download -> stringResource(R.string.apk_update_downloading, progress)
            UpdateStage.Ready -> stringResource(R.string.apk_update_ready)
            UpdateStage.Permission -> stringResource(R.string.apk_update_permission)
            UpdateStage.Verify -> stringResource(R.string.apk_update_verifying)
            UpdateStage.Error -> stringResource(R.string.apk_update_error)
        }) },
        dismissButton = {
            if (stage != UpdateStage.Verify) TextButton(onClick = ::later) {
                Text(stringResource(if (stage == UpdateStage.Download) R.string.apk_update_cancel else R.string.apk_update_later))
            }
        },
        confirmButton = {
            when (stage) {
                UpdateStage.Offer, UpdateStage.Error -> TextButton(onClick = {
                    if (persist(selected.copy(stage = UpdateStage.Download, fileName = null))) {
                        progress = 0
                        operation = scope.launch {
                            try {
                                val downloaded = repository.download(selected.release) { percent ->
                                    scope.launch { progress = percent }
                                }
                                if (!persist(selected.copy(stage = UpdateStage.Ready, fileName = downloaded.name)))
                                    downloaded.delete()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) { persist(selected.copy(stage = UpdateStage.Error, fileName = null)) }
                        }
                    }
                }) { Text(stringResource(if (stage == UpdateStage.Error) R.string.apk_update_retry else R.string.apk_update_download)) }
                UpdateStage.Ready -> TextButton(onClick = ::install) { Text(stringResource(R.string.apk_update_install)) }
                UpdateStage.Permission -> TextButton(onClick = {
                    if (persist(selected)) runCatching {
                        externalActivity = true
                        permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")))
                    }.onFailure {
                        externalActivity = false
                        persist(selected.copy(stage = UpdateStage.Error))
                    }
                }) { Text(stringResource(R.string.apk_update_open_settings)) }
                UpdateStage.Download, UpdateStage.Verify -> Unit
            }
        },
    )
}
