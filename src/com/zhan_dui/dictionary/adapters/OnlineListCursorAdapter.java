package com.zhan_dui.dictionary.adapters;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.zhan_dui.dictionary.R;
import com.zhan_dui.dictionary.db.DictionaryDB;
import com.zhan_dui.dictionary.utils.Constants;
import com.zhan_dui.dictionary.utils.DownloadUtils;
import com.zhan_dui.dictionary.utils.DownloadUtils.DownloadUtilsInterface;
import com.zhan_dui.dictionary.utils.UnzipFile;

public class OnlineListCursorAdapter extends CursorAdapter {

	LayoutInflater layoutInflater;
	Context context;
	ArrayList<String> dictionarysInfos = new ArrayList<String>();
	private static ArrayList<String> downloadingNotificationUrls = new ArrayList<String>();
	private static ArrayList<String> downloadingNotificationCancels = new ArrayList<String>();
	private CursorAdapter thisCursorAdapter;

	public OnlineListCursorAdapter(Context context, Cursor c) {
		super(context, c);
		this.context = context;
		thisCursorAdapter = this;
		if (c == null) {
			DictionaryDB dictionaryDB = new DictionaryDB(context,
					DictionaryDB.DB_NAME, null, DictionaryDB.DB_VERSION);
			SQLiteDatabase sqLiteDatabase = dictionaryDB.getReadableDatabase();
			c = sqLiteDatabase.rawQuery("select * from dictionary_list", null);
			sqLiteDatabase.close();
		}
		layoutInflater = LayoutInflater.from(context);

	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		View view = layoutInflater.inflate(R.layout.dictionary_list_item, null);
		ViewHolder viewHolder = new ViewHolder();
		viewHolder.DictionaryName = (TextView) view
				.findViewById(R.id.item_dictionary_name);
		viewHolder.DictionarySize = (TextView) view
				.findViewById(R.id.item_dictionary_size);
		viewHolder.DictionaryDownloadButton = (Button) view
				.findViewById(R.id.item_download);
		view.setTag(viewHolder);
		return view;
	}

	@Override
	public void bindView(View convertView, Context context, Cursor cursor) {

		ViewHolder viewHolder = (ViewHolder) convertView.getTag();

		String dictionary_name = cursor.getString(cursor
				.getColumnIndex("dictionary_name"));
		String dictionary_size = cursor.getString(cursor
				.getColumnIndex("dictionary_size"));
		String dictionary_save_name = cursor.getString(cursor
				.getColumnIndex("dictionary_save_name"));
		String dictionary_url = cursor.getString(cursor
				.getColumnIndex("dictionary_url"));
		String dictionary_downloaded = cursor.getString(cursor
				.getColumnIndex("dictionary_downloaded"));
		int id = cursor.getInt(cursor.getColumnIndex("_id"));

		viewHolder.DictionaryName.setText(dictionary_name);// 字典的名字
		viewHolder.DictionarySize.setText(dictionary_size);// 大小
		viewHolder.DictionaryName.setContentDescription(id + "");// 设置字典ID
		viewHolder.DictionaryDownloadButton
				.setContentDescription(dictionary_url);

		viewHolder.DictionaryDownloadButton
				.setOnClickListener(new DownloadDictionaryListener(id,
						dictionary_name, dictionary_save_name, dictionary_url,
						dictionary_size));

		if (dictionary_downloaded.equals("1")) {
			viewHolder.DictionaryDownloadButton.setText(context
					.getString(R.string.download_finished));
			viewHolder.DictionaryDownloadButton.setEnabled(false);
		} else if (downloadingNotificationUrls.contains(dictionary_url)) {
			viewHolder.DictionaryDownloadButton.setEnabled(true);
			viewHolder.DictionaryDownloadButton.setText(context
					.getString(R.string.download_cancel));
			viewHolder.DictionaryDownloadButton
					.setOnClickListener(new CancelListener(dictionary_url));
		} else {
			viewHolder.DictionaryDownloadButton.setText(context
					.getString(R.string.dictionary_download));
			if (viewHolder.DictionaryDownloadButton.isEnabled() == false) {
				viewHolder.DictionaryDownloadButton.setEnabled(true);
			}
		}

	}

	static class ViewHolder {
		TextView DictionaryName, DictionarySize;
		Button DictionaryDownloadButton;
	}

	/**
	 * 取消下载监听器
	 * 
	 * @author xuanqinanhai
	 * 
	 */
	class CancelListener implements OnClickListener {

		private String toCancel;

		public CancelListener(String url) {
			this.toCancel = url;
		}

		@Override
		public void onClick(View v) {
			downloadingNotificationUrls.remove(v.getContentDescription());
			thisCursorAdapter.notifyDataSetChanged();
			downloadingNotificationCancels.add(this.toCancel);
		}

	}

	@SuppressLint("HandlerLeak")
	class DownloadBehavior implements DownloadUtilsInterface {

		private Boolean status = true;
		private NotificationManager notificationManager;
		private Notification notification;
		private int notificationId;

		public DownloadBehavior(NotificationManager notificationManager,
				Notification notification, int notificationId) {
			this.notificationManager = notificationManager;
			this.notification = notification;
			this.notificationId = notificationId;
		}

