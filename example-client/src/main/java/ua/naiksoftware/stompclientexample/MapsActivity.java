package ua.naiksoftware.stompclientexample;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import okhttp3.Response;
import okio.Utf8;
import ua.naiksoftware.stomp.Stomp;
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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
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

        Map<String,String> param= new HashMap<>();
        param.put("X_Auth_Token","Bearer "+AccessToken);
        mStompClient = Stomp.over(WebSocket.class, "ws://192.168.0.105:8181/gs-guide-websocket/websocket");
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
        lat=echoModel.getLat();
        lng=echoModel.getLng();

        prev=locate;
        locate = new LatLng(lat, lng);
        /*if(now!=null)
        {
            now.remove();
        }*/

        float rotateDegree=getRotate(prev,locate);
        if(prev==null)
            now=mMap.addMarker(new MarkerOptions().position(locate).title("Server Location")
                    .icon(BitmapDescriptorFactory.fromResource(R.mipmap.marker)).flat(true)
                    .rotation(rotateDegree));
        else
            animate(prev,locate,rotateDegree);
        if(prev==null)
        {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locate,12));
            t1=System.currentTimeMillis();
        }
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
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sydney,12));
    }

    public void animate(LatLng srcLatLng,LatLng destLatLng,float rotDegree)
    {
        double[] startValues = new double[]{srcLatLng.latitude, srcLatLng.longitude};
        double[] endValues = new double[]{destLatLng.latitude, destLatLng.longitude};

        ValueAnimator latLngAnimator = ValueAnimator.ofObject(new DoubleArrayEvaluator(), startValues, endValues);
        t2=System.currentTimeMillis();
        latLngAnimator.setDuration(t2-t1);
        t1=t2;
        latLngAnimator.setInterpolator(new DecelerateInterpolator());
        latLngAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                double[] animatedValue = (double[]) animation.getAnimatedValue();
                now.setPosition(new LatLng(animatedValue[0], animatedValue[1]));
                now.setRotation(rotDegree);
            }
        });
        latLngAnimator.start();
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(locate,12),1000,null);

    }
    private class connect extends AsyncTask<Void,Void,Void>
    {

        @Override
        protected Void doInBackground(Void... voids) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Log.e("Access Token",AccessToken);

            headers.set("X_Auth_Token", AccessToken);
            RestTemplate restTemplate = new RestTemplate();
            MappingJackson2HttpMessageConverter jsonHttpMessageConverter = new MappingJackson2HttpMessageConverter();
            jsonHttpMessageConverter.getObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            restTemplate.getMessageConverters().add(jsonHttpMessageConverter);

            HttpEntity<String> entity = new HttpEntity<String>("parameters",headers);
            ResponseEntity<String> response = restTemplate.exchange("http://192.168.43.198:8181/gs-guide-websocket/websocket", HttpMethod.GET,entity, String.class);
            //mStompClient = Stomp.over(WebSocket.class, "ws://" + ANDROID_EMULATOR_LOCALHOST
            //        + ":" + RestClient.SERVER_PORT + "/gs-guide-websocket/websocket");
            Log.e("Response ",response.getBody());

            return null;
        }
    }

}
