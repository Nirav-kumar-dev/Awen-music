package com.music.vivi

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.music.innertube.YouTube
import com.music.vivi.constants.*
import com.music.vivi.firebase.FirebaseAuthService
import com.music.vivi.ui.theme.vivimusicTheme
import com.music.vivi.utils.dataStore
import com.music.vivi.utils.get
import com.music.vivi.utils.normalizeDataSyncId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val isFirstRun = dataStore.get(IsFirstRunKey, true)
        val forceShow = intent.getBooleanExtra("FORCE_SHOW", false)

        if (!isFirstRun && !forceShow) {
            finishOnboarding()
            return
        }

        enableEdgeToEdge()
        setContent {
            vivimusicTheme {
                WelcomeAuthScreen(
                    onFinished = {
                        lifecycleScope.launch {
                            dataStore.edit { it[IsFirstRunKey] = false }
                            if (forceShow) {
                                finish()
                            } else {
                                finishOnboarding()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun finishOnboarding() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

private enum class AuthMode {
    SIGN_UP,
    SIGN_IN
}

@Composable
private fun WelcomeAuthScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var authMode by remember { mutableStateOf(AuthMode.SIGN_UP) }
    var currentStep by remember { mutableIntStateOf(1) } // 1..4

    // Form states
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var birthdate by remember { mutableStateOf("") }

    var email by remember { mutableStateOf("") }

    // Feature toggles
    var aiSync by remember { mutableStateOf(true) }
    var dolbyMusic by remember { mutableStateOf(true) }
    var customPlaylist by remember { mutableStateOf(true) }
    var equalizer by remember { mutableStateOf(true) }
    var youtubeSync by remember { mutableStateOf(true) }

    // YouTube Sync state
    var isYoutubeConnected by remember { mutableStateOf(false) }
    var youtubeAccountName by remember { mutableStateOf("") }
    var youtubeChannelHandle by remember { mutableStateOf("") }
    var showYoutubeLoginDialog by remember { mutableStateOf(false) }

    // Sign In states
    var signInIdentifier by remember { mutableStateOf("") }
    var signInPassword by remember { mutableStateOf("") }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    // Loading / error states
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val bgGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF231B38),
                Color(0xFF1E284A),
                Color(0xFF181C2E),
                Color(0xFF141724)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header: "welcome / welcome back"
            Text(
                text = if (authMode == AuthMode.SIGN_IN) "welcome back" else "welcome",
                style = TextStyle(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFFD6D1E8)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "to sonic boom",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFFA59EC2)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Step Indicator (Only during SIGN_UP)
            if (authMode == AuthMode.SIGN_UP) {
                StepIndicatorBar(
                    currentStep = currentStep,
                    totalSteps = 4
                )
                Spacer(modifier = Modifier.height(28.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Error display
            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            // Animated Screen Content
            AnimatedContent(
                targetState = if (authMode == AuthMode.SIGN_IN) 0 else currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) togetherWith
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(180))
                },
                label = "AuthSteps"
            ) { step ->
                when (step) {
                    0 -> {
                        // SIGN IN SCREEN ("welcome back")
                        SignInScreenContent(
                            identifier = signInIdentifier,
                            onIdentifierChange = { signInIdentifier = it },
                            password = signInPassword,
                            onPasswordChange = { signInPassword = it },
                            onForgotPasswordClick = { showForgotPasswordDialog = true },
                            onSwitchToSignUp = {
                                authMode = AuthMode.SIGN_UP
                                currentStep = 1
                                errorMessage = null
                            }
                        )
                    }
                    1 -> {
                        // STEP 1: Name, Username, Passwords
                        Step1RegistrationContent(
                            name = name,
                            onNameChange = { name = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            confirmPassword = confirmPassword,
                            onConfirmPasswordChange = { confirmPassword = it },
                            onSwitchToSignIn = {
                                authMode = AuthMode.SIGN_IN
                                errorMessage = null
                            }
                        )
                    }
                    2 -> {
                        // STEP 2: Date of Birth Picker
                        Step2BirthdateContent(
                            birthdate = birthdate,
                            onBirthdateChange = { birthdate = it }
                        )
                    }
                    3 -> {
                        // STEP 3: Email & Feature Checklists
                        Step3PreferencesContent(
                            username = username,
                            email = email,
                            onEmailChange = { email = it },
                            aiSync = aiSync,
                            onAiSyncChange = { aiSync = it },
                            dolbyMusic = dolbyMusic,
                            onDolbyMusicChange = { dolbyMusic = it },
                            customPlaylist = customPlaylist,
                            onCustomPlaylistChange = { customPlaylist = it },
                            equalizer = equalizer,
                            onEqualizerChange = { equalizer = it },
                            youtubeSync = youtubeSync,
                            onYoutubeSyncChange = { youtubeSync = it }
                        )
                    }
                    4 -> {
                        // STEP 4: YouTube Sync
                        Step4YouTubeSyncContent(
                            isConnected = isYoutubeConnected,
                            accountName = youtubeAccountName,
                            channelHandle = youtubeChannelHandle,
                            onConnectClick = { showYoutubeLoginDialog = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.height(32.dp))

            // Bottom Navigation Actions ("skip" / "next")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (authMode == AuthMode.SIGN_UP && currentStep == 4) {
                    TextButton(
                        onClick = {
                            // Skip YouTube sync and complete registration
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                completeRegistration(
                                    context = context,
                                    name = name,
                                    username = username,
                                    email = email,
                                    password = password,
                                    birthdate = birthdate,
                                    preferences = mapOf(
                                        "aiSync" to aiSync,
                                        "dolby" to dolbyMusic,
                                        "customPlaylist" to customPlaylist,
                                        "equalizer" to equalizer,
                                        "youtubeSync" to false
                                    ),
                                    youtubeConnected = false,
                                    youtubeName = "",
                                    youtubeHandle = "",
                                    onSuccess = onFinished,
                                    onError = {
                                        isLoading = false
                                        errorMessage = it
                                    }
                                )
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "skip",
                            color = Color(0xFFD6D1E8),
                            fontSize = 16.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        if (authMode == AuthMode.SIGN_IN) {
                            // Process Sign In
                            if (signInIdentifier.isBlank() || signInPassword.isBlank()) {
                                errorMessage = "Please enter your email and password"
                                return@Button
                            }
                            scope.launch {
                                isLoading = true
                                val res = FirebaseAuthService.signIn(signInIdentifier.trim(), signInPassword)
                                res.onSuccess { fbUser ->
                                    // Save credentials to DataStore
                                    context.dataStore.edit {
                                        it[FirebaseUidKey] = fbUser.uid
                                        it[FirebaseTokenKey] = fbUser.idToken
                                        it[FirebaseEmailKey] = fbUser.email
                                        it[FirebaseIsLoggedInKey] = true
                                    }
                                    // Fetch user profile from database
                                    FirebaseAuthService.fetchUserProfile(fbUser.uid, fbUser.idToken).onSuccess { profile ->
                                        context.dataStore.edit {
                                            it[FirebaseNameKey] = profile.name
                                            it[FirebaseUsernameKey] = profile.username
                                            it[FirebaseBirthdateKey] = profile.birthdate
                                        }
                                    }
                                    isLoading = false
                                    onFinished()
                                }.onFailure {
                                    isLoading = false
                                    errorMessage = it.message ?: "Login failed"
                                }
                            }
                        } else {
                            // Process Sign Up Steps
                            when (currentStep) {
                                1 -> {
                                    if (name.isBlank()) {
                                        errorMessage = "Please enter your name"
                                    } else if (username.isBlank()) {
                                        errorMessage = "Please create a username"
                                    } else if (password.length < 4) {
                                        errorMessage = "Password must be at least 4 characters"
                                    } else if (password != confirmPassword) {
                                        errorMessage = "Passwords do not match"
                                    } else {
                                        currentStep = 2
                                    }
                                }
                                2 -> {
                                    if (birthdate.isBlank()) {
                                        errorMessage = "Please select your date of birth"
                                    } else {
                                        currentStep = 3
                                    }
                                }
                                3 -> {
                                    if (email.isBlank() || !email.contains("@")) {
                                        errorMessage = "Please enter a valid email address"
                                    } else {
                                        currentStep = 4
                                    }
                                }
                                4 -> {
                                    // Complete registration with YouTube Sync (if connected)
                                    scope.launch {
                                        isLoading = true
                                        completeRegistration(
                                            context = context,
                                            name = name,
                                            username = username,
                                            email = email,
                                            password = password,
                                            birthdate = birthdate,
                                            preferences = mapOf(
                                                "aiSync" to aiSync,
                                                "dolby" to dolbyMusic,
                                                "customPlaylist" to customPlaylist,
                                                "equalizer" to equalizer,
                                                "youtubeSync" to isYoutubeConnected
                                            ),
                                            youtubeConnected = isYoutubeConnected,
                                            youtubeName = youtubeAccountName,
                                            youtubeHandle = youtubeChannelHandle,
                                            onSuccess = onFinished,
                                            onError = {
                                                isLoading = false
                                                errorMessage = it
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEBEBF5),
                        contentColor = Color(0xFF141724)
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF141724),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "next",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // YouTube Sign-in WebView Dialog
    if (showYoutubeLoginDialog) {
        YouTubeLoginDialog(
            onDismiss = { showYoutubeLoginDialog = false },
            onLoginSuccess = { ytName, ytHandle ->
                isYoutubeConnected = true
                youtubeAccountName = ytName
                youtubeChannelHandle = ytHandle
                showYoutubeLoginDialog = false
                Toast.makeText(context, "YouTube Synced: $ytName", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            onDismiss = { showForgotPasswordDialog = false },
            onSend = { resetEmail ->
                scope.launch {
                    val res = FirebaseAuthService.sendPasswordReset(resetEmail)
                    res.onSuccess {
                        Toast.makeText(context, "Password reset email sent!", Toast.LENGTH_LONG).show()
                        showForgotPasswordDialog = false
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "Failed to send reset email", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

/**
 * Handles Firebase Auth Sign-Up and saving to Firebase Realtime Database
 */
private suspend fun completeRegistration(
    context: Context,
    name: String,
    username: String,
    email: String,
    password: String,
    birthdate: String,
    preferences: Map<String, Boolean>,
    youtubeConnected: Boolean,
    youtubeName: String,
    youtubeHandle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    // 1. Sign up with Firebase Auth
    var fbUser = FirebaseAuthService.signUp(email.trim(), password).getOrNull()

    // If email already exists, attempt to sign in
    if (fbUser == null) {
        val signInRes = FirebaseAuthService.signIn(email.trim(), password)
        fbUser = signInRes.getOrNull()
        if (fbUser == null) {
            onError(signInRes.exceptionOrNull()?.message ?: "Sign-up failed. Please check your credentials.")
            return
        }
    }

    // 2. Save user profile to Firebase Realtime Database at /users/$uid
    val dbResult = FirebaseAuthService.saveUserProfile(
        uid = fbUser.uid,
        idToken = fbUser.idToken,
        name = name,
        username = username,
        email = email,
        password = password,
        birthdate = birthdate,
        preferences = preferences,
        youtubeConnected = youtubeConnected,
        youtubeAccountName = youtubeName,
        youtubeChannelHandle = youtubeHandle
    )

    if (dbResult.isFailure) {
        Timber.e(dbResult.exceptionOrNull(), "Failed to save profile to Realtime Database")
        // We still save locally to allow user into the app
    }

    // 3. Save session to local DataStore
    context.dataStore.edit {
        it[FirebaseUidKey] = fbUser.uid
        it[FirebaseTokenKey] = fbUser.idToken
        it[FirebaseNameKey] = name.trim()
        it[FirebaseUsernameKey] = username.trim()
        it[FirebaseEmailKey] = email.trim()
        it[FirebaseBirthdateKey] = birthdate
        it[FirebaseIsLoggedInKey] = true

        it[FirebaseAiSyncKey] = preferences["aiSync"] ?: true
        it[FirebaseDolbyKey] = preferences["dolby"] ?: true
        it[FirebaseCustomPlaylistKey] = preferences["customPlaylist"] ?: true
        it[FirebaseEqualizerKey] = preferences["equalizer"] ?: true
        it[FirebaseYoutubeSyncKey] = youtubeConnected
    }

    onSuccess()
}

// ============================================================================
// STEP INDICATOR BAR (1, 2, 3, 4 with cyan progress line)
// ============================================================================
@Composable
private fun StepIndicatorBar(
    currentStep: Int,
    totalSteps: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0x334E577C))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(30.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (step in 1..totalSteps) {
                    val isActive = step <= currentStep
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) Color(0xFF67B7A4) else Color(0xFF535D7A)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$step",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Serif,
                            color = if (isActive) Color(0xFF142420) else Color(0xFFC0C7DE)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress Bar Underline
            val progress = currentStep.toFloat() / totalSteps.toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x22FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF4EE2C1), Color(0xFF3BA2E8))
                            )
                        )
                )
            }
        }
    }
}

// ============================================================================
// STEP 1: Registration Form (Image 3)
// ============================================================================
@Composable
private fun Step1RegistrationContent(
    name: String,
    onNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    onSwitchToSignIn: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PillInputField(
            label = "what is your beautiful Name",
            value = name,
            onValueChange = onNameChange,
            placeholder = "Enter your full name"
        )

        Spacer(modifier = Modifier.height(20.dp))

        PillInputField(
            label = "create a outstanding username",
            value = username,
            onValueChange = onUsernameChange,
            placeholder = "Choose a unique username"
        )

        Spacer(modifier = Modifier.height(20.dp))

        PillInputField(
            label = "create tough password",
            value = password,
            onValueChange = onPasswordChange,
            isPassword = true,
            placeholder = "At least 4 characters"
        )

        Spacer(modifier = Modifier.height(20.dp))

        PillInputField(
            label = "re write password",
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            isPassword = true,
            placeholder = "Re-enter password"
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Already have an account? ",
                color = Color(0xFFA59EC2),
                fontSize = 14.sp
            )
            Text(
                text = "Sign In",
                color = Color(0xFF4EE2C1),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onSwitchToSignIn() }
            )
        }
    }
}

// ============================================================================
// STEP 2: Date of Birth Picker (Image 5)
// ============================================================================
@Composable
private fun Step2BirthdateContent(
    birthdate: String,
    onBirthdateChange: (String) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format("%02d/%02d/%04d", month + 1, dayOfMonth, year)
                onBirthdateChange(formatted)
            },
            calendar.get(Calendar.YEAR) - 18,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0x334E577C)),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0x33FFFFFF)))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Select date",
                    fontSize = 14.sp,
                    color = Color(0xFFA59EC2),
                    fontFamily = FontFamily.Serif
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enter date",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White
                    )

                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar_month),
                            contentDescription = "Select Date",
                            tint = Color(0xFFA59EC2),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Date display pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, Color(0xFF67B7A4), RoundedCornerShape(12.dp))
                        .clickable { datePickerDialog.show() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = if (birthdate.isNotBlank()) birthdate else "mm/dd/yyyy",
                        fontSize = 16.sp,
                        color = if (birthdate.isNotBlank()) Color.White else Color(0x88FFFFFF)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onBirthdateChange("") }) {
                        Text("Cancel", color = Color(0xFFA59EC2))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { datePickerDialog.show() }) {
                        Text("OK", color = Color(0xFF4EE2C1), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// STEP 3: Email & Feature Checklists (Image 2)
// ============================================================================
@Composable
private fun Step3PreferencesContent(
    username: String,
    email: String,
    onEmailChange: (String) -> Unit,
    aiSync: Boolean,
    onAiSyncChange: (Boolean) -> Unit,
    dolbyMusic: Boolean,
    onDolbyMusicChange: (Boolean) -> Unit,
    customPlaylist: Boolean,
    onCustomPlaylistChange: (Boolean) -> Unit,
    equalizer: Boolean,
    onEqualizerChange: (Boolean) -> Unit,
    youtubeSync: Boolean,
    onYoutubeSyncChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PillInputField(
            label = "give me your email ${if (username.isNotBlank()) "{$username}" else ""}",
            value = email,
            onValueChange = onEmailChange,
            placeholder = "name@example.com",
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Checklists container card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0x334E577C)),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0x33FFFFFF)))
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                PreferenceCheckboxRow("ai sync", aiSync, onAiSyncChange)
                Spacer(modifier = Modifier.height(18.dp))
                PreferenceCheckboxRow("music with dolby", dolbyMusic, onDolbyMusicChange)
                Spacer(modifier = Modifier.height(18.dp))
                PreferenceCheckboxRow("custom playlist", customPlaylist, onCustomPlaylistChange)
                Spacer(modifier = Modifier.height(18.dp))
                PreferenceCheckboxRow("equalizer", equalizer, onEqualizerChange)
                Spacer(modifier = Modifier.height(18.dp))
                PreferenceCheckboxRow("YouTube sync", youtubeSync, onYoutubeSyncChange)
            }
        }
    }
}

