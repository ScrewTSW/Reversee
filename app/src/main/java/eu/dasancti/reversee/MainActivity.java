package eu.dasancti.reversee;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;
import com.loopj.android.http.RequestParams;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {

    private static final String GOOGLE_REVERSE_IMAGE_SEARCH_URL = "https://lens.google.com/upload";
    private static final String GOOGLE_REVERSE_IMAGE_SEARCH_URL_BY_URL = "https://lens.google.com/uploadbyurl?url=";
    private static final String FAKE_USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.97 Safari/537.11";
    private static final String API_HL_KEY = "hl";
    private static final String API_HL_VALUE = "en-US";
    private static final String API_RE_KEY = "re";
    private static final String API_RE_VALUE = "df";
    private static final String API_UNIX_TIMESTAMP_KEY = "st";
    private static final String API_EP_KEY = "ep";
    private static final String API_EP_VALUE = "gisbubb";
    private static final String API_PARAMS_COMBINED = "&"+API_HL_KEY+"="+API_HL_VALUE+"&"+API_RE_KEY+"="+API_RE_VALUE+"&"+API_EP_KEY+"="+API_EP_VALUE+"&"+API_UNIX_TIMESTAMP_KEY+"=";
    private static final String API_ENCODED_IMAGE_KEY = "encoded_image";
    private static final String API_USER_AGENT_KEY = "User-Agent";
    private static final int REQUEST_PERMISSION_EXTERNAL_STORAGE_STATE = 99;

    private TextView progressStatus;
    private Uri requestPermissionsFallbackUri;

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_EXTERNAL_STORAGE_STATE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Permission Granted!", Toast.LENGTH_SHORT).show();
                    try {
                        handleImageSearch(this.requestPermissionsFallbackUri);
                    } catch (FileNotFoundException e) {
                        String err = "Failed to handle Share image intent:"+e.getMessage();
                        Log.e("INTENT_HANDLE",err);
                        Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Permission not granted, closing.", Toast.LENGTH_SHORT).show();
                    this.finish();
                }
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressStatus = findViewById(R.id.progressStatus);
        Intent intent = getIntent();
        handleReverseImageSearch(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleReverseImageSearch(intent);
    }

    private void handleReverseImageSearch(Intent intent) {
        if (intent == null) {
            Toast.makeText(MainActivity.this, "No intent sent to application, closing.", Toast.LENGTH_SHORT).show();
            this.finish();
            return;
        }
        String action = intent.getAction();
        if (action == null) {
            Toast.makeText(MainActivity.this, "No action sent to application, closing.", Toast.LENGTH_SHORT).show();
            this.finish();
            return;
        }
        if (action.equals(Intent.ACTION_SEND) || action.equals(Intent.ACTION_SENDTO)) {
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                String intentExtraText = getSharedURL(intent);
                if (intentExtraText == null) return;
                progressStatus.setText(getString(R.string.progress_status_parsing_link));
                if (intentExtraText.endsWith(".jpg") ||
                    intentExtraText.endsWith(".png") ||
                    intentExtraText.endsWith(".gif") ||
                    intentExtraText.endsWith(".webp")) {
                    progressStatus.setText(getString(R.string.progress_status_opening_from_url));
                    Toast.makeText(MainActivity.this, getString(R.string.progress_status_opening_from_url), Toast.LENGTH_SHORT).show();
                    Intent searchIntent = new Intent();
                    searchIntent.setAction(Intent.ACTION_VIEW);
                    searchIntent.setData(Uri.parse(GOOGLE_REVERSE_IMAGE_SEARCH_URL_BY_URL.concat(intentExtraText).concat(API_PARAMS_COMBINED).concat(String.valueOf(System.currentTimeMillis()/1000))));
                    startActivity(searchIntent);
                    MainActivity.this.finish();
                } else {
                    progressStatus.setText(getString(R.string.progress_status_unsupported_format));
                    Toast.makeText(MainActivity.this, getString(R.string.progress_status_unsupported_format), Toast.LENGTH_SHORT).show();
                    this.finish();
                }
            } else if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                if (isPermissionGranted()) {
                    progressStatus.setText(getString(R.string.progress_status_recieved_intent));
                    try {
                        handleImageSearch(intent.getParcelableExtra(Intent.EXTRA_STREAM));
                    } catch (FileNotFoundException e) {
                        String err = "Failed to handle Share image intent:" + e.getMessage();
                        Log.e("INTENT_HANDLE", err);
                        Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Read storage permission required.");
                    builder.setMessage("This application requires READ_EXTERNAL_STORAGE permission in order to function properly.");
                    builder.setPositiveButton("Grant permission", (dialog, which) -> {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_EXTERNAL_STORAGE_STATE);
                        if (action.equals(Intent.ACTION_SEND)) {
                            requestPermissionsFallbackUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                        }
                    });
                    builder.setNegativeButton("Deny permission", ((dialog, which) -> {
                        Toast.makeText(MainActivity.this, "Permission not granted, closing.", Toast.LENGTH_SHORT).show();
                        this.finish();
                    }));
                    builder.create().show();
                }
            }
        } else {
            Toast.makeText(MainActivity.this, "Unhandled action sent to application, closing.", Toast.LENGTH_SHORT).show();
            this.finish();
        }
    }

    @Nullable
    private String getSharedURL(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Toast.makeText(MainActivity.this, "Failed to get shared URL, incorrect format.", Toast.LENGTH_SHORT).show();
            this.finish();
            return null;
        }
        String intentExtraText = extras.getString(Intent.EXTRA_TEXT);
        if (intentExtraText == null) {
            Toast.makeText(MainActivity.this, "Failed to get URL from bundle, URL is empty or malformed", Toast.LENGTH_SHORT).show();
            this.finish();
            return null;
        }
        return intentExtraText;
    }

    private void handleImageSearch(Uri imageUri) throws FileNotFoundException {
        progressStatus.setText(getString(R.string.progress_status_handling_image));
        AtomicReference<String> reverseSearchRedirectURL = new AtomicReference<>();
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
        asyncHttpClient.addHeader(API_USER_AGENT_KEY, FAKE_USER_AGENT);
        asyncHttpClient.addHeader("Host", "lens.google.com");
        asyncHttpClient.addHeader("Origin", "https://images.google.com");
        asyncHttpClient.addHeader("Referer", "https://images.google.com/");
        asyncHttpClient.setTimeout(30_000);
        RequestParams requestParams = new RequestParams();
        requestParams.put(API_HL_KEY, API_HL_VALUE);
        requestParams.put(API_RE_KEY, API_RE_VALUE);
        requestParams.put(API_UNIX_TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()/1000));
        requestParams.put(API_EP_KEY, API_EP_VALUE);
        requestParams.put(API_ENCODED_IMAGE_KEY, Objects.requireNonNull(getContentResolver().openInputStream(imageUri)));
        RequestHandle requestHandle = asyncHttpClient.post(getApplicationContext(),GOOGLE_REVERSE_IMAGE_SEARCH_URL, requestParams, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                progressStatus.setText(getString(R.string.progress_status_response_success));
                Log.i("RESPONSE_SUCCESS", "Recieved status 200, processing HTML response.");
                String responseString = new String(responseBody);
                Document doc = Jsoup.parse(responseString);
                Elements redirects = doc.getElementsByTag("meta");
                for (Element redirect : redirects) {
                    String redirectUrl = redirect.attr("content");
                    if (redirectUrl.contains("/search?p=")) {
                        reverseSearchRedirectURL.set(redirectUrl.split("URL=")[1]);
                        progressStatus.setText(getString(R.string.progress_status_redirect_found));
                        break;
                    }
                }
                if (reverseSearchRedirectURL.get().isEmpty()) {
                    Toast.makeText(MainActivity.this, "Failed to get redirect URL", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Found redirect URL for reverse search, opening browser.", Toast.LENGTH_SHORT).show();
                    Log.i("REVERSE_SEARCH_URL", reverseSearchRedirectURL.get());
                    Intent searchIntent = new Intent();
                    searchIntent.setAction(Intent.ACTION_VIEW);
                    searchIntent.setData(Uri.parse(reverseSearchRedirectURL.get()));
                    startActivity(searchIntent);
                    MainActivity.this.finish();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                String err = "POST call to reverse search failed [" + statusCode + "] error message: " + error.getMessage() + "\n body:" + Arrays.toString(responseBody);
                Log.e("POST_CALL_FAILED", err);
                Toast.makeText(MainActivity.this, err, Toast.LENGTH_LONG).show();
                MainActivity.this.finish();
            }
        });
    }

    private boolean isPermissionGranted() {
        boolean granted;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        granted = permissionCheck == PackageManager.PERMISSION_GRANTED;
        return granted;
    }
}
