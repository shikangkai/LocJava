package com.holobor.loc;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务器运行类
 * @author Holobor
 *
 */
public class LocServer {
	
	static List<SocketThread> socketList;
	
	public static void main(String[] args) {
		
		ReorganizationTask.startReorganizationTask();
		socketList = new ArrayList<SocketThread>();
		
		ServerSocket server = null;
		try {
			server = new ServerSocket(8600);
			
			System.out.println("waiting CONNECT...");
			while (true) {
				socketList.add(new SocketThread(server.accept()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (null != server && !server.isClosed()) {
					server.close();
				}
			} catch (Exception e) {
				/*
				 *  TODO NULL
				 */
			}
		}
	}
}