@Composable
private fun PreferenceCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFFE3E0F2)
        )

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) Color(0xFF6B58A6) else Color(0x33FFFFFF))
                .border(1.dp, if (checked) Color(0xFF8D78D6) else Color(0x44FFFFFF), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ============================================================================
// STEP 4: YouTube Sync (Image 1)
// ============================================================================
@Composable
private fun Step4YouTubeSyncContent(
    isConnected: Boolean,
    accountName: String,
    channelHandle: String,
    onConnectClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "YOU TUBE SYNC",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Google Sign in Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable { onConnectClick() },
            colors = CardDefaults.cardColors(containerColor = Color(0x334E577C)),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0x33FFFFFF)))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Google Circle Avatar ("G")
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD6D1E8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E284A)
                        )
                    }

                    Column {
                        Text(
                            text = if (isConnected) "google sign in: connected" else "google sign in",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (isConnected) {
                                if (channelHandle.isNotBlank()) "@$channelHandle" else accountName
                            } else {
                                "go ahead"
                            },
                            fontSize = 14.sp,
                            color = if (isConnected) Color(0xFF4EE2C1) else Color(0xFFA59EC2)
                        )
                    }
                }

                // Red YouTube Play Icon Badge
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isConnected) Color(0xFF2E7D32) else Color(0xFFFF0000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (isConnected) R.drawable.check else R.drawable.play),
                        contentDescription = "YouTube",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// SIGN IN CONTENT ("welcome back to sonic boom", Image 4)
