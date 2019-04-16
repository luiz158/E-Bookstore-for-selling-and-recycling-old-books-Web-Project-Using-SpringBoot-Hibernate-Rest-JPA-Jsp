package de.tubs.ibr.dtn.chat;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import de.tubs.ibr.dtn.SecurityService;
import de.tubs.ibr.dtn.SecurityUtils;
import de.tubs.ibr.dtn.Services;
import de.tubs.ibr.dtn.api.ServiceNotAvailableException;
import de.tubs.ibr.dtn.chat.service.ChatService;

public class MeFragment extends Fragment implements View.OnClickListener {
	
	private final static String TAG = "MeFragment";
	
	TextView mNickname = null;
	TextView mStatusMsg = null;
	ImageView mState = null;
	
	// ChatService
	private ChatService mChatService = null;
	private boolean mChatServiceBound = false;
	
	// Security API provided by IBR-DTN
	private SecurityService mSecurityService = null;
	private boolean mSecurityBound = false;
	
	// Security action item
	private MenuItem mItemKeyInfo = null;
	
	private ServiceConnection mSecurityConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			mSecurityService = SecurityService.Stub.asInterface(service);
			onPreferencesChanged();
		}

		public void onServiceDisconnected(ComponentName name) {
			mSecurityService = null;
		}
	};
	
	private ServiceConnection mChatConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			mChatService = ((ChatService.LocalBinder)service).getService();
			onPreferencesChanged();
		}

		public void onServiceDisconnected(ComponentName name) {
			mChatService = null;
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.me_fragment, container, false);
		
		RelativeLayout me_layout = (RelativeLayout)v.findViewById(R.id.roster_me_display);
		me_layout.setOnClickListener(this);
		
		mNickname = (TextView)v.findViewById(R.id.me_nickname);
		mStatusMsg = (TextView)v.findViewById(R.id.me_statusmessage);
		mState = (ImageView)v.findViewById(R.id.me_icon);
		
		return v;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		// enable options menu
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.me_menu, menu);
		mItemKeyInfo = (MenuItem)menu.findItem(R.id.itemKeyInfo);
		mItemKeyInfo.setVisible(false);
		onPreferencesChanged();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.itemKeyInfo:
			{
				// get pending intent to open security info
				PendingIntent pi = SecurityUtils.getSecurityInfoIntent(mSecurityService, null);
				try {
					if (pi != null) getActivity().startIntentSenderForResult(pi.getIntentSender(), 1, null, 0, 0, 0);
				} catch (SendIntentException e) {
					// intent error
				}
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		getActivity().unregisterReceiver(mUpdateListener);
	}

	@Override
	public void onResume() {
		super.onResume();
		
		IntentFilter filter = new IntentFilter(ChatService.ACTION_USERINFO_UPDATED);
		getActivity().registerReceiver(mUpdateListener, filter);
		
		onPreferencesChanged();
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		// Establish a connection with the security service
		try {
			Services.SERVICE_SECURITY.bind(getActivity(), mSecurityConnection, Context.BIND_AUTO_CREATE);
			mSecurityBound = true;
		} catch (ServiceNotAvailableException e) {
			// Security API not available
		}
		
		getActivity().bindService(new Intent(getActivity(), ChatService.class), mChatConnection, Context.BIND_AUTO_CREATE);
		mChatServiceBound = true;
	}

	@Override
	public void onStop() {
		if (mSecurityBound) {
		    getActivity().unbindService(mSecurityConnection);
		    mSecurityService = null;
		    mSecurityBound = false;
		}
		
		if (mChatServiceBound) {
			getActivity().unbindService(mChatConnection);
			mChatService = null;
			mChatServiceBound = false;
		}
		
		super.onStop();
	}

	@Override
	public void onClick(View v) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		String presence_tag = prefs.getString("presencetag", "unavailable");
		String presence_text = prefs.getString("statustext", "");
		
		MeDialog dialog = MeDialog.newInstance(presence_tag, presence_text);

		dialog.setOnChangeListener(new MeDialog.OnChangeListener() {
			public void onStateChanged(String presence, String message) {
				Log.d(TAG, "state changed: " + presence + ", " + message);
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
				Editor edit = prefs.edit();
				edit.putString("presencetag", presence);
				edit.putString("statustext", message);
				edit.commit();
				onPreferencesChanged();
			}
		});
		
		dialog.show(getActivity().getSupportFragmentManager(), "me");
	}
	
	private BroadcastReceiver mUpdateListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ChatService.ACTION_USERINFO_UPDATED.equals(intent.getAction())) {
				onPreferencesChanged();
			}
		}
	};

	private void onPreferencesChanged() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		String presence_tag = prefs.getString("presencetag", "unavailable");
		String presence_nick = (mChatService != null) ? mChatService.getLocalNickname() : "";
		String presence_text = prefs.getString("statustext", "");
		
		int presence_icon = R.drawable.online;
		if (presence_tag != null)
		{
			if (presence_tag.equalsIgnoreCase("unavailable"))
			{
				presence_icon = R.drawable.offline;
			}
			else if (presence_tag.equalsIgnoreCase("xa"))
			{
				presence_icon = R.drawable.xa;
			}
			else if (presence_tag.equalsIgnoreCase("away"))
			{
				presence_icon = R.drawable.away;
			}
			else if (presence_tag.equalsIgnoreCase("dnd"))
			{
				presence_icon = R.drawable.busy;
			}
			else if (presence_tag.equalsIgnoreCase("chat"))
			{
				presence_icon = R.drawable.online;
			}
		}
		mState.setImageResource(presence_icon);
		mNickname.setText(presence_nick);
		
		if (presence_text.length() > 0) {
			mStatusMsg.setText(presence_text);
		} else {
			mStatusMsg.setText(this.getResources().getText(R.string.no_status_message));
		}
		
		if (mSecurityService != null)
		{
			// get local security info
			Bundle keyinfo = SecurityUtils.getSecurityInfo(mSecurityService, null);
			if (mItemKeyInfo != null) {
				if (keyinfo != null) {
					mItemKeyInfo.setVisible(true);
				} else {
					mItemKeyInfo.setVisible(false);
				}
			}
		}
	}
}
