package app.lanceur.builtin.todo

import app.lanceur.builtin.WidgetData

data class TodoItem(val id: Long, val text: String, val done: Boolean)

data class TodoList(val items: List<TodoItem> = emptyList()) {
    /** À faire dans l'ordre d'ajout, puis les tâches faites. */
    val visible: List<TodoItem> get() = items.filterNot { it.done } + items.filter { it.done }

    val hasDone: Boolean get() = items.any { it.done }

    fun add(text: String): TodoList {
        val clean = text.trim()
        if (clean.isEmpty()) return this
        return copy(items = items + TodoItem((items.maxOfOrNull { it.id } ?: 0) + 1, clean, done = false))
    }

    fun toggle(id: Long) = copy(items = items.map { if (it.id == id) it.copy(done = !it.done) else it })

    fun remove(id: Long) = copy(items = items.filterNot { it.id == id })

    fun clearDone() = copy(items = items.filterNot { it.done })

    /** Chaque tâche : `id|0 ou 1|texte` (le texte peut contenir `|`). */
    fun toData(): String =
        WidgetData.encode(mapOf("items" to WidgetData.list(items.map { "${it.id}|${if (it.done) 1 else 0}|${it.text}" })))

    companion object {
        fun fromData(data: String?): TodoList = TodoList(
            WidgetData.unlist(WidgetData.decode(data)["items"]).mapNotNull { entry ->
                val parts = entry.split('|', limit = 3)
                val id = parts.getOrNull(0)?.toLongOrNull()
                if (parts.size == 3 && id != null) TodoItem(id, parts[2], parts[1] == "1") else null
            },
        )
    }
}
