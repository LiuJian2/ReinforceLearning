package com.encrypt;

import com.encrypt.tools.EncryptUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Adler32;

/**
 * Created by liujian03 on 2017/12/5.
 */
public class Encrypt {

    private static String sSourceApk; // ���ӹ�Apk

    private static String sReinforceDex; // �ӹ�Apk��Dex�ļ�

    private static String sEncryptedDex; // �ϲ���ļӹ�Dex, ��Ҫ�ŵ� �ӹ�Apk��

    public static void main(String[] args) {
        if (args == null || args.length < 2) {
            System.out.println("please input source apk path & reinforceapp dex path");
            return;
        }
        sSourceApk = args[0];
        sReinforceDex = args[1];
        if (args.length > 2) {
            sEncryptedDex = args[2];
        } else {
            sEncryptedDex = sReinforceDex;
        }
        reinforceDex();
    }

    private static void reinforceDex() {
        File sourceApkFile = new File(sSourceApk);
        File encryptedDex = new File(sReinforceDex);
        System.out.println("SourceApk: " + sSourceApk + ", Size: " + sourceApkFile.length());
        System.out.println("ReinforceDex: " + sReinforceDex + ", Size: " + sReinforceDex.length());
        try {
            byte[] encryptedDexBytes = mergeApk2Dex(sourceApkFile, encryptedDex);
            // �޸�DEX file size�ļ�ͷ
            fixFileSizeHeader(encryptedDexBytes);
            // �޸�DEX SHA1 �ļ�ͷ
            fixSHA1Header(encryptedDexBytes);
            // �޸�DEX CheckSum�ļ�ͷ
            fixCheckSumHeader(encryptedDexBytes);
            // ���ϲ����
            writeToDstDex(encryptedDexBytes);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    /**
     * ��ԴApk byte������ ���� Dex��byte���ϲ�
     *
     * @param sourceApkFile
     * @param encryptDex
     * @return
     * @throws IOException
     */
    private static byte[] mergeApk2Dex(File sourceApkFile, File encryptDex) throws IOException {
        byte[] sourceApkBytes = readFileBytes(sourceApkFile);//�Զ�������ʽ����apk
        byte[] encryptedApkBytes = EncryptUtils.encrptByte(sourceApkBytes);//��ԴApk���м��ܲ���
        byte[] sourceDexBytes = readFileBytes(encryptDex);//�Զ�������ʽ����dex

        int encrptedApkLength = encryptedApkBytes.length;
        int sourceDexLength = sourceDexBytes.length;
        int totalLength = encrptedApkLength + sourceDexLength + 4; //���4�ֽ��Ǵ�ų��ȵġ�

        byte[] encryptedDexBytes = new byte[totalLength];

        // �ȿ���dex����
        System.arraycopy(sourceDexBytes, 0, encryptedDexBytes, 0, sourceDexLength);
        // ��Ӽ��ܺ�Ľ������
        System.arraycopy(encryptedApkBytes, 0, encryptedDexBytes, sourceDexLength, encrptedApkLength);//����dex���ݺ��濽��apk������
        // ��ӽ�����ݳ���
        System.arraycopy(intToByte(encrptedApkLength), 0, encryptedDexBytes, totalLength - 4, 4);//���4Ϊ����, д��sourceApkSize
        return encryptedDexBytes;
    }

    /**
     * д��Ŀ��Dex�ļ�
     *
     * @param encryptedDexBytes
     * @throws IOException
     */
    private static void writeToDstDex(byte[] encryptedDexBytes) throws IOException {
        File file = new File(sEncryptedDex);
        if (!file.exists()) {
            file.createNewFile();
        }

        FileOutputStream localFileOutputStream = new FileOutputStream(sEncryptedDex);
        localFileOutputStream.write(encryptedDexBytes);
        localFileOutputStream.flush();
        localFileOutputStream.close();
        System.out.println("Write to " + sEncryptedDex + " success, file Size: " + file.length());
    }

    /**
     * �޸�У����
     *
     * @param dexBytes
     */
    private static void fixCheckSumHeader(byte[] dexBytes) {
        Adler32 adler = new Adler32();
        adler.update(dexBytes, 12, dexBytes.length - 12); // ��12���ļ�β����У����
        long value = adler.getValue();
        int va = (int) value;
        byte[] newcs = intToByte(va);
        //��λ��ǰ����λ��ǰ������
        byte[] recs = new byte[4];
        for (int i = 0; i < 4; i++) {
            recs[i] = newcs[newcs.length - 1 - i];
            System.out.println(Integer.toHexString(newcs[i]));
        }
        System.arraycopy(recs, 0, dexBytes, 8, 4);//Ч���븳ֵ��8-11��
        System.out.println("Write new check sum: " + Long.toHexString(value));
    }

    /**
     * int תbyte[]
     *
     * @param number
     * @return
     */
    private static byte[] intToByte(int number) {
        byte[] b = new byte[4];
        for (int i = 3; i >= 0; i--) {
            b[i] = (byte) (number % 256);
            number >>= 8;
        }
        return b;
    }

    /**
     * �޸�dexͷ sha1ֵ
     *
     * @param dexBytes
     * @throws NoSuchAlgorithmException
     */
    private static void fixSHA1Header(byte[] dexBytes)
            throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(dexBytes, 32, dexBytes.length - 32);//��32Ϊ����������sha--1
        byte[] newdt = md.digest();
        System.arraycopy(newdt, 0, dexBytes, 12, 20);//�޸�sha-1ֵ��12-31��
        //���sha-1ֵ�����п���
        String hexstr = "";
        for (int i = 0; i < newdt.length; i++) {
            hexstr += Integer.toString((newdt[i] & 0xff) + 0x100, 16)
                    .substring(1);
        }
        System.out.println("Write new sha1: " + hexstr);
    }

    /**
     * �޸�dexͷ file_sizeֵ
     *
     * @param dexBytes
     */
    private static void fixFileSizeHeader(byte[] dexBytes) {
        //���ļ�����
        byte[] newfs = intToByte(dexBytes.length);
        System.out.println("Write new file Size: " + Integer.toHexString(dexBytes.length));
        byte[] refs = new byte[4];
        //��λ��ǰ����λ��ǰ������
        for (int i = 0; i < 4; i++) {
            refs[i] = newfs[newfs.length - 1 - i];
        }
        System.arraycopy(refs, 0, dexBytes, 32, 4);//�޸ģ�32-35��
    }

    /**
     * ��ȡ�ļ�byte��
     *
     * @param file
     * @return
     * @throws IOException
     */
    private static byte[] readFileBytes(File file) throws IOException {
        byte[] arrayOfByte = new byte[1024];
        ByteArrayOutputStream localByteArrayOutputStream = new ByteArrayOutputStream();
        System.out.println("read file: " + file.getAbsolutePath());
        FileInputStream fileInputStream = new FileInputStream(file);
        while (true) {
            int i = fileInputStream.read(arrayOfByte);
            if (i > 0) {
                localByteArrayOutputStream.write(arrayOfByte, 0, i);
            } else {
                return localByteArrayOutputStream.toByteArray();
            }
        }
    }
}
