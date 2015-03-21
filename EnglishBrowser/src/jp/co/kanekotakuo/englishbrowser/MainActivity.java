package jp.co.kanekotakuo.englishbrowser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jp.co.kanekotakuo.englishbrowser.Tag.IgnoreTag;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class MainActivity extends Activity {
	private EditText mEditTxtUrl;
	private TextView mTxtView;
	private TextView mTxtWord;
	private Tag mRoot = null;
	private IgnoreTag mNoDispTag = new IgnoreTag("span", "class", "kana");
	private boolean mIsWriteHtmlToFile = false;
	private Dictionary mDictionary;
	private String mWord;
	private AlertDialog mWordDialog;
	private EditText mWordDialogEtx;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mTxtView = (TextView) this.findViewById(R.id.ID_TxtView);
		// mTxtView.setMovementMethod(ScrollingMovementMethod.getInstance());
		mTxtView.setMovementMethod(LinkMovementMethod.getInstance());

		mTxtWord = (TextView) this.findViewById(R.id.ID_TxtWord);

		mEditTxtUrl = (EditText) this.findViewById(R.id.ID_Url);
		mEditTxtUrl.setOnEditorActionListener(new OnEditorActionListener() {

			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					String url = v.getText().toString();
					if (TextUtils.isEmpty(url)) return false;
					if (!url.startsWith("http://")) {
						url = "http://" + url;
					}
					Log.i("", "url:" + url);
					final String fUrl = url;
					Thread th = new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								mRoot = getAndParseHtml(fUrl);
								parseArticle(mRoot);
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					});
					th.start();
				}
				return false;
			}
		});
		mEditTxtUrl.setText("http://www.japantimes.co.jp");

		Builder bld = new AlertDialog.Builder(this);
		LayoutInflater inf = LayoutInflater.from(this);
		View v = inf.inflate(R.layout.word_dialog, null);
		mWordDialogEtx = (EditText) v.findViewById(R.id.ETX_word);
		bld.setView(v);
		bld.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				mTxtWord.requestFocus();
				String ss = mWordDialogEtx.getText().toString();
				if (ss != null && !ss.isEmpty()) {
					searchWord(ss);
				}
			}
		});
		bld.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				mTxtWord.requestFocus();
			}
		});
		mWordDialog = bld.create();

		mDictionary = new Dictionary(this.getApplicationContext());
	}

	@Override
	public void onResume() {
		super.onResume();
		mDictionary.open();
	}

	@Override
	public void onPause() {
		super.onPause();
		mDictionary.close();
	}

	/**
	 * HTMLの取得とパース
	 * 
	 * @param urlStr
	 * @throws IOException
	 */
	private Tag getAndParseHtml(String urlStr) throws IOException {
		HttpURLConnection con = null;
		URL url = new URL(urlStr);
		con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.setInstanceFollowRedirects(false);
		con.setRequestProperty("Accept-Language", "jp");
		con.connect();

		Map<String, List<String>> headers = con.getHeaderFields();
		Iterator<String> headerIt = headers.keySet().iterator();
		String header = null;
		while (headerIt.hasNext()) {
			String headerKey = (String) headerIt.next();
			header += headerKey + "：" + headers.get(headerKey) + "\r\n";
		}

		InputStream is = con.getInputStream();
		byte[] buf = readBytes(is);
		is.close();
		String body = new String(buf);
		String s = header + "\r\n" + buf.length + "bytes\r\n" + body;
		Log.d("", "Size:" + s.length());
		if (mIsWriteHtmlToFile) {
			writeToFile(body);
		}
		return HtmlParser.parse(body);
	}

	private void parseArticle(Tag root) {
		String s = "";
		List<Tag> articles = root.collect("article");
		int i = 1;
		for (Tag a : articles) {
			Tag p = a.find("p");
			if (p != null) {
				Log.i("", "No." + (i++) + ": " + p.text);
				s += p.text + "\n  - - - - - - - -\n";
			}
		}

		final SpannableStringBuilder sb = new SpannableStringBuilder();
		int st = 0;
		int ed = 0;
		char ch;
		int tail = s.length();
		while (st < tail) {
			ch = s.charAt(st);
			if (!Character.isLetter(ch)) {
				st++;
				continue;
			}
			if (st > ed) {
				sb.append(s.substring(ed, st));
			}
			ed = st + 1;
			while (ed < tail) {
				ch = s.charAt(ed);
				if (!Character.isLetter(ch)) {
					break;
				}
				ed++;
			}
			String ss = s.substring(st, ed);
			appendStrToSbAsSpan(sb, ss);
			st = ed;
		}
		this.runOnUiThread(new Runnable() {

			@Override
			public void run() {
				mTxtView.setText(sb);
				mTxtWord.setFocusable(true);
				mTxtWord.setFocusableInTouchMode(true);
			}
		});
	}

	private void appendStrToSbAsSpan(SpannableStringBuilder sb, final String ss) {
		int len = sb.length();
		sb.append(ss);
		ClickableSpan cs = new ClickableSpan() {
			@Override
			public void updateDrawState(TextPaint ds) {
				super.updateDrawState(ds);
				ds.setUnderlineText(false);
				ds.setColor(Color.BLACK);
			}

			@Override
			public void onClick(View widget) {
				// Toast.makeText(getApplicationContext(), ss,
				// Toast.LENGTH_SHORT).show();
				searchWord(ss);
			}
		};
		sb.setSpan(cs, len, len + ss.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
	}

	private void searchWord(final String word) {
		String explain = mDictionary.find(word);
		if (explain != null) {
			Log.e("", "Found : " + word);
			setExplainText(word, explain);
			return;
		}
		final String fUrl = "http://eow.alc.co.jp/search?q=" + word;

		Thread th = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Tag root = getAndParseHtml(fUrl);
					final String explain = parseDictionary(root);
					if (explain == null) return;
					mDictionary.register(word, explain);
					setExplainText(word, explain);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		th.start();
	}

	private void setExplainText(final String word, final String res) {
		mWord = word;
		mTxtWord.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mWordDialogEtx.setText(word);
				mWordDialogEtx.setSelection(word.length());
				mWordDialog.show();
			}
		});
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Spanned h = Html.fromHtml(word + res);
				mTxtWord.setText(h);
			}
		});
	}

	protected String parseDictionary(Tag root) {
		Tag rl = root.find("div", "resultsList");
		if (rl == null) return null;
		Tag ul = rl.find("ul");
		if (ul == null) return null;
		Tag li = ul.find("li");
		if (li == null) return null;
		Tag div = li.find("div");
		if (div == null) return null;
		Log.i("", "" + div.text);
		StringBuffer sb = new StringBuffer();
		div.addToStringBuffer(sb, mNoDispTag);
		String s = sb.toString();

		return s;
	}

	private byte[] readBytes(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte b[] = new byte[1024];
		while (true) {
			int sz = is.read(b);
			if (sz < 0) break;
			baos.write(b, 0, sz);
		}
		byte[] buf = baos.toByteArray();
		return buf;
	}

	private void writeToFile(String body) throws FileNotFoundException, IOException {
		File path = Environment.getExternalStorageDirectory();
		FileOutputStream fos = new FileOutputStream(path.getAbsolutePath() + "/out.html");
		fos.write(body.getBytes());
		fos.flush();
		fos.close();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

}
