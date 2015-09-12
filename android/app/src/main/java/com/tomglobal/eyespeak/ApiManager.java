package com.tomglobal.eyespeak;

import android.content.Context;
import android.content.Intent;
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
public class ApiManager extends AsyncTask<String, Void, Void> {

    private Context context;
    ArrayList<String> predictions = null;
    JSONArray responses;
    JSONObject apiResult;

    String HTTP_PRE = "http://";
    String HOST_API = "eyespeak.elasticbeanstalk.com/chat?version=1&text=";

    public ApiManager(Context ctx) {
        context = ctx;
    }

    @Override
    protected Void doInBackground(String... params) {
        BufferedReader reader = null;

        try {
            predictions = new ArrayList<String>();
            String call = params[0].replace(" ", "%20");
            Log.d("API Call: ", HTTP_PRE + HOST_API + call);
            URL url = new URL(HTTP_PRE + HOST_API + call);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuffer buffer = new StringBuffer();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1)
                buffer.append(chars, 0, read);
                apiResult = new JSONObject(buffer.toString());
                responses = apiResult.getJSONArray("responses");
            if (apiResult == null || responses.length() < 1) {
                return null;
            }
            else {
                int l;
                if (responses.length() > 4) l = 4;
                else l = responses.length();
                for (int i = 0; i < l;i++) {
                    predictions.add(responses.getString(i));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
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

        JSONArray json = new JSONArray();
        for (int i=0;i < predictions.size();i++) {
            json.put(predictions.get(i));
        }


        Intent intent = new Intent("predictions-received");
        intent.putExtra("predictions", predictions);
        intent.putExtra("json", apiResult.toString());
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

    }
}
