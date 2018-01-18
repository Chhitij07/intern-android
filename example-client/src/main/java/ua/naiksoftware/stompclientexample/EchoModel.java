package ua.naiksoftware.stompclientexample;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Naik on 24.02.17.
 */
public class EchoModel {

    private String echo;

    public EchoModel() {
    }

    public String getEcho() throws JSONException {

        //JSONObject jObj = new JSONObject(echo);
        //this.echo = jObj.getString("content");
        return echo;
    }

    public void setEcho(String echo) throws JSONException {

        JSONObject jObj = new JSONObject(echo);
        this.echo= jObj.getString("content");
        this.echo = echo;
    }
    public Double getLat() throws JSONException {
        JSONObject jObj = new JSONObject(echo);
        double lat = jObj.getDouble("Lat");
        return lat;
    }
    public Double getLng() throws JSONException {
        JSONObject jObj = new JSONObject(echo);
        double lng = jObj.getDouble("Lng");
        return lng;
    }
}
