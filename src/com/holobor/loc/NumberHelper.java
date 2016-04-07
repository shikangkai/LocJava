package com.holobor.loc;

/**
 * 用于进行数字转换的类
 * @author Holobor
 */
public class NumberHelper {

	public static byte intToByte(int i) {
		byte b;
		if (i < 128) {
			b = (byte) i;
		} else {
			b = (byte) (i - 256);
		}
		return b;
	}
	
	public static int byteToInt(byte b) {
		if (b < 0) {
			return 256 + b;
		} else {
			return b;
		}
	}
}
