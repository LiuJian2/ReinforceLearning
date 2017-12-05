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

    private static String sSourceApk; // 待加固Apk

    private static String sReinforceDex; // 加固Apk的Dex文件

    private static String sEncryptedDex; // 合并后的加固Dex, 需要放到 加固Apk中

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
            // 修改DEX file size文件头
            fixFileSizeHeader(encryptedDexBytes);
            // 修改DEX SHA1 文件头
            fixSHA1Header(encryptedDexBytes);
            // 修改DEX CheckSum文件头
            fixCheckSumHeader(encryptedDexBytes);
            // 将合并后的
            writeToDstDex(encryptedDexBytes);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将源Apk byte流加密 并和 Dex的byte流合并
     *
     * @param sourceApkFile
     * @param encryptDex
     * @return
     * @throws IOException
     */
    private static byte[] mergeApk2Dex(File sourceApkFile, File encryptDex) throws IOException {
        byte[] sourceApkBytes = readFileBytes(sourceApkFile);//以二进制形式读出apk
        byte[] encryptedApkBytes = EncryptUtils.encrptByte(sourceApkBytes);//对源Apk进行加密操作
        byte[] sourceDexBytes = readFileBytes(encryptDex);//以二进制形式读出dex

        int encrptedApkLength = encryptedApkBytes.length;
        int sourceDexLength = sourceDexBytes.length;
        int totalLength = encrptedApkLength + sourceDexLength + 4; //多出4字节是存放长度的。

        byte[] encryptedDexBytes = new byte[totalLength];

        // 先拷贝dex内容
        System.arraycopy(sourceDexBytes, 0, encryptedDexBytes, 0, sourceDexLength);
        // 添加加密后的解壳数据
        System.arraycopy(encryptedApkBytes, 0, encryptedDexBytes, sourceDexLength, encrptedApkLength);//再在dex内容后面拷贝apk的内容
        // 添加解壳数据长度
        System.arraycopy(intToByte(encrptedApkLength), 0, encryptedDexBytes, totalLength - 4, 4);//最后4为长度, 写入sourceApkSize
        return encryptedDexBytes;
    }

    /**
     * 写入目标Dex文件
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
     * 修改校验码
     *
     * @param dexBytes
     */
    private static void fixCheckSumHeader(byte[] dexBytes) {
        Adler32 adler = new Adler32();
        adler.update(dexBytes, 12, dexBytes.length - 12); // 从12到文件尾计算校验码
        long value = adler.getValue();
        int va = (int) value;
        byte[] newcs = intToByte(va);
        //高位在前，低位在前掉个个
        byte[] recs = new byte[4];
        for (int i = 0; i < 4; i++) {
            recs[i] = newcs[newcs.length - 1 - i];
            System.out.println(Integer.toHexString(newcs[i]));
        }
        System.arraycopy(recs, 0, dexBytes, 8, 4);//效验码赋值（8-11）
        System.out.println("Write new check sum: " + Long.toHexString(value));
    }

    /**
     * int 转byte[]
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
     * 修改dex头 sha1值
     *
     * @param dexBytes
     * @throws NoSuchAlgorithmException
     */
    private static void fixSHA1Header(byte[] dexBytes)
            throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(dexBytes, 32, dexBytes.length - 32);//从32为到结束计算sha--1
        byte[] newdt = md.digest();
        System.arraycopy(newdt, 0, dexBytes, 12, 20);//修改sha-1值（12-31）
        //输出sha-1值，可有可无
        String hexstr = "";
        for (int i = 0; i < newdt.length; i++) {
            hexstr += Integer.toString((newdt[i] & 0xff) + 0x100, 16)
                    .substring(1);
        }
        System.out.println("Write new sha1: " + hexstr);
    }

    /**
     * 修改dex头 file_size值
     *
     * @param dexBytes
     */
    private static void fixFileSizeHeader(byte[] dexBytes) {
        //新文件长度
        byte[] newfs = intToByte(dexBytes.length);
        System.out.println("Write new file Size: " + Integer.toHexString(dexBytes.length));
        byte[] refs = new byte[4];
        //高位在前，低位在前掉个个
        for (int i = 0; i < 4; i++) {
            refs[i] = newfs[newfs.length - 1 - i];
        }
        System.arraycopy(refs, 0, dexBytes, 32, 4);//修改（32-35）
    }

    /**
     * 读取文件byte流
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
