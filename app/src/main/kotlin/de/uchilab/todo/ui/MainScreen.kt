package de.uchilab.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.uchilab.todo.R

/** Empty screen with the working title; the app has no function yet. */
@androidx.compose.runtime.Composable
fun MainScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        }
    }
}
