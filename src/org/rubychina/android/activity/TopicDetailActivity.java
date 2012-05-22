/*Copyright (C) 2012 Longerian (http://www.longerian.me)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/
package org.rubychina.android.activity;

import greendroid.app.GDActivity;
import greendroid.widget.ActionBarItem;
import greendroid.widget.ActionBarItem.Type;
import greendroid.widget.LoaderActionBarItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.rubychina.android.R;
import org.rubychina.android.RCApplication;
import org.rubychina.android.RCService;
import org.rubychina.android.RCService.LocalBinder;
import org.rubychina.android.api.request.TopicDetailRequest;
import org.rubychina.android.api.response.TopicDetailResponse;
import org.rubychina.android.type.Reply;
import org.rubychina.android.type.Topic;
import org.rubychina.android.type.User;
import org.rubychina.android.util.JsonUtil;
import org.rubychina.android.widget.ReplyAdapter;

import yek.api.ApiCallback;
import yek.api.ApiException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class TopicDetailActivity extends GDActivity {

	public static final String POS = "org.rubychina.android.activity.TopicDetailActivity.POSITION";
	public static final String TOPIC = "org.rubychina.android.activity.TopicDetailActivity.TOPIC";
	private static final String TAG = "TopicDetailActivity";
	
	private LoaderActionBarItem progress;
	private ImageView gravatar;
	private ListView replies;

	private RCService mService;
	private boolean isBound = false; 
	private Topic t;
	
	private TopicDetailRequest request;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setActionBarContentView(R.layout.topic_layout);
		progress = (LoaderActionBarItem) addActionBarItem(Type.Refresh, R.id.action_bar_refresh);
		
		t = JsonUtil.fromJsonObject(getIntent().getStringExtra(TOPIC), Topic.class);
		
		setTitle(t.getTitle());
		
		Intent intent = new Intent(this, RCService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}
	
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            isBound = true;
            startTopicDetailRequest(t.getId());
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(mConnection);
            isBound = false;
        }
    }
	
	@Override
	public boolean onHandleActionBarItemClick(ActionBarItem item, int position) {
		switch (item.getItemId()) {
        case R.id.action_bar_refresh:
        	startTopicDetailRequest(t.getId());
        	return true;
        default:
            return super.onHandleActionBarItemClick(item, position);
		}
	}
	
	private void startTopicDetailRequest(int id) {
		if(request == null) {
			request = new TopicDetailRequest(id);
		}
		request.setId(id);
		((RCApplication) getApplication()).getAPIClient().request(request, new TopicDetailCallback());
		progress.setLoading(true);
	}
	
	private class TopicDetailCallback implements ApiCallback<TopicDetailResponse> {

		@Override
		public void onException(ApiException e) {
			Toast.makeText(getApplicationContext(), R.string.hint_network_error, Toast.LENGTH_SHORT).show();
			progress.setLoading(false);
			refreshView(new ArrayList<Reply>());
		}

		@Override
		public void onFail(TopicDetailResponse r) {
			Toast.makeText(getApplicationContext(), R.string.hint_loading_data_failed, Toast.LENGTH_SHORT).show();
			progress.setLoading(false);
			refreshView(new ArrayList<Reply>());
		}

		@Override
		public void onSuccess(TopicDetailResponse r) {
			progress.setLoading(false);
			refreshView(r.getReplies());
		}
		
	}
	
	private void refreshView(List<Reply> rs) {
		if(replies == null) {
			replies = (ListView) findViewById(R.id.replies);
			replies.addHeaderView(initializeTopicBody(), null, false);
		}
		Collections.sort(rs);
		replies.setAdapter(new ReplyAdapter(this, R.layout.reply_item,
				R.id.body, rs));
	}
	
	private View initializeTopicBody() {
		View body = LayoutInflater.from(getApplicationContext()).inflate(R.layout.topic_body_layout, null);
		
		gravatar = (ImageView) body.findViewById(R.id.gravatar);
		gravatar.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				visitUserProfile(t.getUser());
			}
		});
		mService.requestUserAvatar(t.getUser(), gravatar, 0);
		
		TextView title = (TextView) body.findViewById(R.id.title);
		title.setText(t.getTitle());
		
		TextView bodyText = (TextView) body.findViewById(R.id.body);
		executeRetrieveSpannedTask(bodyText, t.getBodyHTML());
		return body;
	}
	
	public void visitUserProfile(User u) {
		Intent i = new Intent(getApplicationContext(), UserProfileActivity.class);
		i.putExtra(UserProfileActivity.VIEW_PROFILE, JsonUtil.toJsonObject(u));
		startActivity(i);
	}
	
	public void requestUserAvatar(User u, ImageView v, int size) {
		if(isBound) {
			mService.requestUserAvatar(u, v, size);
		}
	}
	
	public void executeRetrieveSpannedTask(TextView tv, String html) {
		new RetrieveSpannedTask(tv).execute(html);
	}
	
	private class RetrieveSpannedTask extends AsyncTask<String, Void, Spanned> {

		private TextView htmlView;
		
		public RetrieveSpannedTask(TextView htmlView) {
			this.htmlView = htmlView;
		}
		
		@Override
		protected void onPreExecute() {
			progress.setLoading(true);
		}

		@Override
		protected Spanned doInBackground(String... params) {
			return Html.fromHtml(params[0], mService.getImageGetter(), null);
		}
		
		@Override
		protected void onPostExecute(Spanned result) {
			progress.setLoading(false);
			htmlView.setText(result);
		}
		
	}
	
}
