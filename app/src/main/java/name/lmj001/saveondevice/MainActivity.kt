package name.lmj001.saveondevice

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import java.io.FileOutputStream
import java.text.Normalizer

lateinit var saveFileResultLauncher: ActivityResultLauncher<Intent>
lateinit var saveFilesResultLauncher: ActivityResultLauncher<Uri>
lateinit var inputUriList: MutableList<Uri>
lateinit var inputText: String
lateinit var inputTextList: MutableList<String>

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        saveFileResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let{ outputUri ->
                    when (intent?.action) {
                        Intent.ACTION_SEND -> {

                            /** Save the file */
                            contentResolver.openFileDescriptor(outputUri, "w")?.use { p ->
                                val inputUri = intent.extras?.get(Intent.EXTRA_STREAM) as? Uri
                                val outputStream = FileOutputStream(p.fileDescriptor)

                                when (inputUri) {
                                    is Uri -> {
                                        val inputStream = contentResolver.openInputStream(inputUri)!!

                                        val buffer = ByteArray(4096)
                                        var length: Int

                                        length = inputStream.read(buffer)

                                        while (length > 0) {
                                            outputStream.write(buffer, 0, length)
                                            length = inputStream.read(buffer)
                                        }

                                        outputStream.flush() // apparently this does nothing..
                                        inputStream.close()
                                    }

                                    else -> {
                                        outputStream.write(inputText.toByteArray(Charsets.UTF_8))
                                    }
                                }

                                outputStream.close()
                            }
                            /***********/

                            finish()
                        }
                        else -> finish()
                    }
                }
            } else finish()
        }

        saveFilesResultLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let{
                val outputDir = DocumentFile.fromTreeUri(this, it)

                if (::inputUriList.isInitialized) { // our list of files to save
                    for (singleUri in inputUriList) {
                        val documentFileNewFile = outputDir?.createFile(contentResolver.getType(singleUri)!!, singleUri.getOriginalFileName(applicationContext)!!)

                        /** Save the file */
                        val inputStream = contentResolver.openInputStream(singleUri)

                        contentResolver.openFileDescriptor(documentFileNewFile!!.uri, "w")?.use { p ->
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
                        /***********/
                    }
                }
                finish()
            }

            finish()
        }

        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                    val inputUri = intent.extras?.get(Intent.EXTRA_STREAM) as Uri
                    callSaveFileResultLauncherForSingleUriData(inputUri = inputUri)
                }
                else {
                    inputText = intent.extras?.getString(Intent.EXTRA_TEXT, "").toString()
                    callSaveFileResultLauncherForPlainTextData(text = inputText)
                }

            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                    inputUriList = (intent.extras?.getParcelableArrayList<Uri>(Intent.EXTRA_STREAM) as ArrayList<Uri>).toMutableList()
                    callSaveFileResultLauncherForMultipleUriData(uriList = inputUriList)
                } else {
                    inputTextList = (intent.extras?.getStringArrayList(Intent.EXTRA_TEXT))!!.toMutableList()
                    val text = inputTextList.removeFirstOrNull()

                    if (text != null) {
                        inputText = text

                        callSaveFileResultLauncherForPlainTextData(text = inputText)
                    }
                }
            }
        }
    }

    private fun isSupportedMimeType(uri: Uri): Boolean {
        val mimeType = contentResolver.getType(uri)

        return !mimeType.isNullOrBlank()
    }

    private fun callSaveFileResultLauncherForSingleUriData(inputUri: Uri) {
        if(isSupportedMimeType(inputUri)) {
            val fromUriFileName = DocumentFile.fromSingleUri(this, inputUri)!!.name
            val saveFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
                addCategory(Intent.CATEGORY_OPENABLE)

                // Create a file with the requested MIME type.
                type = contentResolver.getType(inputUri)
                putExtra(Intent.EXTRA_TITLE, fromUriFileName)
            }

            saveFileResultLauncher.launch(saveFileIntent)
        } else {
            Toast.makeText(applicationContext, getString(R.string.unsupported_mimetype), Toast.LENGTH_LONG)
                .show()
            finish()
        }
    }

    private fun callSaveFileResultLauncherForMultipleUriData(uriList: MutableList<Uri>) {
        if(uriList.none { uri -> !isSupportedMimeType(uri) }) {
            saveFilesResultLauncher.launch(Uri.EMPTY)
        } else {
            Toast.makeText(applicationContext, getString(R.string.unsupported_mimetype), Toast.LENGTH_LONG)
                .show()
            finish()
        }
    }

    private fun callSaveFileResultLauncherForPlainTextData(text: String? = null) {
        if (text != null) {
            val fileName = "${slugify(text.take(20), "_")}.txt"

            val saveFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
                addCategory(Intent.CATEGORY_OPENABLE)

                // Create a file with the requested MIME type.
                type = "text/plain"

                putExtra(Intent.EXTRA_TITLE, fileName)
            }

            saveFileResultLauncher.launch(saveFileIntent)
        } else {
            Toast.makeText(applicationContext, getString(R.string.unsupported_mimetype), Toast.LENGTH_LONG)
                .show()
        }
    }

//    private fun dumpIntent(intent: Intent?) {
//        if (intent == null) return
//
//        val bundle = intent.extras
//
//        Log.d("MainActivity", "intent uri: $intent")
//
//        if (bundle != null) {
//            for (key in bundle.keySet()) {
//                Log.d("MainActivity", key + " : " + if (bundle.get(key) != null) bundle.get(key) else "NULL")
//            }
//        }
//    }


    // extension function
    private fun Uri.getOriginalFileName(context: Context): String? {
        return context.contentResolver.query(this, null, null, null, null)?.use {
            val nameColumnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameColumnIndex)
        }
    }

    /**
     * helper function
     *
     * ref: https://gist.github.com/adrianoluis/641e21dc24a1dbfb09e203d857ae76a3
      */

    fun slugify(word: String, replacement: String = "-") = Normalizer
        .normalize(word, Normalizer.Form.NFD)
        .replace("[^\\p{ASCII}]".toRegex(), "")
        .replace("[^a-zA-Z0-9\\s]+".toRegex(), "").trim()
        .replace("\\s+".toRegex(), replacement)
        .toLowerCase()
}