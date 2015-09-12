package com.tomglobal.eyespeak;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ConversationFragment extends Fragment implements GridView.OnItemClickListener {

    List<String> phrases;
    private int mBindFlag;
    private Messenger mServiceMessenger;
    TextView speechTextView;
    EditText newPhraseEditText;
    Intent speechService;
    TextToSpeech tts;
    ArrayAdapter adapter;
    Button listenButton;
    public ProgressBar spinner;
    GridView gridView;
    android.support.design.widget.FloatingActionButton floatingActionButton;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;

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

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(getActivity(), "Bluetooth is not available", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
        View view = inflater.inflate(R.layout.fragment_conversation, null);


        gridView = (GridView) view.findViewById(R.id.gridView);

        adapter = new ArrayAdapter<String>(getActivity(),
                R.layout.phrase_grid_item, R.id.text1, phrases);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(this);
        gridView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    return true;
                }
                return false;
            }

        });

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
        newPhraseEditText = (EditText) view.findViewById(R.id.newPhraseEditText);
        floatingActionButton = (android.support.design.widget.FloatingActionButton) view.findViewById(R.id.floatingButton);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (newPhraseEditText.getVisibility() == View.GONE) {
                    newPhraseEditText.setVisibility(View.VISIBLE);
                    newPhraseEditText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(newPhraseEditText, InputMethodManager.SHOW_IMPLICIT);
                } else {

                }

            }
        });
        Button repeatBtn = (Button) view.findViewById(R.id.repeatButton);
        repeatBtn.setOnClickListener(new View.OnClickListener() {
                                         @Override
                                         public void onClick(View v) {
                                             ConvertTextToSpeech("Can you please repeat that for me.");
                                         }
                                     }
        );
        return view;
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase("predictions-received")) {
                phrases = intent.getStringArrayListExtra("predictions");
                String json = intent.getStringExtra("json");

                adapter = new ArrayAdapter<String>(getActivity(),
                        R.layout.phrase_grid_item, R.id.text1, phrases);
                gridView.setAdapter(adapter);
                sendMessage(json);
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
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
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
        if (mChatService != null) {
            mChatService.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ConvertTextToSpeech(phrases.get(position));
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
            /*
            try {
                Message msg = new Message();
                msg.what = SpeechRecognitionService.MSG_RECOGNIZER_START_LISTENING;
                mServiceMessenger.send(msg);
                spinner.setVisibility(View.VISIBLE);
                listenButton.setText("Stop Listening");
            }
            catch (RemoteException e)
            {
                e.printStackTrace();
            }
            */
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
    private void setupChat() {

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Activity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    ConvertTextToSpeech(readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }

        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
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
