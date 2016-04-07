package com.holobor.loc;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SocketThread extends Thread {

	Socket socket;
	InputStream inputStream;
	OutputStream outputStream;
	
	boolean isRunning = true;
	boolean isRegister = false;
	
	long timeStamp = 0;
	String IMEI = "", IMSI = "";
	
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
	
	/**
	 * 封装这个通信管道的对象
	 */
	ObjectSocket objectSocket;
	
	public SocketThread(final Socket socket) {
		try {
			System.out.println(socket.getInetAddress().toString() + " CONNECT at " + dateFormat.format(new Date(System.currentTimeMillis())));
			
			this.socket = socket;
			inputStream = socket.getInputStream();
			outputStream = socket.getOutputStream();
			
			Thread registerThread = new Thread(new Runnable() {
				
				@Override
				public void run() {
					try {
						/*
						 * 读取注册信息
						 */
						if (1 != inputStream.read()) {

							outputStream.write(new byte[] {1, 0, 4, 0});
							outputStream.flush();
							throw new Exception("NOT REGISTER FIRST");
						}
						parseRegister();
					} catch (Exception e) {
						LogHelper.writeLog("IMEI:" + IMEI + " IMSI:" + IMSI + " SYSTEM RUNTIME REGISTER ERROR " + e.getMessage());
						removeFromList();
					}
				}
			});
			registerThread.start();
			registerThread.join(180000);	//当等待时间大于180秒，则断开该连接

			if (!isRegister) {
				isRunning = false;
				System.out.println(socket.getInetAddress().toString() + " DISCONNECT at " + dateFormat.format(new Date(System.currentTimeMillis())));
				LogHelper.writeLog(socket.getInetAddress().toString() + " DISCONNECT REGISTER TIMEOUT");
				removeFromList();
				registerThread.interrupt();
				
				return ;
			}
			
			start();
			
		} catch (Exception e) {
			LogHelper.writeLog("IMEI:" + IMEI + " IMSI:" + IMSI + " SYSTEM RUNTIME ERROR " + e.getMessage());
			removeFromList();
		}
	}

	@Override
	public void run() {
		
		while (isRunning) {
			objectSocket.parseInfo();
		}
	}

	/**
	 * 解析注册信息 Type = 1
	 * Type(1), Length(2), Init-seqnum(2), IMEI(15), IMSI(N), CheckSum(2)
	 */
	private synchronized void parseRegister() throws Exception {
		
		int length = inputStream.read() << 8 | inputStream.read();
		@SuppressWarnings("unused")
		int initSeqnum = inputStream.read() << 8 | inputStream.read();
		byte[] imei = new byte[15];
		inputStream.read(imei);
		
		byte[] imsi = new byte[length - 20];
		inputStream.read(imsi);
		
		IMEI = new String(imei);
		IMSI = new String(imsi);
		
		//int checkSum = inputStream.read() << 8 | inputStream.read();
		
		byte[] status;
		if (isRegister)  {
			status = new byte[] {1, 0, 4, -2};	//-2 = 254
		} else {
			status = new byte[] {1, 0, 4, 0};
			isRegister = true;
		}

		/**
		 * 判断用户的类型，给objectSocket赋不同类型的值
		 */
		boolean isMachine = true;
		//判断在tMachine表中是否存在该设备
		//如果不存在，则默认当做tUser 
		ResultSet resultSet = MSSQLHelper.executeQuery("select machineNO from tMachine where machineNO = '" + IMEI + "' or machineNO = '" + IMSI + "';");
		if (resultSet.next()) {
			isMachine = true;
		} else {
			isMachine = false;
		}
		resultSet.close();
		
		if (isMachine) {
			objectSocket = new MachineSocket(this);
			ResultSet resultSetSub = MSSQLHelper.executeQuery("select carID, carNO from tCar where machineNO = '" + IMEI + "' or machineNO = '" + IMSI + "';");
			if (resultSetSub.next()) {
				((MachineSocket) objectSocket).bindVID = resultSetSub.getString(1);
				((MachineSocket) objectSocket).bindVName = resultSetSub.getString(2);
				System.out.println("IMEI:" + IMEI + " IMSI:" + IMSI + " ID:" + resultSetSub.getString(1) + " " +  ((MachineSocket) objectSocket).config);
				resultSetSub.close();
				((MachineSocket) objectSocket).obtainConfig();
			} else {
				resultSetSub.close();
				System.out.println("IMEI:" + IMEI + " IMSI:" + IMSI + " ID:NO MATCH");
			}
			
			LogHelper.writeLog("IMEI:" + IMEI + " IMSI:" + IMSI + " ID:" + ((MachineSocket) objectSocket).bindVID + " REGISTER SIZE=" + LocServer.socketList.size());
		} else {
			objectSocket = new UserSocket(this);
			LogHelper.writeLog("IMEI:" + IMEI + " IMSI:" + IMSI + " USER REGISTER SIZE=" + LocServer.socketList.size());
		}
		
		outputStream.write(status);
		outputStream.flush();
		
		existAndRemove();
		System.out.println("socket list's SIZE=" + LocServer.socketList.size());
		
	}

	/**
	 * 从连接列表中移除自身
	 */
	void removeFromList() {
		try {
			LocServer.socketList.remove(this);
			LogHelper.writeLog("IMEI:" + IMEI + " IMSI:" + IMSI + " REMOVE SIZE=" + LocServer.socketList.size());
			
			isRunning = false;
			inputStream.close();
			outputStream.close();
			if (!socket.isClosed()) {
				socket.close();
			}

		} catch (Exception e) {
			LogHelper.writeLog("IMEI:" + IMEI + " IMSI:" + IMSI + " REMOVE SOCKET OCCUR ERROR " + e.getMessage());
		}
	}
	
	/**
	 * 检查已连接设备中是否存在，如果存在，则移除
	 */
	private void existAndRemove() {
		for (int i = 0; i < LocServer.socketList.size(); i++) {
			if (LocServer.socketList.get(i).equalID(IMEI, IMSI)) {
				if (LocServer.socketList.get(i) == this) {
					continue;
				}
				
				SocketThread socket = LocServer.socketList.get(i);
				LocServer.socketList.remove(i);
				try {
					//socket.interrupt();
					socket.isRunning = false;
					//isRunning = false;
					socket.inputStream.close();
					socket.outputStream.close();
					if (!socket.socket.isClosed()) {
						socket.socket.close();
					}
					break;
				} catch (Exception e) {
					LogHelper.writeLog("IMEI:" + IMEI + " IMSI:" + IMSI + " REMOVE SOCKET OCCUR ERROR " + e.getMessage());
				} finally {
					socket = null;
					LogHelper.writeLog("IMEI:" + IMEI + " IMSI:" + IMSI + " REMOVE EXIST CONNECTION SIZE=" + LocServer.socketList.size());
					System.gc();
				}
			}
		}
	}
	
	public boolean equalID(String imei, String imsi) {
		return imei.equals(IMEI) && imsi.equals(IMSI);
	}
	
}