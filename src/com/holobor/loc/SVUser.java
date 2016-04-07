package com.holobor.loc;

import java.io.OutputStream;
import java.net.Socket;
import java.sql.ResultSet;

import cn.jpush.api.JPushClient;
import cn.jpush.api.common.resp.APIConnectionException;
import cn.jpush.api.common.resp.APIRequestException;
import cn.jpush.api.push.model.Message;
import cn.jpush.api.push.model.Platform;
import cn.jpush.api.push.model.PushPayload;
import cn.jpush.api.push.model.audience.Audience;

/**
 * 表示监管者的实体类
 * @author Holobor
 *
 */
public class SVUser {
	/**
	 * userID和手机号一致
	 */
	String userID;
	/**
	 * 表示用户接收提醒的提示方式
	 */
	int notifyWarningType = 0;
	/**
	 * 用于应用信息推送的设备别名
	 */
	String deviceAlias;
	
	/**
	 * 构造函数
	 */
	public SVUser(String userID, int notifyWarningType, String deviceAlias) {
		this.userID = userID;
		this.notifyWarningType = notifyWarningType;
		this.deviceAlias = deviceAlias;
	}
	
	/**
	 * 提示用户告警信息
	 * @param desc
	 * @param addr
	 * @param code
	 */
	public void sendWarningInfo(final String desc, String addr, int code, String vid) {
		//控制同样的警告信息在一小时内不重复发送
		try {
			ResultSet resultSet = MSSQLHelper.executeQuery("select max(time) time from tWarning where type = " + code + " and carID = " + vid);
			resultSet.next();
			if (System.currentTimeMillis() / 1000 - resultSet.getLong(1) < 3600) {
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		LogHelper.writeLog("SEND WARNING INFO");
		switch (code) {
		//应用消息推送
		case 0: {
			JPushClient jpushClient = new JPushClient("ef4ffe3001dd5bf8814731b2 ", "40a611b01b8a4a1cd00cfed6");
			PushPayload pushPayload = PushPayload.newBuilder()
			.setPlatform(Platform.all())
			.setAudience(Audience.alias(userID))
			.setMessage(
					Message.newBuilder()
					.addExtra("ticker", "有新的报警消息")
					.addExtra("title", "报警信息")
					.addExtra("text", desc)
					.addExtra("type", "warning")
					.addExtra("extra", vid)
					.build())
			.build();
			try {
				jpushClient.sendPush(pushPayload);
			} catch (Exception e) {
				LogHelper.writeLog("SEND WARNING INFO FAILED:" + e.getMessage());
			}
		} break;
		
		//电话呼入
		case 1: {
			
		} break;
		
		//短信提醒，给userID发短信
		case 2: {
			new Thread(new Runnable() {
				public void run() {
					try {
						
						String contentString;
						
						Socket socket = new Socket("114.251.56.233", 8100);
						OutputStream outputStream = socket.getOutputStream();
						
						if (!userID.startsWith("+86")) {
							
							contentString = "+86" + userID + "," + desc + "\r\n";
						} else {
							
							contentString = userID + "," + desc + "\r\n";
						}
						
						outputStream.write(contentString.getBytes());
						outputStream.flush();
						
						socket.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
			
		} break;
		
		default:
			break;
		}
	}
}
