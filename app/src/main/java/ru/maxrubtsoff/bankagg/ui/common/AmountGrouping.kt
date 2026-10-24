package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import ru.maxrubtsoff.bankagg.domain.model.AmountInputFormat

/** Shows a typed amount as "1 000 000" while the field keeps plain digits. */
object AmountGrouping : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        return TransformedText(
            AnnotatedString(AmountInputFormat.format(raw)),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = AmountInputFormat.toShown(raw, offset)
                override fun transformedToOriginal(offset: Int): Int = AmountInputFormat.toRaw(raw, offset)
            }
        )
    }
}
