package com.auraride.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.auraride.app.AuraApp
import com.auraride.app.data.PaymentMethod
import com.auraride.app.data.Role
import com.auraride.app.ui.screens.ProfileScreen
import com.auraride.app.ui.screens.LivenessScreen
import com.auraride.app.ui.screens.PassengerHomeScreen
import com.auraride.app.ui.screens.RideTrackingScreen
import com.auraride.app.ui.screens.driver.DriverActiveRideScreen
import com.auraride.app.ui.screens.driver.DriverHomeScreen
import com.auraride.app.ui.screens.driver.DriverOfferScreen
import com.auraride.app.ui.screens.onboarding.*
import com.auraride.app.ui.screens.passenger.RideRequestScreen
import com.auraride.app.ui.screens.passenger.StepUpScreen
import com.auraride.app.ui.screens.passenger.TripCompleteScreen
import com.auraride.app.ui.vm.DriverViewModel
import com.auraride.app.ui.vm.MeViewModel
import com.auraride.app.ui.vm.PassengerRideViewModel
import com.auraride.app.ui.vm.RegisterViewModel

// Full prototype flow. Backend/biometric/camera/map are mocked (see per-file
// `ponytail:` seams). Role + last payment are held in nav-level state instead of
// route args to keep the graph readable for a prototype.
private object R {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val OTP = "otp"
    const val LIVENESS = "liveness"
    const val NID = "nid"
    const val DRIVER_EXTRAS = "driver_extras"
    const val BIOMETRIC = "biometric"
    const val PENDING = "pending"
    const val HOME = "home"
    const val RIDE_REQUEST = "ride_request"
    const val TRACKING = "tracking"
    const val TRIP_COMPLETE = "trip_complete"
    const val STEP_UP = "step_up"
    const val DRIVER_OFFER = "driver_offer"
    const val DRIVER_ACTIVE = "driver_active"
    const val PROFILE = "profile"
}

