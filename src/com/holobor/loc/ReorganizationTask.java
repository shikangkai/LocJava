package com.holobor.loc;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReorganizationTask {

	static ScheduledExecutorService hourReorganizationService = Executors.newSingleThreadScheduledExecutor();
	static ScheduledExecutorService dayReorganizationService = Executors.newSingleThreadScheduledExecutor();
	static Calendar calendar = Calendar.getInstance(Locale.getDefault());
		
	public static synchronized void startReorganizationTask() {

		long dayTime = System.currentTimeMillis();
		calendar.setTimeInMillis(dayTime);
		//每天的零点40分开始数据整编
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 40);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		dayTime = calendar.getTimeInMillis();
		
		long hourTime = System.currentTimeMillis();
		calendar.setTimeInMillis(hourTime);
		//每小时的10分钟开始数据整编
		calendar.set(Calendar.MINUTE, 10);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		hourTime = calendar.getTimeInMillis();
		
		hourReorganizationService.scheduleAtFixedRate(new Runnable() {
			
			@Override
			public void run() {
				hourReorganizationTask();
			}
		}, hourTime + 3600000l - System.currentTimeMillis(), 3600000l, TimeUnit.MILLISECONDS);
		
		dayReorganizationService.scheduleAtFixedRate(new Runnable() {
			
			@Override
			public void run() {
				dayReorganizationTask();
			}
		}, dayTime + 3600000l * 24 - System.currentTimeMillis(), 3600000l * 24, TimeUnit.MILLISECONDS);
		LogHelper.writeLog("********** START REORGANIZATION TASK **********");
	}
	
	/**
	 * 每小时的整编
	 */
	public synchronized static void hourReorganizationTask() {
		calendar.setTimeInMillis(System.currentTimeMillis());
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MINUTE, 0);
		long time = calendar.getTimeInMillis();
		time /= 1000;
		//整编前一小时的步数
		MSSQLHelper.executeSQL("insert into tStat_Hour (carID, stat, time, type) select carID, SUM(cast(stat as int)) stat, " + (time - 1800l)
				+ ", 5 from tStat_Temp where type = 5 and time >= " 
				+ (time - 3600l) + " and time < " + time + " group by carID");
		//整编前一小时的步数
		MSSQLHelper.executeSQL("insert into tStat_Hour (carID, stat, time, type) select carID, AVG(cast(stat as int)) stat, " + (time - 1800l)
				+ ", 3 from tStat_Temp where type = 3 and time >= " 
				+ (time - 3600l) + " and time < " + time + " group by carID");
		LogHelper.writeLog("********** SQL : " + "insert into tStat_Hour (carID, stat, time, type) select carID, SUM(cast(stat as int)) stat, " + (time - 1800l)
				+ ", 5 from tStat_Temp where type = 5 and time >= " 
				+ (time - 3600l) + " and time < " + time + " group by carID" + " **********");
		LogHelper.writeLog("********** SQL : " + "insert into tStat_Hour (carID, stat, time, type) select carID, AVG(cast(stat as int)) stat, " + (time - 1800l)
				+ ", 3 from tStat_Temp where type = 3 and time >= " 
				+ (time - 3600l) + " and time < " + time + " group by carID" + " **********");
		LogHelper.writeLog("********** HOUR REORGANIZATION TASK FINISH **********");
	}

	/**
	 * 每天的整编
	 */
	public synchronized static void dayReorganizationTask() {
		calendar.setTimeInMillis(System.currentTimeMillis());
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		long time = calendar.getTimeInMillis();
		time /= 1000;
		//整编前一天的步数
		MSSQLHelper.executeSQL("insert into tStat_Day (carID, stat, time, type) select carID, SUM(cast(stat as int)) stat, " + (time - 3600l * 12)
				+ ", 5 from tStat_Temp where type = 5 and time >= " 
				+ (time - 3600l * 24) + " and time < " + time + " group by carID");
		//整编前一天的心率
		MSSQLHelper.executeSQL("insert into tStat_Day (carID, stat, time, type) select carID, AVG(cast(stat as int)) stat, " + (time - 3600l * 12)
				+ ", 3 from tStat_Temp where type = 3 and time >= " 
				+ (time - 3600l * 24) + " and time < " + time + " group by carID");
		LogHelper.writeLog("********** SQL : " + "insert into tStat_Day (carID, stat, time, type) select carID, SUM(cast(stat as int)) stat, " + (time - 3600l * 12)
				+ ", 5 from tStat_Temp where type = 5 and time >= " 
				+ (time - 3600l * 24) + " and time < " + time + " group by carID" + " **********");
		LogHelper.writeLog("********** SQL : " + "insert into tStat_Day (carID, stat, time, type) select carID, AVG(cast(stat as int)) stat, " + (time - 3600l * 12)
				+ ", 3 from tStat_Temp where type = 3 and time >= " 
				+ (time - 3600l * 24) + " and time < " + time + " group by carID" + " **********");
		LogHelper.writeLog("********** DAY REORGANIZATION TASK FINISH **********");
	}
}
