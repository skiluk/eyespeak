package com.tomglobal.eyespeak;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.app.ListFragment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ConversationFragment extends ListFragment {

    List<String> phrases;
    private int mBindFlag;
    private Messenger mServiceMessenger;
    TextView speechTextView;
    Intent speechService;
    TextToSpeech tts;
    ArrayAdapter adapter;
    Button listenButton;
    public ProgressBar spinner;

    private BluetoothChatService mBluetoothService = null;

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;

    // TODO: Rename and change types of parameters
    public static ConversationFragment newInstance(String param1, String param2) {
        ConversationFragment fragment = new ConversationFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public ConversationFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        phrases = new ArrayList<String>();
        phrases.add("I am doing great.");
        phrases.add("I am doing good.");
        phrases.add("I am doing okay.");

        adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_list_item_1, android.R.id.text1, phrases);
        setListAdapter(adapter);

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver,
                        new IntentFilter("speech-recognition-finished"));
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver,
                new IntentFilter("predictions-received"));

        speechService = new Intent(getActivity(), SpeechRecognitionService.class);
        getActivity().startService(speechService);
        mBindFlag = Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH ? 0 : Context.BIND_ABOVE_CLIENT;

        tts = new TextToSpeech(getActivity(), new TextToSpeech.OnInitListener() {

            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("error", "This language is not supported");
                    }
                } else
                    Log.e("error", "TTS Initialization Failed!");
            }
        });
        tts.setOnUtteranceProgressListener(new ttsUtteranceListener());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
        View view = inflater.inflate(R.layout.fragment_conversation, null);
        speechTextView = (TextView) view.findViewById(R.id.predictionTextView);
        listenButton = (Button) view.findViewById(R.id.listenButton);
        spinner = (ProgressBar) view.findViewById(R.id.spinner);
        listenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (spinner.getVisibility() == View.INVISIBLE) {
                    startListening();
                } else {
                    stopListening();
                }
            }
        });
        return view;
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase("predictions-received")) {
                phrases = intent.getStringArrayListExtra("predictions");
                String json = intent.getStringExtra("json");
                adapter = new ArrayAdapter<String>(getActivity(),
                        android.R.layout.simple_list_item_1, android.R.id.text1, phrases);
                setListAdapter(adapter);
                byte[] send = json.getBytes();
                mBluetoothService.write(send);
            }
            else {
                spinner.setVisibility(View.INVISIBLE);
                listenButton.setText("Listen");
                String message = intent.getStringExtra("message");
                speechTextView.setText(message);
            }
        }
    };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().bindService(new Intent(getActivity(), SpeechRecognitionService.class), mServiceConnection, mBindFlag);
        try {
            mListener = (OnFragmentInteractionListener) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString()
                    + " must implement OnFragmentInteractionListener");
        }
        mBluetoothService = new BluetoothChatService(getActivity(), new Handler());
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mServiceMessenger != null) {
            getActivity().unbindService(mServiceConnection);
            mServiceMessenger = null;
        }
    }

    @Override
    public void onDestroy () {
        getActivity().stopService(speechService);
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mMessageReceiver);
        if(tts != null){
            tts.stop();
            tts.shutdown();
        }
        if (mBluetoothService != null) {
            mBluetoothService.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBluetoothService != null) {
            if (mBluetoothService.getState() == BluetoothChatService.STATE_NONE) {
                mBluetoothService.start();
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        if (null != mListener) {
            ConvertTextToSpeech(phrases.get(position));
        }
    }

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(String id);
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            mServiceMessenger = new Messenger(service);
            Message msg = new Message();
            msg.what = SpeechRecognitionService.MSG_RECOGNIZER_START_LISTENING;

            try {
                mServiceMessenger.send(msg);
                spinner.setVisibility(View.VISIBLE);
                listenButton.setText("Stop Listening");
            }
            catch (RemoteException e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            mServiceMessenger = null;
        }
    };


    private void ConvertTextToSpeech(String text) {

        if(text != null || !text.equals("")) {
            HashMap<String, String> map = new HashMap<String, String>();
            map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, map);
        }

    }

    private void startListening() {

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spinner.setVisibility(View.VISIBLE);
                listenButton.setText("Stop Listening");
            }
        });

        try {
            Message msg = Message.obtain(null, SpeechRecognitionService.MSG_RECOGNIZER_CANCEL);
            mServiceMessenger.send(msg);
            msg = Message.obtain(null, SpeechRecognitionService.MSG_RECOGNIZER_START_LISTENING);
            mServiceMessenger.send(msg);
        }
        catch (RemoteException e)
        {
            e.printStackTrace();
        }
    }

    private void stopListening() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spinner.setVisibility(View.INVISIBLE);
                listenButton.setText("Listen");
            }
        });
        try {
            Message msg = Message.obtain(null, SpeechRecognitionService.MSG_RECOGNIZER_CANCEL);
            mServiceMessenger.send(msg);
        }
        catch (RemoteException e)
        {
            e.printStackTrace();
        }
    }

    public class ttsUtteranceListener extends UtteranceProgressListener {

        @Override
        public void onDone(String utteranceId) {
            startListening();
        }

        @Override
        public void onStart(String utteranceId) {

        }
        @Override
        public void onError(String utteranceId) {

        }
    }
}
