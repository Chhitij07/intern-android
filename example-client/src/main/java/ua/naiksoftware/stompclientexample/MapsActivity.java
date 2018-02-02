package ua.naiksoftware.stompclientexample;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import okhttp3.Response;
import okio.Utf8;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompHeader;
import ua.naiksoftware.stomp.client.StompClient;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.java_websocket.WebSocket;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import io.reactivex.FlowableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static ua.naiksoftware.stomp.LifecycleEvent.Type.OPENED;
import static ua.naiksoftware.stompclientexample.RestClient.ANDROID_EMULATOR_LOCALHOST;
//import static com.example.stompwebsocket.RestClient.ANDROID_EMULATOR_LOCALHOST;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private String AccessToken="";

    long t1;
    long t2;
    private static final String TAG = "MapsActivity";

    private GoogleMap mMap;

    private LatLng prev;
    private LatLng locate=null;

    Marker now=null;
    private StompClient mStompClient;
    private Double lat;
    private Double lng;

    private Gson mGson = new GsonBuilder().create();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Intent i=getIntent();
        AccessToken=i.getStringExtra("Access Token");

        connectStomp();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    public void connectStomp() {

        List<StompHeader> header=new ArrayList<>();
        header.add(new StompHeader("X-AUTH-TOKEN",AccessToken));

        mStompClient = Stomp.over(WebSocket.class, "ws://192.168.43.198:8181" +
                "/gs-guide-websocket/websocket",header);
    //mStompClient = Stomp.over(WebSocket.class, "ws://" + ANDROID_EMULATOR_LOCALHOST
     //           + ":" + RestClient.SERVER_PORT + "/gs-guide-websocket/websocket",param);



        mStompClient.lifecycle()
                .subscribeOn(Schedulers.io())


                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lifecycleEvent ->{
                    switch (lifecycleEvent.getType()) {
                        case OPENED:
                            toast("Stomp connection opened");
                            break;
                        case ERROR:
                            Log.e(TAG, "Stomp connection error", lifecycleEvent.getException());
                            toast("Stomp connection error");
                            break;
                        case CLOSED:
                            toast("Stomp connection closed");
                    }
                });


        List<StompHeader> head=new ArrayList<>();
        head.add(new StompHeader("X-AUTH-TOKEN",AccessToken));
        mStompClient.topic("/topic/greetings", head)

                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())

                .subscribe(topicMessage -> {
                    Log.d(TAG, "Received " + topicMessage.getPayload());

                    addItem(mGson.fromJson(topicMessage.getPayload(), EchoModel.class),topicMessage.getPayload());
                });


        sendEchoViaStomp();
        mStompClient.connect();

    }
    public void sendEchoViaStomp() {
        String data="{\"name\": \"John\"}";
        mStompClient.send("/app/hello", data)
                .compose(applySchedulers())
                .subscribe(aVoid -> {
                    Log.w(TAG, "STOMP echo send successfully");
                }, throwable -> {
                    Log.w(TAG, "Error send STOMP echo");
                    toast(throwable.getMessage());
                });
    }
    private void addItem(EchoModel echoModel, String str) throws JSONException {
        if (!isAppIsInBackground(this)) {
            echoModel.setEcho(str);
            lat = echoModel.getLat();
            lng = echoModel.getLng();

            prev = locate;
            locate = new LatLng(lat, lng);
        /*if(now!=null)
        {
            now.remove();
        }*/

            float rotateDegree = getRotate(prev, locate);
            if (prev == null)
                now = mMap.addMarker(new MarkerOptions().position(locate).title("Server Location")
                        .icon(BitmapDescriptorFactory.fromResource(R.mipmap.marker)).flat(true)
                        .rotation(rotateDegree));
            else {
                t2 = System.currentTimeMillis();

                animate(locate, (int) (t2 - t1));
                //mMap.addMarker(new MarkerOptions().position(prev).icon(BitmapDescriptorFactory.fromResource(R.mipmap.spots)).flat(true)
                //.rotation(rotateDegree));


                String url = getDirectionsUrl(prev, locate);

                DownloadTask downloadTask = new DownloadTask();

                // Start downloading json data from Google Directions API

                downloadTask.execute(url);

            }


            if (prev == null) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locate, 12));
                t1 = System.currentTimeMillis();
            }
            //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locate,21));
        }
    }
    private void DrawPolyLine()
    {
        PolylineOptions lineOptions=new PolylineOptions();
        ArrayList points=new ArrayList();
        points.add(prev);
        points.add(locate);
        lineOptions.addAll(points);
        lineOptions.width(5);
        lineOptions.color(Color.BLACK);
        lineOptions.geodesic(true);
        addLine(lineOptions);

    }


    float getRotate(LatLng loc1, LatLng loc2) {
        if (loc1 != null && loc2 != null) {
            double lat1 = loc1.latitude;
            double lat2 = loc2.latitude;
            double lng1 = loc1.longitude;
            double lng2 = loc2.longitude;
            double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lng2 - lng1);
            double y = Math.sin(lng2 - lng1) * Math.cos(lat2);
            float rot = (float) Math.toDegrees(Math.atan2(y, x));
            return rot;
        }
        return 0;
    }

    private void toast(String text) {
        Log.i(TAG, text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    protected <T> FlowableTransformer<T, T> applySchedulers() {
        return tFlowable -> tFlowable
                .unsubscribeOn(Schedulers.newThread())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    protected void onDestroy() {
        mStompClient.disconnect();
        super.onDestroy();
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(12.9716, 77);
        //now=mMap.addMarker(new MarkerOptions().position(sydney).title("Sydney Location"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sydney, 6));
    }

    public void addLine(PolylineOptions lineOptions) {
        mMap.addPolyline(lineOptions);
    }

    public void recenter(View view) {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locate, 15));
    }

    public void animate(LatLng destLatLng, int interval) {
        LatLng n = now.getPosition();
        double[] startValues = new double[]{n.latitude, n.longitude};
        double[] endValues = new double[]{destLatLng.latitude, destLatLng.longitude};
        ValueAnimator latLngAnimator = ValueAnimator.ofObject(new DoubleArrayEvaluator(), startValues, endValues);
        latLngAnimator.setDuration(10000);

        latLngAnimator.setInterpolator(new DecelerateInterpolator());
        latLngAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                double[] animatedValue = (double[]) animation.getAnimatedValue();
                now.setPosition(new LatLng(animatedValue[0], animatedValue[1]));
                now.setRotation(getRotate(n, destLatLng));
            }
        });
        latLngAnimator.start();
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(locate, 12), 1000, null);

    }

    private boolean isAppIsInBackground(Context context) {
        boolean isInBackground = true;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
            List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    for (String activeProcess : processInfo.pkgList) {
                        if (activeProcess.equals(context.getPackageName())) {
                            isInBackground = false;
                        }
                    }
                }
            }
        } else {
            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
            ComponentName componentInfo = taskInfo.get(0).topActivity;
            if (componentInfo.getPackageName().equals(context.getPackageName())) {
                isInBackground = false;
            }
        }

        return isInBackground;
    }

    private class DownloadTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... url) {

            String data = "";

            try {
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            ParserTask parserTask = new ParserTask();

            try{
                parserTask.execute(result);
            }catch (Exception e)
            {
                Log.e("Exception","Parser Task");
            }

        }
    }


    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(routes==null)
            {
                return routes;
            }
            Log.e("Routes", routes.size() + String.valueOf(routes));
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            if(result==null)
            {
                DrawPolyLine();
                return;

            }
            ArrayList points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();

            t2 = System.currentTimeMillis();
            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList();
                lineOptions = new PolylineOptions();

                List<HashMap<String, String>> path = result.get(i);

                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);
                    Log.e("Point", String.valueOf(position));

                    points.add(position);
                }


                lineOptions.addAll(points);
                lineOptions.width(5);
                lineOptions.color(Color.BLACK);
                lineOptions.geodesic(true);

            }

// Drawing polyline in the Google Map for the i-th route
            if (lineOptions != null)
                addLine(lineOptions);
        }
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {

        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        // Sensor enabled
        String sensor = "sensor=false";
        String mode = "mode=driving";
        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + mode;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;


        return url;
    }

    /**
     * A method to download json data from url
     */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.connect();

            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Exception", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

}
