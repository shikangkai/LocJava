package com.holobor.loc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import net.sf.json.JSONObject;
/**
 * 移动设备的连接
 * @author Holobor
 *
 */
public class MachineSocket extends ObjectSocket {

	SocketThread socket;
	
	/**
	 * 与该设备绑定的被监管人员的ID
	 */
	String bindVID = "0", bindVName;
	/**
	 * 监管人员列表
	 */
	List<SVUser> bindSVUsers;
	/**
	 * 被监管人员的安全配置信息
	 */
	VConfig config;
	
	public MachineSocket(SocketThread socketThread) {
		super(socketThread);
		this.socket = socketThread;
		
		config = new VConfig();
		
		ResultSet resultSet = MSSQLHelper.executeQuery("select b.userID, policyNotify from tSupervision a left join tUser b on a.userID = b.userID where a.carID = " + bindVID);
		try {
			while (resultSet.next()) {
				SVUser user = new SVUser(resultSet.getString(1), resultSet.getInt(2), resultSet.getString(3));
				bindSVUsers.add(user);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 获取配置信息
	 */
	public void obtainConfig() {
		try {
			ResultSet resultSet = MSSQLHelper.executeQuery("select lat, lon, scope, backHour, backMinute, actLat, actLon, actScope from tCarConfig where carID = " + bindVID);
			if (resultSet.next()) {
				config.lat = resultSet.getDouble(1);
				config.lon = resultSet.getDouble(2);
				config.scope = resultSet.getInt(3);
				config.hour = resultSet.getInt(4);
				config.minute = resultSet.getInt(5);
				config.time = config.hour * 3600 + config.minute * 60;
				if (0 != config.scope) {
					config.hasHomeScope = true;
				}
				
				config.actLat = resultSet.getDouble(6);
				config.actLon = resultSet.getDouble(7);
				config.actScope = resultSet.getInt(8);
				if (0 != config.actScope) {
					config.hasActScope = true;
				}
				
			}
			
			resultSet.close();
		} catch (Exception e) {
			
		}
	}

	@Override
	public void parseInfo() {
		
		try {
			int type = socket.inputStream.read();
			switch (type) {
			
			//解析请求信息
			case 0x00: {
				parseRequest();
			} break;
			
			//解析设备注册信息
			case 0x01: {
				
			} break;
			
			//解析设备原始数据信息
			case 0x02: {
				parseSendinfo();
			} break;
			
			//解析设备警告信息
			case 0x03: {
				parseWarning();
			} break;
			
			//解析设备配置信息
			case 0x04: {
				parseProfile();
			} break;
				
			//读取结束
			case -1: {
				throw new Exception();
			}
			
			default: {
				throw new Exception("UNDEFINED INFO TYPE=" + type);
			}
			}
		} catch (Exception e) {
			socket.isRunning = false;
			socket.removeFromList();
			System.out.println("socket CLOSED, list SIZE=" + LocServer.socketList.size());
			System.out.println(socket.socket.getInetAddress().toString() + " DISCONNECT at " + socket.dateFormat.format(new Date(System.currentTimeMillis())));
			if (null != e.getMessage()) {
				LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " OCCUR ERROR " + e.toString());
			}
		} finally {
			
		}
	}
	
	/**
	 * 解析原始数据信息 Type = 2
	 * Type(1), Length(2), Seqnum(2), Data(N), Checksum(2)
	 */
	private void parseSendinfo() throws Exception {
		int dataLength = ((socket.inputStream.read() << 8) | socket.inputStream.read()) - 5;
		
		byte[] seqnum = new byte[2];
		socket.inputStream.read(seqnum);
		
		byte[] data = new byte[dataLength];
		socket.inputStream.read(data);
		
		//byte[] checkSum = new byte[2];
		//inputStream.read(checkSum);
		
		parseSendinfoData(data);
		
		byte[] status = new byte[] {2, 0, 6, seqnum[0], seqnum[1], 0};
		socket.outputStream.write(status);
		socket.outputStream.flush();

		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " INFORMATION");
	}
	
	/**
	 * 解析原始数据信息中的data部分数据信息，包括GPS和GSM数据
	 * Type(1), Length(2), Timestamp(4), Content(N) 
	 * Type = 1 GPS
	 * Type = 2 GSM
	 */
	private void parseSendinfoData(byte[] data) throws Exception {
		int parseLength = 0;
		int length = 0;
		byte[] content;
		
		while (parseLength < data.length) {
			socket.timeStamp = 0l;
			length = NumberHelper.byteToInt(data[parseLength + 1]) << 8 | NumberHelper.byteToInt(data[parseLength + 2]);
			socket.timeStamp = ((long) NumberHelper.byteToInt(data[parseLength + 3])) << 24 | NumberHelper.byteToInt(data[parseLength + 4]) << 16 |
					NumberHelper.byteToInt(data[parseLength + 5]) << 8 | NumberHelper.byteToInt(data[parseLength + 6]);
			content = new byte[length - 7];
			//初始化内容变量数组
			for (int i = 0; i < length - 7; i++) {
				content[i] = data[parseLength + 7 + i];
			}

			switch (data[parseLength]) {
			case 0x01:
				//parse GPS
				parseGPS(content); 
				break;
			
			case 0x02:
				//parse GSM
				parseGSM(content); 
				break;
			
			case 0x04: 
				//体温数据
				parseTemperature(content); 
				break;
			
			case 0x05: 
				//心率数据
				parseHeartRate(content); 
				break;
			
			case 0x06: 
				//睡眠时间数据
				parseSleepDuration(content); 
				break;
			
			case 0x07: 
				//步数数据
				parseStepCount(content); 
				break;
			
			case 0x08: 
				//静止时间数据
			case 0x09:
				//摔倒警告
			case 0x0a:
				//越界警告
			case 0x0b:
				//静止警告
			case 0x0c:
				//心率警告
			case 0x0d:
				//体温警告
				break;
				
			case 0x0e:
				//parse WIFI
				parseWifi(content);
				break;
			
			case 0x0f:
				//parse sound message
				parseSoundMsg(content);
				break;
			
			case -1:
				//输入流结束
				throw new Exception();
				
			default: {
				throw new Exception("UNDEFINED DATA TYPE=" + data[parseLength]);
			}
			}
			
			parseLength += length;
		}
	}
	
	/**
	 * 解析GPS数据信息
	 * $GPGGA(72), $GPVTG(34), $GPZDA(4)
	 */
	private void parseGPS(byte[] content) {
		int length = NumberHelper.byteToInt(content[0]) << 8 | NumberHelper.byteToInt(content[1]);
		//LogHelper.writeLog("IMEI:" + IMEI + " IMSI:" + IMSI + " GPS:" + new String(content));
		//String gpgga = new String(content, 0, 72);
		//String gpvtg = new String(content, 72, 34);
		//String gpzda = new String(content, 106, 4);
		String data = new String(content, 2, length - 2);
		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " GPS:" + data);
		
		//if (socket.isRegister) {
//			MySQLHelper.executeSQL("insert into gps_data (imei, imsi, time, gpgga, gpvtg, gpzda) values ('" + IMEI
//					+ "', '" + IMSI
//					+ "', " + timeStamp
//					+ ", '" + gpgga
//					+ "', '" + gpvtg 
//					+ "', '" + gpzda
//					+ "');");
			MSSQLHelper.executeSQL("insert into tGPSData (imei, imsi, time, data) values ('" 
					+ socket.IMEI
					+ "', '" + socket.IMSI
					+ "', " + socket.timeStamp
					+ ", '" + data
					+ "');");
			//定义data格式为lat,lon
		//}
		String[] pos = data.split(",");
		
		try {
			URL url = new URL("http://114.251.56.233:8900/locapp/update_lbs.php?lat=" + Double.parseDouble(pos[0]) + "&lon=" + Double.parseDouble(pos[1]) + "&time=" + socket.timeStamp + "&imei" + socket.IMEI + "&imsi=" + socket.IMSI);
			url.openConnection();
		} catch (Exception e) {
			LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " OCCUR ERROR " + e.getMessage());			
		}
	}
	
	/**
	 * 解析GSM数据信息
	 * @param index 0代表主连基站，其他则代表临近基站
	 */
	private void parseGSM(byte[] content) {
		
		int parseLength = 0;
		int length = NumberHelper.byteToInt(content[0]) << 8 | NumberHelper.byteToInt(content[1]);
		String gsmContent = new String(content, 2, length - 2);
		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " GSM:" + gsmContent);
		String[] gsmInfos = gsmContent.split(",");
		int gsmInfoCount = gsmInfos.length;
		
		if (socket.isRegister) {
			MSSQLHelper.executeSQL("insert into tLBSData (imei, imsi, time, arfcn, rxl, rxq, mcc, mnc, bsic, cellid, rla, txp, lac, ta) values ('" 
					+ socket.IMEI 
					+ "', '" + socket.IMSI
					+ "', " + socket.timeStamp 
					+ ", '" + gsmInfos[0] 
					+ "', '" + gsmInfos[1] 
					+ "', '" + gsmInfos[2] 
					+ "', '" + gsmInfos[3] 
					+ "', '" + gsmInfos[4] 
					+ "', '" + gsmInfos[5] 
					+ "', '" + gsmInfos[6] 
					+ "', '" + gsmInfos[7] 
					+ "', '" + gsmInfos[8] 
					+ "', '" + gsmInfos[9] 
					+ "', '" + gsmInfos[10]
					+ "');");
		}
		
		//parseLength += length;
		parseLength += 11;
		
		while (parseLength < gsmInfoCount) {
			if (socket.isRegister) {
				MSSQLHelper.executeSQL("insert into tLBSCellData (imei, imsi, time, arfcn, rxl, bsic, cellid, mcc, mnc, lac) values ('" 
						+ socket.IMEI
						+ "', '" + socket.IMSI
						+ "', " + socket.timeStamp
						+ ", '" + gsmInfos[parseLength]
						+ "', '" + gsmInfos[parseLength + 1] 
						+ "', '" + gsmInfos[parseLength + 2] 
						+ "', '" + gsmInfos[parseLength + 3] 
						+ "', '" + gsmInfos[parseLength + 4] 
						+ "', '" + gsmInfos[parseLength + 5] 
						+ "', '" + gsmInfos[parseLength + 6] 
						+ "');");
			}
			parseLength += 7;
		}
		
		/**
		 * http://114.251.56.233:8900/locapp/calculate_position.php?time=1458399017&imsi=358551231112154&imei=358551231112154
		 * 获取各个基站的经纬度求加权得到坐标
		 * 插入到tLBSLoc表中
		 */
		try {
			URL url = new URL("http://114.251.56.233:8900/locapp/calculate_position.php?time=" + socket.timeStamp + "&imei=" + socket.IMEI + "&imsi=" + socket.IMSI);
			URLConnection urlConn = url.openConnection();
			byte[] bytes = new byte[1024];
			InputStream inputStreamTemp = urlConn.getInputStream();
			int lengthRead = 0;
			StringBuffer info = new StringBuffer();
			while (-1 != (lengthRead = inputStreamTemp.read(bytes))) {
				info.append(new String(bytes, 0, lengthRead));
			}
			//info
			LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " CALL PHP FUNCTION RESULT:STR=" + info);
			JSONObject jsonObject = JSONObject.fromObject(info.toString());
			double lat = jsonObject.getDouble("lat"), lon = jsonObject.getDouble("lon");
			LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " CALL PHP FUNCTION RESULT:LAT=" + lat
					+ ", LON=" + lon);
			
			MSSQLHelper.executeSQL("insert into tLBSLoc (imei, imsi, time, lat, lon, alt) values ('"
					+ socket.IMEI + "', '"
					+ socket.IMSI + "', "
					+ socket.timeStamp + ", "
					+ lat + ", "
					+ lon + ", "
					+ "0);");
			if (!bindVID.equals("0")) {
				MSSQLHelper.executeSQL("insert into tPosition (carID, lo, la, gpsTime) values ("
						+ bindVID + ", "
						+ lon + ", "
						+ lat + ", '"
						+ socket.dateFormat.format(new Date(1000l * socket.timeStamp)) + "');");
				LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " LBS:LOCATION:LAT=" + lat
						+ ", LON=" + lon);
				//判断当前时间是否是在回家时间范围内
				if (config.hasHomeScope && socket.timeStamp % (24 * 3600l) > config.time 
						&& PositionHelper.calcDistance(lat, lon, config.lat, config.lon) * 1000 > config.scope) {

					for (SVUser user : bindSVUsers) {
						user.sendWarningInfo(bindVName + "有异常发生: " + "未在规定的时间回家", lat + "," + lon, 6, bindVID);
					}
					MSSQLHelper.executeSQL("insert into tWarning (carID, time, stat, addr, type) values ('" + 
							bindVID + "', '" + 
							socket.timeStamp + ", " +
							"未在规定的时间回家" + ", " +
							lat + "," + lon + ", " +
							6 + ");");
					
				}
				
				if (config.hasActScope
						&& PositionHelper.calcDistance(lat, lon, config.actLat, config.actLon) * 1000 > config.actScope) {

					for (SVUser user : bindSVUsers) {
						user.sendWarningInfo(bindVName + "有异常发生: " + "越界", lat + "," + lon, 1, bindVID);
					}
					MSSQLHelper.executeSQL("insert into tWarning (carID, time, stat, addr, type) values ('" + 
							bindVID + "', '" + 
							socket.timeStamp + ", " +
							"越界" + ", " +
							lat + "," + lon + ", " +
							1 + ");");
				}
				//判断位置是否越界
			}
		} catch (Exception e) {
			LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " OCCUR ERROR " + e.getMessage());
		}	
	}
	/**
	 * 解析体温数据信息
	 */
	private void parseTemperature(byte[] content) {
		int length = NumberHelper.byteToInt(content[0]) << 8 | NumberHelper.byteToInt(content[1]);
		String data = new String(content, 2, length - 2);
		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " TEMPERATURE:" + data);
		
		if (socket.isRegister) {
			MSSQLHelper.executeSQL("insert into tStat_Temp (carID, stat, time, type) values (" + bindVID
					+ ", '" + data
					+ "', " + socket.timeStamp
					+ ", 4);");
		}
	}
	
	/**
	 * 解析心率数据信息
	 */
	private void parseHeartRate(byte[] content) {
		int length = NumberHelper.byteToInt(content[0]) << 8 | NumberHelper.byteToInt(content[1]);
		String data = new String(content, 2, length - 2);
		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " HEARTRATE:" + data);
		
		if (socket.isRegister) {
			MSSQLHelper.executeSQL("insert into tStat_Temp (carID, stat, time, type) values (" + bindVID
					+ ", '" + data
					+ "', " + socket.timeStamp
					+ ", 3);");
		}
	}
	
	/**
	 * 解析步数数据信息
	 */
	private void parseStepCount(byte[] content) {
		int length = NumberHelper.byteToInt(content[0]) << 8 | NumberHelper.byteToInt(content[1]);
		String data = new String(content, 2, length - 2);
		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " STEPCOUNT:" + data);
		
		if (socket.isRegister) {
			MSSQLHelper.executeSQL("insert into tStat_Temp (carID, stat, time, type) values (" + bindVID
					+ ", '" + data
					+ "', " + socket.timeStamp
					+ ", 5);");
		}
	}

	/**
	 * 解析睡眠时间数据信息
	 */
	private void parseSleepDuration(byte[] content) {
		int length = NumberHelper.byteToInt(content[0]) << 8 | NumberHelper.byteToInt(content[1]);
		String data = new String(content, 2, length - 2);
		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " SLEEPDURATION:" + data);
		
		if (socket.isRegister) {
			MSSQLHelper.executeSQL("insert into tStat_Temp (carID, stat, time, type) values (" + bindVID
					+ ", '" + data
					+ "', " + socket.timeStamp
					+ ", 6);");
		}
	}
	
	/**
	 * 解析无线网路信息
	 * @param content
	 */
	private void parseWifi(byte[] content) {
		int length = NumberHelper.byteToInt(content[0]) << 8 | NumberHelper.byteToInt(content[1]);
		String wifiContent = "";
		try {
			wifiContent = new String(content, 2, length - 2, "UTF8");
		} catch (UnsupportedEncodingException uee) {
			// TODO Auto-generated catch block
			//e2.printStackTrace();
		}
		//wifiContent = wifiContent.replace("\\,", ",");
		
		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " WIFI:" + wifiContent);
		
		String[] wifiInfos = wifiContent.split(",");
		double lat = 0, lon = 0, alt = 0;
		int weight = 0;
		
		for (int i = 0; i < wifiInfos.length / 4; i++) {

			MSSQLHelper.executeSQL("insert into tWIFIData (imei, imsi, time, bssid, ssid, encrypt, level) values ('" 
					+ socket.IMEI
					+ "', '" + socket.IMSI
					+ "', " + socket.timeStamp
					+ ", '" + wifiInfos[4 * i]
					+ "', '" + wifiInfos[4 * i + 1]
					+ "', " + wifiInfos[4 * i + 2] 
					+ ", " + wifiInfos[4 * i + 3]
					+ ");");
			
			if (Integer.parseInt(wifiInfos[4 * i + 3]) < 0) {
				weight += Integer.parseInt(wifiInfos[4 * i + 3]) + 110;
			} else {
				weight += Integer.parseInt(wifiInfos[4 * i + 3]);
			}
			
			//查询经纬度
			ResultSet result = MSSQLHelper.executeQuery("select lat, lon, alt from tWIFIInfo where bssid = '"
					+ wifiInfos[4 * i] + "' and ssid = '"
					+ wifiInfos[4 * i + 1] + "';");
			try {
				if (result.next()) {
					lat += weight * result.getDouble(1);
					lon += weight * result.getDouble(2);
					alt += weight * result.getDouble(3);
					result.close();
				}
			} catch (SQLException e) {
				
			}
		}
		
		if (0 == lat / weight || 0 == lon / weight || 0 == alt / weight) {
			LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " OCCUR ERROR WIFI LOCATION ERROR");
		} else {
			MSSQLHelper.executeSQL("insert into tWIFILoc (imei, imsi, time, lat, lon, alt) values ('"
					+ socket.IMEI + "', '"
					+ socket.IMSI + "', "
					+ socket.timeStamp + ", "
					+ (lat / weight) + ", "
					+ (lon / weight) + ", "
					+ (alt / weight) + ");");

			if (!bindVID.equals("0")) {
				MSSQLHelper.executeSQL("insert into tPosition (carID, lo, la, gpsTime) values ("
						+ bindVID + ", "
						+ (lon / weight) + ", "
						+ (lat / weight) + ", '"
						+ socket.dateFormat.format(new Date(1000l * socket.timeStamp)) + "');");
				LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " WIFI:LOCATION:LAT=" + (lat / weight)
						+ ", LON=" + (lon / weight)
						+ ", ALT=" + (alt / weight));
			}
		}
		
	}

	/**
	 * 解析用户的语音数据信息
	 * @throws Exception
	 */
	private void parseSoundMsg(byte[] content) throws Exception {
		
	}
	
	/**
	 * 解析警告信息
	 */
	private void parseWarning() throws Exception {
		int length = socket.inputStream.read() << 8 | socket.inputStream.read();
		byte[] timeBytes = new byte[4];
		socket.inputStream.read(timeBytes);
		long timeStamp = (long) NumberHelper.byteToInt(timeBytes[0]) << 24 
				| NumberHelper.byteToInt(timeBytes[1]) << 16 
				| NumberHelper.byteToInt(timeBytes[2]) << 8 
				| NumberHelper.byteToInt(timeBytes[3]);
		int code = socket.inputStream.read();
		byte[] desc = new byte[length - 8];
		socket.inputStream.read(desc);
		String[] descStrings = new String(desc).split(",");
		
		byte[] status = new byte[] {3, 0, 8, timeBytes[0], timeBytes[1], timeBytes[2], timeBytes[3], 0};
		socket.outputStream.write(status);
		socket.outputStream.flush();
		
		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " WARNING CODE=" + code);
		
		//查找数据库
		
		if (socket.isRegister) {
			for (SVUser user : bindSVUsers) {
				user.sendWarningInfo(bindVName + "有异常发生: " + descStrings[0], descStrings[1], code, bindVID);
				MSSQLHelper.executeSQL("insert into tWarning (carID, time, stat, addr, type, userID, isRead) values ('" + 
						bindVID + "', '" + 
						timeStamp + ", " +
						descStrings[0] + ", " +
						descStrings[1] + ", " +
						code + ", '" + 
						user.userID + "', 0);");
			}
		}
	}
	
	/**
	 * 解析配置信息
	 */
	private void parseProfile() throws Exception {

		int length = socket.inputStream.read() << 8 | socket.inputStream.read();
		int paramsCount = (length - 3) / 5;
		int param = 0;
		byte[] valueBytes = new byte[4];
		long value = 0l;
		
		for (int i = 0; i < paramsCount; i++) {
			param = socket.inputStream.read();
			socket.inputStream.read(valueBytes);
			value = (long) NumberHelper.byteToInt(valueBytes[0]) << 24 
					| NumberHelper.byteToInt(valueBytes[1]) << 16 
					| NumberHelper.byteToInt(valueBytes[2]) << 8 
					| NumberHelper.byteToInt(valueBytes[3]);
			//do something...
		}
		
		@SuppressWarnings("unused")
		int checkSum = socket.inputStream.read() << 8 | socket.inputStream.read();
		
		byte[] status = new byte[] {4, 0, 4, NumberHelper.intToByte(255)};
		socket.outputStream.write(status);
		socket.outputStream.flush();
		
		LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " PROFILE KEY=" + param + " VALUE=" + value);
	}

	/**
	 * 解析用户的请求信息
	 * @throws Exception
	 */
	private void parseRequest() throws Exception {

		int length = socket.inputStream.read() << 8 | socket.inputStream.read();
		byte[] seqnum = new byte[2];
		socket.inputStream.read(seqnum);
		switch (socket.inputStream.read()) {
		
		//请求配置信息
		case 0x00: {
			byte[] profilebytes = new byte[14];
			profilebytes[0] = 0;
			profilebytes[1] = 0;
			ResultSet resultSet = MSSQLHelper.executeQuery("select gpsInterval, lbsInterval, dataInterval from tMachine where machineNO = '" + socket.IMEI + "';");
			int gi = 0, li = 0, di = 0;
			if (resultSet.next()) {
				gi = resultSet.getInt(1);
				li = resultSet.getInt(2);
				di = resultSet.getInt(3);
			}
			
			profilebytes[2] = (byte) ((gi >> 24) & 0xff);
			profilebytes[3] = (byte) ((gi >> 16) & 0xff);
			profilebytes[4] = (byte) ((gi >> 8) & 0xff);
			profilebytes[5] = (byte) ((gi) & 0xff);
			
			profilebytes[6] = (byte) ((li >> 24) & 0xff);
			profilebytes[7] = (byte) ((li >> 16) & 0xff);
			profilebytes[8] = (byte) ((li >> 8) & 0xff);
			profilebytes[9] = (byte) ((li) & 0xff);
			
			profilebytes[10] = (byte) ((di >> 24) & 0xff);
			profilebytes[11] = (byte) ((di >> 16) & 0xff);
			profilebytes[12] = (byte) ((di >> 8) & 0xff);
			profilebytes[13] = (byte) ((di) & 0xff);
			
			socket.outputStream.write(profilebytes);
			socket.outputStream.flush();
		} break;

		//下发实时数据
		case 0x01: {
			byte[] toDeviceIdBytes = new byte[length - 6];
			socket.inputStream.read(toDeviceIdBytes);
			String toDeviceId = new String(toDeviceIdBytes);
			
			for (SocketThread socketThread : LocServer.socketList) {
				if (socketThread.equalID(toDeviceId, toDeviceId)) {
					/**
					 * 从数据库中查询数据，写到输出流中
					 */
					StringBuffer stringBuffer = new StringBuffer();
					ResultSet resultSet =  MSSQLHelper.executeQuery("select top 1 lo, la from tPosition where carID = " + bindVID + " order by gpsTime desc;");
					if (resultSet.next()) {
						stringBuffer.append(resultSet.getDouble(1));
						stringBuffer.append(',');
						stringBuffer.append(resultSet.getDouble(2));
					} else {
						stringBuffer.append("116.404,39.915");
					}
					resultSet =  MSSQLHelper.executeQuery("select top 1 stat from tStat_Temp where carID = " + bindVID + " and type = 3 order by time desc;");
					stringBuffer.append(',');
					if (resultSet.next()) {
						stringBuffer.append(resultSet.getString(1));
					} else {
						stringBuffer.append("--");
					}
					resultSet =  MSSQLHelper.executeQuery("select top 1 stat from tStat_Temp where carID = " + bindVID + " and type = 4 order by time desc;");
					stringBuffer.append(',');
					if (resultSet.next()) {
						stringBuffer.append(resultSet.getString(1));
					} else {
						stringBuffer.append("--");
					}
					
					//lo,la,hr,tp
					socketThread.outputStream.write(new byte[] {0, (byte) (stringBuffer.length() + 2)});
					socketThread.outputStream.write(stringBuffer.toString().getBytes());
					socketThread.outputStream.flush();
					LogHelper.writeLog("IMEI:" + socket.IMEI + " IMSI:" + socket.IMSI + " SEND REALTIME INFORMATION TO " + toDeviceId + " INFO:" + stringBuffer);
					break;
				}
			}
			
		} break;
		
		default:
			break;
		}
	}
	
	public static String loadJson (String url) {
        StringBuilder json = new StringBuilder();
        try {
            URL urlObject = new URL(url);
            URLConnection uc = urlObject.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
            String inputLine = null;
            while ((inputLine = in.readLine()) != null) {
                json.append(inputLine);
            }
            in.close();
        } catch (MalformedURLException e) {
        	
        } catch (IOException e) {
        	
        }
        return json.toString();
    }
}

/**
 * 配置被监管人员属性信息
 * @author Holobor
 *
 */
class VConfig {
	/**
	 * 居住地点中心
	 */
	double lat, lon;
	/**
	 * 居住地点范围
	 */
	int scope;
	/**
	 * 最晚回到居住地点的时间，time表示当日最晚回家时间的秒表示
	 */
	int hour, minute, time;
	/**
	 * 标志是否设置居住范围
	 */
	boolean hasHomeScope = false;
	/**
	 * 活动范围中心点
	 */
	double actLat, actLon;
	/**
	 * 活动范围半径
	 */
	int actScope;
	/**
	 * 标志是否设置活动范围
	 */
	boolean hasActScope = false;
	
	@Override
	public String toString() {
		return "SAFE-CONFIG:HOME=" + lat + "," + lon + "," + scope + " " + hour + ":" + minute + " ACT=" + actLat + "," + actLon + "," + actScope;
	}
}