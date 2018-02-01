package ua.naiksoftware.stompclientexample;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.fasterxml.jackson.databind.SerializationFeature;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

public class MainActivity extends AppCompatActivity {




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



    }

    public void login(View view) {
        new LoginTask(this).execute();
    }
}

class LoginTask extends AsyncTask<Void, String, String> {

    private String accessToken=null;
    private Context context;
    public LoginTask(Context context){
        this.context=context;
    }
    @Override
    protected String doInBackground(Void... params) {

        try {
            String loginResponse = "Login successful. Redirecting to Home screen...";

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("username", "user");
            jsonObject.put("password","password");
            User user = new User();
            user.setUsername("user");
            user.setPassword("password");
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            RestTemplate restTemplate = new RestTemplate();
            MappingJackson2HttpMessageConverter jsonHttpMessageConverter = new MappingJackson2HttpMessageConverter();
            jsonHttpMessageConverter.getObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            restTemplate.getMessageConverters().add(jsonHttpMessageConverter);
            //restTemplate.getMessageConverters().add(new MappingJacksonHttpMessageConverter());
            //String details = "{\"username\":\"\",\"password\":\"password\"}";
            HttpEntity<User> httpEntity = new HttpEntity<>(user, requestHeaders);
            String URL = "http://192.168.43.198:8181/auth/login";
            Log.e("Details", String.valueOf(httpEntity));
            ResponseEntity<AccessToken> response = restTemplate.exchange(URL, HttpMethod.POST, httpEntity, AccessToken.class);

            Log.e("Main Activity Response ", String.valueOf(response));
            if (response.getStatusCode() == HttpStatus.OK || response.getBody().getAccess_token() != null) {
                Log.e("Access Token",response.getBody().getAccess_token());
                accessToken=response.getBody().getAccess_token();
                //AppPreferences.singleton.getSharedPreferences().edit().putString(X_AUTH_TOKEN, response.getBody().getAccess_token()).commit();
            }

        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return accessToken;
    }

    @Override
    protected void onPostExecute(String s) {
        Intent intent=new Intent();
        intent.setClass(context, MapsActivity.class);
        intent.putExtra("Access Token",accessToken);
        context.startActivity(intent);
    }
}


