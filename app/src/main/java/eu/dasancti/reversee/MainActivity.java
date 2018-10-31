package eu.dasancti.reversee;

import android.Manifest;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {

    private static final String GOOGLE_REVERSE_IMAGE_SEARCH_URL = "https://www.google.com/searchbyimage/upload";
    private static final String FAKE_USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.97 Safari/537.11";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        String action = intent.getAction();
        if (isPermissionGranted()) {
            if (action.equals(Intent.ACTION_SEND)) {
                handleImageSearch(intent);
            }
        } else {
            Toast.makeText(this, "The app wasn't allowed permissions to manage files.", Toast.LENGTH_LONG).show();
        }
    }

    private void handleImageSearch(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        Toast.makeText(this, "Received image URI:"+imageUri.getPath(), Toast.LENGTH_SHORT).show();
        AtomicReference<String> imageData = new AtomicReference<>();
        try {
            imageData.set(IOUtils.toString(Objects.requireNonNull(getContentResolver().openInputStream(imageUri))));
        } catch (FileNotFoundException e) {
            String err = "Couldn't open the shared image as an input stream.";
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            Log.e("GET_IMAGE_FILE", err);
            this.finish();
            return;
        } catch (IOException e) {
            String err = "Couldn't read input stream of image.";
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            Log.e("GET_IMAGE_FILE", err);
            this.finish();
            return;
        }
        Intent searchIntent = new Intent();
        searchIntent.setAction(Intent.ACTION_VIEW);
        //TODO: Implement browser intent with POST method
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        final AtomicReference<String> browserRedirect = new AtomicReference<>();
        StringRequest getGoogleRedirectUrl = new StringRequest(
                Request.Method.POST,
                GOOGLE_REVERSE_IMAGE_SEARCH_URL,
                response -> {
                    Document doc = Jsoup.parse(response);
                    Element content = doc.getElementById("content");
                    Elements redirects = content.getElementsByTag("meta");
                    for (Element redirect : redirects) {
                        String redirectUrl = redirect.attr("url");
                        if (redirectUrl.contains("/search?tbs=sbi:")) {
                            browserRedirect.set(redirectUrl);
                            break;
                        }
                    }
                    if (browserRedirect.get().isEmpty()) {
                        Toast.makeText(this, "Failed to get redirect URL", Toast.LENGTH_LONG).show();
                    } else {
                        //TODO: Launch browser intent with the redirect
                        Toast.makeText(this, "Found redirect URL for reverse search.", Toast.LENGTH_SHORT).show();
                        Log.i("REVERSE_SEARCH_URL", browserRedirect.get());
                    }
                },
                error -> Toast.makeText(this, "POST call to reverse search failed: "+error.getMessage(), Toast.LENGTH_LONG).show()
        ) {
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("sch", "sch");
                params.put("encoded_image", imageData.get());
                return params;
            }
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", FAKE_USER_AGENT);
                return headers;
            }
        };
        requestQueue.add(getGoogleRedirectUrl);
        Toast.makeText(this, "Handling google reverse image search.", Toast.LENGTH_LONG).show();
        this.finish();
    }

    private boolean isPermissionGranted() {
        boolean granted;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        granted = permissionCheck == PackageManager.PERMISSION_GRANTED;
        return granted;
    }
}