@Composable
fun AuraNav() {
    val nav = rememberNavController()
    val registerVm: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory)
    val rideVm: PassengerRideViewModel = viewModel(factory = PassengerRideViewModel.Factory)
    val driverVm: DriverViewModel = viewModel(factory = DriverViewModel.Factory)
    val meVm: MeViewModel = viewModel(factory = MeViewModel.Factory)
    val activity = LocalContext.current as? FragmentActivity
    val authRepo = (LocalContext.current.applicationContext as AuraApp).container.authRepository

    // Session restore: if a token is already stored, skip onboarding and go straight to home.
    val loggedIn = remember { authRepo.isLoggedIn() }
    var role by remember {
        mutableStateOf(authRepo.cachedRole()?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.PASSENGER)
    }
    var payment by remember { mutableStateOf(PaymentMethod.BKASH) }
    var activeRideId by remember { mutableStateOf(0L) }
    var driverRideId by remember { mutableStateOf(0L) }
    var resubmitMode by remember { mutableStateOf(false) }

    // navigate to home, clearing the onboarding back stack
    fun goHome() = nav.navigate(R.HOME) { popUpTo(R.SPLASH) { inclusive = true } }

    // A restored session is optimistic (we start on HOME), but the account may have been
    // deleted or rejected server-side. Confirm with the server; if it's gone/unusable, sign
    // out and return to the splash so we never show a home for a dead account.
    LaunchedEffect(Unit) {
        if (loggedIn && !authRepo.sessionStillValid()) {
            authRepo.logout()
            role = Role.PASSENGER
            nav.navigate(R.SPLASH) { popUpTo(R.HOME) { inclusive = true } }
        }
    }

    NavHost(navController = nav, startDestination = if (loggedIn) R.HOME else R.SPLASH) {
        composable(R.SPLASH) {
            SplashScreen(
                onCreateAccount = { nav.navigate(R.REGISTER) },
                onLogin = { nav.navigate(R.LOGIN) },
            )
        }
        composable(R.LOGIN) {
            LoginScreen(onBack = { nav.popBackStack() }, onLogin = { r -> role = r; goHome() })
        }
        composable(R.REGISTER) {
            RegisterInfoScreen(registerVm, onBack = { nav.popBackStack() }, onContinue = { r ->
                role = r; nav.navigate(R.OTP)
            })
        }
        composable(R.OTP) {
            OtpScreen(onBack = { nav.popBackStack() }, onVerified = { nav.navigate(R.LIVENESS) })
        }
        composable(R.LIVENESS) {
            LivenessScreen(registerVm, onVerified = { nav.navigate(R.NID) })
        }
        composable(R.NID) {
            NidCaptureScreen(registerVm, onBack = { nav.popBackStack() }, onContinue = {
                if (resubmitMode) {
                    // Rejected → resubmit: no re-enroll, just POST a fresh submission.
                    registerVm.resubmit {
                        resubmitMode = false
                        nav.navigate(R.PENDING) { popUpTo(R.LIVENESS) { inclusive = true } }
                    }
                } else if (role == Role.DRIVER) nav.navigate(R.DRIVER_EXTRAS) else nav.navigate(R.BIOMETRIC)
            })
        }
        composable(R.DRIVER_EXTRAS) {
            DriverExtrasScreen(registerVm, onBack = { nav.popBackStack() }, onContinue = { nav.navigate(R.BIOMETRIC) })
        }
        composable(R.BIOMETRIC) {
            BiometricEnrollScreen(registerVm, onBack = { nav.popBackStack() }, onEnrolled = { nav.navigate(R.PENDING) })
        }
        composable(R.PENDING) {
            PendingScreen(
                registerVm,
                onApproved = { goHome() },
                onResubmit = { resubmitMode = true; nav.navigate(R.LIVENESS) },
            )
        }

        composable(R.HOME) {
            val me by meVm.state.collectAsState()
            LaunchedEffect(Unit) { meVm.refresh() }
            // Keep role in sync with the real account once /me returns.
            LaunchedEffect(me.role) {
                runCatching { Role.valueOf(me.role) }.getOrNull()?.let { role = it }
            }
            if (role == Role.DRIVER) {
                DriverHomeScreen(
                    driverVm,
                    userName = me.name.ifBlank { "Driver" },
                    onProfile = { nav.navigate(R.PROFILE) },
                    onIncomingOffer = { nav.navigate(R.DRIVER_OFFER) },
                )
            } else {
                PassengerHomeScreen(
                    userName = me.name.ifBlank { "there" },
                    onRequestRide = { nav.navigate(R.RIDE_REQUEST) },
                    onProfile = { nav.navigate(R.PROFILE) },
                    onStepUp = { nav.navigate(R.STEP_UP) },
                )
            }
        }

        // passenger
        composable(R.RIDE_REQUEST) {
            RideRequestScreen(rideVm, activity, onBack = { nav.popBackStack() }, onConfirmed = { id, m ->
                activeRideId = id; payment = PaymentMethod.valueOf(m); nav.navigate(R.TRACKING)
            })
        }
        composable(R.TRACKING) {
            RideTrackingScreen(
                rideId = activeRideId, vm = rideVm,
                onCancel = { nav.popBackStack(R.HOME, inclusive = false) },
                onCompleted = { m -> payment = PaymentMethod.valueOf(m); nav.navigate(R.TRIP_COMPLETE) },
            )
        }
        composable(R.TRIP_COMPLETE) {
            TripCompleteScreen(activeRideId, payment.name, rideVm,
                onDone = { nav.popBackStack(R.HOME, inclusive = false) })
        }
        composable(R.STEP_UP) {
            StepUpScreen(onResolved = { nav.popBackStack() })
        }

        // driver
        composable(R.DRIVER_OFFER) {
            DriverOfferScreen(
                driverVm, activity,
                onDecline = { nav.popBackStack() },
                onAccept = {
                    driverRideId = driverVm.state.value.activeRide?.id ?: 0L
                    nav.navigate(R.DRIVER_ACTIVE)
                },
            )
        }
        composable(R.DRIVER_ACTIVE) {
            DriverActiveRideScreen(driverVm, driverRideId,
                onFinished = { nav.popBackStack(R.HOME, inclusive = false) })
        }

        // shared
        composable(R.PROFILE) {
            val me by meVm.state.collectAsState()
            ProfileScreen(role = role, userName = me.name.ifBlank { "You" },
                onBack = { nav.popBackStack() }, onLogout = {
                    authRepo.logout()   // clear tokens so we don't auto-restore next launch
                    nav.navigate(R.SPLASH) { popUpTo(R.HOME) { inclusive = true } }
                })
        }
    }
}
