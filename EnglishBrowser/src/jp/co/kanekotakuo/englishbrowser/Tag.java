package jp.co.kanekotakuo.englishbrowser;

import java.util.ArrayList;
import java.util.List;

public class Tag {
	public String name;
	public String text = "";
	public Tag parent;
	public List<Tag> children = new ArrayList<Tag>();
	public AttrList attrs = new AttrList();
	public boolean isSingle = false;

	public static class AttrList extends ArrayList<Attr> {
		private static final long serialVersionUID = -897150342198480958L;

		Attr get(String key) {
			for (Attr attr : this) {
				if (key.equals(attr.key)) return attr;
			}
			return null;
		}

		String getValue(String key) {
			Attr attr = get(key);
			if (attr != null) return attr.value;
			return null;
		}
	}

	public static class IgnoreTag {
		String name;
		String key;
		String value;

		public IgnoreTag(String name, String key, String value) {
			this.name = name;
			this.key = key;
			this.value = value;
		}

		public boolean isMatch(Tag tag) {
			if (name != null && !tag.name.equals(name)) return false;
			if (key == null || value == null) return false;
			if (tag.attrs.size() <= 0) return false;
			for (Attr attr : tag.attrs) {
				if (attr.key.equals(key) && attr.value.equals(value)) {
					return true;
				}
			}
			return false;
		}
	}

	public Tag(String name) {
		this.name = name;
	}

	public void addChild(Tag tag) {
		children.add(tag);
		tag.parent = this;
	}

	public boolean removeChild(Tag tag) {
		if (!children.remove(tag)) {
			return false;
		}
		tag.parent = null;
		return true;
	}

	public Tag removeChild(int index) {
		Tag tag = children.remove(index);
		if (tag != null) tag.parent = null;
		return tag;
	}

	public Tag find(String name) {
		if (this.name.equals(name)) {
			return this;
		}
		for (Tag c : children) {
			Tag res = c.find(name);
			if (res != null) {
				return res;
			}
		}
		return null;
	}

	public Tag find(String name, String id) {
		if (this.name.equals(name) && id.equals(this.attrs.getValue("id"))) {
			return this;
		}
		for (Tag c : children) {
			Tag res = c.find(name, id);
			if (res != null) {
				return res;
			}
		}
		return null;
	}

	public List<Tag> collect(String name) {
		List<Tag> ls = new ArrayList<Tag>();
		collect(ls, name);
		return ls;
	}

	public void collect(List<Tag> ls, String name) {
		if (this.name.equals(name)) {
			ls.add(this);
		}
		for (Tag c : children) {
			c.collect(ls, name);
		}
	}

	public void addToStringBuffer(StringBuffer sb, IgnoreTag ignore) {
		if (ignore != null) {
			if (ignore.isMatch(this)) return;
		}
		if (isSingle && children.isEmpty()) {
			sb.append("<" + name + " />");
			return;
		}
		sb.append("<" + name + ">");
		for (Tag c : children) {
			c.addToStringBuffer(sb, ignore);
		}
		if (this.text != null) {
			sb.append(this.text);
		}
		sb.append("</" + name + ">");
	}
}
