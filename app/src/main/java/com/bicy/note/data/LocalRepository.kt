package com.bicy.note.data

import androidx.compose.runtime.staticCompositionLocalOf

val LocalRepository = staticCompositionLocalOf<NoteRepository> {
    error("NoteRepository 未初始化")
}