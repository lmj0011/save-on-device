package name.lmj001.saveondevice

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import java.io.FileOutputStream

lateinit var saveFileResultLauncher: ActivityResultLauncher<Intent>
lateinit var inputUri: Uri
lateinit var inputUriList: MutableList<Uri>
lateinit var inputText: String
lateinit var inputTextList: MutableList<String>

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        saveFileResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let{
                    when (intent?.action) {
                        Intent.ACTION_SEND -> {
                            saveFile(it)

                            finish()
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            saveFile(it)

                            // seeing if there are other files in queue to be saved
                            if (::inputUriList.isInitialized) {
                                val uri = inputUriList.removeFirstOrNull()

                                if (uri != null) {
                                    inputUri = uri

                                    callSaveFileResultLauncher(uri = inputUri)
                                } else finish()

                            } else {
                                val text = inputTextList.removeFirstOrNull()

                                if (text != null) {
                                    inputText = text

                                    callSaveFileResultLauncher(text = text)
                                } else finish()
                            }
                        }
                        else -> finish()
                    }
                }
            } else finish()
        }

        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                    inputUri = intent.extras?.get(Intent.EXTRA_STREAM) as Uri
                    callSaveFileResultLauncher(uri = inputUri)
                }
                else {
                    inputText = intent.extras?.get(Intent.EXTRA_TEXT) as String
                    callSaveFileResultLauncher(text = inputText)
                }

            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                    inputUriList = (intent.extras?.getParcelableArrayList<Uri>(Intent.EXTRA_STREAM) as ArrayList<Uri>).toMutableList()
                    val uri = inputUriList.removeFirstOrNull()

                    if (uri != null) {
                        inputUri = uri

                        callSaveFileResultLauncher(uri = inputUri)
                    }
                } else {
                    inputTextList = (intent.extras?.getStringArrayList(Intent.EXTRA_TEXT))!!.toMutableList()
                    val text = inputTextList.removeFirstOrNull()

                    if (text != null) {
                        inputText = text

                        callSaveFileResultLauncher(text = inputText)
                    }
                }
            }
        }
    }

    private fun isSupportedMimeType(uri: Uri): Boolean {
        val mimeType = contentResolver.getType(uri)

        return !mimeType.isNullOrBlank()
    }

    private fun callSaveFileResultLauncher(uri: Uri? = null, text: String? = null) {
        if (text != null || isSupportedMimeType(uri!!)) {
            val fromUriFileName =
                if (uri != null) DocumentFile.fromSingleUri(this, uri)!!.name
                else ""
            val saveFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
                addCategory(Intent.CATEGORY_OPENABLE)

                // Create a file with the requested MIME type.
                type = if (uri != null) contentResolver.getType(inputUri) else "text/plain"
                putExtra(Intent.EXTRA_TITLE, fromUriFileName)
            }

            saveFileResultLauncher.launch(saveFileIntent)
        } else {
            Toast.makeText(applicationContext, getString(R.string.unsupported_mimetype), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun dumpIntent(intent: Intent?) {
        if (intent == null) return

        val bundle = intent.extras

        Log.d("MainActivity", "intent uri: $intent")

        if (bundle != null) {
            for (key in bundle.keySet()) {
                Log.d("MainActivity", key + " : " + if (bundle.get(key) != null) bundle.get(key) else "NULL")
            }
        }
    }

    private fun saveFile(outputUri: Uri) {
        val inputStream =
            if (::inputUri.isInitialized) contentResolver.openInputStream(inputUri)
            else null
        contentResolver.openFileDescriptor(outputUri, "w")?.use { p ->
            val outputStream = FileOutputStream(p.fileDescriptor)
            if (inputStream != null) {
                val buffer = ByteArray(4096)
                var length: Int

                length = inputStream.read(buffer)

                while (length > 0) {
                    outputStream.write(buffer, 0, length)
                    length = inputStream.read(buffer)
                }

                outputStream.flush() // apparently this does nothing..
                inputStream.close()
            } else outputStream.write(inputText.toByteArray(Charsets.UTF_8))
            outputStream.close()
        }
    }
}