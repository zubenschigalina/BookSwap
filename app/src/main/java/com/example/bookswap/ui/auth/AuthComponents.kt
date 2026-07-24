package com.example.bookswap.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookswap.R
import kotlinx.coroutines.launch

/** "Sign in with Google" button that runs the Credential Manager flow. */
@Composable
fun GoogleSignInButton(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webClientId = stringResource(R.string.default_web_client_id)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OutlinedButton(
        onClick = {
            scope.launch {
                viewModel.setLoading(true)
                try {
                    val idToken = requestGoogleIdToken(context, webClientId)
                    viewModel.signInWithGoogle(idToken)
                } catch (e: GetCredentialCancellationException) {
                    viewModel.setLoading(false) // user backed out — no error banner
                } catch (e: GetCredentialException) {
                    viewModel.onExternalError(e.localizedMessage ?: "Google Sign-In failed.")
                }
            }
        },
        enabled = !uiState.isLoading,
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(stringResource(R.string.auth_continue_google))
    }
}

@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.auth_or),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
