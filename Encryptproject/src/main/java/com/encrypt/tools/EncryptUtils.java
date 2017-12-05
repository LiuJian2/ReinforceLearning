package com.encrypt.tools;

public class EncryptUtils {

    private static final int ENCRYPT_KEY = 0xAF;

    /**
     * ����byte��, ����ֻ�Ǽ򵥵��� {@link #ENCRYPT_KEY} ���
     * ���ܺͽ��ܱ����Ӧ, �����������ȷ
     * ͨ�����ｨ��ʹ��JNI��Native��ȥ������
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
     * ����byte��, ����ܷ�����Ӧ
     * ������Ȼ���� {@link #ENCRYPT_KEY} ���
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
