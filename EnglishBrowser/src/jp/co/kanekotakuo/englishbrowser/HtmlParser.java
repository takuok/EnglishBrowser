package jp.co.kanekotakuo.englishbrowser;

import java.io.IOException;

import jp.co.kanekotakuo.englishbrowser.Tag.AttrList;
import android.util.Log;

public class HtmlParser {
	private static class HtmlBuffer {
		StringBuffer sb;
		int index;
		int prevIndex;
		int length;

		HtmlBuffer(String html) {
			sb = new StringBuffer(html);
			index = prevIndex = 0;
			length = sb.length();
		}

		boolean skipToBegin(char tgt) {
			while (index < length) {
				if (sb.charAt(index) == tgt) {
					return true;
				}
				index++;
			}
			return false;
		}

		boolean startWith(String key) {
			int keyLen = key.length();
			if (index + keyLen > length) {
				return false;
			}
			for (int i = 0; i < keyLen; i++) {
				if (sb.charAt(index + i) != key.charAt(i)) {
					return false;
				}
			}
			return true;
		}

		void addToParentText(Tag parent) {
			String text = sb.substring(prevIndex, index).trim();
			if (text.length() > 0) {
				parent.text += text;
			}
		}

		boolean skipToEnd(String key) {
			int keyLen = key.length();
			int sbLen = sb.length();
			while (true) {
				if (index + keyLen > sbLen) return false;
				boolean f = true;
				for (int i = 0; i < keyLen; i++) {
					if (sb.charAt(index + i) != key.charAt(i)) {
						f = false;
						break;
					}
				}
				if (f) {
					index += keyLen;
					return true;
				}
				index++;
			}
		}

		boolean skipToEnd(char tgt) {
			int sbLen = sb.length();
			int cnt = 0;
			while (index < sbLen) {
				if (sb.charAt(index) == tgt && (cnt & 1) == 0) {
					index++;
					return true;
				}
				if (sb.charAt(index) == '"') {
					cnt++;
				}
				index++;
			}
			return false;
		}

		String getToken() {
			StringBuilder b = new StringBuilder();
			for (; index < length; index++) {
				char ch = sb.charAt(index);
				if (ch <= 0x20 || ch == '>' || ch == '=') {
					break;
				}
				b.append(ch);
			}
			if (b.length() > 0) {
				return b.toString();
			}
			return null;
		}

		public void recordIndex() {
			prevIndex = index;
		}

		public AttrList getAttrs() {
			AttrList ls = new AttrList();
			while (index < length) {
				if (!skipWhite()) {
					Log.w("", "not found attr close");
					return null;
				}
				char ch = sb.charAt(index);
				if (ch == '>' || (ch == '/' && sb.charAt(index + 1) == '>')) {
					break;
				}
				if (!isAlpha(ch)) {
					Log.v("", "not alphabet attr name / " + ch + " : " + index);
					index++;
					continue;
				}
				String key = getToken();
				if (!skipWhite()) {
					Log.w("", "not found '='");
					return null;
				}
				ch = sb.charAt(index);
				if (ch == '>' || (ch == '/' && sb.charAt(index + 1) == '>')) {
					Log.v("", "not found '=' / " + ch + " : " + index);
					break;
				}
				if (ch != '=') {
					Log.w("", "not found '=' / " + ch + " : " + index);
					return null;
				}
				index++;
				if (!skipWhite()) {
					Log.w("", "not found attr value");
					return null;
				}
				String val = null;
				ch = sb.charAt(index);
				if (ch == '"') {
					int dqIndex = index++;
					if (!skipToEnd('"')) {
						Log.w("", "not found close '\"' : " + index);
						return null;
					}
					val = sb.substring(dqIndex, index);
				} else {
					val = getToken();
				}
				ls.add(new Attr(key, val));
				if (!skipWhite()) {
					Log.w("", "not close attr : " + index);
					return null;
				}
			}
			return ls;
		}

		private boolean skipWhite() {
			for (; index < length; index++) {
				char ch = sb.charAt(index);
				if (ch <= 0x20) {
					continue;
				}
				return true;
			}
			return false;
		}

	}

	public static Tag parse(String html) throws IOException {
		Tag root = new Tag("root");
		Tag parent = root;
		HtmlBuffer hb = new HtmlBuffer(html);
		while (true) {
			if (!hb.skipToBegin('<')) {
				break;
			}
			if (hb.startWith("<!--")) {
				hb.addToParentText(parent);
				if (!hb.skipToEnd("-->")) {
					Log.w("", "not close comment");
					break;
				}
				hb.recordIndex();
			} else if (hb.startWith("<!")) {
				hb.addToParentText(parent);
				if (!hb.skipToEnd('>')) {
					Log.w("", "not close special");
					break;
				}
				hb.recordIndex();
			} else if (hb.startWith("</")) {
				// close tag
				hb.addToParentText(parent);
				hb.index += 2;
				String name = hb.getToken();
				if (name == null) {
					Log.w("", "no name in close tag");
					break;
				}
				Tag p = parent;
				while (p != null) {
					if (p.name.equals(name)) break;
					p = p.parent;
				}
				if (p == null) {
					Log.w("", "not found open tag [" + name + "]");
					break;
				}
				if (p == parent) {
					parent = p.parent;
				} else {
					while (p != parent) {
						Tag c;
						while (parent.children.size() > 0) {
							c = parent.removeChild(0);
							if (c == null) {
								Log.w("", "remove child failed");
								break;
							}
							p.addChild(c);
						}
						parent = parent.parent;
					}
					parent = p.parent;
					Log.v("", "exit loop");
				}
				hb.skipToEnd('>');
				hb.recordIndex();
			} else {
				// open tag
				hb.addToParentText(parent);
				hb.index++;
				String name = hb.getToken();
				if (name == null) {
					Log.w("", "no name in open tag");
					break;
				}
				Tag tag = new Tag(name);
				AttrList attrs = hb.getAttrs();
				if (attrs == null) {
					Log.w("", "parse attr error");
					break;
				}
				tag.attrs = attrs;
				boolean noChild = false;
				if (hb.startWith("/>")) {
					noChild = true;
					tag.isSingle = true;
				} else if (hb.startWith(">")) {
				} else {
					Log.w("", "not found '>'");
					break;
				}
				hb.skipToEnd('>');
				if (name.equals("script")) {
					// scriptéûÇÃì¡ï èàóù
					if (!hb.skipToEnd("</script>")) {
						Log.w("", "not found '</script>'");
					}
					noChild = true;
				} else {
					parent.addChild(tag);
				}
				if (!noChild) {
					parent = tag;
				}
				hb.recordIndex();
			}
		}
		return root;
	}

	private static boolean isAlpha(char ch) {
		if ('a' <= ch && ch <= 'z') return true;
		if ('A' <= ch && ch <= 'Z') return true;
		return false;
	}

}
