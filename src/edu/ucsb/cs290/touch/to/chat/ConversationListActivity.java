package edu.ucsb.cs290.touch.to.chat;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import edu.ucsb.cs290.touch.to.chat.crypto.DatabaseHelper;
import edu.ucsb.cs290.touch.to.chat.crypto.SealablePublicKey;

public class ConversationListActivity extends FragmentActivity implements
ConversationListFragment.Callbacks {

	private boolean mTwoPane;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState == null) {
			startActivityForResult(new Intent(getApplicationContext(),
					AuthActivity.class), 100);
		}
		setContentView(R.layout.activity_conversation_list);

		if (findViewById(R.id.conversation_detail_container) != null) {
			mTwoPane = true;
			((ConversationListFragment) getSupportFragmentManager()
					.findFragmentById(R.id.conversation_list))
					.setActivateOnItemClick(true);
		}
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.activity_conversation_view, menu);
		return true;
	}

	public boolean addContact(MenuItem item) {
		startActivityForResult(new Intent(getApplicationContext(),
				NewContactActivity.class), 101);
		return true;
	}

	@Override
	public void onItemSelected(String id) {
		if (mTwoPane) {
			Bundle arguments = new Bundle();
			arguments.putString(ConversationDetailFragment.ARG_ITEM_ID, id);
			ConversationDetailFragment fragment = new ConversationDetailFragment();
			fragment.setArguments(arguments);
			getSupportFragmentManager().beginTransaction()
			.replace(R.id.conversation_detail_container, fragment)
			.commit();

		} else {
			Intent detailIntent = new Intent(this,
					ConversationDetailActivity.class);
			detailIntent.putExtra(ConversationDetailFragment.ARG_ITEM_ID, id);
			startActivity(detailIntent);
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case 100:
			// Set password, initialize db, and generate keypair of doesn't exist.
			String derp = data.getExtras().getString("edu.ucsb.cs290.touch.to.chat.password");
			DatabaseHelper.getInstance(getApplicationContext()).initalizeInstance(derp);
			return;
		case 101:
			System.out.println("Contact added");
        	// String long SealablePublicKey byte[]
			// name time key signedSecret
			DatabaseHelper.getInstance(getApplicationContext()).addContact(data.getExtras().getString("name"),
		        	data.getExtras().getLong("date"),
					(SealablePublicKey)data.getExtras().get("key"),
					data.getExtras().getByteArray("signedsecret")
					);
			return;
		}
	}

}
