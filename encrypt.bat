// Windows命令行支持问题,暂时没写脚本.
// 运行步骤如下:
// 1.手动构建SourceApp
// 2.手动构建ReinforceApp
// 3.拷贝 ReinforceApp-debug.apk 和 SourceApp-debug.apk 到 Encryptproject 目录下
// 4.解压 ReinforceApp-debug.apk中的classes.dex文件到Encryptproject
// 5.运行Encrypt 并指定参数 SourceApp-debug.apk(全路径) classes.dex(全路径)
//   例如 java com/encrypt/Encrypt D:\SourceApp-debug.apk D:\classes.dex D:\encrypted-classes.dex(输出目标dex)
// 6.将输出目标dex放入ReinforceApp-debug.apk
// 7.对ReinforceApp-debug.apk重新签名, 签名命令如下
   jarsigner -verbose -keystore D:\learning.jks(签名文件路径) -storepass 123456(密码) -keypass 123456(密码) -sigfile CERT -digestalg SHA1 -sigalg MD5withRSA -signedjar Reinforce-Signed.apk(签名后Apk) Reinforce.apk(待签名Apk) learning(alias)
// 8.安装apk即可
