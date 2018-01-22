package ua.naiksoftware.stompclientexample;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import okio.Utf8;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.client.StompClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.java_websocket.WebSocket;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;

import javax.net.ssl.HttpsURLConnection;

import io.reactivex.FlowableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
//import static com.example.stompwebsocket.RestClient.ANDROID_EMULATOR_LOCALHOST;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private LatLng next;
    private static final String TAG = "MapsActivity";

    private LatLng locate=null;
    private double prevLocate;
    Marker now=null;
    private StompClient mStompClient;
    private Gson mGson = new GsonBuilder().create();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        final AsyncTask<String, Void, String> execute = new HttpClient().execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        connectStomp();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    public void connectStomp() {
        mStompClient = Stomp.over(WebSocket.class, "ws://192.168.43.198:8080/gs-guide-websocket/websocket");
        //mStompClient = Stomp.over(WebSocket.class, "ws://" + ANDROID_EMULATOR_LOCALHOST
        //        + ":" + RestClient.SERVER_PORT + "/gs-guide-websocket/websocket");

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

        mStompClient.topic("/topic/greetings")
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
        mStompClient.send("/app/hello", data )
                .compose(applySchedulers())
                .subscribe(aVoid -> {
                    Log.w(TAG, "STOMP echo send successfully");
                }, throwable -> {
                    Log.w(TAG, "Error send STOMP echo");
                    toast(throwable.getMessage());
                });
    }
    private void addItem(EchoModel echoModel,String str) throws JSONException {
        echoModel.setEcho(str);
        Double lat = echoModel.getLat();
        Double lng = echoModel.getLng();

        LatLng prev = locate;
        locate = new LatLng(lat, lng);
        if(now!=null)
        {
            now.remove();
        }
        float rotateDegree=getRotate(prev,locate);
        now=mMap.addMarker(new MarkerOptions().position(locate).title("Server Location")
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.marker)).flat(true)
                .rotation(rotateDegree));
        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locate,21));
    }

    float getRotate(LatLng loc1,LatLng loc2)
    {
        if(loc1!=null && loc2!=null) {
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
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sydney,21));
    }
    public class HttpClient extends AsyncTask<String, Void, String> {

        protected void onPreExecute(){}

        protected String doInBackground(String... arg0) {

            try {

                java.net.URL url = new URL("http://192.168.43.198:8080/login"); // here is your URL path


                String user="user";
                String password="password";
                String postDataParams=String.format("username=%s&password=%s", URLEncoder.encode(user, "UTF-8"), URLEncoder.encode(password, "UTF-8"));
                Log.e("params",postDataParams);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(15000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(os, "UTF-8"));
                writer.write(postDataParams);

                writer.flush();
                writer.close();
                os.close();

                int responseCode=conn.getResponseCode();

                if (responseCode == HttpsURLConnection.HTTP_OK) {

                    BufferedReader in=new BufferedReader(new
                            InputStreamReader(
                            conn.getInputStream()));

                    StringBuffer sb = new StringBuffer("");
                    String line="";

                    while((line = in.readLine()) != null) {

                        sb.append(line);
                        break;
                    }
                    Log.i(TAG, "doInBackground: "+sb);
                    in.close();
                    return sb.toString();

                }
                else {
                    return new String("false : "+responseCode);
                }
            }
            catch(Exception e){
                return new String("Exception: " + e.getMessage());
            }

        }

        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(getApplicationContext(), result,Toast.LENGTH_LONG).show();
        }


        public String getPostDataString(JSONObject params) throws Exception {

            StringBuilder result = new StringBuilder();
            boolean first = true;

            Iterator<String> itr = params.keys();

            while(itr.hasNext()){

                String key= itr.next();
                Object value = params.get(key);

                if (first)
                    first = false;
                else
                    result.append("&");

                result.append(URLEncoder.encode(key, "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(value.toString(), "UTF-8"));

            }
            return result.toString();
        }
    }
}
