package ru.maxrubtsoff.bankagg.ui.settings

import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BrandDecor
import ru.maxrubtsoff.bankagg.ui.common.BrandDecorEditor
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.rememberBrandDecorState
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * An own bank (F-22): the name, and the logo as up to three letters on a chosen
 * colour, or a picture from the gallery with its plain background cut away. The
 * look is edited by the same editor as a subscription's (F-33).
 */
@Composable
fun BankEditorScreen(vm: BanksViewModel, existing: BankBrand?, initialName: String, onBack: () -> Unit, onDone: (BankBrand) -> Unit) {
    val look = rememberBrandDecorState(
        initialTitle = existing?.title ?: initialName,
        initialDecor = existing?.decor() ?: BrandDecor.defaultFor(initialName),
        touched = existing != null
    )

    Column(
        Modifier.fillMaxSize().background(Slitok.Bg).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(if (existing == null) "Свой банк" else "Банк", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BrandDecorEditor(look, CircleShape)
        }
        BrassButton(
            onClick = { vm.saveOwn(existing, look.title, look.decor(), look.picked, look.usePicture, onDone) },
            enabled = look.title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) { Text("Сохранить") }
    }
}
