package com.thingswise.appframework.examples.rpc.authorization;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.thingswise.appframework.services.rpc.RPCHandlerException;
import com.thingswise.appframework.services.rpc.RPCMethodHandler;

public class SendAlertMethod implements RPCMethodHandler {

	private final String scope;
	
	private final String project;
	
	private static ThreadLocal<Gson> gson = new ThreadLocal<Gson>() {
		@Override
		protected Gson initialValue() {
			return new Gson();
		}		
	};
	
	private static Gson getGson() {
		return gson.get();
	}
	
	public SendAlertMethod(String scope, String project) {
		this.scope = scope;
		this.project = project;
	}

	@Override
	public ListenableFuture<List<RPCMethodResponse>> handle(List<List<Object>> requests) {
		List<ListenableFuture<RPCMethodResponse>> futures = new ArrayList<ListenableFuture<RPCMethodResponse>>(requests.size());
		for (List<Object> request : requests) {
			if (request.size() != 1) {
				futures.add(
					Futures.immediateFuture(new RPCMethodResponse(null, new RPCHandlerException("example:invalidSyntax", "Invalid input request"))));
			} else {
				URI backendUri = this.backendUri;
				
				if (backendUri == null) {
					futures.add(Futures.immediateFuture(new RPCMethodResponse(null, new RPCHandlerException(RPCHandlerException.INTERNAL_ERROR, "Backend not configured"))));
					continue;
				}
				
				HttpPost post = new HttpPost();
				post.setEntity(new StringEntity(getGson().toJson(request.get(0)), "application/json"));
				post.setURI(backendUri);
				
				final SettableFuture<RPCMethodResponse> future = SettableFuture.create();
				final Future<HttpResponse> invocation = HttpAsyncClients.createDefault().execute(post, new FutureCallback<HttpResponse>() {
					@Override
					public void cancelled() {
						
					}
					@Override
					public void completed(HttpResponse resp) {
						if (resp.getStatusLine().getStatusCode() != 200) {
							future.set(new RPCMethodResponse(null, new RPCHandlerException("example:backendError", String.format("Backend replied with error: %s", resp.getStatusLine()))));
						} else {
							future.set(new RPCMethodResponse(Collections.emptyList(), null));
						}
					}
					@Override
					public void failed(Exception err) {
						future.set(new RPCMethodResponse(null, new RPCHandlerException("example:backendInvocationError", "Backend invocation error", err)));
					}			
				});
				
				future.addListener(new Runnable() {
					@Override
					public void run() {
						if (future.isCancelled()) {
							// if cancelled then cleanup
							invocation.cancel(true);
						}
					}			
				}, MoreExecutors.sameThreadExecutor());
				
				futures.add(future);				
			}
						
		}
		
		return Futures.allAsList(futures);
		
	}
	
	private URI backendUri;

	@Override
	public ListenableFuture<Void> configure(Map<String, ?> properties) {
		String backendUri = (String) properties.get("backend.uri");
		if (backendUri != null) {
			try {
				this.backendUri = new URI(backendUri);
			} catch (URISyntaxException e) {
				return Futures.immediateFailedFuture(new Exception("Invalid backend URI syntax", e));
			}
		}
		return Futures.immediateFuture(null);
	}

}
