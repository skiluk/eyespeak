package com.tomglobal.eyespeak;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.widget.DrawerLayout;

public class MainActivity extends AppCompatActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks, ConversationFragment.OnFragmentInteractionListener, MainFragment.OnFragmentInteractionListener {

    private NavigationDrawerFragment mNavigationDrawerFragment;
    private CharSequence mTitle;
    BluetoothChatFragment bluetoothChatFragment;
    ConversationFragment conversationFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {

        FragmentManager fragmentManager = getFragmentManager();
        android.support.v4.app.FragmentManager supportFragmentManager = getSupportFragmentManager();
        if (position == 1) {

            if (bluetoothChatFragment != null) {
                supportFragmentManager.beginTransaction().remove(bluetoothChatFragment).commit();
            }
            if (conversationFragment == null) {
                conversationFragment = new ConversationFragment();
            }
            fragmentManager.beginTransaction()
                    .replace(R.id.container, conversationFragment)
                    .commit();
        }
        else if (position == 2) {
            Intent myIntent = new Intent(MainActivity.this, DeviceListActivity.class);
            startActivity(myIntent);
        }
        else if (position == 3) {

            if (conversationFragment != null) {
                fragmentManager.beginTransaction().remove(conversationFragment).commit();
            }
            if (bluetoothChatFragment == null) {
                bluetoothChatFragment = new BluetoothChatFragment();
            }
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, bluetoothChatFragment)
                    .commit();
        }
        else{
            fragmentManager.beginTransaction()
                .replace(R.id.container, new MainFragment())
                .commit();
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME|ActionBar.DISPLAY_SHOW_TITLE|ActionBar.DISPLAY_HOME_AS_UP);
        actionBar.setTitle(R.string.app_name);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    @Override
    public void onFragmentInteraction(String text) {

    }


}
