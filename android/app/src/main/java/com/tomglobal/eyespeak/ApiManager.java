package com.tomglobal.eyespeak;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
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
    JSONArray responses;
    JSONObject apiResult;
    SpeakItem utterance;

    String HTTP_PRE = "http://";
    String HOST_API = "eyespeak.elasticbeanstalk.com/chat?version=1&utterance=";

    //version=1
    //userId=
    //utterance=

    public ApiManager(Context ctx) {
        context = ctx;
    }

    @Override
    protected Void doInBackground(String... params) {
        BufferedReader reader = null;

        SharedPreferences prefs = context.getSharedPreferences("com.tomglobal.eyespeak", Context.MODE_PRIVATE);
        String user = prefs.getString("userId","eliza");
        utterance = new SpeakItem();

        try {
            Singleton.predictions.clear();
            String call = params[0].replace(" ", "%20");
            Log.d("API Call: ", HTTP_PRE + HOST_API + call + "&userId=" + user);
            URL url = new URL(HTTP_PRE + HOST_API + call + "&userId=" + user);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuffer buffer = new StringBuffer();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1)
                buffer.append(chars, 0, read);
                apiResult = new JSONObject(buffer.toString());
                utterance.Id = apiResult.getString("utteranceId");
                utterance.Text = apiResult.getString("utteranceText");
                responses = apiResult.getJSONArray("responses");
            if (apiResult == null || responses.length() < 1) {
                return null;
            }
            else {
                int l;
                if (responses.length() > 4) l = 4;
                else l = responses.length();
                for (int i = 0; i < l;i++) {
                    SpeakItem s = new SpeakItem();
                    s.utteranceId = utterance.Id;
                    s.Id = responses.getJSONObject(i).getString("responseId");
                    s.Text = responses.getJSONObject(i).getString("responseText");
                    Singleton.predictions.add(s);
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
        for (int i=0;i < Singleton.predictions.size();i++) {
            SpeakItem s = Singleton.predictions.get(i);
            json.put(s.Text);
        }
        Intent intent = new Intent("predictions-received");
        intent.putExtra("json", json.toString());
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

    }
}
