package jp.co.kanekotakuo.englishbrowser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jp.co.kanekotakuo.englishbrowser.Tag.IgnoreTag;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
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
import android.view.View.OnLongClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class MainActivity extends Activity {
	private EditText mEditTxtUrl;
	private ScrollView mScrlViewWord;
	private TextView mTxtView;
	private TextView mTxtWord;
	private IgnoreTag mNoDispTag = new IgnoreTag("span", "class", "kana");
	private boolean mIsWriteHtmlToFile = false;
	private Dictionary mDictionary;
	private String mWord;
	private AlertDialog mWordDialog;
	private EditText mWordDialogEtx;
	private Map<String, Tag> mHtmlMap = new HashMap<String, Tag>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mTxtView = (TextView) this.findViewById(R.id.ID_TxtView);
		// mTxtView.setMovementMethod(ScrollingMovementMethod.getInstance());
		mTxtView.setMovementMethod(LinkMovementMethod.getInstance());

		mScrlViewWord = (ScrollView) this.findViewById(R.id.ID_WordScrollView);

		mTxtWord = (TextView) this.findViewById(R.id.ID_TxtWord);

		this.findViewById(R.id.BTN_left).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				procNextArticle(-1);
			}
		});
		this.findViewById(R.id.BTN_right).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				procNextArticle(1);
			}
		});

		mEditTxtUrl = (EditText) this.findViewById(R.id.ID_Url);
		mEditTxtUrl.setOnEditorActionListener(new OnEditorActionListener() {

			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
					String url = v.getText().toString();
					if (TextUtils.isEmpty(url)) return false;
					if (!url.startsWith("http://")) {
						url = "http://" + url;
					}
					Log.i("", "url:" + url);
					procUrl(url);
				}
				return false;
			}
		});
		// mEditTxtUrl.setText("http://www.japantimes.co.jp");
		mEditTxtUrl.setText("http://k.nhk.jp/daily/index1.html");

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
	 * 
	 * @param url
	 */
	private void procUrl(String url) {
		final String fUrl = url;
		Thread th = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Tag root = mHtmlMap.get(fUrl);
					if (root == null) {
						root = getAndParseHtml(fUrl);
						mHtmlMap.put(fUrl, root);
					}
					parseArticle(root);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		th.start();
	}

	private void procNextArticle(int d) {
		if (mEditTxtUrl == null) return;
		String url = mEditTxtUrl.getText().toString();
		if (TextUtils.isEmpty(url)) return;
		final String pre = "index";
		final int pre_len = pre.length();
		int i = url.indexOf(pre);
		if (i < 0) return;
		i += pre_len;
		if (url.length() < i) return;
		try {
			int n = Integer.parseInt(url.substring(i, i + 1)) + d;
			if (n < 1 || n > 9) return;
			url = url.substring(0, i) + n + url.substring(i + 1);
			procUrl(url);
		} catch (Exception e) {
			return;
		}
	}

	/**
	 * HTMLÇÃéÊìæÇ∆ÉpÅ[ÉX
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
			header += headerKey + "ÅF" + headers.get(headerKey) + "\r\n";
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

	private String parseArticleSub(Tag root) {
		StringBuffer sb = new StringBuffer();
		List<Tag> articles = root.collect("hr");
		for (Tag a : articles) {
			String text = a.text;
			if (text == null || text.isEmpty()) continue;
			if (text.length() < 10) continue;
			sb.append(text);
			sb.append("\n  - - - - - - - -\n");
		}
		return sb.toString();
	}

	private String _parseArticleSub(Tag root) {
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
		return s;
	}

	private void parseArticle(Tag root) {
		String s = parseArticleSub(root);
		s = decodeEscape(s);
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

	private String decodeEscape(String s) {
		final int esc_len = 6;
		for (int i = 0; i < 100; i++) {
			int idx = s.indexOf("&#");
			if (idx < 0 || s.length() < idx + esc_len) break;
			String esc = s.substring(idx, idx + esc_len);
			if (esc.charAt(esc_len - 1) != ';') break;
			try {
				char ch = (char) Integer.parseInt(esc.substring(2, 5), 10);
				String rep = Character.toString(ch);
				s = s.substring(0, idx) + rep + s.substring(idx + esc_len);
			} catch (Exception e) {
				break;
			}
		}
		return s;
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
				widget.invalidate();
				mWord = null;
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
		if (word == null) return;
		mTxtWord.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mWord = word;
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
		if (mWord == null || word.equals(mWord)) {
			mTxtWord.setLongClickable(false);
		} else {
			mTxtWord.setLongClickable(false);
			mTxtWord.setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
					b.setTitle("ï ñºìoò^: " + mWord);
					b.setPositiveButton("ìoò^", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							String explain = mDictionary.find(word);
							if (TextUtils.isEmpty(explain)) return;
							StringBuffer sb = new StringBuffer();
							sb.append(mDictionary.find(mWord));
							sb.append("ÅÑ");
							sb.append(mWord);
							sb.append(explain);
							mDictionary.register(mWord, sb.toString());
						}
					});
					b.setNegativeButton("ÉLÉÉÉìÉZÉã", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
						}
					});
					Dialog dlg = b.create();
					dlg.show();
					return true;
				}
			});
		}
		mScrlViewWord.scrollTo(0, 0);
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
