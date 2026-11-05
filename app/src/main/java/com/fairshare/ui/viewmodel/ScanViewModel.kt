package com.fairshare.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fairshare.BuildConfig
import com.fairshare.scanner.ReceiptScanner
import com.fairshare.scanner.ScanArchive
import com.fairshare.scanner.ScanException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Keys under which the scan result goes back to the "Nowy wydatek" screen (its back stack entry's SavedStateHandle). */
object ScanResultKeys {
    /** Long, grosze. Set last: the form reacts to this one. */
    const val TOTAL = "scan_total"
    const val STORE = "scan_store"
    /** Short line for the "Paragon" row, e.g. "Biedronka · 12 pozycji". */
    const val SUMMARY = "scan_summary"
}

/** "Skanuj paragon": photo → ML Kit → rules → (Gemini when the sum does not match). */
class ScanViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val scanner = ReceiptScanner(application)
    /** Debug builds keep every scanned photo with its reading (ScanArchive); release builds keep nothing. */
    private val archive: ScanArchive? = if (BuildConfig.SAVE_SCANS) ScanArchive(application) else null

    sealed interface State {
        data object Idle : State
        data class Working(val stage: String) : State
        /** [savedAs]: folder of the saved photo and reading in debug builds ("2026-11-04_18-05-12"). */
        class Done(val outcome: ReceiptScanner.Outcome, val savedAs: String? = null) : State
        data class Failed(val message: String, val savedAs: String? = null) : State
    }

    var state by mutableStateOf<State>(State.Idle); private set

    val aiAvailable: Boolean get() = scanner.aiAvailable
    val aiModel: String? get() = scanner.aiModel

    /** Where the camera app writes the next photo. In SavedStateHandle: Android may kill the app while the camera is open. */
    private var pendingPhoto: Uri?
        get() = savedState[PENDING_PHOTO]
        set(value) { savedState[PENDING_PHOTO] = value }

    fun newPhotoUri(): Uri {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "receipts").apply { mkdirs() }
        // keep only the latest few photos
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        val file = File(dir, "paragon_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file).also { pendingPhoto = it }
    }

    fun onPhotoTaken(saved: Boolean) {
        val uri = pendingPhoto
        if (saved && uri != null) scan(uri)
    }

    fun scan(uri: Uri) {
        if (state is State.Working) return
        state = State.Working("Wczytuję zdjęcie…")
        viewModelScope.launch {
            var outcome: ReceiptScanner.Outcome? = null
            val failure: String? = try {
                outcome = scanner.scan(uri) { stage -> state = State.Working(stage) }
                null
            } catch (e: ScanException) {
                e.message ?: "Nie udało się odczytać paragonu."
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                "Zdjęcie jest za duże dla pamięci telefonu."
            } catch (e: Exception) {
                "Błąd: ${e.javaClass.simpleName}: ${e.message}"
            }
            val savedAs = archive?.save(uri, outcome, failure)
            val result = outcome
            state = if (result != null) State.Done(result, savedAs) else State.Failed(failure ?: "Nieznany błąd.", savedAs)
        }
    }

    fun showError(message: String) {
        state = State.Failed(message)
    }

    fun reset() {
        state = State.Idle
    }

    override fun onCleared() {
        scanner.close()
    }

    private companion object {
        const val PENDING_PHOTO = "pending_photo"
    }
}