		@Override
		public void beforeDownload(String url) {
			downloadingNotificationUrls.add(url);
			thisCursorAdapter.notifyDataSetChanged();
		}

		@Override
		public void afterDownload(Boolean result, String url, String savePath) {
			downloadingNotificationUrls.remove(url);
			thisCursorAdapter.notifyDataSetChanged();
			if (result && status) {
				DictionaryDB dictionaryDB = new DictionaryDB(context,
						DictionaryDB.DB_NAME, null, DictionaryDB.DB_VERSION);
				SQLiteDatabase sqLiteDatabase = dictionaryDB
						.getWritableDatabase();
				ContentValues contentValues = new ContentValues();
				contentValues.put("dictionary_downloaded", "1");

				String args[] = { url };
				sqLiteDatabase.update(DictionaryDB.DB_DICTIONARY_LIST_NAME,
						contentValues, "dictionary_url=?", args);
				sqLiteDatabase.close();
			}
		}

		@Override
		public void errorHand(String errorMsg, String url) {
			Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show();
		}

		/**
		 * 下载线程开始前执行，不能更新UI进程
		 */
		@Override
		public void beforeThread(String url) {

		}

		/**
		 * 在下载线程执行即将返回时执行，不能更新UI进程
		 */
		@Override
		public void afterThread(Boolean result, String url, String filePath) {
			if (result) {
				try {
					InputStream inputStream = new FileInputStream(filePath);
					String outputDirectory = Environment
							.getExternalStorageDirectory()
							+ "/"
							+ Constants.SAVE_DIRECTORY + "/";
					new UnzipFile(unzipHandler, inputStream, outputDirectory,
							true).unzip();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					status = false;
				}
			}
		}

		/**
		 * 解压处理Handler，只用记录出错状况
		 */
		@SuppressLint("HandlerLeak")
		private Handler unzipHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				if (msg.what == Constants.UNZIP_ERROR) {
					status = false;
				}
			}
		};

		@Override
		public void update(String url, int fileDownloaded, int fileSize) {
			String downloadedText;
			if (fileDownloaded < 1048576) {
				downloadedText = new DecimalFormat("#.00")
						.format(fileDownloaded / 1024.0) + "KB";
			} else {
				downloadedText = new DecimalFormat("#.00")
						.format(fileDownloaded / 1048576.0) + "MB";
			}
			String totalText;
			if (fileSize < 1048576) {
				totalText = new DecimalFormat("#.00").format(fileSize / 1024.0)
						+ "KB";
			} else {
				totalText = new DecimalFormat("#.00")
						.format(fileSize / 1048576.0) + "MB";
			}
			String progressText = String.format("%d%%(%s/%s)",
					(int) (((double) fileDownloaded / fileSize) * 100),
					downloadedText, totalText);

			notification.contentView.setTextViewText(
					R.id.txt_download_progress, progressText);
			notificationManager.notify(notificationId, notification);
		}

		public void addAnAbort(String url) {
			downloadingNotificationCancels.add(url);
		}

		@Override
		public boolean ifAbort(String url) {
			return downloadingNotificationCancels.remove(url);
		}
	}

	/**
	 * 下载按钮监听器
	 * 
	 * @author xuanqinanhai
	 * 
	 */
	class DownloadDictionaryListener implements OnClickListener {

		private int id;
		private String dictionaryName;
		private String dictionaryUrl;
		private String dictionarySize;
		private String dictionarySaveName;

		public DownloadDictionaryListener(int id, String dictionaryName,
				String dictionarySaveName, String dictionaryUrl,
				String dictionarySize) {
			super();
			this.id = id;
			this.dictionaryName = dictionaryName;
			this.dictionarySaveName = dictionarySaveName;
			this.dictionaryUrl = dictionaryUrl;
			this.dictionarySize = dictionarySize;
		}

		@Override
		public void onClick(View v) {
			String savePath = Environment.getExternalStorageDirectory() + "/"
					+ Constants.SAVE_DIRECTORY + "/" + dictionarySaveName;
			NotificationManager notificationManager = (NotificationManager) context
					.getSystemService(Context.NOTIFICATION_SERVICE);
			Notification notification = new Notification();
			notification.icon = android.R.drawable.stat_sys_download;
			notification.tickerText = "开始下载" + dictionaryName + " 大小:"
					+ dictionarySize;
			RemoteViews contentView = new RemoteViews(context.getPackageName(),
					R.layout.notification_progress);
			notification.contentView = contentView;
			// 使用自定义下拉视图时，不需要再调用setLatestEventInfo()方法
			// 但是必须定义 contentIntent
			Intent intent = new Intent();
			PendingIntent pd = PendingIntent.getActivity(context, 0, intent, 0);
			notification.contentIntent = pd;
			notification.contentView.setTextViewText(
					R.id.txt_download_dictionary_name, dictionaryName);
			notificationManager.notify(id, notification);
			DownloadUtils
					.download(dictionaryUrl, savePath, new DownloadBehavior(
							notificationManager, notification, id),
							notificationManager, notification,
							R.id.download_progress, id);
		}
	}

}
