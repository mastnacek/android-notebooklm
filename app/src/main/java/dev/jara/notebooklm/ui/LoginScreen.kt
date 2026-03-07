package dev.jara.notebooklm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    onLogin: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Term.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "notebooklm",
            color = Term.green,
            fontFamily = Term.font,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "android client v0.2.0",
            color = Term.textDim,
            fontFamily = Term.font,
            fontSize = Term.fontSize,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Login button
        Text(
            text = "[ PRIHLASIT SE ]",
            color = Term.bg,
            fontFamily = Term.font,
            fontSize = Term.fontSizeLg,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLogin() }
                .background(Term.green)
                .padding(vertical = 14.dp),
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = Term.red,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                textAlign = TextAlign.Center,
            )
        }
    }
}
