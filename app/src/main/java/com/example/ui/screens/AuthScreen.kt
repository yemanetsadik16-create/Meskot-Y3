package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private enum class AuthTab { SIGN_IN, CREATE_ACCOUNT }

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DAYS = (1..31).map { it.toString() }
private val YEARS = (2012 downTo 1940).map { it.toString() }
private val GENDERS = listOf("Female", "Male", "Custom")

private fun isStrongPassword(pw: String): Boolean {
    if (pw.length !in 7..49) return false
    val hasUpper = pw.any { it.isUpperCase() }
    val hasLower = pw.any { it.isLowerCase() }
    val hasDigit = pw.any { it.isDigit() }
    val hasSymbol = pw.any { !it.isLetterOrDigit() }
    return hasUpper && hasLower && hasDigit && hasSymbol
}

@Composable
fun AuthScreen(
    lang: AppLang,
    onToggleLang: () -> Unit,
    onPendingProfileSet: (displayName: String, location: String) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(AuthTab.SIGN_IN) }

    // Sign-in & shared fields
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Create-account fields matching the HTML design
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var birthMonth by remember { mutableStateOf("Jan") }
    var birthDay by remember { mutableStateOf("1") }
    var birthYear by remember { mutableStateOf("2000") }
    var selectedGender by remember { mutableStateOf("Female") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }

    val orangeGradient = Brush.horizontalGradient(listOf(Color(0xFFF55805), Color(0xFFD64003)))
    val bannerGradient = Brush.verticalGradient(listOf(Color(0xFFF05105), Color(0xFFC23B02)))

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedPlaceholderColor = Color(0xFF7D808D),
        unfocusedPlaceholderColor = Color(0xFF7D808D),
        focusedBorderColor = AuthOrange,
        unfocusedBorderColor = Color.White.copy(alpha = 0.72f),
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.92f),
        focusedContainerColor = Color(0xFF1E2027),
        unfocusedContainerColor = Color(0xFF1E2027),
        cursorColor = AuthOrange
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111116))
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .widthIn(max = 500.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Framed dark rounded card with subtle ember-orange border matching the HTML screenshots
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF1A1B21))
                    .border(1.dp, AuthOrange.copy(alpha = 0.45f), RoundedCornerShape(26.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top slanted ember-orange header banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (activeTab == AuthTab.SIGN_IN) 205.dp else 215.dp)
                        .drawBehind {
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                if (activeTab == AuthTab.SIGN_IN) {
                                    // Slants up from left to right (matches Sign in screenshot)
                                    lineTo(size.width, size.height * 0.72f)
                                    lineTo(0f, size.height)
                                } else {
                                    // Slants down from left to right (matches Create account screenshot)
                                    lineTo(size.width, size.height)
                                    lineTo(0f, size.height * 0.74f)
                                }
                                close()
                            }
                            drawPath(path = path, brush = bannerGradient)
                        }
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    // Compact EN/አማ pill top-right
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clickable { onToggleLang() }
                            .testTag("auth_lang_toggle")
                    ) {
                        Text(
                            text = if (lang == AppLang.EN) "EN · አማ" else "አማ · EN",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (activeTab == AuthTab.SIGN_IN) {
                                if (lang == AppLang.AM) "እንኳን ደህና መጡ!" else "WELCOME BACK!"
                            } else {
                                if (lang == AppLang.AM) "የመስኮት አካል ይሁኑ!" else "BE PART OF MESKOT!"
                            },
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (activeTab == AuthTab.SIGN_IN) {
                                if (lang == AppLang.AM) {
                                    "ከማህበረሰብዎ ጋር ይገናኙ፣ እድሎችን ያግኙ እና ታሪኮችዎን በመስኮት ያጋሩ።"
                                } else {
                                    "Connect with your community, explore opportunities, and share your stories on Meskot."
                                }
                            } else {
                                if (lang == AppLang.AM) {
                                    "ቀዳሚውን የሐበሻ ማህበረሰብ መድረክ ይቀላቀሉ እና ዛሬ ወደ ማህበረሰቡ የሚያዩበትን መስኮት ይፍጠሩ።"
                                } else {
                                    "Join the premier Habesha community platform and create your window onto the community today."
                                }
                            },
                            color = Color.White.copy(alpha = 0.94f),
                            fontSize = 14.5.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }

                if (activeTab == AuthTab.SIGN_IN) {
                    // ==================== SIGN IN VIEW ====================
                    // Glowing Meskot M logo
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(AuthOrange.copy(alpha = 0.45f), Color.Transparent),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = size.width * 0.65f
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.meskot_launcher_icon_1795099216867),
                            contentDescription = "Meskot Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(2.dp, AuthOrange, RoundedCornerShape(22.dp))
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Meskot",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (lang == AppLang.AM) {
                            "መስኮት · የኢትዮጵያ እና የሐበሻ ማህበረሰብ አውታረ መረብ"
                        } else {
                            "Meskot · Ethiopian & Habesha Community Network"
                        },
                        color = AuthMuted,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(Modifier.height(20.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pill Segmented Switcher: Sign in | Create account
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .background(Color(0xFF1F2028))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AuthSegmentButton(
                                label = if (lang == AppLang.AM) "ግባ" else "Sign in",
                                selected = true,
                                gradient = orangeGradient,
                                onClick = { },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("auth_tab_signin")
                            )
                            AuthSegmentButton(
                                label = if (lang == AppLang.AM) "አካውንት ፍጠር" else "Create account",
                                selected = false,
                                gradient = orangeGradient,
                                onClick = {
                                    activeTab = AuthTab.CREATE_ACCOUNT
                                    errorMsg = null
                                    infoMsg = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("auth_tab_create")
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        AuthFeedbackBanners(errorMsg = errorMsg, infoMsg = infoMsg)

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMsg = null; infoMsg = null },
                            label = { Text(MeskotStrings.t(lang, "email")) },
                            placeholder = { Text(if (lang == AppLang.AM) "ኢሜይልዎን ያስገቡ" else "Enter your email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMsg = null },
                            label = { Text(MeskotStrings.t(lang, "password")) },
                            placeholder = { Text(if (lang == AppLang.AM) "የይለፍ ቃልዎን ያስገቡ" else "Enter your password") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = Color.White.copy(alpha = 0.78f)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val targetEmail = email.trim()
                                    if (targetEmail.isBlank()) {
                                        errorMsg = if (lang == AppLang.AM) "እባክዎ መጀመሪያ ኢሜይልዎን ያስገቡ።" else "Please enter your email address first."
                                        return@TextButton
                                    }
                                    errorMsg = null
                                    infoMsg = null
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            Firebase.auth.sendPasswordResetEmail(targetEmail).await()
                                            infoMsg = MeskotStrings.t(lang, "resetEmailSent")
                                        } catch (e: Exception) {
                                            errorMsg = e.message ?: "Could not send password reset email."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("auth_forgot_password")
                            ) {
                                Text(
                                    text = MeskotStrings.t(lang, "forgotPassword"),
                                    color = Color.White.copy(alpha = 0.86f),
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Full-width ember-orange Sign in pill button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(orangeGradient)
                                .clickable(enabled = !isLoading) {
                                    val cleanEmail = email.trim()
                                    if (cleanEmail.isBlank() || password.isBlank()) {
                                        errorMsg = if (lang == AppLang.AM) "እባክዎ ኢሜይል እና የይለፍ ቃል ያስገቡ።" else "Please enter your email and password."
                                        return@clickable
                                    }
                                    errorMsg = null
                                    infoMsg = null
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            onPendingProfileSet("", "")
                                            Firebase.auth.signInWithEmailAndPassword(cleanEmail, password).await()
                                        } catch (e: Exception) {
                                            errorMsg = e.message ?: "Sign in failed."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                                .testTag("auth_submit_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                            } else {
                                Text(
                                    text = if (lang == AppLang.AM) "ግባ" else "Sign in",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // "--- or ---" divider matching screenshot
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.14f))
                            Text(
                                text = if (lang == AppLang.AM) "ወይም" else "or",
                                color = AuthMuted,
                                fontSize = 13.5.sp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.14f))
                        }

                        Spacer(Modifier.height(18.dp))

                        // Outlined "Create account" pill button matching screenshot
                        OutlinedButton(
                            onClick = {
                                activeTab = AuthTab.CREATE_ACCOUNT
                                errorMsg = null
                                infoMsg = null
                            },
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("auth_switch_to_create_button")
                        ) {
                            Text(
                                text = if (lang == AppLang.AM) "አካውንት ፍጠር" else "Create account",
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(Modifier.height(26.dp))
                    }
                } else {
                    // ==================== CREATE ACCOUNT VIEW ====================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = MeskotStrings.t(lang, "joinTitle"),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = MeskotStrings.t(lang, "joinSub"),
                            color = AuthMuted,
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.height(18.dp))

                        AuthFeedbackBanners(errorMsg = errorMsg, infoMsg = infoMsg)

                        // First name | Last name side-by-side
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it; errorMsg = null },
                                label = { Text(if (lang == AppLang.AM) "ስም" else "First name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text(if (lang == AppLang.AM) "ስም" else "First name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_first_name_input")
                            )
                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { lastName = it; errorMsg = null },
                                label = { Text(if (lang == AppLang.AM) "የአባት ስም" else "Last name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text(if (lang == AppLang.AM) "የአባት ስም" else "Last name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_last_name_input")
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMsg = null },
                            label = { Text(MeskotStrings.t(lang, "email")) },
                            placeholder = { Text(if (lang == AppLang.AM) "ኢሜይል አድራሻ ያስገቡ" else "Enter email address") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMsg = null },
                            label = { Text(MeskotStrings.t(lang, "password")) },
                            placeholder = { Text(if (lang == AppLang.AM) "የይለፍ ቃል ይፍጠሩ" else "Create password") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = Color.White.copy(alpha = 0.78f)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; errorMsg = null },
                            label = { Text(if (lang == AppLang.AM) "የይለፍ ቃል ያረጋግጡ" else "Confirm password") },
                            placeholder = { Text(if (lang == AppLang.AM) "የይለፍ ቃል ያረጋግጡ" else "Confirm password") },
                            singleLine = true,
                            visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                                    Icon(
                                        imageVector = if (showConfirmPassword) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = "Toggle confirm password visibility",
                                        tint = Color.White.copy(alpha = 0.78f)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_confirm_password_input")
                        )

                        Spacer(Modifier.height(18.dp))

                        // Birthday header + info badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (lang == AppLang.AM) "የልደት ቀን" else "Birthday",
                                color = Color.White,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3A3C4A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("i", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (lang == AppLang.AM) {
                                "የልደት ቀንዎን ማስገባት ተሞክሮዎን ለማሻሻል ይረዳል።"
                            } else {
                                "Providing your birthday helps personalize your experience."
                            },
                            color = AuthMuted,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        // Month | Day | Year dropdowns
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AuthDropdownBox(
                                value = birthMonth,
                                options = MONTHS,
                                onSelected = { birthMonth = it },
                                modifier = Modifier.weight(1.1f)
                            )
                            AuthDropdownBox(
                                value = birthDay,
                                options = DAYS,
                                onSelected = { birthDay = it },
                                modifier = Modifier.weight(0.9f)
                            )
                            AuthDropdownBox(
                                value = birthYear,
                                options = YEARS,
                                onSelected = { birthYear = it },
                                modifier = Modifier.weight(1.1f)
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        // Gender selector
                        Text(
                            text = if (lang == AppLang.AM) "ጾታ" else "Gender",
                            color = Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GENDERS.forEach { gender ->
                                val isSelected = selectedGender == gender
                                val label = when (gender) {
                                    "Female" -> if (lang == AppLang.AM) "ሴት" else "Female"
                                    "Male" -> if (lang == AppLang.AM) "ወንድ" else "Male"
                                    else -> if (lang == AppLang.AM) "ሌላ" else "Custom"
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) AuthOrange.copy(alpha = 0.08f) else Color(0xFF1E2027))
                                        .border(
                                            width = 1.2.dp,
                                            color = if (isSelected) AuthOrange else Color.White.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { selectedGender = gender }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) AuthOrange else Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = 2.dp,
                                                color = if (isSelected) AuthOrange else Color.White.copy(alpha = 0.8f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(AuthOrange)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = MeskotStrings.t(lang, "pwHint"),
                            color = AuthMuted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (lang == AppLang.AM) {
                                "አካውንት ፍጠር የሚለውን ሲጫኑ በውሎቻችን፣ በግላዊነት ፖሊሲያችን እና በማህበረሰብ መመሪያዎቻችን ይስማማሉ።"
                            } else {
                                "By clicking Sign Up, you agree to our Terms, Privacy Policy and Community Standards."
                            },
                            color = AuthMuted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(18.dp))

                        // Full-width ember-orange Create account pill button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(orangeGradient)
                                .clickable(enabled = !isLoading) {
                                    val fn = firstName.trim()
                                    val ln = lastName.trim()
                                    val cleanEmail = email.trim()
                                    if (fn.isBlank() || ln.isBlank()) {
                                        errorMsg = if (lang == AppLang.AM) "እባክዎ ስም እና የአባት ስም ያስገቡ።" else "Please enter your first and last name."
                                        return@clickable
                                    }
                                    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                        errorMsg = if (lang == AppLang.AM) "እባክዎ ትክክለኛ ኢሜይል አድራሻ ያስገቡ።" else "Please enter a valid email address."
                                        return@clickable
                                    }
                                    if (!isStrongPassword(password)) {
                                        errorMsg = MeskotStrings.t(lang, "pwHint")
                                        return@clickable
                                    }
                                    if (password != confirmPassword) {
                                        errorMsg = if (lang == AppLang.AM) "የይለፍ ቃሎቹ አይዛመዱም።" else "Passwords do not match."
                                        return@clickable
                                    }
                                    errorMsg = null
                                    infoMsg = null
                                    isLoading = true
                                    val fullNameCombined = "$fn $ln"
                                    scope.launch {
                                        try {
                                            onPendingProfileSet(fullNameCombined, "")
                                            val result = Firebase.auth
                                                .createUserWithEmailAndPassword(cleanEmail, password)
                                                .await()
                                            result.user?.updateProfile(
                                                userProfileChangeRequest { displayName = fullNameCombined }
                                            )?.await()
                                        } catch (e: Exception) {
                                            errorMsg = e.message ?: "Account creation failed."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                                .testTag("auth_submit_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                            } else {
                                Text(
                                    text = if (lang == AppLang.AM) "አካውንት ፍጠር" else "Create account",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // "--- Already have an account? Log in ---" footer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(0.5f), color = Color.White.copy(alpha = 0.14f))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = MeskotStrings.t(lang, "hasAccount") + " ",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = MeskotStrings.t(lang, "logInLink"),
                                color = AuthOrange,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        activeTab = AuthTab.SIGN_IN
                                        errorMsg = null
                                        infoMsg = null
                                    }
                                    .testTag("auth_back_to_login")
                            )
                            Spacer(Modifier.width(10.dp))
                            HorizontalDivider(modifier = Modifier.weight(0.5f), color = Color.White.copy(alpha = 0.14f))
                        }

                        Spacer(Modifier.height(26.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthFeedbackBanners(errorMsg: String?, infoMsg: String?) {
    errorMsg?.let { err ->
        Surface(
            color = MeskotCross.copy(alpha = 0.22f),
            border = BorderStroke(1.dp, MeskotCross.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = err,
                color = Color(0xFFFFB4BC),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    infoMsg?.let { ok ->
        Surface(
            color = MeskotAcceptGreen.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, MeskotAcceptGreen.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = ok,
                color = Color(0xFFB7F4C9),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun AuthDropdownBox(
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E2027))
                .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 260.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthSegmentButton(
    label: String,
    selected: Boolean,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(gradient) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else AuthMuted,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
