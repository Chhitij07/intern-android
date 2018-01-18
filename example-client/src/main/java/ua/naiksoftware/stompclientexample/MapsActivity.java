package ua.naiksoftware.stompclientexample;

import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

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

import io.reactivex.FlowableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.client.StompClient;

import static ua.naiksoftware.stompclientexample.RestClient.ANDROID_EMULATOR_LOCALHOST;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private static final String TAG = "MapsActivity";

    Marker now=null;
    private StompClient mStompClient;
    private Double lat;
    private Double lng;
    private Gson mGson = new GsonBuilder().create();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        connectStomp();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    public void disconnectStomp(View view) {
        mStompClient.disconnect();
    }

    public void connectStomp() {
        mStompClient = Stomp.over(WebSocket.class, "ws://192.168.43.198:8080/gs-guide-websocket/websocket");
        //mStompClient = Stomp.over(WebSocket.class, "ws://" + ANDROID_EMULATOR_LOCALHOST
          //      + ":" + RestClient.SERVER_PORT + "/gs-guide-websocket/websocket");

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
        LatLng locate = new LatLng(lat, lng);
        if(now!=null)
        {
            now.remove();
        }
        now=mMap.addMarker(new MarkerOptions().position(locate).title("Server Location")
        .icon(BitmapDescriptorFactory.fromResource(R.mipmap.marker)));
        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locate,21));
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
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }
}
