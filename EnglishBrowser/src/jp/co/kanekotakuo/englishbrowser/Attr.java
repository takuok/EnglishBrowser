package jp.co.kanekotakuo.englishbrowser;

public class Attr {
	public Attr() {
		key = value = null;
	}

	public Attr(String key, String value) {
		this.key = key;
		if (value != null) {
			int len = value.length();
			if (len >= 2) {
				if (value.charAt(0) == '"' && value.charAt(len - 1) == '"') {
					value = value.substring(1, len - 1);
				}
			}
		}
		this.value = value;
	}

	public String key;
	public String value;
}
