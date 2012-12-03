package edu.ucsb.cs290.touch.to.chat;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.app.Fragment;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import edu.ucsb.cs290.touch.to.chat.crypto.CryptoContacts;
import edu.ucsb.cs290.touch.to.chat.crypto.CryptoContacts.Contact;
import edu.ucsb.cs290.touch.to.chat.crypto.DatabaseHelper;
import edu.ucsb.cs290.touch.to.chat.crypto.MessagesListCursorAdapter;
import edu.ucsb.cs290.touch.to.chat.https.TorProxy;
import edu.ucsb.cs290.touch.to.chat.remote.messages.Message;
import edu.ucsb.cs290.touch.to.chat.remote.messages.ProtectedMessage;
import edu.ucsb.cs290.touch.to.chat.remote.messages.SignedMessage;
import edu.ucsb.cs290.touch.to.chat.remote.messages.TokenAuthMessage;

public class ConversationDetailFragment extends Fragment {

	public static final String ARG_ITEM_ID = "contact name";

	CryptoContacts.Contact mItem;
	ListView messageList;
	EditText messageText;
	View rootView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments().containsKey(ARG_ITEM_ID)) {
			mItem = (CryptoContacts.Contact) getArguments().get(ARG_ITEM_ID);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		rootView = inflater.inflate(R.layout.fragment_conversation_detail,
				container, false);
		messageList = (ListView) rootView.findViewById(R.id.messages_list);
		if(((KeyActivity) getActivity()).mBound) {
			inflateContact();
		}
		return rootView;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		((CursorAdapter) messageList.getAdapter()).getCursor().close();
	};
	
	private void sendMessage(View v) {
		EditText messageToSend = (EditText) v
				.findViewById(R.id.edit_message_text);
		if (messageToSend.getText() == null) {
			return;
		}
		Message m = new Message(messageToSend.getText().toString());
		ProtectedMessage pm = null;
		SignedMessage signedMessage = null;
		DatabaseHelper instance = ((KeyActivity) getActivity()).getInstance();
		try {
			signedMessage = new SignedMessage(m, instance.getSigningKey());
			pm = new ProtectedMessage(signedMessage, mItem.getEncryptingKey(),
					instance.getSigningKey());

			instance.addOutgoingMessage(signedMessage, mItem);

		} catch (GeneralSecurityException e) {
			Logger.getLogger("touch-to-text").log(Level.SEVERE,
					"Problem creating ProtectedMessage!", e);
		} catch (IOException e) {
			Logger.getLogger("touch-to-text").log(Level.SEVERE,
					"Problem creating ProtectedMessage!", e);
		}
		TokenAuthMessage tm = new TokenAuthMessage(pm, mItem.getSigningKey(),
				mItem.getToken());
		TorProxy.sendMessage(tm);
	}

	private class GetMessagesFromDBTask extends AsyncTask<Object, Void, Cursor> {
		private PublicKey author;
		private PublicKey self;
		public GetMessagesFromDBTask(PublicKey self, Contact mItem) {
			this.author = mItem.getSigningKey();
			this.self = self;
		}
		
		@Override
		protected Cursor doInBackground(Object... ids) {
			
			Log.v("touch-to-text", "Updating message view");
			DatabaseHelper databaseHelper = (DatabaseHelper) ids[0];
			return databaseHelper.getMessagesCursor((Long) ids[1]);
		}

		@Override
		protected void onPostExecute(Cursor result) {
			super.onPostExecute(result);
			if (messageList.getAdapter() != null) {
				((CursorAdapter) messageList.getAdapter()).swapCursor(result).close();
			} else {
				MessagesListCursorAdapter s = new MessagesListCursorAdapter(
						getActivity(),result, author, self);
				messageList.setAdapter(s);
			}
		}
	}

	public void onServiceConnected() {
		new GetMessagesFromDBTask(((KeyActivity) getActivity()).mService.getInstance().getPGPPublicKey().sign(), mItem).execute(
				((KeyActivity) getActivity()).mService.getInstance(), mItem);
		if (rootView != null) {
			inflateContact();
		}
	}

	public void inflateContact() {
		rootView.findViewById(R.id.send_message_button)
				.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						sendMessage(rootView);

					}
				});
	}
}
