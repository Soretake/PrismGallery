package com.example.amoledgallery.ui.features

import android.content.Context
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.editor.EditSpec
import com.example.amoledgallery.ui.editor.Looks
import com.example.amoledgallery.ui.editor.MediaEditor
import com.example.amoledgallery.ui.editor.NormalizedRect
import com.example.amoledgallery.ui.i18n.F
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "Copy edits" / "Paste edits": the colour look and the watermark of one edit, applied to other photos. The
 * crop, rotation and drawings belong to their photo and are not copied.
 */
object EditClipboard {
    var spec: EditSpec? = null
        private set

    fun copy(looks: Looks, watermark: String) {
        spec = EditSpec(0, false, false, NormalizedRect.Full, looks = looks, watermark = watermark.takeIf { it.isNotBlank() })
    }

    /** Saves an edited copy of each photo next to its original. */
    fun pasteOnto(context: Context, items: List<MediaItem>, scope: CoroutineScope) {
        val edit = spec ?: return
        val env = Features.env
        scope.launch {
            var done = 0
            items.forEach { item ->
                if (runCatching { MediaEditor.saveImage(context, item, edit) }.isSuccess) done++
            }
            env?.vm?.message(F.editsPasted(done))
            env?.vm?.clearSelection()
            env?.vm?.refreshAfterEdit()
        }
    }
}
