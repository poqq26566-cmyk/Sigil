package dev.animeshvarma.sigil

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import dev.animeshvarma.sigil.data.LockManager
import dev.animeshvarma.sigil.ui.OnboardingOrchestrator
import dev.animeshvarma.sigil.ui.SigilApp
import dev.animeshvarma.sigil.ui.screens.LockScreen
import dev.animeshvarma.sigil.ui.theme.SigilTheme
import dev.animeshvarma.sigil.util.SigilPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var lockManager: LockManager
    private lateinit var viewModel: SigilViewModel
    private lateinit var prefs: SigilPreferences

    private val isLockedState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[SigilViewModel::class.java]
        prefs = SigilPreferences(this)
        lockManager = LockManager(this)

        // 1. SECURE WINDOW IMMEDIATELY
        if (prefs.isScreenShieldEnabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        // 2. Initial Lock Logic
        if (lockManager.isAppLocked()) {
            isLockedState.value = true
        }

        // 3. Lifecycle Enforcer
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    lockManager.recordBackgroundEvent()
                    if (prefs.isScreenShieldEnabled) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (prefs.isScreenShieldEnabled) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }

                    if (lockManager.isAppLocked()) {
                        isLockedState.value = true
                        // AMNESIA PROTOCOL
                        viewModel.clearSensitiveData()
                    }
                }
                else -> {}
            }
        })

        val showOnboarding = mutableStateOf(!prefs.hasCompletedOnboarding())

        checkAndProcessIntent(intent, viewModel)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = if (prefs.isDynamicColorsEnabled) systemDark else prefs.isDarkModeEnabled

            SigilTheme(
                darkTheme = useDarkTheme,
                dynamicColor = prefs.isDynamicColorsEnabled,
                seedColor = prefs.selectedThemeColor
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {

                        // LAYER 1: APP
                        SigilApp(viewModel = viewModel)

                        // LAYER 2: LOCK SCREEN
                        if (isLockedState.value) {
                            LockScreen(
                                viewModel = viewModel,
                                onUnlock = { isLockedState.value = false }
                            )
                        }

                        // LAYER 3: ONBOARDING WITH ANIMATION
                        if (!isLockedState.value) {
                            AnimatedVisibility(
                                visible = showOnboarding.value,
                                // FIX: Removed slideOutVertically to prevent layout jump
                                exit = fadeOut(animationSpec = tween(500))
                            ) {
                                OnboardingOrchestrator(
                                    viewModel = viewModel,
                                    onComplete = {
                                        prefs.setOnboardingCompleted(true)
                                        showOnboarding.value = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val viewModel = ViewModelProvider(this)[SigilViewModel::class.java]
        checkAndProcessIntent(intent, viewModel)
    }

    private fun checkAndProcessIntent(intent: Intent?, viewModel: SigilViewModel) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                intent.removeExtra(Intent.EXTRA_TEXT)
                viewModel.handleIncomingSharedText(sharedText)
            }
        }
    }
}