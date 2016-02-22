package jp.co.kanekotakuo.englishbrowser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class Dictionary {
	private Context mCtxt;
	private DBHelper mDbHelper;
	private SQLiteDatabase mDb;

	public Dictionary(Context context) {
		mCtxt = context;
		mDbHelper = new DBHelper(context);
	}

	public Dictionary open() {
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		mDbHelper.close();
	}

	public void register(String word, String explain) {
		mDbHelper.insert(word, explain);
	}

	public String find(String word) {
		return mDbHelper.find(word);
	}

	private class DBHelper extends SQLiteOpenHelper {
		static final String DB = "englishbrowser.db";
		static final int DB_VERSION = 1;
		static final String TABLE_NAME = "mytable";
		static final String CREATE_TABLE = "create table " + TABLE_NAME
				+ " ( _id integer primary key autoincrement, word text not null, explain text not null );";
		static final String DROP_TABLE = "drop table " + TABLE_NAME + ";";

		public DBHelper(Context c) {
			super(c, DB, null, DB_VERSION);
		}

		public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_TABLE);
		}

		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL(DROP_TABLE);
			onCreate(db);
		}

		public String find(String word) {
			Cursor csr = mDb.query(TABLE_NAME, new String[] { "explain" }, "word = ?", new String[] { word }, null,
					null, null);
			if (csr == null) return null;
			String explain = getFromCsr(csr);
			csr.close();
			return explain;
		}

		private String getFromCsr(Cursor csr) {
			if (!csr.moveToNext()) return null;
			int index = csr.getColumnIndex("explain");
			if (index < 0 || index >= csr.getColumnCount()) return null;
			return csr.getString(index);
		}

		public void insert(String word, String explain) {
			if (find(word) != null) {
				try {
					mDb.delete(TABLE_NAME, "word = ?", new String[] { word });
				} catch (Exception e) {
					Log.w("", "delete / " + e);
				}
			}
			ContentValues values = new ContentValues();
			values.put("word", word);
			values.put("explain", explain);
			mDb.insertOrThrow(TABLE_NAME, null, values);
		}
	}
}
