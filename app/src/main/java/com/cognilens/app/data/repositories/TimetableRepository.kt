package com.cognilens.app.data.repositories

import com.cognilens.app.data.local.dao.TimetableDao
import com.cognilens.app.data.local.entity.TimetableEntry
import com.cognilens.app.data.remote.TimetableApiService
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody

class TimetableRepository(
    private val apiService: TimetableApiService,
    private val timetableDao: TimetableDao
) {
    // Expose the live room stream to feed directly into your Composable layouts
    val savedTimetable: Flow<List<TimetableEntry>> = timetableDao.getTimetableFlow()

    suspend fun uploadAndSaveTimetable(filePart: MultipartBody.Part): Result<String> {
        return try {
            val response = apiService.uploadTimetable(filePart)
            if (response.isSuccessful && response.body()?.status == "success") {
                val apiData = response.body()!!

                // Map the JSON mapping elements straight into Room structure
                val dbEntries = apiData.timetable.map { map ->
                    TimetableEntry(
                        day = map["day"] ?: "Unknown",
                        slot1 = map["8.15 - 9.15"] ?: "-",
                        slot2 = map["9.15 – 10.15"] ?: "-",
                        slot3 = map["10.15 – 11.15"] ?: "-",
                        slot4 = map["11.15 – 12.15"] ?: "-",
                        slot5 = map["12.15 - 1.15"] ?: "-",
                        slot6 = map["1.15 - 2.15"] ?: "-",
                        slot7 = map["2.15 – 3.15"] ?: "-",
                        slot8 = map["3.15 – 4.15"] ?: "-",
                        slot9 = map["4.15 - 5.15"] ?: "-"
                    )
                }

                // Refresh local device cache with the newest timetable data
                timetableDao.clearTimetable()
                timetableDao.insertTimetable(dbEntries)

                Result.success("Timetable updated successfully!")
            } else {
                Result.failure(Exception("Server parsing error: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
