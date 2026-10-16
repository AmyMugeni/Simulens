package com.cognilens.app.ui.profile

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cognilens.app.data.local.CogniLensDatabase
import com.cognilens.app.data.remote.RetrofitClient
import com.cognilens.app.data.repositories.ImportState
import com.cognilens.app.data.repositories.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class TimetableImportViewModel(
    private val repository: TimetableRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    val entryCount: StateFlow<Int> = repository.savedTimetable
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun clearTimetable(onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearTimetable()
            launch(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun uploadAndParsePdf(uri: Uri, context: Context, onCompletionSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = ImportState.Loading
            val contentResolver = context.contentResolver

            try {
                val fileName = getFileName(uri, contentResolver) ?: "timetable.pdf"
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _state.value = ImportState.Error("Unable to open selected document stream.")
                    return@launch
                }
                val fileBytes = inputStream.readBytes()
                inputStream.close()

                val requestFile = fileBytes.toRequestBody("application/pdf".toMediaTypeOrNull(), 0, fileBytes.size)
                val bodyPart = MultipartBody.Part.createFormData("file", fileName, requestFile)

                val result = repository.uploadAndSaveTimetable(bodyPart)

                result.fold(
                    onSuccess = {
                        _state.value = ImportState.Success
                        launch(Dispatchers.Main) {
                            onCompletionSuccess()
                        }
                    },
                    onFailure = { throwable ->
                        _state.value = ImportState.Error(throwable.message ?: "Server execution error encountered.")
                    }
                )

            } catch (e: Exception) {
                _state.value = ImportState.Error("Local system error: ${e.localizedMessage}")
            }
        }
    }

    private fun getFileName(uri: Uri, contentResolver: ContentResolver): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) name = it.getString(nameIndex)
                }
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = CogniLensDatabase.getDatabase(context.applicationContext)
                val apiService = RetrofitClient.timetableApiService
                val repository = TimetableRepository(apiService, database.timetableDao())
                return TimetableImportViewModel(repository) as T
            }
        }
    }
}
