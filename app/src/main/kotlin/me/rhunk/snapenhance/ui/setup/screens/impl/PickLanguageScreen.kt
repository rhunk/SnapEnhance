package me.rhunk.snapenhance.ui.setup.screens.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.ui.util.ObservableMutableState
import me.rhunk.snapenhance.ui.setup.screens.SetupScreen
import java.util.Locale


class PickLanguageScreen : SetupScreen(){
    @Composable
    override fun Content() {
        val androidContext = LocalContext.current
        val availableLocales = remember { LocaleWrapper.fetchAvailableLocales(androidContext) }

        allowNext(true)

        fun getLocaleDisplayName(locale: String): String {
            locale.split("_").let {
                return Locale(it[0], it[1]).getDisplayName(Locale.getDefault())
            }
        }

        val selectedLocale = remember {
            val deviceLocale = Locale.getDefault().toString()
            fun reloadTranslation(selectedLocale: String) {
                context.translation.reloadFromContext(androidContext, selectedLocale)
            }
            ObservableMutableState(
                defaultValue = availableLocales.firstOrNull {
                        locale -> locale == deviceLocale
                } ?: LocaleWrapper.DEFAULT_LOCALE
            ) { _, newValue ->
                context.config.locale = newValue
                context.config.writeConfig()
                reloadTranslation(newValue)
            }.also { reloadTranslation(it.value) }
        }

        DialogText(text = context.translation["setup.dialogs.select_language"])

        val isDialog = remember { mutableStateOf(false) }

        if (isDialog.value) {
            Dialog(onDismissRequest = { isDialog.value = false }) {
                Surface(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    elevation = 8.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    LazyColumn(
                        modifier = Modifier.scrollable(rememberScrollState(), orientation = Orientation.Vertical)
                    ) {
                        items(availableLocales) { locale ->
                            Box(
                                modifier = Modifier
                                    .height(70.dp)
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedLocale.value = locale
                                        isDialog.value = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = getLocaleDisplayName(locale),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Light,
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(top = 40.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            OutlinedButton(onClick = {
                isDialog.value = true
            }) {
                Text(text = getLocaleDisplayName(selectedLocale.value), fontSize = 16.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}