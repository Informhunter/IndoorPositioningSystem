package io.informhunter.datacollector2;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import io.informhunter.datacollector2.data.Collector;
import io.informhunter.datacollector2.data.PositionDataPoint;
import io.informhunter.datacollector2.data.RSSIDataPoint;
import io.informhunter.datacollector2.drawing.FlatMap;

public class MainActivity extends AppCompatActivity {

    FlatMap flatMap;
    Collector collector;

    private boolean isCapturing = false;
    private BluetoothAdapter mAdapter;

    final private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            RSSIDataPoint dp = new RSSIDataPoint(device.getName(), rssi, scanRecord);
            collector.AddDataPoint(dp);
            TextView textView = (TextView) findViewById(R.id.textLog);
            textView.setText("Minors count: " + String.valueOf(collector.GetMinorCount()));
        }
    };


    private static final int REQUEST_PERMISSIONS = 1;
    private static String[] PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    public static void verifyPermissions(Activity activity) {
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS,
                    REQUEST_PERMISSIONS
            );
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        while(!mAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        verifyPermissions(this);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = true;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap original = BitmapFactory.decodeResource(getResources(), R.drawable.cropped_flat, options);

        flatMap = new FlatMap(original, 13.90f, 7.35f);

        collector = new Collector();

    }

    public void onClickButtonCapture(View v) {
        TextView textView = (TextView) findViewById(R.id.textLog);
        ToggleButton btn = (ToggleButton) v;

        float[] cursor = flatMap.GetCursor();

        if (btn.isChecked()) {
            //Enable
            textView.setText("Start capture\n");
            collector.ResetMinorCounter();
            isCapturing = true;
            collector.AddDataPoint(new PositionDataPoint(cursor[0], cursor[1]));
            mAdapter.startLeScan(leScanCallback);
        } else {
            //Disable
            mAdapter.stopLeScan(leScanCallback);
            flatMap.AddRoutePoint(cursor);
            collector.AddDataPoint(new PositionDataPoint(cursor[0], cursor[1]));
            redrawPlan();
            isCapturing = false;
            textView.setText("Stop capture\n");
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        ImageView flatPlan = (ImageView) findViewById(R.id.flatPlan);

        if(isCapturing) {
            return false;
        }

        int loc[] = new int[2];
        flatPlan.getLocationOnScreen(loc);

        float x = event.getX();
        float y = event.getY();

        x = (x - loc[0]) / flatPlan.getWidth();
        y = (y - loc[1]) / flatPlan.getHeight();

        float[] cursor = new float[]{x, y};
        flatMap.SetCursor(cursor);

        redrawPlan();

        return super.onTouchEvent(event);
    }

    private void redrawPlan() {
        ImageView flatPlan = (ImageView) findViewById(R.id.flatPlan);
        flatPlan.setImageBitmap(flatMap.Render());
    }

    public void onClickButtonClear(View v) {
        TextView textLog = (TextView) findViewById(R.id.textLog);
        textLog.setText("");
    }

    public void onClickButtonSave(View v) {
        TextView textLog = (TextView) findViewById(R.id.textLog);

        EditText captureEdit = (EditText) findViewById(R.id.dataNameEdit);
        String sessionName = captureEdit.getText().toString();

        textLog.append("Save data\n");
        String error = collector.SaveData(sessionName);
        if(error == null) {
            textLog.append("Data saved\n");
        } else {
            textLog.append("Save failed: " + error + "\n");
        }
    }


    public void onResetRouteButtonClick(View v) {
        flatMap.ClearRoute();
        //Clear data buffer
        collector.ResetData();
        redrawPlan();
    }

    public void onSendButtonClick(View v) {
        EditText captureEdit = (EditText) findViewById(R.id.dataNameEdit);
        EditText urlEdit = (EditText) findViewById(R.id.collectorURLEdit);

        new SendDataTask().execute(
                captureEdit.getText().toString(),
                urlEdit.getText().toString()
        );
    }

    class SendDataTask extends AsyncTask<String, String, String> {

        @Override
        protected void onPreExecute() {
            TextView textLog = (TextView) findViewById(R.id.textLog);
            textLog.append("Upload started\n");
        }

        @Override
        protected void onPostExecute(String s) {
            TextView textLog = (TextView) findViewById(R.id.textLog);
            if(s == null) {
                textLog.append("Upload done\n");
            } else {
                textLog.append("Upload failed: " + s + "\n");
            }
        }

        @Override
        protected String doInBackground(String... strings) {
            String url = strings[0];
            String sessionName = strings[1];
            return collector.SendData(url, sessionName);
        }
    }

}