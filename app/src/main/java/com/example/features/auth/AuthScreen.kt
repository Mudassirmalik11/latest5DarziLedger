package com.example.features.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ui.AtelierCard
import com.example.data.remote.AuthSessionProvider
import com.example.data.remote.EmailAuthResult
import com.example.data.remote.FirebaseAuthSessionProvider
import com.example.data.remote.GoogleSignInResult
import kotlinx.coroutines.launch

private enum class AuthTabMode {
  SIGN_IN,
  SIGN_UP,
}

@Composable
fun AuthScreen(
  authSessionProvider: AuthSessionProvider,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var mode by rememberSaveable { mutableStateOf(AuthTabMode.SIGN_IN) }
  var displayName by rememberSaveable { mutableStateOf("") }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }

  var isEmailLoading by remember { mutableStateOf(false) }
  var isGoogleLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var infoMessage by remember { mutableStateOf<String?>(null) }

  val isBusy = isEmailLoading || isGoogleLoading

  LaunchedEffect(Unit) {
    authSessionProvider.attemptAutoSignIn(context)
  }

  val submitEmailAuth: () -> Unit = submitEmail@{
    if (isBusy) return@submitEmail
    errorMessage = null
    infoMessage = null

    val trimmedEmail = email.trim()
    if (!FirebaseAuthSessionProvider.isValidEmail(trimmedEmail)) {
      errorMessage = context.getString(R.string.auth_error_invalid_email)
      return@submitEmail
    }
    if (mode == AuthTabMode.SIGN_IN) {
      if (password.isEmpty()) {
        errorMessage = context.getString(R.string.auth_error_empty_password)
        return@submitEmail
      }
      isEmailLoading = true
      scope.launch {
        when (val res = authSessionProvider.signInWithEmailPassword(trimmedEmail, password)) {
          is EmailAuthResult.Success -> isEmailLoading = false
          is EmailAuthResult.Error -> {
            isEmailLoading = false
            errorMessage = res.message
          }
        }
      }
    } else {
      if (password.length < 6) {
        errorMessage = context.getString(R.string.auth_error_short_password)
        return@submitEmail
      }
      if (password != confirmPassword) {
        errorMessage = context.getString(R.string.auth_error_passwords_mismatch)
        return@submitEmail
      }
      isEmailLoading = true
      scope.launch {
        when (
          val res = authSessionProvider.signUpWithEmailPassword(
            displayName = displayName.trim(),
            email = trimmedEmail,
            password = password,
          )
        ) {
          is EmailAuthResult.Success -> isEmailLoading = false
          is EmailAuthResult.Error -> {
            isEmailLoading = false
            errorMessage = res.message
          }
        }
      }
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("auth_screen"),
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp),
      contentAlignment = Alignment.Center,
    ) {
      AtelierCard(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 460.dp),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.ContentCut,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          Text(
            text = stringResource(R.string.auth_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )

          Text(
            text = stringResource(R.string.auth_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              modifier = Modifier.weight(1f),
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Devices,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = stringResource(R.string.auth_badge_single_device),
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }

            Surface(
              modifier = Modifier.weight(1f),
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.VerifiedUser,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = stringResource(R.string.auth_badge_shop_roles),
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }

          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val tabs = listOf(
              AuthTabMode.SIGN_IN to stringResource(R.string.auth_tab_sign_in),
              AuthTabMode.SIGN_UP to stringResource(R.string.auth_tab_sign_up),
            )
            tabs.forEachIndexed { index, (tabMode, label) ->
              SegmentedButton(
                selected = mode == tabMode,
                onClick = {
                  if (!isBusy && mode != tabMode) {
                    mode = tabMode
                    errorMessage = null
                    infoMessage = null
                  }
                },
                enabled = !isBusy,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag(
                    if (tabMode == AuthTabMode.SIGN_IN) "auth_mode_sign_in" else "auth_mode_sign_up",
                  ),
              ) {
                Text(label)
              }
            }
          }

          AnimatedVisibility(visible = mode == AuthTabMode.SIGN_UP) {
            OutlinedTextField(
              value = displayName,
              onValueChange = { displayName = it },
              label = { Text(stringResource(R.string.auth_name_label)) },
              leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
              singleLine = true,
              enabled = !isBusy,
              keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_name_input"),
            )
          }

          OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.auth_email_label)) },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Email,
              imeAction = ImeAction.Next,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("auth_email_input"),
          )

          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password_label)) },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
              IconButton(
                onClick = { passwordVisible = !passwordVisible },
                modifier = Modifier.size(48.dp),
              ) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = stringResource(
                    if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                  ),
                )
              }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              imeAction = if (mode == AuthTabMode.SIGN_IN) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
              onDone = {
                if (mode == AuthTabMode.SIGN_IN) submitEmailAuth()
              },
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("auth_password_input"),
          )

          AnimatedVisibility(visible = mode == AuthTabMode.SIGN_UP) {
            OutlinedTextField(
              value = confirmPassword,
              onValueChange = { confirmPassword = it },
              label = { Text(stringResource(R.string.auth_confirm_password_label)) },
              leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
              visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              enabled = !isBusy,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
              ),
              keyboardActions = KeyboardActions(
                onDone = { submitEmailAuth() },
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_confirm_password_input"),
            )
          }

          if (mode == AuthTabMode.SIGN_IN) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
              TextButton(
                onClick = {
                  errorMessage = null
                  infoMessage = null
                  val trimmedEmail = email.trim()
                  if (!FirebaseAuthSessionProvider.isValidEmail(trimmedEmail)) {
                    errorMessage = context.getString(R.string.auth_error_reset_email_required)
                    return@TextButton
                  }
                  isEmailLoading = true
                  scope.launch {
                    authSessionProvider.sendPasswordResetEmail(trimmedEmail).fold(
                      onSuccess = {
                        isEmailLoading = false
                        infoMessage = context.getString(R.string.auth_info_reset_sent, trimmedEmail)
                      },
                      onFailure = { err ->
                        isEmailLoading = false
                        errorMessage = err.message ?: context.getString(R.string.auth_error_reset_failed)
                      },
                    )
                  }
                },
                enabled = !isBusy,
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("auth_forgot_password_button"),
              ) {
                Text(stringResource(R.string.auth_forgot_password))
              }
            }
          }

          if (!errorMessage.isNullOrBlank()) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.errorContainer,
            ) {
              Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                  .padding(12.dp)
                  .testTag("auth_error_banner"),
              )
            }
          }

          if (!infoMessage.isNullOrBlank()) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Text(
                text = infoMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                  .padding(12.dp)
                  .testTag("auth_info_banner"),
              )
            }
          }

          Button(
            onClick = submitEmailAuth,
            enabled = !isBusy,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("auth_email_submit_button"),
          ) {
            if (isEmailLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
              )
            } else {
              Text(
                stringResource(
                  if (mode == AuthTabMode.SIGN_IN) R.string.auth_sign_in_email
                  else R.string.auth_sign_up_email,
                ),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            HorizontalDivider(
              modifier = Modifier.weight(1f),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
              text = stringResource(R.string.auth_or_divider),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(
              modifier = Modifier.weight(1f),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
          }

          OutlinedButton(
            onClick = {
              errorMessage = null
              infoMessage = null
              isGoogleLoading = true
              scope.launch {
                when (val res = authSessionProvider.signInWithGoogle(context)) {
                  is GoogleSignInResult.Success -> isGoogleLoading = false
                  is GoogleSignInResult.Cancelled -> isGoogleLoading = false
                  is GoogleSignInResult.Error -> {
                    isGoogleLoading = false
                    errorMessage = res.message
                  }
                }
              }
            },
            enabled = !isBusy,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("google_sign_in_button"),
          ) {
            if (isGoogleLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier
                  .padding(end = 8.dp)
                  .size(20.dp),
              )
              Text(stringResource(R.string.auth_sign_in_google))
            }
          }

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = stringResource(R.string.auth_offline_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}
