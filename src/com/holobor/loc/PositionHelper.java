package com.holobor.loc;

/**
 * 计算位置相关变量的类
 * @author Holobor
 *
 */
public class PositionHelper {

	/**
	 * 计算两个坐标点之间的距离，单位km
	 * @param lat1
	 * @param lon1
	 * @param lat2
	 * @param lon2
	 * @return
	 */
	public static double calcDistance(double lat1, double lon1, double lat2, double lon2) {

		double c = Math.sin(lat1) * Math.sin(lat2) * Math.cos(lon1 - lon2) + Math.cos(lat1) * Math.cos(lat2);
		double distance = 6371 * Math.acos(c) * Math.PI / 180;
		return distance;
	}
}
