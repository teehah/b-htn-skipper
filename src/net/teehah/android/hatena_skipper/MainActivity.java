package net.teehah.android.hatena_skipper;

import java.io.IOException;
import java.net.URLDecoder;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		String url = getIntent().getDataString();
		if (url == null) {
			finish();
		} else {
			startExpantion(url);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	void startExpantion(String url) {
		final String TAG = "b.htn Skipper";
		Log.d(TAG, "original url:"+url);
		new AsyncTask<String, String, String>() {
			ProgressDialog mProgressDialog;

			@Override
			protected void onPreExecute() {
				mProgressDialog = new ProgressDialog(MainActivity.this);
				mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				mProgressDialog.show();
			}

			@Override
			protected String doInBackground(String... shortUrls) {
				String shortUrl = shortUrls[0];
				String expandedUrl = null;
				try {
					expandedUrl = sendRequest(shortUrls[0]);
				} catch (Exception e) {
					Log.e(TAG, "could not expand htn.to url.", e);
					expandedUrl = shortUrl;
				}
				return expandedUrl;
			}

			@Override
			protected void onPostExecute(String url) {
				mProgressDialog.dismiss();
				Uri uri = Uri.parse(url);
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				startActivity(intent);
				finish();
			}
			
			// SEE http://developer.hatena.ne.jp/ja/documents/shorturl/apis/redirectto
			String sendRequest(String shortUrl) throws Exception {
				Uri.Builder uriBuilder = new Uri.Builder();
				uriBuilder.scheme("http");
				uriBuilder.encodedAuthority("b.hatena.ne.jp");
				uriBuilder.path("/api/htnto/expand");
				uriBuilder.appendQueryParameter("shortUrl", shortUrl);

				HttpGet get = new HttpGet(uriBuilder.build().toString());
				get.setHeader(HTTP.CONTENT_TYPE, "application/json");

				DefaultHttpClient client = new DefaultHttpClient();
				String result = null;
				try {
					result = client.execute(get, new ResponseHandler<String>(){
						@Override
						public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
							return parseResponse(response);
						}
					});
				} catch (Exception e) {
					//nothing to do.
				} finally {
					client.getConnectionManager().shutdown();
				}
				
				return result;
			}

			// SEE https://gist.github.com/mala/5882869
			String parseResponse(HttpResponse httpResponse) {
				StatusLine statusLine = httpResponse.getStatusLine();
				if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
					Log.e(TAG, "hatena api returns an error:"+statusLine.getStatusCode()+":"+statusLine.getReasonPhrase());
					return null;
				}

				String expandedUrl = null;
				String responseString = null;
				try {
					responseString = EntityUtils.toString(httpResponse.getEntity(), HTTP.UTF_8);
					JSONObject response = new JSONObject(responseString);
					JSONArray expanded = response.getJSONObject("data").getJSONArray("expand");
					expandedUrl = expanded.getJSONObject(0).getString("long_url");
					Log.d(TAG, "expanded url:"+expandedUrl);
					String[][] fxxkingPrefices = {
						{"http://b.hatena.ne.jp/entry/s/", "https://"},
						{"http://b.hatena.ne.jp/entry/", "http://"},
					};
					for (String[] replacer : fxxkingPrefices) {
						if (expandedUrl.startsWith(replacer[0])) {
							expandedUrl = expandedUrl.replace(replacer[0], replacer[1]);
							break;
						}
					}
					Log.d(TAG, "opening url:"+expandedUrl);
				} catch (Exception e) {
					Log.e(TAG, "Parsing httpResponse failure:"+responseString, e);
				}
				return expandedUrl;
			}
		}.execute(url);
	}
}
