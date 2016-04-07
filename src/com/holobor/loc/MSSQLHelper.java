package com.holobor.loc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class MSSQLHelper {
	//Connection Pool:http://www.cnblogs.com/yangyh/archive/2011/07/15/2107824.html
	Connection connection;
	Statement statement;
	
	static MSSQLHelper msSQLHelper;
	
	static {
		msSQLHelper = new MSSQLHelper();
	}
	
	//单例模式
	private MSSQLHelper() {
		try {
			Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
			String   url="jdbc:sqlserver://localhost:1433;DatabaseName=GPS";
			String   user="sa";
			String   password="cai83114";
			connection = DriverManager.getConnection(url,user,password);
			statement = connection.createStatement();
		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println("When Connecting Database Occurs ERROR, Quit The Server.");
			System.exit(0);
		}
	}
	
	
	/**
	 * 执行更新操作，包括增加，删除，修改
	 * @param sql 要执行的SQL语句，包括INSERT, DELETE, UPDATE
	 */
	public static synchronized void executeSQL(String sql) {
		
		try {
			msSQLHelper.statement.execute(sql);
		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println("When Connecting Database Occurs ERROR, Quit The Server.");
			LogHelper.writeLog("DATABASE ERROR " + e.getMessage() + " SQL: " + sql);
			System.exit(0);
		}
	}
	
	/**
	 * 执行查询操作
	 * @param sql 查询的SQL语句，SELECT
	 * @return 返回查询的结果集
	 */
	public static ResultSet executeQuery(String sql) {
		
		try {
			return msSQLHelper.statement.executeQuery(sql);
		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println("When Connecting Database Occurs ERROR, Quit The Server.");
			LogHelper.writeLog("DATABASE ERROR " + e.getMessage() + " SQL: " + sql);
			System.exit(0);
			return null;
		}
	}
}