// ============================================================================
@Composable
private fun SignInScreenContent(
    identifier: String,
    onIdentifierChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSwitchToSignUp: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PillInputField(
            label = "Enter you username or email",
            value = identifier,
            onValueChange = onIdentifierChange,
            placeholder = "username or name@example.com"
        )

        Spacer(modifier = Modifier.height(24.dp))

        PillInputField(
            label = "Enter your password",
            value = password,
            onValueChange = onPasswordChange,
            isPassword = true,
            placeholder = "Enter password"
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "forgot password",
            fontSize = 14.sp,
            color = Color(0xFF67B7A4),
            fontFamily = FontFamily.Serif,
            modifier = Modifier
                .clickable { onForgotPasswordClick() }
                .padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Don't have an account? ",
                color = Color(0xFFA59EC2),
                fontSize = 14.sp
            )
            Text(
                text = "Sign Up",
                color = Color(0xFF4EE2C1),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onSwitchToSignUp() }
            )
        }
    }
}

// ============================================================================
// REUSABLE PILL INPUT FIELD
// ============================================================================
@Composable
private fun PillInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontFamily = FontFamily.Serif,
            color = Color(0xFFDCD8EE),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x2E53658C))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color(0x66FFFFFF),
                    fontSize = 15.sp
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ============================================================================
// YOUTUBE SIGN-IN DIALOG (WebView)
// ============================================================================
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (name: String, handle: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141724))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sign in to YouTube",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(painter = painterResource(R.drawable.close), contentDescription = "Close", tint = Color.White)
                    }
                }

                if (isValidating) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF141724)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF4EE2C1))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connecting YouTube account...", color = Color.White)
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                                        loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

                                        if (url?.startsWith("https://music.youtube.com") == true ||
                                            url?.contains("youtube.com") == true
                                        ) {
                                            val cookies = CookieManager.getInstance().getCookie(url)
                                            if (!cookies.isNullOrBlank() && (cookies.contains("SAPISID") || cookies.contains("__Secure-3PSID"))) {
                                                isValidating = true
                                                coroutineScope.launch {
                                                    context.dataStore.edit { it[InnerTubeCookieKey] = cookies }
                                                    YouTube.cookie = cookies

                                                    val result = withTimeoutOrNull(15_000.milliseconds) {
                                                        var attempt = YouTube.accountInfo()
                                                        if (attempt.isFailure) {
                                                            delay(750)
                                                            attempt = YouTube.accountInfo()
                                                        }
                                                        attempt
                                                    }

                                                    result?.onSuccess { info ->
                                                        context.dataStore.edit {
                                                            it[AccountNameKey] = info.name
                                                            it[AccountEmailKey] = info.email.orEmpty()
                                                            it[AccountChannelHandleKey] = info.channelHandle.orEmpty()
                                                        }
                                                        onLoginSuccess(info.name, info.channelHandle.orEmpty())
                                                    }?.onFailure {
                                                        isValidating = false
                                                        Toast.makeText(context, "Could not fetch YouTube info", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                settings.apply {
                                    javaScriptEnabled = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun onRetrieveVisitorData(newVisitorData: String?) {
                                        if (newVisitorData != null) {
                                            coroutineScope.launch {
                                                context.dataStore.edit { it[VisitorDataKey] = newVisitorData }
                                            }
                                        }
                                    }
                                    @JavascriptInterface
                                    fun onRetrieveDataSyncId(newDataSyncId: String?) {
                                        if (newDataSyncId != null) {
                                            val normalized = normalizeDataSyncId(newDataSyncId)
                                            if (normalized != null) {
                                                coroutineScope.launch {
                                                    context.dataStore.edit { it[DataSyncIdKey] = normalized }
                                                }
                                            }
                                        }
                                    }
                                }, "Android")

                                loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue")
                                webView = this
                            }
                        }
                    )
                }
            }
        }
    }
}

// ============================================================================
// FORGOT PASSWORD DIALOG
// ============================================================================
@Composable
private fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onSend: (email: String) -> Unit
) {
    var emailInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Password", color = Color.White) },
        text = {
            Column {
                Text(
                    "Enter your registered email address and we'll send you a password reset link.",
                    color = Color(0xFFA59EC2),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                PillInputField(
                    label = "Email Address",
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    placeholder = "name@example.com",
                    keyboardType = KeyboardType.Email
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (emailInput.isNotBlank()) onSend(emailInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF67B7A4))
            ) {
                Text("Send Reset Link", color = Color(0xFF142420))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA59EC2))
            }
        },
        containerColor = Color(0xFF1E284A),
        shape = RoundedCornerShape(24.dp)
    )
}
