package app.lanceur.builtin.note

import app.lanceur.builtin.WidgetData

object NoteData {
    fun text(data: String?): String = WidgetData.decode(data)["text"].orEmpty()

    fun of(text: String): String = WidgetData.encode(mapOf("text" to text))
}
