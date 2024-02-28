package name.lmj001.saveondevice;

//import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;
import android.os.Build;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int SAVE_FILE_REQUEST_CODE = 1;
    private static final int SAVE_FILES_REQUEST_CODE = 2;
    private Uri inputUri;
    private ArrayList<Uri> inputUris;
    private int currentFileIndex = 0;
    private boolean saveIndividually;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);

        saveIndividually = settings.getBoolean("saveIndividually", true );

        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                inputUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                callSaveFileResultLauncherForIndividual(inputUri, SAVE_FILE_REQUEST_CODE);
            } else {
                callSaveFileResultLauncherForPlainTextData(intent.getStringExtra(Intent.EXTRA_TEXT));
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                inputUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (saveIndividually) {
                    inputUri = inputUris.get(currentFileIndex);
                    callSaveFileResultLauncherForIndividual(inputUri, SAVE_FILES_REQUEST_CODE);
                }
                else callSaveFilesResultLauncherForMultipleUriDataAllAtOnce();
            }
        } else {
            Switch mySwitch = findViewById(R.id.multiSaveSwitch);
            if(Build.VERSION.SDK_INT<21) {
                mySwitch.setChecked(true);
                mySwitch.setEnabled(false);
                findViewById(R.id.oldAndroidInfo).setVisibility(View.VISIBLE);
            } else {
                mySwitch.setChecked(saveIndividually);
                mySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("saveIndividually", isChecked).apply());
            }
            // if(Build.VERSION.SDK_INT>22 && Build.VERSION.SDK_INT<29) requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private void callSaveFilesResultLauncherForMultipleUriDataAllAtOnce() {
        if (isSupportedMimeTypes(inputUris)) {
            Intent saveFilesIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(saveFilesIntent, SAVE_FILES_REQUEST_CODE);
        } else {
            Toast.makeText(getApplicationContext(), "Unsupported MIME type", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private String getMimeType(Uri uri) {
        String mimeType = getApplicationContext().getContentResolver().getType(uri);
        /*
        if (mimeType == null || mimeType.isEmpty()) {
            String fileExtension = getOriginalFileName(this, uri);
            fileExtension = fileExtension.substring(fileExtension.lastIndexOf('.') + 1).trim();
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
            if (mimeType == null || mimeType.isEmpty()) mimeType = "application/octet-stream";
        }
        //If you use anything besides ContentResolver.getType to set the MIME type Android apparently no longer recognizes this as a file coming from the app or whatever and the file will be 0 bytes unless you grant WRITE_EXTERNAL_STORAGE. I am not sure if Google Play will allowing uploading the app with it.
        */
        return mimeType;
    }

    private boolean isSupportedMimeTypes(ArrayList<Uri> uris) {
        for (Uri uri : uris) {
            String mimeType = getApplicationContext().getContentResolver().getType(uri);
            if (mimeType == null || mimeType.isEmpty()) return false;
        }
        return true;
    }
    
    private void callSaveFileResultLauncherForIndividual(Uri inputUri, int code) {
        final String mimeType = getMimeType(inputUri);
        if (mimeType == null || mimeType.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Unsupported MIME type", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Intent saveFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            saveFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            saveFileIntent.setType(mimeType);
            saveFileIntent.putExtra(Intent.EXTRA_TITLE, getOriginalFileName(this, inputUri));
            startActivityForResult(saveFileIntent, code);
        }
    }
    private void callSaveFileResultLauncherForPlainTextData(String text) {
        if (text != null) {
            String fileName = slugify(text.substring(0, Math.min(text.length(), 20))) + ".txt";
            Intent saveFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            saveFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            saveFileIntent.setType("text/plain");
            saveFileIntent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(saveFileIntent, SAVE_FILE_REQUEST_CODE);
        } else {
            Toast.makeText(getApplicationContext(), "Unsupported MIME type", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private String getOriginalFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private String slugify(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^a-zA-Z0-9\\s]+", "").trim()
                .replaceAll("\\s+", "-")
                .toLowerCase();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri outputUri = data.getData();
            if (outputUri != null) {
                switch (requestCode) {
                    case SAVE_FILE_REQUEST_CODE:
                        saveFile(outputUri);
                        break;
                    case SAVE_FILES_REQUEST_CODE:
                        if(saveIndividually) {
                            saveFile(outputUri);
                            if (inputUris != null && currentFileIndex < inputUris.size() - 1) {
                                currentFileIndex++;
                                callSaveFileResultLauncherForIndividual(inputUri, SAVE_FILES_REQUEST_CODE);
                            }
                        } else {
                            saveMultipleFilesAllAtOnce(outputUri);
                        }
                        break;
                }
            }
        }
    }

    private void saveMultipleFilesAllAtOnce(Uri treeUri) {
        if (treeUri != null) {
            ContentResolver resolver = getContentResolver();
            try {
                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                for (Uri inputUri : inputUris) {
                    String fromUriFileName = getOriginalFileName(this, inputUri);
                    InputStream inputStream = resolver.openInputStream(inputUri);
                    if (inputStream != null) {
                        Uri fileUri = DocumentsContract.createDocument(resolver, docUri, "*/*", fromUriFileName);
                        assert fileUri != null;
                        FileOutputStream outputStream = (FileOutputStream) resolver.openOutputStream(fileUri);
                        if (outputStream != null) {
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                            outputStream.close();
                        }
                        inputStream.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        finish();
    }
    private void saveFile(Uri outputUri) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            // Open a ParcelFileDescriptor using the content URI for internal storage
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(outputUri, "w");
            if (pfd != null) {
                // Open a FileOutputStream using the ParcelFileDescriptor
                outputStream = new FileOutputStream(pfd.getFileDescriptor());
                if (outputStream != null) {
                    if (inputUri != null) {
                        // Open an input stream using the content URI for the input file
                        inputStream = getContentResolver().openInputStream(inputUri);
                        if (inputStream != null) {
                            // Copy the contents of the input stream to the output stream
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                        }
                    } else {
                        // If the input URI is null, write the text from the intent directly to the output stream
                        String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
                        if (text != null) {
                            outputStream.write(text.getBytes());
                        }
                    }
                    outputStream.flush();
                }
                pfd.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // If there are more input URIs to process, save the next file; otherwise, finish the activity
        if (inputUris != null && currentFileIndex < inputUris.size() - 1) {
            currentFileIndex++;
            inputUri = inputUris.get(currentFileIndex);
            callSaveFileResultLauncherForIndividual(inputUri, SAVE_FILES_REQUEST_CODE);
        } else {
            finish();
        }
    }


}
