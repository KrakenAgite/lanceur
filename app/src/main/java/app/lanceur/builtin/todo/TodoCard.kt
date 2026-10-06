package app.lanceur.builtin.todo

import app.lanceur.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground

@Composable
fun TodoCard(list: TodoList, onChange: (TodoList) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(cardBackground()).padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(tr("À faire", "To do"), Modifier.weight(1f))
            if (list.hasDone) {
                TextButton(onClick = { onChange(list.clearDone()) }) {
                    Text(tr("Effacer les tâches faites", "Clear done tasks"), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            list.visible.forEach { item ->
                key(item.id) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(list.toggle(item.id)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.done, onCheckedChange = { onChange(list.toggle(item.id)) })
                        Text(
                            item.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.done) colors.onSurfaceVariant else colors.onSurface,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onChange(list.remove(item.id)) }) {
                            Icon(Icons.Default.Clear, contentDescription = tr("Supprimer ", "Delete ") + item.text, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().testTag("todo-new"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onChange(list.add(draft))
                            draft = ""
                        }),
                    )
                    if (draft.isEmpty()) Text(tr("Ajouter une tâche…", "Add a task…"), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
