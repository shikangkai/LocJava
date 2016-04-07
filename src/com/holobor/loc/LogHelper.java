package com.holobor.loc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogHelper {

	static FileWriter writer;
	static LogHelper logHelper;
	static SimpleDateFormat dateFormat;
	
	static {
		dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		try {
			File file = new File((new SimpleDateFormat("yyyy-MM-dd HH_mm_ss")).format(new Date(System.currentTimeMillis())) + "-loc_info_data.log");
			if (!file.exists()) {
				file.createNewFile();
			}
			writer = new FileWriter(file);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("When Creating Log File Occurs ERROR, Quit The Server.");
			System.exit(0);
		}
	}
	
	private LogHelper() {
		
	}
	
	public static void writeLog(String info) {
		try {
			writer.append(dateFormat.format(new Date(System.currentTimeMillis())) + " | " +  info);
			writer.append("\r\n");
			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("When Writing Log Occurs ERROR, Quit The Server.");
			System.exit(0);
		}
	}
}
