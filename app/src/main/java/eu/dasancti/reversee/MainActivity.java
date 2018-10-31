package eu.dasancti.reversee;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;
import com.loopj.android.http.RequestParams;
import cz.msebera.android.httpclient.Header;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {

    private static final String GOOGLE_REVERSE_IMAGE_SEARCH_URL = "https://www.google.com/searchbyimage/upload";
    private static final String FAKE_USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.97 Safari/537.11";
    private static final String API_SCH_KEY = "sch";
    private static final String API_SCH_VALUE = "sch";
    private static final String API_ENCODED_IMAGE_KEY = "encoded_image";
    private static final String API_USER_AGENT_KEY = "User-Agent";

    private TextView progressStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressStatus = findViewById(R.id.progressStatus);

        Intent intent = getIntent();
        String action = intent.getAction();
        if (isPermissionGranted()) {
            if (action.equals(Intent.ACTION_SEND)) {
                progressStatus.setText(getString(R.string.progress_status_recieved_intent));
                try {
                    handleImageSearch(intent);
                } catch (FileNotFoundException e) {
                    String err = "Failed to handle Share image intent:"+e.getMessage();
                    Log.e("INTENT_HANDLE",err);
                    Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "The app wasn't allowed permissions to manage files.", Toast.LENGTH_LONG).show();
        }
    }

    private void handleImageSearch(Intent intent) throws FileNotFoundException {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        progressStatus.setText(getString(R.string.progress_status_handling_image));
        AtomicReference<String> reverseSearchRedirectURL = new AtomicReference<>();
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
        asyncHttpClient.addHeader(API_USER_AGENT_KEY, FAKE_USER_AGENT);
        RequestParams requestParams = new RequestParams();
        requestParams.put(API_SCH_KEY, API_SCH_VALUE);
        requestParams.put(API_ENCODED_IMAGE_KEY, Objects.requireNonNull(getContentResolver().openInputStream(imageUri)));
        RequestHandle requestHandle = asyncHttpClient.post(getApplicationContext(),GOOGLE_REVERSE_IMAGE_SEARCH_URL, requestParams, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                Log.e("UNEXPECTED", "Based on the stupendous behavior of this async library, we should never be able to get here. 3xx HTML codes are considered a failure.");
                MainActivity.this.finish();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                if (statusCode == 302) {
                    progressStatus.setText(getString(R.string.progress_status_response_success));
                    Log.i("RESPONSE_SUCCESS", "Recieved 302 redirect, processing HTML response.");
                    String responseString = new String(responseBody);
                    Document doc = Jsoup.parse(responseString);
                    Elements redirects = doc.getElementsByTag("a");
                    for (Element redirect : redirects) {
                        String redirectUrl = redirect.attr("href");
                        if (redirectUrl.contains("/search?tbs=sbi:")) {
                            reverseSearchRedirectURL.set(redirectUrl);
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
                } else {
                    String err = "POST call to reverse search failed [" + statusCode + "] error message: " + error.getMessage() + "\n body:" + Arrays.toString(responseBody);
                    Log.e("POST_CALL_FAILED", err);
                    Toast.makeText(MainActivity.this, err, Toast.LENGTH_LONG).show();
                }
            }
        });
        // TODO: Display spinner until requestHandle.isFinished() is true
    }

    private boolean isPermissionGranted() {
        boolean granted;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        granted = permissionCheck == PackageManager.PERMISSION_GRANTED;
        return granted;
    }
}