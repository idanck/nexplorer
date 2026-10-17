package com.idanck.nexplorer.ui.net

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.data.net.CloudAuths
import com.idanck.nexplorer.data.net.CloudGrant
import com.idanck.nexplorer.data.net.GoogleAuth
import com.idanck.nexplorer.data.net.Pkce
import com.idanck.nexplorer.data.net.TokenStore
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import kotlinx.coroutines.flow.filterNotNull

/** ההתחברות לענן ב"הוסף מקום": [start] פותח את החלון של Google או את דף ההתחברות של Microsoft. */
class CloudSignIn internal constructor(private val begin: (NetProtocol) -> Unit) {
    fun start(protocol: NetProtocol) = begin(protocol)
}

/**
 * Google: `AuthorizationClient.authorize` — בחירת חשבון והסכמה בחלון של Google (כשצריך). Microsoft: דף ההתחברות
 * ב-Custom Tab, והקוד חוזר דרך [OAuthRedirectActivity]. [onGrant] — הצליח; [onFailed] — הודעה (null: בוטל).
 * ה-PKCE של התחברות שבאמצע שורד סיבוב מסך.
 */
@Composable
fun rememberCloudSignIn(onGrant: (CloudGrant) -> Unit, onFailed: (String?) -> Unit): CloudSignIn {
    val context = LocalContext.current
    val grant by rememberUpdatedState(onGrant)
    val failed by rememberUpdatedState(onFailed)
    var pending by rememberSaveable(stateSaver = PkceSaver) { mutableStateOf<Pkce?>(null) }

    val google = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            failed(null)
            return@rememberLauncherForActivityResult
        }
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data) }
            .onSuccess { r -> r.accessToken?.let { grant(CloudGrant.Google(it)) } ?: failed("Google לא החזיר הרשאה ל-Drive") }
            .onFailure { failed(googleError(it)) }
    }

    // החזרה מהדפדפן. בלי תשובה (הדף נסגר) — כשהמסך חוזר, ההתחברות בוטלה.
    LaunchedEffect(Unit) {
        OAuthRedirects.latest.filterNotNull().collect { uri ->
            OAuthRedirects.consume()
            val pkce = pending ?: return@collect
            pending = null
            val code = uri.getQueryParameter("code")
            when {
                uri.getQueryParameter("state") != pkce.state -> failed("תשובה לא צפויה מ-Microsoft — נסה שוב")
                code != null -> grant(CloudGrant.Microsoft(code, pkce, CloudAuths.microsoftRedirect))
                uri.getQueryParameter("error") == "access_denied" -> failed(null)
                else -> failed("Microsoft: ${uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error") ?: "ההתחברות נכשלה"}")
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pending != null && OAuthRedirects.latest.value == null) {
                pending = null
                failed(null)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return remember {
        CloudSignIn { protocol ->
            when (protocol) {
                NetProtocol.GoogleDrive -> Identity.getAuthorizationClient(context).authorize(GoogleAuth.request())
                    .addOnSuccessListener { r ->
                        val intent = r.pendingIntent
                        when {
                            r.hasResolution() && intent != null -> google.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                            r.accessToken != null -> grant(CloudGrant.Google(r.accessToken!!))
                            else -> failed("Google לא החזיר הרשאה ל-Drive")
                        }
                    }
                    .addOnFailureListener { failed(googleError(it)) }
                NetProtocol.OneDrive -> runCatching { CloudAuths.microsoft(NoTokens) }
                    .onFailure { failed(it.message) }
                    .onSuccess { auth ->
                        val pkce = Pkce.create()
                        OAuthRedirects.consume()
                        pending = pkce
                        if (!openBrowser(context, auth.authorizeUrl(CloudAuths.microsoftRedirect, pkce).toString())) {
                            pending = null
                            failed("אין במכשיר דפדפן לפתוח את דף ההתחברות של Microsoft")
                        }
                    }
                else -> failed("${protocol.label} אינו שירות ענן")
            }
        }
    }
}

/** בחירת השירות בפרטי החיבור: Google Drive או OneDrive. בעריכה — רק השירות של החיבור. */
@Composable
fun CloudServices(selected: NetProtocol, enabled: Boolean, onSelect: (NetProtocol) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
        CLOUD_SERVICES.forEach { (protocol, account) ->
            val chosen = protocol == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NexplorerDimens.ConnectFoundRow)
                    .border(
                        if (chosen) NexplorerDimens.ConnectTypeBorderSelected else NexplorerDimens.ConnectTypeBorder,
                        if (chosen) scheme.primary else scheme.outline,
                        NexplorerShape.ConnectField,
                    )
                    .background(if (chosen) scheme.primaryContainer else scheme.surface, NexplorerShape.ConnectField)
                    .selectable(selected = chosen, enabled = enabled || chosen, role = Role.RadioButton) { onSelect(protocol) }
                    .padding(horizontal = NexplorerDimens.ConnectFieldPadding),
            ) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = if (chosen) scheme.onPrimaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon))
                Column(Modifier.weight(1f)) {
                    Text(protocol.label, style = MaterialTheme.typography.bodyLarge, color = if (chosen) scheme.onPrimaryContainer else scheme.onSurface)
                    Text(account, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RadioButton(selected = chosen, onClick = null, enabled = enabled || chosen)
            }
        }
    }
}

/** החשבון של חיבור ענן בעריכה ("מחובר כ-…"). */
@Composable
fun CloudAccount(account: String) {
    Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1)) {
        Text("מחובר כ-", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(account, style = MonoTextStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
    }
}

private val CLOUD_SERVICES = listOf(
    NetProtocol.GoogleDrive to "חשבון Google",
    NetProtocol.OneDrive to "חשבון Microsoft אישי",
)

/** ההתחברות עצמה לא שומרת כלום — ה-refresh token נשמר רק אחרי שהחיבור נבדק. */
private object NoTokens : TokenStore {
    override fun get(): String? = null

    override fun put(token: String) = Unit
}

private val PkceSaver = Saver<Pkce?, List<String>>(
    save = { it?.let { p -> listOf(p.verifier, p.challenge, p.state) } ?: emptyList() },
    restore = { if (it.size == 3) Pkce(it[0], it[1], it[2]) else null },
)

private fun openBrowser(context: Context, url: String): Boolean = try {
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** DEVELOPER_ERROR — האפליקציה לא רשומה בפרויקט ב-Google Cloud (שם החבילה וטביעת ה-SHA-1 של מפתח החתימה). */
private fun googleError(e: Throwable): String? {
    val status = (e as? ApiException)?.statusCode
    return when (status) {
        CommonStatusCodes.CANCELED -> null
        CommonStatusCodes.DEVELOPER_ERROR -> "Google לא מכיר את האפליקציה — צריך לקוח Android בפרויקט ב-Google Cloud (שם החבילה וטביעת ה-SHA-1)"
        CommonStatusCodes.NETWORK_ERROR -> "אין חיבור לאינטרנט"
        CommonStatusCodes.SIGN_IN_REQUIRED -> "אין חשבון Google במכשיר — הוסף אחד בהגדרות"
        null -> e.message ?: "ההתחברות ל-Google נכשלה"
        else -> "ההתחברות ל-Google נכשלה (${CommonStatusCodes.getStatusCodeString(status)})"
    }
}
