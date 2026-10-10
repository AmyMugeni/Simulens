package com.cognilens.app.data.repositories

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    object Success : ImportState()
    data class Error(val message: String) : ImportState()
}
