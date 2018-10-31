package eu.dasancti.reversee;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

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
        Intent searchIntent = new Intent();
        searchIntent.setAction(Intent.ACTION_VIEW);
        //TODO: Implement browser intent with POST method
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