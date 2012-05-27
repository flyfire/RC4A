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
package org.rubychina.android.fragment;

import java.util.List;

import org.rubychina.android.R;
import org.rubychina.android.activity.RubyChinaActor;
import org.rubychina.android.activity.UserIndexActivity;
import org.rubychina.android.api.request.TopicsRequest;
import org.rubychina.android.api.response.TopicsResponse;
import org.rubychina.android.type.Node;
import org.rubychina.android.type.Topic;
import org.rubychina.android.type.User;
import org.rubychina.android.util.LogUtil;
import org.rubychina.android.widget.TopicAdapter;

import yek.api.ApiCallback;
import yek.api.ApiException;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;

public class TopicListFragment extends SherlockFragment implements TopicActor {

	private static final String TAG = "TopicListFragment";
	public static final String NODE = "node";
	
	private OnTopicSelectedListener listener;
	private RubyChinaActor rubyChina;
	private TopicsRequest request;
	
	private TextView nodeSection;
	private ListView topicList;
	
	private Node node;
	
	public static TopicListFragment newInstance(Node node) {
		TopicListFragment f = new TopicListFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(NODE, node);
        f.setArguments(bundle);
        return f;
    }
	
    public void setNode(Node node) {
		this.node = node;
	}

	public Node getNode() {
		return node;
	}

	@Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        LogUtil.d(TAG, "onAttach");
        try {
            listener = (OnTopicSelectedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnTopicSelectedListener");
        }
        try {
        	rubyChina = (RubyChinaActor) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement RubyChinaActor");
        }
    }
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LogUtil.d(TAG, "onCreate");
		if(getArguments() != null) {
			node = getArguments().getParcelable(NODE);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		LogUtil.d(TAG, "onCreateView");
    	View view = inflater.inflate(R.layout.topics_layout, null);
    	topicList = (ListView) view.findViewById(R.id.topics);
		return view;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		LogUtil.d(TAG, "onActivityCreated");
		List<Topic> cachedTopics = fetchTopics();
		refreshPage(cachedTopics, node);//TODO node info must also be persisted
		startTopicsRequest(node);
	}

	private List<Topic> fetchTopics() {
		return rubyChina.getService().fetchTopics();
	}

    @Override
	public void onDestroyView() {
		super.onDestroyView();
		LogUtil.d(TAG, "onDestroyView");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		LogUtil.d(TAG, "onDestroy");
		cancelTopicsRequest();
	}

	@Override
	public void onDetach() {
		super.onDetach();
		LogUtil.d(TAG, "onDetach");
	}

	private void initializeHead(Node node) {
		if(nodeSection == null) {
			nodeSection = (TextView) LayoutInflater.from(getActivity()).inflate(R.layout.node_section_header, null);
		}
		topicList.addHeaderView(nodeSection, null, false);
		nodeSection.setText(node.getName());
	}
	
	private void refreshPage(List<Topic> topics, Node node) {
//		initializeHead(node);//TODO
		TopicAdapter adapter = new TopicAdapter(this, getActivity(),
				R.layout.topic_item,
				R.id.title, 
				topics);
		topicList.setAdapter(adapter);
		topicList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
//		        listener.onTopicSelected(((TopicAdapter) ((HeaderViewListAdapter) parent.getAdapter()).getWrappedAdapter()).getItems(), 
//						position - 1);
		        listener.onTopicSelected(((TopicAdapter) (parent.getAdapter())).getItems(), 
		        		position);
			}
		});
	}
	
	public void startTopicsRequest(Node node) {
		if(request == null) {
			request = new TopicsRequest();
		}
		request.setNodeId(node.getId());
		request.setSize(rubyChina.getApp().getPageSize());
		rubyChina.getClient().request(request, new ActiveTopicsCallback());
		rubyChina.showIndeterminateProgressBar();
	}
	
	private void cancelTopicsRequest() {
		if(request != null) {
			rubyChina.getClient().cancel(request);
			rubyChina.hideIndeterminateProgressBar();
		}
	}
	
	private class ActiveTopicsCallback implements ApiCallback<TopicsResponse> {

		@Override
		public void onException(ApiException e) {
			rubyChina.hideIndeterminateProgressBar();
			Toast.makeText(getActivity(), R.string.hint_network_error, Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onFail(TopicsResponse r) {
			rubyChina.hideIndeterminateProgressBar();
			Toast.makeText(getActivity(), R.string.hint_loading_data_failed, Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onSuccess(TopicsResponse r) {
			rubyChina.hideIndeterminateProgressBar();
			refreshPage(r.getTopics(), node);
			new CacheTopicsTask().execute(r.getTopics());
		}
		
	}
	
	private class CacheTopicsTask extends AsyncTask<List<Topic>, Void, Void> {

		@Override
		protected void onPreExecute() {
			rubyChina.showIndeterminateProgressBar();
		}

		@Override
		protected Void doInBackground(List<Topic>... params) {
			rubyChina.getService().clearTopics();
			rubyChina.getService().insertTopics(params[0]);
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			rubyChina.hideIndeterminateProgressBar();
		}
		
	}

	@Override
	public void visitUserProfile(User u) {
		Intent i = new Intent(getActivity(), UserIndexActivity.class);
		Bundle b = new Bundle();
		b.putParcelable(UserIndexActivity.VIEW_PROFILE, u);
		i.putExtras(b);
		startActivity(i);
	}
	
	@Override
	public void requestUserAvatar(User u, ImageView v, int size) {
		rubyChina.getService().requestUserAvatar(u, v, size);
	}
	
	public interface OnTopicSelectedListener {
        public void onTopicSelected(List<Topic> topics, int position);
    }
	
}
