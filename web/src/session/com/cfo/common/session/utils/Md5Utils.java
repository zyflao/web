
package com.cfo.common.session.utils;

import java.security.MessageDigest;

public class Md5Utils {
	public static String getMD5String(String str) {
		try {
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			md5.update(str.getBytes());
			byte[] b = md5.digest();
			return byte2hex(b);
		} catch (Exception ex) {
			return null;
		}

	}
	
	public static String get16MD5String(String str) {
		String s=getMD5String(str);
		if(s!=null && s.length()==32){
			return s.substring(8, 24);
		}else{
			return null;
		}
	}

	private static String byte2hex(byte[] b) {
		String hs = "";
		StringBuffer sb = new StringBuffer();
		String stmp = "";
		for (int i = 0; i < b.length; i++) {
			stmp = (java.lang.Integer.toHexString(b[i] & 0XFF));
			if (stmp.length() == 1) {
				sb.append("0");
				sb.append(stmp);
			} else {
				sb.append(stmp);
			}
		}
		hs = sb.toString();
		return hs.toUpperCase();
	}
}
