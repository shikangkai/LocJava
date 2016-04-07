package com.holobor.loc;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 监管人员的连接
 * @author Holobor
 *
 */
public class UserSocket extends ObjectSocket {

	SocketThread socket;
	
	/**
	 * 该人员监管的设备列表
	 */
	List<MachineSocket> bindMachineList;
	
	/**
	 * 用户ID
	 */
	String userID = null;
	
	public UserSocket(SocketThread socketThread) {
		super(socketThread);
		this.socket = socketThread;
		bindMachineList = new ArrayList<MachineSocket>();
	}

	@Override
	public void parseInfo() {
		try {
			int type = socket.inputStream.read();
			switch (type) {
			
			case 0x01: 
				break;
				
			//解析用户信息
			case 65:
				parseUser();
				break;
			
			//加锁和解锁操作
			case 66:
				parseLock();
				break;
			
			//语音数据
			case 67:
				parseSoundMsg();
				break;
			
			case -1:
				throw new Exception();
				
			case 127: {

				//直接根据信息内容找到对应的设备进行转发信息
				//暂时只有加锁解锁功能
				int length = socket.inputStream.read() << 8 | socket.inputStream.read();
				byte[] bytes = new byte[length];
				socket.inputStream.read(bytes);
				String mno = new String(bytes);
				length = socket.inputStream.read() << 8 | socket.inputStream.read();
				bytes = new byte[length];
				socket.inputStream.read(bytes);
				
				for (SocketThread thread : LocServer.socketList) {
					if (thread.IMEI.equals(mno) || thread.IMSI.equals(mno)) {
						thread.outputStream.write(bytes);
						thread.outputStream.flush();
						LocServer.socketList.remove(this);
						System.out.println("send to " + mno + " msg:" + bytes[0] + ", " + bytes[1] + ", " + bytes[2] + ", " + bytes[3]);
						LogHelper.writeLog("IMEI:" + thread.IMEI + " IMSI:" + thread.IMSI + " LOCKTYPE:" + bytes[3]);
						break;
					}
				}
			
			} break;
			
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
		}
	}

	/**
	 * 解析用户信息
	 */
	private void parseUser() throws Exception {
		//匹配用户ID和Password
		int length = socket.inputStream.read() << 8 | socket.inputStream.read();
		byte[] userBytes = new byte[length];
		socket.inputStream.read(userBytes);
		String[] userInfo = new String(userBytes).split(",");
		ResultSet resultSet = MSSQLHelper.executeQuery("select userID from tUser where userID = '" + userInfo[0] + "' and password = '" + userInfo[1] + "';");
		if (resultSet.next()) {
			userID = resultSet.getString(1);
		}
		resultSet.close();
	}
	
	/**
	 * 对加锁和解锁操作进行解析
	 */
	private void parseLock() throws Exception {
		
		int length = socket.inputStream.read() << 8 | socket.inputStream.read();
		byte[] bytes = new byte[length];
		socket.inputStream.read(bytes);
		String mno = new String(bytes);
		length = socket.inputStream.read() << 8 | socket.inputStream.read();
		bytes = new byte[length];
		socket.inputStream.read(bytes);
		
		for (SocketThread thread : LocServer.socketList) {
			if (thread.IMEI.equals(mno) || thread.IMSI.equals(mno)) {
				thread.outputStream.write(bytes);
				thread.outputStream.flush();
				LocServer.socketList.remove(this);
				System.out.println("send to " + mno + " msg:" + bytes[0] + ", " + bytes[1] + ", " + bytes[2] + ", " + bytes[3]);
				LogHelper.writeLog("IMEI:" + thread.IMEI + " IMSI:" + thread.IMSI + " LOCKTYPE:" + bytes[3]);
				break;
			}
		}
	}
	
	/**
	 * 解析语音数据
	 */
	private void parseSoundMsg() {
		
	}
}
