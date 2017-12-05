package com.encrypt.tools;

public class EncryptUtils {

    private static final int ENCRYPT_KEY = 0xAF;

    /**
     * 加密byte流, 这里只是简单的与 {@link #ENCRYPT_KEY} 异或
     * 加密和解密必须对应, 否则解析不正确
     * 通常这里建议使用JNI到Native层去做加密
     *
     * @param srcdata
     * @return
     */
    public static byte[] encrptByte(byte[] srcdata) {
        for (int i = 0; i < srcdata.length; i++) {
            srcdata[i] = (byte) (ENCRYPT_KEY ^ srcdata[i]);
        }
        return srcdata;
    }

    /**
     * 解密byte流, 与加密方法对应
     * 这里依然是与 {@link #ENCRYPT_KEY} 异或
     *
     * @param srcdata
     * @return
     */
    public static byte[] decrptByte(byte[] srcdata) {
        for (int i = 0; i < srcdata.length; i++) {
            srcdata[i] = (byte) (ENCRYPT_KEY ^ srcdata[i]);
        }
        return srcdata;
    }
}
