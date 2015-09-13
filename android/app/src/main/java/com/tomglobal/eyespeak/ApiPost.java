package com.tomglobal.eyespeak;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

/**
 * Created by skiluk on 9/11/15.
 */
public class ApiPost extends AsyncTask<String, Void, Void> {

    private Context context;

    String HTTP_PRE = "http://";
    String HOST_API = "eyespeak.elasticbeanstalk.com/update?version=1&userId=";

    //version=1
    //userId==
    //utteranceId
    //responseId OR text=

    public ApiPost(Context ctx) {
        context = ctx;
    }

   String apiResult;

    @Override
    protected Void doInBackground(String... params) {
        BufferedReader reader = null;

        SharedPreferences prefs = context.getSharedPreferences("com.tomglobal.eyespeak", Context.MODE_PRIVATE);
        String user = prefs.getString("userId","eliza");

        try {
            String call = params[0].replace(" ", "%20");
            Log.d("API Call: ", HTTP_PRE + HOST_API + user + call);
            URL url = new URL(HTTP_PRE + HOST_API + user + call);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuffer buffer = new StringBuffer();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1)
                buffer.append(chars, 0, read);
                apiResult = buffer.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {


    }
}
